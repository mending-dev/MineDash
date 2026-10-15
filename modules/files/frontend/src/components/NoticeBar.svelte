<script lang="ts">
  import type { Explorer } from '../lib/explorer.svelte';

  let { explorer }: { explorer: Explorer } = $props();
</script>

{#if explorer.notice}
  <div class="banner {explorer.notice.kind}" role={explorer.notice.kind === 'error' ? 'alert' : 'status'}>
    <p>{explorer.notice.text}</p>
    <button type="button" class="mdf-btn ghost" onclick={() => explorer.dismissNotice()}>Dismiss</button>
  </div>
{/if}

<style>
  .banner {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    padding: 0.5rem 0.75rem;
    border-bottom: 1px solid var(--md-border);
    font-size: 0.8125rem;
  }
  .banner p {
    flex: 1;
    min-width: 0;
    margin: 0;
    overflow-wrap: anywhere;
  }
  .banner.error {
    color: var(--md-danger);
    background: color-mix(in srgb, var(--md-danger) 10%, transparent);
  }
  .banner.info {
    color: var(--md-info);
    background: color-mix(in srgb, var(--md-info) 10%, transparent);
  }
</style>