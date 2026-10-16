import { Drafts, type DraftResult } from './drafts';
import type { Explorer } from './explorer.svelte';
import { formatBytes } from './format';
import { join, parent } from './paths';

/** What a save needs: the text to send and an opaque marker of the exact editor state it was taken from. */
export interface Snapshot {
    text: string;
    token: unknown;
}

/** Link to the code editor component. It exists while the editor is on screen. */
export interface Controller {
    snapshot(): Snapshot;
    markSaved(snapshot: Snapshot): void;
}

export type EditorStatus = 'idle' | 'loading' | 'ready' | 'error';
export type ConflictKind = 'changed' | 'deleted';

/** What the user can decide when a save ran into a conflict. */
export type ConflictAction = 'keep' | 'copy' | 'reload' | 'overwrite' | 'recreate' | 'discard';

/** A question about leaving the editor with unsaved changes. */
export interface LeaveRequest {
    /** "close" leaves the editor, "reload" replaces the content with the file on disk. */
    reason: 'close' | 'reload';
    proceed: () => void;
    stay: () => void;
}

/** Shown when unsaved text from an earlier visit was put back into the editor. */
export interface DraftNotice {
    /** When the draft was written, in milliseconds since 1970. */
    at: number;
    /** True if the file was changed on disk after the draft was written. */
    stale: boolean;
}

interface FileContent {
    path: string;
    content: string;
    version: string;
    size: number;
    modified: number;
}

interface SaveResult {
    version: string;
    size: number;
    modified: number;
}

/** "push" adds a history entry, "pop" is used when the entry exists already, "keep" reloads the same file. */
type LoadMode = 'push' | 'pop' | 'keep';

const WRAP_KEY = 'minedash.files.wrap';
const DEFAULT_MAX_EDIT_BYTES = 2 * 1024 * 1024;
/** How many names are tried for a copy: "x (my copy)", "x (my copy 2)" and so on. */
const MAX_COPY_ATTEMPTS = 20;
/** A draft is written this long after the last change. */
const DRAFT_DELAY_MS = 700;
/** Typing without a pause still writes a draft at least this often. */
const DRAFT_MAX_WAIT_MS = 5000;

function describe(error: unknown): string {
    return error instanceof Error && error.message ? error.message : 'Something went wrong';
}

function statusOf(error: unknown): number {
    const status = (error as { status?: unknown } | null)?.status;
    return typeof status === 'number' ? status : 0;
}

function readItem(key: string): string | null {
    try {
        return localStorage.getItem(key);
    } catch {
        return null;
    }
}

function writeItem(key: string, value: string): void {
    try {
        localStorage.setItem(key, value);
    } catch {
        // Storage is blocked, the choice is just not remembered
    }
}

/**
 * The line break the file uses. The editor splits lines at exactly this separator, so every other
 * character, including a lone "\r", stays untouched and the file is saved byte for byte.
 */
function detectSeparator(text: string): '\n' | '\r\n' {
    let crlf = 0;
    let lf = 0;
    for (let i = text.indexOf('\n'); i >= 0; i = text.indexOf('\n', i + 1)) {
        if (i > 0 && text.charCodeAt(i - 1) === 13) crlf++;
        else lf++;
    }
    return crlf > lf ? '\r\n' : '\n';
}

/** "server.yml" becomes "server (my copy).yml", the next attempts are "server (my copy 2).yml" and so on. */
function copyName(name: string, attempt: number): string {
    const dot = name.lastIndexOf('.');
    const stem = dot > 0 ? name.slice(0, dot) : name;
    const extension = dot > 0 ? name.slice(dot) : '';
    const label = attempt === 1 ? 'my copy' : `my copy ${attempt}`;
    return `${stem} (${label})${extension}`;
}

/** One editing session at a time: which file is open, what was loaded and what a save has to do. */
export class Editing {
    readonly explorer: Explorer;

