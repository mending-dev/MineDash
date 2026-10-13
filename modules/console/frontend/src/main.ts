import { mount as mountComponent, unmount } from 'svelte';
import Console from './Console.svelte';
import LogTail from './LogTail.svelte';
import type { ModuleContext } from './minedash';

/** Page of the module, called by the MineDash panel. Returns the cleanup function. */
export function mount(target: HTMLElement, ctx: ModuleContext): () => void {
    const app = mountComponent(Console, { target, props: { ctx } });
    return () => {
        unmount(app);
    };
}

/** Overview widgets declared in module.json. Returns the cleanup function. */
export function mountWidget(widgetId: string, target: HTMLElement, ctx: ModuleContext): () => void {
    if (widgetId !== 'latest') {
        throw new Error(`Unknown widget: ${widgetId}`);
    }
    const app = mountComponent(LogTail, { target, props: { ctx } });
    return () => {
        unmount(app);
    };
}