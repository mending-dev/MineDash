export type LanguageKind = 'json' | 'yaml' | 'properties' | 'toml' | 'xml' | 'shell';

// A Map, so names like "constructor" are never mistaken for a known extension
const EXTENSIONS = new Map<string, LanguageKind>([
    ['json', 'json'],
    ['mcmeta', 'json'],
    ['yml', 'yaml'],
    ['yaml', 'yaml'],
    ['properties', 'properties'],
    ['toml', 'toml'],
    ['xml', 'xml'],
    ['sh', 'shell'],
    ['bash', 'shell']
]);

const LABELS: Record<LanguageKind, string> = {
    json: 'JSON',
    yaml: 'YAML',
    properties: 'Properties',
    toml: 'TOML',
    xml: 'XML',
    shell: 'Shell'
};

export function languageKind(fileName: string): LanguageKind | null {
    const dot = fileName.lastIndexOf('.');
    if (dot < 0) return null;
    return EXTENSIONS.get(fileName.slice(dot + 1).toLowerCase()) ?? null;
}

export function languageLabel(fileName: string): string {
    const kind = languageKind(fileName);
    return kind === null ? 'Plain text' : LABELS[kind];
}