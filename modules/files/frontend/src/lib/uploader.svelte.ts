import type { Explorer } from './explorer.svelte';
import { formatBytes, formatCount } from './format';
import { join } from './paths';
import { validateName } from './validate';

/** Files uploaded at the same time. More would only compete for the same bandwidth and disk. */
const CONCURRENCY = 2;
/** Most files accepted in one go, so the list and the totals stay fast. */
const MAX_BATCH = 1000;

const FOLDER_HINT =
    'Folders cannot be uploaded. Create a ZIP archive of the folder, upload that and extract it here.';

export type UploadStatus = 'queued' | 'uploading' | 'done' | 'failed' | 'cancelled';
export type ConflictChoice = 'replace' | 'skip' | 'cancel';

function describe(error: unknown): string {
    return error instanceof Error && error.message ? error.message : 'The upload failed';
}

function statusOf(error: unknown): number {
    const status = (error as { status?: unknown } | null)?.status;
    return typeof status === 'number' ? status : 0;
}

/** A module is a separate bundle, so errors are recognised by their name and not by their class. */
function isAbort(error: unknown): boolean {
    return (error as { name?: unknown } | null)?.name === 'AbortError';
}

export class UploadJob {
    readonly id: number;
    readonly file: File;
    readonly name: string;
    readonly folder: string;
    readonly size: number;

    overwrite = $state(false);
    status = $state<UploadStatus>('queued');
    loaded = $state(0);
    error = $state('');
    /** True if the server refused because a file with this name exists. Then "Replace" is offered. */
    conflict = $state(false);
    /** False for files that were rejected up front, trying again would not change anything. */
    retryable = $state(true);

    /** Set while the upload runs. It is only used to cancel, so it is not reactive. */
    controller: AbortController | null = null;

    constructor(id: number, file: File, folder: string, overwrite: boolean) {
        this.id = id;
        this.file = file;
        this.name = file.name;
        this.folder = folder;
        this.size = file.size;
        this.overwrite = overwrite;
    }
}

/** Files that wait for the decision what to do with the ones that already exist. */
export interface PendingBatch {
    folder: string;
    /** Files that passed the checks. */
    files: File[];
    /** Names of the files that already exist in the folder. */
    conflicts: string[];
    /** Files that were rejected up front. */
    failed: Array<{ file: File; error: string }>;
}

export class Uploader {
    readonly explorer: Explorer;

    jobs = $state.raw<UploadJob[]>([]);
    pending = $state.raw<PendingBatch | null>(null);

    queued = $derived(this.jobs.filter((job) => job.status === 'queued').length);
    uploading = $derived(this.jobs.filter((job) => job.status === 'uploading').length);
    done = $derived(this.jobs.filter((job) => job.status === 'done').length);
    failed = $derived(this.jobs.filter((job) => job.status === 'failed').length);
    cancelled = $derived(this.jobs.filter((job) => job.status === 'cancelled').length);

    active = $derived(this.queued + this.uploading > 0);
    /** True if there is something to clear. */
    finished = $derived(this.done + this.failed + this.cancelled > 0);

    // Failed and cancelled files do not count, they are not going to be sent
    private counted = $derived(
        this.jobs.filter((job) => job.status === 'queued' || job.status === 'uploading' || job.status === 'done')
    );
    totalBytes = $derived(this.counted.reduce((sum, job) => sum + job.size, 0));
    loadedBytes = $derived(
        this.counted.reduce(
            (sum, job) => sum + (job.status === 'done' ? job.size : job.status === 'uploading' ? job.loaded : 0),
            0
        )
    );
    percent = $derived(
        this.totalBytes === 0 ? 0 : Math.min(100, Math.floor((this.loadedBytes / this.totalBytes) * 100))
    );

    private maxBytes = 0;
    private running = 0;
    private nextId = 0;
    private disposed = false;

    constructor(explorer: Explorer) {
        this.explorer = explorer;
    }

