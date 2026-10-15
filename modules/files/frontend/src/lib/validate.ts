const MAX_NAME_LENGTH = 255;

/**
 * Quick check of a file or folder name for instant feedback, expects an already trimmed name.
 * The server applies the real rules and stays the authority.
 */
export function validateName(name: string): string | null {
    if (name === '') return 'Enter a name';
    if (name === '.' || name === '..') return `"${name}" is not a valid name`;
    if (name.length > MAX_NAME_LENGTH) return 'The name is too long';
    if (name.includes('/') || name.includes('\\')) return 'The name must not contain / or \\';
    for (const char of name) {
        const code = char.codePointAt(0) ?? 0;
        if (code < 0x20 || code === 0x7f) return 'The name contains an invalid character';
    }
    return null;
}

/** "archive.tar.gz" becomes "archive.tar". A leading dot is not an extension. */
export function stripExtension(name: string): string {
    const dot = name.lastIndexOf('.');
    return dot > 0 ? name.slice(0, dot) : name;
}

export function isZip(name: string): boolean {
    return name.toLowerCase().endsWith('.zip');
}

export function withZipExtension(name: string): string {
    return isZip(name) ? name : `${name}.zip`;
}

/** Adds " (2)", " (3)" and so on before the extension until the name is not taken. */
export function uniqueName(name: string, taken: ReadonlySet<string>): string {
    if (!taken.has(name)) return name;
    const dot = name.lastIndexOf('.');
    const stem = dot > 0 ? name.slice(0, dot) : name;
    const extension = dot > 0 ? name.slice(dot) : '';
    for (let i = 2; ; i++) {
        const candidate = `${stem} (${i})${extension}`;
        if (!taken.has(candidate)) return candidate;
    }
}

/** Part of a name that is selected when it is edited: everything except the extension of files. */
export function baseSelection(name: string, directory: boolean): [number, number] {
    const dot = name.lastIndexOf('.');
    return [0, directory || dot <= 0 ? name.length : dot];
}