    /** Path of the open file relative to the managed root. Null while no file is open. */
    path = $state<string | null>(null);
    /** Raised for every loaded content, so the code editor is created fresh for it. */
    session = $state(0);
    status = $state<EditorStatus>('idle');
    loadError = $state('');
    /** HTTP status of the failed load, or 0. */
    loadStatus = $state(0);
    /** Content as it is on disk. Only the initial text of the code editor. */
    text = $state.raw('');
    /** Hash of the content the text in the editor is based on, the server compares it when saving. */
    version = $state('');
    size = $state(0);
    separator = $state<'\n' | '\r\n'>('\n');
    dirty = $state(false);
    /** True while a save, an overwrite or a copy is running. */
    saving = $state(false);
    saveError = $state('');
    savedAt = $state<number | null>(null);
    conflict = $state<ConflictKind | null>(null);
    /** True while the conflict dialog is shown. The conflict itself stays until it is resolved. */
    conflictOpen = $state(false);
    leave = $state.raw<LeaveRequest | null>(null);
    wrap = $state(false);
    /** Largest file the editor opens, as configured on the server. */
    maxBytes = $state(DEFAULT_MAX_EDIT_BYTES);

    /** Unsaved text from an earlier visit that the editor puts on top of the content. Null if there is none. */
    draftText = $state.raw<string | null>(null);
    draftNotice = $state.raw<DraftNotice | null>(null);
    /** What happened to the last draft write. "none" until the first one. */
    draftStatus = $state<DraftResult | 'none'>('none');

    /** Paths of the files that have a draft. The file list marks them. */
    draftPaths = $state.raw<ReadonlySet<string>>(new Set());
    /** Folders that hold a file with a draft, parent folders included. The file list marks them too. */
    draftFolders = $derived.by(() => {
        const folders = new Set<string>();
        for (const path of this.draftPaths) {
            for (let folder = parent(path); folder !== ''; folder = parent(folder)) {
                // When a folder is known already, so are its parents
                if (folders.has(folder)) break;
                folders.add(folder);
            }
        }
        return folders;
    });

    active = $derived(this.path !== null);
    name = $derived(this.path === null ? '' : this.path.slice(this.path.lastIndexOf('/') + 1));
    folder = $derived(this.path === null ? '' : parent(this.path));
    readOnly = $derived(!this.explorer.canWrite);

    /** Set by the editor view while it is mounted. Not reactive on purpose. */
    controller: Controller | null = null;

    private readonly drafts: Drafts;
    private requestId = 0;
    private entryPushed = false;
    private disposed = false;
    private draftTimer: number | undefined;
    private draftFirstChange = 0;

    constructor(explorer: Explorer) {
        this.explorer = explorer;
        this.drafts = new Drafts(explorer.ctx.user.username);
        this.wrap = readItem(WRAP_KEY) === '1';
    }

    /** Starts listening and reopens the editor after a reload of the page. Returns the cleanup function. */
    start(): () => void {
        if (!this.explorer.canView) {
            return () => {};
        }
        window.addEventListener('popstate', this.onPopState);
        window.addEventListener('beforeunload', this.onBeforeUnload);
        window.addEventListener('pagehide', this.onPageHide);
        document.addEventListener('visibilitychange', this.onVisibilityChange);
        void this.loadLimit();
        this.refreshDrafts();

        const state: unknown = history.state;
        const edit = (state as { mdEdit?: unknown } | null)?.mdEdit;
        if (typeof edit === 'string' && edit !== '') {
            void this.load(edit, 'pop');
        }

        return () => {
            this.disposed = true;
            this.requestId++;
            window.clearTimeout(this.draftTimer);
            window.removeEventListener('popstate', this.onPopState);
            window.removeEventListener('beforeunload', this.onBeforeUnload);
            window.removeEventListener('pagehide', this.onPageHide);
            document.removeEventListener('visibilitychange', this.onVisibilityChange);
        };
    }

    tooLarge(size: number): boolean {
        return size > this.maxBytes;
    }

    // ---------------------------------------------------------------- opening and closing

    open(path: string): void {
        if (this.path === path && this.status !== 'error') return;
        this.guard('close', () => void this.load(path, 'push'));
    }

