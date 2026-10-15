<script lang="ts">
  import { untrack } from 'svelte';
  import type { Explorer } from '../lib/explorer.svelte';
  import { formatBytes, formatModified } from '../lib/format';
  import type { Entry, SortKey } from '../lib/types';
  import Checkbox from './Checkbox.svelte';
  import FileIcon from './FileIcon.svelte';

  let { explorer }: { explorer: Explorer } = $props();

  /** Rows rendered at once. Huge folders get a button instead of thousands of rows. */
  const PAGE_SIZE = 500;

  let limit = $state(PAGE_SIZE);
  /** Row that can be reached with the Tab key. The arrow keys move it. */
  let cursor = $state(0);
  const rows: Array<HTMLTableRowElement | null> = [];

  const visible = $derived(explorer.sorted.slice(0, limit));
  const hidden = $derived(explorer.sorted.length - visible.length);

  /** Gives the keyboard focus to the first selected row, or else to the row at the cursor. */
  export function focus(): void {
    if (visible.length === 0) return;
    const selected = visible.findIndex((entry) => explorer.isSelected(entry.name));
    const index = selected >= 0 ? selected : Math.min(cursor, visible.length - 1);
    cursor = index;
    rows[index]?.focus();
  }

  // A new folder starts at the top again
  $effect(() => {
    void explorer.path;
    untrack(() => {
      limit = PAGE_SIZE;
      cursor = 0;
    });
  });

  // Keep the keyboard position inside the list when rows disappear
  $effect(() => {
    const last = visible.length - 1;
    untrack(() => {
      if (cursor > last) cursor = Math.max(0, last);
    });
  });

  function ariaSort(key: SortKey): 'ascending' | 'descending' | 'none' {
    if (explorer.sortKey !== key) return 'none';
    return explorer.sortDirection === 'asc' ? 'ascending' : 'descending';
  }

  function open(entry: Entry) {
    if (entry.directory) explorer.enter(entry.name);
    // Files open in the editor in a later step
  }

  function onRowClick(event: MouseEvent, entry: Entry, index: number) {
    cursor = index;
    if (event.shiftKey) explorer.selectRange(entry.name);
    else if (event.metaKey || event.ctrlKey) explorer.toggle(entry.name);
    else explorer.selectOnly(entry.name);
  }

  function onCheckClick(event: MouseEvent, entry: Entry, index: number) {
    // The row must not handle the same click again
    event.stopPropagation();
    cursor = index;
    if (event.shiftKey) explorer.selectRange(entry.name);
    else explorer.toggle(entry.name);
  }

  function moveTo(target: number, extend: boolean) {
    if (visible.length === 0) return;
    const next = Math.max(0, Math.min(visible.length - 1, target));
    if (extend) {
      explorer.setAnchor(visible[cursor].name);
      explorer.selectRange(visible[next].name);
    }
    cursor = next;
    rows[next]?.focus();
  }

  /**
   * Keys that only make sense on a row. Delete, F2, Backspace, select all and Escape are handled
   * by the explorer itself, so they also work when a toolbar button has the focus.
   */
  function onKeydown(event: KeyboardEvent, entry: Entry, index: number) {
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        moveTo(index + 1, event.shiftKey);
        break;
      case 'ArrowUp':
        event.preventDefault();
        moveTo(index - 1, event.shiftKey);
        break;
      case 'Home':
        event.preventDefault();
        moveTo(0, event.shiftKey);
        break;
      case 'End':
        event.preventDefault();
        moveTo(visible.length - 1, event.shiftKey);
        break;
      case 'Enter':
        event.preventDefault();
        open(entry);
        break;
      case ' ':
        event.preventDefault();
        explorer.toggle(entry.name);
        break;
    }
  }
</script>

