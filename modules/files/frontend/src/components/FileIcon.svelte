<script lang="ts">
  let { directory, link = false }: { directory: boolean; link?: boolean } = $props();
</script>

<svg class="icon" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
  {#if directory}
    <path
      d="M3 6.5A1.5 1.5 0 0 1 4.5 5h4.2a1.5 1.5 0 0 1 1.06.44L11.3 7h8.2A1.5 1.5 0 0 1 21 8.5v9a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 17.5v-11Z"
      class="folder"
    />
  {:else}
    <path
      d="M7 3h6.5L19 8.5V19a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z"
      class="file"
    />
    <path d="M13 3v5.5h6" class="fold" />
  {/if}
  {#if link}
    <circle cx="18" cy="18" r="5" class="badge" />
    <path d="M16 20l4-4M17 16h3v3" class="arrow" />
  {/if}
</svg>

<style>
  .icon {
    flex: none;
    display: block;
  }
  .folder {
    fill: var(--md-accent);
    fill-opacity: 0.85;
  }
  .file {
    fill: var(--md-surface-2);
    stroke: var(--md-muted);
    stroke-width: 1.5;
    stroke-linejoin: round;
  }
  .fold {
    fill: none;
    stroke: var(--md-muted);
    stroke-width: 1.5;
    stroke-linejoin: round;
  }
  .badge {
    fill: var(--md-surface);
    stroke: var(--md-muted);
    stroke-width: 1.2;
  }
  .arrow {
    fill: none;
    stroke: var(--md-text);
    stroke-width: 1.5;
    stroke-linecap: round;
    stroke-linejoin: round;
  }
</style>