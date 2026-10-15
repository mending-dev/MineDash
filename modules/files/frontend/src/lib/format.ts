export function formatBytes(bytes: number, digits = 1): string {
    if (!Number.isFinite(bytes) || bytes < 0) return '—';
    const units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];
    let value = bytes;
    let unit = 0;
    while (value >= 1024 && unit < units.length - 1) {
        value /= 1024;
        unit++;
    }
    return `${value.toFixed(unit === 0 ? 0 : digits)} ${units[unit]}`;
}

// One formatter for all rows, creating it per call would be slow in long lists
const dateFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' });

export function formatModified(timestamp: number): string {
    if (!Number.isFinite(timestamp) || timestamp <= 0) return '—';
    return dateFormat.format(new Date(timestamp));
}

export function formatCount(count: number, singular: string, plural = `${singular}s`): string {
    return `${count} ${count === 1 ? singular : plural}`;
}