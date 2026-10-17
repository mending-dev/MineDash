# Folder layout

Everything MineDash stores is in `plugins/MineDash/`:

```text
plugins/MineDash/
├── config.json          settings of the panel (web server, sessions, avatars)
├── users.json           accounts and their permissions
├── modules.json         which modules are switched on or off
├── modules/             module jars, one file per module
│   ├── console-1.0.0.jar
│   └── files-1.0.0.jar
└── data/                one private folder per module, created automatically
    ├── console/
    └── files/
        └── config.json  settings of the Files module
```

| Path | Written by | Notes |
| --- | --- | --- |
| `config.json` | You | Created with defaults on the first start. New options are added at every start. Read at start only. |
| `users.json` | You and MineDash | Plain text passwords are replaced by hashes at the next start. Read at start only. |
| `modules.json` | MineDash | Changed by the buttons on *Manage modules*. You normally never edit it. |
| `modules/` | You | Put module jars here. New jars are detected at server start. |
| `data/<module-id>/` | The module | Private to the module. Many modules keep a `config.json` here. |

## modules.json

```json
{
  "modules": {
    "console": true,
    "files": false
  }
}
```

A module that is not listed yet is enabled and then added to the file. `false` keeps a module switched off across restarts. Use the page *Manage modules* instead of editing the file by hand.

## What to back up

- `users.json` – your accounts
- `config.json` – your settings
- `modules.json` – which modules are on
- `data/` – everything the modules saved

The jars do not need a backup, they can be downloaded again.

## File permissions

`users.json` contains password hashes, and module configs may contain secrets. On a shared machine, make `plugins/MineDash/` readable only for the user that runs the server:

```bash
chmod -R go-rwx plugins/MineDash
```
