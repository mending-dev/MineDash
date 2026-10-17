# Domain and DNS

A domain name makes the panel easy to remember, and it is required to get a trusted HTTPS certificate.

## Pick a name

Use a subdomain of a domain you own:

```text
panel.example.com
```

A subdomain is better than a path (`example.com/panel`), because the panel must be served at the root of its host name. It also keeps the cookie of the panel separate from your website.

> If you already use `example.com` for a website or for your Minecraft address, do not change those records. Add a new record for `panel`.

## Create the DNS record

In the DNS settings of your domain (at your registrar or DNS provider), add a record that points to the **public IP address of the machine that runs your reverse proxy**:

| Type | Name | Value | Use |
| --- | --- | --- | --- |
| `A` | `panel` | `203.0.113.10` | IPv4 address of the server |
| `AAAA` | `panel` | `2001:db8::10` | IPv6 address of the server (only if the server really is reachable over IPv6) |
| `CNAME` | `panel` | `myserver.example.net.` | Alternative: point to another name that already has an `A` record (for example a dynamic DNS name) |

Do not add an `AAAA` record unless IPv6 works end to end, including your firewall and your proxy. A wrong `AAAA` record makes the panel unreachable for people who have IPv6.

How to find the public IP of the server:

```bash
curl -4 https://ifconfig.me
curl -6 https://ifconfig.me
```

DNS changes can take a few minutes, sometimes longer. Check whether the record arrived:

```bash
dig +short panel.example.com
nslookup panel.example.com
```

## Server at home or with a changing IP

If your internet provider changes your IP address now and then:

- use a **dynamic DNS** service (for example DuckDNS, No-IP, or the dynamic DNS feature of your DNS provider or router) and make your record a `CNAME` to that name, or update the `A` record automatically with a small script or your router,
- or use [Cloudflare Tunnel](cloudflare.md#cloudflare-tunnel), which needs no open port and no public IP at all.

If your provider shares one IP between many customers (carrier-grade NAT), incoming connections do not reach your home. Then a tunnel is the only choice.

## Open the ports

For a reverse proxy with a certificate from Let's Encrypt, the machine that runs the proxy must be reachable on:

| Port | Why |
| --- | --- |
| 80/tcp | Certificate checks (HTTP-01) and the redirect to HTTPS |
| 443/tcp | The panel over HTTPS |

Do **not** open the MineDash port (8765) to the internet when you use a proxy. The proxy reaches it on `127.0.0.1`.

### Linux firewall examples

```bash
# ufw (Ubuntu, Debian)
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw status

# firewalld (Fedora, Rocky, Alma)
sudo firewall-cmd --permanent --add-service=http --add-service=https
sudo firewall-cmd --reload
```

Docker publishes ports around `ufw`. A container port published with `-p 8765:8765` is reachable from outside even if `ufw` denies it. Publish it on the loopback address instead: `-p 127.0.0.1:8765:8765`, see [Hosting panels and Docker](hosting-panels.md).

### Router and cloud firewalls

- Home router: forward ports 80 and 443 (TCP) to the machine with the proxy.
- Cloud providers (AWS, Hetzner, DigitalOcean, OVH, …): open ports 80 and 443 in the provider's firewall or security group as well, not only in the operating system.

## Check from outside

```bash
curl -I http://panel.example.com
```

You should get an answer from your proxy (even if it is only a redirect or an error page). A timeout means the port is not reachable, see [Troubleshooting](../reference/troubleshooting.md).

## The Minecraft address is a different thing

Players connect to the Minecraft port (25565 by default) with the address of the game server. You can use the same domain with a different subdomain, but the panel does not interact with SRV records or the game port in any way.
