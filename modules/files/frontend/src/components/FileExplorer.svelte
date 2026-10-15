<script lang="ts">
  import { onMount } from 'svelte';
  import { Explorer } from '../lib/explorer.svelte';
  import { formatCount } from '../lib/format';
  import type { ModuleContext } from '../minedash';
  import FileList from './FileList.svelte';
  import Toolbar from './Toolbar.svelte';

  let { ctx }: { ctx: ModuleContext } = $props();

  // The context does not change while the page is mounted
  const explorer = new Explorer(ctx);

  onMount(() => explorer.start());
</script>

{#if !explorer.canView}
  <div class="mdf-root denied">
    <h2>No access</h2>
    <p>You need the permission <code>files.view</code> to browse files.</p>
  </div>
{:else}
  <div class="mdf-root explorer">
    <Toolbar {explorer} />

    {#if explorer.notice}
      <div class="banner {explorer.notice.kind}" role={explorer.notice.kind === 'error' ? 'alert' : 'status'}>
        <p>{explorer.notice.text}</p>
        <button type="button" class="mdf-btn ghost" onclick={() => explorer.dismissNotice()}>Dismiss</button>
      </div>
    {/if}

    <div class="content">
      {#if !explorer.loaded}
        {#if explorer.loadError}
          <div class="state" role="alert">
            <p>{explorer.loadError}</p>
            <div class="buttons">
              <button type="button" class="mdf-btn primary" onclick={() => explorer.reload()}>Try again</button>
              <button type="button" class="mdf-btn" onclick={() => void explorer.open('', 'replace')}>
                Go to the server folder
              </button>
            </div>
          </div>
        {:else}
          <div class="state">Loading…</div>
        {/if}
      {:else}
        <FileList {explorer} />
      {/if}
    </div>

    {#if explorer.loaded}
      <div class="status">
        {formatCount(explorer.folderCount, 'folder')} · {formatCount(explorer.fileCount, 'file')}
      </div>
    {/if}
  </div>
{/if}

<style>
  .mdf-root {
    border: 1px solid var(--md-border);
    border-radius: var(--md-radius);
    background: var(--md-surface);
    color: var(--md-text);
  }

  .explorer {
    display: flex;
    flex-direction: column;
    height: 100%;
    min-height: 22rem;
    overflow: hidden;
  }

  .denied {
    max-width: 28rem;
    margin: 2.5rem auto;
    padding: 1.5rem;
    text-align: center;
  }
  .denied h2 {
    margin: 0 0 0.5rem;
    font-size: 1rem;
  }
  .denied p {
    margin: 0;
    color: var(--md-muted);
    font-size: 0.875rem;
  }

  .content {
    flex: 1;
    min-height: 0;
  }

  .state {
    display: grid;
    gap: 1rem;
    justify-items: center;
    padding: 3rem 1rem;
    color: var(--md-muted);
    text-align: center;
  }
  .state p {
    margin: 0;
    max-width: 32rem;
    overflow-wrap: anywhere;
  }
  .buttons {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 0.5rem;
  }

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

  .status {
    padding: 0.4rem 0.75rem;
    border-top: 1px solid var(--md-border);
    color: var(--md-muted);
    font-size: 0.75rem;
  }

  /* Shared button style for all components of this module */
  :global(.mdf-root .mdf-btn) {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 0.4rem;
    padding: 0.4rem 0.75rem;
    border: 1px solid var(--md-border);
    border-radius: 0.5rem;
    background: var(--md-surface);
    color: var(--md-text);
    font: inherit;
    font-size: 0.8125rem;
    font-weight: 500;
    cursor: pointer;
  }
  :global(.mdf-root .mdf-btn:hover:not(:disabled)) {
    background: var(--md-surface-2);
  }
  :global(.mdf-root .mdf-btn:disabled) {
    opacity: 0.5;
    cursor: not-allowed;
  }
  :global(.mdf-root .mdf-btn:focus-visible) {
    outline: 2px solid var(--md-accent);
    outline-offset: 1px;
  }
  :global(.mdf-root .mdf-btn.icon) {
    padding: 0.4rem;
  }
  :global(.mdf-root .mdf-btn.ghost) {
    border-color: transparent;
    background: transparent;
  }
  :global(.mdf-root .mdf-btn.ghost:hover:not(:disabled)) {
    background: var(--md-surface-2);
  }
  :global(.mdf-root .mdf-btn.primary) {
    border-color: transparent;
    background: var(--md-accent);
    color: var(--md-accent-fg);
  }
  :global(.mdf-root .mdf-btn.primary:hover:not(:disabled)) {
    background: var(--md-accent-hover);
  }
</style>