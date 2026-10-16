# Files module

Browse, edit, upload, download, archive and extract the files of your server from the MineDash panel.

- Module id: `files`
- Requires MineDash with module API version 1 (including the streaming additions) and Java 8 or newer
- The panel and the API are part of the module jar, nothing else has to be installed

## Features

- Folder browser with breadcrumbs, sortable columns, multi-selection and full keyboard control
- Text editor with syntax highlighting for JSON, YAML, Properties, TOML, XML and shell scripts, search and line wrapping
- Safe saving: conflict detection, atomic writes, line endings and BOM stay as they are, unsaved text is kept as a draft, and files with a draft are marked in the list
- Upload by button or drag and drop, with progress, cancel and retry
- Download of single files, folders and multi-selections (folders and multiple items are streamed as a ZIP archive)
- Create folders and files, rename, move, delete
- Create and extract ZIP archives on the server
- Protected paths, protection against leaving the managed folder, audit log

## Permissions

| Permission | Allows |
| --- | --- |
| `files.view` | Browse folders, read and download files |
| `files.write` | Save edited files, upload, create files and folders, rename, move, create and extract ZIP archives |
| `files.delete` | Delete files and folders |

- Permissions do not include each other. Give `files.view` together with `files.write`, because the panel needs `files.view` to show anything.
- Wildcards work as everywhere in MineDash: `files.*` grants all three, `*` grants everything.
- Example in `users.json`:

```json
{ "username": "moderator", "password": "change-me", "permissions": ["files.view", "files.write"] }
```

> **`files.write` is a powerful permission.** Anyone who can write files can replace plugin jars, edit start scripts and change the server configuration. Treat it like shell access to the server folder.

## Configuration

File: `plugins/MineDash/data/files/config.json`

- Created with default values on the first start. Missing options are added at every start.
- Values outside the allowed range are corrected and a warning is written to the server log.
- Changes apply after the module was disabled and enabled again (page *Manage modules*) or after a server restart.
- If the file contains invalid JSON, the module does not start. The reason is shown on the page *Manage modules*.

| Option | Default | Allowed range | Meaning |
| --- | --- | --- | --- |
| `root` | `"."` | an existing folder | Folder the explorer manages. A relative path is resolved against the folder the server was started in. Nothing above it is reachable. |
| `denyPaths` | `["plugins/MineDash/users.json"]` | any list | Paths relative to `root` that can never be listed, read, changed or archived. See below. |
| `maxUploadBytes` | `268435456` (256 MiB) | 1 KiB to 64 GiB | Largest accepted upload |
| `maxEditBytes` | `2097152` (2 MiB) | 1 KiB to 16 MiB | Largest file that can be opened in the editor, and largest text that can be saved from it |
| `maxListEntries` | `10000` | 100 to 100000 | Entries shown per folder, the rest is cut off |
| `maxUnzipBytes` | `10737418240` (10 GiB) | 1 MiB to 1 TiB | Largest total size of an archive after extraction |
| `maxZipEntries` | `100000` | 100 to 1000000 | Most entries when creating or extracting an archive |

Example:

```json
{
  "root": ".",
  "denyPaths": ["plugins/MineDash/users.json", "server.properties"],
  "maxUploadBytes": 268435456,
  "maxEditBytes": 2097152,
  "maxListEntries": 10000,
  "maxUnzipBytes": 10737418240,
  "maxZipEntries": 100000
}
```

### Protected paths

- Entries of `denyPaths` are relative to `root`. Each entry also protects everything below it.
- Names are compared without regard to case. In addition, existing files are compared by identity, so hard links and symbolic links to a protected file are protected as well.
- Protected paths are hidden in listings, cannot be read, overwritten, uploaded to, extracted over or downloaded, and are left out of ZIP archives.
- A folder that contains a protected path cannot be deleted, renamed or moved.
- The accounts file `plugins/MineDash/users.json` (it contains the password hashes) is **always** protected, whatever `root` and `denyPaths` say.
- Entries that are invalid or would block everything (an empty path) are ignored with a warning.
- Good candidates to add: `server.properties` (contains the RCON password), configuration files of other plugins that hold database passwords or API keys.
- If you change `root`, check `denyPaths`: its entries are relative to the new root.

## Security model

