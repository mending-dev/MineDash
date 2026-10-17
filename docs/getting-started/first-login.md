# First login

## Log in

1. Open the panel, for example `http://localhost:8765`.
2. Enter the username `admin` and the password from the server log.
3. You land on the **Overview** page.

The default account has the permission `*`, which means everything. Create more accounts with fewer rights for other people, and give the administrator account a password you choose yourself.

## Set your own password

Passwords are changed in `users.json`:

1. Open `plugins/MineDash/users.json`.
2. In your account, remove the `passwordHash` line and add a `password` line with the new password in plain text.
3. Restart the server (or the plugin).

```json
{
  "users": [
    {
      "username": "admin",
      "password": "a-long-password-you-chose",
      "permissions": ["*"]
    }
  ]
}
```

On the next start MineDash hashes the password with bcrypt, removes the plain text and writes the file back. See [Users and permissions](../administration/users-and-permissions.md) for everything about accounts.

## What you should see

| Place | What you see |
| --- | --- |
| Sidebar | *Overview*, then a *Modules* group with one entry per enabled module (for example *Console* and *Files*), then *Manage modules* |
| Overview | Cards for players, uptime, TPS, storage, CPU, memory, server info and system info |
| Bottom of the sidebar | Your avatar, your username and the sign out button |

If something is missing:

- **No Overview cards**: the account lacks the permission `overview.view`.
- **No module entries**: no module is installed or enabled, or the account lacks the permission of the module. Check *Manage modules*.
- **No *Manage modules* entry**: the account lacks the permission `modules.manage`.

## Sessions

You stay logged in while you use the panel. A session ends

- after **60 minutes without activity** (setting `auth.sessionTimeoutMinutes`),
- after **12 hours in total**, no matter how active you are (setting `auth.maxSessionHours`),
- when you press the sign out button,
- when the server restarts. Sessions are kept in memory only.

When a session ends, the panel returns to the login page and open live connections (for example the console) are closed.

## Too many wrong passwords

After 5 failed logins from the same address, further logins from it are refused for 5 minutes with the message "Too many login attempts, try again later". Both numbers are settings, see [Configuration](../administration/configuration.md).
