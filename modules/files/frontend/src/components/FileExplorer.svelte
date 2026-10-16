<script lang="ts">
  import { onMount, tick, untrack } from 'svelte';
  import { Actions } from '../lib/actions.svelte';
  import { collectFiles } from '../lib/dropped';
  import { Editing } from '../lib/editor.svelte';
  import { Explorer } from '../lib/explorer.svelte';
  import { formatBytes, formatCount } from '../lib/format';
  import { join } from '../lib/paths';
  import type { Entry } from '../lib/types';
  import { Uploader } from '../lib/uploader.svelte';
  import { baseSelection, validateName } from '../lib/validate';
  import type { ModuleContext } from '../minedash';
  import ConfirmDialog from './ConfirmDialog.svelte';
  import FileEditor from './FileEditor.svelte';
  import FileList from './FileList.svelte';
  import MoveDialog from './MoveDialog.svelte';
  import NoticeBar from './NoticeBar.svelte';
  import PromptDialog from './PromptDialog.svelte';
  import ReportDialog from './ReportDialog.svelte';
  import Toolbar from './Toolbar.svelte';
  import UploadConflictDialog from './UploadConflictDialog.svelte';
  import UploadPanel from './UploadPanel.svelte';

  let { ctx }: { ctx: ModuleContext } = $props();

  // The context does not change while the page is mounted
  const explorer = new Explorer(ctx);
  const editing = new Editing(explorer);
  // Unsaved text of a file follows it when it is renamed or moved, and goes away when it is deleted
  const actions = new Actions(explorer, {
    deleted: (paths) => editing.forgetDrafts(paths),
    moved: (moves) => editing.moveDrafts(moves)
  });
  const uploader = new Uploader(explorer);

  let root = $state<HTMLDivElement>();
  let list = $state<ReturnType<typeof FileList>>();
  let fileInput = $state<HTMLInputElement>();

  /** True while files are dragged over the explorer. */
  let dragging = $state(false);
  /** dragenter and dragleave fire for every child element, so they are counted. */
  let dragDepth = 0;

  onMount(() => {
    const stopExplorer = explorer.start();
    const stopUploader = uploader.start();
    const stopEditing = editing.start();
    window.addEventListener('dragover', onWindowDrag);
    window.addEventListener('drop', onWindowDrag);
    return () => {
      stopExplorer();
      stopUploader();
      stopEditing();
      window.removeEventListener('dragover', onWindowDrag);
      window.removeEventListener('drop', onWindowDrag);
    };
  });

  // After an action the row that had the focus may be gone, so the list takes the focus back.
  // Focus that moved to somewhere outside of the explorer in the meantime is left alone.
  $effect(() => {
    const request = actions.focusRequest;
    if (request === 0) return;
    untrack(() => {
      void tick().then(() => {
        if (editing.active) return;
        const active = document.activeElement;
        if (active && active !== document.body && root && !root.contains(active)) return;
        if (list) list.focus();
        else root?.focus({ preventScroll: true });
      });
    });
  });

  // When the editor closes, the list is back and takes the focus
  let wasEditing = false;
  $effect(() => {
    const now = editing.active;
    untrack(() => {
      if (wasEditing && !now) {
        void tick().then(() => list?.focus());
      }
      wasEditing = now;
    });
  });

  // ---------------------------------------------------------------- opening files and draft marks

  function openFile(entry: Entry): void {
    if (editing.tooLarge(entry.size)) {
      explorer.notify(
        'error',
        `"${entry.name}" is too large to edit (the limit is ${formatBytes(editing.maxBytes, 0)}). ` +
          'Select it and use Download instead.'
      );
      return;
    }
    editing.open(join(explorer.path, entry.name));
  }

  /** Which mark a row gets: a file with a draft, or a folder that contains files with drafts. */
  function draftMark(entry: Entry): 'draft' | 'inside' | null {
    const path = join(explorer.path, entry.name);
    if (entry.directory) return editing.draftFolders.has(path) ? 'inside' : null;
    return editing.draftPaths.has(path) ? 'draft' : null;
  }

  // ---------------------------------------------------------------- upload

  /** Only one dialog at a time, and only with the permission and a shown folder. */
  function acceptsFiles(): boolean {
    return (
      explorer.canWrite && explorer.loaded && !editing.active && !actions.dialog && !uploader.pending
    );
  }

  function addFiles(files: File[], folders = 0): void {
    if (acceptsFiles()) uploader.add(files, folders);
  }

  function openPicker(): void {
    fileInput?.click();
  }

  function onPick(event: Event): void {
    const input = event.currentTarget as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    // Reset, so choosing the same file again fires another change event
    input.value = '';
    addFiles(files);
  }

  function hasFiles(event: DragEvent): boolean {
    return Array.from(event.dataTransfer?.types ?? []).includes('Files');
  }

  function onDragEnter(event: DragEvent): void {
    if (!hasFiles(event)) return;
    event.preventDefault();
    dragDepth++;
    if (acceptsFiles()) dragging = true;
  }

  function onDragOver(event: DragEvent): void {
    if (!hasFiles(event)) return;
    // Cancelling the event allows the drop here and keeps the browser from opening the file
    event.preventDefault();
    if (event.dataTransfer) event.dataTransfer.dropEffect = acceptsFiles() ? 'copy' : 'none';
  }

  function onDragLeave(event: DragEvent): void {
    if (!hasFiles(event)) return;
    dragDepth = Math.max(0, dragDepth - 1);
    if (dragDepth === 0) dragging = false;
  }

  function onDrop(event: DragEvent): void {
    if (!hasFiles(event)) return;
    event.preventDefault();
    dragDepth = 0;
    dragging = false;
    if (!event.dataTransfer) return;
    // The files have to be read now, the list is not valid after the event
    const { files, folders } = collectFiles(event.dataTransfer);
    addFiles(files, folders);
  }

  /** Outside of the explorer a dropped file would replace the whole panel with the file. */
  function onWindowDrag(event: DragEvent): void {
    if (!hasFiles(event)) return;
    if (root && event.target instanceof Node && root.contains(event.target)) return;
    event.preventDefault();
    if (event.type === 'dragover' && event.dataTransfer) event.dataTransfer.dropEffect = 'none';
  }

  // ---------------------------------------------------------------- keyboard

  function isEditable(target: EventTarget | null): boolean {
    if (!(target instanceof HTMLElement)) return false;
    return (
      target.isContentEditable ||
      target.tagName === 'INPUT' ||
      target.tagName === 'TEXTAREA' ||
      target.tagName === 'SELECT'
    );
  }

  function consume(event: KeyboardEvent): void {
    event.preventDefault();
    event.stopPropagation();
  }

  /**
   * Keyboard shortcuts of the file list. They work whenever the focus is anywhere inside the explorer
   * (a row, a toolbar button, the empty area), but never while typing, while a dialog is open or
   * while a file is edited.
   * On a Mac the key labelled "delete" sends Backspace, so Backspace deletes when something is selected.
   */
  function onKeydown(event: KeyboardEvent) {
    if (event.defaultPrevented || event.isComposing || event.repeat) return;
    if (editing.active || actions.dialog || uploader.pending || isEditable(event.target)) return;

    const modifier = event.metaKey || event.ctrlKey;
    const hasSelection = explorer.selection.size > 0;

    switch (event.key) {
      case 'F2':
        if (actions.canRename) {
          consume(event);
          actions.openRename();
        }
        break;

      case 'Delete':
      case 'Backspace':
        if (hasSelection) {
          consume(event);
          if (explorer.canDelete) actions.openDelete();
          else explorer.notify('error', 'Deleting needs the permission files.delete');
        } else if (event.key === 'Backspace' && !modifier && explorer.path !== '') {
          // Nothing to delete, so Backspace goes up one folder
          consume(event);
          explorer.up();
        }
        break;

      case 'ArrowUp':
        if ((event.altKey || event.metaKey) && explorer.path !== '') {
          consume(event);
          explorer.up();
        }
        break;

      case 'a':
      case 'A':
        if (modifier) {
          consume(event);
          explorer.selectAll();
        }
        break;

      case 'Escape':
        if (hasSelection) {
          consume(event);
          explorer.clearSelection();
        }
        break;
    }
  }
