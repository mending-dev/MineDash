# Example: the Notes module

A complete module that uses almost everything the API offers:

- a **configuration file** (`config.json`) with defaults
- **storage** in the data folder, written safely
- **permissions** (`notes.view`, `notes.write`)
- **HTTP routes** with input checks and error statuses
- a **WebSocket channel** that pushes changes to all open pages
- a **page** and an **overview card**

Users with `notes.view` see the notes. Users with `notes.write` can add and delete notes. Everybody sees changes of others at once.

> **Status of this code:** The Java classes were compiled against the module API with `--release 8`, and the TypeScript was checked with `tsc --strict`. The module was **not** run inside a live server for this documentation. Test it on your server first, and report differences.

## Files

```text
modules/notes/
├── build.gradle.kts
├── frontend/
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   └── src/
│       ├── main.ts
│       └── minedash.d.ts          copy from the Frontend page
└── src/main/
    ├── java/com/example/notes/
    │   ├── NotesModule.java
    │   ├── NotesConfig.java
    │   ├── Note.java
    │   └── NoteStore.java
    └── resources/module.json
```

## `module.json`

`src/main/resources/module.json`

```json
{
  "id": "notes",
  "name": "Notes",
  "version": "${version}",
  "description": "Shared notes for the team",
  "authors": [
    "You"
  ],
  "main": "com.example.notes.NotesModule",
  "apiVersion": 1,
  "frontend": "notes.js",
  "widgets": [
    {
      "id": "count",
      "title": "Notes",
      "size": "small",
      "permission": "notes.view"
    }
  ],
  "dependencies": []
}
```

The card is declared here and drawn by `mountWidget` in the frontend. `notes.view` hides the card from users who could not read the data anyway.

## `build.gradle.kts`

```kotlin
// Adds the tasks npmInstall and buildFrontend for modules/notes/frontend
apply(from = rootProject.file("gradle/frontend.gradle.kts"))

dependencies {
    // Modules only depend on the API, never on core
    compileOnly(project(":api"))
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
}

tasks.processResources {
    val version = project.version
    inputs.property("version", version)
    filesMatching("module.json") {
        expand("version" to version)
    }

    // Bundle the module frontend into the jar under /web
    from(tasks.named("buildFrontend")) {
        into("web")
    }
}
```

## The configuration

`src/main/java/com/example/notes/NotesConfig.java`

```java
package com.example.notes;

/** Content of plugins/MineDash/data/notes/config.json. Gson fills the fields, so they must not be final. */
public class NotesConfig {

    /** Longest note in characters. */
    public int maxLength = 500;

    /** Most notes that are kept. The oldest ones are dropped. */
    public int maxNotes = 200;
}
```

On the first start `plugins/MineDash/data/notes/config.json` is created:

```json
{
  "maxLength": 500,
  "maxNotes": 200
}
```

The fields are not `final`, because Gson fills them.

## The data class

`src/main/java/com/example/notes/Note.java`

```java
package com.example.notes;

/** One note, serialized to JSON as is. */
public class Note {

    public long id;
    public String text;
    public String author;
    public long createdAt;

    Note() {
    }

    Note(long id, String text, String author, long createdAt) {
        this.id = id;
        this.text = text;
        this.author = author;
        this.createdAt = createdAt;
    }
}
```

## The store

`src/main/java/com/example/notes/NoteStore.java`

```java
package com.example.notes;

import dev.mending.minedash.api.ModuleContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/** Keeps the notes in memory and in a JSON file. Handlers run on several threads, so everything is synchronized. */
final class NoteStore {

    private final ModuleContext context;
    private final Path file;
    private final int maxNotes;
    private final List<Note> notes = new ArrayList<>();
    private long nextId = 1;

    NoteStore(ModuleContext context, Path file, int maxNotes) throws IOException {
        this.context = context;
        this.file = file;
        this.maxNotes = maxNotes;
        if (Files.exists(file)) {
            String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            Note[] stored = context.fromJson(json, Note[].class);
            if (stored != null) {
                notes.addAll(Arrays.asList(stored));
                for (Note note : notes) {
                    nextId = Math.max(nextId, note.id + 1);
                }
            }
        }
    }

    synchronized List<Note> all() {
        return new ArrayList<>(notes);
    }

    synchronized int count() {
        return notes.size();
    }

    synchronized Note add(String text, String author) throws IOException {
        Note note = new Note(nextId++, text, author, System.currentTimeMillis());
        notes.add(note);
        while (notes.size() > maxNotes) {
            notes.remove(0);
        }
        save();
        return note;
    }

    /** Returns false if there is no note with this id. */
    synchronized boolean remove(long id) throws IOException {
        for (Iterator<Note> it = notes.iterator(); it.hasNext(); ) {
            if (it.next().id == id) {
                it.remove();
                save();
                return true;
            }
        }
        return false;
    }

    private void save() throws IOException {
        // Write to a temporary file first, so a crash never leaves half a file behind
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temp, context.toJson(notes).getBytes(StandardCharsets.UTF_8));
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    }
}
```

