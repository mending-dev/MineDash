<script lang="ts">
  import Card from '../components/Card.svelte';
  import Progress from '../components/Progress.svelte';
  import { overview } from '../lib/overview.svelte';

  const online = $derived(overview.latest?.players ?? null);
  const max = $derived(overview.live?.maxPlayers ?? 0);
  const ratio = $derived(online !== null && max > 0 ? online / max : 0);
</script>

<Card title="Players">
  <div class="flex items-baseline gap-1.5">
    <span class="text-3xl font-semibold tabular-nums">{online ?? '—'}</span>
    <span class="text-sm text-muted">/ {max > 0 ? max : '—'}</span>
  </div>
  <div class="mt-4">
    <Progress value={ratio} label="Player slots in use" />
  </div>
  <p class="mt-2 text-xs text-muted">{Math.round(ratio * 100)}% of slots in use</p>
</Card>