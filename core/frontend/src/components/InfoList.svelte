<script lang="ts">
  let { rows }: { rows: [string, string][] } = $props();
</script>

<dl class="grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-2 text-sm">
  {#each rows as [label, value] (label)}
    <dt class="text-muted">{label}</dt>
    <dd class="truncate text-right font-medium" title={value}>{value}</dd>
  {/each}
</dl>