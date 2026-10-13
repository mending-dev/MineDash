<script lang="ts">
  type Tone = 'auto' | 'accent' | 'warning' | 'danger';

  let {
    value,
    label,
    tone = 'auto'
  }: { value: number; label: string; tone?: Tone } = $props();

  const ratio = $derived(Number.isFinite(value) ? Math.min(1, Math.max(0, value)) : 0);
  const color = $derived.by(() => {
    const resolved = tone === 'auto' ? (ratio >= 0.9 ? 'danger' : ratio >= 0.75 ? 'warning' : 'accent') : tone;
    return resolved === 'danger' ? 'bg-danger' : resolved === 'warning' ? 'bg-amber-500' : 'bg-accent';
  });
</script>

<div
  class="h-2 overflow-hidden rounded-full bg-surface-2"
  role="progressbar"
  aria-label={label}
  aria-valuemin="0"
  aria-valuemax="100"
  aria-valuenow={Math.round(ratio * 100)}
>
  <div class="h-full rounded-full transition-[width,background-color] duration-500 {color}" style:width="{ratio * 100}%"></div>
</div>