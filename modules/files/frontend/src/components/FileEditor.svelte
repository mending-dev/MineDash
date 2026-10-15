<script lang="ts">
  import { onMount, type Component } from 'svelte';
  import type { Editing, Snapshot } from '../lib/editor.svelte';
  import type { Explorer } from '../lib/explorer.svelte';
  import { formatBytes } from '../lib/format';
  import { languageLabel } from '../lib/languages';
  import LeaveDialog from './LeaveDialog.svelte';
  import NoticeBar from './NoticeBar.svelte';

  /** What the lazily loaded code editor offers to this component. */
  interface CodeEditorApi {
    getSnapshot(): Snapshot;
    markSaved(snapshot: Snapshot): void;
    find(): void;
    focus(): void;
  }

  type CodeEditorComponent = Component<
    {
      text: string;
      separator: string;
      fileName: string;
      wrap: boolean;
      readOnly: boolean;
      onchange: (dirty: boolean) => void;
      onposition: (line: number, column: number) => void;
    },
    CodeEditorApi
  >;

  let { editing, explorer }: { editing: Editing; explorer: Explorer } = $props();

  // The code editor is a separate chunk. It is requested while the file is loading.
  let Editor = $state.raw<CodeEditorComponent | null>(null);
  let editorError = $state('');
  let codeEditor = $state<CodeEditorApi>();
  let line = $state(1);
  let column = $state(1);

  onMount(() => {
    import('./CodeEditor.svelte')
      .then((module) => {
        Editor = module.default as unknown as CodeEditorComponent;
      })
      .catch(() => {
        editorError = 'The editor could not be loaded. Reload the page and try again.';
      });
  });

  // Gives the editing session access to the editor that is on screen
  $effect(() => {
    const instance = codeEditor;
    editing.controller = instance
      ? {
          snapshot: () => instance.getSnapshot(),
          markSaved: (snapshot) => instance.markSaved(snapshot)
        }
      : null;
    return () => {
      editing.controller = null;
    };
  });

  const statusText = $derived.by(() => {
    if (editing.saving) return 'Saving…';
    if (editing.dirty) return 'Unsaved changes';
    if (editing.savedAt !== null) return `Saved ${new Date(editing.savedAt).toTimeString().slice(0, 8)}`;
    return '';
  });

  /** Cmd/Ctrl+S saves, wherever the focus is. Without this the browser would offer to save the page. */
  function onKeydown(event: KeyboardEvent) {
    const wantsSave =
      (event.metaKey || event.ctrlKey) && !event.altKey && !event.shiftKey && event.key.toLowerCase() === 's';
    if (!wantsSave) return;

    event.preventDefault();
    event.stopPropagation();
    if (editing.leave === null && !editing.readOnly) void editing.save();
  }
</script>