</script>

{#if !explorer.canView}
  <div class="mdf-root denied">
    <h2>No access</h2>
    <p>You need the permission <code>files.view</code> to browse files.</p>
  </div>
{:else}
  <!-- svelte-ignore a11y_no_static_element_interactions -->
  <div
    bind:this={root}
    class="mdf-root explorer"
    tabindex="-1"
    onkeydowncapture={onKeydown}
    ondragenter={onDragEnter}
    ondragover={onDragOver}
    ondragleave={onDragLeave}
    ondrop={onDrop}
  >
    {#if editing.active}
      <FileEditor {editing} {explorer} />
    {:else}
      <Toolbar {explorer} {actions} onupload={openPicker} />
      <NoticeBar {explorer} />

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
          <FileList bind:this={list} {explorer} onopenfile={openFile} {draftMark} />
        {/if}
      </div>
    {/if}

    <UploadPanel {uploader} />

    {#if !editing.active && explorer.loaded}
      <div class="status">
        {formatCount(explorer.folderCount, 'folder')} · {formatCount(explorer.fileCount, 'file')}
      </div>
    {/if}

    <input bind:this={fileInput} class="picker" type="file" multiple tabindex="-1" aria-hidden="true" onchange={onPick} />

    {#if dragging}
      <div class="drop" aria-hidden="true">
        <div class="drop-box">
          <svg viewBox="0 0 24 24" width="32" height="32"><path d="M12 16V5m0 0-4 4m4-4 4 4M5 20h14" /></svg>
          <strong>Drop files to upload</strong>
          <span>to {explorer.path === '' ? 'the server folder' : explorer.path}</span>
        </div>
      </div>
    {/if}

    {#if uploader.pending}
      <UploadConflictDialog names={uploader.pending.conflicts} onchoose={(choice) => uploader.resolve(choice)} />
    {/if}

    {#if actions.dialog}
      {@const dialog = actions.dialog}
      {#if dialog.kind === 'folder'}
        <PromptDialog
          title="New folder"
          label="Folder name"
          confirmLabel="Create"
          check={(value) => actions.checkName(value)}
          onsubmit={(value) => actions.createFolder(dialog, value)}
          onclose={() => actions.close()}
        />
      {:else if dialog.kind === 'file'}
        <PromptDialog
          title="New file"
          label="File name"
          confirmLabel="Create"
          hint="The file is created empty."
          check={(value) => actions.checkName(value)}
          onsubmit={(value) => actions.createFile(dialog, value)}
          onclose={() => actions.close()}
        />
      {:else if dialog.kind === 'rename'}
        <PromptDialog
          title="Rename"
          label="New name"
          confirmLabel="Rename"
          initial={dialog.entry.name}
          selection={baseSelection(dialog.entry.name, dialog.entry.directory)}
          check={(value) => actions.checkName(value, dialog.entry.name)}
          onsubmit={(value) => actions.rename(dialog, value)}
          onclose={() => actions.close()}
        />
      {:else if dialog.kind === 'delete'}
        <ConfirmDialog
          title="Delete {formatCount(dialog.names.length, 'item')}?"
          message="This cannot be undone. Folders are deleted with everything in them."
          names={dialog.names}
          confirmLabel="Delete"
          danger
          onconfirm={() => actions.remove(dialog)}
          onclose={() => actions.close()}
        />
      {:else if dialog.kind === 'move'}
        <MoveDialog
          {ctx}
          folder={dialog.folder}
          count={dialog.names.length}
          blocked={dialog.blocked}
          onsubmit={(destination) => actions.move(dialog, destination)}
          onclose={() => actions.close()}
        />
      {:else if dialog.kind === 'zip'}
        <PromptDialog
          title="Create ZIP archive"
          label="Archive name"
          confirmLabel="Create"
          initial={dialog.suggested}
          selection={[0, Math.max(0, dialog.suggested.length - 4)]}
          hint="Large folders can take a while. Links and protected files are left out."
          check={(value) => actions.checkZipName(value)}
          onsubmit={(value) => actions.zip(dialog, value)}
          onclose={() => actions.close()}
        />
      {:else if dialog.kind === 'unzip'}
        <PromptDialog
          title="Extract archive"
          label="Extract into folder"
          confirmLabel="Extract"
          initial={dialog.suggested}
          hint="Existing files are never overwritten."
          check={(value) => validateName(value.trim())}
          onsubmit={(value) => actions.unzip(dialog, value)}
          onclose={() => actions.close()}
        />
      {:else if dialog.kind === 'report'}
        <ReportDialog
          title={dialog.title}
          summary={dialog.summary}
          lines={dialog.lines}
          more={dialog.more}
          onclose={() => actions.close()}
        />
      {/if}
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
    position: relative;
    display: flex;
    flex-direction: column;
    height: 100%;
    min-height: 22rem;
    overflow: hidden;
  }
  /* The container only takes the focus so shortcuts keep working, a ring around everything would be noise */
  .explorer:focus {
    outline: none;
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

  .status {
    padding: 0.4rem 0.75rem;
    border-top: 1px solid var(--md-border);
    color: var(--md-muted);
    font-size: 0.75rem;
  }

  .picker {
    display: none;
  }

  /* Covers the explorer while files are dragged over it. It ignores the pointer, so the drag events stay calm. */
  .drop {
    position: absolute;
    inset: 0;
    z-index: 5;
    display: grid;
    place-items: center;
    border: 2px dashed var(--md-accent);
    border-radius: inherit;
    background: color-mix(in srgb, var(--md-accent) 12%, var(--md-surface));
    pointer-events: none;
  }
  .drop-box {
    display: grid;
    gap: 0.25rem;
    justify-items: center;
    padding: 1rem;
    color: var(--md-accent);
    text-align: center;
  }
  .drop-box span {
    color: var(--md-muted);
    font-size: 0.8125rem;
    overflow-wrap: anywhere;
  }
  .drop-box svg {
    fill: none;
    stroke: currentColor;
    stroke-width: 2;
    stroke-linecap: round;
    stroke-linejoin: round;
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