Why it looks like this:

- Route handlers run on several threads. Every public method is `synchronized`, so two requests never change the list at the same time.
- `all()` returns a **copy**, so the caller can iterate while others add notes.
- `save()` writes to `notes.json.tmp` and then **moves** the file over `notes.json`. A crash while writing never leaves a half-written `notes.json`.
- Ids come from a counter that restarts from the highest stored id.

## The module class

`src/main/java/com/example/notes/NotesModule.java`

```java
package com.example.notes;

import dev.mending.minedash.api.ApiException;
import dev.mending.minedash.api.MineDashModule;
import dev.mending.minedash.api.ModuleContext;
import dev.mending.minedash.api.WebSocketConnection;
import dev.mending.minedash.api.WebSocketHandler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NotesModule implements MineDashModule {

    /** Body of POST /notes. */
    private static final class NewNote {
        String text;
    }

    private final Set<WebSocketConnection> subscribers = ConcurrentHashMap.newKeySet();
    private ModuleContext context;
    private NoteStore store;

    @Override
    public void onEnable(ModuleContext context) throws Exception {
        this.context = context;
        final NotesConfig config = context.loadConfig(NotesConfig.class);
        this.store = new NoteStore(context, context.getDataFolder().resolve("notes.json"), config.maxNotes);

        // GET /api/modules/notes/notes
        context.get("/notes", "notes.view", request -> store.all());

        // GET /api/modules/notes/count, used by the overview widget
        context.get("/count", "notes.view", request -> Collections.singletonMap("count", store.count()));

        // POST /api/modules/notes/notes with {"text": "..."}
        context.post("/notes", "notes.write", request -> {
            NewNote body = request.getBodyAs(NewNote.class);
            String text = body.text == null ? "" : body.text.trim();
            if (text.isEmpty() || text.length() > config.maxLength) {
                throw new ApiException(400, "A note needs 1 to " + config.maxLength + " characters");
            }
            Note note = store.add(text, request.getUser().getUsername());
            broadcast("added", note);
            return note;
        });

        // DELETE /api/modules/notes/notes?id=3 (routes match the path exactly, so the id is a query parameter)
        context.delete("/notes", "notes.write", request -> {
            long id;
            try {
                id = Long.parseLong(String.valueOf(request.getQueryParam("id")));
            } catch (NumberFormatException e) {
                throw new ApiException(400, "Missing or invalid id");
            }
            if (!store.remove(id)) {
                throw new ApiException(404, "There is no note with this id");
            }
            broadcast("removed", Collections.singletonMap("id", id));
            return null;
        });

        // Browsers connect to /api/ws?module=notes&channel=live
        context.webSocket("live", "notes.view", new WebSocketHandler() {
            @Override
            public void onConnect(WebSocketConnection connection) {
                subscribers.add(connection);
            }

            @Override
            public void onMessage(WebSocketConnection connection, String message) {
                // The panel pings every 20 seconds to keep the connection open
                if ("ping".equals(message.trim())) {
                    connection.send("{\"type\":\"pong\"}");
                }
            }

            @Override
            public void onClose(WebSocketConnection connection) {
                subscribers.remove(connection);
            }
        });
    }

    @Override
    public void onDisable() {
        // The core closes the connections itself, only our own list has to be emptied
        subscribers.clear();
    }

    private void broadcast(String type, Object payload) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", type);
        message.put("data", payload);
        String json = context.toJson(message);
        for (WebSocketConnection connection : subscribers) {
            connection.send(json);
        }
    }
}
```

Things to notice:

