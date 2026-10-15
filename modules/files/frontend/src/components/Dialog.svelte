<script module lang="ts">
  let counter = 0;
</script>

<script lang="ts">
  import { onMount, type Snippet } from 'svelte';

  let {
    title,
    busy = false,
    wide = false,
    onclose,
    children,
    actions
  }: {
    title: string;
    /** While an action runs, the dialog cannot be closed. */
    busy?: boolean;
    wide?: boolean;
    onclose: () => void;
    children: Snippet;
    actions?: Snippet;
  } = $props();

  const titleId = `mdf-dialog-title-${++counter}`;

  let dialog = $state<HTMLDialogElement>();
  let destroyed = false;
  let pressedOnBackdrop = false;

  // A native modal dialog traps the focus, blocks the page behind it and returns the focus on close
  onMount(() => {
    dialog?.showModal();
    return () => {
      destroyed = true;
      if (dialog?.open) dialog.close();
    };
  });

  function requestClose() {
    if (!busy) onclose();
  }

  function onKeydown(event: KeyboardEvent) {
    // Handled here, because browsers ignore a repeated cancel and close the dialog anyway
    if (event.key === 'Escape') {
      event.preventDefault();
      requestClose();
    }
  }

  // Some browsers close a modal dialog on their own. While an action runs, it comes back.
  function onNativeClose() {
    if (destroyed || !dialog) return;
    if (busy) dialog.showModal();
    else onclose();
  }
</script>

<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
<!-- svelte-ignore a11y_click_events_have_key_events -->
<dialog
  bind:this={dialog}
  class:wide
  aria-labelledby={titleId}
  onkeydown={onKeydown}
  oncancel={(event) => {
    event.preventDefault();
    requestClose();
  }}
  onclose={onNativeClose}
  onpointerdown={(event) => (pressedOnBackdrop = event.target === dialog)}
  onclick={(event) => {
    // Only a click that started and ended on the backdrop closes it, not a text selection that ends there
    if (event.target === dialog && pressedOnBackdrop) requestClose();
  }}
>
  <div class="panel">
    <header>
      <h2 id={titleId}>{title}</h2>
    </header>
    <div class="body">
      {@render children()}
    </div>
    {#if actions}
      <footer>
        {@render actions()}
      </footer>
    {/if}
  </div>
</dialog>

<style>
  dialog {
    width: min(26rem, calc(100vw - 2rem));
    max-width: none;
    max-height: calc(100dvh - 2rem);
    padding: 0;
    overflow: hidden;
    border: 1px solid var(--md-border);
    border-radius: var(--md-radius);
    background: var(--md-surface);
    color: var(--md-text);
    box-shadow: 0 20px 50px rgb(0 0 0 / 0.35);
  }
  dialog.wide {
    width: min(36rem, calc(100vw - 2rem));
  }
  dialog::backdrop {
    background: rgb(0 0 0 / 0.5);
  }

  .panel {
    display: flex;
    flex-direction: column;
    max-height: calc(100dvh - 2rem);
  }
  header {
    padding: 1.125rem 1.25rem 0.5rem;
  }
  h2 {
    margin: 0;
    font-size: 1rem;
    font-weight: 600;
    overflow-wrap: anywhere;
  }
  .body {
    min-height: 0;
    padding: 0.5rem 1.25rem 1rem;
    overflow: auto;
  }
  footer {
    display: flex;
    justify-content: flex-end;
    gap: 0.5rem;
    padding: 0.75rem 1.25rem 1.25rem;
  }
</style>