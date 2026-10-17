# Frontend

The frontend of a module is **one JavaScript file** (an ES module) that the panel loads in the browser. It draws the module page and, if you declare any, the cards of the overview page.

You can write it in plain JavaScript, TypeScript, Svelte, React, Vue, or anything that compiles to a browser script. The panel does not care.

## The contract

Your file exports up to two functions:

```ts
export function mount(target: HTMLElement, ctx: ModuleContext): Cleanup | Promise<Cleanup>;
export function mountWidget(widgetId: string, target: HTMLElement, ctx: ModuleContext): Cleanup | Promise<Cleanup>;
// Cleanup = void | (() => void)
```

| Export | Called when | Required |
| --- | --- | --- |
| `mount` | The user opens the module page (`#/m/<id>`) | Yes, if the module has a page |
| `mountWidget` | A card of your module appears on the overview page. Called once per card, with its `id` from `module.json`. | Only if you declare `widgets` |

Both functions:

- receive a **`target`** element that is yours. Put your content inside it, do not touch anything outside.
- receive the **`ctx`** object described below.
- may be `async`. The panel waits for the result.
- may return a **cleanup function**. The panel calls it when the page or card is removed (the user navigates away, the module is switched off, the card is hidden). Remove event listeners, timers, observers and framework instances there. Also close sockets you opened, although the panel closes those for you as a safety net.
- run in the page of the panel, in the same JavaScript realm. A thrown error shows a message in the place of the module and does not break the panel.

The panel loads the file **once** and keeps it. The page may be mounted and unmounted many times, so keep no state in module-level variables that survives wrongly between mounts.

## The `ctx` object

```ts
interface ModuleContext {
    readonly moduleId: string;
    readonly user: { readonly username: string; readonly permissions: readonly string[] };
    hasPermission(permission: string): boolean;
    readonly http: ModuleHttp;
    url(path: string): string;
    connect(channel: string, handlers: SocketHandlers): ModuleSocket;
}
```

### `user` and `hasPermission`

```ts
if (ctx.hasPermission('notes.write')) { /* show the form */ }
```

Matching is the same as on the server (exact, `x.*`, `*`). **Hiding a button is a convenience, not security.** The server checks every request again.

### `http`

All paths are relative to your module: `ctx.http.get('/notes')` calls `GET /api/modules/<id>/notes`. A path starts with `/` and may contain a query string.

```ts
const list = await ctx.http.get<Note[]>('/notes');
await ctx.http.post('/notes', { text: 'Hello' });          // JSON body
await ctx.http.put('/settings', { enabled: true });
await ctx.http.delete('/notes?id=3');
await ctx.http.upload('/file?name=a.txt', blob, {          // raw body with PUT
    contentType: 'text/plain',
    onProgress: (loaded, total) => { /* … */ },
    signal: abortController.signal
});
```

| Method | Sends | Gives back |
| --- | --- | --- |
| `get<T>(path)` | `GET` | The parsed JSON, or `undefined` for `204` |
| `post<T>(path, body?)` | `POST` with a JSON body | same |
| `put<T>(path, body?)` | `PUT` with a JSON body | same |
| `delete<T>(path)` | `DELETE` | same |
| `upload<T>(path, body, options?)` | `PUT` with the raw body | same. Cancelled with `options.signal`: the promise rejects with an error named `AbortError`. |

Running uploads are cancelled automatically when the page is unmounted.

**Errors.** Every failed call rejects with an `Error`:

- `error.message` is the text the server sent (the text of your `ApiException`), or a generic text.
- `error.status` is the HTTP status, or `0` if the server could not be reached.

```ts
try {
    await ctx.http.post('/notes', { text });
} catch (error) {
    const status = (error as { status?: number }).status;
    message.textContent = status === 403 ? 'You may not do this' : (error as Error).message;
}
```

Do not use `instanceof` with the error class, your bundle has its own copy. Check `status`.

If the server answers `401` (the session ended), the panel itself sends the user back to the login page.

### `url`

Builds an absolute URL for a route of your module, for links and downloads:

```ts
link.href = ctx.url('/download?name=' + encodeURIComponent(name));
// "/api/modules/<id>/download?name=…"
```

The path must start with `/`. Encode values in the query string yourself. The browser sends the session cookie with links, so a download link to a protected route works.

### `connect`

