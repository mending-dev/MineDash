/** Logged-in user as returned by /api/auth/me. */
export interface User {
    username: string;
    permissions: string[];
    /** Template for the player heads with {name} and {size}. Empty if heads are switched off. */
    avatarUrl?: string;
}

export type WidgetSize = 'small' | 'medium' | 'large';

/** Overview widget declared by a module in module.json. */
export interface WidgetEntry {
    id: string;
    title: string;
    size: WidgetSize;
    permission?: string;
}

/** Module as returned by /api/core/modules. */
export interface ModuleEntry {
    id: string;
    name: string;
    version: string;
    description: string;
    authors: string[];
    dependencies: string[];
    widgets: WidgetEntry[];
    state: 'enabled' | 'disabled' | 'failed';
    error?: string;
    /** Cleaned SVG markup of the icon. Missing if the module has no icon. */
    icon?: string;
    /** URL of the frontend entry file, only present while the module is enabled. */
    frontend?: string;
}

// ---------------------------------------------------------------------------
// Module frontend contract. Module authors copy these types into their project.
// ---------------------------------------------------------------------------

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

type Cleanup = void | (() => void);

/** Mounts the module page. The returned function unmounts it. */
export type MountFunction = (target: HTMLElement, ctx: ModuleContext) => Cleanup | Promise<Cleanup>;

/** Mounts one overview widget declared in module.json. The returned function unmounts it. */
export type MountWidgetFunction = (
    widgetId: string,
    target: HTMLElement,
    ctx: ModuleContext
) => Cleanup | Promise<Cleanup>;

/** Everything the panel reads from a module bundle. Both exports are optional. */
export interface ModuleBundle {
    mount?: MountFunction;
    mountWidget?: MountWidgetFunction;
}