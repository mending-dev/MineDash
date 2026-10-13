<script lang="ts">
  import { onMount, tick } from 'svelte';
  import type { ModuleContext, ModuleSocket, SocketStatus } from './minedash';

  interface LogEntry {
    id: number;
    time: number;
    level: string;
    logger: string;
    message: string;
  }

  let { ctx }: { ctx: ModuleContext } = $props();

  // Keeps the DOM small. The server itself keeps 1000 lines of history.
  const MAX_LINES = 2000;

  const canExecute = $derived(ctx.hasPermission('console.execute'));

  let entries = $state.raw<LogEntry[]>([]);
  let status = $state<SocketStatus>('connecting');
  let stickToBottom = $state(true);
  let command = $state('');
  let sending = $state(false);
  let error = $state('');
  let viewport = $state<HTMLDivElement>();
  let input = $state<HTMLInputElement>();

  // Incoming lines are collected and rendered once per animation frame
  let pending: LogEntry[] = [];
  let frame = 0;
  let socket: ModuleSocket | undefined;

  // Previously sent commands, newest first
  const sent: string[] = [];
  let sentIndex = -1;

  function limit(list: LogEntry[]): LogEntry[] {
    return list.length > MAX_LINES ? list.slice(list.length - MAX_LINES) : list;
  }

  async function scrollToBottom() {
    await tick();
    if (viewport) viewport.scrollTop = viewport.scrollHeight;
  }

  function flush() {
    frame = 0;
    if (pending.length === 0) return;
    entries = limit(entries.concat(pending));
    pending = [];
    if (stickToBottom) scrollToBottom();
  }

  function onMessage(raw: string) {
    let message: { type?: string; entries?: LogEntry[]; entry?: LogEntry };
    try {
      message = JSON.parse(raw);
    } catch {
      return;
    }

    if (message.type === 'history' && Array.isArray(message.entries)) {
      // Sent after every (re)connect, it replaces everything shown
      pending = [];
      entries = limit(message.entries);
      stickToBottom = true;
      scrollToBottom();
    } else if (message.type === 'log' && message.entry) {
      pending.push(message.entry);
      if (frame === 0) frame = requestAnimationFrame(flush);
    }
  }

  function onScroll() {
    if (!viewport) return;
    stickToBottom = viewport.scrollHeight - viewport.scrollTop - viewport.clientHeight < 40;
  }

  function jumpToLatest() {
    stickToBottom = true;
    scrollToBottom();
  }

  function clear() {
    pending = [];
    entries = [];
  }

  async function send() {
    const text = command.trim();
    if (!text || sending || !canExecute) return;

    sending = true;
    error = '';
    try {
      await ctx.http.post('/command', { command: text });
      if (sent[0] !== text) sent.unshift(text);
      sentIndex = -1;
      command = '';
      stickToBottom = true;
      scrollToBottom();
    } catch (e) {
      error = e instanceof Error ? e.message : 'Command failed';
    } finally {
      sending = false;
      input?.focus();
    }
  }

  function onKeydown(event: KeyboardEvent) {
    if (event.key === 'Enter') {
      event.preventDefault();
      send();
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      if (sentIndex < sent.length - 1) {
        sentIndex++;
        command = sent[sentIndex];
      }
    } else if (event.key === 'ArrowDown') {
      event.preventDefault();
      if (sentIndex > 0) {
        sentIndex--;
        command = sent[sentIndex];
      } else {
        sentIndex = -1;
        command = '';
      }
    }
  }

  function formatTime(ms: number): string {
    // "12:34:56" in the local time zone
    return new Date(ms).toTimeString().slice(0, 8);
  }

  onMount(() => {
    socket = ctx.connect('logs', {
      onStatus: (s) => (status = s),
      onMessage
    });
    return () => {
      socket?.close();
      if (frame !== 0) cancelAnimationFrame(frame);
    };
  });
</script>

