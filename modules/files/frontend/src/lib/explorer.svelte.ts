import type { ModuleContext } from '../minedash';
import { join, parent } from './paths';
import type { Entry, Listing, SortDirection, SortKey } from './types';

const SORT_STORAGE_KEY = 'minedash.files.sort';
const PATH_STORAGE_KEY = 'minedash.files.path';
/** Browsers and servers limit the length of a URL, so a download link has a ceiling. */
const MAX_DOWNLOAD_URL_LENGTH = 7000;

export interface Notice {
    kind: 'error' | 'info';
    text: string;
}

/** "push" adds a browser history entry, "replace" overwrites the current one, "none" leaves it alone. */
export type HistoryMode = 'push' | 'replace' | 'none';

/** Natural order, so "file2" comes before "file10", and case is ignored. */
const collator = new Intl.Collator(undefined, { numeric: true, sensitivity: 'base' });

function readStorage(storage: () => Storage, key: string): string | null {
    try {
        return storage().getItem(key);
    } catch {
        return null;
    }
}

function writeStorage(storage: () => Storage, key: string, value: string): void {
    try {
        storage().setItem(key, value);
    } catch {
        // Storage is blocked, the choice is just not remembered
    }
}

function compareNames(a: string, b: string): number {
    const result = collator.compare(a, b);
    if (result !== 0) return result;
    // Names that differ only in case still need a stable order
    return a < b ? -1 : a > b ? 1 : 0;
}

function sortEntries(entries: Entry[], key: SortKey, direction: SortDirection): Entry[] {
    const factor = direction === 'asc' ? 1 : -1;
    return entries.slice().sort((a, b) => {
        // Folders stay on top, whatever the direction
        if (a.directory !== b.directory) return a.directory ? -1 : 1;

        let primary = 0;
        if (key === 'size') primary = a.size - b.size;
        else if (key === 'modified') primary = a.modified - b.modified;
        else primary = compareNames(a.name, b.name);

        if (primary !== 0) return primary * factor;
        // Ties are always ordered by name, ascending
        return compareNames(a.name, b.name);
    });
}

function describe(error: unknown): string {
    return error instanceof Error && error.message ? error.message : 'Something went wrong';
}

function isUnavailable(error: unknown): boolean {
    const status = (error as { status?: number } | null)?.status;
    return status === 404 || status === 403;
}

export class Explorer {
    readonly ctx: ModuleContext;
    readonly canView: boolean;
    readonly canWrite: boolean;
    readonly canDelete: boolean;

    /** Folder that is shown, relative to the managed root. The root itself is "". */
    path = $state('');
    entries = $state.raw<Entry[]>([]);
    truncated = $state(false);
    /** True once a folder was shown. Before that the page shows "loading" or the load error. */
    loaded = $state(false);
    busy = $state(false);
    /** Why the first folder could not be loaded. Later failures become a notice. */
    loadError = $state('');
    notice = $state.raw<Notice | null>(null);
    /** Names of the selected items in the shown folder. */
    selection = $state.raw<ReadonlySet<string>>(new Set());
    sortKey = $state<SortKey>('name');
    sortDirection = $state<SortDirection>('asc');

    sorted = $derived(sortEntries(this.entries, this.sortKey, this.sortDirection));
    folderCount = $derived(this.entries.filter((entry) => entry.directory).length);
    fileCount = $derived(this.entries.length - this.folderCount);
    selectedEntries = $derived(this.sorted.filter((entry) => this.selection.has(entry.name)));
    /** Total size of the selected files. Folders count as 0, their size is not known. */
    selectedBytes = $derived(
        this.selectedEntries.reduce((sum, entry) => sum + (entry.directory ? 0 : entry.size), 0)
    );
    allSelected = $derived(this.entries.length > 0 && this.selection.size === this.entries.length);
    someSelected = $derived(this.selection.size > 0 && !this.allSelected);

    private readonly pathKey: string;
    private requestId = 0;
    private requestedPath = '';
    private anchor: string | null = null;
    private disposed = false;

    constructor(ctx: ModuleContext) {
        this.ctx = ctx;
        this.canView = ctx.hasPermission('files.view');
        this.canWrite = ctx.hasPermission('files.write');
        this.canDelete = ctx.hasPermission('files.delete');
        this.pathKey = `${PATH_STORAGE_KEY}.${ctx.user.username}`;
        this.restoreSort();
    }

    /** Loads the first folder and listens to the back button. Returns the function that stops it. */
    start(): () => void {
        if (!this.canView) {
            return () => {};
        }
        window.addEventListener('popstate', this.onPopState);
        void this.open(this.initialPath(), 'replace', true);
        return () => {
            this.disposed = true;
            window.removeEventListener('popstate', this.onPopState);
        };
    }

    // ---------------------------------------------------------------- navigation

    /**
     * Shows a folder. A failure keeps the current folder on screen and shows a notice.
     * With fallbackToRoot, a folder that is gone or not allowed any more falls back to the root.
     */
    async open(path: string, mode: HistoryMode = 'push', fallbackToRoot = false): Promise<void> {
        const id = ++this.requestId;
        this.requestedPath = path;
        this.busy = true;
        try {
            const listing = await this.ctx.http.get<Listing>(`/list?path=${encodeURIComponent(path)}`);
            // A newer request or the end of the page makes this answer obsolete
            if (id !== this.requestId || this.disposed) return;
            this.show(listing, mode);
        } catch (error) {
            if (id !== this.requestId || this.disposed) return;
            if (fallbackToRoot && path !== '' && isUnavailable(error)) {
                await this.open('', 'replace');
                return;
            }
            const text = describe(error);
            if (this.loaded) this.notify('error', text);
            else this.loadError = text;
        } finally {
            if (id === this.requestId) this.busy = false;
        }
    }

