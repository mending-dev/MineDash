# Deployment overview

MineDash speaks plain HTTP on one port. How you make it reachable is up to you. This chapter shows every common setup, from a quick test to a public panel with a domain and a certificate.

## Choose a setup

| Situation | Setup | Effort | Pages |
| --- | --- | --- | --- |
| Test on the machine you are sitting at | `http://localhost:8765`, nothing else | none | [Installation](../getting-started/installation.md) |
| Home network or VPN only, no internet access to the panel | Plain HTTP on the private address | low | [Direct access](direct-access.md) |
| **Public panel with a domain (recommended)** | Reverse proxy with HTTPS in front of MineDash | medium | [Reverse proxy basics](reverse-proxy.md) and one of the proxy pages |
| You use Cloudflare for your domain | Cloudflare proxy or Cloudflare Tunnel | low to medium | [Cloudflare](cloudflare.md) |
| Docker, Pterodactyl or a game host | Published port plus a proxy outside | medium | [Hosting panels and Docker](hosting-panels.md) |

> **Rule of thumb:** if the panel can be reached from the internet, put HTTPS in front of it. Without HTTPS, passwords and session cookies travel in clear text.

## The recommended architecture

```text
Browser ──HTTPS (443)──► Reverse proxy ──HTTP (127.0.0.1:8765)──► MineDash
                         nginx / Caddy /                          (inside the Minecraft
                         Apache / Traefik                          server process)
                         • certificate (HTTPS)
                         • domain name
                         • HTTP → HTTPS redirect
```

What this gives you:

- HTTPS with a free certificate that renews itself
- a normal domain name instead of `ip:port`
- MineDash is not reachable from outside at all, only through the proxy (`web.host` is `127.0.0.1`)
- the possibility to add IP restrictions, extra authentication or rate limits in the proxy

## Steps, in order

1. [Domain and DNS](domain-and-dns.md): pick a (sub)domain and point it to your server.
2. Set up the reverse proxy. Follow [Reverse proxy basics](reverse-proxy.md) for what it has to do, then use the page for your software: [nginx](nginx.md), [Apache](apache.md), [Caddy](caddy.md) or [Traefik](traefik.md).
3. Get the certificate. Caddy and Traefik do it automatically. For nginx and Apache see [TLS certificates](ssl-certificates.md).
4. Change `config.json`: `web.host` to `127.0.0.1`, `web.trustProxy` to `true`, `auth.secureCookie` to `true`. Restart.
5. Test with the [checklist at the end of the reverse proxy page](reverse-proxy.md#test-your-setup).
6. Work through the [hardening checklist](../security/hardening-checklist.md).

## Things that do not work

- **A sub-path.** The panel has to be served at the root of a host name, for example `https://panel.example.com/`. Setups like `https://example.com/minedash/` are not supported, because the panel loads its files and calls its API with absolute paths starting with `/`. Use a subdomain.
- **HTTPS directly from MineDash.** MineDash has no TLS settings and no keystore. HTTPS is always done by something in front of it.
- **Two different public host names for one panel with different protocols** (for example HTTP and HTTPS at the same time) while `secureCookie` is on. The cookie is only sent over HTTPS. Redirect everything to HTTPS.