- **Routes use exact paths.** The id to delete is a query parameter (`/notes?id=3`).
- **`getBodyAs`** parses the JSON body. `text` can still be `null`, so it is checked.
- **`ApiException(400, …)`** sends a message that the page shows to the user.
- **The author** of a note is taken from `request.getUser()`, never from the body. Do not trust the client for who they are.
- **`broadcast`** pushes the change to everybody who has the channel open.
- The ping answer keeps the socket usable behind proxies.
- `onDisable` clears the subscriber list. The core closes the sockets itself.

## The frontend

### Config files

`frontend/package.json`

```json
{
  "name": "minedash-module-notes",
  "private": true,
  "version": "1.0.0",
  "type": "module",
  "scripts": {
    "build": "vite build"
  },
  "devDependencies": {
    "typescript": "^5.9.0",
    "vite": "^7.0.0"
  }
}
```

`frontend/vite.config.ts`

```ts
import { defineConfig } from 'vite';

// Builds ONE self-contained file: dist/notes.js
export default defineConfig({
    build: {
        outDir: 'dist',
        emptyOutDir: true,
        target: 'es2020',
        lib: {
            entry: 'src/main.ts',
            formats: ['es'],
            fileName: () => 'notes.js'
        }
    }
});
```

`frontend/tsconfig.json`

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "moduleResolution": "bundler",
    "strict": true,
    "verbatimModuleSyntax": true,
    "isolatedModules": true,
    "skipLibCheck": true,
    "noEmit": true
  },
  "include": ["src/**/*.ts"]
}
```

### `frontend/src/main.ts`

```ts
import type { ModuleContext, ModuleSocket } from './minedash';

interface Note {
    id: number;
    text: string;
    author: string;
    createdAt: number;
}

interface LiveMessage {
    type: 'added' | 'removed';
    data: Note | { id: number };
}

const STYLE = `
.notes { display: grid; gap: 1rem; max-width: 40rem; color: var(--md-text); }
.notes form { display: flex; gap: 0.5rem; }
.notes input { flex: 1; padding: 0.5rem 0.75rem; border: 1px solid var(--md-border); border-radius: 0.5rem;
  background: var(--md-bg); color: var(--md-text); font: inherit; }
.notes input:focus { outline: none; border-color: var(--md-accent); }
.notes button { padding: 0.5rem 0.875rem; border: 0; border-radius: 0.5rem; cursor: pointer;
  background: var(--md-accent); color: var(--md-accent-fg); font: inherit; font-weight: 500; }
.notes button.ghost { background: transparent; color: var(--md-muted); padding: 0.25rem 0.5rem; }
.notes ul { display: grid; gap: 0.5rem; margin: 0; padding: 0; list-style: none; }
.notes li { display: flex; align-items: center; gap: 0.75rem; padding: 0.75rem 1rem;
  border: 1px solid var(--md-border); border-radius: var(--md-radius); background: var(--md-surface); }
.notes li span.text { flex: 1; overflow-wrap: anywhere; }
.notes li small { color: var(--md-muted); }
.notes .error { color: var(--md-danger); }
`;