    /** Closes the editor, after asking if there are unsaved changes. */
    close(): void {
        this.guard('close', () => this.leaveByButton());
    }

    /** Loads the file from disk again, after asking if there are unsaved changes. */
    requestReload(): void {
        this.guard('reload', () => this.reloadFromDisk());
    }

    /** Loads the file from disk again and drops all unsaved changes, including the draft. */
    reloadFromDisk(): void {
        if (this.path === null) return;
        this.dropDraft();
        void this.load(this.path, 'keep');
    }

    /** After a failed load: try the same file once more. */
    retry(): void {
        if (this.path !== null) void this.load(this.path, 'keep');
    }

    /** Closes without asking. Used when the user already decided to give up the unsaved text. */
    discardAndClose(): void {
        this.dropDraft();
        this.dirty = false;
        this.leaveByButton();
    }

    // ---------------------------------------------------------------- saving

    /** Saves the text. Returns true if it was saved. Problems are shown in the editor. */
    save(): Promise<boolean> {
        return this.send(false);
    }

    dismissSaveError(): void {
        this.saveError = '';
    }

    /** Called by the editor view whenever the text changed, with the unsaved state after the change. */
    onChange(dirty: boolean): void {
        this.dirty = dirty;
        if (dirty) this.scheduleDraft();
        else this.dropDraft();
    }

    toggleWrap(): void {
        this.wrap = !this.wrap;
        writeItem(WRAP_KEY, this.wrap ? '1' : '0');
    }

    /** Downloads the open file. A link with the download attribute never replaces the panel. */
    download(): void {
        if (this.path === null) return;
        const link = document.createElement('a');
        link.href = this.explorer.ctx.url(`/download?path=${encodeURIComponent(this.path)}`);
        link.download = '';
        link.rel = 'noopener';
        link.style.display = 'none';
        document.body.appendChild(link);
        link.click();
        link.remove();
    }

    // ---------------------------------------------------------------- drafts

    /**
     * Writes the unsaved text to the draft storage right now. Safe to call at any time, it does
     * nothing unless there is unsaved text. The editor passes a snapshot when it is about to be
     * destroyed, because the controller is not reliable any more at that point.
     */
    flushDraft(snapshot?: Snapshot): void {
        window.clearTimeout(this.draftTimer);
        this.draftFirstChange = 0;
        if (!this.dirty || this.readOnly || this.path === null || this.status !== 'ready') return;

        const current = snapshot ?? this.controller?.snapshot();
        if (!current) return;
        this.draftStatus = this.drafts.write(this.path, current.text, this.version);
        this.refreshDrafts();
    }

    /** Throws the restored draft away and shows the file as it is on disk. */
    discardDraft(): void {
        this.dirty = false;
        this.reloadFromDisk();
    }

    dismissDraftNotice(): void {
        this.draftNotice = null;
    }

    /**
     * Reads which files have a draft again, for the marks in the file list. Called after every change
     * of the draft storage. Users who cannot write have no drafts, so nothing is marked for them.
     */
    refreshDrafts(): void {
        const next = this.readOnly ? [] : this.drafts.paths();
        const current = this.draftPaths;
        // The same files again: no new set, so nothing in the list has to be updated
        if (next.length === current.size && next.every((path) => current.has(path))) return;
        this.draftPaths = new Set(next);
    }

    /** Files or folders were deleted in the panel. The drafts of everything inside are of no use any more. */
    forgetDrafts(paths: string[]): void {
        for (const path of paths) this.drafts.removeTree(path);
        this.refreshDrafts();
    }

    /** Files or folders were renamed or moved in the panel. Their drafts follow them. */
    moveDrafts(moves: Array<{ from: string; to: string }>): void {
        for (const move of moves) this.drafts.moveTree(move.from, move.to);
        this.refreshDrafts();
    }

    // ---------------------------------------------------------------- conflicts

    /** Shows the conflict dialog again, e.g. after "Keep editing". */
    openConflict(): void {
        if (this.conflict !== null) this.conflictOpen = true;
    }

