<script lang="ts">
  import type { Actions } from '../lib/actions.svelte';
  import type { Explorer } from '../lib/explorer.svelte';
  import { formatBytes, formatCount } from '../lib/format';
  import { crumbs } from '../lib/paths';
  import Breadcrumbs from './Breadcrumbs.svelte';

  let {
    explorer,
    actions,
    onupload
  }: {
    explorer: Explorer;
    actions: Actions;
    /** Opens the file picker. */
    onupload: () => void;
  } = $props();

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

{#snippet icon(d: string)}
  <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"><path {d} /></svg>
{/snippet}

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
    {#if explorer.canWrite}
      <button type="button" class="mdf-btn" title="Upload files" disabled={!actions.canCreate} onclick={onupload}>
        {@render icon('M12 16V5m0 0-4 4m4-4 4 4M5 20h14')}
        <span class="label">Upload</span>
      </button>
      <button type="button" class="mdf-btn" title="New folder" disabled={!actions.canCreate} onclick={() => actions.openNewFolder()}>
        {@render icon('M3 6.5A1.5 1.5 0 0 1 4.5 5h4l2 2h9A1.5 1.5 0 0 1 21 8.5v9a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 17.5v-11ZM12 10.5v5M9.5 13h5')}
        <span class="label">New folder</span>
      </button>
      <button type="button" class="mdf-btn" title="New file" disabled={!actions.canCreate} onclick={() => actions.openNewFile()}>
        {@render icon('M7 3h6.5L19 8.5V19a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2ZM13 3v5.5h6M12 12v5M9.5 14.5h5')}
        <span class="label">New file</span>
      </button>
      <span class="divider" aria-hidden="true"></span>
    {/if}

    <button type="button" class="mdf-btn" title="Download" disabled={count === 0} onclick={() => explorer.downloadSelected()}>
      {@render icon('M12 4v11m0 0-4-4m4 4 4-4M5 20h14')}
      <span class="label">Download</span>
    </button>

    {#if explorer.canWrite}
      <button type="button" class="mdf-btn" title="Rename (F2)" disabled={!actions.canRename} onclick={() => actions.openRename()}>
        {@render icon('M4 20h4L19 9l-4-4L4 16v4ZM13.5 6.5l4 4')}
        <span class="label">Rename</span>
      </button>
      <button type="button" class="mdf-btn" title="Move" disabled={!actions.canMove} onclick={() => actions.openMove()}>
        {@render icon('M3 6.5A1.5 1.5 0 0 1 4.5 5h4l2 2h9A1.5 1.5 0 0 1 21 8.5v9a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 17.5v-11ZM8.5 13h7m-3-3 3 3-3 3')}
        <span class="label">Move</span>
      </button>
      <button type="button" class="mdf-btn" title="Create ZIP archive" disabled={!actions.canArchive} onclick={() => actions.openZip()}>
        {@render icon('M4 7h16v3H4zM5 10v8a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-8M10 14h4')}
        <span class="label">ZIP</span>
      </button>
      {#if actions.canUnzip}
        <button type="button" class="mdf-btn" title="Extract archive" onclick={() => actions.openUnzip()}>
          {@render icon('M4 7h16v3H4zM5 10v8a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-8M12 17v-5m-2.5 2.5L12 17l2.5-2.5')}
          <span class="label">Extract</span>
        </button>
      {/if}
    {/if}

    <span class="spacer"></span>

    <!-- Everything below only shows up while something is selected -->
    {#if count > 0}
      <span class="summary" role="status">
        {summary}
        {#if !explorer.canDelete}
          · deleting needs the permission <code>files.delete</code>
        {/if}
      </span>

      {#if explorer.canDelete}
        <button
          type="button"
          class="mdf-btn delete"
          title="Delete (Del)"
          aria-keyshortcuts="Delete"
          disabled={!actions.canRemove}
          onclick={() => actions.openDelete()}
        >
          {@render icon('M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v6M14 11v6')}
          <span class="label">Delete</span>
        </button>
      {/if}

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
    flex-wrap: wrap;
    min-height: 2.125rem;
  }
  .spacer {
    flex: 1;
  }
  .divider {
    align-self: stretch;
    width: 1px;
    margin: 0.25rem 0.125rem;
    background: var(--md-border);
  }
  .summary {
    color: var(--md-muted);
    font-size: 0.8125rem;
  }
  code {
    font-size: 0.75rem;
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

  :global(.mdf-root .mdf-btn.delete) {
    border-color: var(--md-danger);
    color: var(--md-danger);
  }
  :global(.mdf-root .mdf-btn.delete:hover:not(:disabled)) {
    background: color-mix(in srgb, var(--md-danger) 12%, transparent);
  }

  /* Buttons shrink to icons on narrow screens */
  @media (max-width: 40rem) {
    .label {
      display: none;
    }
    .actions :global(.mdf-btn) {
      padding: 0.45rem;
    }
    .summary {
      flex-basis: 100%;
      order: 10;
    }
  }

  @keyframes mdf-spin {
    to {
      transform: rotate(360deg);
    }
  }
</style>