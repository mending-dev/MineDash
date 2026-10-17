# Apache HTTP Server

Apache works as a reverse proxy with `mod_proxy`. This page shows a complete virtual host for MineDash.

> The configurations in this documentation were written from the official Apache documentation, but not run against every version. Always check with `apachectl configtest` before you reload.

## Prerequisites

- Apache 2.4 installed
- a DNS record for your domain, see [Domain and DNS](domain-and-dns.md)
- ports 80 and 443 open
- MineDash configured as in [Reverse proxy basics](reverse-proxy.md#configure-minedash-first)

## Step 1: enable the modules

Debian and Ubuntu:

```bash
sudo a2enmod proxy proxy_http proxy_wstunnel rewrite headers ssl
sudo systemctl restart apache2
```

On Fedora, Rocky and Alma, these modules are loaded by default in `/etc/httpd/conf.modules.d/`. Check with `httpd -M | grep -E "proxy|rewrite|headers|ssl"`.

## Step 2: HTTP virtual host and certificate

Create `/etc/apache2/sites-available/minedash.conf` (Debian/Ubuntu) or `/etc/httpd/conf.d/minedash.conf` (Fedora/Rocky/Alma):

```apache
<VirtualHost *:80>
    ServerName panel.example.com

    # Folder used by certbot's webroot mode
    Alias /.well-known/acme-challenge/ /var/www/certbot/.well-known/acme-challenge/
    <Directory "/var/www/certbot/.well-known/acme-challenge/">
        Require all granted
    </Directory>

    RewriteEngine On
    RewriteCond %{REQUEST_URI} !^/\.well-known/acme-challenge/
    RewriteRule ^ https://%{HTTP_HOST}%{REQUEST_URI} [R=301,L]
</VirtualHost>
```

```bash
sudo mkdir -p /var/www/certbot/.well-known/acme-challenge
sudo a2ensite minedash          # Debian/Ubuntu only
sudo apachectl configtest && sudo systemctl reload apache2
```

Then get a certificate, see [TLS certificates](ssl-certificates.md#certbot-with-apache).

## Step 3: HTTPS virtual host

Add this to the same file. Change the domain and the certificate paths.

```apache
<VirtualHost *:443>
    ServerName panel.example.com

    SSLEngine on
    SSLCertificateFile    /etc/letsencrypt/live/panel.example.com/fullchain.pem
    SSLCertificateKeyFile /etc/letsencrypt/live/panel.example.com/privkey.pem
    SSLProtocol           all -SSLv3 -TLSv1 -TLSv1.1

    # Keep the Host header of the browser (required for the origin check)
    ProxyPreserveHost On

    # We set the forwarded headers ourselves
    RequestHeader set X-Forwarded-Proto "https"
    RequestHeader set X-Forwarded-Port  "443"

    # Long transfers: a big upload or a ZIP download may take minutes
    ProxyTimeout 3600

    # No size limit for request bodies (0 = unlimited). Upload size is limited by MineDash.
    LimitRequestBody 0

    # WebSockets first, then everything else
    RewriteEngine On
    RewriteCond %{HTTP:Upgrade} =websocket [NC]
    RewriteRule ^/(.*)$ ws://127.0.0.1:8765/$1 [P,L]

    ProxyPass        / http://127.0.0.1:8765/ upgrade=websocket
    ProxyPassReverse / http://127.0.0.1:8765/
</VirtualHost>
```

```bash
sudo apachectl configtest
sudo systemctl reload apache2     # Fedora/Rocky/Alma: httpd
```

### What each part does

| Line | Why |
| --- | --- |
| `ProxyPreserveHost On` | Passes the browser's `Host` (with port) to MineDash |
| `RequestHeader set X-Forwarded-Proto` | Tells MineDash the browser used HTTPS |
| `ProxyTimeout 3600` | The default of 60 s would cut long transfers and idle WebSockets |
| `RewriteRule … ws://… [P,L]` and `upgrade=websocket` | Two ways to pass WebSockets. The first works on Apache 2.4.x with `mod_proxy_wstunnel`, the second on Apache 2.4.47 and newer. Having both is harmless. |
| `LimitRequestBody 0` | Apache has no small default limit, the line only makes that explicit |

`mod_proxy` adds `X-Forwarded-For` (with the client address as the last entry) and `X-Forwarded-Host` (the original host) on its own. That is what MineDash expects with `trustProxy` on. Do not add `RequestHeader set X-Forwarded-For`, as it would hide the module's value.

## Pitfalls

- **Missing module.** The error `Invalid command 'ProxyPreserveHost'` or `'RewriteEngine'` means the module from step 1 is not enabled.
- **WebSockets close immediately.** Check that `proxy_wstunnel` is enabled and the rewrite rule comes before the general `ProxyPass`.
- **`403` on every change.** The `Host` header got lost. Check `ProxyPreserveHost On`.
- **A trailing slash.** Keep the trailing slash in `ProxyPass / http://127.0.0.1:8765/`. It is written correctly above.

## Optional: restrict access by IP address

```apache
<Location "/">
    Require ip 203.0.113.10 192.168.1.0/24
</Location>
```

## Optional: extra password

```bash
sudo htpasswd -c /etc/apache2/minedash.htpasswd youradmin
```

```apache
<Location "/">
    AuthType Basic
    AuthName "MineDash"
    AuthUserFile /etc/apache2/minedash.htpasswd
    Require valid-user
</Location>
```

## Do not add these

Do not add your own `Content-Security-Policy`, `X-Frame-Options` or cache headers for the panel. MineDash sends the right ones.

## Check it

Use the [test list](reverse-proxy.md#test-your-setup).
