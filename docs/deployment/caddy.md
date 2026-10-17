# Caddy

Caddy is the easiest way to get HTTPS: it requests and renews the certificate on its own, redirects HTTP to HTTPS, supports WebSockets, and sets the forwarded headers. The whole configuration for MineDash is three lines.

## Prerequisites

- Caddy 2 installed, see the [official installation guide](https://caddyserver.com/docs/install)
- a DNS record for your domain, see [Domain and DNS](domain-and-dns.md)
- ports 80 and 443 open and **not used by another program** (stop nginx or Apache first, or use one of them instead)
- MineDash configured as in [Reverse proxy basics](reverse-proxy.md#configure-minedash-first)

## The Caddyfile

Edit `/etc/caddy/Caddyfile`:

```caddy
panel.example.com {
	reverse_proxy 127.0.0.1:8765
}
```

Reload:

```bash
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

That is all. On the first request Caddy gets a certificate from Let's Encrypt (or ZeroSSL), and it renews it before it expires.

What Caddy does for you:

| Need | Caddy |
| --- | --- |
| HTTPS certificate and renewal | automatic |
| HTTP to HTTPS redirect | automatic |
| WebSockets | automatic |
| `Host` header | passed unchanged, including a port |
| `X-Forwarded-For`, `X-Forwarded-Host`, `X-Forwarded-Proto` | set automatically |
| Request size limit | none by default |
| Response buffering | none by default |

> **About `X-Forwarded-For`:** By default Caddy does not trust forwarded headers sent by clients, so the address it passes on is the one it really saw. MineDash reads the **last** entry, so a forged header does not matter either way. If you put another proxy (such as Cloudflare) in front of Caddy, read [Cloudflare](cloudflare.md#real-visitor-addresses) first.

## Custom options

### Timeouts for very long transfers

Caddy has no read timeout on proxied connections by default, so uploads and idle WebSockets work as they are.

### Restrict access by IP address

```caddy
panel.example.com {
	@blocked not remote_ip 203.0.113.10 192.168.1.0/24
	respond @blocked 403

	reverse_proxy 127.0.0.1:8765
}
```

### Extra password

Create a password hash and add a `basic_auth` block:

```bash
caddy hash-password
```

```caddy
panel.example.com {
	basic_auth {
		youradmin $2a$14$REPLACE_WITH_THE_HASH
	}
	reverse_proxy 127.0.0.1:8765
}
```

### A custom HTTPS port

```caddy
panel.example.com:8443 {
	reverse_proxy 127.0.0.1:8765
}
```

Caddy passes `Host: panel.example.com:8443`, which MineDash accepts.

### Certificates without open port 80: DNS challenge

If port 80 or 443 is not reachable from the internet, Caddy can prove ownership through DNS. This needs a Caddy build with the plugin for your DNS provider, see [TLS certificates](ssl-certificates.md#caddy-and-traefik).

### Several servers

```caddy
survival.example.com {
	reverse_proxy 127.0.0.1:8765
}

creative.example.com {
	reverse_proxy 127.0.0.1:8766
}
```

## Docker

If Caddy runs in a container and MineDash on the host, point it at the host address of the container network, for example:

```caddy
panel.example.com {
	reverse_proxy host.docker.internal:8765
}
```

and add `extra_hosts: ["host.docker.internal:host-gateway"]` to the Caddy service in Compose. MineDash then has to listen on an address the container can reach (`web.host` = `0.0.0.0`, firewall port 8765 so that only the Docker network can use it). When MineDash listens on `127.0.0.1` only, a proxy in a bridge-network container cannot reach it. See [Hosting panels and Docker](hosting-panels.md).

## Check it

Use the [test list](reverse-proxy.md#test-your-setup).

If the certificate is not issued, read the log: `journalctl -u caddy --no-pager -n 100`. The usual causes are a DNS record that does not point to the server yet, or closed ports 80/443.