    enter(name: string): void {
        void this.open(join(this.path, name));
    }

    up(): void {
        if (this.path !== '') void this.open(parent(this.path));
    }

    /** Loads the shown folder again. Without a shown folder it retries the one that failed. */
    reload(): void {
        void this.open(this.loaded ? this.path : this.requestedPath, this.loaded ? 'none' : 'replace');
    }

    private show(listing: Listing, mode: HistoryMode): void {
        const pathChanged = !this.loaded || listing.path !== this.path;
        this.path = listing.path;
        this.entries = listing.entries;
        this.truncated = listing.truncated;
        this.loaded = true;
        this.loadError = '';

        if (pathChanged) {
            this.selection = new Set();
            this.anchor = null;
            this.notice = null;
        } else {
            // A reload keeps the selection of items that still exist
            const names = new Set(listing.entries.map((entry) => entry.name));
            this.selection = new Set([...this.selection].filter((name) => names.has(name)));
        }
        this.remember(mode, pathChanged);
    }

    private initialPath(): string {
        const state: unknown = history.state;
        const fromHistory = (state as { mdFiles?: unknown } | null)?.mdFiles;
        if (typeof fromHistory === 'string') return fromHistory;
        return readStorage(() => sessionStorage, this.pathKey) ?? '';
    }

    private remember(mode: HistoryMode, pathChanged: boolean): void {
        writeStorage(() => sessionStorage, this.pathKey, this.path);
        if (mode === 'none') return;

        const current: unknown = history.state;
        const base = typeof current === 'object' && current !== null ? current : {};
        const state = { ...base, mdFiles: this.path };
        try {
            if (mode === 'push' && pathChanged) history.pushState(state, '');
            else history.replaceState(state, '');
        } catch {
            // The browser refuses to change the history, navigation still works without it
        }
    }

    private onPopState = (event: PopStateEvent): void => {
        const state: unknown = event.state;
        const path = (state as { mdFiles?: unknown } | null)?.mdFiles;
        if (typeof path === 'string' && path !== this.path) {
            void this.open(path, 'none');
        }
    };

    // ---------------------------------------------------------------- selection

    isSelected(name: string): boolean {
        return this.selection.has(name);
    }

    selectOnly(name: string): void {
        this.selection = new Set([name]);
        this.anchor = name;
    }

    toggle(name: string): void {
        const next = new Set(this.selection);
        if (next.has(name)) next.delete(name);
        else next.add(name);
        this.selection = next;
        this.anchor = name;
    }

    /** Selects everything between the last clicked item and the given one. */
    selectRange(name: string): void {
        const names = this.sorted.map((entry) => entry.name);
        const from = this.anchor === null ? -1 : names.indexOf(this.anchor);
        const to = names.indexOf(name);
        if (from < 0 || to < 0) {
            this.selectOnly(name);
            return;
        }
        const start = Math.min(from, to);
        const end = Math.max(from, to);
        this.selection = new Set(names.slice(start, end + 1));
    }

    /** Sets the starting point for the next range selection. */
    setAnchor(name: string): void {
        if (this.anchor === null) this.anchor = name;
    }

    selectAll(): void {
        this.selection = new Set(this.entries.map((entry) => entry.name));
    }

    toggleAll(): void {
        if (this.allSelected) this.clearSelection();
        else this.selectAll();
    }

    clearSelection(): void {
        this.selection = new Set();
        this.anchor = null;
    }

    // ---------------------------------------------------------------- sorting

    setSort(key: SortKey): void {
        if (this.sortKey === key) {
            this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
        } else {
            this.sortKey = key;
            // Sizes and dates are most useful with the biggest and newest first
            this.sortDirection = key === 'name' ? 'asc' : 'desc';
        }
        writeStorage(
            () => localStorage,
            SORT_STORAGE_KEY,
            JSON.stringify({ key: this.sortKey, direction: this.sortDirection })
        );
    }

    private restoreSort(): void {
        const raw = readStorage(() => localStorage, SORT_STORAGE_KEY);
        if (!raw) return;
        try {
            const data = JSON.parse(raw);
            const validKey = data.key === 'name' || data.key === 'size' || data.key === 'modified';
            const validDirection = data.direction === 'asc' || data.direction === 'desc';
            if (validKey && validDirection) {
                this.sortKey = data.key;
                this.sortDirection = data.direction;
            }
        } catch {
            // Broken value, the default order stays
        }
    }

    // ---------------------------------------------------------------- download and notices

    /** Downloads the selected items. A single file is sent as it is, anything else as a ZIP. */
    downloadSelected(): void {
        const names = this.selectedEntries.map((entry) => entry.name);
        if (names.length === 0) return;

        const query = names.map((name) => `path=${encodeURIComponent(join(this.path, name))}`).join('&');
        const url = this.ctx.url(`/download?${query}`);
        if (url.length > MAX_DOWNLOAD_URL_LENGTH) {
            this.notify('error', 'Too many items for one download. Select fewer items.');
            return;
        }

        // A link with the download attribute never replaces the panel, even if the server answers with an error
        const link = document.createElement('a');
        link.href = url;
        link.download = '';
        link.rel = 'noopener';
        link.style.display = 'none';
        document.body.appendChild(link);
        link.click();
        link.remove();
    }

    notify(kind: Notice['kind'], text: string): void {
        this.notice = { kind, text };
    }

    dismissNotice(): void {
        this.notice = null;
    }
}