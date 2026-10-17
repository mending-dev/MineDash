# Configuration (config.json)

File: `plugins/MineDash/config.json`

- It is created with the default values on the first start.
- At every start MineDash adds options that are missing and **writes the file back**. Because of that, comments in the file are lost. Keep notes elsewhere.
- The file is read **once, when the plugin starts**. After a change, restart the server (or reload the plugin).
- Values that are out of range are corrected silently, except where noted below. A value that makes the web server impossible to start (for example a wrong port) stops the plugin with a message in the log.
- Invalid JSON stops the plugin with a message that names the file.

## Default file

```json
{
  "web": {
    "host": "0.0.0.0",
    "port": 8765,
    "trustProxy": false,
    "allowedOrigins": [],
    "avatarUrl": "https://api.mcheads.org/head/{name}/{size}"
  },
  "auth": {
    "sessionTimeoutMinutes": 60,
    "maxSessionHours": 12,
    "maxLoginAttempts": 5,
    "lockoutMinutes": 5,
    "secureCookie": false
  }
}
```

## web

| Option | Default | Meaning |
| --- | --- | --- |
| `host` | `"0.0.0.0"` | Network address the web server listens on. `0.0.0.0` means every network interface. Use `127.0.0.1` when a reverse proxy on the same machine is the only way in. An empty value becomes `0.0.0.0`. |
| `port` | `8765` | TCP port of the panel. Must be 1 to 65535, otherwise the plugin does not start. If the port is taken, the log says so. |
| `trustProxy` | `false` | Set to `true` **only** if the panel can be reached exclusively through your own reverse proxy. See [below](#trustproxy). |
| `allowedOrigins` | `[]` | Extra browser origins that may call the API, for example `["https://panel.example.com"]`. See [below](#allowedorigins). |
| `avatarUrl` | `"https://api.mcheads.org/head/{name}/{size}"` | Where the player heads in front of the user names come from. See [below](#avatarurl). |

### host

| Value | Who can connect |
| --- | --- |
| `0.0.0.0` | Everybody who can reach the machine on that port. Fine for a test in a private network, risky on the internet without HTTPS. |
| `127.0.0.1` (or `localhost`, `::1`) | Only programs on the same machine, for example your reverse proxy. **Recommended behind a proxy.** |
| A specific IP of the machine | Only that network interface |

If `host` is not a loopback address and `secureCookie` is `false`, the log warns on every start that the panel is served over plain HTTP.

### trustProxy

Behind a reverse proxy, the connection to MineDash always comes from the proxy. With `"trustProxy": true`, MineDash reads

- the client address from the **last** entry of the `X-Forwarded-For` header (used for the login rate limit and the log), and
- the public host name from `X-Forwarded-Host` (used for the origin check and for the content security policy of WebSocket connections).

> **Warning:** Only enable `trustProxy` when nobody can reach MineDash without going through your proxy: set `web.host` to `127.0.0.1` or block the port in the firewall. Otherwise anyone can send a forged `X-Forwarded-For` and get around the login rate limit.

Which entry is read matters. A proxy appends the address of the connection it received to `X-Forwarded-For`, so the last entry is the one your proxy saw and earlier entries can be forged by the client. Use proxy settings that make this entry the real client, as shown in the [nginx](../deployment/nginx.md), [Apache](../deployment/apache.md), [Caddy](../deployment/caddy.md) and [Traefik](../deployment/traefik.md) pages. If another proxy (for example Cloudflare) sits in front of yours, see [Cloudflare](../deployment/cloudflare.md).

### allowedOrigins

Every request that changes something (login, saving a file, running a command) and every WebSocket connection must come from the panel itself. MineDash compares the `Origin` header of the browser with the host the request was sent to. If they differ, the request is answered with **403 Cross-origin request blocked**.

That is correct in nearly all setups. You need `allowedOrigins` when the host the browser sees is **not** the host MineDash receives, and the proxy does not tell it. Typical case: a proxy on port 8443 that drops the port from the `Host` header.

```json
"allowedOrigins": ["https://panel.example.com", "https://panel.example.com:8443"]
```

- Each entry is `scheme://host` with an optional `:port`. No path.
- Entries are trimmed, written in lower case, trailing `/` are removed and duplicates are dropped.
- Do **not** add `*`. Wildcards are not supported on purpose.

### avatarUrl

The small picture in front of the user name in the sidebar. By default it is the player head from the service `mcheads.org`.

| Part | Meaning |
| --- | --- |
| `{name}` | Required. Replaced by the user name. |
| `{size}` | Optional. Replaced by the edge length in pixels (the panel asks for 64). |

How it works:

- A head is requested only when the user name is a valid Minecraft name: 3 to 16 characters, letters, digits and `_`. For other names, and when the service has no image or cannot be reached, the panel shows the first letter of the name in a colored box.
- The browser loads the image, not the server. The service therefore sees the user name and the IP address of the person who uses the panel. If that is not acceptable, switch heads off.
- The host of the URL is added to the `img-src` part of the content security policy. No other foreign host can deliver images.
- The URL must start with `http://` or `https://`, contain `{name}`, and have a plain host name (letters, digits, `.` and `-`, an optional port). Anything else stops the plugin with an explanation.

Examples:

```json
"avatarUrl": "https://api.mcheads.org/head/{name}/{size}"
"avatarUrl": "https://mc-heads.net/avatar/{name}/{size}"
"avatarUrl": "https://crafatar.com/avatars/{name}?size={size}&overlay"
"avatarUrl": ""
```

The last line (an empty string) switches heads off. Only the letter is shown, and no foreign host is contacted. The services in the examples are third-party services, check their terms of use.

If the panel is served over HTTPS, use an `https://` URL, because browsers block images from `http://` addresses on HTTPS pages.

## auth

| Option | Default | Allowed | Meaning |
| --- | --- | --- | --- |
| `sessionTimeoutMinutes` | `60` | 1 or more | A session ends after this many minutes without activity |
| `maxSessionHours` | `12` | 1 to 8760 | A session ends after this many hours no matter what |
| `maxLoginAttempts` | `5` | 1 or more | Wrong passwords from one address before it is locked out |
| `lockoutMinutes` | `5` | 1 or more | How long the lock-out lasts |
| `secureCookie` | `false` | `true` or `false` | Set to `true` when users reach the panel over HTTPS |

### secureCookie

With `true`:

- The session cookie gets the `Secure` attribute, so the browser sends it only over HTTPS.
- MineDash adds the `Strict-Transport-Security` header (`max-age=15552000`, about 180 days), so browsers insist on HTTPS for the host afterwards.
- The startup warning about plain HTTP is not shown.

Set it to `true` as soon as the panel is behind HTTPS, and **only then**: if you reach the panel over plain HTTP with this option on, the browser drops the cookie and you cannot log in. For the same reason the HSTS header can cause trouble if you later move the panel back to HTTP on the same host name; it is remembered by the browser for the time above.

### Lock-out details

- The counter is kept per client address (see `trustProxy` for which address that is).
- A successful login resets the counter.
- The counter only grows with wrong passwords. An unknown user name counts like a wrong password and takes the same time to answer, so the response does not reveal which accounts exist.
- During a lock-out, the answer is HTTP 429 with a `Retry-After` header.
- A server restart clears all lock-outs.

## Typical configurations

### Local test

```json
{ "web": { "host": "127.0.0.1", "port": 8765 } }
```

Only reachable from the same machine. No other option is needed.

### Behind a reverse proxy with HTTPS (recommended for public servers)

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

### Directly on a private network, plain HTTP

```json
{
  "web": { "host": "0.0.0.0", "port": 8765 },
  "auth": { "secureCookie": false }
}
```

Only do this in a network you trust (home network, VPN).

### Behind Docker or a hosting panel

Inside a container the server has to listen on `0.0.0.0`, and the container port is published only on the loopback address of the host. See [Hosting panels and Docker](../deployment/hosting-panels.md).

## Settings of modules

Modules have their own settings, usually in `plugins/MineDash/data/<module-id>/config.json`. They are described on the page of the module, for example [Files](../modules/files.md#configuration).
