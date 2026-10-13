<script lang="ts">
  import { onMount } from 'svelte';
  import type { ModuleContext, SocketStatus } from './minedash';

  interface LogEntry {
    id: number;
    time: number;
    level: string;
    message: string;
  }

  let { ctx }: { ctx: ModuleContext } = $props();

  const LINES = 8;

  let entries = $state.raw<LogEntry[]>([]);
  let status = $state<SocketStatus>('connecting');

  // Incoming lines are collected and rendered once per animation frame
  let pending: LogEntry[] = [];
  let frame = 0;

  function flush() {
    frame = 0;
    if (pending.length === 0) return;
    entries = entries.concat(pending).slice(-LINES);
    pending = [];
  }

  function onMessage(raw: string) {
    let message: { type?: string; entries?: LogEntry[]; entry?: LogEntry };
    try {
      message = JSON.parse(raw);
    } catch {
      return;
    }

    if (message.type === 'history' && Array.isArray(message.entries)) {
      pending = [];
      entries = message.entries.slice(-LINES);
    } else if (message.type === 'log' && message.entry) {
      pending.push(message.entry);
      if (frame === 0) frame = requestAnimationFrame(flush);
    }
  }

  function firstLine(text: string): string {
    const newline = text.indexOf('\n');
    return newline < 0 ? text : text.slice(0, newline);
  }

  function formatTime(ms: number): string {
    return new Date(ms).toTimeString().slice(0, 8);
  }

  onMount(() => {
    const socket = ctx.connect('logs', { onStatus: (s) => (status = s), onMessage });
    return () => {
      socket.close();
      if (frame !== 0) cancelAnimationFrame(frame);
    };
  });
</script>

<div class="tail">
  <div class="lines" role="log" aria-live="off">
    {#each entries as entry (entry.id)}
      <div
        class="line"
        class:warn={entry.level === 'WARN'}
        class:error={entry.level === 'ERROR' || entry.level === 'FATAL'}
        title={entry.message}
      >
        <span class="time">{formatTime(entry.time)}</span>
        {firstLine(entry.message)}
      </div>
    {:else}
      <div class="empty">{status === 'open' ? 'No output yet' : 'Connecting…'}</div>
    {/each}
  </div>
  <a class="link" href="#/m/console">Open console →</a>
</div>

<style>
  .tail {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
    height: 100%;
  }

  .lines {
    flex: 1;
    min-height: 11rem;
    overflow: hidden;
    padding: 0.5rem 0.625rem;
    border: 1px solid var(--md-border);
    border-radius: 0.5rem;
    background: var(--md-bg);
    font-family: var(--md-font-mono);
    font-size: 0.75rem;
    line-height: 1.6;
  }

  .line {
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
    color: var(--md-text);
  }
  .line.warn {
    color: var(--md-warning);
  }
  .line.error {
    color: var(--md-danger);
  }

  .time {
    color: var(--md-muted);
  }

  .empty {
    color: var(--md-muted);
  }

  .link {
    align-self: flex-end;
    color: var(--md-accent);
    font-size: 0.8125rem;
    font-weight: 500;
    text-decoration: none;
  }
  .link:hover {
    color: var(--md-accent-hover);
    text-decoration: underline;
  }
</style>