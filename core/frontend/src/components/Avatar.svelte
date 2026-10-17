<script lang="ts">
  let {
    name,
    template,
    class: className = 'h-6 w-6 text-xs'
  }: {
    name: string;
    /** URL with {name} and {size}. Empty or missing: only the first letter is shown. */
    template?: string;
    class?: string;
  } = $props();

  /** The rule of Minecraft: 3 to 16 letters, digits or underscores. Other names have no head. */
  const MINECRAFT_NAME = /^[A-Za-z0-9_]{3,16}$/;
  /** Edge length asked from the head service. Big enough to stay sharp on high-density screens. */
  const PIXELS = 64;

  const src = $derived.by(() => {
    if (!template || !MINECRAFT_NAME.test(name)) return undefined;
    return template.split('{name}').join(encodeURIComponent(name)).split('{size}').join(String(PIXELS));
  });

  // Remembered per address, so a new name or template starts over without any effect code
  let loadedSrc = $state<string | undefined>();
  let failedSrc = $state<string | undefined>();

  const showImage = $derived(src !== undefined && failedSrc !== src);
  const loaded = $derived(src !== undefined && loadedSrc === src);
</script>

<!-- Same box as the module icons. The letter stays until the head has arrived, and stays for good if it never does. -->
<span
  class="relative grid shrink-0 place-items-center overflow-hidden rounded-md bg-accent/15 font-semibold text-accent {className}"
  aria-hidden="true"
>
  {#if !loaded}
    <span>{name.charAt(0).toUpperCase() || '?'}</span>
  {/if}
  {#if showImage}
    <img
      {src}
      alt=""
      referrerpolicy="no-referrer"
      decoding="async"
      draggable="false"
      class="absolute inset-0 h-full w-full object-cover [image-rendering:pixelated] {loaded ? '' : 'opacity-0'}"
      onload={() => (loadedSrc = src)}
      onerror={() => (failedSrc = src)}
    />
  {/if}
</span>