/** Called by the panel when the user opens the module page. */
export function mount(target: HTMLElement, ctx: ModuleContext): () => void {
    const canWrite = ctx.hasPermission('notes.write');
    let notes: Note[] = [];

    const style = document.createElement('style');
    style.textContent = STYLE;
    const root = document.createElement('div');
    root.className = 'notes';
    target.append(style, root);

    const message = document.createElement('p');
    message.className = 'error';
    const list = document.createElement('ul');

    if (canWrite) {
        const form = document.createElement('form');
        const input = document.createElement('input');
        input.placeholder = 'Write a note…';
        input.maxLength = 500;
        const submit = document.createElement('button');
        submit.textContent = 'Add';
        form.append(input, submit);
        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            const text = input.value.trim();
            if (!text) return;
            try {
                message.textContent = '';
                await ctx.http.post('/notes', { text });
                input.value = '';
            } catch (error) {
                message.textContent = error instanceof Error ? error.message : 'Request failed';
            }
        });
        root.append(form);
    }
    root.append(message, list);

    function render(): void {
        list.replaceChildren(
            ...notes.map((note) => {
                const item = document.createElement('li');
                const text = document.createElement('span');
                text.className = 'text';
                // textContent, never innerHTML: notes are written by other users
                text.textContent = note.text;
                const meta = document.createElement('small');
                meta.textContent = note.author;
                item.append(text, meta);
                if (canWrite) {
                    const remove = document.createElement('button');
                    remove.className = 'ghost';
                    remove.textContent = 'Delete';
                    remove.addEventListener('click', () => {
                        ctx.http.delete(`/notes?id=${note.id}`).catch((error: unknown) => {
                            message.textContent = error instanceof Error ? error.message : 'Request failed';
                        });
                    });
                    item.append(remove);
                }
                return item;
            })
        );
    }

    function load(): void {
        ctx.http
            .get<Note[]>('/notes')
            .then((result) => {
                notes = result;
                render();
            })
            .catch((error: unknown) => {
                message.textContent = error instanceof Error ? error.message : 'Request failed';
            });
    }

    // Live updates from other users. The list is loaded again on every (re)connect, so nothing is missed.
    const socket: ModuleSocket = ctx.connect('live', {
        onStatus: (status) => {
            if (status === 'open') load();
        },
        onMessage: (raw) => {
            let event: LiveMessage;
            try {
                event = JSON.parse(raw) as LiveMessage;
            } catch {
                return; // "pong" and other text
            }
            if (event.type === 'added') {
                notes = [...notes, event.data as Note];
            } else if (event.type === 'removed') {
                const id = (event.data as { id: number }).id;
                notes = notes.filter((note) => note.id !== id);
            } else {
                return;
            }
            render();
        }
    });

    return () => {
        socket.close();
        style.remove();
        root.remove();
    };
}

/** Called by the panel for every widget of this module that is on the overview page. */
export function mountWidget(widgetId: string, target: HTMLElement, ctx: ModuleContext): () => void {
    if (widgetId !== 'count') return () => {};

    const value = document.createElement('div');
    value.style.cssText = 'font-size: 2rem; font-weight: 600; color: var(--md-text);';
    value.textContent = '–';
    target.append(value);

    const refresh = (): void => {
        ctx.http
            .get<{ count: number }>('/count')
            .then((result) => (value.textContent = String(result.count)))
            .catch(() => (value.textContent = '–'));
    };
    refresh();
    const timer = window.setInterval(refresh, 15_000);

    return () => {
        window.clearInterval(timer);
        value.remove();
    };
}
```

Things to notice:

- The page is plain DOM code to keep the example free of dependencies. A framework works the same way, see [Frontend](frontend.md).
- **`textContent`, not `innerHTML`.** Notes are written by other users.
- The form and the delete buttons are shown only if `ctx.hasPermission('notes.write')`. The server checks the permission again.
- The list is **loaded again on every socket `open`**. This covers the first connection and every reconnect, so no change is lost.
- The `onMessage` handler ignores texts that are not JSON (`pong`).
- The cleanup closes the socket and removes the `<style>` element and the root element.
- `mountWidget` draws the card and refreshes it every 15 seconds. Its cleanup stops the timer.

## Build and install

```bash
./gradlew :modules:notes:jar
cp modules/notes/build/libs/notes-*.jar /path/to/server/plugins/MineDash/modules/
```

Restart the server. Then give an account the permissions in `users.json` (or use an account with `*`):

```json
{ "username": "helper", "password": "change-me", "permissions": ["overview.view", "notes.view", "notes.write"] }
```

## Try it

1. Open **Notes** in two browsers with different accounts. Add a note in one: it appears in the other at once.
2. Log in with an account that has only `notes.view`: the form and delete buttons are not there. Try the request with `curl` anyway, it answers `403`:

   ```bash
   curl -b cookies.txt -H 'Content-Type: application/json' \
        -d '{"text":"hi"}' http://localhost:8765/api/modules/notes/notes
   # Missing permission: notes.write
   ```

3. Switch the module off on the Modules page: the page disappears and the sockets close. Switch it on again: the notes are still there.
4. Restart the server: the notes stay (they are in `plugins/MineDash/data/notes/notes.json`).

## Ideas to extend it

- Edit notes (a `PUT /notes` route and an `updated` message).
- Notes per player: store the player name, filter in the route.
- Send a message in the game when a note is added: `context.runSync(() -> Bukkit.broadcastMessage(...))`.
- Move the storage to SQLite (bundle the driver in your jar).
