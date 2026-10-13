import { api, ApiError } from './api';

export interface ServerInfo {
    software: string;
    minecraftVersion: string;
    build: string;
    port: number;
    onlineMode: boolean;
    os: { name: string; version: string; arch: string };
    java: { version: string; vendor: string; vm: string };
    processors: number;
    totalMemory: number;
    maxHeap: number;
    timezone: string;
}

export interface Sample {
    seq: number;
    t: number;
    cpu: number;
    systemCpu: number;
    heapUsed: number;
    tps: number;
    players: number;
}

export interface LiveStats {
    heapCommitted: number;
    heapMax: number;
    maxPlayers: number;
    plugins: number;
    diskTotal: number;
    diskFree: number;
}

interface MetricsResponse {
    interval: number;
    capacity: number;
    reset: boolean;
    uptimeMs: number;
    current: LiveStats | null;
    samples: Sample[];
}

export type OverviewStatus = 'idle' | 'loading' | 'ok' | 'error' | 'forbidden';

const MAX_BACKOFF_MS = 15_000;

/**
 * Polls the server metrics and keeps the history. One instance is shared by all
 * core widgets. Polling only runs while at least one of them is on screen and the tab is visible.
 */
class OverviewStore {
    info = $state.raw<ServerInfo | null>(null);
    samples = $state.raw<Sample[]>([]);
    live = $state.raw<LiveStats | null>(null);
    intervalMs = $state(2000);
    capacity = $state(150);
    status = $state<OverviewStatus>('idle');
    error = $state('');
    /** Local timestamp of the server start, kept stable between polls. */
    startedAt = $state<number | null>(null);
    uptimeBase = $state(0);
    fetchedAt = $state(0);

    latest = $derived(this.samples.length > 0 ? this.samples[this.samples.length - 1] : null);
    windowMs = $derived(this.capacity * this.intervalMs);
    gapMs = $derived(this.intervalMs * 3);

    private running = false;
    private inFlight = false;
    private failures = 0;
    private lastSeq = 0;
    private timer: number | undefined;

    /** Uptime in ms, interpolated between polls. Uses the local clock only for the difference. */
    uptimeNow(): number {
        return this.uptimeBase + (performance.now() - this.fetchedAt);
    }

    start(): void {
        if (this.running) return;
        this.running = true;
        if (this.status === 'idle' || this.status === 'forbidden') this.status = 'loading';
        document.addEventListener('visibilitychange', this.onVisibility);
        this.schedule(0);
    }

    stop(): void {
        this.running = false;
        window.clearTimeout(this.timer);
        document.removeEventListener('visibilitychange', this.onVisibility);
    }

    /** Forgets everything. Called on logout. */
    reset(): void {
        this.stop();
        this.inFlight = false;
        this.failures = 0;
        this.lastSeq = 0;
        this.info = null;
        this.samples = [];
        this.live = null;
        this.startedAt = null;
        this.uptimeBase = 0;
        this.fetchedAt = 0;
        this.error = '';
        this.status = 'idle';
    }

    private onVisibility = (): void => {
        if (!this.running) return;
        if (document.hidden) {
            window.clearTimeout(this.timer);
        } else {
            // Fetches everything that was missed in one request
            this.schedule(0);
        }
    };

    private schedule(delay: number): void {
        window.clearTimeout(this.timer);
        this.timer = window.setTimeout(() => void this.poll(), delay);
    }

    private nextDelay(): number {
        if (this.failures === 0) return this.intervalMs;
        return Math.min(MAX_BACKOFF_MS, this.intervalMs * 2 ** this.failures);
    }

    private async poll(): Promise<void> {
        if (!this.running || this.inFlight || document.hidden) return;
        this.inFlight = true;
        try {
            if (!this.info) {
                this.info = await api.get<ServerInfo>('/api/core/overview/info');
            }
            const response = await api.get<MetricsResponse>(`/api/core/overview/metrics?since=${this.lastSeq}`);
            if (this.running) {
                this.apply(response);
                this.failures = 0;
                this.error = '';
                this.status = 'ok';
            }
        } catch (e) {
            if (e instanceof ApiError && e.status === 403) {
                this.status = 'forbidden';
                this.stop();
            } else if (this.running) {
                this.failures++;
                this.status = 'error';
                this.error = e instanceof Error ? e.message : 'Request failed';
            }
        } finally {
            this.inFlight = false;
            if (this.running && !document.hidden) this.schedule(this.nextDelay());
        }
    }

    private apply(response: MetricsResponse): void {
        this.intervalMs = Math.min(30_000, Math.max(1000, response.interval));
        this.capacity = Math.max(1, response.capacity);

        let samples = response.reset ? [] : this.samples;
        if (response.samples.length > 0) {
            samples = samples.concat(response.samples);
            if (samples.length > this.capacity) samples = samples.slice(samples.length - this.capacity);
        }
        this.samples = samples;
        this.lastSeq = samples.length > 0 ? samples[samples.length - 1].seq : 0;

        if (response.current) this.live = response.current;

        this.uptimeBase = response.uptimeMs;
        this.fetchedAt = performance.now();
        const startedAt = Date.now() - response.uptimeMs;
        // Network jitter must not make the displayed start time flicker
        if (this.startedAt === null || Math.abs(startedAt - this.startedAt) > 5000) {
            this.startedAt = startedAt;
        }
    }
}

export const overview = new OverviewStore();