- **The managed folder is a boundary.** Paths with `..`, backslashes or control characters are rejected. Symbolic links are resolved, and a path whose real location is outside of `root` is refused (HTTP 403). Broken links are refused as well.
- **Windows names.** Reserved names (`CON`, `NUL`, `COM1` ...), the characters `< > : " | ? *` and names ending with a dot or a space are rejected.
- **Downloads are always attachments.** A file from the server folder is never displayed by the browser, so an uploaded HTML or SVG file cannot run inside the panel.
- **Extracting never overwrites** and never follows paths out of the target folder (see below).
- **Audit log.** Every change and every download is written to the server log with the user name, for example:

```
[MineDash] [files] admin saved plugins/Essentials/config.yml
[MineDash] [files] admin uploaded world/data.zip (1048576 bytes)
[MineDash] [files] admin deleted old-backup.zip
```

Logged actions: `saved`, `saved (forced)`, `uploaded`, `downloaded`, `downloaded as ZIP`, `created folder`, `created file`, `renamed`, `moved`, `deleted`, `created archive`, `extracted`.

## How it behaves

### Listing

- Folders come first, then files, sorted by name without regard to case.
- Hidden are: protected entries, names the panel could not send back (invalid names), links that point outside of `root` or to a protected path, and broken links.
- Symbolic links are marked in the panel. Size and type shown are those of the target.
- Files that have a saved draft show an orange pencil next to the name, and folders that contain such files show an orange dot (see *Drafts*).

### Editing and saving

- Only text can be edited: valid UTF-8 without NUL bytes, up to `maxEditBytes`. Other files are refused (HTTP 415 or 413). Download them instead.
- Line endings (LF or CRLF) and a byte order mark are kept exactly. A lone `\r` is shown as a visible special character and is not changed.
- The **version** of a file is the SHA-256 hash of its content. The editor sends the version it opened. If the file on disk has a different content when saving, the save is refused with HTTP 409 and the editor asks how to continue.
- Possible answers to a conflict: save the own text as a copy (`name (my copy).ext`, the original is not touched), reload the file from disk, overwrite the file, or, if the file was deleted, recreate it or close and discard.
- Saving writes a temporary file next to the target (`.minedash-xxxxxxxx.tmp`) and replaces the target atomically. A failed save never leaves a half written file. File permissions are kept (a script stays executable). If the target is a symbolic link, the file it points to is changed and the link stays.
- Between the version check and the replacement another process can still write to the file. The window is very small, but it exists.

### Drafts

- Unsaved text is kept as a draft in the **session storage of the browser tab**: 0.7 seconds after the last key press, at least every 5 seconds while typing, and immediately when the page is hidden or left.
- Leaving the editor in any way (other panel page, back button, reload) keeps the draft. It is restored when the file is opened again, with a notice. Saving, discarding, reloading from disk or undoing back to the original text removes it.
- **Markers in the file list.** A file with a saved draft shows an orange pencil next to its name (tooltip: *Unsaved changes are kept as a draft*). A folder that contains such files, at any depth, shows an orange dot. The markers update after every save, discard and file action. Users without `files.write` never see markers, because they have no drafts.
- **Drafts follow their file.** Renaming or moving a file or a folder in the panel moves its drafts to the new path. Deleting a file or folder in the panel removes the drafts inside. If a file is changed outside of the panel (for example deleted in a terminal), its draft stays until the tab is closed, and its folder can stay marked although the file is gone.
- A draft remembers the file version it is based on. If the file changed in the meantime, saving asks how to combine both versions.
- Limits: 400,000 characters per draft, 1,200,000 characters for all drafts of a user. Older drafts are removed first. The status line shows when a draft could not be kept.
- Drafts disappear when the tab is closed. This is intended, because configuration files can contain passwords. The browser warns before closing a tab with unsaved changes.

### Upload

- The file is streamed to a temporary file and moved into place when complete. An aborted upload leaves nothing behind.
- Existing files are only replaced after the panel asked (dialog with *Cancel*, *Skip existing*, *Replace*). If the server reports a conflict that the panel did not know about, the failed entry offers *Replace*.
- Written through symbolic links: the link stays, its target changes.
- Folders cannot be uploaded. Create a ZIP archive, upload it and use *Extract*.
- The panel checks names and the size limit before sending, uploads at most 2 files at the same time and accepts up to 1000 files at once.
- Uploads stop when you leave the Files page.

### Download

- A single file is sent with its size, so browsers show a progress bar. A file that grows while it is sent (a log) is cut off at the size it had at the start.
- A folder or several items are packed while they are sent (no temporary file, no size shown). The name is `<name>.zip` for one item and `files.zip` for several.
- Symbolic links inside folders are not followed and not included. Protected files are left out silently. Files that cannot be read are skipped and listed in `MINEDASH-SKIPPED.txt` inside the archive.
- At most 1000 items can be selected for one download.

