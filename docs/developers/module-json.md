# module.json

Every module jar has a file `module.json` in its root. It tells MineDash what the module is.

## Full example

```json
{
  "id": "notes",
  "name": "Notes",
  "version": "1.0.0",
  "description": "Shared notes for the team",
  "authors": ["Your Name"],
  "main": "com.example.notes.NotesModule",
  "apiVersion": 1,
  "frontend": "notes.js",
  "icon": "icon.svg",
  "widgets": [
    { "id": "count", "title": "Notes", "size": "small", "permission": "notes.view" }
  ],
  "dependencies": []
}
```

## Fields

| Field | Required | Type | Description |
| --- | --- | --- | --- |
| `id` | yes | string | Unique name of the module. Lower case letters, digits, `_` and `-`, 1 to 32 characters. Used in URLs (`/api/modules/<id>/…`), for the data folder and in `modules.json`. **Do not change it later**, or the module loses its data and its enabled/disabled state. |
| `name` | yes | string | Name shown in the panel |
| `version` | yes | string | Version of your module, shown on the Modules page. Any text, `1.2.3` by convention. |
| `main` | yes | string | Full class name of your class that implements `MineDashModule` |
| `apiVersion` | yes | number | Version of the module API you wrote against. Must be equal to the version of MineDash (currently `1`). Otherwise the module is not loaded. |
| `description` | no | string | One line for the Modules page |
| `authors` | no | list of strings | Shown on the Modules page |
| `frontend` | no | string | Path of your JavaScript file **inside `web/` in the jar**, for example `notes.js`. Without it the module has no page. |
| `icon` | no | string | Sidebar icon: inline SVG markup or the path of an SVG file in the jar. See [Icons](icons.md). |
| `widgets` | no | list | Cards for the overview page. Needs `frontend`. See [Widgets](widgets.md). |
| `dependencies` | no | list of strings | Ids of modules that must be enabled first |

Unknown fields are ignored.

### `id`

```text
good:  notes   world-backup   player_stats   a1
bad:   Notes   my module      de.example.notes   (longer than 32 characters)
```

### `frontend`

The panel loads `/api/assets/<id>/<frontend>`, which serves `web/<frontend>` from the jar. The path must be relative and must not contain `..` or `\`. Sub folders work (`js/app.js`), but one file is the rule: build a single bundle, see [Frontend](frontend.md#build-a-single-file).

### `widgets`

Each entry:

| Field | Required | Description |
| --- | --- | --- |
| `id` | yes | 1 to 32 characters (`a-z`, `0-9`, `_`, `-`), unique inside the module |
| `title` | yes | Heading of the card |
| `size` | no | `small` (default), `medium` or `large` |
| `permission` | no | Permission needed to see the card. Without it every user sees it. |

### `dependencies`

```json
"dependencies": ["files"]
```

- A module is enabled only after all of its dependencies were enabled.
- A module cannot be switched off while an enabled module depends on it.
- Dependencies are about order and availability. They do **not** give you access to the other module's classes. Talk to it through its HTTP routes or through Bukkit services.

## Version placeholders

With the Gradle setup of this documentation, write `"version": "${version}"` and the build replaces it by the project version. This only works if `processResources` expands it, see [Project setup](project-setup.md). If you do not use the expansion, write a normal version number. Remember that other `${…}` text in the file would be replaced too.

## What happens when it is wrong

If `module.json` is missing or invalid, the jar is **not listed** in the panel. The server log says:

```text
[MineDash] Failed to load module my-module-1.0.0.jar: 'id' must match [a-z0-9_-] and be 1-32 characters long
```

Messages you can get:

| Message | Meaning |
| --- | --- |
| `module.json is missing` or `is empty` | No file in the root of the jar |
| `module.json is invalid: …` | The JSON cannot be read |
| `'id' must match [a-z0-9_-] and be 1-32 characters long` | Bad `id` |
| `'name', 'version' and 'main' are required` | A required field is missing |
| `Requires API version X but this MineDash provides version Y` | `apiVersion` does not match |
| `'frontend' must be a relative path without '..'` | Bad `frontend` |
| `'widgets' require a 'frontend'` | You declared cards but no frontend file |
| `Widget ids must match …`, `Duplicate widget id`, `needs a 'title'`, `'size' must be small, medium or large` | Problems in a widget entry |

A jar that has the same `id` as one that is already loaded is skipped. Jars are read in alphabetical order of their file names.

A module that was found but whose `onEnable` fails shows the state **failed** with the error on the Modules page.
