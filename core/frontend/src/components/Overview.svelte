<script lang="ts">
  import { untrack } from 'svelte';
  import { overview } from '../lib/overview.svelte';
  import { widgets, type WidgetSize } from '../lib/widgets.svelte';
  import ModuleWidget from './ModuleWidget.svelte';
  import Switch from './Switch.svelte';

  // Column spans of the 4 column grid (2 columns on tablets, 1 on phones)
  const SPAN: Record<WidgetSize, string> = {
    small: '',
    medium: 'md:col-span-2',
    large: 'md:col-span-2 xl:col-span-4'
  };

  let panelOpen = $state(false);
  let panel = $state<HTMLDivElement>();
  let toggleButton = $state<HTMLButtonElement>();

  // Metrics are only polled while a built-in widget is on screen
  const needsMetrics = $derived(widgets.visible.some((w) => w.component !== undefined));

  $effect(() => {
    if (!needsMetrics) return;
    untrack(() => overview.start());
    return () => overview.stop();
  });

  function onWindowPointerDown(event: PointerEvent) {
    if (!panelOpen) return;
    const target = event.target as Node;
    if (panel?.contains(target) || toggleButton?.contains(target)) return;
    panelOpen = false;
  }

  function onWindowKeydown(event: KeyboardEvent) {
    if (event.key === 'Escape') panelOpen = false;
  }

  const statusText = $derived(
    overview.status === 'ok'
      ? 'Live'
      : overview.status === 'error'
        ? 'Reconnecting…'
        : overview.status === 'forbidden'
          ? 'No access'
          : 'Connecting…'
  );
  const statusDot = $derived(
    overview.status === 'ok' ? 'bg-accent' : overview.status === 'error' ? 'bg-amber-500' : 'bg-line'
  );
</script>

<svelte:window onpointerdown={onWindowPointerDown} onkeydown={onWindowKeydown} />

<div class="mx-auto max-w-7xl">
  <div class="mb-5 flex flex-wrap items-center gap-x-4 gap-y-2">
    <div class="min-w-0 flex-1">
      <h2 class="text-xl font-semibold sm:text-2xl">Overview</h2>
      <p class="text-sm text-muted">Live status of your server</p>
    </div>

    {#if needsMetrics}
      <span class="inline-flex items-center gap-1.5 text-xs text-muted" role="status">
        <span class="h-2 w-2 rounded-full {statusDot}"></span>
        {statusText}
      </span>
    {/if}

    {#if widgets.available.length > 0}
      <div class="relative">
        <button
          bind:this={toggleButton}
          type="button"
          class="md-btn md-btn-secondary"
          aria-haspopup="dialog"
          aria-expanded={panelOpen}
          onclick={() => (panelOpen = !panelOpen)}
        >
          <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
            <path d="M4 6h10M18 6h2M4 12h2M10 12h10M4 18h12M20 18h0" />
            <circle cx="16" cy="6" r="2" />
            <circle cx="8" cy="12" r="2" />
            <circle cx="18" cy="18" r="2" />
          </svg>
          Customize
        </button>

        {#if panelOpen}
          <div
            bind:this={panel}
            role="dialog"
            aria-label="Customize widgets"
            class="absolute right-0 z-20 mt-2 max-h-[70dvh] w-80 max-w-[calc(100vw-2rem)] overflow-y-auto rounded-xl border border-line bg-surface p-3 shadow-lg"
          >
            {#each widgets.groups as group (group.source)}
              <p class="px-1 pt-2 pb-1 text-xs font-medium tracking-wider text-muted uppercase">{group.source}</p>
              {#each group.widgets as w (w.key)}
                <div class="flex items-center justify-between gap-3 px-1 py-1.5">
                  <span class="min-w-0 truncate text-sm">{w.title}</span>
                  <Switch
                    checked={widgets.isVisible(w.key)}
                    label="Show {w.title}"
                    onchange={(value) => widgets.setVisible(w.key, value)}
                  />
                </div>
              {/each}
            {/each}
            <button type="button" class="md-btn md-btn-secondary mt-3 w-full" onclick={() => widgets.showAll()}>
              Show all
            </button>
          </div>
        {/if}
      </div>
    {/if}
  </div>

  {#if widgets.available.length === 0}
    <div class="rounded-xl border border-dashed border-line p-8 text-center text-sm text-muted">
      There is nothing to show for your account. The permission <code>overview.view</code> unlocks the server widgets.
    </div>
  {:else if widgets.visible.length === 0}
    <div class="rounded-xl border border-dashed border-line p-8 text-center text-sm text-muted">
      All widgets are hidden. Use <strong>Customize</strong> to show them again.
    </div>
  {:else}
    <div class="grid grid-flow-dense grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
      {#each widgets.visible as w (w.key)}
        <div class="min-w-0 {SPAN[w.size]}">
          {#if w.component}
            {@const Widget = w.component}
            <Widget />
          {:else}
            <ModuleWidget def={w} />
          {/if}
        </div>
      {/each}
    </div>
  {/if}
</div>