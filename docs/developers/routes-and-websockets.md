# Routes and WebSockets

## HTTP routes

```java
context.get("/items", "mymod.view", request -> store.all());
context.post("/items", "mymod.write", request -> { … });
context.put("/items", "mymod.write", request -> { … });
context.delete("/items", "mymod.write", request -> { … });
```

All four are shortcuts for `context.route(HttpMethod, path, permission, handler)`.

A route has:

| Part | Rules |
| --- | --- |
| **Path** | Starts with `/`. A trailing `/` is removed. The route is available at `/api/modules/<id><path>`. |
| **Method** | `GET`, `POST`, `PUT` or `DELETE` |
| **Permission** | A string, or `null` for "any logged-in user" |
| **Handler** | A `RouteHandler`: receives an `ApiRequest`, returns the answer |

Rules:

- Paths match **exactly**. `/items` does not match `/items/3`. There are no path parameters and no wildcards. Send the id as a query parameter (`/items?id=3`) or in the body.
- Registering the same method and path twice throws `IllegalStateException`.
- A path that does not exist answers `404`. A path that exists but where the user lacks the permission answers `403` with the text `Missing permission: <permission>`.
- Nothing is registered before `onEnable`, and nothing can be registered after the module was switched off (`IllegalStateException`).

### Reading the request

```java
context.post("/items", "mymod.write", request -> {
    ApiUser user = request.getUser();          // who is calling
    String id = request.getQueryParam("id");   // ?id=…, null if missing
    List<String> tags = request.getQueryParams("tag"); // ?tag=a&tag=b
    String agent = request.getHeader("User-Agent");
    MyBody body = request.getBodyAs(MyBody.class);     // JSON body
    return null;
});
```

| Method | Returns |
| --- | --- |
| `getUser()` | `ApiUser` with `getUsername()` and `hasPermission(String)` |
| `getPath()` | The path relative to the module, e.g. `/items` |
| `getQueryParam(name)` | First value, or `null` |
| `getQueryParams(name)` | All values in order (empty list if none) |
| `getHeader(name)` | Header value or `null` |
| `getBody()` | Body as text, at most 1 MB |
| `getBodyAs(Type.class)` | JSON body parsed to your class. `400` if missing or invalid. |
| `getBodyStream(maxBytes)` | The raw body for uploads, see below |

Body classes for `getBodyAs` are plain classes with fields:

```java
private static final class NewItem {
    String text;
}
```

Gson leaves fields it does not find in the JSON as `null`/`0`, so **validate**:

```java
NewItem body = request.getBodyAs(NewItem.class);
String text = body.text == null ? "" : body.text.trim();
if (text.isEmpty() || text.length() > 500) {
    throw new ApiException(400, "A note needs 1 to 500 characters");
}
```

JSON bodies over 1 MB are rejected with `413` before your handler runs.

### Query parameters and paths

Always treat them as untrusted. If a parameter is a file name, make sure it cannot leave your folder (`..`, absolute paths), see the Files module's `PathGuard` for a careful example.

### Answering

```java
return Collections.singletonMap("count", 3);   // {"count":3}
return list;                                   // [ … ]
return "text";                                 // "text" (a JSON string)
return null;                                   // 204, no body
```

For answers with a different status use an exception: `throw new ApiException(404, "Not found")`.

### Files and large answers: `StreamResponse`

To send a file or generate data on the fly without holding it in memory:

```java
context.get("/download", "mymod.view", request -> {
    Path file = resolveSafely(request.getQueryParam("name"));
    return StreamResponse.of(Files.newInputStream(file))
            .contentType("application/octet-stream")
            .attachment(file.getFileName().toString())
            .length(Files.size(file));
});
```

- `StreamResponse.of(InputStream)`: the stream is sent and then closed.
- `StreamResponse.of(writer)`: your `Writer` writes to the output while it is sent (for example a ZIP archive). It must not close the stream. Set `length(…)` only if the number is exact.
- `.attachment(name)` makes the browser save the file instead of showing it.
- In the frontend, link to such a route with `ctx.url("/download?name=…")`. `ctx.http.get` expects JSON and is not meant for files.

