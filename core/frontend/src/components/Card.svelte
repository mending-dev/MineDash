<script lang="ts">
  import type { Snippet } from 'svelte';

  let { title, children }: { title: string; children: Snippet } = $props();
</script>

<section class="flex h-full min-h-28 flex-col rounded-xl border border-line bg-surface p-4">
  <h3 class="mb-3 text-xs font-medium tracking-wider text-muted uppercase">{title}</h3>
  <div class="min-h-0 flex-1">
    {@render children()}
  </div>
</section>