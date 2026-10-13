<script lang="ts">
  import Card from '../components/Card.svelte';
  import LineChart from '../components/LineChart.svelte';
  import { formatPercent } from '../lib/format';
  import { overview } from '../lib/overview.svelte';

  const times = $derived(overview.samples.map((s) => s.t));

  // Series without any data (unsupported JVM) are left out
  const series = $derived(
    [
      { label: 'Server', color: 'var(--md-accent)', values: overview.samples.map((s) => s.cpu) },
      { label: 'System', color: 'var(--md-info)', values: overview.samples.map((s) => s.systemCpu) }
    ].filter((s) => s.values.some((v) => v >= 0))
  );

  const latest = $derived(overview.latest);
</script>

<Card title="CPU usage">
  <div class="mb-3 flex flex-wrap items-baseline gap-x-6 gap-y-1">
    <div>
      <span class="text-3xl font-semibold tabular-nums">{formatPercent(latest?.cpu)}</span>
      <span class="ml-1 text-sm text-muted">server</span>
    </div>
    <div class="text-sm text-muted">
      <span class="font-medium text-fg tabular-nums">{formatPercent(latest?.systemCpu)}</span> system
    </div>
  </div>

  {#if series.length > 0}
    <LineChart
      {times}
      {series}
      max={100}
      windowMs={overview.windowMs}
      gapMs={overview.gapMs}
      format={(v) => `${Math.round(v)}%`}
      label="CPU usage over the last minutes"
    />
  {:else}
    <p class="text-sm text-muted">
      {overview.samples.length === 0 ? 'Collecting data…' : 'This Java runtime cannot report CPU usage.'}
    </p>
  {/if}
</Card>