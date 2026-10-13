<script lang="ts">
  import { untrack } from 'svelte';
  import { modules } from '../lib/modules.svelte';
  import { createModuleContext, loadBundle } from '../lib/moduleHost';

  let { id }: { id: string } = $props();

  let host = $state<HTMLDivElement>();
  let error = $state('');

  const info = $derived(modules.list.find((m) => m.id === id));
  const entry = $derived(info && info.state === 'enabled' ? info.frontend : undefined);

  // Loads the module bundle and mounts it into the host element
  $effect(() => {
    const target = host;
    const url = entry;
    if (!target || !url) return;

    let cancelled = false;
    let unmount: (() => void) | undefined;
    const ctx = untrack(() => createModuleContext(id));
    error = '';

    (async () => {
      try {
        const bundle = await loadBundle(url);
        if (typeof bundle.mount !== 'function') {
          throw new Error('The module frontend does not export a mount() function');
        }
        const result = await bundle.mount(target, ctx);
        const cleanup = typeof result === 'function' ? result : undefined;
        if (cancelled) {
          cleanup?.();
        } else {
          unmount = cleanup;
        }
      } catch (e) {
        if (!cancelled) error = e instanceof Error ? e.message : String(e);
      }
    })();

    return () => {
      cancelled = true;
      try {
        unmount?.();
      } catch (e) {
        console.error(`[MineDash] Failed to unmount module '${id}'`, e);
      }
      ctx.dispose();
      target.replaceChildren();
    };
  });
</script>

{#snippet notice(title: string, text: string)}
  <div class="mx-auto mt-10 max-w-md rounded-xl border border-line bg-surface p-6 text-center">
    <h2 class="font-semibold">{title}</h2>
    <p class="mt-2 text-sm text-muted">{text}</p>
  </div>
{/snippet}

{#if !modules.loaded}
  <p class="text-sm text-muted">Loading…</p>
{:else if !info}
  {@render notice('Module not found', `There is no module with the id "${id}".`)}
{:else if info.state === 'failed'}
  {@render notice('Module failed to start', info.error ?? 'Unknown error')}
{:else if info.state === 'disabled'}
  {@render notice('Module is disabled', 'Enable it on the Manage modules page.')}
{:else if !info.frontend}
  {@render notice('No user interface', 'This module does not provide a user interface.')}
{:else}
  {#if error}
    {@render notice('Failed to load module', error)}
  {/if}
  <div bind:this={host} class="h-full {error ? 'hidden' : ''}"></div>
{/if}