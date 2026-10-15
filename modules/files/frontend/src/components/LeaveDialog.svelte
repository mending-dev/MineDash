<script lang="ts">
  import type { LeaveRequest } from '../lib/editor.svelte';
  import Dialog from './Dialog.svelte';

  let {
    reason,
    name,
    busy,
    onstay,
    ondiscard,
    onsave
  }: {
    reason: LeaveRequest['reason'];
    name: string;
    /** True while the file is being saved. */
    busy: boolean;
    onstay: () => void;
    ondiscard: () => void;
    onsave: () => void;
  } = $props();
</script>

<!-- Closing the dialog any other way means: keep editing -->
<Dialog title={reason === 'close' ? 'Unsaved changes' : 'Reload from disk?'} {busy} onclose={onstay}>
  <p>
    {#if reason === 'close'}
      "{name}" has changes that are not saved yet.
    {:else}
      Your changes to "{name}" are replaced by the file on disk. This cannot be undone.
    {/if}
  </p>

  <!-- "Keep editing" comes first, so it has the focus when the dialog opens -->
  {#snippet actions()}
    <button type="button" class="mdf-btn" disabled={busy} onclick={onstay}>Keep editing</button>
    <button type="button" class="mdf-btn" disabled={busy} onclick={ondiscard}>
      {reason === 'close' ? 'Discard' : 'Discard and reload'}
    </button>
    {#if reason === 'close'}
      <button type="button" class="mdf-btn primary" disabled={busy} onclick={onsave}>
        {busy ? 'Saving…' : 'Save'}
      </button>
    {/if}
  {/snippet}
</Dialog>

<style>
  p {
    margin: 0;
    color: var(--md-muted);
    font-size: 0.875rem;
    overflow-wrap: anywhere;
  }
</style>