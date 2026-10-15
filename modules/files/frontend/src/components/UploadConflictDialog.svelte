<script lang="ts">
  import type { ConflictChoice } from '../lib/uploader.svelte';
  import Dialog from './Dialog.svelte';

  let {
    names,
    onchoose
  }: {
    /** Names of the files that already exist. */
    names: string[];
    onchoose: (choice: ConflictChoice) => void;
  } = $props();

  const MAX_SHOWN = 8;

  const shown = $derived(names.slice(0, MAX_SHOWN));
</script>

<!-- Closing the dialog any other way counts as cancel -->
<Dialog
  title={names.length === 1 ? '1 file already exists' : `${names.length} files already exist`}
  onclose={() => onchoose('cancel')}
>
  <p class="message">Replacing overwrites the current content of these files. This cannot be undone.</p>
  <ul>
    {#each shown as name (name)}
      <li>{name}</li>
    {/each}
  </ul>
  {#if names.length > shown.length}
    <p class="more">… and {names.length - shown.length} more</p>
  {/if}

  <!-- Cancel comes first, so it has the focus when the dialog opens -->
  {#snippet actions()}
    <button type="button" class="mdf-btn" onclick={() => onchoose('cancel')}>Cancel</button>
    <button type="button" class="mdf-btn" onclick={() => onchoose('skip')}>Skip existing</button>
    <button type="button" class="mdf-btn primary danger" onclick={() => onchoose('replace')}>Replace</button>
  {/snippet}
</Dialog>

<style>
  p {
    margin: 0;
    font-size: 0.875rem;
  }
  .message {
    color: var(--md-muted);
  }
  ul {
    margin: 0.75rem 0 0;
    padding: 0.5rem 0.75rem 0.5rem 1.75rem;
    border: 1px solid var(--md-border);
    border-radius: 0.5rem;
    background: var(--md-bg);
    font-size: 0.8125rem;
  }
  li {
    overflow-wrap: anywhere;
  }
  .more {
    margin-top: 0.375rem;
    color: var(--md-muted);
    font-size: 0.8125rem;
  }
  :global(.mdf-root .mdf-btn.primary.danger) {
    background: var(--md-danger);
    color: #fff;
  }
  :global(.mdf-root .mdf-btn.primary.danger:hover:not(:disabled)) {
    background: var(--md-danger);
    filter: brightness(1.1);
  }
</style>