    /** Carries out the decision of the user in the conflict dialog. */
    async resolve(action: ConflictAction): Promise<void> {
        switch (action) {
            case 'keep':
                this.conflictOpen = false;
                break;
            case 'overwrite':
            case 'recreate':
                await this.send(true);
                break;
            case 'copy':
                await this.saveCopy();
                break;
            case 'reload':
                this.reloadFromDisk();
                break;
            case 'discard':
                this.discardAndClose();
                break;
        }
    }

    // ---------------------------------------------------------------- the question about unsaved changes

    /** The user wants to stay in the editor. */
    stay(): void {
        const request = this.leave;
        this.leave = null;
        request?.stay();
    }

    /** The user gives up the unsaved changes. */
    discard(): void {
        const request = this.leave;
        if (!request) return;
        this.leave = null;
        // The text is given up on purpose, so no draft is kept either
        this.dropDraft();
        request.proceed();
    }

    /** The user wants to save first. Only offered when the editor is about to close. */
    async saveAndLeave(): Promise<void> {
        const request = this.leave;
        if (!request || request.reason !== 'close') return;

        const saved = await this.save();
        // A conflict or an error is shown in the editor itself, so the question gives way to it
        this.leave = null;
        if (saved && !this.dirty) request.proceed();
        else request.stay();
    }

    // ---------------------------------------------------------------- internals

    /**
     * Sends the text to the file. With "force" the server skips the version check, so the file on disk
     * is replaced, or created again if it was deleted. Returns true if the text was saved.
     */
    private async send(force: boolean): Promise<boolean> {
        if (this.saving || this.readOnly || this.status !== 'ready' || this.path === null || !this.controller) {
            return false;
        }
        const path = this.path;
        const snapshot = this.controller.snapshot();

        // The server would refuse it as well, but this message is clearer than a dropped connection
        if (new Blob([snapshot.text]).size > this.maxBytes) {
            this.saveError =
                `The text is larger than the editor limit (${formatBytes(this.maxBytes, 0)}). ` +
                'Shorten it, or upload the file instead.';
            return false;
        }

        this.saving = true;
        this.saveError = '';
        try {
            const query = force
                ? `/content?path=${encodeURIComponent(path)}&force=true`
                : `/content?path=${encodeURIComponent(path)}&version=${encodeURIComponent(this.version)}`;
            const result = await this.explorer.ctx.http.upload<SaveResult>(query, snapshot.text, {
                contentType: 'text/plain; charset=utf-8'
            });
            if (this.path !== path) return false;

            this.version = result.version;
            this.size = result.size;
            this.savedAt = Date.now();
            this.conflict = null;
            this.conflictOpen = false;
            this.draftNotice = null;

            if (this.controller) {
                // Edits made while the request was running stay marked as unsaved
                this.controller.markSaved(snapshot);
            } else {
                // The editor was closed while the request was running. Its draft now builds on the saved text.
                this.drafts.settle(path, snapshot.text, result.version);
                this.refreshDrafts();
            }
            return true;
        } catch (error) {
            if (this.path === path) this.handleSaveError(error);
            return false;
        } finally {
            this.saving = false;
        }
    }

    /**
     * Stores the text next to the original under a new name. The file on disk is not touched, so
     * the unsaved text is kept without overwriting what someone else wrote.
     */
    private async saveCopy(): Promise<boolean> {
        if (this.saving || this.readOnly || this.status !== 'ready' || this.path === null || !this.controller) {
            return false;
        }
        const path = this.path;
        const folder = this.folder;
        const original = this.name;
        const snapshot = this.controller.snapshot();

        this.saving = true;
        this.saveError = '';
        try {
            for (let attempt = 1; attempt <= MAX_COPY_ATTEMPTS; attempt++) {
                const name = copyName(original, attempt);
                try {
                    await this.explorer.ctx.http.upload(
                        `/upload?path=${encodeURIComponent(join(folder, name))}&overwrite=false`,
                        snapshot.text,
                        { contentType: 'text/plain; charset=utf-8' }
                    );
                } catch (error) {
                    // The name is taken by a file or a folder, so the next one is tried
                    if (statusOf(error) === 409 && /already exists/i.test(describe(error))) continue;
                    throw error;
                }

                if (this.path !== path) return false;
                this.conflictOpen = false;
                this.explorer.notify('info', `Saved your version as "${name}". The original file was not changed.`);
                return true;
            }
            this.saveError = 'No free name was found for the copy. Rename or delete some old copies first.';
            return false;
        } catch (error) {
            if (this.path === path) this.saveError = describe(error);
            return false;
        } finally {
            this.saving = false;
        }
    }

