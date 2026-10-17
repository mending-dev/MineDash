# Security model

This page explains how MineDash protects the panel, and what it expects from you.

## What is protected, and from whom

| Threat | Protection |
| --- | --- |
| Someone guesses a password | Passwords are stored as bcrypt hashes. Failed logins are limited per address and then locked for a while. |
| Someone listens on the network | Use HTTPS. MineDash itself speaks HTTP and relies on a [reverse proxy](../deployment/reverse-proxy.md) for it. |
| Another website makes your browser send requests to the panel (CSRF) | Session cookie with `SameSite=Strict`, plus an `Origin` check on every request that changes something and on WebSockets |
| A malicious script on a page | Strict Content Security Policy, HttpOnly cookie |
| The panel is shown inside another site (clickjacking) | `X-Frame-Options: DENY` and `frame-ancestors 'none'` |
| A stolen session | Idle timeout and an absolute maximum lifetime, new token at every login, logout destroys the session |
| An account does more than it should | Permissions per account, checked on the server for every request |
| A module does something unexpected | A module runs with the same rights as any plugin. **Only install modules you trust.** |

## Accounts and passwords

- Accounts are defined in `users.json`. See [Users and permissions](../administration/users-and-permissions.md).
- Passwords are hashed with bcrypt (cost 10). A plain `password` that you type into the file is hashed at the next start and removed from the file.
- Account names are not case sensitive.
- Unknown account names cost the same time as known ones, so response times do not reveal which names exist.

## Login limits

- After `maxLoginAttempts` failed logins (default 5) from one address, that address is locked for `lockoutMinutes` (default 5). The answer is `429` with a `Retry-After` header.
- The address is the connection address, or with `trustProxy` the last `X-Forwarded-For` entry.

## Sessions

- The session cookie is called `minedash_session`. It is `HttpOnly` (scripts cannot read it) and `SameSite=Strict` (sent only for requests that start on the panel's own site). With `secureCookie` it is also `Secure`.
- It holds a random 256-bit token. All session data stays in the memory of the server, so a **server restart logs everybody out**.
- A session ends after `sessionTimeoutMinutes` without activity or after `maxSessionHours` in total, whichever comes first.
- Open WebSocket connections are closed within seconds after their session ends (code 4401).

## Same-origin checks

Every request that changes something (`POST`, `PUT`, `DELETE`) to `/api/*` and every WebSocket connection must carry an `Origin` that matches the host of the request, or one that is listed in `allowedOrigins`. Otherwise the answer is `403` (WebSocket close code 4403). Requests without any `Origin` header, for example from `curl`, are accepted, because a browser always sends one for these requests and an attacker's page cannot remove it. The session cookie is still required.

## Response headers

| Header | Value |
| --- | --- |
| `Content-Security-Policy` | `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: [avatar origin]; font-src 'self'; connect-src 'self' ws(s)://host; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'` |
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `same-origin` |
| `Cross-Origin-Opener-Policy`, `Cross-Origin-Resource-Policy` | `same-origin` |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=(), payment=(), usb=()` |
| `Strict-Transport-Security` | `max-age=15552000` (about 180 days), only when `secureCookie` is on |
| `Cache-Control` | `no-store` for API answers |

Consequences for modules: a module frontend is a script served by the panel itself (`script-src 'self'`). It can not load scripts or styles from other sites, and it can only reach the panel's own API (`connect-src 'self'`). Remote images are blocked, except for the origin of the avatar service. See [Developer security notes](../developers/permissions.md).

## Permissions

Every route of a module has an optional permission. The server checks it before the module code runs, so a hidden button is not the protection, the check on the server is. See [Permissions](../reference/permissions.md).

## What MineDash does not do

- **No HTTPS itself.** Use a reverse proxy.
- **No two-factor login.** Put a second layer in front (VPN, Cloudflare Access, IP restriction, basic auth in the proxy) if you need it.
- **No account management in the panel.** Accounts are files. Changes need a server restart.
- **No persistent sessions.** A restart logs everybody out.
- **No isolation between modules and the server.** A module is code in the server process.

## Reporting a problem

If you find a security problem in MineDash, do not post it in a public issue. Contact the author privately, for example by email, with the steps to reproduce.
