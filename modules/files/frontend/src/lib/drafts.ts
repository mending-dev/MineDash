/** An unsaved text that was kept for a file. */
export interface Draft {
    path: string;
    text: string;
    /** Hash of the file content the draft is based on. The server compares it when the draft is saved. */
    base: string;
    /** Milliseconds since 1970 when the draft was written. */
    at: number;
}

export type DraftResult = 'saved' | 'too-large' | 'failed';

const PREFIX = 'minedash.files.draft.v1.';
/** Longest text that is kept as a draft, in characters. Browser storage is small, about 5 MB per site. */
const MAX_DRAFT_CHARS = 400_000;
/** Total size of all drafts of one user, in characters of the stored records. */
const MAX_TOTAL_CHARS = 1_200_000;

interface Stored {
    key: string;
    size: number;
}

/** Null if the browser blocks the storage, e.g. in some privacy modes. */
function storage(): Storage | null {
    try {
        return window.sessionStorage;
    } catch {
        return null;
    }
}

function parse(raw: string): Draft | null {
    try {
        const data: unknown = JSON.parse(raw);
        if (typeof data !== 'object' || data === null) return null;
        const record = data as Record<string, unknown>;
        if (
            record.v !== 1 ||
            typeof record.path !== 'string' ||
            typeof record.text !== 'string' ||
            typeof record.base !== 'string' ||
            typeof record.at !== 'number'
        ) {
            return null;
        }
        return { path: record.path, text: record.text, base: record.base, at: record.at };
    } catch {
        return null;
    }
}

function tryStore(store: Storage, key: string, value: string): boolean {
    try {
        store.setItem(key, value);
        return true;
    } catch {
        // The storage is full or blocked
        return false;
    }
}

function removeKey(store: Storage, key: string): void {
    try {
        store.removeItem(key);
    } catch {
        // Nothing to do
    }
}

/**
 * Drafts of the files of one user. They live in the session storage of the browser tab, so they
 * survive page changes and reloads but disappear with the tab. Config files can contain
 * passwords, which therefore never stay in the browser for longer than the tab.
 */
export class Drafts {
    private readonly prefix: string;

    constructor(username: string) {
        // A "/" cannot appear in an encoded name, so the keys of two users never overlap
        this.prefix = `${PREFIX}${encodeURIComponent(username)}/`;
    }

    read(path: string): Draft | null {
        const store = storage();
        if (!store) return null;

        const key = this.key(path);
        let raw: string | null;
        try {
            raw = store.getItem(key);
        } catch {
            return null;
        }
        if (raw === null) return null;

        const draft = parse(raw);
        if (draft === null || draft.path !== path) {
            // Not a draft of this file, so it is of no use
            removeKey(store, key);
            return null;
        }
        return draft;
    }

    /** Keeps the text as the draft of the file. "base" is the version of the file the text builds on. */
    write(path: string, text: string, base: string): DraftResult {
        const store = storage();
        if (!store) return 'failed';

        const key = this.key(path);
        if (text.length > MAX_DRAFT_CHARS) {
            // An older, shorter draft of this file would be outdated now
            removeKey(store, key);
            return 'too-large';
        }

        const record = JSON.stringify({ v: 1, path, text, base, at: Date.now() });
        this.makeRoom(store, key, record.length);
        if (tryStore(store, key, record)) return 'saved';

        // The browser refused, so older drafts of other files make room one by one
        for (const entry of this.oldestFirst(store, this.others(store, key))) {
            removeKey(store, entry.key);
            if (tryStore(store, key, record)) return 'saved';
        }
        return 'failed';
    }

    remove(path: string): void {
        const store = storage();
        if (store) removeKey(store, this.key(path));
    }

    /**
     * Called when a text was saved while the editor was already gone. A draft with exactly this text is
     * no longer needed, a different one builds on the saved version from now on.
     */
    settle(path: string, savedText: string, version: string): void {
        const draft = this.read(path);
        if (draft === null) return;
        if (draft.text === savedText) this.remove(path);
        else this.write(path, draft.text, version);
    }

    // ---------------------------------------------------------------- helpers

    private key(path: string): string {
        return this.prefix + encodeURIComponent(path);
    }

    /** The other drafts of this user with the size of their stored record. */
    private others(store: Storage, exclude: string): Stored[] {
        const found: Stored[] = [];
        try {
            for (let i = 0; i < store.length; i++) {
                const key = store.key(i);
                if (key === null || key === exclude || !key.startsWith(this.prefix)) continue;
                found.push({ key, size: store.getItem(key)?.length ?? 0 });
            }
        } catch {
            // The storage cannot be read, so there is nothing to evict
        }
        return found;
    }

    /** Oldest first. Records that cannot be read count as the oldest, so they go first. */
    private oldestFirst(store: Storage, entries: Stored[]): Stored[] {
        const dated = entries.map((entry) => {
            let at = 0;
            try {
                at = parse(store.getItem(entry.key) ?? '')?.at ?? 0;
            } catch {
                at = 0;
            }
            return { entry, at };
        });
        dated.sort((a, b) => a.at - b.at);
        return dated.map((item) => item.entry);
    }

    /** Keeps the total size of all drafts of this user below the budget by dropping the oldest ones. */
    private makeRoom(store: Storage, key: string, size: number): void {
        const others = this.others(store, key);
        let total = others.reduce((sum, entry) => sum + entry.size, 0);
        if (total + size <= MAX_TOTAL_CHARS) return;

        for (const entry of this.oldestFirst(store, others)) {
            if (total + size <= MAX_TOTAL_CHARS) break;
            removeKey(store, entry.key);
            total -= entry.size;
        }
    }
}