{#snippet icon(d: string)}
  <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"><path {d} /></svg>
{/snippet}

<!-- svelte-ignore a11y_no_static_element_interactions -->
<div class="editor" onkeydowncapture={onKeydown}>
  <header>
    <button type="button" class="mdf-btn icon" title="Back to the files" aria-label="Back to the files" onclick={() => editing.close()}>
      <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path d="M15 18l-6-6 6-6" /></svg>
    </button>

    <div class="title">
      <span class="name">
        {editing.name}
        {#if editing.dirty}
          <span class="dot" role="img" aria-label="Unsaved changes" title="Unsaved changes">●</span>
        {/if}
      </span>
      <span class="path">{editing.folder === '' ? 'Server folder' : editing.folder}</span>
    </div>

    <span class="spacer"></span>

    {#if editing.status === 'ready'}
      <button type="button" class="mdf-btn" title="Find (Cmd/Ctrl+F)" onclick={() => codeEditor?.find()}>
        {@render icon('M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14ZM21 21l-4.3-4.3')}
        <span class="label">Find</span>
      </button>
      <button
        type="button"
        class="mdf-btn"
        class:on={editing.wrap}
        aria-pressed={editing.wrap}
        title="Wrap long lines"
        onclick={() => editing.toggleWrap()}
      >
        {@render icon('M3 6h18M3 12h14a3 3 0 0 1 0 6h-4m0 0 2-2m-2 2 2 2M3 18h5')}
        <span class="label">Wrap</span>
      </button>
      <button type="button" class="mdf-btn" title="Load the file from disk again" onclick={() => editing.requestReload()}>
        {@render icon('M20 11A8 8 0 0 0 5.6 6.3L4 8M4 4v4h4M4 13a8 8 0 0 0 14.4 4.7L20 16m0 4v-4h-4')}
        <span class="label">Reload</span>
      </button>
      <button type="button" class="mdf-btn" title="Download" onclick={() => editing.download()}>
        {@render icon('M12 4v11m0 0-4-4m4 4 4-4M5 20h14')}
        <span class="label">Download</span>
      </button>
      {#if !editing.readOnly}
        <button
          type="button"
          class="mdf-btn primary"
          title="Save (Cmd/Ctrl+S)"
          disabled={!editing.dirty || editing.saving}
          onclick={() => void editing.save()}
        >
          {@render icon('M5 4h11l3 3v13H5V4ZM8 4v5h7V4M8 20v-6h8v6')}
          <span class="label">{editing.saving ? 'Saving…' : 'Save'}</span>
        </button>
      {/if}
    {/if}
  </header>

  <NoticeBar {explorer} />

  {#if editing.status === 'ready'}
    {#if editing.readOnly}
      <div class="banner info" role="status">
        <p>Read-only: saving needs the permission <code>files.write</code>.</p>
      </div>
    {/if}

    {#if editing.conflict === 'changed'}
      <div class="banner error" role="alert">
        <p>This file was changed by someone else after you opened it, so it was not saved.</p>
        <button type="button" class="mdf-btn" onclick={() => editing.reloadFromDisk()}>
          Reload from disk (discards your changes)
        </button>
      </div>
    {:else if editing.conflict === 'deleted'}
      <div class="banner error" role="alert">
        <p>This file no longer exists, so it was not saved.</p>
        <button type="button" class="mdf-btn" onclick={() => editing.discardAndClose()}>Close editor</button>
      </div>
    {/if}

    {#if editing.saveError}
      <div class="banner error" role="alert">
        <p>{editing.saveError}</p>
        <button type="button" class="mdf-btn ghost" onclick={() => editing.dismissSaveError()}>Dismiss</button>
      </div>
    {/if}
  {/if}

  <div class="body">
    {#if editing.status === 'loading'}
      <div class="state">Loading…</div>
    {:else if editing.status === 'error'}
      <div class="state" role="alert">
        <p>{editing.loadError}</p>
        <div class="buttons">
          <button type="button" class="mdf-btn primary" onclick={() => editing.close()}>Back to the files</button>
          <button type="button" class="mdf-btn" onclick={() => editing.download()}>Download</button>
          {#if editing.loadStatus !== 413 && editing.loadStatus !== 415}
            <button type="button" class="mdf-btn" onclick={() => editing.retry()}>Try again</button>
          {/if}
        </div>
      </div>
    {:else if editorError}
      <div class="state" role="alert"><p>{editorError}</p></div>
    {:else if Editor}
      {#key editing.session}
        <Editor
          bind:this={codeEditor}
          text={editing.text}
          separator={editing.separator}
          fileName={editing.name}
          wrap={editing.wrap}
          readOnly={editing.readOnly}
          onchange={(dirty) => editing.onChange(dirty)}
          onposition={(l, c) => {
            line = l;
            column = c;
          }}
        />
      {/key}
    {:else}
      <div class="state">Loading the editor…</div>
    {/if}
  </div>

  {#if editing.status === 'ready'}
    <footer>
      <span>{languageLabel(editing.name)}</span>
      <span>Ln {line}, Col {column}</span>
      <span>{editing.separator === '\r\n' ? 'CRLF' : 'LF'}</span>
      <span>{formatBytes(editing.size)}</span>
      <span class="spacer"></span>
      <span class="save-state" class:dirty={editing.dirty} role="status">{statusText}</span>
    </footer>
  {/if}

  {#if editing.leave}
    <LeaveDialog
      reason={editing.leave.reason}
      name={editing.name}
      busy={editing.saving}
      onstay={() => editing.stay()}
      ondiscard={() => editing.discard()}
      onsave={() => void editing.saveAndLeave()}
    />
  {/if}
</div>

<style>
  .editor {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-height: 0;
  }

  header {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.5rem 0.75rem;
    border-bottom: 1px solid var(--md-border);
  }
  .title {
    display: grid;
    min-width: 0;
  }
  .name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 0.875rem;
    font-weight: 600;
  }
  .dot {
    margin-left: 0.25rem;
    color: var(--md-warning);
    font-size: 0.75rem;
  }
  .path {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--md-muted);
    font-size: 0.75rem;
  }
  .spacer {
    flex: 1;
  }

  svg {
    fill: none;
    stroke: currentColor;
    stroke-width: 2;
    stroke-linecap: round;
    stroke-linejoin: round;
  }
  :global(.mdf-root .mdf-btn.on) {
    background: color-mix(in srgb, var(--md-accent) 14%, transparent);
    border-color: var(--md-accent);
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

  .body {
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

  footer {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.25rem 1rem;
    padding: 0.4rem 0.75rem;
    border-top: 1px solid var(--md-border);
    color: var(--md-muted);
    font-size: 0.75rem;
  }
  .save-state.dirty {
    color: var(--md-warning);
  }

  @media (max-width: 40rem) {
    .label {
      display: none;
    }
    header :global(.mdf-btn) {
      padding: 0.45rem;
    }
  }
</style>