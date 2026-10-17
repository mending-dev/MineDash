# Backend API

The Java API is in the package `dev.mending.minedash.api`. This page explains the concepts. Every method is listed in the [API reference](api-reference.md).

## The module class

```java
public class MyModule implements MineDashModule {

    @Override
    public void onEnable(ModuleContext context) throws Exception {
        // register routes and channels, load config, start tasks
    }

    @Override
    public void onDisable() {
        // stop tasks, unregister listeners, release resources
    }
}
```

- MineDash creates an instance with the public no-argument constructor **every time the module is enabled**.
- `onEnable` and `onDisable` run on the **server main thread**.
- You may throw any exception from `onEnable`. The module then shows as *failed* with the message, and everything you registered with the context so far is removed. **`onDisable` is not called in that case**, so release things you created before the exception yourself (for example with a `try`/`catch` that cleans up and rethrows).
- A module can be switched off and on again while the server runs: `onDisable`, and later a fresh instance and `onEnable`. Do not keep state in `static` fields, it survives the new instance.
- You do not need to unregister routes and channels in `onDisable`. MineDash drops them and closes the WebSocket connections of the module. Do clean up what MineDash does not know about: Bukkit tasks, listeners, files, threads, appenders.

## The context

`ModuleContext` is your handle to MineDash. You get it in `onEnable`. Keep it in a field if you need it later.

| Method | Purpose |
| --- | --- |
| `getInfo()` | The parsed `module.json` |
| `getLogger()` | A `java.util.logging.Logger` that prefixes messages with the module id |
| `getDataFolder()` | `plugins/MineDash/data/<id>/`, created for you |
| `getPlugin()` | The MineDash plugin, as owner for Bukkit tasks and listeners |
| `get`, `post`, `put`, `delete`, `route` | Register HTTP routes, see [Routes and WebSockets](routes-and-websockets.md) |
| `webSocket` | Register a live channel |
| `runSync`, `runAsync` | Run a task on the main thread or on a separate thread |
| `toJson`, `fromJson` | JSON with the Gson of MineDash |
| `loadConfig` | Read `config.json` of the module into your own class |

## Configuration

`loadConfig` reads `plugins/MineDash/data/<id>/config.json` into an object of your class. Defaults are the values you set in the class.

```java
public class NotesConfig {
    // Not final: Gson fills the fields
    public int maxLength = 500;
    public int maxNotes = 200;
}
```

```java
NotesConfig config = context.loadConfig(NotesConfig.class);
```

How it behaves:

- **File missing:** it is created with the default values.
- **File present:** it is read, and then **written back**, so new options you add in a later version appear in the file of existing servers. The comments or formatting an admin added are lost.
- **Invalid JSON:** throws `IOException`. Let it propagate, the module shows as failed and the admin sees why in the log.
- The class needs a no-argument constructor (any visibility).
- Read the config once in `onEnable`. There is no automatic reload, an admin restarts the server after editing.
- Validate values yourself (negative numbers, zero) before you use them.

## Data

Keep your files in `context.getDataFolder()`:

```java
Path file = context.getDataFolder().resolve("notes.json");
```

Write files atomically (write to a temporary file, then move it), see `NoteStore` in the [Notes module](example-notes-module.md#the-store).

## JSON

Use `context.toJson(object)` and `context.fromJson(json, Type.class)`. MineDash uses Gson with HTML escaping off.

- Public fields and package-private fields are serialized by field name. You need no annotations.
- `fromJson` throws `IllegalArgumentException` for invalid JSON.
- The Gson library itself is relocated inside MineDash and **not** part of the API. Do not import `com.google.gson` in a module unless you bundle your own copy.

Route handlers can return any object. It is serialized with the same Gson. Maps, lists, strings and numbers work (a plain string becomes a JSON string).

## Threads

| Code | Thread |
| --- | --- |
| `onEnable`, `onDisable` | Server main thread |
| Route handlers | A web server (Jetty) thread |
| `WebSocketHandler` methods | A web server thread |
| `runSync(task)` | Server main thread, later (next tick) |
| `runAsync(task)` | A Bukkit async thread |

**Bukkit is mostly not thread-safe.** Do not call Bukkit methods that read or change the world, players, inventories or commands from a route handler. Hand the work to the main thread:

```java
context.post("/kick", "players.kick", request -> {
    final String name = request.getQueryParam("name");
    context.runSync(() -> {
        Player player = Bukkit.getPlayerExact(name);
        if (player != null) player.kickPlayer("Kicked from the panel");
    });
    return null;
});
```

`runSync` returns immediately. If the answer depends on a result from the main thread, collect it with a `CompletableFuture`:

```java
context.get("/online", "players.view", request -> {
    CompletableFuture<List<String>> names = new CompletableFuture<>();
    context.runSync(() -> {
        List<String> list = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) list.add(player.getName());
        names.complete(list);
    });
    return names.get(5, TimeUnit.SECONDS);
});
```

Always use a timeout when you wait. A handler that waits forever blocks a web server thread. If the future times out, the exception makes the request answer `500` and the details are written to the log.

Your own data structures are accessed by several threads at once (two requests at the same time, plus the main thread). Use `synchronized`, concurrent collections or immutable objects.

## Errors

| You do | The client gets |
| --- | --- |
| Return an object | `200` with JSON |
| Return `null` | `204` with no body |
| `throw new ApiException(400, "Text")` | That status, `Text` as the body |
| Throw any other exception | `500` with an empty body. The stack trace is written to the log with the module id. |

Use `ApiException` for mistakes of the caller (`400` bad input, `404` not found, `409` conflict, `413` too large). The text is shown to the user in the panel, so write it for people and do not include internals.

> **Do not answer `401`.** The panel treats every `401` as "the session ended" and returns to the login page. For a forbidden action use `403`.

## Logging

```java
context.getLogger().info("Something happened");
context.getLogger().log(Level.WARNING, "Could not save", exception);
```

Log changes made by users (who did what) the way the Console and Files modules do, for example `user.getUsername() + " deleted " + path`. This gives admins an audit trail in `latest.log`.

## Scheduled and background work

Use the Bukkit scheduler with the MineDash plugin as owner:

```java
private BukkitTask task;

@Override
public void onEnable(ModuleContext context) {
    task = Bukkit.getScheduler().runTaskTimer(context.getPlugin(), this::tick, 20L, 20L);
}

@Override
public void onDisable() {
    if (task != null) task.cancel();
}
```

Tasks that you forget to cancel keep running after the module is switched off, and they keep the old class loader alive.

## Listening to Bukkit events

```java
Listener listener = new MyListener();
Bukkit.getPluginManager().registerEvents(listener, context.getPlugin());
// in onDisable:
HandlerList.unregisterAll(listener);
```

## Depending on another module

Add its id to `dependencies` in `module.json`. MineDash makes sure it starts first. Talk to it through its HTTP routes from your frontend, or through a shared Bukkit service. Modules do not share classes.
