# Managing modules

## How modules are found

At every server start, MineDash scans `plugins/MineDash/modules/` for `*.jar` files, sorted by file name, and reads the file `module.json` inside each of them.

- A module that cannot be read (broken jar, invalid `module.json`, wrong API version) is **skipped**, and the log says why. The other modules are not affected.
- When two jars contain a module with the same id, the first one is used and the other is skipped with a warning.
- A module that is found for the first time is **enabled** and written to `modules.json`.
- A module that is listed as `false` in `modules.json` is loaded but not started.

Jars are read **only at server start**. To add, update or remove a module jar, stop the server, change the files, and start it again.

## The page "Manage modules"

Accounts with the permission `modules.manage` see *Manage modules* in the sidebar. Every installed module is listed with its icon, name, version, description, state and a switch.

| State | Meaning |
| --- | --- |
| `enabled` | The module is running |
| `disabled` | The module is installed but switched off |
| `failed` | The module could not start. The reason is shown below its name and written to the server log. |

### Switch a module on or off

Use the switch. The change takes effect at once, without a restart, and is remembered in `modules.json`.

- Switching **on** starts the module on the server main thread. If starting fails, the module shows `failed` with the reason. A failed module is tried again at every server start, as long as it is switched on, and the reason is logged each time.
- Switching **off** stops the module: its routes and WebSocket connections are closed and its sidebar entry and widgets disappear. Data on disk stays.
- Modules can depend on each other. A module cannot start when a module it requires is not enabled, and a module cannot be switched off while an enabled module still needs it. The message names the module in question.

## Install a module

1. Stop the server.
2. Put the jar into `plugins/MineDash/modules/`.
3. Start the server. Look for a line like this in the log:

```text
[MineDash] Enabled module Files v1.0.0
[MineDash] Found 2 module(s), 2 enabled.
```

4. Reload the panel in the browser. The module appears in the sidebar.
5. Give accounts the permissions of the module in `users.json`, see [Users and permissions](users-and-permissions.md).

## Remove a module

1. Stop the server.
2. Delete the jar from `plugins/MineDash/modules/`.
3. Start the server.

The data of the module in `plugins/MineDash/data/<module-id>/` stays. Delete the folder if you do not need it any more.

## Only install modules you trust

A module is Java code that runs inside your server process with the same rights as every other plugin. It can read and write files, open network connections and use the whole Bukkit API. The permission system of MineDash controls what *users of the panel* may do. It does not limit the module itself.

Install modules only from sources you trust, in the same way you treat plugins.

## Included modules

| Module | Id | Description |
| --- | --- | --- |
| [Console](../modules/console.md) | `console` | Live server log and command input |
| [Files](../modules/files.md) | `files` | File manager and editor |

## Module settings and data

| Where | What |
| --- | --- |
| `plugins/MineDash/data/<module-id>/` | Private folder of the module |
| `plugins/MineDash/data/<module-id>/config.json` | Settings of the module, if it has any |

Module settings are read when the module starts. After a change, switch the module off and on again on *Manage modules*, or restart the server.

## Troubleshooting

| Problem | Likely cause |
| --- | --- |
| The module is not listed | The jar is not in `plugins/MineDash/modules/`, the server was not restarted, or the log has a line `Failed to load module …` |
| `Requires API version …` | The module was built for another version of the module API |
| `Requires module 'x' to be enabled` | A dependency is missing or switched off |
| State `failed` with a Java error | The module code threw an exception while starting. The log has the full stack trace. |
| The module is enabled but not in the sidebar | It has no user interface, or the page needs a reload |
