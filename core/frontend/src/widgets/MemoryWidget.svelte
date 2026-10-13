<script lang="ts">
  import Card from '../components/Card.svelte';
  import LineChart from '../components/LineChart.svelte';
  import Progress from '../components/Progress.svelte';
  import { formatBytes } from '../lib/format';
  import { overview } from '../lib/overview.svelte';

  const used = $derived(overview.latest?.heapUsed ?? 0);
  const max = $derived(overview.live?.heapMax ?? 0);
  const committed = $derived(overview.live?.heapCommitted ?? 0);
  const ratio = $derived(max > 0 ? used / max : 0);

  const times = $derived(overview.samples.map((s) => s.t));
  const series = $derived([
    { label: 'Used', color: 'var(--md-accent)', values: overview.samples.map((s) => s.heapUsed) }
  ]);
</script>

<Card title="Memory">
  <div class="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
    <div>
      <span class="text-3xl font-semibold tabular-nums">{overview.latest ? formatBytes(used) : '—'}</span>
      <span class="ml-1 text-sm text-muted">/ {max > 0 ? formatBytes(max, 0) : '—'}</span>
    </div>
    <span class="text-sm text-muted tabular-nums">{Math.round(ratio * 100)}% used</span>
  </div>

  <div class="mt-3">
    <Progress value={ratio} label="Heap memory in use" />
  </div>
  <p class="mt-2 mb-4 text-xs text-muted">
    Allocated {committed > 0 ? formatBytes(committed, 0) : '—'} · Free {max > 0 ? formatBytes(Math.max(0, max - used)) : '—'}
  </p>

  <LineChart
    {times}
    {series}
    max={max > 0 ? max : Math.max(1, ...overview.samples.map((s) => s.heapUsed))}
    windowMs={overview.windowMs}
    gapMs={overview.gapMs}
    format={(v) => formatBytes(v, 0)}
    label="Memory usage over the last minutes"
  />
</Card>