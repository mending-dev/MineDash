<script lang="ts">
  import Card from '../components/Card.svelte';
  import InfoList from '../components/InfoList.svelte';
  import { formatBytes } from '../lib/format';
  import { overview } from '../lib/overview.svelte';

  const info = $derived(overview.info);

  const rows: [string, string][] = $derived([
    ['Operating system', info?.os.name ?? '—'],
    ['Version', info?.os.version || '—'],
    ['Architecture', info?.os.arch || '—'],
    ['Processors', info ? String(info.processors) : '—'],
    ['Physical memory', info && info.totalMemory > 0 ? formatBytes(info.totalMemory, 0) : '—'],
    ['Max heap', info ? formatBytes(info.maxHeap, 0) : '—'],
    ['Java', info ? `${info.java.version} (${info.java.vendor})` : '—'],
    ['JVM', info?.java.vm || '—'],
    ['Time zone', info?.timezone || '—']
  ]);
</script>

<Card title="System">
  <InfoList {rows} />
</Card>