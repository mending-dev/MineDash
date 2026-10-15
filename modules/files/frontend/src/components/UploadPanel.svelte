<script lang="ts">
  import { formatBytes } from '../lib/format';
  import type { UploadJob, Uploader } from '../lib/uploader.svelte';

  let { uploader }: { uploader: Uploader } = $props();

  /** Rows rendered at once. Uploads that are done only count in the summary. */
  const MAX_ROWS = 100;

  let open = $state(true);

  const rows = $derived(uploader.jobs.filter((job) => job.status !== 'done'));
  const shown = $derived(rows.slice(0, MAX_ROWS));

  const summary = $derived.by(() => {
    if (uploader.active) {
      const total = uploader.done + uploader.queued + uploader.uploading;
      return `Uploading · ${uploader.done} of ${total} done · ${uploader.percent}%`;
    }
    const parts: string[] = [];
    if (uploader.failed > 0) parts.push(`${uploader.failed} failed`);
    if (uploader.cancelled > 0) parts.push(`${uploader.cancelled} cancelled`);
    return parts.join(', ');
  });

  function percent(job: UploadJob): number {
    if (job.size === 0) return job.status === 'uploading' ? 100 : 0;
    return Math.min(100, Math.floor((job.loaded / job.size) * 100));
  }
</script>

{#if uploader.jobs.length > 0}
  <section class="panel" aria-label="Uploads">
    <header>
      <button
        type="button"
        class="toggle"
        aria-expanded={open}
        aria-label={open ? 'Hide uploads' : 'Show uploads'}
        onclick={() => (open = !open)}
      >
        <svg class:turned={!open} viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
          <path d="M6 9l6 6 6-6" />
        </svg>
      </button>
      <span class="summary">{summary}</span>
      <span class="spacer"></span>
      {#if uploader.active}
        <button type="button" class="mdf-btn ghost" onclick={() => uploader.cancelAll()}>Cancel all</button>
      {/if}
      {#if uploader.finished}
        <button type="button" class="mdf-btn ghost" onclick={() => uploader.clearFinished()}>Clear</button>
      {/if}
    </header>

    {#if uploader.active}
      <div
        class="bar total"
        role="progressbar"
        aria-label="Total upload progress"
        aria-valuemin="0"
        aria-valuemax="100"
        aria-valuenow={uploader.percent}
      >
        <div class="fill" style:width="{uploader.percent}%"></div>
      </div>
      <p class="hint">Uploads stop when you leave this page.</p>
    {/if}

    {#if open}
      <ul>
        {#each shown as job (job.id)}
          <li class:failed={job.status === 'failed'}>
            <div class="info">
              <span class="name" title={job.name}>{job.name}</span>
              <span class="state" class:error={job.status === 'failed'}>
                {#if job.status === 'queued'}
                  Waiting · {formatBytes(job.size)}
                {:else if job.status === 'uploading'}
                  {formatBytes(job.loaded)} of {formatBytes(job.size)} · {percent(job)}%
                {:else if job.status === 'failed'}
                  {job.error}
                {:else if job.status === 'cancelled'}
                  Cancelled
                {/if}
              </span>
              {#if job.status === 'uploading'}
                <div
                  class="bar"
                  role="progressbar"
                  aria-label="Upload progress of {job.name}"
                  aria-valuemin="0"
                  aria-valuemax="100"
                  aria-valuenow={percent(job)}
                >
                  <div class="fill" style:width="{percent(job)}%"></div>
                </div>
              {/if}
            </div>

            <div class="buttons">
              {#if job.status === 'queued' || job.status === 'uploading'}
                <button
                  type="button"
                  class="mdf-btn ghost icon"
                  title="Cancel"
                  aria-label="Cancel upload of {job.name}"
                  onclick={() => uploader.cancel(job)}
                >
                  <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" /></svg>
                </button>
              {:else}
                {#if job.conflict}
                  <button type="button" class="mdf-btn" onclick={() => uploader.retry(job, true)}>Replace</button>
                {/if}
                {#if job.retryable}
                  <button type="button" class="mdf-btn" onclick={() => uploader.retry(job)}>Retry</button>
                {/if}
                <button
                  type="button"
                  class="mdf-btn ghost icon"
                  title="Remove from list"
                  aria-label="Remove {job.name} from the list"
                  onclick={() => uploader.remove(job)}
                >
                  <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" /></svg>
                </button>
              {/if}
            </div>
          </li>
        {/each}
      </ul>
      {#if rows.length > shown.length}
        <p class="more">… and {rows.length - shown.length} more waiting</p>
      {/if}
    {/if}
  </section>
{/if}

<style>
  .panel {
    border-top: 1px solid var(--md-border);
    background: var(--md-surface);
  }

  header {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.375rem 0.75rem;
  }
  .spacer {
    flex: 1;
  }
  .summary {
    min-width: 0;
    font-size: 0.8125rem;
    font-weight: 500;
    overflow-wrap: anywhere;
  }

  .toggle {
    display: inline-grid;
    place-items: center;
    padding: 0.25rem;
    border: none;
    border-radius: 0.375rem;
    background: transparent;
    color: var(--md-muted);
    cursor: pointer;
  }
  .toggle:hover {
    background: var(--md-surface-2);
    color: var(--md-text);
  }
  .toggle:focus-visible {
    outline: 2px solid var(--md-accent);
    outline-offset: 1px;
  }
  svg {
    fill: none;
    stroke: currentColor;
    stroke-width: 2;
    stroke-linecap: round;
    stroke-linejoin: round;
  }
  .toggle svg {
    transition: transform 0.15s;
  }
  .toggle svg.turned {
    transform: rotate(-90deg);
  }

  .bar {
    height: 0.375rem;
    overflow: hidden;
    border-radius: 9999px;
    background: var(--md-surface-2);
  }
  .bar.total {
    margin: 0 0.75rem;
  }
  .fill {
    height: 100%;
    border-radius: 9999px;
    background: var(--md-accent);
    transition: width 0.15s linear;
  }

  .hint {
    margin: 0.25rem 0.75rem 0;
    color: var(--md-muted);
    font-size: 0.75rem;
  }

  ul {
    max-height: 13rem;
    margin: 0.375rem 0 0;
    padding: 0;
    overflow: auto;
    list-style: none;
    border-top: 1px solid var(--md-border);
  }
  li {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    padding: 0.5rem 0.75rem;
    border-bottom: 1px solid var(--md-border);
  }
  li:last-child {
    border-bottom: none;
  }

  .info {
    flex: 1;
    min-width: 0;
    display: grid;
    gap: 0.125rem;
  }
  .name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 0.8125rem;
    font-weight: 500;
  }
  .state {
    color: var(--md-muted);
    font-size: 0.75rem;
    overflow-wrap: anywhere;
  }
  .state.error {
    color: var(--md-danger);
  }
  .info .bar {
    margin-top: 0.25rem;
  }

  .buttons {
    display: flex;
    flex: none;
    align-items: center;
    gap: 0.25rem;
  }

  .more {
    margin: 0;
    padding: 0.5rem 0.75rem;
    border-top: 1px solid var(--md-border);
    color: var(--md-muted);
    font-size: 0.75rem;
  }
</style>