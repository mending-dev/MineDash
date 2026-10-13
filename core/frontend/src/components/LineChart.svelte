<script lang="ts">
  import { buildPaths, VIEW_H, VIEW_W, yFor, type ChartSeries } from '../lib/chart';
  import { formatClock } from '../lib/format';

  let {
    times,
    series,
    max,
    windowMs,
    gapMs,
    format,
    label
  }: {
    times: number[];
    series: ChartSeries[];
    max: number;
    windowMs: number;
    gapMs: number;
    format: (value: number) => string;
    label: string;
  } = $props();

  const RATIOS = [0, 0.5, 1];

  let box = $state<HTMLDivElement>();
  let hover = $state<number | null>(null);

  const yMax = $derived(max > 0 ? max : 1);
  // The newest sample is always at the right edge
  const tEnd = $derived(times.length > 0 ? times[times.length - 1] : 0);
  const tStart = $derived(tEnd - windowMs);
  const paths = $derived(series.map((s) => buildPaths(times, s.values, tStart, windowMs, yMax, gapMs)));

  // The index can point past the end when samples shift while the pointer rests on the chart
  const hoverIndex = $derived(hover !== null && hover < times.length ? hover : null);
  const hoverPercent = $derived(hoverIndex === null ? 0 : ((times[hoverIndex] - tStart) / windowMs) * 100);

  const windowLabel = $derived(
    windowMs >= 60_000 ? `${Math.round(windowMs / 60_000)} min ago` : `${Math.round(windowMs / 1000)} s ago`
  );

  /** Index of the time closest to t (binary search). */
  function nearest(t: number): number {
    let low = 0;
    let high = times.length - 1;
    while (low < high) {
      const mid = (low + high) >> 1;
      if (times[mid] < t) low = mid + 1;
      else high = mid;
    }
    if (low > 0 && Math.abs(times[low - 1] - t) <= Math.abs(times[low] - t)) return low - 1;
    return low;
  }

  function onMove(event: PointerEvent) {
    if (!box || times.length === 0) return;
    const rect = box.getBoundingClientRect();
    if (rect.width === 0) return;
    const t = tStart + ((event.clientX - rect.left) / rect.width) * windowMs;
    const index = nearest(t);
    // No tooltip over a hole in the data
    hover = Math.abs(times[index] - t) <= gapMs ? index : null;
  }

  function valueText(s: ChartSeries, index: number): string {
    const v = s.values[index];
    return Number.isFinite(v) && v >= 0 ? format(v) : 'n/a';
  }
</script>

<div class="flex gap-2">
  <div
    class="flex h-36 w-14 shrink-0 flex-col-reverse justify-between text-right text-[10px] leading-none text-muted tabular-nums"
    aria-hidden="true"
  >
    {#each RATIOS as ratio (ratio)}
      <span>{format(ratio * yMax)}</span>
    {/each}
  </div>

  <div class="min-w-0 flex-1">
    <div
      bind:this={box}
      class="relative h-36 touch-pan-y"
      role="img"
      aria-label={label}
      onpointermove={onMove}
      onpointerdown={onMove}
      onpointerleave={() => (hover = null)}
    >
      <svg
        class="absolute inset-0 h-full w-full"
        viewBox="0 0 {VIEW_W} {VIEW_H}"
        preserveAspectRatio="none"
        aria-hidden="true"
      >
        {#each RATIOS as ratio (ratio)}
          <line
            x1="0"
            x2={VIEW_W}
            y1={yFor(ratio)}
            y2={yFor(ratio)}
            stroke="var(--md-border)"
            stroke-width="1"
            stroke-dasharray={ratio === 0 ? undefined : '4 4'}
            vector-effect="non-scaling-stroke"
          />
        {/each}
        {#each series as s, i (s.label)}
          {#if paths[i].area}
            <path d={paths[i].area} style="fill: {s.color}" fill-opacity="0.12" />
          {/if}
          {#if paths[i].line}
            <path
              d={paths[i].line}
              fill="none"
              style="stroke: {s.color}"
              stroke-width="2"
              stroke-linejoin="round"
              stroke-linecap="round"
              vector-effect="non-scaling-stroke"
            />
          {/if}
        {/each}
      </svg>

      {#if hoverIndex !== null}
        <div class="pointer-events-none absolute inset-y-0 w-px bg-muted/40" style="left: {hoverPercent}%"></div>
        <div
          class="pointer-events-none absolute top-1 z-10 rounded-md border border-line bg-surface px-2 py-1 text-xs whitespace-nowrap shadow-md {hoverPercent >
          60
            ? '-translate-x-[calc(100%+0.5rem)]'
            : 'translate-x-2'}"
          style="left: {hoverPercent}%"
        >
          <div class="text-muted">{formatClock(times[hoverIndex])}</div>
          {#each series as s (s.label)}
            <div class="flex items-center gap-1.5">
              <span class="h-2 w-2 rounded-full" style="background: {s.color}"></span>
              {s.label}: <span class="font-medium">{valueText(s, hoverIndex)}</span>
            </div>
          {/each}
        </div>
      {/if}
    </div>

    <div class="mt-1 flex items-center justify-between text-[10px] text-muted">
      <span>{windowLabel}</span>
      {#if series.length > 1}
        <span class="flex items-center gap-3">
          {#each series as s (s.label)}
            <span class="flex items-center gap-1">
              <span class="h-2 w-2 rounded-full" style="background: {s.color}"></span>{s.label}
            </span>
          {/each}
        </span>
      {/if}
      <span>now</span>
    </div>
  </div>
</div>