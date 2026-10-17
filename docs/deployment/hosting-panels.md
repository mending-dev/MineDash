# Docker, Pterodactyl and game hosts

Many Minecraft servers do not run directly on a machine you control, but in a container or on a rented game host. The panel works the same way, but making its port reachable needs a few extra steps.

## What you always need

1. A port for the panel that is reachable from where you want to open it
2. `web.host` set so that the connection can arrive (`0.0.0.0` inside a container)
3. HTTPS if the panel is reachable from the internet

## Docker

### Plain `docker run`

Publish the panel port **on the loopback address**, and put a reverse proxy on the host in front of it:

```bash
docker run -d --name mc \
  -e EULA=TRUE -e TYPE=PAPER \
  -p 25565:25565 \
  -p 127.0.0.1:8765:8765 \
  -v "$PWD/data:/data" \
  itzg/minecraft-server
```

`127.0.0.1:8765:8765` means: the host listens on its own loopback address only. The proxy on the host reaches the panel through `http://127.0.0.1:8765`, and the internet cannot.

Inside the container MineDash must listen on all interfaces, otherwise Docker cannot forward the connection:

```json
{
  "web": { "host": "0.0.0.0", "port": 8765, "trustProxy": true },
  "auth": { "secureCookie": true }
}
```

> **Careful:** `-p 8765:8765` (without the address) publishes the port on all interfaces. Docker adds its own firewall rules, so `ufw` does **not** block it. Always include `127.0.0.1:` for a panel behind a proxy.

Then configure [nginx](nginx.md), [Apache](apache.md) or [Caddy](caddy.md) on the host with `127.0.0.1:8765` as the target.

### Docker Compose

```yaml
services:
  minecraft:
    image: itzg/minecraft-server
    restart: unless-stopped
    environment:
      EULA: "TRUE"
      TYPE: PAPER
    ports:
      - "25565:25565"
      - "127.0.0.1:8765:8765"
    volumes:
      - ./data:/data
```

The files of MineDash end up in `./data/plugins/MineDash/` on the host (with this image). Edit `config.json` there and restart the container.

### Proxy inside the same Compose project

When the proxy is a container too, do not publish the panel port at all. Put both services in the same Compose network and let the proxy use the service name:

```yaml
services:
  minecraft:
    # …as above, but without the 8765 port mapping
  caddy:
    image: caddy:2
    ports: ["80:80", "443:443"]
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile
      - caddy_data:/data
volumes:
  caddy_data:
```

```caddy
panel.example.com {
	reverse_proxy minecraft:8765
}
```

[Traefik with labels](traefik.md#2-traefik-in-docker-compose) works the same way.

### Persistent data

Make sure `plugins/MineDash/` is in a volume. If it is not, `config.json`, `users.json` and the module data are lost whenever the container is recreated, and a new `admin` account with a new random password is created.

## Pterodactyl and similar game panels

Game panels such as Pterodactyl run each server in a container and decide which ports a server can use. MineDash needs one extra port.

1. **Create an allocation.** In the admin area of the game panel, add an extra port allocation for the node (for example 8765) and assign it to your server (*Server → Network*). The names differ between panels.
2. **Set the port in MineDash.** In `plugins/MineDash/config.json` set `web.port` to the allocated port and `web.host` to `0.0.0.0`.
3. **Reach it.** The panel is now available at `http://<node-ip>:<port>`. For HTTPS, put a reverse proxy in front on the node (or on another machine) and point it at `<node-ip>:<port>`.
4. **Firewall.** If the proxy runs on the same node, allow the port only from `127.0.0.1` (or from the proxy's address) and keep it closed to the internet.

If you cannot run a proxy on the node, a [Cloudflare Tunnel](cloudflare.md#cloudflare-tunnel) or a small separate VPS with a proxy that forwards to `<node-ip>:<port>` are alternatives.

## Rented Minecraft hosts

Providers differ. What you need to find out:

| Question | Why |
| --- | --- |
| Can I open one more TCP port? | The panel needs its own port. Many hosts offer extra ports on request or on higher plans. |
| Can I change `plugins/MineDash/config.json`? | Needed for host, port and proxy settings. Most hosts give file access. |
| Can I run a reverse proxy? | If not, use a Cloudflare Tunnel from another machine or a second small server as a proxy, or go without HTTPS and use only a trusted network. |

If the host only gives you the Minecraft port, MineDash cannot be reached from outside at all. Ask the host for a second port.

> A host that only gives you a plain `ip:port` leaves you with plain HTTP. In that case do not use the panel over untrusted networks.

## Check it

After the setup, run the [test list](reverse-proxy.md#test-your-setup). If the port does not answer, [Troubleshooting](../reference/troubleshooting.md) has a section for it.
