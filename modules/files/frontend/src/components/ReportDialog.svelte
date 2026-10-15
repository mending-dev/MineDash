<script lang="ts">
  import Dialog from './Dialog.svelte';

  let {
    title,
    summary,
    lines,
    more = 0,
    onclose
  }: {
    title: string;
    summary: string;
    lines: string[];
    /** Number of entries that are not listed. */
    more?: number;
    onclose: () => void;
  } = $props();
</script>

<Dialog {title} wide {onclose}>
  <p class="summary">{summary}</p>
  {#if lines.length > 0}
    <ul>
      {#each lines as line, index (index)}
        <li>{line}</li>
      {/each}
    </ul>
    {#if more > 0}
      <p class="more">… and {more} more</p>
    {/if}
  {/if}

  {#snippet actions()}
    <button type="button" class="mdf-btn primary" onclick={onclose}>Close</button>
  {/snippet}
</Dialog>

<style>
  p {
    margin: 0;
    font-size: 0.875rem;
  }
  .summary {
    color: var(--md-muted);
  }
  ul {
    max-height: 14rem;
    margin: 0.75rem 0 0;
    padding: 0.5rem 0.75rem 0.5rem 1.75rem;
    overflow: auto;
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
</style>