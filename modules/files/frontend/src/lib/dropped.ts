export interface DroppedFiles {
    files: File[];
    /** Number of dropped folders. They cannot be uploaded. */
    folders: number;
}

/**
 * Reads the files of a drop. Must run inside the drop event, afterwards the browser invalidates the list.
 * Folders are counted instead of read, because a folder looks like an empty file to the File API.
 */
export function collectFiles(data: DataTransfer): DroppedFiles {
    const files: File[] = [];
    let folders = 0;

    const items = data.items;
    if (items && items.length > 0) {
        for (let i = 0; i < items.length; i++) {
            const item = items[i];
            if (item.kind !== 'file') continue;

            const entry = typeof item.webkitGetAsEntry === 'function' ? item.webkitGetAsEntry() : null;
            if (entry && entry.isDirectory) {
                folders++;
                continue;
            }
            const file = item.getAsFile();
            if (file) files.push(file);
        }
        return { files, folders };
    }

    // Old browsers without the items list
    return { files: Array.from(data.files), folders: 0 };
}