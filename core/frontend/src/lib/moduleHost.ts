import { api, notifyUnauthorized } from './api';
import { auth } from './auth.svelte';
import type { ModuleBundle, ModuleContext, ModuleSocket, SocketHandlers, UploadOptions } from './types';

const PING_INTERVAL_MS = 20_000;
const MAX_RETRY_DELAY_MS = 10_000;
/** Close codes the server uses for failures that a reconnect cannot fix. */
const FATAL_CLOSE_CODES = [4401, 4403, 4404];

// ---------------------------------------------------------------------------
// Bundle loading
// ---------------------------------------------------------------------------

const bundles = new Map<string, Promise<ModuleBundle>>();
const failures = new Map<string, number>();

/**
 * Loads a module bundle once and shares it between the module page and its widgets.
 * Browsers remember failed module imports for the whole page lifetime, so a retry
 * uses a different query string to force a new request.
 */
export function loadBundle(url: string): Promise<ModuleBundle> {
    const cached = bundles.get(url);
    if (cached) return cached;

    const failed = failures.get(url) ?? 0;
    const target = failed > 0 ? `${url}?retry=${failed}` : url;
    const promise = import(/* @vite-ignore */ target) as Promise<ModuleBundle>;
    bundles.set(url, promise);
    promise.catch(() => {
        bundles.delete(url);
        failures.set(url, failed + 1);
    });
    return promise;
}

// ---------------------------------------------------------------------------
// WebSocket
// ---------------------------------------------------------------------------

function openSocket(moduleId: string, channel: string, handlers: SocketHandlers): ModuleSocket {
    let socket: WebSocket | null = null;
    let closed = false;
    let attempt = 0;
    let pingTimer: number | undefined;
    let retryTimer: number | undefined;

    const connect = () => {
        if (closed) return;
        handlers.onStatus?.('connecting');

        const scheme = location.protocol === 'https:' ? 'wss:' : 'ws:';
        const query = `module=${encodeURIComponent(moduleId)}&channel=${encodeURIComponent(channel)}`;
        const ws = new WebSocket(`${scheme}//${location.host}/api/ws?${query}`);
        socket = ws;

        ws.onopen = () => {
            attempt = 0;
            handlers.onStatus?.('open');
            // The server drops idle connections, so keep this one alive
            pingTimer = window.setInterval(() => {
                if (ws.readyState === WebSocket.OPEN) ws.send('ping');
            }, PING_INTERVAL_MS);
        };

        ws.onmessage = (event) => {
            if (typeof event.data === 'string') handlers.onMessage?.(event.data);
        };

        ws.onclose = (event) => {
            window.clearInterval(pingTimer);
            handlers.onStatus?.('closed');
            if (closed) return;

            if (FATAL_CLOSE_CODES.includes(event.code)) {
                closed = true;
                if (event.code === 4401) notifyUnauthorized();
                return;
            }
            attempt++;
            const delay = Math.min(MAX_RETRY_DELAY_MS, 1000 * 2 ** (attempt - 1));
            retryTimer = window.setTimeout(connect, delay);
        };
    };

    connect();

    return {
        send(data: string): boolean {
            if (socket && socket.readyState === WebSocket.OPEN) {
                socket.send(data);
                return true;
            }
            return false;
        },
        close(): void {
            closed = true;
            window.clearTimeout(retryTimer);
            window.clearInterval(pingTimer);
            socket?.close();
        }
    };
}

// ---------------------------------------------------------------------------
// Context
// ---------------------------------------------------------------------------

export interface HostedContext extends ModuleContext {
    /** Cancels uploads and closes sockets the module opened. Called by the panel when the module is unmounted. */
    dispose(): void;
}

/** Builds the object a module frontend receives in mount() and mountWidget(). */
export function createModuleContext(moduleId: string): HostedContext {
    const user = auth.user;
    if (!user) throw new Error('Not logged in');

    const base = `/api/modules/${encodeURIComponent(moduleId)}`;
    const url = (path: string): string => {
        if (!path.startsWith('/')) throw new Error("Path must start with '/'");
        return base + path;
    };
    const sockets = new Set<ModuleSocket>();
    const uploads = new Set<AbortController>();

    const upload = <T = unknown>(path: string, body: Blob | string, options: UploadOptions = {}): Promise<T> => {
        const target = url(path);

        // One controller per upload, so dispose() can cancel it no matter what the module passed in
        const controller = new AbortController();
        const outer = options.signal;
        const forward = () => controller.abort();
        if (outer) {
            if (outer.aborted) controller.abort();
            else outer.addEventListener('abort', forward, { once: true });
        }
        uploads.add(controller);

        return api.upload<T>(target, body, { ...options, signal: controller.signal }).finally(() => {
            uploads.delete(controller);
            outer?.removeEventListener('abort', forward);
        });
    };

    return {
        moduleId,
        user: Object.freeze({ username: user.username, permissions: [...user.permissions] }),
        hasPermission: (permission) => auth.can(permission),
        http: {
            get: <T = unknown>(path: string) => api.get<T>(url(path)),
            post: <T = unknown>(path: string, body?: unknown) => api.post<T>(url(path), body),
            put: <T = unknown>(path: string, body?: unknown) => api.put<T>(url(path), body),
            delete: <T = unknown>(path: string) => api.delete<T>(url(path)),
            upload
        },
        url,
        connect(channel, handlers) {
            const socket = openSocket(moduleId, channel, handlers);
            sockets.add(socket);
            return {
                send: (data) => socket.send(data),
                close: () => {
                    sockets.delete(socket);
                    socket.close();
                }
            };
        },
        dispose() {
            for (const controller of uploads) controller.abort();
            uploads.clear();
            for (const socket of sockets) socket.close();
            sockets.clear();
        }
    };
}