    /** Runs "proceed" right away, or after the user answered the question if there are unsaved changes. */
    private guard(reason: LeaveRequest['reason'], proceed: () => void, stay: () => void = () => {}): void {
        if (!this.dirty) {
            proceed();
            return;
        }
        // A question is already open, the answer to it decides
        if (this.leave) return;
        this.leave = { reason, proceed, stay };
    }

    private async load(path: string, mode: LoadMode): Promise<void> {
        const id = ++this.requestId;
        this.path = path;
        this.dirty = false;
        this.status = 'loading';
        this.loadError = '';
        this.loadStatus = 0;
        this.saveError = '';
        this.conflict = null;
        this.conflictOpen = false;
        this.savedAt = null;
        this.leave = null;
        this.draftText = null;
        this.draftNotice = null;
        this.draftStatus = 'none';
        window.clearTimeout(this.draftTimer);
        this.draftFirstChange = 0;

        if (mode === 'push') this.pushEntry(path);
        else if (mode === 'pop') this.entryPushed = true;

        try {
            const file = await this.explorer.ctx.http.get<FileContent>(`/content?path=${encodeURIComponent(path)}`);
            // A newer request or the end of the page makes this answer obsolete
            if (id !== this.requestId || this.disposed) return;

            this.text = file.content;
            this.version = file.version;
            this.size = file.size;
            this.separator = detectSeparator(file.content);
            this.restoreDraft(path, file);
            this.session++;
            this.status = 'ready';
        } catch (error) {
            if (id !== this.requestId || this.disposed) return;
            this.loadError = describe(error);
            this.loadStatus = statusOf(error);
            this.status = 'error';
        }
    }

    /** Puts unsaved text from an earlier visit back into the editor. */
    private restoreDraft(path: string, file: FileContent): void {
        if (this.readOnly) return;

        const draft = this.drafts.read(path);
        if (draft === null) return;
        if (draft.text === file.content) {
            // Nothing to restore, the file already contains this text
            this.removeDraft(path);
            return;
        }

        this.draftText = draft.text;
        // The draft builds on the version it was written for. If the file changed since then, saving
        // is refused with a conflict and the user decides how to combine them.
        this.version = draft.base;
        this.draftNotice = { at: draft.at, stale: draft.base !== file.version };
        this.draftStatus = 'saved';
    }

    private scheduleDraft(): void {
        if (this.readOnly || this.path === null) return;

        const now = Date.now();
        if (this.draftFirstChange === 0) this.draftFirstChange = now;
        window.clearTimeout(this.draftTimer);
        // Without a pause in typing, the draft is still written every few seconds
        const wait = now - this.draftFirstChange >= DRAFT_MAX_WAIT_MS ? 0 : DRAFT_DELAY_MS;
        this.draftTimer = window.setTimeout(() => this.flushDraft(), wait);
    }

    /** Forgets the draft of the open file. */
    private dropDraft(): void {
        window.clearTimeout(this.draftTimer);
        this.draftFirstChange = 0;
        this.draftStatus = 'none';
        if (this.path !== null && !this.readOnly) this.removeDraft(this.path);
    }

    /** Removes the draft of a file and updates the marks in the file list. */
    private removeDraft(path: string): void {
        this.drafts.remove(path);
        this.refreshDrafts();
    }

