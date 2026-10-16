<script lang="ts">
  import type { ConflictAction, ConflictKind } from '../lib/editor.svelte';
  import Dialog from './Dialog.svelte';

  let {
    kind,
    name,
    busy,
    error,
    onresolve
  }: {
    kind: ConflictKind;
    name: string;
    /** True while an action runs. */
    busy: boolean;
    /** Why the last action failed, shown inside the dialog. */
    error: string;
    onresolve: (action: ConflictAction) => void;
  } = $props();
</script>

<!-- Closing the dialog any other way (Esc, a click outside) means: keep editing -->
<Dialog
  title={kind === 'changed' ? 'The file was changed on disk' : 'The file was deleted'}
  wide
  {busy}
  onclose={() => onresolve('keep')}
>
  <p class="message">
    {#if kind === 'changed'}
      "{name}" was modified by someone else after you opened it. Your changes are not saved yet.
      What should happen with them?
    {:else}
      "{name}" no longer exists. Your changes are not saved yet.
    {/if}
  </p>

  <div class="choices">
    {#if kind === 'changed'}
      <button type="button" class="choice" disabled={busy} onclick={() => onresolve('copy')}>
        <strong>Save my version as a copy</strong>
        <span>Keeps both versions. The file on disk stays as it is, so nothing is lost.</span>
      </button>
      <button type="button" class="choice" disabled={busy} onclick={() => onresolve('reload')}>
        <strong>Reload from disk</strong>
        <span>Shows the version on disk and drops your changes.</span>
      </button>
      <button type="button" class="choice danger" disabled={busy} onclick={() => onresolve('overwrite')}>
        <strong>Overwrite with my version</strong>
        <span>Replaces the changes of the other person. This cannot be undone.</span>
      </button>
    {:else}
      <button type="button" class="choice" disabled={busy} onclick={() => onresolve('recreate')}>
        <strong>Recreate the file</strong>
        <span>Creates "{name}" again with your text.</span>
      </button>
      <button type="button" class="choice danger" disabled={busy} onclick={() => onresolve('discard')}>
        <strong>Close and discard my text</strong>
        <span>Leaves the editor. Your changes are lost.</span>
      </button>
    {/if}
  </div>

  {#if busy}
    <p class="working" role="status">Working…</p>
  {/if}
  {#if error}
    <p class="error" role="alert">{error}</p>
  {/if}

  {#snippet actions()}
    <!-- The safe choice has the focus when the dialog opens -->
    <!-- svelte-ignore a11y_autofocus -->
    <button type="button" class="mdf-btn" autofocus disabled={busy} onclick={() => onresolve('keep')}>
      Keep editing
    </button>
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

  .choices {
    display: grid;
    gap: 0.5rem;
    margin-top: 0.875rem;
  }
  .choice {
    display: grid;
    gap: 0.125rem;
    padding: 0.625rem 0.75rem;
    border: 1px solid var(--md-border);
    border-radius: 0.5rem;
    background: var(--md-surface);
    color: var(--md-text);
    font: inherit;
    text-align: left;
    cursor: pointer;
  }
  .choice strong {
    font-size: 0.875rem;
    font-weight: 600;
  }
  .choice span {
    color: var(--md-muted);
    font-size: 0.8125rem;
  }
  .choice:hover:not(:disabled) {
    background: var(--md-surface-2);
    border-color: var(--md-accent);
  }
  .choice:focus-visible {
    outline: 2px solid var(--md-accent);
    outline-offset: 1px;
  }
  .choice:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
  .choice.danger strong {
    color: var(--md-danger);
  }
  .choice.danger:hover:not(:disabled) {
    border-color: var(--md-danger);
  }

  .working {
    margin-top: 0.75rem;
    color: var(--md-muted);
  }
  .error {
    margin-top: 0.75rem;
    color: var(--md-danger);
    overflow-wrap: anywhere;
  }
</style>