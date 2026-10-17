# Cloudflare

Cloudflare can sit between visitors and your server in two ways:

| Way | Needs open ports? | Needs a proxy of your own? | Good for |
| --- | --- | --- | --- |
| [Cloudflare proxy](#cloudflare-proxy-orange-cloud) (orange cloud) | yes, 443 | yes (nginx, Apache, Caddy, …) | A normal server with a public IP that you want to hide and protect |
| [Cloudflare Tunnel](#cloudflare-tunnel) | **no** | no, the tunnel goes straight to MineDash | Home servers, carrier-grade NAT, no access to the router |

Both give you HTTPS between the visitor and Cloudflare with a certificate that Cloudflare manages.

> Cloudflare changes plans, limits and menu names from time to time. Check the current Cloudflare documentation for details, in particular the **upload size limit** (see below).

## Cloudflare proxy (orange cloud)

### 1. DNS

In the Cloudflare dashboard, add an `A` (or `AAAA`) record for `panel` to your server's IP and turn the **proxy status to Proxied** (orange cloud).

### 2. TLS mode

Under **SSL/TLS**, set the encryption mode to **Full (strict)**. Do not use *Flexible*: it sends traffic from Cloudflare to your server without encryption, and it breaks `secureCookie` redirects (endless redirect loops).

*Full (strict)* needs a valid certificate on your server. Either:

- a Let's Encrypt certificate, see [TLS certificates](ssl-certificates.md), or
- a free **Cloudflare Origin Certificate**, see [TLS certificates](ssl-certificates.md#cloudflare-origin-certificate)

### 3. Your own proxy

Use [nginx](nginx.md), [Apache](apache.md), [Caddy](caddy.md) or [Traefik](traefik.md) as described there. Cloudflare passes WebSockets without extra settings (the setting *Network → WebSockets* is on by default).

### Real visitor addresses

With the orange cloud, every connection to your server comes from a Cloudflare address. For MineDash this matters: with `trustProxy` on, it takes the **last** entry of `X-Forwarded-For`. If your proxy writes `$remote_addr` there, that is a Cloudflare address, so all visitors would look like the same few clients to the login rate limit.

Fix: let your proxy restore the real address from the header `CF-Connecting-IP`, but **only for connections that really come from Cloudflare**. In nginx:

```nginx
# Download the current lists and keep them up to date:
#   https://www.cloudflare.com/ips-v4
#   https://www.cloudflare.com/ips-v6
# One line per address range:
set_real_ip_from 173.245.48.0/20;     # example, use the current list
# set_real_ip_from … ;

real_ip_header CF-Connecting-IP;
```

Put this into the `http { }` block (for example in `/etc/nginx/conf.d/cloudflare-realip.conf`). After that, `$remote_addr` is the visitor's address, and the standard line `proxy_set_header X-Forwarded-For $remote_addr;` from the [nginx page](nginx.md) does the right thing. The list changes rarely, but not never: refresh it on a schedule.

For Caddy, set `trusted_proxies` in the global options with the Cloudflare ranges and use the client address header:

```caddy
{
	servers {
		trusted_proxies static <list of Cloudflare ranges>
		client_ip_headers CF-Connecting-IP
	}
}
```

Also close the door in the firewall: only allow ports 80/443 from Cloudflare addresses, otherwise anybody can reach your server directly and bypass Cloudflare.

### Upload size limit

Cloudflare limits the size of a request body that goes through the proxy. At the time of writing this is 100 MB on the Free and Pro plans (more on higher plans). Uploads in the Files module that are bigger than the limit fail, no matter what your nginx allows. Check the current limit in the Cloudflare documentation.

Options:

- Upload large files with SFTP or the host's own file manager.
- Use a second host name **without** the orange cloud (grey cloud, *DNS only*) only for uploads. This exposes your IP again.
- Use a [tunnel](#cloudflare-tunnel): the same request size limits apply there.

## Cloudflare Tunnel

A tunnel makes an outgoing connection from your server to Cloudflare. Visitors come in through Cloudflare, and no port needs to be opened at all.

### 1. Install `cloudflared`

Follow the Cloudflare instructions for your system. On Debian/Ubuntu, add the Cloudflare package repository and install `cloudflared`. Then log in and create a tunnel:

```bash
cloudflared tunnel login
cloudflared tunnel create minedash
cloudflared tunnel route dns minedash panel.example.com
```

The second command prints the tunnel ID and creates a credentials file.

### 2. Configuration

Create `/etc/cloudflared/config.yml`:

```yaml
tunnel: <TUNNEL-ID>
credentials-file: /etc/cloudflared/<TUNNEL-ID>.json

ingress:
  - hostname: panel.example.com
    service: http://127.0.0.1:8765
  - service: http_status:404
```

The tunnel talks plain HTTP to MineDash on the same machine. Cloudflare provides the HTTPS for visitors. No separate reverse proxy is needed.

### 3. Run it as a service

```bash
sudo cloudflared service install
sudo systemctl enable --now cloudflared
```

### 4. MineDash settings

```json
{
  "web": { "host": "127.0.0.1", "port": 8765, "trustProxy": true },
  "auth": { "secureCookie": true }
}
```

With a tunnel, `cloudflared` is the client of MineDash and connects from `127.0.0.1`. Cloudflare adds the visitor's address to `X-Forwarded-For`, and the `Host` header of the visitor's request is passed on. With `trustProxy` on, MineDash then uses the last entry of `X-Forwarded-For` as the visitor's address.

> Check this in your own setup instead of assuming it: make a failed login from your phone (mobile network) and one from your computer, and compare the addresses in the log. If both show the same address, the login attempt limit counts all visitors together, and you should look at the headers your tunnel really sends.

### Docker

Run `cloudflared` in Compose next to the Minecraft container and use the service name in `service: http://minecraft:8765`. Do not publish port 8765. See [Hosting panels and Docker](hosting-panels.md).

## Cloudflare Access (optional)

Cloudflare Zero Trust *Access* can put a login (one-time PIN by email, Google, GitHub, …) in front of the panel. This is a second layer before MineDash's own login. It is configured in the Cloudflare dashboard and independent of MineDash.

## Check it

Use the [test list](reverse-proxy.md#test-your-setup). A redirect loop in the browser almost always means the SSL/TLS mode is *Flexible*.