    /** Loads the size limit and guards against closing the tab during an upload. Returns the cleanup function. */
    start(): () => void {
        void this.loadLimit();
        window.addEventListener('beforeunload', this.onBeforeUnload);
        return () => {
            this.disposed = true;
            window.removeEventListener('beforeunload', this.onBeforeUnload);
        };
    }

    // ---------------------------------------------------------------- adding files

    /** Adds files to the shown folder. "skippedFolders" is the number of dropped folders that were left out. */
    add(files: File[], skippedFolders = 0): void {
        const explorer = this.explorer;
        if (!explorer.canWrite || !explorer.loaded || this.pending) return;

        if (skippedFolders > 0) {
            explorer.notify('error', FOLDER_HINT);
        }
        if (files.length === 0) return;
        if (files.length > MAX_BATCH) {
            explorer.notify(
                'error',
                `Too many files at once, the limit is ${MAX_BATCH}. Create a ZIP archive and extract it on the server instead.`
            );
            return;
        }

        const folder = explorer.path;
        const existing = new Map(explorer.entries.map((entry) => [entry.name, entry.directory]));
        const seen = new Set<string>();
        const valid: File[] = [];
        const conflicts: string[] = [];
        const failed: Array<{ file: File; error: string }> = [];

        for (const file of files) {
            let problem = validateName(file.name) ?? this.sizeProblem(file);
            if (problem === null && seen.has(file.name)) {
                problem = 'Another file with this name is part of the same upload';
            }
            const isDirectory = existing.get(file.name);
            if (problem === null && isDirectory === true) {
                problem = 'A folder with this name already exists';
            }
            if (problem !== null) {
                failed.push({ file, error: problem });
                continue;
            }

            seen.add(file.name);
            if (isDirectory === false) conflicts.push(file.name);
            valid.push(file);
        }

        const batch: PendingBatch = { folder, files: valid, conflicts, failed };
        if (conflicts.length > 0) {
            // The dialog asks what to do with the files that exist
            this.pending = batch;
            return;
        }
        this.enqueue(batch, 'skip');
    }

    /** Answer to the question about files that already exist. */
    resolve(choice: ConflictChoice): void {
        const batch = this.pending;
        if (!batch) return;
        this.pending = null;
        if (choice === 'cancel') return;
        this.enqueue(batch, choice);
    }

    // ---------------------------------------------------------------- controlling jobs

    cancel(job: UploadJob): void {
        if (job.status === 'queued') {
            job.status = 'cancelled';
            this.pump();
        } else if (job.status === 'uploading') {
            // run() sets the status when the abort arrives
            job.controller?.abort();
        }
    }

    cancelAll(): void {
        for (const job of this.jobs) {
            if (job.status === 'queued') job.status = 'cancelled';
            else if (job.status === 'uploading') job.controller?.abort();
        }
        this.pump();
    }

    retry(job: UploadJob, overwrite: boolean = job.overwrite): void {
        if (job.status !== 'failed' && job.status !== 'cancelled') return;
        job.overwrite = overwrite;
        job.loaded = 0;
        job.error = '';
        job.conflict = false;
        job.status = 'queued';
        this.pump();
    }

    remove(job: UploadJob): void {
        if (job.status === 'queued' || job.status === 'uploading') return;
        this.jobs = this.jobs.filter((other) => other !== job);
    }

    clearFinished(): void {
        this.jobs = this.jobs.filter((job) => job.status === 'queued' || job.status === 'uploading');
    }

    // ---------------------------------------------------------------- internals