Opens a [WebSocket channel](routes-and-websockets.md#websocket-channels) of your module:

```ts
const socket = ctx.connect('live', {
    onStatus: (status) => { /* 'connecting' | 'open' | 'closed' */ },
    onMessage: (text) => { /* a string */ }
});
socket.send('hello');   // returns false if the connection is not open
socket.close();
```

- The panel **reconnects** for you, with waits that grow up to 10 seconds. `onStatus` tells you when. After a reconnect you probably missed messages, so load the current state again when the status becomes `'open'`.
- It does not reconnect after the server closed with `4401` (session over), `4403` (forbidden) or `4404` (unknown channel).
- The panel sends `ping` every 20 seconds. Messages from the server are always strings: parse JSON yourself and ignore texts that are not JSON (like `pong`).
- Sockets are closed automatically when the page is unmounted. Call `socket.close()` in your cleanup anyway.

## Type definitions

For TypeScript, copy this file as `src/minedash.d.ts` into your frontend project (the complete file, also in `modules/console/frontend/src/minedash.d.ts` of the repository):

```ts
// Types of the object the MineDash panel passes to mount() and mountWidget().
// Copy this file into the src folder of your module frontend.

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

/**
 * Every failed call of ModuleHttp throws an Error that has these properties.
 * Do not use instanceof, a module is a separate bundle. Check "status" instead.
 */
export interface ModuleHttpError extends Error {
    /** HTTP status of the answer, or 0 if the server could not be reached. */
    readonly status: number;
}

export interface UploadOptions {
    /** Defaults to the type of the Blob, or "text/plain; charset=utf-8" for strings. */
    contentType?: string;
    /** Called while the body is sent. "total" is the size of the body in bytes. */
    onProgress?: (loaded: number, total: number) => void;
    /** Cancels the upload. The promise then rejects with an Error named "AbortError". */
    signal?: AbortSignal;
}

/** HTTP helper. Paths are relative to the module, e.g. "/command", and may contain a query string. */
export interface ModuleHttp {
    get<T = unknown>(path: string): Promise<T>;
    post<T = unknown>(path: string, body?: unknown): Promise<T>;
    put<T = unknown>(path: string, body?: unknown): Promise<T>;
    delete<T = unknown>(path: string): Promise<T>;
    /**
     * Sends a raw body (file or text) with PUT and reports the progress.
     * Unlike put(), the body is not converted to JSON. Running uploads are cancelled
     * automatically when the module is unmounted.
     */
    upload<T = unknown>(path: string, body: Blob | string, options?: UploadOptions): Promise<T>;
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
     * Absolute URL of a route of this module, for links and downloads, e.g.
     * url("/download?path=a.txt") gives "/api/modules/files/download?path=a.txt".
     * The path must start with "/". Values inside the query string have to be encoded by the caller.
     */
    url(path: string): string;
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
```

Import it with `import type { ModuleContext } from './minedash';`.

## Build a single file

The panel imports `web/<frontend>` from the jar as an ES module. It must therefore be **one file that contains everything**: your code, your framework and your CSS. Relative imports of other files and dynamic chunks are not part of the contract, because only the file named in `frontend` is guaranteed to be served at a known address.

### Vite, plain TypeScript

```ts
import { defineConfig } from 'vite';

export default defineConfig({
    build: {
        outDir: 'dist',
        emptyOutDir: true,
        target: 'es2020',
        lib: {
            entry: 'src/main.ts',
            formats: ['es'],
            fileName: () => 'my-module.js'
        }
    }
});
```

`lib` mode with the `es` format keeps your `mount` and `mountWidget` exports and builds a single file.

### Vite with Svelte 5

```ts
import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';

export default defineConfig({
    plugins: [svelte({ compilerOptions: { css: 'injected' } })],
    build: {
        outDir: 'dist',
        emptyOutDir: true,
        target: 'es2020',
        lib: { entry: 'src/main.ts', formats: ['es'], fileName: () => 'my-module.js' }
    }
});
```

`css: 'injected'` puts the component styles into the JavaScript file, so there is no separate `.css` file. `package.json` needs `svelte`, `@sveltejs/vite-plugin-svelte` and `vite`. `svelte.config.js` is `export default { preprocess: vitePreprocess() }` as in `modules/console/frontend`.

`src/main.ts`:

```ts
import { mount as mountComponent, unmount } from 'svelte';
import Page from './Page.svelte';
import type { ModuleContext } from './minedash';

export function mount(target: HTMLElement, ctx: ModuleContext): () => void {
    const app = mountComponent(Page, { target, props: { ctx } });
    return () => {
        unmount(app);
    };
}
```

### Other frameworks

The same pattern works for React (`createRoot(target).render(...)`, cleanup `root.unmount()`), Vue (`createApp(...).mount(target)`, cleanup `app.unmount()`) and others. Inject CSS into the JavaScript (a plugin such as `vite-plugin-css-injected-by-js`), or build the styles as a string and add a `<style>` element in `mount`, like the Notes example does.

### CSS

Styles are global in the page of the panel. Two rules:

1. **Prefix your class names** (`.notes-list`, `.my-module-row`), or scope them under a root class that you add to your own container (`.notes li`). Otherwise you can change the panel by accident.
2. **Remove your `<style>` element in the cleanup** if you added one by hand.

Use the panel's colours through [CSS variables](theming.md), so your module follows light and dark mode.

## Security of the frontend

- **Never put text from users into `innerHTML`.** Use `textContent`, or the escaping of your framework. Notes, player names and file names can contain anything.
- The content security policy forbids inline event attributes, scripts from other sites, and requests to other sites. `fetch` to your own routes works, anything else does not.
- Images from other sites are blocked. `data:` images are allowed.
- `eval` and `new Function` are not allowed by the policy (`script-src 'self'`). Some libraries use them, choose one that does not.
- `style-src` allows inline styles, so `element.style` and `<style>` elements work.

## Checklist for a module frontend

- [ ] One file, built as an ES module, named in `frontend`
- [ ] `export function mount` (and `mountWidget` when you declare widgets)
- [ ] Everything you add is removed in the cleanup function
- [ ] Text from users goes through `textContent` or an escaping framework
- [ ] Errors from `ctx.http` are shown to the user
- [ ] After a WebSocket reconnect the data is loaded again
- [ ] Colours come from `--md-*` variables
- [ ] Works on a phone screen (the panel is responsive)
