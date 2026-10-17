# Quickstart: your first module

In this tutorial you build a module called **Hello**. It has a backend route that greets the logged-in user and a page that shows the greeting.

There are two ways to build it:

- **A.** Inside the MineDash repository, as a new folder in `modules/`. Gradle finds it by itself. This is the quickest way if you already build MineDash.
- **B.** As a separate project that only needs the MineDash jar. See [Project setup](project-setup.md#standalone-project).

This page uses **A**. The code is the same for both.

> You need JDK 17 or newer for Gradle, Node.js 20.19 or newer for the frontend, and a Spigot or Paper test server with the MineDash plugin. See [Requirements](../getting-started/requirements.md).

## 1. Create the folder

```text
modules/hello/
├── build.gradle.kts
├── frontend/
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   └── src/
│       ├── main.ts
│       └── minedash.d.ts
└── src/main/
    ├── java/com/example/hello/HelloModule.java
    └── resources/module.json
```

Gradle registers every folder in `modules/` that contains a `build.gradle.kts`, so you do not have to change `settings.gradle.kts`.

## 2. `module.json`

`modules/hello/src/main/resources/module.json`:

```json
{
  "id": "hello",
  "name": "Hello",
  "version": "${version}",
  "description": "Greets the logged-in user",
  "authors": ["You"],
  "main": "com.example.hello.HelloModule",
  "apiVersion": 1,
  "frontend": "hello.js",
  "dependencies": []
}
```

`${version}` is replaced by the Gradle build with the project version. Details of every field: [module.json](module-json.md).

## 3. The backend

`modules/hello/src/main/java/com/example/hello/HelloModule.java`:

```java
package com.example.hello;

import dev.mending.minedash.api.MineDashModule;
import dev.mending.minedash.api.ModuleContext;

import java.util.Collections;

public class HelloModule implements MineDashModule {

    @Override
    public void onEnable(ModuleContext context) {
        // GET /api/modules/hello/greeting, any logged-in user may call it (permission is null)
        context.get("/greeting", null, request ->
                Collections.singletonMap("message", "Hello, " + request.getUser().getUsername() + "!"));

        context.getLogger().info("Hello module enabled.");
    }
}
```

What happens here:

- `MineDashModule` is the entry point. MineDash creates the class (it needs a public constructor without arguments) and calls `onEnable`.
- `context.get(path, permission, handler)` registers a `GET` route. The path is relative to the module: the full URL is `/api/modules/hello/greeting`.
- `null` as permission means every logged-in user is allowed.
- The returned object is turned into JSON. Returning `null` answers `204 No Content`.

## 4. The frontend

`modules/hello/frontend/package.json`:

```json
{
  "name": "minedash-module-hello",
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

`modules/hello/frontend/vite.config.ts`:

```ts
import { defineConfig } from 'vite';

// Builds ONE self-contained file: dist/hello.js
export default defineConfig({
    build: {
        outDir: 'dist',
        emptyOutDir: true,
        target: 'es2020',
        lib: {
            entry: 'src/main.ts',
            formats: ['es'],
            fileName: () => 'hello.js'
        }
    }
});
```

`modules/hello/frontend/tsconfig.json`:

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

Copy the types file [`minedash.d.ts`](frontend.md#type-definitions) into `modules/hello/frontend/src/`. It describes the object the panel gives you.

`modules/hello/frontend/src/main.ts`:

```ts
import type { ModuleContext } from './minedash';

interface Greeting {
    message: string;
}

/** Called by the panel when the user opens the module page. The returned function removes the page again. */
export async function mount(target: HTMLElement, ctx: ModuleContext): Promise<() => void> {
    const box = document.createElement('div');
    box.style.cssText = 'padding: 1rem; color: var(--md-text);';
    box.textContent = 'Loading…';
    target.appendChild(box);

    try {
        const greeting = await ctx.http.get<Greeting>('/greeting');
        box.textContent = greeting.message;
    } catch (error) {
        box.textContent = error instanceof Error ? error.message : 'Request failed';
    }

    return () => box.remove();
}
```

What happens here:

- The file must be an **ES module** that exports `mount`. The panel imports it and calls `mount(target, ctx)` when the user opens the page.
- `ctx.http.get('/greeting')` calls `/api/modules/hello/greeting` with the session of the user.
- The function you return is called when the user leaves the page. Remove everything you added.
- `var(--md-text)` is a colour of the panel theme, so the page fits in light and dark mode. See [Theming](theming.md).

## 5. `build.gradle.kts`

`modules/hello/build.gradle.kts` (the same as for the built-in modules):

```kotlin
// Adds the tasks npmInstall and buildFrontend for modules/hello/frontend
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

## 6. Build and install

```bash
./gradlew :modules:hello:jar
```

The jar is `modules/hello/build/libs/hello-<version>.jar` (for example `hello-1.0.0-SNAPSHOT.jar`). Copy it:

```bash
cp modules/hello/build/libs/hello-*.jar /path/to/server/plugins/MineDash/modules/
```

**Restart the server.** Modules are found at server start only.

The log should show:

```text
[MineDash] Found 3 module(s), 3 enabled.
[MineDash] [hello] Hello module enabled.
```

(the number depends on how many modules you have, and the exact wording of the lines may differ).

## 7. Try it

1. Open the panel and log in. **Hello** appears in the sidebar and on the **Modules** page.
2. Open it. You see *Hello, admin!* (with your account name).
3. Change something, build, copy the jar, restart. This is the loop while developing.

Check the route without the browser:

```bash
curl -c cookies.txt -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"…"}' http://localhost:8765/api/auth/login
curl -b cookies.txt http://localhost:8765/api/modules/hello/greeting
# {"message":"Hello, admin!"}
```

## If something goes wrong

| Symptom | Fix |
| --- | --- |
| The module is not in the panel | Look for `Failed to load module hello-….jar:` in the log. Usually `module.json` is wrong. |
| `FAILED` on the Modules page | `onEnable` threw an exception. The log has the stack trace. |
| The sidebar shows the module but the page stays empty | The frontend file is missing in the jar (`web/hello.js`) or has an error. Open the browser console. |
| `Cannot find module './minedash'` | Copy `minedash.d.ts` into `frontend/src/`. |

More: [Testing and debugging](testing-and-debugging.md).

## Next

- Add a permission and use it: [Permissions](permissions.md).
- Add a card to the overview page: [Widgets](widgets.md).
- Give your module an icon: [Icons](icons.md).
- Study a complete module with storage and live updates: [Notes module](example-notes-module.md).
