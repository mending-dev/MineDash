# Hardening checklist

Go through this list before you give anybody else access, or before the panel is reachable from the internet. Each item links to the page that explains it.

## Must do

- [ ] **Use HTTPS** for every connection that leaves your own network. [Reverse proxy](../deployment/reverse-proxy.md), [certificates](../deployment/ssl-certificates.md).
- [ ] **Let MineDash listen on `127.0.0.1`** when a proxy on the same machine is used. `web.host` = `127.0.0.1`. [Configuration](../administration/configuration.md#host).
- [ ] **Do not open the panel port** (8765) in the firewall or in Docker when a proxy is used. [Docker](../deployment/hosting-panels.md).
- [ ] **Set `auth.secureCookie` to `true`** as soon as HTTPS works.
- [ ] **Set `web.trustProxy` to `true` only behind a proxy you control**, and never when the port is reachable directly. [trustProxy](../administration/configuration.md#trustproxy).
- [ ] **Change the generated `admin` password** or replace the account. [First login](../getting-started/first-login.md).
- [ ] **Use a long, unique password** for every account (a password manager and 16 or more characters).
- [ ] **Give people only the permissions they need.** [Users and permissions](../administration/users-and-permissions.md).

## Should do

- [ ] **No shared accounts.** One account per person, so the audit lines in the log name a person.
- [ ] **Treat `files.write` and `console.execute` like shell access.** Both can take over the server.
- [ ] **Install only modules you trust** and only from sources you know. A module has the same rights as any plugin.
- [ ] **Restrict the panel by IP** or put it behind a VPN if only a few people use it. [nginx](../deployment/nginx.md#optional-restrict-access-by-ip-address), [Caddy](../deployment/caddy.md#restrict-access-by-ip-address), [Apache](../deployment/apache.md#optional-restrict-access-by-ip-address).
- [ ] **Keep the system up to date:** the Minecraft server, MineDash, the proxy and the operating system.
- [ ] **Keep the file permissions of `plugins/MineDash/` tight.** `users.json` holds the password hashes. On Linux, only the user that runs the server should be able to read it (`chmod 600 users.json`).
- [ ] **Back up** `plugins/MineDash/` (`config.json`, `users.json`, `modules.json`, `data/`).
- [ ] **Run the server as a normal user,** never as `root` or Administrator.

## Optional extras

- [ ] A second login layer in front of the panel: Cloudflare Access, basic auth in the proxy, or a VPN. [Cloudflare](../deployment/cloudflare.md#cloudflare-access-optional).
- [ ] A rate limit for the login endpoint in nginx. [nginx](../deployment/nginx.md#optional-rate-limit).
- [ ] Fail2ban or similar on the proxy log for repeated `401` or `429` answers.
- [ ] Restrict the avatar service: set `web.avatarUrl` to an empty string to switch off head images completely (then the panel never contacts another site). [avatarUrl](../administration/configuration.md#avatarurl).

## Things to check from outside

```bash
# The panel port must NOT answer from the internet
curl -m 5 http://YOUR_PUBLIC_IP:8765/        # should time out or be refused

# HTTP must redirect
curl -I http://panel.example.com             # 301/308 to https

# Headers
curl -I https://panel.example.com            # check strict-transport-security etc.
```

Run these from a machine that is not in your own network. Your own machine may reach the port through the local network even if the internet cannot.

## After a suspected leak

1. Stop the server (this ends every session at once).
2. Change the passwords in `users.json` (type a new `password`, it is hashed at the next start) and remove accounts that are not needed.
3. Check `latest.log` for lines like `<name> executed: …` from the console and for file operations from the Files module.
4. Check `plugins/` and `plugins/MineDash/modules/` for jars you did not install.
5. Start the server again.
