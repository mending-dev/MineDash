<script lang="ts">
  import { auth } from '../lib/auth.svelte';
  import Logo from './Logo.svelte';
  import ThemeToggle from './ThemeToggle.svelte';

  let username = $state('');
  let password = $state('');
  let error = $state('');
  let busy = $state(false);

  async function submit(event: SubmitEvent) {
    event.preventDefault();
    if (busy) return;
    busy = true;
    error = '';
    try {
      await auth.login(username.trim(), password);
    } catch (e) {
      error = e instanceof Error ? e.message : 'Login failed';
      password = '';
    } finally {
      busy = false;
    }
  }
</script>

<div class="relative grid min-h-dvh place-items-center px-4">
  <div class="absolute top-4 right-4">
    <ThemeToggle />
  </div>

  <form
    onsubmit={submit}
    class="w-full max-w-sm rounded-2xl border border-line bg-surface p-6 shadow-sm sm:p-8"
  >
    <div class="mb-6 flex items-center gap-3">
      <Logo class="h-9 w-9 text-accent" />
      <div>
        <h1 class="text-lg leading-tight font-semibold">MineDash</h1>
        <p class="text-sm text-muted">Sign in to continue</p>
      </div>
    </div>

    <label class="mb-4 block">
      <span class="mb-1.5 block text-sm font-medium">Username</span>
      <input
        class="md-input"
        type="text"
        autocomplete="username"
        autocapitalize="off"
        required
        bind:value={username}
      />
    </label>

    <label class="mb-5 block">
      <span class="mb-1.5 block text-sm font-medium">Password</span>
      <input
        class="md-input"
        type="password"
        autocomplete="current-password"
        required
        bind:value={password}
      />
    </label>

    {#if error}
      <p class="mb-4 rounded-lg bg-danger/10 px-3 py-2 text-sm text-danger" role="alert">{error}</p>
    {/if}

    <button type="submit" class="md-btn md-btn-primary w-full" disabled={busy}>
      {busy ? 'Signing in…' : 'Sign in'}
    </button>
  </form>
</div>