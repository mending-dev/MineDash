# Troubleshooting

Find your symptom, then follow the steps. Most problems show up in the server log, so look at `logs/latest.log` first and search for `MineDash`.

## The plugin does not start

| Symptom | Cause and fix |
| --- | --- |
| `MineDash` is red in `/plugins`, or missing | Look at the first error in `latest.log`. Typical: Java older than 8 (see [Requirements](../getting-started/requirements.md)), a server that is not Spigot/Paper compatible, or a damaged jar. |
| Log says the server is Folia and the plugin disables itself | Folia is not supported. |
| `Address already in use` | Another program (or another server) uses `web.port`. Change `web.port`, restart. |
| `Cannot assign requested address` | `web.host` is an address that this machine does not have. Use `0.0.0.0` or `127.0.0.1`. |
| Invalid `config.json` | The log names the setting. Fix the value or delete the file to get a fresh one (you lose your changes). |
| `avatarUrl` rejected | It must contain `{name}`, be shorter than 300 characters and start with `http://` or `https://`. Or set it to `""` to switch it off. |

## The page does not load

| Symptom | Cause and fix |
| --- | --- |
| Timeout | The port is not reachable. Check `web.host`, the firewall of the machine and of your provider, the port forwarding of your router, and that the server is running. Test on the server itself: `curl -I http://127.0.0.1:8765`. |
| Connection refused | Nothing listens on that port. Look in the log for the line that says where MineDash listens. |
| `502 Bad Gateway` from the proxy | The proxy cannot reach MineDash. Check that the server is running, that `proxy_pass`/`reverse_proxy` points to the right port and that `web.host` allows the proxy (`127.0.0.1` for a proxy on the same machine, `0.0.0.0` for Docker). |
| `504 Gateway Timeout` | The server is stuck or too slow. For uploads/downloads raise the proxy timeouts, see [nginx](../deployment/nginx.md). |
| Browser warning about the certificate | The certificate is missing the domain, is expired, or is self-signed. See [TLS certificates](../deployment/ssl-certificates.md#verify-your-certificate). |
| Endless redirects | Cloudflare mode *Flexible* (use *Full (strict)*), or the proxy and the panel both redirect. |
| White page, console shows blocked scripts | Another proxy or plugin adds its own `Content-Security-Policy`. Remove it for the panel. |
| The page works at `ip:8765` but not at the domain | See the proxy sections below. |

## Login problems

| Symptom | Cause and fix |
| --- | --- |
| *Invalid username or password* | Check upper/lower case of the password (the username is not case sensitive). If `users.json` was edited, restart the server, because it is read only at start. |
| *Too many login attempts* | Wait `lockoutMinutes` (default 5), or restart the server to reset the counter. |
| Everybody shares one lock-out | Behind a proxy `trustProxy` is off, so all users appear to come from the proxy's address. Turn it on, see [trustProxy](../administration/configuration.md#trustproxy). |
| Login works, but you are sent back to the login page | The cookie is not stored. Typical: `secureCookie` is `true`, but you are using plain `http://`. Use HTTPS or turn `secureCookie` off for the test. |
| Logged out after some time | Idle timeout (`sessionTimeoutMinutes`) or maximum lifetime (`maxSessionHours`). Or the server restarted. |
| Lost the `admin` password | Edit `users.json`, set `"password": "new-password"` for the account (or remove the file to create a new `admin`), then restart. See [First login](../getting-started/first-login.md). |
| Where is the generated password? | It is printed once in the server log at the first start. Search for `admin`. |

## `403 Cross-origin request blocked`

The `Origin` header of the browser does not match the `Host` the server sees.

1. Make sure the proxy passes the original host: nginx `proxy_set_header Host $http_host;`, Apache `ProxyPreserveHost On`.
2. Make sure `web.trustProxy` is `true` and the proxy sends `X-Forwarded-Host`.
3. If you use a custom port, the proxy must keep the port in `Host` (`$http_host`, not `$host`).
4. As a last resort, add the exact browser origin to `web.allowedOrigins`, for example `"https://panel.example.com"`. See [allowedOrigins](../administration/configuration.md#allowedorigins).

## The console or live data does not update (WebSocket)

Symptoms: the status stays on *Connecting…* or *Disconnected*, or the console is empty.

1. Open the developer tools of the browser, tab *Network*, filter *WS*. Look at the request to `/api/ws`.
2. Close code or status:
   - `101` then closed with `4401`: you are not logged in or the session ended. Log in again.
   - `4403`: wrong origin (see above) or missing permission.
   - `4404`: the module is disabled or has no such channel.
   - `4429`: too many open panel tabs (20 connections per account). Close some.
   - No `101`, status `400`/`502`/`426`: the proxy does not pass WebSockets. Check `Upgrade` and `Connection` headers (nginx), `proxy_wstunnel` (Apache).
3. Idle connections that break after exactly 60 seconds: raise the proxy's read timeout (`proxy_read_timeout`, Apache `ProxyTimeout`).

## Uploads fail

| Symptom | Cause and fix |
| --- | --- |
| `413 Request Entity Too Large` | nginx `client_max_body_size`. Raise it to at least the module limit (256 MiB by default). |
| Fails after about 100 MB behind Cloudflare | The Cloudflare plan limit for request bodies. See [Cloudflare](../deployment/cloudflare.md#upload-size-limit). |
| Fails after 60 seconds | Raise proxy timeouts. |
| Rejected by the module | The file is larger than `maxUploadBytes` in the Files configuration, or the path is protected. The panel shows the reason. |

## Modules

| Symptom | Cause and fix |
| --- | --- |
| A new module jar does not appear | Modules are scanned at server start. Restart the server. |
| The jar is in the folder after a restart, but the module is not listed | The jar could not be loaded at all. The log says `Failed to load module <file>: <reason>`. Typical: `module.json` missing or invalid, a wrong `id`, a different `apiVersion`, or an invalid `widgets` entry. |
| State `FAILED` | The module was found, but it did not start. The Modules page shows the error. Typical: an exception in `onEnable`, the `main` class is missing, or a dependency is missing or disabled. The log has the details. |
| Two jars with the same id | The first one (by file name) wins, the other is ignored with a warning. |
| Module page is blank | Open the console of the browser. A JavaScript error in the module frontend, or a build that is not a single ES module file. |
| Module icon missing | See the warning in the log. The SVG may use a tag that is not allowed or be too large. See [Icons](../developers/icons.md). |
| `Missing permission` | The account lacks the permission. See the module's documentation. |

## The Overview page

| Symptom | Cause and fix |
| --- | --- |
| *No access* | The account has no `overview.view`. |
| CPU shows that it is not available | The Java runtime cannot report it. |
| Cards you deleted are gone | The choice is stored in the browser. Use **Show all** or clear the site data. |

## Head images (avatars)

If the head of a player does not appear, the letter box is shown instead. This happens when the name is not a Minecraft name (3–16 letters, digits, underscore), when the avatar service has no answer, or when `avatarUrl` is empty. Check the origin in `web.avatarUrl` and that your own network can reach it.

## Collecting information for a bug report

1. The MineDash version (log line at start) and the server software and version.
2. The part of `latest.log` around the problem, with passwords removed.
3. The output of `curl -i` for the failing request, and in the browser the *Network* entry.
4. Your proxy configuration with secrets removed.
