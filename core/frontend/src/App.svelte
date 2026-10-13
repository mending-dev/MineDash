<script lang="ts">
  import { untrack } from 'svelte';
  import { auth } from './lib/auth.svelte';
  import { modules } from './lib/modules.svelte';
  import { overview } from './lib/overview.svelte';
  import { widgets } from './lib/widgets.svelte';
  import Login from './components/Login.svelte';
  import Shell from './components/Shell.svelte';

  auth.restore();

  // Runs before the DOM updates, so widget preferences are loaded before the Overview renders
  $effect.pre(() => {
    const username = auth.user?.username ?? null;
    untrack(() => {
      if (username) {
        widgets.load(username);
        modules.refresh();
      } else {
        widgets.reset();
        modules.reset();
        overview.reset();
      }
    });
  });
</script>

{#if !auth.ready}
  <div class="grid h-dvh place-items-center text-sm text-muted">Loading…</div>
{:else if auth.user}
  <Shell />
{:else}
  <Login />
{/if}