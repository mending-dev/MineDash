/** Paths are relative to the managed root and separated by "/". The root itself is "". */

export function join(directory: string, name: string): string {
    return directory === '' ? name : `${directory}/${name}`;
}

/** The folder that contains the given path. The parent of the root is the root. */
export function parent(path: string): string {
    const index = path.lastIndexOf('/');
    return index < 0 ? '' : path.slice(0, index);
}

export interface Crumb {
    name: string;
    path: string;
}

/** The parts of a path for the breadcrumb bar, starting with the root. */
export function crumbs(path: string, rootLabel: string): Crumb[] {
    const result: Crumb[] = [{ name: rootLabel, path: '' }];
    let current = '';
    for (const part of path.split('/')) {
        if (part === '') continue;
        current = join(current, part);
        result.push({ name: part, path: current });
    }
    return result;
}