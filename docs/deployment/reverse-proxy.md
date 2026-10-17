# Reverse proxy basics

A reverse proxy is a program that listens on the public ports 80 and 443, handles HTTPS, and passes the requests on to MineDash on the same machine. This page explains what MineDash needs from it. The following pages contain ready configurations:

- [nginx](nginx.md)
- [Apache](apache.md)
- [Caddy](caddy.md) (the easiest, certificates are automatic)
- [Traefik](traefik.md)
- [Cloudflare Proxy and Tunnel](cloudflare.md)

## Configure MineDash first

```json
{
  "web": {
    "host": "127.0.0.1",
    "port": 8765,
    "trustProxy": true
  },
  "auth": {
    "secureCookie": true
  }
}
```

| Setting | Why |
| --- | --- |
| `web.host` = `127.0.0.1` | Only the proxy on the same machine can reach MineDash. This is what makes `trustProxy` safe. |
| `web.trustProxy` = `true` | MineDash takes the client address and the public host name from the proxy headers |
| `auth.secureCookie` = `true` | The session cookie is only sent over HTTPS, and browsers are told to insist on HTTPS |

Restart the server after you change the file.

## What the proxy has to do

| # | Requirement | Why |
| --- | --- | --- |
| 1 | **Forward everything** (`/`) to `http://127.0.0.1:8765` | The panel and the API share one server |
| 2 | **Support WebSockets** for the path `/api/ws` | The console and other live features use them |
| 3 | **Pass the `Host` header** of the browser, including a non-standard port | MineDash compares it with the `Origin` header of requests that change something. Without it you get *403 Cross-origin request blocked*. |
| 4 | **Set `X-Forwarded-For`** to the address of the client that connected to the proxy, as the last entry | Login rate limit and log. See [trustProxy](../administration/configuration.md#trustproxy). |
| 5 | **Set `X-Forwarded-Host`** to the host of the browser | Used when `trustProxy` is on, for the origin check and the content security policy |
| 6 | **Allow large request bodies** | The Files module uploads files in one request. The limit must be at least as high as `maxUploadBytes` of the module (default 256 MiB). nginx allows only 1 MB by default. |
| 7 | **Do not buffer uploads and downloads** (recommended) | Large files and ZIP archives are streamed, buffering wastes disk and delays the progress |
| 8 | **Long timeouts** | A big upload or a ZIP download can take minutes. Idle WebSocket connections must survive at least 60 seconds, the panel sends a ping every 20. |
| 9 | **Redirect HTTP to HTTPS** | So nobody lands on an unencrypted page |
| 10 | **Do not cache `/api/`** and do not add your own `Content-Security-Policy` | MineDash sends the correct headers itself |

All proxy pages in this chapter implement these points.

## Why the Host header matters

For every request that changes something (login, saving, running a command) and for every WebSocket connection, MineDash checks that the browser `Origin` belongs to the host the request was sent to:

```text
Origin: https://panel.example.com        (what the browser says)
Host:   panel.example.com                (what the proxy passes on)
```

If they differ, the request is refused. The check cannot be fooled by another website, because a browser always sends the real `Origin`.

Typical mistakes that break it:

- the proxy replaces `Host` with `127.0.0.1:8765` (nginx without `proxy_set_header Host`, Apache without `ProxyPreserveHost On`)
- the panel runs on a non-standard HTTPS port such as `:8443`, and the proxy passes a `Host` without the port (nginx `$host` drops the port, `$http_host` keeps it)

If you cannot fix the header, add the browser origin to `allowedOrigins`, see [Configuration](../administration/configuration.md#allowedorigins).

## The panel must be at the root of a host name

`https://panel.example.com/` works. `https://example.com/panel/` does not. See [Deployment overview](overview.md#things-that-do-not-work).

## Several panels on one machine

Run each server with its own port (`8765`, `8766`, …) and give each one its own subdomain in the proxy, for example `survival.example.com` → `127.0.0.1:8765` and `creative.example.com` → `127.0.0.1:8766`.

## Test your setup

Run these checks after the proxy works.

1. **HTTP redirects to HTTPS**

   ```bash
   curl -I http://panel.example.com
   ```

   Expect `301` or `308` with a `Location: https://panel.example.com/`.

2. **HTTPS answers with the panel and the security headers**

   ```bash
   curl -I https://panel.example.com
   ```

   Expect `200` and headers such as `content-security-policy`, `x-frame-options: DENY`, `x-content-type-options: nosniff` and, with `secureCookie` on, `strict-transport-security`.

3. **The API refuses anonymous requests**

   ```bash
   curl -i https://panel.example.com/api/auth/me
   ```

   Expect `401`.

4. **Run the smoke test** from the repository:

   ```bash
   tools/smoke-test.sh https://panel.example.com admin '<password>'
   ```

5. **Log in with a browser** and open the Console. The status in the top bar should say *Connected*. If it stays on *Connecting…* or *Disconnected*, the WebSocket does not pass the proxy.

6. **Upload a file** larger than 1 MB with the Files module, if you use it. A failure with *The upload failed or was rejected by the server* usually means a request size limit in the proxy.

Problems and their causes: [Troubleshooting](../reference/troubleshooting.md).
