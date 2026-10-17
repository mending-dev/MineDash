# Updating

## Update the plugin

1. Stop the server.
2. Replace the old `MineDash-<version>.jar` in `plugins/` by the new one. Make sure only one MineDash jar remains.
3. Start the server.

Your settings, accounts and module data stay in `plugins/MineDash/` and are kept. New options are added to `config.json` automatically at the start. Existing values are not changed.

## Update a module

1. Stop the server.
2. Replace the module jar in `plugins/MineDash/modules/` by the new one. Do not keep two versions of the same module there: when two jars have the same module id, the first one (in alphabetical order of the file name) is loaded and the other one is skipped with a warning in the log.
3. Start the server.

A module that was switched off stays switched off after an update. The state is stored in `plugins/MineDash/modules.json`.

## Module API version

Modules declare the version of the MineDash module API they were written for (`apiVersion` in their `module.json`). The version is increased only for breaking changes. A module with a different API version is not loaded, and the log says why:

```text
[MineDash] Failed to load module example.jar: Requires API version 2 but this MineDash provides version 1
```

If that happens, update either MineDash or the module so that the versions match.

## Back up before you update

Copy `plugins/MineDash/` to a safe place. The important files are `users.json` and, if you changed it, `config.json`. Module data lives in `plugins/MineDash/data/<module-id>/`.

## Roll back

Stop the server, put the old jars back, and start the server. Newer versions only add options, so older versions ignore them.
