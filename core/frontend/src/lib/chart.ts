export interface ChartSeries {
    label: string;
    color: string;
    /** One value per time entry. Negative or non-finite values are drawn as gaps. */
    values: number[];
}

/** Internal coordinate system of the SVG. It is stretched to the real size. */
export const VIEW_W = 600;
export const VIEW_H = 150;
const PAD = 3;

/** Y coordinate for a value between 0 and 1. */
export function yFor(ratio: number): number {
    const clamped = Math.min(1, Math.max(0, ratio));
    return VIEW_H - PAD - clamped * (VIEW_H - PAD * 2);
}

export interface SeriesPaths {
    line: string;
    area: string;
}

const f = (n: number): string => n.toFixed(1);

/**
 * Builds SVG path data for one series. The line is interrupted wherever a value is
 * missing or two samples are further apart than gapMs, so holes are never bridged.
 */
export function buildPaths(
    times: number[],
    values: number[],
    tStart: number,
    windowMs: number,
    yMax: number,
    gapMs: number
): SeriesPaths {
    const lines: string[] = [];
    const areas: string[] = [];
    let segment: Array<[number, number]> = [];

    const flush = () => {
        if (segment.length === 1) {
            // A lone point becomes a dot (zero length line with round caps)
            const [x, y] = segment[0];
            lines.push(`M${f(x)} ${f(y)}L${f(x)} ${f(y)}`);
        } else if (segment.length > 1) {
            const points = segment.map(([x, y]) => `${f(x)} ${f(y)}`).join('L');
            lines.push(`M${points}`);
            const first = segment[0];
            const last = segment[segment.length - 1];
            areas.push(`M${f(first[0])} ${VIEW_H}L${points}L${f(last[0])} ${VIEW_H}Z`);
        }
        segment = [];
    };

    let previous = -Infinity;
    for (let i = 0; i < times.length; i++) {
        const t = times[i];
        const v = values[i];
        if (!Number.isFinite(v) || v < 0 || t < tStart - gapMs) {
            flush();
            previous = -Infinity;
            continue;
        }
        if (t - previous > gapMs) flush();
        segment.push([((t - tStart) / windowMs) * VIEW_W, yFor(v / yMax)]);
        previous = t;
    }
    flush();

    return { line: lines.join(''), area: areas.join('') };
}