# Module development: introduction

MineDash is built from a small core and **modules**. The Console and the Files page are modules too. You can write your own to add pages, cards and endpoints to the panel: player lists, backups, a warp manager, a link to your own plugin, and so on.

## What a module is

A module is a normal `.jar` file that you put in `plugins/MineDash/modules/`. It has two halves:

```text
my-module.jar
├── module.json                      what the module is called, what it needs
├── com/example/…/MyModule.class     Java backend, runs in the server
└── web/my-module.js                 browser frontend, one JavaScript file
```

| Half | Language | Runs | Does |
| --- | --- | --- | --- |
| **Backend** | Java 8 or newer | In the Minecraft server | Registers HTTP routes and WebSocket channels, reads and writes data, calls Bukkit |
| **Frontend** | JavaScript or TypeScript (any framework) | In the browser of the admin | Draws the module page and cards, calls the backend |

The frontend is optional. A module without one only adds routes (for other modules or scripts).

## How the pieces fit

```text
 Browser                                   Minecraft server
┌─────────────────────────┐              ┌──────────────────────────────────┐
│ MineDash panel          │   HTTP/WS    │ MineDash core                    │
│  └─ your frontend       │ ───────────► │  ├─ login, sessions, permissions │
│     mount(target, ctx)  │ /api/modules │  └─ your module (own class loader)│
└─────────────────────────┘     /<id>/…  │       context.get("/x", perm, …) │
                                         └──────────────────────────────────┘
```

- The core does the **login, the session, the permission check and the security headers**. Your code only runs when the request is allowed.
- Your routes are available at `/api/modules/<id>/<path>`.
- Your frontend file is served at `/api/assets/<id>/<file>`. The panel loads it as an ES module and calls `mount()`.

## What you get

- A **Java API** (`dev.mending.minedash.api`): small and without dependencies besides Bukkit. See [Backend API](backend-api.md).
- A **frontend contract**: you receive a context with `http`, `connect`, `hasPermission` and `user`. See [Frontend](frontend.md).
- Your **own configuration file**, **data folder** and **logger**.
- **Live updates** over WebSocket channels.
- **Cards for the overview page**. See [Widgets](widgets.md).
- An **icon** in the sidebar. See [Icons](icons.md).
- **Permissions** that admins can give per account. See [Permissions](permissions.md).

## What a module cannot do

- Change the layout of the panel outside its own page and cards.
- Load scripts or styles from other websites (the content security policy forbids it). Bundle everything into your file.
- Use a path parameter in a route (`/users/{id}`). Paths match exactly, use query parameters.
- Handle requests on the server main thread. Handlers run on web server threads, see [Routes and WebSockets](backend-api.md#threads).

## Trust

A module runs in the server process with the same rights as any plugin. There is **no sandbox**. Only the permission checks on routes protect its endpoints from users. Admins should only install modules they trust, and you should treat input from the browser as untrusted. See [Security for module authors](permissions.md#security-checklist).

## Where to go next

1. [Quickstart](quickstart.md): a working module in about 15 minutes.
2. [Project setup](project-setup.md): Gradle and Vite projects for real modules.
3. [Notes module](example-notes-module.md): a complete example with config, storage, permissions, WebSocket and a card.
4. [API reference](api-reference.md): every class and method on one page.

## Version of the API

The module API has a version number, currently `1`. A module declares the version it was written for in `module.json` (`apiVersion`). If it does not match the version of the installed MineDash, the module is skipped with a message in the log. The number goes up only for breaking changes.
