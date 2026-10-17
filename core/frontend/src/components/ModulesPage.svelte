<script lang="ts">
  import { auth } from '../lib/auth.svelte';
  import { modules } from '../lib/modules.svelte';
  import ModuleIcon from './ModuleIcon.svelte';

  let busy = $state<string | null>(null);
  let error = $state('');

  const canManage = $derived(auth.can('modules.manage'));

  async function toggle(id: string, enable: boolean) {
    if (busy) return;
    busy = id;
    error = '';
    try {
      await modules.setEnabled(id, enable);
    } catch (e) {
      error = e instanceof Error ? e.message : 'Request failed';
      // A failed start changes the module state on the server, so reload it
      await modules.refresh();
    } finally {
      busy = null;
    }
  }

  function badgeClass(state: string): string {
    if (state === 'enabled') return 'bg-accent/15 text-accent';
    if (state === 'failed') return 'bg-danger/10 text-danger';
    return 'bg-surface-2 text-muted';
  }
</script>

<div class="mx-auto max-w-3xl">
  <h2 class="text-xl font-semibold sm:text-2xl">Modules</h2>
  <p class="mt-1 text-sm text-muted">
    Enable or disable modules. New modules are detected when the server starts.
  </p>

  {#if error}
    <p class="mt-6 rounded-lg bg-danger/10 px-3 py-2 text-sm text-danger" role="alert">{error}</p>
  {/if}

  {#if !modules.loaded}
    <p class="mt-8 text-sm text-muted">Loading…</p>
  {:else if modules.list.length === 0}
    <div class="mt-8 rounded-xl border border-dashed border-line p-8 text-center text-sm text-muted">
      No modules found in <code class="rounded bg-surface-2 px-1.5 py-0.5">plugins/MineDash/modules</code>.
    </div>
  {:else}
    <ul class="mt-6 space-y-3">
      {#each modules.list as m (m.id)}
        <li class="rounded-xl border border-line bg-surface p-4">
          <div class="flex items-center gap-3">
            <ModuleIcon name={m.name} icon={m.icon} class="h-10 w-10 text-base" />
            <div class="min-w-0 flex-1">
              <div class="flex flex-wrap items-center gap-2">
                <span class="font-medium">{m.name}</span>
                <span class="text-xs text-muted">v{m.version}</span>
                <span class="rounded-full px-2 py-0.5 text-xs font-medium {badgeClass(m.state)}">{m.state}</span>
              </div>
              {#if m.description}
                <p class="mt-1 text-sm text-muted">{m.description}</p>
              {/if}
              {#if m.dependencies.length > 0}
                <p class="mt-1 text-xs text-muted">Requires: {m.dependencies.join(', ')}</p>
              {/if}
              {#if m.error}
                <p class="mt-2 text-sm text-danger">{m.error}</p>
              {/if}
            </div>

            <button
              type="button"
              role="switch"
              aria-checked={m.state === 'enabled'}
              aria-label="Enable {m.name}"
              disabled={!canManage || busy !== null}
              onclick={() => toggle(m.id, m.state !== 'enabled')}
              class="relative inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full transition focus-visible:ring-2 focus-visible:ring-accent/50 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-50 {m.state ===
              'enabled'
                ? 'bg-accent'
                : 'bg-line'}"
            >
              <span
                class="inline-block h-5 w-5 rounded-full bg-white shadow transition-transform {m.state === 'enabled'
                  ? 'translate-x-5.5'
                  : 'translate-x-0.5'}"
              ></span>
            </button>
          </div>
        </li>
      {/each}
    </ul>
  {/if}

  {#if !canManage}
    <p class="mt-6 text-sm text-muted">You need the permission <code>modules.manage</code> to change modules.</p>
  {/if}
</div>