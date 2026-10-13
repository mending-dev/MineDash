import { api } from './api';
import type { ModuleEntry } from './types';

class ModuleStore {
    list = $state<ModuleEntry[]>([]);
    loaded = $state(false);
    error = $state('');

    /** Enabled modules that bring a user interface. */
    navigable = $derived(this.list.filter((m) => m.state === 'enabled' && !!m.frontend));

    async refresh(): Promise<void> {
        try {
            this.list = await api.get<ModuleEntry[]>('/api/core/modules');
            this.error = '';
        } catch (e) {
            this.error = e instanceof Error ? e.message : 'Could not load modules';
        } finally {
            this.loaded = true;
        }
    }

    reset(): void {
        this.list = [];
        this.loaded = false;
        this.error = '';
    }

    async setEnabled(id: string, enabled: boolean): Promise<void> {
        const action = enabled ? 'enable' : 'disable';
        const updated = await api.post<ModuleEntry>(`/api/core/modules/${encodeURIComponent(id)}/${action}`);
        this.list = this.list.map((m) => (m.id === id ? updated : m));
    }
}

export const modules = new ModuleStore();