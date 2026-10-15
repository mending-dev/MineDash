/** One item of a folder, as sent by GET /list. */
export interface Entry {
    name: string;
    directory: boolean;
    /** True if the item is a symbolic link. "directory" and "size" describe its target. */
    link: boolean;
    size: number;
    /** Milliseconds since 1970. */
    modified: number;
}

export interface Listing {
    /** Folder path relative to the managed root, separated by "/". The root itself is "". */
    path: string;
    entries: Entry[];
    /** True if the folder has more items than the server is willing to list. */
    truncated: boolean;
}

export type SortKey = 'name' | 'size' | 'modified';
export type SortDirection = 'asc' | 'desc';