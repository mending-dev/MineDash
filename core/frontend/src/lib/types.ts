/** Logged-in user as returned by /api/auth/me. */
export interface User {
    username: string;
    permissions: string[];
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

/** HTTP helper. Paths are relative to the module, e.g. "/command". */
export interface ModuleHttp {
    get<T = unknown>(path: string): Promise<T>;
    post<T = unknown>(path: string, body?: unknown): Promise<T>;
    put<T = unknown>(path: string, body?: unknown): Promise<T>;
    delete<T = unknown>(path: string): Promise<T>;
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