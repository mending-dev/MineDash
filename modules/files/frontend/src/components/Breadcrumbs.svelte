<script lang="ts">
  import type { Crumb } from '../lib/paths';

  let { trail, onnavigate }: { trail: Crumb[]; onnavigate: (path: string) => void } = $props();

  let list = $state<HTMLOListElement>();

  // Long paths scroll sideways. The current folder at the end must stay visible.
  $effect(() => {
    void trail.length;
    if (list) list.scrollLeft = list.scrollWidth;
  });
</script>

<nav class="crumbs" aria-label="Folder path">
  <ol bind:this={list}>
    {#each trail as crumb, index (crumb.path)}
      <li>
        {#if index > 0}
          <span class="sep" aria-hidden="true">/</span>
        {/if}
        {#if index === trail.length - 1}
          <span class="current" aria-current="page">{crumb.name}</span>
        {:else}
          <button type="button" onclick={() => onnavigate(crumb.path)}>{crumb.name}</button>
        {/if}
      </li>
    {/each}
  </ol>
</nav>

<style>
  .crumbs {
    flex: 1;
    min-width: 0;
  }
  ol {
    display: flex;
    align-items: center;
    margin: 0;
    padding: 0;
    list-style: none;
    overflow-x: auto;
    white-space: nowrap;
    scrollbar-width: none;
  }
  ol::-webkit-scrollbar {
    display: none;
  }
  li {
    display: flex;
    align-items: center;
    flex: none;
  }
  .sep {
    padding: 0 0.125rem;
    color: var(--md-muted);
  }
  button,
  .current {
    padding: 0.25rem 0.375rem;
    border-radius: 0.375rem;
    font: inherit;
    font-size: 0.875rem;
  }
  button {
    border: none;
    background: transparent;
    color: var(--md-muted);
    cursor: pointer;
  }
  button:hover {
    background: var(--md-surface-2);
    color: var(--md-text);
  }
  button:focus-visible {
    outline: 2px solid var(--md-accent);
    outline-offset: 1px;
  }
  .current {
    color: var(--md-text);
    font-weight: 600;
  }
</style>