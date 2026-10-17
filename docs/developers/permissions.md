# Permissions and security for module authors

## How permissions work in a module

Every route and every WebSocket channel gets a permission string when you register it:

```java
context.get("/notes",  "notes.view",  request -> store.all());
context.post("/notes", "notes.write",  request -> { … });
context.webSocket("live", "notes.view", handler);
context.get("/ping",   null,          request -> "pong");   // null: any logged-in user
```

MineDash checks it **before your handler runs**:

- not logged in: `401`
- logged in without the permission: `403 Missing permission: notes.write`
- a WebSocket without the permission is closed with code `4403`

Your handler only runs for allowed users.

There is no list of permissions to register. A permission exists as soon as an admin writes it into `users.json`. Document yours so admins know what to give.

## Naming

Use `<module-id>.<action>`:

| Good | Why |
| --- | --- |
| `notes.view`, `notes.write`, `notes.delete` | `notes.*` grants all of them |
| `backups.create`, `backups.restore` | Dangerous actions get their own permission |

Do not reuse names from other modules (`files.write`) for something different. Do not invent names with several levels unless you need them. `a.b.c` matches `a.*` and `a.b.*`.

## Matching

`user.hasPermission(p)` is true if the account has:

- exactly `p`,
- `*`, or
- a prefix pattern `x.*` where `p` starts with `x.`

Permissions never imply each other. If writing without viewing makes no sense, say so in your documentation, or check both in the handler:

```java
if (!request.getUser().hasPermission("notes.view")) {
    throw new ApiException(403, "Missing permission: notes.view");
}
```

## Splitting permissions well

Think of what a bad actor with each permission could do:

| Permission | Risk | Advice |
| --- | --- | --- |
| Read only | Sees data | Fine for most helpers |
| Write | Changes data | Give to trusted staff |
| Execute / restore / delete | Can break things | Separate permission |
| Anything that runs commands or writes files on the server | Same as shell access | Say so loudly in your docs |

## Checks inside the handler

A permission string is enough for most routes. When the rule depends on the data, do the extra check yourself:

```java
context.delete("/notes", "notes.write", request -> {
    Note note = store.find(id);
    boolean mine = note.author.equalsIgnoreCase(request.getUser().getUsername());
    if (!mine && !request.getUser().hasPermission("notes.moderate")) {
        throw new ApiException(403, "You can only delete your own notes");
    }
    …
});
```

## Security checklist

Your module runs inside the server, so a mistake in it is a mistake in the server.

**Input**

- [ ] Every value from the request (body, query, headers) is checked: type, length, allowed characters, range.
- [ ] File names from users are resolved against a base folder and checked not to leave it (`..`, absolute paths, symbolic links).
- [ ] Commands are never built from user text by string concatenation.
- [ ] Bodies are limited. JSON is limited by MineDash to 1 MB, for uploads you pass `maxBytes` to `getBodyStream`.
- [ ] Lists that grow (notes, logs) have a maximum size.

**Output**

- [ ] The frontend uses `textContent` or an escaping framework, never `innerHTML` with user text.
- [ ] Error messages do not contain paths, stack traces or secrets.
- [ ] Do not return data the user is not allowed to see just because the route is open. Filter by user.

**Permissions**

- [ ] Every route that reads private data or changes anything has a permission. `null` is only for harmless things.
- [ ] Dangerous actions have their own permission.
- [ ] The frontend hides what the user cannot do, but the **server** is the real check.

**Server**

- [ ] Bukkit calls from handlers go through `runSync`.
- [ ] Files you write are written atomically and only in the data folder, unless the module's purpose is different and documented.
- [ ] Changes made by users are logged with the user name.
- [ ] Secrets (tokens, passwords) are not written to the log, to the config in plain view, or sent to the browser.

**Dependencies**

- [ ] Libraries you bundle are up to date and are needed.
- [ ] You do not load code or data from the internet at runtime unless the admin asked for it.

## What the core already does for you

| Protection | By |
| --- | --- |
| Authentication and sessions | Core |
| Permission checks before your handler | Core |
| Same-origin check on `POST`/`PUT`/`DELETE` and WebSockets | Core |
| Login rate limit | Core |
| Security headers and a strict content security policy | Core |
| Request size limit for JSON bodies | Core |
| Closing sockets when a session ends | Core |
| Input validation, authorization of data, safe file access, safe commands | **You** |
