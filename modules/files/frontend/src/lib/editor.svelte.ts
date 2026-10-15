import type { Explorer } from './explorer.svelte';
import { parent } from './paths';

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

/** A question about leaving the editor with unsaved changes. */
export interface LeaveRequest {
    /** "close" leaves the editor, "reload" replaces the content with the file on disk. */
    reason: 'close' | 'reload';
    proceed: () => void;
    stay: () => void;
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
    /** Hash of the loaded content, the server compares it when saving. */
    version = $state('');
    size = $state(0);
    separator = $state<'\n' | '\r\n'>('\n');
    dirty = $state(false);
    saving = $state(false);
    saveError = $state('');
    savedAt = $state<number | null>(null);
    conflict = $state<ConflictKind | null>(null);
    leave = $state.raw<LeaveRequest | null>(null);
    wrap = $state(false);
    /** Largest file the editor opens, as configured on the server. */
    maxBytes = $state(DEFAULT_MAX_EDIT_BYTES);

    active = $derived(this.path !== null);
    name = $derived(this.path === null ? '' : this.path.slice(this.path.lastIndexOf('/') + 1));
    folder = $derived(this.path === null ? '' : parent(this.path));
    readOnly = $derived(!this.explorer.canWrite);

    /** Set by the editor view while it is mounted. Not reactive on purpose. */
    controller: Controller | null = null;

    private requestId = 0;
    private entryPushed = false;
    private disposed = false;

    constructor(explorer: Explorer) {
        this.explorer = explorer;
        this.wrap = readItem(WRAP_KEY) === '1';
    }

    /** Starts listening and reopens the editor after a reload of the page. Returns the cleanup function. */
    start(): () => void {
        if (!this.explorer.canView) {
            return () => {};
        }
        window.addEventListener('popstate', this.onPopState);
        window.addEventListener('beforeunload', this.onBeforeUnload);
        void this.loadLimit();

        const state: unknown = history.state;
        const edit = (state as { mdEdit?: unknown } | null)?.mdEdit;
        if (typeof edit === 'string' && edit !== '') {
            void this.load(edit, 'pop');
        }

        return () => {
            this.disposed = true;
            this.requestId++;
            window.removeEventListener('popstate', this.onPopState);
            window.removeEventListener('beforeunload', this.onBeforeUnload);
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

    /** Loads the file from disk again and drops all unsaved changes. */
    reloadFromDisk(): void {
        if (this.path !== null) void this.load(this.path, 'keep');
    }

    /** After a failed load: try the same file once more. */
    retry(): void {
        if (this.path !== null) void this.load(this.path, 'keep');
    }

    /** Closes without asking. Used when the file is gone and there is nothing to save to. */
    discardAndClose(): void {
        this.dirty = false;
        this.leaveByButton();
    }

    // ---------------------------------------------------------------- saving

    /** Saves the text. Returns true if it was saved. Problems are shown in the editor. */
    async save(): Promise<boolean> {
        if (this.saving || this.readOnly || this.status !== 'ready' || this.path === null || !this.controller) {
            return false;
        }
        const path = this.path;
        const snapshot = this.controller.snapshot();

        this.saving = true;
        this.saveError = '';
        this.conflict = null;
        try {
            const query = `/content?path=${encodeURIComponent(path)}&version=${encodeURIComponent(this.version)}`;
            const result = await this.explorer.ctx.http.upload<SaveResult>(query, snapshot.text, {
                contentType: 'text/plain; charset=utf-8'
            });
            if (this.path !== path) return false;

            this.version = result.version;
            this.size = result.size;
            this.savedAt = Date.now();
            // Edits made while the request was running stay marked as unsaved
            this.controller?.markSaved(snapshot);
            return true;
        } catch (error) {
            if (this.path === path) this.handleSaveError(error);
            return false;
        } finally {
            this.saving = false;
        }
    }

    dismissSaveError(): void {
        this.saveError = '';
    }

    /** Called by the editor view whenever the unsaved state changes. */
    onChange(dirty: boolean): void {
        this.dirty = dirty;
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
        this.status = 'loading';
        this.loadError = '';
        this.loadStatus = 0;
        this.dirty = false;
        this.saveError = '';
        this.conflict = null;
        this.savedAt = null;
        this.leave = null;

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
            this.session++;
            this.status = 'ready';
        } catch (error) {
            if (id !== this.requestId || this.disposed) return;
            this.loadError = describe(error);
            this.loadStatus = statusOf(error);
            this.status = 'error';
        }
    }

    private handleSaveError(error: unknown): void {
        const status = statusOf(error);
        if (status === 409) {
            this.conflict = /no longer exists/i.test(describe(error)) ? 'deleted' : 'changed';
        } else if (status === 413) {
            this.saveError = 'The text is larger than the editor limit. Shorten it, or upload the file instead.';
        } else {
            this.saveError = describe(error);
        }
    }

    private reset(): void {
        this.requestId++;
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
        this.leave = null;
        this.controller = null;
        this.entryPushed = false;
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
        if (!this.dirty) return;
        event.preventDefault();
        // Needed by some browsers to show the warning
        event.returnValue = '';
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