### Rename, move, delete

- Move and delete work on several items at once. Each item has its own result, so some can succeed while others fail. The panel shows a report for failures.
- Existing files are never replaced by a move or rename.
- A folder cannot be moved into itself. Folders that would have to be copied to another drive are not moved.
- A link is moved or deleted as a link, never its target. Deleting a folder removes everything in it, links inside are removed and not followed.
- The root folder itself cannot be changed. Folders that contain protected paths cannot be changed (see above).
- Up to 1000 items per action.
- Drafts of the affected files are moved or removed together with the files (see *Drafts*).

### ZIP archives

- **Create:** the archive name must end with `.zip` and must not exist. It is written to a temporary file and renamed when complete, and never contains itself. Compression is set to fast to spare the CPU of the game server. Skipped items are listed in the panel and in `MINEDASH-SKIPPED.txt`.
- **Extract:** the whole archive is checked first, then extracted into a folder that is created if needed.
  - Nothing is overwritten. If files exist already, HTTP 409 names the first of them and nothing is extracted.
  - Entry names are validated like all paths. Backslashes count as separators, leading slashes are dropped, `..` is refused.
  - Protection against ZIP bombs: the announced sizes are checked up front, and the bytes that are really read are counted while extracting (`maxUnzipBytes`). The free disk space is checked as well (HTTP 507).
  - If anything fails halfway, everything created so far is removed again.
  - Archives are read as UTF-8, and if that fails as ISO-8859-1. Password protected archives are not supported.
  - Symbolic links in archives are not created. File permissions and owners are not restored, a script has to be made executable again.
- **Load:** creating, extracting and ZIP downloads share a limit of 2 running operations. Further requests receive HTTP 429 and can be repeated shortly after.

## Limits at a glance

| Limit | Value | Where |
| --- | --- | --- |
| Upload size | `maxUploadBytes` (256 MiB) | config |
| Editable file size | `maxEditBytes` (2 MiB) | config |
| Entries per folder | `maxListEntries` (10000) | config |
| Unpacked archive size | `maxUnzipBytes` (10 GiB) | config |
| Entries per archive | `maxZipEntries` (100000) | config |
| Items per move, delete, ZIP or download | 1000 | fixed |
| Path length / depth / name length | 4096 / 64 segments / 255 characters | fixed |
| Parallel archive operations | 2 | fixed |
| Parallel uploads / files per upload | 2 / 1000 | panel |
| Download link length | 7000 characters | panel |
| Rows shown at once | 500, more on request | panel |
| Draft size | 400,000 characters (1,200,000 per user) | panel |

## HTTP API

All routes are below `/api/modules/files`. They need a logged in session, and requests that change something must come from the panel's own origin (this is enforced by MineDash). `path` values are relative to `root` and separated by `/`. The empty string is the root folder.

| Method and route | Permission | Request | Answer |
| --- | --- | --- | --- |
| `GET /info` | `files.view` | | `{maxUploadBytes, maxEditBytes, maxListEntries}` |
| `GET /list?path=` | `files.view` | | `{path, entries: [{name, directory, link, size, modified}], truncated}` |
| `GET /content?path=` | `files.view` | | `{path, content, version, size, modified}` |
| `PUT /content?path=&version=` | `files.write` | body: the new text (`text/plain`) | `{version, size, modified}` |
| `PUT /content?path=&force=true` | `files.write` | body: the new text | like above. Skips the version check, overwrites or creates the file (its folder must exist). |
| `GET /download?path=&path=...` | `files.view` | one or more `path` parameters | file, or ZIP stream (`application/zip`) |
| `PUT /upload?path=&overwrite=` | `files.write` | body: the raw file content | `{size}` |
| `POST /folder` | `files.write` | `{path}` | 204 |
| `POST /file` | `files.write` | `{path}` | 204 (creates an empty file) |
| `POST /rename` | `files.write` | `{path, name}` | 204 |
| `POST /move` | `files.write` | `{paths: [], destination}` | `{done: [], failed: [{path, error}]}` |
| `POST /delete` | `files.delete` | `{paths: []}` | `{done: [], failed: [{path, error}]}` |
| `POST /zip` | `files.write` | `{paths: [], target}` | `{entries, size, skipped: [], skippedTotal}` |
| `POST /unzip` | `files.write` | `{path, destination}` | `{entries, bytes}` |