### Uploads: `getBodyStream`

```java
context.put("/upload", "mymod.write", request -> {
    Path target = context.getDataFolder().resolve("upload.bin");
    try (InputStream in = request.getBodyStream(10L * 1024 * 1024)) {   // at most 10 MiB
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }
    return null;
});
```

- `getBodyStream(maxBytes)` throws `ApiException(413)` as soon as the limit is exceeded, immediately if the `Content-Length` header is already too large.
- Consume the stream inside the handler.
- Write to a temporary file and move it into place, so a cancelled upload never leaves a half file.
- The frontend sends the body with `ctx.http.upload(path, blob, { onProgress, signal })`, which uses `PUT`.
- Remember the limit of the reverse proxy for body size, see [Reverse proxy basics](../deployment/reverse-proxy.md).

## WebSocket channels

Channels push data from the server to the browser, and take small messages back.

```java
context.webSocket("live", "mymod.view", new WebSocketHandler() {
    @Override
    public void onConnect(WebSocketConnection connection) {
        subscribers.add(connection);
    }

    @Override
    public void onMessage(WebSocketConnection connection, String message) {
        if ("ping".equals(message.trim())) {
            connection.send("{\"type\":\"pong\"}");
        }
    }

    @Override
    public void onClose(WebSocketConnection connection) {
        subscribers.remove(connection);
    }
});
```

| Part | Rules |
| --- | --- |
| **Channel name** | 1 to 32 characters: letters, digits, `_`, `-`. Registering a name twice throws `IllegalStateException`. |
| **Permission** | Checked when the connection opens. A user without it is closed with code `4403`. |
| **URL** | `/api/ws?module=<id>&channel=<name>` (the panel builds it for you with `ctx.connect`) |
| **Messages** | Text only |

`WebSocketConnection`:

| Method | |
| --- | --- |
| `getId()` | A unique id of the connection |
| `getUser()` | The logged-in user |
| `isOpen()` | Whether it is still open |
| `send(String)` | Sends text. Thread-safe. Does nothing if the connection is closed. |
| `close()` | Closes it |

### Things to know

- **The panel sends the text `ping` every 20 seconds** on every channel it opened. Answer it (as above) or ignore it, but do not treat it as data. It keeps proxies from closing idle connections.
- Every connection is tied to a session. When the session ends (logout, timeout) MineDash closes the connection with code `4401`, within about 10 seconds.
- Each account can have at most **20** open connections across all channels. More are closed with `4429`.
- When your module is switched off, MineDash closes all its connections **without calling `onClose`**. Clear your own subscriber list in `onDisable`.
- Handlers run on web server threads. `send` is safe to call from any thread, so you can broadcast from a Bukkit event or a scheduled task.
- Messages that you send should be JSON with a `type` field. This makes it easy to add message kinds later.
- When someone connects, send the **current state** first (the Console sends its history), or let the frontend load it over HTTP when the channel opens. Otherwise users miss what happened before they connected.

### Broadcasting safely

```java
private final Set<WebSocketConnection> subscribers = ConcurrentHashMap.newKeySet();

private void broadcast(String type, Object payload) {
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("type", type);
    message.put("data", payload);
    String json = context.toJson(message);
    for (WebSocketConnection connection : subscribers) {
        connection.send(json);
    }
}
```

A `ConcurrentHashMap`-based set lets you add and remove connections while another thread is broadcasting.

## Permissions on routes and channels

The permission is checked by MineDash **before** your code. Use `null` only for things every logged-in user may do. Name permissions `<module-id>.<action>`. More: [Permissions](permissions.md).

If an action needs a second check, for example "may only change own entries", use `request.getUser().hasPermission(...)` or compare names inside the handler.
