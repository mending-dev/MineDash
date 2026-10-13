export class ApiError extends Error {
    readonly status: number;

    constructor(status: number, message: string) {
        super(message);
        this.status = status;
    }
}

let unauthorizedHandler: () => void = () => {};

/** Called whenever the server says the session is gone. */
export function setUnauthorizedHandler(handler: () => void): void {
    unauthorizedHandler = handler;
}

export function notifyUnauthorized(): void {
    unauthorizedHandler();
}

async function readError(res: Response): Promise<string> {
    const text = (await res.text()).trim();
    try {
        const data = JSON.parse(text);
        if (data && typeof data.title === 'string') return data.title;
        if (data && typeof data.message === 'string') return data.message;
    } catch {
        // Plain text error
    }
    return text || `Request failed (${res.status})`;
}

export async function request<T = unknown>(method: string, url: string, body?: unknown): Promise<T> {
    const headers: Record<string, string> = { Accept: 'application/json' };
    const init: RequestInit = { method, headers, credentials: 'same-origin' };
    if (body !== undefined) {
        headers['Content-Type'] = 'application/json';
        init.body = JSON.stringify(body);
    }

    let res: Response;
    try {
        res = await fetch(url, init);
    } catch {
        throw new ApiError(0, 'Cannot reach the server');
    }

    if (res.status === 401 && url !== '/api/auth/login') {
        notifyUnauthorized();
    }
    if (!res.ok) {
        throw new ApiError(res.status, await readError(res));
    }
    if (res.status === 204) {
        return undefined as T;
    }
    const text = await res.text();
    return (text ? JSON.parse(text) : undefined) as T;
}

export const api = {
    get: <T = unknown>(url: string) => request<T>('GET', url),
    post: <T = unknown>(url: string, body?: unknown) => request<T>('POST', url, body),
    put: <T = unknown>(url: string, body?: unknown) => request<T>('PUT', url, body),
    delete: <T = unknown>(url: string) => request<T>('DELETE', url)
};