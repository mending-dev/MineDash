# Direct access without a proxy

In this setup, browsers talk to MineDash directly on its own port, for example `http://192.168.1.20:8765`. There is no HTTPS.

Use it only for:

- a test on your own computer
- a **trusted private network** (home LAN, an office network, a VPN such as WireGuard or Tailscale)

> **Warning:** On the internet, plain HTTP exposes passwords and session cookies to everybody on the path. For a public panel use a [reverse proxy with HTTPS](reverse-proxy.md).

## Configuration

Reachable from other machines in the network:

```json
{
  "web": { "host": "0.0.0.0", "port": 8765 },
  "auth": { "secureCookie": false }
}
```

Only reachable from the machine itself:

```json
{
  "web": { "host": "127.0.0.1", "port": 8765 }
}
```

Leave `trustProxy` at `false`. Every connection then counts with its real address.

With `host` set to anything but a loopback address and `secureCookie` off, the log warns about plain HTTP at every start. That is expected here.

## Open the port

Only to the network that needs it. Examples for Linux:

```bash
# ufw: allow only your local network
sudo ufw allow from 192.168.1.0/24 to any port 8765 proto tcp

# firewalld: same idea
sudo firewall-cmd --permanent --add-rich-rule='rule family="ipv4" source address="192.168.1.0/24" port protocol="tcp" port="8765" accept'
sudo firewall-cmd --reload
```

Do not forward the port in your router.

## Reach the panel from outside through a VPN

A VPN makes the private network available to you from anywhere without exposing the panel:

- **WireGuard / OpenVPN**: connect to your network, then open `http://<private-ip>:8765`
- **Tailscale / ZeroTier**: install the client on the server and on your device and open `http://<tailscale-ip>:8765` (Tailscale also offers `tailscale serve`, which adds HTTPS)

## SSH tunnel for occasional access

If you can log in to the machine with SSH, you can reach a panel that listens on `127.0.0.1` without opening any port:

```bash
ssh -L 8765:127.0.0.1:8765 user@your-server
```

Then open `http://localhost:8765` on your own computer. The traffic is encrypted by SSH.

## Windows firewall

On a Windows server, allow the port in an elevated PowerShell if other machines should reach the panel:

```powershell
New-NetFirewallRule -DisplayName "MineDash" -Direction Inbound -Protocol TCP -LocalPort 8765 -Action Allow -RemoteAddress LocalSubnet
```
