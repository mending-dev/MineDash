<script lang="ts">
  let { class: className = '' }: { class?: string } = $props();
</script>

<svg
  class={className}
  viewBox="0 0 24 24"
  fill="none"
  stroke="currentColor"
  stroke-width="1.8"
  stroke-linejoin="round"
  stroke-linecap="round"
  aria-hidden="true"
>
  <path d="M12 2.5 3.5 7v10l8.5 4.5 8.5-4.5V7L12 2.5Z" />
  <path d="M3.5 7 12 11.5 20.5 7M12 11.5v10" />
</svg>