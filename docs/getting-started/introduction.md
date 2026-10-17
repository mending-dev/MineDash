# Introduction

## What MineDash is

MineDash is a Spigot/Paper plugin. When your server starts, the plugin starts a small web server inside the same process. You open that web server in a browser, log in, and manage your server there.

```text
Browser ──HTTP──► MineDash plugin (web server on port 8765)
                     │
                     ├── core        login, sessions, overview, module management
                     └── modules     console, files, and everything you add
                          (jars in plugins/MineDash/modules/)
```

There is nothing else to install: no database, no Node.js, no separate service. The panel itself is part of the plugin jar.

## Core ideas

### The core

The **core** is the plugin itself. It provides:

- the web server and the panel (login page, sidebar, overview page, *Manage modules* page)
- accounts and permissions
- the module system

The core contains no features that you could want to turn off. Everything else is a module.

### Modules

A **module** is one `.jar` file in `plugins/MineDash/modules/`. A module can:

- add a **page** to the sidebar (for example *Console* or *Files*)
- add **widgets** to the overview page
- register its own **HTTP routes** and **WebSocket channels**
- define its own **permissions**

You switch modules on and off on the page *Manage modules* without restarting the server. Modules written by other people work the same way as the ones that come with MineDash, see the [developer documentation](../developers/introduction.md).

### Permissions

Every action is protected by a permission such as `console.execute` or `files.write`. Permissions are listed per account in `users.json`. A module that an account has no permission for does not show up for it. See [Users and permissions](../administration/users-and-permissions.md).

## What MineDash does not do

- It does not replace your hosting panel: it does not install servers, start or stop the Minecraft process, or manage several servers. MineDash runs inside one server and manages that server.
- It does not terminate TLS (HTTPS) itself. For HTTPS you put a reverse proxy in front of it, which takes a few minutes, see [Deployment](../deployment/overview.md).
- It does not run on Folia. Spigot and Paper are supported.

## A first look at the panel

| Part | What it is |
| --- | --- |
| **Sidebar** | *Overview*, one entry per enabled module with a user interface, and *Manage modules* for accounts with the permission `modules.manage` |
| **Overview** | Live status of the server. Widgets can be shown or hidden per account. |
| **Module pages** | The page of the module you selected |
| **Account area** | Your avatar and name at the bottom of the sidebar, and the sign out button |
| **Theme switch** | Light and dark theme in the top bar |

## Next steps

1. Check the [requirements](requirements.md)
2. [Install](installation.md) the plugin and the modules
3. [Log in](first-login.md) for the first time