    private enqueue(batch: PendingBatch, choice: 'replace' | 'skip'): void {
        const conflicts = new Set(batch.conflicts);
        const queued: UploadJob[] = [];
        const rejected: UploadJob[] = [];

        for (const file of batch.files) {
            const exists = conflicts.has(file.name);
            if (exists && choice === 'skip') continue;
            queued.push(new UploadJob(++this.nextId, file, batch.folder, exists && choice === 'replace'));
        }
        for (const { file, error } of batch.failed) {
            const job = new UploadJob(++this.nextId, file, batch.folder, false);
            job.status = 'failed';
            job.error = error;
            job.retryable = false;
            rejected.push(job);
        }

        if (queued.length === 0 && rejected.length === 0) return;
        this.jobs = this.jobs.concat(queued, rejected);
        this.pump();
    }

    /** Starts waiting uploads until the limit is reached, and wraps up when everything is finished. */
    private pump(): void {
        if (this.disposed) return;

        while (this.running < CONCURRENCY) {
            const next = this.jobs.find((job) => job.status === 'queued');
            if (!next) break;
            // The first part of run() is synchronous and marks the job as uploading, so the loop moves on
            void this.run(next);
        }

        if (this.running === 0 && !this.jobs.some((job) => job.status === 'queued')) {
            void this.finish();
        }
    }

    private async run(job: UploadJob): Promise<void> {
        this.running++;
        const controller = new AbortController();
        job.controller = controller;
        job.loaded = 0;
        job.status = 'uploading';

        try {
            const query = `/upload?path=${encodeURIComponent(join(job.folder, job.name))}&overwrite=${job.overwrite}`;
            await this.explorer.ctx.http.upload(query, job.file, {
                contentType: 'application/octet-stream',
                signal: controller.signal,
                onProgress: (loaded) => {
                    job.loaded = Math.min(loaded, job.size);
                }
            });
            job.loaded = job.size;
            job.status = 'done';
        } catch (error) {
            if (isAbort(error)) {
                job.status = 'cancelled';
            } else {
                job.error = describe(error);
                job.conflict = statusOf(error) === 409 && /file with this name/i.test(job.error);
                job.status = 'failed';
            }
        } finally {
            job.controller = null;
            this.running--;
            this.pump();
        }
    }

    /** Called when nothing is running or waiting: reports the result and refreshes the list. */
    private async finish(): Promise<void> {
        const done = this.jobs.filter((job) => job.status === 'done');
        if (done.length === 0 || this.disposed) return;

        // Finished uploads leave the list, failed and cancelled ones stay so they can be tried again
        this.jobs = this.jobs.filter((job) => job.status !== 'done');
        const failed = this.jobs.filter((job) => job.status === 'failed').length;
        const bytes = done.reduce((sum, job) => sum + job.size, 0);
        this.explorer.notify(
            failed > 0 ? 'error' : 'info',
            `Uploaded ${formatCount(done.length, 'file')} (${formatBytes(bytes)})` + (failed > 0 ? `, ${failed} failed` : '')
        );

        const here = this.explorer.path;
        const names = done.filter((job) => job.folder === here).map((job) => job.name);
        if (names.length === 0) return;

        await this.explorer.open(here, 'none');
        if (this.disposed || this.explorer.path !== here) return;

        // Show what arrived
        const present = new Set(this.explorer.entries.map((entry) => entry.name));
        this.explorer.selection = new Set(names.filter((name) => present.has(name)));
    }

    private sizeProblem(file: File): string | null {
        if (this.maxBytes > 0 && file.size > this.maxBytes) {
            return `Too large (the limit is ${formatBytes(this.maxBytes, 0)})`;
        }
        return null;
    }

    private async loadLimit(): Promise<void> {
        try {
            const info = await this.explorer.ctx.http.get<{ maxUploadBytes?: number }>('/info');
            if (typeof info.maxUploadBytes === 'number' && info.maxUploadBytes > 0) {
                this.maxBytes = info.maxUploadBytes;
            }
        } catch {
            // Without the limit the server still enforces it, the file is just sent first
        }
    }

    private onBeforeUnload = (event: BeforeUnloadEvent): void => {
        if (!this.active) return;
        event.preventDefault();
        // Needed by some browsers to show the warning
        event.returnValue = '';
    };
}