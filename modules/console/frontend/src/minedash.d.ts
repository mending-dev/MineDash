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