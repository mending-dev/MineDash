# TLS certificates (HTTPS)

HTTPS needs a certificate for your domain. MineDash does not handle certificates itself, the **reverse proxy** does. This page covers every common way to get one.

| Method | Cost | Needs port 80/443 open | Best for |
| --- | --- | --- | --- |
| [Caddy or Traefik](#caddy-and-traefik) | free | yes (or DNS challenge) | Nothing to do, automatic |
| [Certbot with nginx](#certbot-with-nginx) | free | yes | Standard Linux setup with nginx |
| [Certbot with Apache](#certbot-with-apache) | free | yes | Standard Linux setup with Apache |
| [Certbot standalone](#certbot-standalone) | free | port 80 | Create a certificate without a running web server |
| [DNS challenge / wildcard](#dns-challenge-and-wildcard-certificates) | free | **no** | Server not reachable from the internet, or one certificate for many subdomains |
| [Cloudflare origin certificate](#cloudflare-origin-certificate) | free | no | Only with the Cloudflare proxy |
| [Cloudflare Tunnel](cloudflare.md#cloudflare-tunnel) | free | no | Nothing to install on the server |
| [Local certificate (mkcert)](#local-network-mkcert-and-self-signed) | free | no | Home network with an internal name |
| [Self-signed](#local-network-mkcert-and-self-signed) | free | no | Tests only |
| [Commercial certificate](#commercial-or-existing-certificates) | paid | no | If you already have one or need EV/OV |

Whatever you use, check [Renewal](#renewal) and [Verify](#verify-your-certificate) at the end.

## Caddy and Traefik

Both request and renew certificates **automatically**. You only need the domain in the configuration and open ports 80/443. See [Caddy](caddy.md) and [Traefik](traefik.md).

### With a DNS challenge

If the ports are not reachable, both can prove ownership via DNS. This needs support for your DNS provider:

- **Caddy**: a build that contains the DNS plugin of your provider (for example `caddy-dns/cloudflare`). Build with `xcaddy build --with github.com/caddy-dns/cloudflare`, then:

  ```caddy
  panel.example.com {
  	tls {
  		dns cloudflare {env.CF_API_TOKEN}
  	}
  	reverse_proxy 127.0.0.1:8765
  }
  ```

- **Traefik**: in the static configuration replace `httpChallenge` with

  ```yaml
  certificatesResolvers:
    letsencrypt:
      acme:
        email: you@example.com
        storage: /etc/traefik/acme.json
        dnsChallenge:
          provider: cloudflare
  ```

  and provide the credentials of the provider as environment variables (for Cloudflare `CF_DNS_API_TOKEN`). The list of supported providers and their variables is in the Traefik documentation.

## Certbot with nginx

Certbot is the standard client for Let's Encrypt.

### Install

```bash
# Debian / Ubuntu
sudo apt install certbot python3-certbot-nginx

# Fedora / Rocky / Alma (EPEL needed on Rocky/Alma)
sudo dnf install certbot python3-certbot-nginx
```

Alternatively install it with `snap` as described on the Certbot website.

### Option A: let certbot edit nginx

If you only have the simple port 80 block from the [nginx page](nginx.md#step-1-a-first-server-block-on-port-80):

```bash
sudo certbot --nginx -d panel.example.com
```

Certbot gets the certificate, edits your configuration to use it and sets up the redirect. Afterwards add the proxy settings (`location /` with `proxy_pass`, WebSockets, `client_max_body_size`) from the [nginx page](nginx.md#step-3-the-full-configuration) into the new HTTPS block.

### Option B: webroot (recommended, no automatic edits)

Use the configuration from the nginx page, where port 80 serves `/.well-known/acme-challenge/` from `/var/www/certbot`:

```bash
sudo certbot certonly --webroot -w /var/www/certbot -d panel.example.com
```

Then use the paths certbot prints in the HTTPS block. Reload nginx after changes:

```bash
sudo nginx -t && sudo systemctl reload nginx
```

## Certbot with Apache

```bash
# Debian / Ubuntu
sudo apt install certbot python3-certbot-apache

# Let certbot edit Apache
sudo certbot --apache -d panel.example.com

# or, webroot (the folder from the Apache page)
sudo certbot certonly --webroot -w /var/www/certbot -d panel.example.com
```

For the webroot variant, the challenge folder must be reachable under `/.well-known/acme-challenge/` on port 80, as set up on the [Apache page](apache.md#step-2-http-virtual-host-and-certificate).

## Certbot standalone

Certbot starts its own tiny web server on port 80, so **no other program may use port 80** while it runs:

```bash
sudo systemctl stop nginx        # or apache2 / caddy
sudo certbot certonly --standalone -d panel.example.com
sudo systemctl start nginx
```

For renewals without manual stop/start, use hooks:

```bash
sudo certbot renew --pre-hook "systemctl stop nginx" --post-hook "systemctl start nginx"
```

## DNS challenge and wildcard certificates

With the DNS challenge, you prove that you control the domain by creating a TXT record. No port needs to be open, and you can get a wildcard certificate (`*.example.com`) that covers every subdomain.

Certbot has plugins for many DNS providers. Example with Cloudflare:

```bash
sudo apt install python3-certbot-dns-cloudflare
sudo mkdir -p /root/.secrets
sudo sh -c 'echo "dns_cloudflare_api_token = YOUR_API_TOKEN" > /root/.secrets/cloudflare.ini'
sudo chmod 600 /root/.secrets/cloudflare.ini

sudo certbot certonly --dns-cloudflare \
  --dns-cloudflare-credentials /root/.secrets/cloudflare.ini \
  -d example.com -d '*.example.com'
```

Use an API token with the least rights needed (edit DNS for that one zone). Other providers have their own plugin (`certbot-dns-route53`, `certbot-dns-digitalocean`, …) with the same pattern.

Without a plugin for your provider, there is a manual mode (`--manual --preferred-challenges dns`), but renewal cannot be automated that way. For that case consider [acme.sh](https://github.com/acmesh-official/acme.sh) or [lego](https://go-acme.github.io/lego/), which support a very large number of DNS providers.

## Cloudflare origin certificate

If you use the Cloudflare orange cloud with the mode *Full (strict)*, you can use a free certificate that Cloudflare issues for the connection between Cloudflare and **your** server. It is only trusted by Cloudflare, not by browsers, so it only works with the proxy turned on.

1. Cloudflare dashboard → your domain → **SSL/TLS → Origin Server → Create Certificate**.
2. Pick the host names (`panel.example.com` or `*.example.com`) and a validity (up to 15 years).
3. Copy the certificate to `/etc/ssl/cloudflare/panel.pem` and the private key to `/etc/ssl/cloudflare/panel.key` on the server.

   ```bash
   sudo mkdir -p /etc/ssl/cloudflare
   sudo nano /etc/ssl/cloudflare/panel.pem
   sudo nano /etc/ssl/cloudflare/panel.key
   sudo chmod 600 /etc/ssl/cloudflare/panel.key
   ```

4. In nginx use these two files:

   ```nginx
   ssl_certificate     /etc/ssl/cloudflare/panel.pem;
   ssl_certificate_key /etc/ssl/cloudflare/panel.key;
   ```

The certificate is not renewed automatically for years, but if you ever turn the orange cloud off, browsers will show a warning.

## Local network: mkcert and self-signed

If the panel only lives in your home network under a name like `minedash.lan`, public certificate authorities cannot issue a certificate for it.

### mkcert (recommended for local use)

`mkcert` creates a small private certificate authority that your own devices trust:

```bash
mkcert -install
mkcert minedash.lan 192.168.1.20
```

This writes `minedash.lan+1.pem` and `minedash.lan+1-key.pem`. Use them in nginx (`ssl_certificate`, `ssl_certificate_key`). Install the root certificate (`mkcert -CAROOT`) on every other device that should trust the panel.

### Self-signed (tests only)

```bash
openssl req -x509 -nodes -newkey rsa:4096 -days 365 \
  -keyout minedash.key -out minedash.crt \
  -subj "/CN=minedash.lan" \
  -addext "subjectAltName=DNS:minedash.lan,IP:192.168.1.20"
```

Browsers will show a warning for this certificate. Do not train people to click through warnings. Use mkcert or a real certificate instead.

> The connection between the **proxy and MineDash on the same machine** needs no certificate at all. It never leaves the machine.

## Commercial or existing certificates

If you bought a certificate or your company provides one, you need two files: the certificate **with the intermediate chain** (often called `fullchain.pem`) and the private key. Use them in `ssl_certificate` and `ssl_certificate_key` (nginx), or `SSLCertificateFile` and `SSLCertificateKeyFile` (Apache). A missing intermediate certificate causes errors on some devices even when your browser is fine.

## Renewal

Let's Encrypt certificates are valid for 90 days. Certbot installs a systemd timer or cron job that renews them. Check:

```bash
systemctl list-timers | grep certbot
sudo certbot renew --dry-run
```

nginx and Apache need a reload to use a new certificate. A deploy hook does that after each renewal:

```bash
sudo sh -c 'printf "#!/bin/sh\nsystemctl reload nginx\n" > /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh'
sudo chmod +x /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh
```

(use `apache2` or `httpd` instead of `nginx` for Apache). Caddy and Traefik reload on their own.

## Verify your certificate

```bash
# What does the server present, and when does it expire?
echo | openssl s_client -connect panel.example.com:443 -servername panel.example.com 2>/dev/null \
  | openssl x509 -noout -subject -issuer -dates

# Does a browser-like client accept it?
curl -vI https://panel.example.com 2>&1 | grep -E "SSL|subject|expire|HTTP/"
```

Online tests such as the SSL Labs server test show the chain and protocol support.

## After HTTPS works

Set `auth.secureCookie` to `true` in `config.json`. Then MineDash marks its session cookie `Secure` and sends the HSTS header. See [Configuration](../administration/configuration.md#securecookie).
