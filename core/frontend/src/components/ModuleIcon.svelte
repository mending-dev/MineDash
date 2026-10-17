<script lang="ts">
  let {
    name,
    icon,
    class: className = 'h-6 w-6 text-xs'
  }: {
    name: string;
    /** Cleaned SVG markup from the server. Missing: the first letter of the name is shown. */
    icon?: string;
    class?: string;
  } = $props();
</script>

<span
  class="grid shrink-0 place-items-center overflow-hidden rounded-md bg-accent/15 font-semibold text-accent [&>svg]:size-[62%] {className}"
  aria-hidden="true"
>
  {#if icon}
    <!-- The server rebuilds the icon from a list of allowed elements and attributes, see SvgIcon.java -->
    {@html icon}
  {:else}
    {name.charAt(0).toUpperCase() || '?'}
  {/if}
</span>
