# Users and permissions (users.json)

File: `plugins/MineDash/users.json`

Accounts are loaded **when the plugin starts**. After you change the file, restart the server (or the plugin) for the change to apply. There is no page in the panel to manage accounts.

## Format

```json
{
  "users": [
    {
      "username": "admin",
      "passwordHash": "$2a$10$...",
      "permissions": ["*"]
    },
    {
      "username": "moderator",
      "password": "change-me-please",
      "permissions": ["overview.view", "console.view", "files.view"]
    }
  ]
}
```

| Field | Meaning |
| --- | --- |
| `username` | Required. Not case-sensitive for login (`Admin` and `admin` are the same account), and must be unique. |
| `password` | The password in plain text. **Use it only to set or change a password**: at the next start MineDash hashes it, stores the hash in `passwordHash`, and removes the plain text from the file. |
| `passwordHash` | The bcrypt hash that MineDash wrote. Do not edit it by hand. |
| `permissions` | List of permissions, see below. An empty list is allowed: the account can log in and sees an empty panel. |

Accounts without a username, without any password, or with a duplicate username are skipped with a warning in the log.

If both `password` and `passwordHash` are present, `password` wins and replaces the hash.

## Add an account

1. Open `users.json`.
2. Add an entry with `username`, `password` and `permissions`.
3. Restart the server.
4. Check the log for `Loaded n account(s).`
5. Open the file again: the `password` line is gone and `passwordHash` is there.

## Change a password

Add a `password` field to the account (you can leave the old `passwordHash` in place), then restart.

## Remove an account

Delete its entry and restart. Sessions that are open end at the restart.

## Lost password

Anybody with file access to the server can reset it:

1. Stop the server (or just plan a restart).
2. Add `"password": "a-new-password"` to the account in `users.json`.
3. Start the server.

If you lose **all** accounts, delete `users.json` (or make the list empty). At the next start MineDash creates a new `admin` account with a random password and prints it once in the log.

## Passwords

- Passwords are hashed with bcrypt (cost 10) before they are stored. Nobody, including the module code, can read them back.
- Login fields are limited to 128 characters.
- MineDash enforces no password rules. Use long, unique passwords, ideally from a password manager.

## Permissions

A permission is a text such as `console.execute`. An account may use a feature if one of its permissions matches.

### Matching rules

| In `users.json` | Matches |
| --- | --- |
| `console.execute` | exactly that permission |
| `console.*` | every permission that starts with `console.` |
| `*` | everything, including permissions of modules you install later |

Permissions do **not** include each other. For example, `files.write` does not imply `files.view`. List every permission an account needs.

### Permissions that exist

| Permission | Allows |
| --- | --- |
| `overview.view` | See the Overview page and its built-in cards |
| `modules.manage` | See *Manage modules* and switch modules on and off |
| `console.view` | See the live server log (Console module) |
| `console.execute` | Run commands on the server console (Console module) |
| `files.view` | Browse and read files (Files module) |
| `files.write` | Save, upload, create, rename, move, zip and unzip files (Files module) |
| `files.delete` | Delete files and folders (Files module) |

Third-party modules define their own permissions. They are listed in the documentation of the module. See the [permission reference](../reference/permissions.md) for the full table with notes.

### What an account sees

- A module appears in the sidebar for every enabled module that has a user interface. Whether the page is useful depends on the permissions, because the module's data and actions check them: without `console.view` the console page opens but receives no log.
- Overview widgets of a module are hidden from accounts without the permission the widget declares.

### Dangerous permissions

Some permissions are as powerful as a shell on the server. Give them with care.

| Permission | Why it is powerful |
| --- | --- |
| `console.execute` | Can run any server command, including `op`, `stop` and, with the right plugins, commands that run system programs |
| `files.write` | Can replace plugin jars, edit start scripts and change the whole server configuration |
| `modules.manage` | Can disable modules, for example to remove protections |
| `*` | Everything |

### Example roles

```json
{
  "users": [
    { "username": "owner",   "password": "...", "permissions": ["*"] },
    { "username": "admin",   "password": "...", "permissions": ["overview.view", "modules.manage", "console.*", "files.*"] },
    { "username": "mod",     "password": "...", "permissions": ["overview.view", "console.view"] },
    { "username": "builder", "password": "...", "permissions": ["files.view", "files.write"] }
  ]
}
```