<div class="console">
  <div class="bar">
    <span class="status">
      <span class="dot" class:open={status === 'open'} class:connecting={status === 'connecting'} class:closed={status === 'closed'}></span>
      {status === 'open' ? 'Connected' : status === 'connecting' ? 'Connecting…' : 'Disconnected'}
    </span>
    <span class="spacer"></span>
    <button type="button" class="ghost" onclick={clear}>Clear</button>
  </div>

  <div class="viewport">
    <div class="log" bind:this={viewport} onscroll={onScroll} role="log" aria-live="off">
      {#each entries as entry (entry.id)}
        <div
          class="line"
          class:warn={entry.level === 'WARN'}
          class:error={entry.level === 'ERROR' || entry.level === 'FATAL'}
          class:debug={entry.level === 'DEBUG' || entry.level === 'TRACE'}
        ><span class="time">{formatTime(entry.time)}</span> <span class="level">{entry.level}</span> {entry.message}</div>
      {/each}
    </div>
    {#if !stickToBottom}
      <button type="button" class="jump" onclick={jumpToLatest}>Jump to latest ↓</button>
    {/if}
  </div>

  <div class="prompt">
    <span class="chevron" aria-hidden="true">›</span>
    <input
      bind:this={input}
      bind:value={command}
      onkeydown={onKeydown}
      type="text"
      autocomplete="off"
      autocapitalize="off"
      spellcheck="false"
      disabled={!canExecute}
      placeholder={canExecute ? 'Type a command…' : 'Read-only: missing permission console.execute'}
      aria-label="Command"
    />
    <button type="button" class="primary" onclick={send} disabled={!canExecute || sending || command.trim() === ''}>
      Send
    </button>
  </div>
  {#if error}
    <p class="error-text" role="alert">{error}</p>
  {/if}
</div>

<style>
  .console {
    display: flex;
    flex-direction: column;
    height: 100%;
    min-height: 20rem;
    overflow: hidden;
    border: 1px solid var(--md-border);
    border-radius: var(--md-radius);
    background: var(--md-surface);
    color: var(--md-text);
  }

  .bar {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    padding: 0.5rem 0.75rem;
    border-bottom: 1px solid var(--md-border);
    font-size: 0.8125rem;
  }

  .status {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    color: var(--md-muted);
  }

  .dot {
    width: 0.5rem;
    height: 0.5rem;
    border-radius: 9999px;
    background: var(--md-muted);
  }
  .dot.open {
    background: var(--md-accent);
  }
  .dot.connecting {
    background: var(--md-warning);
  }
  .dot.closed {
    background: var(--md-danger);
  }

  .spacer {
    flex: 1;
  }

  button {
    font: inherit;
    cursor: pointer;
    border-radius: 0.5rem;
  }
  button:disabled {
    cursor: not-allowed;
    opacity: 0.5;
  }

  .ghost {
    padding: 0.25rem 0.625rem;
    border: 1px solid var(--md-border);
    background: transparent;
    color: var(--md-text);
  }
  .ghost:hover {
    background: var(--md-surface-2);
  }

  .viewport {
    position: relative;
    flex: 1;
    min-height: 0;
  }

  .log {
    height: 100%;
    overflow: auto;
    padding: 0.5rem 0.75rem;
    background: var(--md-bg);
    font-family: var(--md-font-mono);
    font-size: 0.8125rem;
    line-height: 1.5;
  }

  .line {
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }
  .line.warn {
    color: var(--md-warning);
  }
  .line.error {
    color: var(--md-danger);
  }
  .line.debug {
    color: var(--md-muted);
  }

  .time {
    color: var(--md-muted);
  }

  .level {
    display: inline-block;
    min-width: 5ch;
    font-weight: 600;
  }

  .jump {
    position: absolute;
    right: 1rem;
    bottom: 1rem;
    padding: 0.375rem 0.75rem;
    border: none;
    background: var(--md-accent);
    color: var(--md-accent-fg);
    font-size: 0.8125rem;
    font-weight: 500;
    box-shadow: 0 2px 8px rgb(0 0 0 / 0.25);
  }
  .jump:hover {
    background: var(--md-accent-hover);
  }

  .prompt {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.5rem 0.75rem;
    border-top: 1px solid var(--md-border);
  }

  .chevron {
    color: var(--md-accent);
    font-family: var(--md-font-mono);
    font-weight: 700;
  }

  input {
    flex: 1;
    min-width: 0;
    padding: 0.5rem 0.625rem;
    border: 1px solid var(--md-border);
    border-radius: 0.5rem;
    background: var(--md-bg);
    color: var(--md-text);
    /* 16px prevents the zoom on focus in mobile Safari */
    font-family: var(--md-font-mono);
    font-size: 1rem;
    outline: none;
  }
  input:focus {
    border-color: var(--md-accent);
  }
  input::placeholder {
    color: var(--md-muted);
  }

  .primary {
    padding: 0.5rem 0.875rem;
    border: none;
    background: var(--md-accent);
    color: var(--md-accent-fg);
    font-weight: 500;
  }
  .primary:hover:not(:disabled) {
    background: var(--md-accent-hover);
  }

  .error-text {
    margin: 0;
    padding: 0 0.75rem 0.5rem;
    color: var(--md-danger);
    font-size: 0.8125rem;
  }
</style>