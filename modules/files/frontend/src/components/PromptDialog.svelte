<script module lang="ts">
  let counter = 0;
</script>

<script lang="ts">
  import { onMount, tick, untrack } from 'svelte';
  import Dialog from './Dialog.svelte';

  let {
    title,
    label,
    confirmLabel,
    initial = '',
    selection,
    hint = '',
    check,
    onsubmit,
    onclose
  }: {
    title: string;
    label: string;
    confirmLabel: string;
    initial?: string;
    /** Part of the initial text that is selected, defaults to all of it. */
    selection?: [number, number];
    hint?: string;
    /** Returns an error message for an invalid value, or null. */
    check: (value: string) => string | null;
    /** Returns an error message, or null on success. On success the caller has already closed the dialog. */
    onsubmit: (value: string) => Promise<string | null>;
    onclose: () => void;
  } = $props();

  const id = `mdf-prompt-${++counter}`;

  let value = $state(untrack(() => initial));
  let input = $state<HTMLInputElement>();
  let busy = $state(false);
  let touched = $state(false);
  let serverError = $state('');

  const problem = $derived(check(value));
  const message = $derived(serverError || (touched && problem ? problem : ''));

  onMount(async () => {
    await tick();
    input?.focus();
    const [start, end] = selection ?? [0, value.length];
    input?.setSelectionRange(start, end);
  });

  async function submit(event: SubmitEvent) {
    event.preventDefault();
    if (busy) return;
    if (problem !== null) {
      touched = true;
      return;
    }

    busy = true;
    serverError = '';
    let result: string | null;
    try {
      result = await onsubmit(value);
    } catch {
      result = 'Something went wrong';
    }
    // On success this dialog is already gone
    if (result !== null) {
      serverError = result;
      busy = false;
      await tick();
      input?.focus();
    }
  }
</script>

<Dialog {title} {busy} {onclose}>
  <form id="{id}-form" onsubmit={submit} novalidate>
    <label for="{id}-input">{label}</label>
    <input
      id="{id}-input"
      bind:this={input}
      bind:value
      oninput={() => {
        touched = true;
        serverError = '';
      }}
      type="text"
      autocomplete="off"
      autocapitalize="off"
      spellcheck="false"
      disabled={busy}
      aria-invalid={message !== ''}
      aria-describedby="{id}-message"
    />
    <p id="{id}-message" class:error={message !== ''} role={message ? 'alert' : undefined}>
      {message || hint}
    </p>
  </form>

  {#snippet actions()}
    <button type="button" class="mdf-btn" disabled={busy} onclick={onclose}>Cancel</button>
    <button type="submit" form="{id}-form" class="mdf-btn primary" disabled={busy || problem !== null}>
      {busy ? 'Working…' : confirmLabel}
    </button>
  {/snippet}
</Dialog>

<style>
  label {
    display: block;
    margin-bottom: 0.375rem;
    font-size: 0.8125rem;
    font-weight: 500;
  }
  input {
    box-sizing: border-box;
    width: 100%;
    padding: 0.5rem 0.625rem;
    border: 1px solid var(--md-border);
    border-radius: 0.5rem;
    background: var(--md-bg);
    color: var(--md-text);
    /* 16px prevents the zoom on focus in mobile Safari */
    font: inherit;
    font-size: 1rem;
    outline: none;
  }
  input:focus {
    border-color: var(--md-accent);
  }
  input[aria-invalid='true'] {
    border-color: var(--md-danger);
  }
  input:disabled {
    opacity: 0.6;
  }
  p {
    min-height: 1.25rem;
    margin: 0.5rem 0 0;
    color: var(--md-muted);
    font-size: 0.8125rem;
    overflow-wrap: anywhere;
  }
  p.error {
    color: var(--md-danger);
  }
</style>