`modified` is in milliseconds since 1970. `version` is the SHA-256 hash of the content as lower case hex.

Status codes: `400` invalid request, path or archive. `403` missing permission, path outside of the managed folder, protected path, or the operating system denied access. `404` not found. `409` exists already, or the file changed or no longer exists. `413` too large. `415` not valid UTF-8 text or binary. `429` too many archive operations. `500` unexpected, see the server log. `507` not enough disk space.

## Using the panel

### Keyboard

| Key | In the file list |
| --- | --- |
| Arrow up / down, Home, End | Move the cursor |
| Shift + arrow keys | Extend the selection |
| Space | Select or deselect the row |
| Enter, double click | Open a folder or a file |
| Ctrl/Cmd + A | Select all |
| Esc | Clear the selection |
| F2 | Rename (on a MacBook: Fn + F2) |
| Delete, Backspace or Cmd/Ctrl + Backspace with a selection | Delete (always asks first) |
| Backspace without a selection, Alt + Up, Cmd + Up | One folder up |

| Key | In the editor |
| --- | --- |
| Ctrl/Cmd + S | Save |
| Ctrl/Cmd + F | Find |
| Tab | Indent by 2 spaces |
| Ctrl/Cmd + Z, Shift + Ctrl/Cmd + Z | Undo, redo |

The shortcuts of the list do not work while typing in a field, while a dialog is open, or in the editor.

### Markers in the file list

| Marker | Meaning |
| --- | --- |
| Orange pencil next to a file name | Unsaved changes to this file are kept as a draft. Open the file to restore them. |
| Orange dot next to a folder name | The folder contains at least one file with a draft. |
| Link symbol on the icon | The entry is a symbolic link. |

### Navigation

- The back and forward buttons of the browser move between folders and into and out of the editor.
- The last folder is remembered per browser tab. After a reload the editor opens again with the same file.
- Syntax highlighting is chosen by file extension: `.json`, `.mcmeta`, `.yml`, `.yaml`, `.properties`, `.toml`, `.xml`, `.sh`, `.bash`. Everything else is shown as plain text.

### Data stored in the browser

| Key | Storage | Content |
| --- | --- | --- |
| `minedash.files.sort` | local | sort column and direction |
| `minedash.files.wrap` | local | line wrapping in the editor |
| `minedash.files.path.<user>` | session | last folder |
| `minedash.files.draft.v1.<user>/<path>` | session | unsaved text of a file |

## Troubleshooting

| Message | Cause and fix |
| --- | --- |
| `config.json: 'root' is not a folder` | The folder in `root` does not exist. Fix the path and enable the module again. |
| `Access to this path is not allowed` | The path is protected (`denyPaths` or MineDash's own `users.json`). |
| `Path is outside of the managed folder` | The path or a symbolic link in it leads out of `root`. |
| `The file was changed by someone else...` | The file changed since it was opened. Use the dialog of the editor. |
| `The file is too large to edit` | The file is bigger than `maxEditBytes`. Download it, or raise the limit. |
| `Binary files cannot be edited` / `not valid UTF-8 text` | The editor only handles UTF-8 text. |
| `Too large (the limit is ...)` when uploading | The file is bigger than `maxUploadBytes`. |
| An upload fails with `The upload failed or was rejected by the server` | The connection broke, or a limit rejected the data before it was read completely. Check the limits and a reverse proxy in front of the server (for example `client_max_body_size` in nginx). |
| `These already exist: ...` when extracting | Extracting never overwrites. Delete or rename those items, or extract into a new folder. |
| `The archive is too large once extracted` | The archive exceeds `maxUnzipBytes`. Raise it if the archive is trusted. |
| `Too many archive operations are running` | 2 archive jobs are running. Try again in a moment. |
| `The folder contains protected files` | The folder holds a protected path, for example `plugins/MineDash`. Change single files inside instead. |
| `The operating system denied access` | The server process has no rights for this file or folder. Fix the permissions on the host. |

## Known limitations

- No folder upload, no search, no image preview.
- Permissions apply to the whole managed folder, not per folder.
- Extracting does not restore file permissions, so scripts lose the executable bit.
- Folders cannot be moved between drives.
- Editing is limited to UTF-8 text.
- Drafts are lost when the tab is closed and are not kept for texts over 400,000 characters.
- Drafts of files that were changed outside of the panel are not cleaned up until the tab is closed, and a folder can stay marked because of them.
- A running upload is cancelled when you leave the Files page.