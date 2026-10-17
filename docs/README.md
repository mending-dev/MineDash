# MineDash

MineDash is a web panel for Minecraft servers. It runs inside your server as one plugin, needs no database and no extra software, and everything you see in it is a **module** that you can switch on and off.

## What you get

- **Overview page** with live graphs for CPU, memory and TPS, player count, uptime, storage and information about the server and the machine
- **Console module** to watch the server log live and run commands
- **Files module** to browse, edit, upload, download, zip and unzip the files of the server
- **Accounts and permissions** in a simple `users.json`
- **Modules as plain `.jar` files** in a folder. Third parties can write their own modules with a small Java API and any web frontend
- A clean, responsive interface with light and dark theme

## Who this documentation is for

| You are | Start here |
| --- | --- |
| A **server admin** who wants to run MineDash | [Introduction](getting-started/introduction.md), then [Installation](getting-started/installation.md) |
| An admin who wants a **domain and HTTPS** | [Deployment overview](deployment/overview.md) |
| An admin who cares about **security** | [Security model](security/security-model.md) and the [Hardening checklist](security/hardening-checklist.md) |
| A **developer** who wants to build a module | [Developer introduction](developers/introduction.md), then the [Quickstart](developers/quickstart.md) |

## How the documentation is organized

1. **Getting started** – requirements, installation, first login, updating
2. **Administration** – folder layout, `config.json`, accounts and permissions, managing modules
3. **Deployment** – domain, DNS, reverse proxies (nginx, Apache, Caddy, Traefik, Cloudflare), hosting panels and Docker, TLS certificates
4. **Security** – how MineDash protects the panel and what you should do on top
5. **Modules** – the modules that come with MineDash
6. **Reference** – every HTTP endpoint, every permission, troubleshooting
7. **Developers** – the module API, the frontend contract, a step-by-step tutorial and a complete example

## Conventions

- Paths are relative to the folder the server was started in, so `plugins/MineDash/config.json` is the file in the `plugins` folder of your server.
- Text in `code style` is something you type or find in a file.
- Boxes that start with **Warning** describe things that can lock you out or expose your server.
