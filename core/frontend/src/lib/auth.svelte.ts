import { api, setUnauthorizedHandler } from './api';
import type { User } from './types';

class AuthState {
    user = $state<User | null>(null);
    /** True once the initial session check has finished. */
    ready = $state(false);

    async restore(): Promise<void> {
        try {
            this.user = await api.get<User>('/api/auth/me');
        } catch {
            this.user = null;
        }
        this.ready = true;
    }

    async login(username: string, password: string): Promise<void> {
        this.user = await api.post<User>('/api/auth/login', { username, password });
    }

    async logout(): Promise<void> {
        try {
            await api.post('/api/auth/logout');
        } finally {
            this.user = null;
        }
    }

    /** Same rules as the server: exact match, "*" and prefix wildcards like "console.*". */
    can(permission: string): boolean {
        const granted = this.user?.permissions ?? [];
        return granted.some(
            (p) => p === '*' || p === permission || (p.endsWith('.*') && permission.startsWith(p.slice(0, -1)))
        );
    }
}

export const auth = new AuthState();

setUnauthorizedHandler(() => {
    auth.user = null;
});