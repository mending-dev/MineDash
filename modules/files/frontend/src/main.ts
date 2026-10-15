import { mount as mountComponent, unmount } from 'svelte';
import FileExplorer from './components/FileExplorer.svelte';
import type { ModuleContext } from './minedash';

/** Page of the module, called by the MineDash panel. Returns the cleanup function. */
export function mount(target: HTMLElement, ctx: ModuleContext): () => void {
    const app = mountComponent(FileExplorer, { target, props: { ctx } });
    return () => {
        unmount(app);
    };
}