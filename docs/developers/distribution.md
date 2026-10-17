# Release and distribution

## Before you release

- [ ] `module.json`: a good `description`, correct `authors`, a real `version`
- [ ] `apiVersion` matches the MineDash versions you tested with
- [ ] The jar contains `module.json`, your classes and `web/<frontend>`: `unzip -l your-module.jar`
- [ ] No debug logs, no source maps (unless you want them), no test data
- [ ] Tested on at least one old and one new server version if you claim to support both
- [ ] Tested with a user who has only some of the permissions
- [ ] Tested: switch off, switch on, restart
- [ ] The module does not write outside its data folder (or documents why)
- [ ] A README with the information below

## Versioning

Use [semantic versioning](https://semver.org): `MAJOR.MINOR.PATCH`.

- Patch: bug fixes
- Minor: new features, nothing breaks
- Major: changes to routes, configuration or data that need action from admins

Keep your configuration and data **backward compatible**. `loadConfig` adds new options to old files, so adding options is safe. Renaming or removing them is a breaking change.

If MineDash raises `MineDashApi.VERSION`, modules built for the old version stop loading until they are updated. Check the changelog of MineDash for API changes.

## What to write in your README

Admins should not have to read your code. Include:

1. What the module does, with a screenshot
2. Requirements: MineDash version, Minecraft versions, Java version, other modules
3. Installation: copy the jar to `plugins/MineDash/modules/`, restart
4. **Permissions** with a table (`notes.view`: …, `notes.write`: …) and which are dangerous
5. **Configuration** with every option, its default and its effect (`plugins/MineDash/data/<id>/config.json`)
6. Where the data is stored
7. Known limits
8. License and how to report problems

The Files module page of this documentation ([Files module](../modules/files.md)) is a good model.

## Building the release jar

```bash
./gradlew :modules:notes:jar            # in the MineDash repository
./gradlew jar                           # in a standalone project
```

Name the file `<id>-<version>.jar`, for example `notes-1.2.0.jar`. The file name does not matter to MineDash for anything but the order of loading, but a clear name helps admins keep track.

If you use libraries, build a fat jar. Test that it contains the libraries and that they do not clash with another plugin (relocate their packages).

## Publishing

A module is a normal file, so you can publish it wherever plugins live:

- GitHub Releases (attach the jar, add the changelog)
- Your own website
- A plugin marketplace, if its rules allow modules for another plugin

Add a **checksum** (for example `sha256sum notes-1.2.0.jar`) so people can verify their download.

## Licensing

You choose the license of your module. MineDash's license applies to MineDash itself. Libraries you bundle have their own licenses, and you must follow them.

## Updating a module (for admins)

1. Stop the server.
2. Replace the old jar in `plugins/MineDash/modules/` with the new one. **Remove the old jar**, do not leave both.
3. Read the changelog for new permissions and options. Start the server. `loadConfig` adds new options to the existing `config.json`.
4. Check the Modules page: the module should be enabled and the version updated.

The enabled/disabled state is stored by module id in `modules.json` and stays.

## Removing a module

Remove the jar and restart. The data in `plugins/MineDash/data/<id>/` stays until you delete it. Remove the module's entry from `modules.json` if you want a clean state.

## Writing documentation for your own module

If you write a documentation page for your module in the style of this book, keep this structure: short intro, features, permissions, configuration, usage, limits, troubleshooting.
