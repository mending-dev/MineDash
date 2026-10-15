import type { UploadOptions } from './types';

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

/** Extracts a readable message from the body of an error response. */
function errorMessage(text: string, status: number): string {
    const trimmed = text.trim();
    try {
        const data = JSON.parse(trimmed);
        if (data && typeof data.title === 'string') return data.title;
        if (data && typeof data.message === 'string') return data.message;
    } catch {
        // Plain text error
    }
    return trimmed || `Request failed (${status})`;
}

/** Parses the body of a successful response. It is either empty or JSON. */
function parseBody<T>(text: string, status: number): T {
    if (status === 204 || text === '') {
        return undefined as T;
    }
    try {
        return JSON.parse(text) as T;
    } catch {
        throw new ApiError(status, 'The server sent an invalid response');
    }
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
    const text = await res.text();
    if (!res.ok) {
        throw new ApiError(res.status, errorMessage(text, res.status));
    }
    return parseBody<T>(text, res.status);
}

function abortError(): DOMException {
    return new DOMException('The upload was cancelled', 'AbortError');
}

/**
 * Sends a raw body (file or text) with PUT and reports the progress.
 * Uses XMLHttpRequest, because fetch cannot report the progress of an upload.
 */
export function upload<T = unknown>(url: string, body: Blob | string, options: UploadOptions = {}): Promise<T> {
    return new Promise<T>((resolve, reject) => {
        const { signal, onProgress } = options;
        if (signal?.aborted) {
            reject(abortError());
            return;
        }

        const xhr = new XMLHttpRequest();
        const onAbort = () => xhr.abort();
        const cleanup = () => signal?.removeEventListener('abort', onAbort);

        xhr.open('PUT', url);
        xhr.setRequestHeader('Accept', 'application/json');
        const contentType =
            options.contentType ??
            (typeof body === 'string' ? 'text/plain; charset=utf-8' : body.type || 'application/octet-stream');
        xhr.setRequestHeader('Content-Type', contentType);

        if (onProgress) {
            xhr.upload.onprogress = (event) => {
                if (event.lengthComputable) onProgress(event.loaded, event.total);
            };
        }

        xhr.onload = () => {
            cleanup();
            const text = xhr.responseText;
            if (xhr.status === 401) {
                notifyUnauthorized();
            }
            if (xhr.status >= 200 && xhr.status < 300) {
                try {
                    resolve(parseBody<T>(text, xhr.status));
                } catch (error) {
                    reject(error);
                }
            } else {
                reject(new ApiError(xhr.status, errorMessage(text, xhr.status)));
            }
        };

        xhr.onerror = () => {
            cleanup();
            // A server that rejects a body early (e.g. too large) often shows up as a network error
            reject(new ApiError(0, 'The upload failed or was rejected by the server'));
        };

        xhr.onabort = () => {
            cleanup();
            reject(abortError());
        };

        signal?.addEventListener('abort', onAbort, { once: true });
        xhr.send(body);
    });
}

export const api = {
    get: <T = unknown>(url: string) => request<T>('GET', url),
    post: <T = unknown>(url: string, body?: unknown) => request<T>('POST', url, body),
    put: <T = unknown>(url: string, body?: unknown) => request<T>('PUT', url, body),
    delete: <T = unknown>(url: string) => request<T>('DELETE', url),
    upload
};