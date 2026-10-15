<script lang="ts">
  import { onMount, untrack } from 'svelte';
  import { crumbs, join } from '../lib/paths';
  import type { Listing } from '../lib/types';
  import type { ModuleContext } from '../minedash';
  import Breadcrumbs from './Breadcrumbs.svelte';
  import Dialog from './Dialog.svelte';
  import FileIcon from './FileIcon.svelte';

  let {
    ctx,
    folder,
    count,
    blocked,
    onsubmit,
    onclose
  }: {
    ctx: ModuleContext;
    /** Folder the items are in. It starts as the shown folder. */
    folder: string;
    count: number;
    /** Folders that are moved themselves. They and everything below them are no valid destination. */
    blocked: string[];
    /** Returns an error message, or null on success. On success the caller has already closed the dialog. */
    onsubmit: (destination: string) => Promise<string | null>;
    onclose: () => void;
  } = $props();

  let path = $state(untrack(() => folder));
  let directories = $state.raw<string[]>([]);
  let truncated = $state(false);
  let loading = $state(true);
  let loadError = $state('');
  let retryPath = $state('');
  let error = $state('');
  let busy = $state(false);
  let requestId = 0;

  const trail = $derived(crumbs(path, 'Server'));
  const sameFolder = $derived(path === folder);
  const insideMoved = $derived(isBlocked(path));
  const canSubmit = $derived(!loading && loadError === '' && !sameFolder && !insideMoved && !busy);
  const note = $derived(
    sameFolder ? 'The items are already in this folder.' : insideMoved ? 'A folder cannot be moved into itself.' : ''
  );

  function isBlocked(candidate: string): boolean {
    return blocked.some((item) => candidate === item || candidate.startsWith(`${item}/`));
  }

  async function load(target: string) {
    const id = ++requestId;
    loading = true;
    loadError = '';
    error = '';
    try {
      const listing = await ctx.http.get<Listing>(`/list?path=${encodeURIComponent(target)}`);
      if (id !== requestId) return;
      // The server answers with the cleaned up path
      path = listing.path;
      directories = listing.entries.filter((entry) => entry.directory).map((entry) => entry.name);
      truncated = listing.truncated;
    } catch (e) {
      if (id !== requestId) return;
      retryPath = target;
      loadError = e instanceof Error && e.message ? e.message : 'The folder could not be loaded';
    } finally {
      if (id === requestId) loading = false;
    }
  }

  async function submit() {
    if (!canSubmit) return;
    busy = true;
    error = '';
    let result: string | null;
    try {
      result = await onsubmit(path);
    } catch {
      result = 'Something went wrong';
    }
    // On success this dialog is already gone
    if (result !== null) {
      error = result;
      busy = false;
    }
  }

  onMount(() => {
    void load(path);
  });
</script>

<Dialog title={count === 1 ? 'Move 1 item' : `Move ${count} items`} wide {busy} {onclose}>
  <p class="label">Choose the destination folder</p>
  <div class="crumbs">
    <Breadcrumbs {trail} onnavigate={(target) => void load(target)} />
  </div>

  <div class="list" aria-busy={loading}>
    {#if loadError}
      <div class="state" role="alert">
        <p>{loadError}</p>
        <div class="buttons">
          <button type="button" class="mdf-btn" onclick={() => void load(retryPath)}>Try again</button>
          <button type="button" class="mdf-btn" onclick={() => void load('')}>Go to the server folder</button>
        </div>
      </div>
    {:else if loading && directories.length === 0}
      <div class="state">Loading…</div>
    {:else if directories.length === 0}
      <div class="state">No subfolders here</div>
    {:else}
      <ul class:dim={loading}>
        {#each directories as name (name)}
          {@const own = isBlocked(join(path, name))}
          <li>
            <button
              type="button"
              disabled={own}
              title={own ? 'This folder is being moved' : name}
              onclick={() => void load(join(path, name))}
            >
              <FileIcon directory={true} />
              <span class="name">{name}</span>
            </button>
          </li>
        {/each}
      </ul>
      {#if truncated}
        <p class="more">This folder has more items than can be shown.</p>
      {/if}
    {/if}
  </div>

  <p class="note" class:error={error !== ''} role={error ? 'alert' : undefined}>{error || note}</p>

  {#snippet actions()}
    <button type="button" class="mdf-btn" disabled={busy} onclick={onclose}>Cancel</button>
    <button type="button" class="mdf-btn primary" disabled={!canSubmit} onclick={submit}>
      {busy ? 'Moving…' : 'Move here'}
    </button>
  {/snippet}
</Dialog>

<style>
  p {
    margin: 0;
    font-size: 0.8125rem;
  }
  .label {
    color: var(--md-muted);
  }
  .crumbs {
    display: flex;
    margin: 0.375rem 0 0.5rem;
    padding: 0.125rem 0.25rem;
    border: 1px solid var(--md-border);
    border-radius: 0.5rem;
    background: var(--md-bg);
  }
  .list {
    height: 14rem;
    overflow: auto;
    border: 1px solid var(--md-border);
    border-radius: 0.5rem;
  }
  ul {
    margin: 0;
    padding: 0.25rem;
    list-style: none;
    transition: opacity 0.15s;
  }
  ul.dim {
    opacity: 0.6;
  }
  li button {
    display: flex;
    align-items: center;
    gap: 0.625rem;
    width: 100%;
    padding: 0.5rem 0.625rem;
    border: none;
    border-radius: 0.375rem;
    background: transparent;
    color: var(--md-text);
    font: inherit;
    font-size: 0.875rem;
    text-align: left;
    cursor: pointer;
  }
  li button:hover:not(:disabled) {
    background: var(--md-surface-2);
  }
  li button:focus-visible {
    outline: 2px solid var(--md-accent);
    outline-offset: -2px;
  }
  li button:disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }
  .name {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .state {
    display: grid;
    gap: 0.75rem;
    justify-items: center;
    padding: 2.5rem 1rem;
    color: var(--md-muted);
    text-align: center;
    font-size: 0.875rem;
  }
  .state p {
    font-size: 0.875rem;
    overflow-wrap: anywhere;
  }
  .buttons {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 0.5rem;
  }
  .more {
    padding: 0.5rem 0.75rem;
    color: var(--md-muted);
  }
  .note {
    min-height: 1.25rem;
    margin-top: 0.5rem;
    color: var(--md-muted);
  }
  .note.error {
    color: var(--md-danger);
  }
</style>