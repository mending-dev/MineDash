import type { Component } from 'svelte';
import { auth } from './auth.svelte';
import { modules } from './modules.svelte';
import type { WidgetSize } from './types';
import CpuWidget from '../widgets/CpuWidget.svelte';
import DiskWidget from '../widgets/DiskWidget.svelte';
import MemoryWidget from '../widgets/MemoryWidget.svelte';
import PlayersWidget from '../widgets/PlayersWidget.svelte';
import ServerInfoWidget from '../widgets/ServerInfoWidget.svelte';
import SystemInfoWidget from '../widgets/SystemInfoWidget.svelte';
import TpsWidget from '../widgets/TpsWidget.svelte';
import UptimeWidget from '../widgets/UptimeWidget.svelte';

export type { WidgetSize };

export interface WidgetDef {
    /** Unique, e.g. "core:cpu" or "console:latest". Used to remember the visibility. */
    key: string;
    title: string;
    size: WidgetSize;
    /** Group name in the customize list. */
    source: string;
    /** Built-in widgets are Svelte components. */
    component?: Component;
    /** Module widgets are mounted by the module frontend. */
    moduleId?: string;
    widgetId?: string;
}

const CORE_PERMISSION = 'overview.view';
const CORE_SOURCE = 'Server';
const STORAGE_PREFIX = 'minedash.widgets.';

// The order here is the order on the page
const CORE_WIDGETS: WidgetDef[] = [
    { key: 'core:players', title: 'Players', size: 'small', source: CORE_SOURCE, component: PlayersWidget },
    { key: 'core:uptime', title: 'Uptime', size: 'small', source: CORE_SOURCE, component: UptimeWidget },
    { key: 'core:tps', title: 'TPS', size: 'small', source: CORE_SOURCE, component: TpsWidget },
    { key: 'core:storage', title: 'Storage', size: 'small', source: CORE_SOURCE, component: DiskWidget },
    { key: 'core:cpu', title: 'CPU usage', size: 'medium', source: CORE_SOURCE, component: CpuWidget },
    { key: 'core:memory', title: 'Memory', size: 'medium', source: CORE_SOURCE, component: MemoryWidget },
    { key: 'core:server', title: 'Server info', size: 'medium', source: CORE_SOURCE, component: ServerInfoWidget },
    { key: 'core:system', title: 'System info', size: 'medium', source: CORE_SOURCE, component: SystemInfoWidget }
];

function readHidden(username: string): Set<string> {
    try {
        const raw = localStorage.getItem(STORAGE_PREFIX + username);
        const data = raw ? JSON.parse(raw) : null;
        if (data && Array.isArray(data.hidden)) {
            return new Set(data.hidden.filter((key: unknown): key is string => typeof key === 'string'));
        }
    } catch {
        // Storage is blocked or the value is broken, everything stays visible
    }
    return new Set();
}

class WidgetStore {
    /** Keys the user switched off. Storing the hidden ones means new widgets show up automatically. */
    hidden = $state.raw<ReadonlySet<string>>(new Set());

    /** Everything this account may see, hidden or not. */
    available = $derived.by(() => {
        const list: WidgetDef[] = [];
        if (auth.can(CORE_PERMISSION)) list.push(...CORE_WIDGETS);

        for (const m of modules.list) {
            if (m.state !== 'enabled' || !m.frontend) continue;
            for (const w of m.widgets ?? []) {
                if (w.permission && !auth.can(w.permission)) continue;
                list.push({
                    key: `${m.id}:${w.id}`,
                    title: w.title,
                    size: w.size,
                    source: m.name,
                    moduleId: m.id,
                    widgetId: w.id
                });
            }
        }
        return list;
    });

    visible = $derived(this.available.filter((w) => !this.hidden.has(w.key)));

    /** The same widgets grouped by source for the customize list. */
    groups = $derived.by(() => {
        const groups: Array<{ source: string; widgets: WidgetDef[] }> = [];
        for (const w of this.available) {
            let group = groups.find((g) => g.source === w.source);
            if (!group) {
                group = { source: w.source, widgets: [] };
                groups.push(group);
            }
            group.widgets.push(w);
        }
        return groups;
    });

    private username: string | null = null;

    load(username: string): void {
        this.username = username;
        this.hidden = readHidden(username);
    }

    reset(): void {
        this.username = null;
        this.hidden = new Set();
    }

    isVisible(key: string): boolean {
        return !this.hidden.has(key);
    }

    setVisible(key: string, visible: boolean): void {
        const next = new Set(this.hidden);
        if (visible) next.delete(key);
        else next.add(key);
        this.hidden = next;
        this.persist();
    }

    showAll(): void {
        this.hidden = new Set();
        this.persist();
    }

    private persist(): void {
        if (!this.username) return;
        try {
            localStorage.setItem(STORAGE_PREFIX + this.username, JSON.stringify({ hidden: [...this.hidden] }));
        } catch {
            // Storage is blocked, the choice just is not remembered
        }
    }
}

export const widgets = new WidgetStore();