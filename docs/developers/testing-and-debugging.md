# Testing and debugging

## The development loop

Modules are found when the server starts, so the basic loop is:

```text
change code → build the jar → copy it to plugins/MineDash/modules/ → restart the server → reload the page
```

Tips to make it faster:

- Keep a **test server** with only MineDash and your module. It starts in seconds on Paper.
- Let Gradle copy the jar for you. In the `build.gradle.kts` of your module:

  ```kotlin
  tasks.register<Copy>("deploy") {
      dependsOn(tasks.named("jar"))
      from(tasks.named("jar"))
      into("/path/to/test-server/plugins/MineDash/modules")
  }
  ```

  Then `./gradlew :modules:notes:deploy` builds and copies. Remove old jars of the same module from that folder first: two jars with the same id are not both loaded.
- Switching the module **off and on** on the Modules page runs `onDisable` and `onEnable` again without a restart. This is useful to test cleanup, but it does not load a changed jar.
- **Do not replace a jar while the server is running.** The jar is open, and the result is undefined.
- After a frontend change, reload the page with the cache disabled (the browser tools have an option for it), because the browser may keep the old file.

## Read the log

Everything about modules is in `logs/latest.log`. Useful lines:

| Line | Meaning |
| --- | --- |
| `Found 3 module(s), 3 enabled.` | Scan result at start |
| `Failed to load module x.jar: …` | The jar was rejected (module.json, apiVersion, …) |
| `Skipping x.jar: module 'id' is already loaded from y.jar` | Duplicate id |
| `Enabled module Name vX` | `onEnable` succeeded |
| `Failed to enable: …` | `onEnable` threw. The stack trace follows. |
| `Module 'id' failed on GET /path` | A route handler threw. The client got `500`. |
| `Module 'id': icon ignored, …` | Problem with the icon |

Your own logger adds the module id in front: `context.getLogger().info("…")`.

## Test the backend without the browser

1. Log in once and keep the cookie:

   ```bash
   curl -sS -c cookies.txt -H 'Content-Type: application/json' \
     -d '{"username":"admin","password":"…"}' http://localhost:8765/api/auth/login
   ```

2. Call your routes:

   ```bash
   curl -sS -b cookies.txt http://localhost:8765/api/modules/notes/notes
   curl -sS -b cookies.txt -H 'Content-Type: application/json' \
     -d '{"text":"first"}' http://localhost:8765/api/modules/notes/notes
   curl -sS -b cookies.txt -X DELETE 'http://localhost:8765/api/modules/notes/notes?id=1'
   ```

   Add `-i` to see the status and headers.

3. Test the permission handling with a second account that has fewer permissions.

4. WebSocket from a terminal (needs the `Origin` of the panel and the cookie):

   ```bash
   # npm install -g wscat
   wscat -c 'ws://localhost:8765/api/ws?module=notes&channel=live' \
     -H "Cookie: minedash_session=<value from cookies.txt>"
   ```

   Without `Origin` the connection is accepted (a browser always sends one). Use a valid cookie, otherwise it closes with code `4401`.

5. `tools/smoke-test.sh <base-url> <user> <pass>` in the repository checks the core endpoints.

## Debug the frontend

- Open the **developer tools** of the browser (F12).
- *Console* shows errors from your module. A broken module shows the message in its place and the rest of the panel keeps working.
- *Network*: `Fetch/XHR` shows your calls (`/api/modules/<id>/…`), `WS` shows the socket with all messages.
- *Sources*: the bundle is minified in production builds. Set `build.minify: false` in `vite.config.ts` and `build.sourcemap: true` while you debug. Remove them before you publish.
- `console.log` is fine while developing. Remove it in the release.

Common problems:

| Symptom | Cause |
| --- | --- |
| *The module frontend does not export a mount() function* | The build is not an ES module with `export function mount`, or it renamed it. Use `lib` mode with `formats: ['es']`. |
| 404 for `/api/assets/<id>/<file>` | The `frontend` name in `module.json` does not match a file under `web/` in the jar. Check the jar: `unzip -l my-module.jar`. |
| CSP error in the console | You loaded something from another site, or use `eval`. Bundle it. |
| `Cannot reach the server` (status 0) | The server stopped or the proxy dropped the connection |
| Page works, widget does not | `mountWidget` is not exported, or the widget `id` does not match |
| Everything is unstyled | CSS is a separate file that is not loaded. Inject it into the JavaScript. |

## Inspect the jar

```bash
unzip -l build/libs/notes-1.0.0-SNAPSHOT.jar
```

A good jar has:

```text
module.json
com/example/notes/NotesModule.class
…
web/notes.js
icon.svg                         (optional)
```

If `web/…` is missing, the frontend was not built before `jar`, or `processResources` does not copy it. If `module.json` still contains `${version}`, the `expand` did not run.

## Unit tests

Keep logic that does not need Bukkit in plain classes (like `NoteStore` or parsers), and test those with JUnit. The `ModuleContext` and `ApiRequest` types are interfaces, so a small fake implementation or a mocking library is enough to test a handler.

## Performance and cleanup checks

- Switch the module off and on a few times. The count of threads and listeners must not grow. Use a profiler or `jstack` if you suspect a leak.
- Open the page, then close it. No timers or sockets should remain. The network tab shows if a socket stays open.
- Send a big or bad input to every route. The result must be a clean `4xx`, never a `500` or a hang.
