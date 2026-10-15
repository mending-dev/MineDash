// Types of the object the MineDash panel passes to mount() and mountWidget().

export type SocketStatus = 'connecting' | 'open' | 'closed';

export interface SocketHandlers {
    onMessage?(data: string): void;
    onStatus?(status: SocketStatus): void;
}

export interface ModuleSocket {
    /** Returns false if the connection is not open. */
    send(data: string): boolean;
    close(): void;
}

/**
 * Every failed call of ModuleHttp throws an Error that has these properties.
 * Do not use instanceof, a module is a separate bundle. Check "status" instead.
 */
export interface ModuleHttpError extends Error {
    /** HTTP status of the answer, or 0 if the server could not be reached. */
    readonly status: number;
}

export interface UploadOptions {
    /** Defaults to the type of the Blob, or "text/plain; charset=utf-8" for strings. */
    contentType?: string;
    /** Called while the body is sent. "total" is the size of the body in bytes. */
    onProgress?: (loaded: number, total: number) => void;
    /** Cancels the upload. The promise then rejects with an Error named "AbortError". */
    signal?: AbortSignal;
}

/** HTTP helper. Paths are relative to the module, e.g. "/command", and may contain a query string. */
export interface ModuleHttp {
    get<T = unknown>(path: string): Promise<T>;
    post<T = unknown>(path: string, body?: unknown): Promise<T>;
    put<T = unknown>(path: string, body?: unknown): Promise<T>;
    delete<T = unknown>(path: string): Promise<T>;
    /**
     * Sends a raw body (file or text) with PUT and reports the progress.
     * Unlike put(), the body is not converted to JSON. Running uploads are cancelled
     * automatically when the module is unmounted.
     */
    upload<T = unknown>(path: string, body: Blob | string, options?: UploadOptions): Promise<T>;
}

export interface ModuleUser {
    readonly username: string;
    readonly permissions: readonly string[];
}

export interface ModuleContext {
    readonly moduleId: string;
    readonly user: ModuleUser;
    hasPermission(permission: string): boolean;
    readonly http: ModuleHttp;
    /**
     * Absolute URL of a route of this module, for links and downloads, e.g.
     * url("/download?path=a.txt") gives "/api/modules/files/download?path=a.txt".
     * The path must start with "/". Values inside the query string have to be encoded by the caller.
     */
    url(path: string): string;
    /**
     * Opens a WebSocket channel of this module. Reconnects automatically and
     * sends "ping" every 20 seconds, so handlers must ignore or answer that text.
     */
    connect(channel: string, handlers: SocketHandlers): ModuleSocket;
}