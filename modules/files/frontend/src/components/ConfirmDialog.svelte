<script lang="ts">
  import Dialog from './Dialog.svelte';

  let {
    title,
    message,
    names = [],
    confirmLabel,
    danger = false,
    onconfirm,
    onclose
  }: {
    title: string;
    message: string;
    /** Items the action applies to. Only the first ones are listed. */
    names?: string[];
    confirmLabel: string;
    danger?: boolean;
    /** Returns an error message, or null on success. On success the caller has already closed the dialog. */
    onconfirm: () => Promise<string | null>;
    onclose: () => void;
  } = $props();

  const MAX_SHOWN = 8;

  let busy = $state(false);
  let error = $state('');

  const shown = $derived(names.slice(0, MAX_SHOWN));

  async function confirm() {
    if (busy) return;
    busy = true;
    error = '';
    let result: string | null;
    try {
      result = await onconfirm();
    } catch {
      result = 'Something went wrong';
    }
    if (result !== null) {
      error = result;
      busy = false;
    }
  }
</script>

<Dialog {title} {busy} {onclose}>
  <p class="message">{message}</p>

  {#if shown.length > 0}
    <ul>
      {#each shown as name (name)}
        <li>{name}</li>
      {/each}
    </ul>
    {#if names.length > shown.length}
      <p class="more">… and {names.length - shown.length} more</p>
    {/if}
  {/if}

  {#if error}
    <p class="error" role="alert">{error}</p>
  {/if}

  <!-- Cancel comes first, so it is the button that has the focus when the dialog opens -->
  {#snippet actions()}
    <button type="button" class="mdf-btn" disabled={busy} onclick={onclose}>Cancel</button>
    <button type="button" class="mdf-btn primary" class:danger disabled={busy} onclick={confirm}>
      {busy ? 'Working…' : confirmLabel}
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
  .error {
    margin-top: 0.75rem;
    color: var(--md-danger);
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