<script lang="ts">
  import Card from '../components/Card.svelte';
  import InfoList from '../components/InfoList.svelte';
  import { overview } from '../lib/overview.svelte';

  const info = $derived(overview.info);

  const rows: [string, string][] = $derived([
    ['Software', info?.software ?? '—'],
    ['Version', info?.minecraftVersion ?? '—'],
    ['Port', info ? String(info.port) : '—'],
    ['Online mode', info ? (info.onlineMode ? 'Enabled' : 'Disabled') : '—'],
    ['Plugins', overview.live ? String(overview.live.plugins) : '—'],
    ['Build', info?.build ?? '—']
  ]);
</script>

<Card title="Server">
  <InfoList {rows} />
</Card>