<script lang="ts">
  let {
    checked,
    label,
    disabled = false,
    onchange
  }: { checked: boolean; label: string; disabled?: boolean; onchange: (value: boolean) => void } = $props();
</script>

<button
  type="button"
  role="switch"
  aria-checked={checked}
  aria-label={label}
  {disabled}
  onclick={() => onchange(!checked)}
  class="relative inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full transition focus-visible:ring-2 focus-visible:ring-accent/50 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-50 {checked
    ? 'bg-accent'
    : 'bg-line'}"
>
  <span
    class="inline-block h-5 w-5 rounded-full bg-white shadow transition-transform {checked
      ? 'translate-x-5.5'
      : 'translate-x-0.5'}"
  ></span>
</button>