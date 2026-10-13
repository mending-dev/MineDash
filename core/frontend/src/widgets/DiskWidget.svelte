<script lang="ts">
  import Card from '../components/Card.svelte';
  import Progress from '../components/Progress.svelte';
  import { formatBytes } from '../lib/format';
  import { overview } from '../lib/overview.svelte';

  const total = $derived(overview.live?.diskTotal ?? 0);
  const free = $derived(overview.live?.diskFree ?? 0);
  const used = $derived(Math.max(0, total - free));
</script>

<Card title="Storage">
  {#if total > 0}
    <div class="flex items-baseline gap-1.5">
      <span class="text-3xl font-semibold tabular-nums">{formatBytes(used)}</span>
      <span class="text-sm text-muted">/ {formatBytes(total, 0)}</span>
    </div>
    <div class="mt-4">
      <Progress value={used / total} label="Disk space in use" />
    </div>
    <p class="mt-2 text-xs text-muted">{formatBytes(free)} free on the server drive</p>
  {:else}
    <div class="text-3xl font-semibold">—</div>
    <p class="mt-4 text-xs text-muted">{overview.live ? 'Not available on this system' : 'Waiting for data…'}</p>
  {/if}
</Card>