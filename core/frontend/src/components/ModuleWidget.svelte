<script lang="ts">
  import { untrack } from 'svelte';
  import { modules } from '../lib/modules.svelte';
  import { createModuleContext, loadBundle } from '../lib/moduleHost';
  import type { WidgetDef } from '../lib/widgets.svelte';
  import Card from './Card.svelte';

  let { def }: { def: WidgetDef } = $props();

  let host = $state<HTMLDivElement>();
  let error = $state('');
  let loading = $state(true);

  // Plain strings: the effect only reruns when one of them really changes,
  // not whenever the module list is reloaded
  const moduleId = $derived(def.moduleId ?? '');
  const widgetId = $derived(def.widgetId ?? '');
  const entry = $derived(modules.list.find((m) => m.id === moduleId)?.frontend);

  $effect(() => {
    const target = host;
    const url = entry;
    const wid = widgetId;
    if (!target || !url) return;

    let cancelled = false;
    let unmount: (() => void) | undefined;
    const ctx = untrack(() => createModuleContext(moduleId));
    error = '';
    loading = true;

    (async () => {
      try {
        const bundle = await loadBundle(url);
        if (typeof bundle.mountWidget !== 'function') {
          throw new Error('The module does not export mountWidget()');
        }
        const result = await bundle.mountWidget(wid, target, ctx);
        const cleanup = typeof result === 'function' ? result : undefined;
        if (cancelled) {
          cleanup?.();
        } else {
          unmount = cleanup;
        }
      } catch (e) {
        // One broken widget must never affect the others
        if (!cancelled) error = e instanceof Error ? e.message : String(e);
      } finally {
        if (!cancelled) loading = false;
      }
    })();

    return () => {
      cancelled = true;
      try {
        unmount?.();
      } catch (e) {
        console.error(`[MineDash] Failed to unmount widget '${moduleId}:${wid}'`, e);
      }
      ctx.dispose();
      target.replaceChildren();
    };
  });
</script>

<Card title={def.title}>
  {#if error}
    <p class="text-sm text-danger" role="alert">{error}</p>
  {/if}
  {#if loading && !error}
    <div class="h-16 animate-pulse rounded-lg bg-surface-2"></div>
  {/if}
  <div bind:this={host} class="h-full {error ? 'hidden' : ''}"></div>
</Card>