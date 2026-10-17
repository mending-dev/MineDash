# Permissions reference

All permissions that exist in a default installation, and how they are matched.

## Built-in permissions

| Permission | Where | Allows |
| --- | --- | --- |
| `overview.view` | Core | See the Overview page and its cards |
| `modules.manage` | Core | Enable and disable modules |
| `console.view` | [Console](../modules/console.md) | See the live log |
| `console.execute` | [Console](../modules/console.md) | Run commands as the console |
| `files.view` | [Files](../modules/files.md) | Browse folders, read and download files |
| `files.write` | [Files](../modules/files.md) | Save, upload, create, rename, move, create and extract ZIP archives |
| `files.delete` | [Files](../modules/files.md) | Delete files and folders |

A module you install can define more. They are listed in that module's documentation.

## Matching rules

A permission in `users.json` can be:

| Form | Example | Grants |
| --- | --- | --- |
| Exact | `console.view` | exactly that permission |
| Prefix wildcard | `files.*` | every permission that starts with `files.` |
| Everything | `*` | every permission, including those of modules you install later |

Matching is case sensitive. Write permissions in lower case.

Important:

- **Permissions do not include each other.** `files.write` does not give `files.view`, and `console.execute` does not give `console.view`. Give both.
- **There are no negative permissions.** You cannot subtract from a wildcard.
- `*` is only for administrators. A new module is covered by it immediately.

## Examples

```json
{
  "users": [
    { "username": "admin",     "passwordHash": "…", "permissions": ["*"] },
    { "username": "moderator", "passwordHash": "…", "permissions": ["overview.view", "console.view"] },
    { "username": "builder",   "passwordHash": "…", "permissions": ["files.view", "files.write"] },
    { "username": "devops",    "passwordHash": "…", "permissions": ["overview.view", "modules.manage", "files.*", "console.*"] }
  ]
}
```

## Where permissions are checked

- **On the server,** for every route and WebSocket channel of a module (`context.get("/x", "permission", handler)`). This is the real protection.
- **In the panel,** to hide pages, cards and buttons that the account cannot use. This is only for convenience.

## For module developers

Name your permissions `<module-id>.<action>`, for example `notes.write`. That way a wildcard like `notes.*` works for your module. See [Permissions for developers](../developers/permissions.md).
