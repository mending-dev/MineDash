<script lang="ts">
  import Card from '../components/Card.svelte';
  import Progress from '../components/Progress.svelte';
  import { overview } from '../lib/overview.svelte';

  const tps = $derived(overview.latest?.tps ?? -1);

  const state = $derived.by(() => {
    if (tps < 0) return { text: 'Measuring…', tone: 'accent' as const, color: 'text-muted' };
    if (tps === 0) return { text: 'Not responding', tone: 'danger' as const, color: 'text-danger' };
    if (tps >= 18) return { text: 'Running smoothly', tone: 'accent' as const, color: 'text-accent' };
    if (tps >= 15) return { text: 'Slightly lagging', tone: 'warning' as const, color: 'text-amber-500' };
    return { text: 'Lagging', tone: 'danger' as const, color: 'text-danger' };
  });
</script>

<Card title="TPS">
  <div class="flex items-baseline gap-1.5">
    <span class="text-3xl font-semibold tabular-nums">{tps >= 0 ? tps.toFixed(2) : '—'}</span>
    <span class="text-sm text-muted">/ 20</span>
  </div>
  <div class="mt-4">
    <Progress value={tps >= 0 ? tps / 20 : 0} tone={state.tone} label="Ticks per second" />
  </div>
  <p class="mt-2 text-xs {state.color}">{state.text}</p>
</Card>