    private handleSaveError(error: unknown): void {
        const status = statusOf(error);
        if (status === 409) {
            // "No longer exists" means the file was deleted, anything else means it is there but different
            this.conflict = /no longer exists/i.test(describe(error)) ? 'deleted' : 'changed';
            this.conflictOpen = true;
        } else if (status === 413) {
            this.saveError = 'The text is larger than the editor limit. Shorten it, or upload the file instead.';
        } else {
            this.saveError = describe(error);
        }
    }

    private reset(): void {
        this.requestId++;
        window.clearTimeout(this.draftTimer);
        this.draftFirstChange = 0;
        this.path = null;
        this.status = 'idle';
        this.loadError = '';
        this.loadStatus = 0;
        this.text = '';
        this.version = '';
        this.size = 0;
        this.dirty = false;
        this.saving = false;
        this.saveError = '';
        this.savedAt = null;
        this.conflict = null;
        this.conflictOpen = false;
        this.leave = null;
        this.controller = null;
        this.entryPushed = false;
        this.draftText = null;
        this.draftNotice = null;
        this.draftStatus = 'none';
    }

    /** Closing with the button of the editor: go back in the history if the editor has an entry there. */
    private leaveByButton(): void {
        const pushed = this.entryPushed;
        this.reset();
        if (pushed) {
            try {
                history.back();
            } catch {
                // The entry stays in the history, which is harmless
            }
        } else {
            this.dropEditEntry();
        }
        this.refreshList();
    }

    /** The browser already moved in the history, so the editor only has to close. */
    private leaveAfterHistory(): void {
        this.reset();
        this.refreshList();
    }

    /** The size and date of the file may have changed while it was edited. */
    private refreshList(): void {
        void this.explorer.open(this.explorer.path, 'none');
    }

    private pushEntry(path: string): void {
        try {
            const current: unknown = history.state;
            const base = typeof current === 'object' && current !== null ? current : {};
            history.pushState({ ...base, mdFiles: parent(path), mdEdit: path }, '');
            this.entryPushed = true;
        } catch {
            this.entryPushed = false;
        }
    }

    /** Removes the editor part from the current history entry, when there is no entry of its own to go back to. */
    private dropEditEntry(): void {
        const current: unknown = history.state;
        if (typeof current !== 'object' || current === null || !('mdEdit' in current)) return;
        const rest = { ...(current as Record<string, unknown>) };
        delete rest.mdEdit;
        try {
            history.replaceState(rest, '');
        } catch {
            // Nothing to do
        }
    }

    /**
     * The back and forward buttons of the browser. The explorer shows the folder of the entry,
     * this part opens and closes the editor.
     */
    private onPopState = (event: PopStateEvent): void => {
        const state: unknown = event.state;
        const edit = (state as { mdEdit?: unknown } | null)?.mdEdit;

        if (typeof edit !== 'string') {
            const path = this.path;
            if (path === null) return;
            // Back from the editor to the list. Without a decision, the editor entry is put back.
            this.guard('close', () => this.leaveAfterHistory(), () => this.pushEntry(path));
            return;
        }

        if (edit === this.path) return;
        const previous = this.path;
        this.guard(
            'close',
            () => void this.load(edit, 'pop'),
            () => {
                if (previous !== null) this.pushEntry(previous);
            }
        );
    };

    private onBeforeUnload = (event: BeforeUnloadEvent): void => {
        this.flushDraft();
        if (!this.dirty) return;
        event.preventDefault();
        // Needed by some browsers to show the warning
        event.returnValue = '';
    };

    /** Mobile browsers often skip beforeunload, so the draft is also written when the page is hidden. */
    private onPageHide = (): void => {
        this.flushDraft();
    };

    private onVisibilityChange = (): void => {
        if (document.visibilityState === 'hidden') this.flushDraft();
    };

    private async loadLimit(): Promise<void> {
        try {
            const info = await this.explorer.ctx.http.get<{ maxEditBytes?: number }>('/info');
            if (typeof info.maxEditBytes === 'number' && info.maxEditBytes > 0) {
                this.maxBytes = info.maxEditBytes;
            }
        } catch {
            // The server enforces the limit anyway, the file is just requested first
        }
    }
}