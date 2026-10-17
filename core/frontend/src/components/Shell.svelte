<script lang="ts">
  import { auth } from '../lib/auth.svelte';
  import { modules } from '../lib/modules.svelte';
  import { router } from '../lib/router.svelte';
  import Avatar from './Avatar.svelte';
  import Logo from './Logo.svelte';
  import ThemeToggle from './ThemeToggle.svelte';
  import Overview from './Overview.svelte';
  import ModulesPage from './ModulesPage.svelte';
  import ModuleIcon from './ModuleIcon.svelte';
  import ModuleView from './ModuleView.svelte';

  let menuOpen = $state(false);

  function safeDecode(value: string): string {
    try {
      return decodeURIComponent(value);
    } catch {
      return value;
    }
  }

  const moduleId = $derived(router.path.startsWith('/m/') ? safeDecode(router.path.slice(3)) : null);

  const title = $derived.by(() => {
    if (moduleId !== null) return modules.list.find((m) => m.id === moduleId)?.name ?? 'Module';
    if (router.path === '/modules') return 'Modules';
    return 'Overview';
  });

  function linkClass(active: boolean): string {
    return (
      'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition ' +
      (active ? 'bg-accent/15 text-accent' : 'text-muted hover:bg-surface-2 hover:text-fg')
    );
  }

  function closeMenu() {
    menuOpen = false;
  }
</script>

<div class="flex h-dvh bg-bg text-fg">
  {#if menuOpen}
    <button
      type="button"
      class="fixed inset-0 z-30 bg-black/50 lg:hidden"
      aria-label="Close menu"
      onclick={closeMenu}
    ></button>
  {/if}

  <aside
    class="fixed inset-y-0 left-0 z-40 flex w-64 flex-col border-r border-line bg-surface transition-transform lg:static lg:translate-x-0 {menuOpen
      ? 'translate-x-0'
      : '-translate-x-full'}"
  >
    <div class="flex h-14 shrink-0 items-center gap-2.5 border-b border-line px-4">
      <Logo class="h-7 w-7 text-accent" />
      <span class="text-base font-semibold">MineDash</span>
    </div>

    <nav class="flex-1 space-y-1 overflow-y-auto p-3">
      <a href="#/" class={linkClass(router.path === '/')} onclick={closeMenu}>Overview</a>

      {#if modules.navigable.length > 0}
        <p class="px-3 pt-4 pb-1 text-xs font-medium tracking-wider text-muted uppercase">Modules</p>
        {#each modules.navigable as m (m.id)}
          <a href="#/m/{m.id}" class={linkClass(moduleId === m.id)} onclick={closeMenu}>
            <ModuleIcon name={m.name} icon={m.icon} />
            <span class="truncate">{m.name}</span>
          </a>
        {/each}
      {/if}

      {#if auth.can('modules.manage')}
        <p class="px-3 pt-4 pb-1 text-xs font-medium tracking-wider text-muted uppercase">System</p>
        <a href="#/modules" class={linkClass(router.path === '/modules')} onclick={closeMenu}>Manage modules</a>
      {/if}
    </nav>

    <div class="flex shrink-0 items-center gap-2 border-t border-line p-3">
      <Avatar name={auth.user?.username ?? ''} template={auth.user?.avatarUrl} class="h-7 w-7 text-xs" />
      <span class="min-w-0 flex-1 truncate text-sm text-muted">{auth.user?.username}</span>
      <button type="button" class="md-btn md-btn-secondary px-2" onclick={() => auth.logout()} aria-label="Sign out" title="Sign out">
        <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9" />
        </svg>
      </button>
    </div>
  </aside>

  <div class="flex min-w-0 flex-1 flex-col">
    <header class="flex h-14 shrink-0 items-center gap-3 border-b border-line bg-surface px-4">
      <button
        type="button"
        class="md-btn md-btn-secondary px-2 lg:hidden"
        aria-label="Open menu"
        onclick={() => (menuOpen = true)}
      >
        <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
          <path d="M4 6h16M4 12h16M4 18h16" />
        </svg>
      </button>
      <h1 class="min-w-0 flex-1 truncate text-sm font-semibold sm:text-base">{title}</h1>
      <ThemeToggle />
    </header>

    <main class="min-h-0 flex-1 overflow-auto p-4 sm:p-6">
      {#if moduleId !== null}
        {#key moduleId}
          <ModuleView id={moduleId} />
        {/key}
      {:else if router.path === '/modules'}
        <ModulesPage />
      {:else}
        <Overview />
      {/if}
    </main>
  </div>
</div>