# Requirements

## Server software

| Item | Requirement |
| --- | --- |
| Server software | **Spigot** or **Paper** (and forks that keep the Bukkit plugin API, such as Purpur) |
| Not supported | **Folia** (MineDash refuses to start and writes a message to the log) |
| Minecraft versions | 1.8 and newer. The plugin is compiled against the Spigot 1.8.8 API, so it uses no newer API. |
| Java | 8 or newer. The plugin is compiled to Java 8 bytecode and is meant to run on every Java version from 8 up to 25. |

> **Note for maintainers:** the version ranges above are the targets of the project. Record the combinations you actually tested in your release notes.

## Network

| Item | Requirement |
| --- | --- |
| Port | One TCP port for the panel. The default is **8765**. It must be free and reachable by the browsers that use the panel. |
| Browser | A current version of Chrome, Edge, Firefox or Safari. The panel uses ES modules, WebSockets and CSS variables. |
| Outgoing access | Optional. The browser loads player heads from the avatar service (by default `api.mcheads.org`). The server itself needs no outgoing connections. |

## Resources

MineDash is light. The web server and the metrics collector use a small amount of memory and almost no CPU while nobody is looking at the panel. The overview page polls the server about once per second while it is open.

## What you need for a public setup

If the panel should be reachable from the internet, you also need:

- a **domain name** (or a subdomain such as `panel.example.com`)
- a **reverse proxy** such as nginx or Caddy, which also takes care of HTTPS
- ports **80 and 443** open on the machine that runs the proxy

All of this is explained in the [Deployment](../deployment/overview.md) chapter. For a quick test on your own computer or in a private network you need none of it.
