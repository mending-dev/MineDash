<script lang="ts">
  import { onMount } from 'svelte';
  import Card from '../components/Card.svelte';
  import { formatDateTime, formatDuration } from '../lib/format';
  import { overview } from '../lib/overview.svelte';

  // Re-evaluates the uptime once per second. The server value is only fetched every few seconds.
  let tick = $state(0);

  onMount(() => {
    const id = window.setInterval(() => tick++, 1000);
    return () => window.clearInterval(id);
  });

  const uptime = $derived.by(() => {
    void tick;
    return overview.fetchedAt > 0 ? formatDuration(overview.uptimeNow()) : '—';
  });
</script>

<Card title="Uptime">
  <div class="text-3xl font-semibold tabular-nums">{uptime}</div>
  <p class="mt-4 text-xs text-muted">
    {overview.startedAt !== null ? `Started ${formatDateTime(overview.startedAt)}` : 'Waiting for data…'}
  </p>
</Card>