# nginx

nginx is the most common reverse proxy. This page contains a complete configuration for MineDash with HTTPS, WebSockets and large uploads.

> The configurations in this documentation follow the official nginx syntax, but they were not run against every nginx version. Always check with `nginx -t` before you reload.

## Prerequisites

- nginx installed (`sudo apt install nginx` on Debian/Ubuntu, `sudo dnf install nginx` on Fedora/Rocky)
- a DNS record for your domain, see [Domain and DNS](domain-and-dns.md)
- ports 80 and 443 open
- MineDash configured as in [Reverse proxy basics](reverse-proxy.md#configure-minedash-first)

## Step 1: a first server block on port 80

Start with HTTP only. The certificate tool needs it, and it lets you check that the proxy reaches MineDash.

Create `/etc/nginx/conf.d/minedash.conf` (or `/etc/nginx/sites-available/minedash` with a link in `sites-enabled` on Debian/Ubuntu):

```nginx
server {
    listen 80;
    listen [::]:80;
    server_name panel.example.com;

    # Folder used by certbot's webroot mode, see "TLS certificates"
    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        return 301 https://$host$request_uri;
    }
}
```

```bash
sudo mkdir -p /var/www/certbot
sudo nginx -t && sudo systemctl reload nginx
```

## Step 2: get a certificate

Follow [TLS certificates](ssl-certificates.md#certbot-with-nginx). When you are done you have these files (the path depends on your domain):

```text
/etc/letsencrypt/live/panel.example.com/fullchain.pem
/etc/letsencrypt/live/panel.example.com/privkey.pem
```

## Step 3: the full configuration

Replace the file with this version. Change `panel.example.com` and the paths.

```nginx
# Needed for WebSockets: "Connection: upgrade" only for upgrade requests
map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
}

upstream minedash {
    server 127.0.0.1:8765;
    keepalive 16;
}

server {
    listen 80;
    listen [::]:80;
    server_name panel.example.com;

    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        return 301 https://$host$request_uri;
    }
}

server {
    listen 443 ssl;
    listen [::]:443 ssl;
    # nginx 1.25.1 or newer. On older versions write "listen 443 ssl http2;" instead.
    http2 on;
    server_name panel.example.com;

    ssl_certificate     /etc/letsencrypt/live/panel.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/panel.example.com/privkey.pem;

    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 1d;
    ssl_session_tickets off;

    # Uploads of the Files module. Must be at least maxUploadBytes (default 256 MiB).
    client_max_body_size 300m;

    # Do not buffer uploads and downloads, MineDash streams them.
    proxy_request_buffering off;
    proxy_buffering off;

    # Long transfers and idle WebSockets
    proxy_connect_timeout 10s;
    proxy_send_timeout    1h;
    proxy_read_timeout    1h;
    send_timeout          1h;

    location / {
        proxy_pass http://minedash;
        proxy_http_version 1.1;

        # WebSocket support
        proxy_set_header Upgrade    $http_upgrade;
        proxy_set_header Connection $connection_upgrade;

        # Keep the host exactly as the browser sent it, including a custom port
        proxy_set_header Host              $http_host;
        proxy_set_header X-Forwarded-Host  $http_host;
        proxy_set_header X-Forwarded-Proto $scheme;

        # MineDash reads the LAST entry. $remote_addr replaces whatever the client sent,
        # so nobody can fake their address.
        proxy_set_header X-Forwarded-For   $remote_addr;
    }
}
```

```bash
sudo nginx -t
sudo systemctl reload nginx
```

### What each part does

| Line | Why it is there |
| --- | --- |
| `map $http_upgrade $connection_upgrade` | Sends `Connection: upgrade` for WebSockets and `close` for everything else |
| `upstream … keepalive` | Re-uses connections to MineDash. Works together with `proxy_http_version 1.1` and the `Connection` header set above. |
| `client_max_body_size 300m` | nginx rejects larger bodies with `413`. Keep it above the upload limit of the Files module. |
| `proxy_request_buffering off` | Uploads go straight to MineDash and are not written to a temporary file first |
| `proxy_buffering off` | Downloads and ZIP archives are streamed to the browser |
| `proxy_read_timeout 1h` | nginx closes a connection after 60 s without data by default. Idle WebSockets and slow transfers need more. |
| `Host $http_host` | Keeps the port. `$host` would drop it. |
| `X-Forwarded-For $remote_addr` | Replaces the header instead of appending. Do not use `$proxy_add_x_forwarded_for` unless you know why, the value then depends on what the client sent. |

### Cloudflare or another proxy in front of nginx

If nginx itself sits behind another proxy (for example Cloudflare), `$remote_addr` is the address of that proxy, not of the visitor. See [Cloudflare](cloudflare.md#real-visitor-addresses).

## Serving the panel on a custom HTTPS port

If you serve the panel on a port other than 443, for example 8443:

```nginx
server {
    listen 8443 ssl;
    server_name panel.example.com;
    # …same as above…
}
```

The `Host` header then contains `panel.example.com:8443`. Because the configuration above uses `$http_host`, the origin check keeps working. Open the port in your firewall.

## Optional: restrict access by IP address

Only allow your own addresses to reach the panel:

```nginx
location / {
    allow 203.0.113.10;
    allow 192.168.1.0/24;
    deny  all;

    proxy_pass http://minedash;
    # …the proxy_set_header lines from above…
}
```

## Optional: extra password in front of the panel

An extra layer of HTTP basic authentication in nginx. Note that browsers send this header for every request, so it combines with the panel login, not instead of it.

```bash
sudo apt install apache2-utils
sudo htpasswd -c /etc/nginx/minedash.htpasswd youradmin
```

```nginx
location / {
    auth_basic           "MineDash";
    auth_basic_user_file /etc/nginx/minedash.htpasswd;
    proxy_pass http://minedash;
    # …
}
```

## Optional: rate limit

MineDash already limits failed logins per address. A limit in nginx protects the login endpoint earlier and also stops flooding:

```nginx
# in the http { } context, for example at the top of the conf.d file
limit_req_zone $binary_remote_addr zone=minedash_login:10m rate=10r/m;

# inside the server { } block
location = /api/auth/login {
    limit_req zone=minedash_login burst=5 nodelay;
    proxy_pass http://minedash;
    # …the proxy_set_header lines from above…
}
```

When you add a location like this, repeat the `proxy_set_header` lines inside it, because `proxy_set_header` is not inherited into a block that defines its own.

## Do not add these

- Do not add your own `Content-Security-Policy`, `X-Frame-Options` or `Cache-Control` headers for the panel. MineDash sends the right ones and a second copy can break the page.
- Do not enable `gzip` or caching for `/api/`.

## Check it

Use the [test list](reverse-proxy.md#test-your-setup). If something fails, see [Troubleshooting](../reference/troubleshooting.md).