{#snippet sortHeader(key: SortKey, label: string)}
  <button type="button" class="sort" onclick={() => explorer.setSort(key)}>
    {label}
    {#if explorer.sortKey === key}
      <span aria-hidden="true">{explorer.sortDirection === 'asc' ? '↑' : '↓'}</span>
    {/if}
  </button>
{/snippet}

<div class="scroll" class:busy={explorer.busy} aria-busy={explorer.busy}>
  {#if explorer.entries.length === 0}
    <div class="empty">This folder is empty</div>
  {:else}
    <table
      role="grid"
      aria-label="Files and folders"
      aria-multiselectable="true"
      aria-rowcount={explorer.entries.length + 1}
    >
      <thead>
        <tr>
          <th class="check" scope="col">
            <Checkbox
              checked={explorer.allSelected ? true : explorer.someSelected ? 'mixed' : false}
              label="Select all"
              onclick={() => explorer.toggleAll()}
            />
          </th>
          <th class="name" scope="col" aria-sort={ariaSort('name')}>{@render sortHeader('name', 'Name')}</th>
          <th class="size" scope="col" aria-sort={ariaSort('size')}>{@render sortHeader('size', 'Size')}</th>
          <th class="modified" scope="col" aria-sort={ariaSort('modified')}>
            {@render sortHeader('modified', 'Modified')}
          </th>
        </tr>
      </thead>
      <tbody>
        {#each visible as entry, index (entry.name)}
          <tr
            bind:this={rows[index]}
            role="row"
            class:selected={explorer.isSelected(entry.name)}
            aria-selected={explorer.isSelected(entry.name)}
            tabindex={index === cursor ? 0 : -1}
            onclick={(event) => onRowClick(event, entry, index)}
            ondblclick={() => open(entry)}
            onkeydown={(event) => onKeydown(event, entry, index)}
            onfocus={() => (cursor = index)}
          >
            <td class="check">
              <Checkbox
                checked={explorer.isSelected(entry.name)}
                label="Select {entry.name}"
                tabindex={-1}
                onclick={(event) => onCheckClick(event, entry, index)}
              />
            </td>
            <td class="name">
              <span class="cell">
                <FileIcon directory={entry.directory} link={entry.link} />
                <span class="text">
                  {#if entry.directory}
                    <button
                      type="button"
                      class="open"
                      title={entry.name}
                      tabindex="-1"
                      onclick={(event) => {
                        event.stopPropagation();
                        explorer.enter(entry.name);
                      }}
                    >
                      {entry.name}
                    </button>
                  {:else}
                    <span class="filename" title={entry.name}>{entry.name}</span>
                  {/if}
                  <span class="meta">
                    {entry.directory ? '' : `${formatBytes(entry.size)} · `}{formatModified(entry.modified)}
                  </span>
                </span>
              </span>
            </td>
            <td class="size">{entry.directory ? '—' : formatBytes(entry.size)}</td>
            <td class="modified">{formatModified(entry.modified)}</td>
          </tr>
        {/each}

        {#if hidden > 0}
          <tr class="more">
            <td colspan="4">
              <button type="button" class="mdf-btn" onclick={() => (limit += PAGE_SIZE)}>
                Show {Math.min(PAGE_SIZE, hidden)} more ({hidden} hidden)
              </button>
            </td>
          </tr>
        {/if}
        {#if explorer.truncated}
          <tr class="more">
            <td colspan="4">Only the first {explorer.entries.length} items of this folder are shown.</td>
          </tr>
        {/if}
      </tbody>
    </table>
  {/if}
</div>

<style>
  .scroll {
    height: 100%;
    overflow: auto;
    transition: opacity 0.15s;
  }
  .scroll.busy {
    opacity: 0.6;
  }

  table {
    width: 100%;
    border-collapse: separate;
    border-spacing: 0;
    table-layout: fixed;
    font-size: 0.875rem;
  }

  th,
  td {
    padding: 0 0.5rem;
    height: 2.5rem;
    border-bottom: 1px solid var(--md-border);
    text-align: left;
    vertical-align: middle;
  }

  thead th {
    position: sticky;
    top: 0;
    z-index: 1;
    height: 2.25rem;
    background: var(--md-surface);
    color: var(--md-muted);
    font-size: 0.75rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }

  .check {
    width: 2.75rem;
    padding-left: 0.75rem;
  }
  .size {
    width: 6.5rem;
    text-align: right;
  }
  .modified {
    width: 11rem;
  }

  .sort {
    padding: 0.25rem 0;
    border: none;
    background: transparent;
    color: inherit;
    font: inherit;
    letter-spacing: inherit;
    text-transform: inherit;
    cursor: pointer;
  }
  .sort:hover {
    color: var(--md-text);
  }
  .sort:focus-visible {
    outline: 2px solid var(--md-accent);
    outline-offset: 2px;
  }

  tbody tr[role='row'] {
    cursor: default;
    user-select: none;
  }
  tbody tr[role='row']:hover {
    background: var(--md-surface-2);
  }
  tbody tr.selected {
    background: var(--md-surface-2);
    background: color-mix(in srgb, var(--md-accent) 14%, transparent);
  }
  tbody tr[role='row']:focus-visible {
    outline: 2px solid var(--md-accent);
    outline-offset: -2px;
  }

  .cell {
    display: flex;
    align-items: center;
    gap: 0.625rem;
    min-width: 0;
  }
  .text {
    min-width: 0;
    flex: 1;
  }
  .filename,
  .open {
    display: block;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .open {
    padding: 0;
    border: none;
    background: transparent;
    color: var(--md-text);
    font: inherit;
    font-weight: 500;
    text-align: left;
    cursor: pointer;
  }
  .open:hover {
    color: var(--md-accent);
    text-decoration: underline;
  }

  /* Size and date move below the name on narrow screens */
  .meta {
    display: none;
    color: var(--md-muted);
    font-size: 0.75rem;
  }

  .more td {
    height: auto;
    padding: 0.75rem;
    color: var(--md-muted);
    font-size: 0.8125rem;
    text-align: center;
  }

  .empty {
    padding: 3rem 1rem;
    color: var(--md-muted);
    text-align: center;
  }

  @media (max-width: 40rem) {
    .size,
    .modified {
      display: none;
    }
    .meta {
      display: block;
    }
    th,
    td {
      height: 3rem;
    }
  }
</style>