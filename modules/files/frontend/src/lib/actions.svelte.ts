import type { Explorer } from './explorer.svelte';
import { formatBytes, formatCount } from './format';
import { join } from './paths';
import type { Entry } from './types';
import { isZip, stripExtension, uniqueName, validateName, withZipExtension } from './validate';

interface BatchResult {
    done: string[];
    failed: Array<{ path: string; error: string }>;
}

interface ArchiveResult {
    entries: number;
    size: number;
    skipped: string[];
    skippedTotal: number;
}

interface UnzipResult {
    entries: number;
    bytes: number;
}

interface Report {
    title: string;
    summary: string;
    lines: string[];
    more: number;
}

/** What a finished action leaves behind. */
interface Outcome {
    /** Name to select once the list is reloaded. */
    select?: string;
    notice?: string;
    report?: Report;
}

/**
 * The open dialog. Every dialog remembers the folder it was opened in, so an action still hits the
 * right folder when the browser's back button changes the shown folder in the meantime.
 */
export type DialogState =
    | { kind: 'folder'; folder: string }
    | { kind: 'file'; folder: string }
    | { kind: 'rename'; folder: string; entry: Entry }
    | { kind: 'delete'; folder: string; names: string[] }
    | { kind: 'move'; folder: string; names: string[]; blocked: string[] }
    | { kind: 'zip'; folder: string; names: string[]; suggested: string }
    | { kind: 'unzip'; folder: string; entry: Entry; suggested: string }
    | ({ kind: 'report' } & Report);

type Of<K extends DialogState['kind']> = Extract<DialogState, { kind: K }>;

const MAX_REPORT_LINES = 50;

function describe(error: unknown): string {
    return error instanceof Error && error.message ? error.message : 'Something went wrong';
}

function baseName(path: string): string {
    return path.slice(path.lastIndexOf('/') + 1);
}

function isInside(path: string, folders: string[]): boolean {
    return folders.some((folder) => path === folder || path.startsWith(`${folder}/`));
}

export class Actions {
    readonly explorer: Explorer;

    dialog = $state.raw<DialogState | null>(null);

    /** Raised when an action and the reload after it are done, so the list can take the keyboard focus back. */
    focusRequest = $state(0);

    // What the toolbar may offer for the current selection
    canCreate = $derived(this.explorer.canWrite && this.explorer.loaded);
    canRename = $derived(this.explorer.canWrite && this.single !== null);
    canMove = $derived(this.explorer.canWrite && this.explorer.selectedEntries.length > 0);
    canArchive = $derived(this.explorer.canWrite && this.explorer.selectedEntries.length > 0);
    canUnzip = $derived(
        this.explorer.canWrite && this.single !== null && !this.single.directory && isZip(this.single.name)
    );
    canRemove = $derived(this.explorer.canDelete && this.explorer.selectedEntries.length > 0);

    private single = $derived(
        this.explorer.selectedEntries.length === 1 ? this.explorer.selectedEntries[0] : null
    );

    constructor(explorer: Explorer) {
        this.explorer = explorer;
    }

    // ---------------------------------------------------------------- opening dialogs

    openNewFolder(): void {
        if (this.canCreate) this.show({ kind: 'folder', folder: this.explorer.path });
    }

    openNewFile(): void {
        if (this.canCreate) this.show({ kind: 'file', folder: this.explorer.path });
    }

    openRename(): void {
        const entry = this.single;
        if (this.canRename && entry) this.show({ kind: 'rename', folder: this.explorer.path, entry });
    }

    openDelete(): void {
        if (this.canRemove) this.show({ kind: 'delete', folder: this.explorer.path, names: this.selectedNames() });
    }

    openMove(): void {
        if (!this.canMove) return;
        const folder = this.explorer.path;
        // A folder must not be moved into itself, so these cannot be chosen as the destination
        const blocked = this.explorer.selectedEntries
            .filter((entry) => entry.directory)
            .map((entry) => join(folder, entry.name));
        this.show({ kind: 'move', folder, names: this.selectedNames(), blocked });
    }

    openZip(): void {
        if (!this.canArchive) return;
        const entries = this.explorer.selectedEntries;
        const only = entries.length === 1 ? entries[0] : null;
        const base = only ? (only.directory ? only.name : stripExtension(only.name)) : 'archive';
        this.show({
            kind: 'zip',
            folder: this.explorer.path,
            names: this.selectedNames(),
            suggested: uniqueName(`${base}.zip`, this.takenNames())
        });
    }

    openUnzip(): void {
        const entry = this.single;
        if (!this.canUnzip || !entry) return;
        // The name ends with ".zip", which is 4 characters
        const base = entry.name.slice(0, -4) || 'extracted';
        this.show({
            kind: 'unzip',
            folder: this.explorer.path,
            entry,
            suggested: uniqueName(base, this.takenNames())
        });
    }

    close(): void {
        this.dialog = null;
    }

    // ---------------------------------------------------------------- name checks

    /** Error message for a new name, or null if it can be used. "current" is the name being renamed. */
    checkName(raw: string, current?: string): string | null {
        const name = raw.trim();
        const problem = validateName(name);
        if (problem) return problem;
        if (name !== current && this.explorer.entries.some((entry) => entry.name === name)) {
            return 'A file or folder with this name already exists';
        }
        return null;
    }

    checkZipName(raw: string): string | null {
        const name = raw.trim();
        return name === '' ? 'Enter a name' : this.checkName(withZipExtension(name));
    }

    // ---------------------------------------------------------------- actions

    createFolder(state: Of<'folder'>, raw: string): Promise<string | null> {
        return this.create(state.folder, '/folder', raw);
    }

    createFile(state: Of<'file'>, raw: string): Promise<string | null> {
        return this.create(state.folder, '/file', raw);
    }

