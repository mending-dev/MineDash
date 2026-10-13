<script lang="ts">
  import { theme } from '../lib/theme.svelte';
</script>

<button
  type="button"
  class="md-btn md-btn-secondary px-2"
  onclick={() => theme.toggle()}
  aria-label={theme.dark ? 'Switch to light mode' : 'Switch to dark mode'}
  title={theme.dark ? 'Switch to light mode' : 'Switch to dark mode'}
>
  <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    {#if theme.dark}
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41" />
    {:else}
      <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
    {/if}
  </svg>
</button>