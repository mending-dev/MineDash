# HTTP API

The panel talks to the server through a small HTTP API. You can use it from scripts too (for example monitoring). Everything is under `/api/`.

> The API is meant for the panel and its modules. Stable for module developers is the [module API](../developers/api-reference.md). The core endpoints below can change between major versions.

## Conventions

- Bodies and answers are JSON (`Content-Type: application/json`), unless noted.
- Errors use the HTTP status and a short text as the body.
- Everything except `POST /api/auth/login` needs a valid session cookie (`minedash_session`).
- Requests that change something (`POST`, `PUT`, `DELETE`) need a matching `Origin` header or none at all, see [Security model](../security/security-model.md#same-origin-checks).
- JSON request bodies are limited to 1 MB.
- Answers under `/api/` are sent with `Cache-Control: no-store`.

| Status | Meaning |
| --- | --- |
| `200` | OK, JSON in the body |
| `204` | OK, no body |
| `400` | Invalid request |
| `401` | Not logged in, or wrong username/password at login |
| `403` | Missing permission, or foreign origin |
| `404` | Unknown route, module not available |
| `409` | Conflict, for example a module cannot be enabled because of its dependencies |
| `413` | Body too large |
| `429` | Too many login attempts, see `Retry-After` |
| `500` | Server error, see the server log |

## Authentication

### `POST /api/auth/login`

```json
{ "username": "admin", "password": "secret" }
```

Answer `200` with the account and sets the session cookie:

```json
{
  "username": "admin",
  "permissions": ["*"],
  "avatarUrl": "https://api.mcheads.org/head/{name}/{size}"
}
```

`401` for wrong credentials, `429` when the address is locked (with a `Retry-After` header in seconds).

### `POST /api/auth/logout`

Ends the session and removes the cookie. Answer `204`.

### `GET /api/auth/me`

Same JSON as the login answer, for the current session. `401` if not logged in.

## Modules

### `GET /api/core/modules`

A list of all modules found, with their state:

```json
[
  {
    "id": "console",
    "name": "Console",
    "version": "1.0.0",
    "description": "Live server console with command execution",
    "authors": ["Mending"],
    "dependencies": [],
    "widgets": [],
    "state": "enabled",
    "error": null,
    "icon": "<svg …>",
    "frontend": "/api/assets/console/console.js"
  }
]
```

`state` is `enabled`, `disabled` or `failed`. When it is `failed`, `error` holds the reason. `icon` is the sanitized SVG markup or `null`. `frontend` is the URL of the frontend file, only present while the module is enabled and has one. Each entry of `widgets` has `id`, `title`, `size` and `permission` (or `null`).

### `POST /api/core/modules/{id}/enable` and `/disable`

Needs `modules.manage`. Answer `200` with the updated module object. `404` for an unknown module, `409` if the module cannot change state (for example missing dependencies).

## Overview

Both need `overview.view`.

### `GET /api/core/overview/info`

Static information about the server and the system: software, versions, Java, operating system, and so on.

### `GET /api/core/overview/metrics?since={seq}`

Samples for the graphs. With `since`, only samples newer than that sequence number are returned.

```json
{
  "interval": 2000,
  "capacity": 300,
  "reset": false,
  "uptimeMs": 123456,
  "current": { "heapCommitted": 0, "heapMax": 0, "maxPlayers": 20, "plugins": 12, "diskTotal": 0, "diskFree": 0 },
  "samples": [
    { "seq": 41, "t": 1700000000000, "cpu": 0.12, "systemCpu": 0.30, "heapUsed": 0, "tps": 20.0, "players": 3 }
  ]
}
```

The numbers above are placeholders to show the shape. A value that is not available (for example the CPU load on a system that cannot report it, or `tps` before the first measurement) is negative.

## Module routes

### `/api/modules/{id}/{path}`

Every route a module registers lives here, with the HTTP methods `GET`, `POST`, `PUT` and `DELETE`. Paths match exactly, there are no path parameters. The permission the module chose is checked first. A module that is not enabled answers `404`.

Example: the console module registers `POST /command`, so it is `POST /api/modules/console/command`.

### `/api/assets/{id}/{file}`

The files of a module's frontend (`web/` in the jar). Only for modules that are enabled.

## WebSocket

### `/api/ws?module={id}&channel={name}`

Opens a channel of a module. The session cookie and a matching `Origin` are required. Close codes:

| Code | Meaning |
| --- | --- |
| `4401` | Not logged in, or the session ended |
| `4403` | Foreign origin, or the permission of the channel is missing |
| `4404` | Unknown module or channel, or the module is disabled |
| `4429` | Too many connections for this account (20) |

## Using the API from a script

```bash
# 1. Log in and keep the cookie
curl -sS -c cookies.txt \
  -H 'Content-Type: application/json' \
  -d '{"username":"monitor","password":"secret"}' \
  https://panel.example.com/api/auth/login

# 2. Call something
curl -sS -b cookies.txt https://panel.example.com/api/core/overview/metrics

# 3. Log out
curl -sS -b cookies.txt -X POST https://panel.example.com/api/auth/logout
```

Create a separate account with only the permissions the script needs. For the secure cookie to be accepted by `curl` over HTTPS nothing special is needed. Over plain HTTP with `secureCookie` on, the cookie is not sent back.

`tools/smoke-test.sh` in the repository runs a set of such calls against a running panel.