    async rename(state: Of<'rename'>, raw: string): Promise<string | null> {
        const name = raw.trim();
        if (name === state.entry.name) {
            this.dialog = null;
            return null;
        }
        const problem = this.checkName(name, state.entry.name);
        if (problem) return problem;

        return this.perform(state.folder, async () => {
            await this.explorer.ctx.http.post('/rename', { path: join(state.folder, state.entry.name), name });
            return { select: name };
        });
    }

    remove(state: Of<'delete'>): Promise<string | null> {
        return this.perform(state.folder, async () => {
            const paths = state.names.map((name) => join(state.folder, name));
            const result = await this.explorer.ctx.http.post<BatchResult>('/delete', { paths });
            return this.batchOutcome(result, 'delete');
        });
    }

    async move(state: Of<'move'>, destination: string): Promise<string | null> {
        if (destination === state.folder) return 'The items are already in this folder';
        if (isInside(destination, state.blocked)) return 'A folder cannot be moved into itself';

        return this.perform(state.folder, async () => {
            const paths = state.names.map((name) => join(state.folder, name));
            const result = await this.explorer.ctx.http.post<BatchResult>('/move', { paths, destination });
            const target = destination === '' ? 'the server folder' : `"${baseName(destination)}"`;
            return this.batchOutcome(result, 'move', ` to ${target}`);
        });
    }

    async zip(state: Of<'zip'>, raw: string): Promise<string | null> {
        const name = withZipExtension(raw.trim());
        const problem = this.checkName(name);
        if (problem) return problem;

        return this.perform(state.folder, async () => {
            const paths = state.names.map((entry) => join(state.folder, entry));
            const result = await this.explorer.ctx.http.post<ArchiveResult>('/zip', {
                paths,
                target: join(state.folder, name)
            });

            const outcome: Outcome = {
                select: name,
                notice: `Created ${name} (${formatCount(result.entries, 'entry', 'entries')}, ${formatBytes(result.size)})`
            };
            if (result.skippedTotal > 0) {
                outcome.report = {
                    title: 'Some items were left out',
                    summary:
                        `${formatCount(result.skippedTotal, 'item')} could not be added to ${name}. ` +
                        'The list is also stored in the archive as MINEDASH-SKIPPED.txt.',
                    lines: result.skipped.slice(0, MAX_REPORT_LINES),
                    more: Math.max(0, result.skippedTotal - Math.min(result.skipped.length, MAX_REPORT_LINES))
                };
            }
            return outcome;
        });
    }

    async unzip(state: Of<'unzip'>, raw: string): Promise<string | null> {
        const name = raw.trim();
        const problem = validateName(name);
        if (problem) return problem;

        return this.perform(state.folder, async () => {
            const result = await this.explorer.ctx.http.post<UnzipResult>('/unzip', {
                path: join(state.folder, state.entry.name),
                destination: join(state.folder, name)
            });
            return {
                select: name,
                notice:
                    `Extracted ${formatCount(result.entries, 'entry', 'entries')} ` +
                    `(${formatBytes(result.bytes)}) into "${name}"`
            };
        });
    }

    // ---------------------------------------------------------------- helpers

    private async create(folder: string, route: string, raw: string): Promise<string | null> {
        const name = raw.trim();
        const problem = this.checkName(name);
        if (problem) return problem;

        return this.perform(folder, async () => {
            await this.explorer.ctx.http.post(route, { path: join(folder, name) });
            return { select: name };
        });
    }

    /**
     * Runs an action. Returns an error message, or null on success. On success the dialog is closed
     * (or replaced by a report) here, and the list is reloaded.
     */
    private async perform(folder: string, task: () => Promise<Outcome>): Promise<string | null> {
        let outcome: Outcome;
        try {
            outcome = await task();
        } catch (error) {
            return describe(error);
        }

        this.dialog = outcome.report ? { kind: 'report', ...outcome.report } : null;
        if (outcome.notice) this.explorer.notify('info', outcome.notice);
        void this.refresh(folder, outcome.select);
        return null;
    }

    private async refresh(folder: string, select?: string): Promise<void> {
        // The user may have left the folder while the action was running
        if (this.explorer.path !== folder) return;
        await this.explorer.open(folder, 'none');
        if (this.explorer.path !== folder) return;

        if (select !== undefined && this.explorer.entries.some((entry) => entry.name === select)) {
            this.explorer.selectOnly(select);
        }
        // The row that had the focus may be gone, so the list takes it back
        this.focusRequest++;
    }

    private batchOutcome(result: BatchResult, action: 'delete' | 'move', suffix = ''): Outcome {
        const done = result.done.length;
        const failed = result.failed.length;
        const past = action === 'delete' ? 'deleted' : 'moved';
        const notice =
            done > 0 ? `${action === 'delete' ? 'Deleted' : 'Moved'} ${formatCount(done, 'item')}${suffix}` : undefined;

        if (failed === 0) return { notice };
        return {
            notice,
            report: {
                title: done > 0 ? `Some items could not be ${past}` : `Nothing was ${past}`,
                summary:
                    done > 0
                        ? `${formatCount(done, 'item')} ${past}, ${failed} failed.`
                        : `${formatCount(failed, 'item')} could not be ${past}.`,
                lines: result.failed.slice(0, MAX_REPORT_LINES).map((item) => `${baseName(item.path)}: ${item.error}`),
                more: Math.max(0, failed - MAX_REPORT_LINES)
            }
        };
    }

    private show(state: DialogState): void {
        if (this.dialog === null) this.dialog = state;
    }

    private selectedNames(): string[] {
        return this.explorer.selectedEntries.map((entry) => entry.name);
    }

    private takenNames(): Set<string> {
        return new Set(this.explorer.entries.map((entry) => entry.name));
    }
}