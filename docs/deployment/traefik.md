# Traefik

Traefik is a reverse proxy that is configured with files or with Docker labels. It gets certificates from Let's Encrypt on its own, supports WebSockets and sets the forwarded headers.

This page shows two setups:

1. Traefik with **static and dynamic files**, for MineDash running on the host
2. Traefik in **Docker Compose**, for a Minecraft server that also runs in Docker

> The configurations follow the Traefik v3 documentation but were not run here. Check the log after a start for errors.

## Prerequisites

- Traefik v3 (the syntax is the same in v2 for the parts used here)
- a DNS record for your domain, see [Domain and DNS](domain-and-dns.md)
- ports 80 and 443 open
- MineDash configured as in [Reverse proxy basics](reverse-proxy.md#configure-minedash-first)

## 1. Traefik with files (MineDash on the host)

### Static configuration: `/etc/traefik/traefik.yml`

```yaml
entryPoints:
  web:
    address: ":80"
    http:
      redirections:
        entryPoint:
          to: websecure
          scheme: https
  websecure:
    address: ":443"
    transport:
      respondingTimeouts:
        # Long uploads and downloads. 0 = no timeout.
        readTimeout: 0s
        writeTimeout: 0s
        idleTimeout: 3600s

providers:
  file:
    filename: /etc/traefik/dynamic.yml
    watch: true

certificatesResolvers:
  letsencrypt:
    acme:
      email: you@example.com
      storage: /etc/traefik/acme.json
      httpChallenge:
        entryPoint: web
```

```bash
sudo touch /etc/traefik/acme.json
sudo chmod 600 /etc/traefik/acme.json
```

### Dynamic configuration: `/etc/traefik/dynamic.yml`

```yaml
http:
  routers:
    minedash:
      rule: "Host(`panel.example.com`)"
      entryPoints:
        - websecure
      service: minedash
      tls:
        certResolver: letsencrypt

  services:
    minedash:
      loadBalancer:
        servers:
          - url: "http://127.0.0.1:8765"
        passHostHeader: true
```

If Traefik runs as a container with the host network, `127.0.0.1` refers to the host. In a bridge network, use the address of the host as seen from the container (for example `http://host.docker.internal:8765`) and allow the connection, see below.

## 2. Traefik in Docker Compose

This example runs Traefik and a Minecraft server (with MineDash inside) in one Compose project. The Minecraft image is only an example, use whichever you have.

```yaml
services:
  traefik:
    image: traefik:v3.1
    restart: unless-stopped
    command:
      - --providers.docker=true
      - --providers.docker.exposedbydefault=false
      - --entrypoints.web.address=:80
      - --entrypoints.web.http.redirections.entrypoint.to=websecure
      - --entrypoints.web.http.redirections.entrypoint.scheme=https
      - --entrypoints.websecure.address=:443
      - --entrypoints.websecure.transport.respondingtimeouts.readtimeout=0s
      - --entrypoints.websecure.transport.respondingtimeouts.writetimeout=0s
      - --certificatesresolvers.letsencrypt.acme.email=you@example.com
      - --certificatesresolvers.letsencrypt.acme.storage=/letsencrypt/acme.json
      - --certificatesresolvers.letsencrypt.acme.httpchallenge.entrypoint=web
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock:ro
      - ./letsencrypt:/letsencrypt

  minecraft:
    image: itzg/minecraft-server
    restart: unless-stopped
    environment:
      EULA: "TRUE"
      TYPE: PAPER
    ports:
      - "25565:25565"            # Minecraft. The panel port is NOT published.
    volumes:
      - ./data:/data
    labels:
      - traefik.enable=true
      - traefik.http.routers.minedash.rule=Host(`panel.example.com`)
      - traefik.http.routers.minedash.entrypoints=websecure
      - traefik.http.routers.minedash.tls.certresolver=letsencrypt
      - traefik.http.services.minedash.loadbalancer.server.port=8765
```

For this setup, MineDash needs to listen on an address that Traefik can reach inside the Docker network. Set in `plugins/MineDash/config.json`:

```json
{
  "web": { "host": "0.0.0.0", "port": 8765, "trustProxy": true },
  "auth": { "secureCookie": true }
}
```

This is safe as long as port 8765 is **not** published with `ports:`: only containers in the same Compose network can reach it. Everything from the internet must pass Traefik.

> Mounting `docker.sock` gives Traefik control over Docker. Many people place a socket proxy in between. This is a Traefik topic and not specific to MineDash.

## What Traefik handles for you

| Need | Traefik |
| --- | --- |
| Certificate and renewal | ACME resolver in the static configuration |
| HTTP to HTTPS | redirection on the `web` entry point |
| WebSockets | automatic |
| `Host` header | passed on unchanged (`passHostHeader` is true by default) |
| `X-Forwarded-For`, `-Host`, `-Proto` | set automatically |
| Request size limit | none by default |

By default Traefik does not trust forwarded headers from clients. MineDash reads the last `X-Forwarded-For` entry, which is the address Traefik saw, so a forged header from the client is ignored. If Traefik is behind another proxy such as Cloudflare, see [Cloudflare](cloudflare.md#real-visitor-addresses).

## Other challenges

- **DNS challenge** (no open port needed, wildcard certificates): replace `httpChallenge` by `dnsChallenge` with your provider, see [TLS certificates](ssl-certificates.md#caddy-and-traefik).
- **Your own certificate** (for example a Cloudflare origin certificate): list it in the dynamic file under `tls.certificates` with `certFile` and `keyFile`, and leave out `certResolver`.

## Check it

Use the [test list](reverse-proxy.md#test-your-setup). The Traefik log (`docker logs traefik` or `journalctl -u traefik`) shows certificate and routing errors.
