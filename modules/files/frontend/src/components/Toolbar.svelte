<script lang="ts">
  import type { Explorer } from '../lib/explorer.svelte';
  import { formatBytes, formatCount } from '../lib/format';
  import { crumbs } from '../lib/paths';
  import Breadcrumbs from './Breadcrumbs.svelte';

  let { explorer }: { explorer: Explorer } = $props();

  const trail = $derived(crumbs(explorer.path, 'Server'));
  const count = $derived(explorer.selection.size);
  const summary = $derived(
    count === 0
      ? ''
      : explorer.selectedBytes > 0
        ? `${formatCount(count, 'item')} selected · ${formatBytes(explorer.selectedBytes)}`
        : `${formatCount(count, 'item')} selected`
  );
</script>

<div class="toolbar">
  <div class="row">
    <button
      type="button"
      class="mdf-btn icon"
      title="Up one folder"
      aria-label="Up one folder"
      disabled={explorer.path === ''}
      onclick={() => explorer.up()}
    >
      <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
        <path d="M12 19V5M5 12l7-7 7 7" />
      </svg>
    </button>

    <Breadcrumbs {trail} onnavigate={(path) => void explorer.open(path)} />

    <button
      type="button"
      class="mdf-btn icon"
      title="Refresh"
      aria-label="Refresh"
      disabled={explorer.busy}
      onclick={() => explorer.reload()}
    >
      <svg class:spin={explorer.busy} viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
        <path d="M20 11A8 8 0 0 0 5.6 6.3L4 8M4 4v4h4M4 13a8 8 0 0 0 14.4 4.7L20 16m0 4v-4h-4" />
      </svg>
    </button>
  </div>

  <div class="row actions">
    <button type="button" class="mdf-btn" disabled={count === 0} onclick={() => explorer.downloadSelected()}>
      <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
        <path d="M12 4v11m0 0-4-4m4 4 4-4M5 20h14" />
      </svg>
      Download
    </button>

    <span class="spacer"></span>

    {#if count > 0}
      <span class="summary" role="status">{summary}</span>
      <button type="button" class="mdf-btn ghost" onclick={() => explorer.clearSelection()}>Clear</button>
    {/if}
  </div>
</div>

<style>
  .toolbar {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
    padding: 0.5rem 0.75rem;
    border-bottom: 1px solid var(--md-border);
  }
  .row {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    min-width: 0;
  }
  .actions {
    min-height: 2.125rem;
  }
  .spacer {
    flex: 1;
  }
  .summary {
    color: var(--md-muted);
    font-size: 0.8125rem;
  }
  svg {
    fill: none;
    stroke: currentColor;
    stroke-width: 2;
    stroke-linecap: round;
    stroke-linejoin: round;
  }
  .spin {
    animation: mdf-spin 0.9s linear infinite;
  }
  @keyframes mdf-spin {
    to {
      transform: rotate(360deg);
    }
  }
</style>