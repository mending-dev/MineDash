<script lang="ts">
  let {
    checked,
    label,
    onclick,
    tabindex = 0
  }: {
    checked: boolean | 'mixed';
    label: string;
    onclick: (event: MouseEvent) => void;
    tabindex?: number;
  } = $props();
</script>

<!-- A button instead of a native checkbox, so the state always comes from the list and cannot drift apart -->
<button
  type="button"
  role="checkbox"
  aria-checked={checked}
  aria-label={label}
  {tabindex}
  class="box"
  class:on={checked !== false}
  {onclick}
>
  {#if checked === true}
    <svg viewBox="0 0 16 16" width="12" height="12" aria-hidden="true">
      <path d="M3.5 8.5l3 3 6-7" />
    </svg>
  {:else if checked === 'mixed'}
    <svg viewBox="0 0 16 16" width="12" height="12" aria-hidden="true">
      <path d="M4 8h8" />
    </svg>
  {/if}
</button>

<style>
  .box {
    display: inline-grid;
    place-items: center;
    width: 1.125rem;
    height: 1.125rem;
    padding: 0;
    border: 1.5px solid var(--md-muted);
    border-radius: 0.3rem;
    background: transparent;
    color: var(--md-accent-fg);
    cursor: pointer;
    vertical-align: middle;
  }
  .box:hover {
    border-color: var(--md-accent);
  }
  .box:focus-visible {
    outline: 2px solid var(--md-accent);
    outline-offset: 2px;
  }
  .box.on {
    border-color: var(--md-accent);
    background: var(--md-accent);
  }
  svg {
    fill: none;
    stroke: currentColor;
    stroke-width: 2;
    stroke-linecap: round;
    stroke-linejoin: round;
  }
</style>