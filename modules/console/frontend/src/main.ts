import { mount as mountComponent, unmount } from 'svelte';
import Console from './Console.svelte';
import type { ModuleContext } from './minedash';

/** Page of the module, called by the MineDash panel. Returns the cleanup function. */
export function mount(target: HTMLElement, ctx: ModuleContext): () => void {
    const app = mountComponent(Console, { target, props: { ctx } });
    return () => {
        unmount(app);
    };
}