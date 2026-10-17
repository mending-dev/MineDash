# API reference

All types of the module API, in the package `dev.mending.minedash.api`. The source below is the real source of API version 1, including its comments. Concepts and examples: [Backend API](backend-api.md) and [Routes and WebSockets](routes-and-websockets.md).

| Type | Purpose |
| --- | --- |
| [`MineDashApi`](#minedashapi) | Holds the version of the module API. |
| [`MineDashModule`](#minedashmodule) | Entry point of a module. Implement it and name the class in `main` of `module.json`. |
| [`ModuleContext`](#modulecontext) | Everything a module can do. Passed to `onEnable`. |
| [`ModuleInfo`](#moduleinfo) | The parsed `module.json`. Read it with `context.getInfo()`. |
| [`WidgetInfo`](#widgetinfo) | One entry of `widgets` in `module.json`. |
| [`HttpMethod`](#httpmethod) | The methods a route can have. |
| [`RouteHandler`](#routehandler) | Functional interface for route handlers. |
| [`ApiRequest`](#apirequest) | The request passed to a route handler. |
| [`ApiUser`](#apiuser) | The logged-in user. |
| [`ApiException`](#apiexception) | Throw it to answer with a specific status. |
| [`StreamResponse`](#streamresponse) | Return value for files and large content. |
| [`WebSocketHandler`](#websockethandler) | Callbacks of a WebSocket channel. |
| [`WebSocketConnection`](#websocketconnection) | One open WebSocket connection. |

The API has no dependencies except the Bukkit `Plugin` type (in `ModuleContext.getPlugin()`). Compile against it with `compileOnly`, never bundle it: the classes are provided by MineDash at runtime.

## MineDashApi

Holds the version of the module API.

```java
package dev.mending.minedash.api;

public final class MineDashApi {

    /** Increased on every breaking API change. Modules must declare the same value. */
    public static final int VERSION = 1;

    private MineDashApi() {
    }
}
```

## MineDashModule

Entry point of a module. Implement it and name the class in `main` of `module.json`.

```java
package dev.mending.minedash.api;

/** Entry point of a module. The class is referenced by "main" in module.json. */
public interface MineDashModule {

    /** Called on the server main thread. Register routes and channels here. */
    void onEnable(ModuleContext context) throws Exception;

    /** Called on the server main thread. Stop tasks and release resources here. */
    default void onDisable() {
    }
}
```

## ModuleContext

Everything a module can do. Passed to `onEnable`.

```java
package dev.mending.minedash.api;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

public interface ModuleContext {

    ModuleInfo getInfo();

    /** Logger that prefixes every message with the module id. */
    Logger getLogger();

    /** Private data folder of this module, created automatically. */
    Path getDataFolder();

    /** The MineDash plugin. Use it as owner for Bukkit tasks and listeners. */
    Plugin getPlugin();

    /**
     * Registers a REST route below /api/modules/{id}.
     * Paths are exact matches, e.g. "/command".
     *
     * @param permission required permission, or null if any logged-in user may call it
     */
    void route(HttpMethod method, String path, String permission, RouteHandler handler);

    default void get(String path, String permission, RouteHandler handler) {
        route(HttpMethod.GET, path, permission, handler);
    }

    default void post(String path, String permission, RouteHandler handler) {
        route(HttpMethod.POST, path, permission, handler);
    }

    default void put(String path, String permission, RouteHandler handler) {
        route(HttpMethod.PUT, path, permission, handler);
    }

    default void delete(String path, String permission, RouteHandler handler) {
        route(HttpMethod.DELETE, path, permission, handler);
    }

    /**
     * Registers a WebSocket channel, reachable at /api/ws?module={id}&amp;channel={name}.
     *
     * @param permission required permission, or null if any logged-in user may connect
     */
    void webSocket(String channel, String permission, WebSocketHandler handler);

    /** Runs a task on the server main thread. HTTP and WebSocket handlers do NOT run there. */
    void runSync(Runnable task);

    /** Runs a task on a separate thread. */
    void runAsync(Runnable task);

    /** Serializes an object to JSON. */
    String toJson(Object value);

    /** Parses JSON. Throws IllegalArgumentException if the JSON is invalid. */
    <T> T fromJson(String json, Class<T> type);

    /**
     * Loads config.json from the data folder into an instance of the given class.
     * The file is created with the default values if it is missing, and new options are added
     * to existing files. The class needs a no-argument constructor (any visibility).
     * Throws IOException if the file contains invalid JSON.
     */
    <T> T loadConfig(Class<T> type) throws IOException;
}
```

## ModuleInfo

The parsed `module.json`. Read it with `context.getInfo()`.

```java
package dev.mending.minedash.api;

import java.util.ArrayList;
import java.util.List;

/** Content of module.json. Filled by Gson, so the fields must stay non-final. */
public class ModuleInfo {

    private String id;
    private String name;
    private String version;
    private String description;
    private String main;
    private int apiVersion;
    private String frontend;
    private String icon;
    private List<String> authors = new ArrayList<>();
    private List<String> dependencies = new ArrayList<>();
    private List<WidgetInfo> widgets = new ArrayList<>();

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getDescription() {
        return description == null ? "" : description;
    }

    public String getMain() {
        return main;
    }

    public int getApiVersion() {
        return apiVersion;
    }

    /** Path of the frontend entry file inside the "web" folder of the module jar, or null. */
    public String getFrontend() {
        return frontend == null || frontend.trim().isEmpty() ? null : frontend.trim();
    }

    /**
     * Icon of the module: the path of an SVG file inside the module jar (e.g. "icon.svg"), or the SVG
     * markup itself, starting with "&lt;svg". Null if the module sets none. The core then looks for
     * "icon.svg" at the root of the jar.
     */
    public String getIcon() {
        return icon == null || icon.trim().isEmpty() ? null : icon.trim();
    }

    public List<String> getAuthors() {
        return authors == null ? new ArrayList<String>() : authors;
    }

    /** Ids of modules that must be enabled before this one. */
    public List<String> getDependencies() {
        return dependencies == null ? new ArrayList<String>() : dependencies;
    }

    /** Overview widgets of this module. Requires a frontend. */
    public List<WidgetInfo> getWidgets() {
        return widgets == null ? new ArrayList<WidgetInfo>() : widgets;
    }
}
```

## WidgetInfo

One entry of `widgets` in `module.json`.

```java
package dev.mending.minedash.api;

/** A dashboard widget declared in module.json. Its UI is mounted by the module frontend. */
public class WidgetInfo {

    private String id;
    private String title;
    private String size;
    private String permission;

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    /** "small", "medium" or "large". Defaults to "small". */
    public String getSize() {
        return size == null || size.trim().isEmpty() ? "small" : size.trim();
    }

    /** Permission needed to see the widget, or null if any user may see it. */
    public String getPermission() {
        return permission == null || permission.trim().isEmpty() ? null : permission.trim();
    }
}
```

## HttpMethod

The methods a route can have.

```java
package dev.mending.minedash.api;

public enum HttpMethod {
    GET, POST, PUT, DELETE
}
```

## RouteHandler

Functional interface for route handlers.

```java
package dev.mending.minedash.api;

@FunctionalInterface
public interface RouteHandler {

    /**
     * Returns the response body, which is serialized to JSON.
     * Returning null answers with 204 No Content.
     */
    Object handle(ApiRequest request) throws Exception;
}
```

## ApiRequest

The request passed to a route handler.

```java
package dev.mending.minedash.api;

import java.io.InputStream;
import java.util.List;

public interface ApiRequest {

    ApiUser getUser();

    /** Path relative to the module, e.g. "/command". */
    String getPath();

    /** Returns null if the parameter is missing. */
    String getQueryParam(String name);

    /** All values of a query parameter in order. Empty if the parameter is missing. */
    List<String> getQueryParams(String name);

    /** Returns null if the header is missing. */
    String getHeader(String name);

    /** The body as text. Limited to 1 MB, use getBodyStream for larger payloads. */
    String getBody();

    /** Parses the JSON body. Throws ApiException with status 400 if it is missing or invalid. */
    <T> T getBodyAs(Class<T> type);

    /**
     * Streams the raw request body, for uploads and large payloads.
     * Throws ApiException with status 413 as soon as the body is larger than maxBytes,
     * either immediately (Content-Length header) or while it is read.
     * The stream must be consumed inside the handler.
     */
    InputStream getBodyStream(long maxBytes);
}
```

## ApiUser

The logged-in user.

```java
package dev.mending.minedash.api;

public interface ApiUser {

    String getUsername();

    /** Supports exact match, "*" and prefix wildcards like "console.*". */
    boolean hasPermission(String permission);
}
```

## ApiException

Throw it to answer with a specific status.

```java
package dev.mending.minedash.api;

/** Throw from a route handler to answer with a specific HTTP status. */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
```

## StreamResponse

Return value for files and large content.

```java
package dev.mending.minedash.api;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Return value for routes that send files or other large content.
 * The body is streamed, so it never has to fit into memory.
 */
public final class StreamResponse implements Closeable {

    /** Writes the body directly to the response, e.g. a ZIP archive that is built on the fly. */
    @FunctionalInterface
    public interface Writer {

        /** The writer must not close the stream. */
        void write(OutputStream out) throws IOException;
    }

    private final InputStream stream;
    private final Writer writer;
    private String contentType = "application/octet-stream";
    private String fileName;
    private long length = -1;

    private StreamResponse(InputStream stream, Writer writer) {
        this.stream = stream;
        this.writer = writer;
    }

    /** Streams the given input. It is closed after the response was sent. */
    public static StreamResponse of(InputStream stream) {
        if (stream == null) {
            throw new IllegalArgumentException("stream must not be null");
        }
        return new StreamResponse(stream, null);
    }

    /** Lets the writer produce the body while it is sent. */
    public static StreamResponse of(Writer writer) {
        if (writer == null) {
            throw new IllegalArgumentException("writer must not be null");
        }
        return new StreamResponse(null, writer);
    }

    public StreamResponse contentType(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()
                || contentType.indexOf('\r') >= 0 || contentType.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Invalid content type");
        }
        this.contentType = contentType.trim();
        return this;
    }

    /** The browser saves the response as a file with this name instead of showing it. */
    public StreamResponse attachment(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("fileName must not be empty");
        }
        this.fileName = fileName;
        return this;
    }

    /**
     * Size in bytes if known. Lets browsers show a progress bar.
     * With a stream, at most this many bytes are sent. With a writer the value must be exact.
     */
    public StreamResponse length(long length) {
        this.length = length < 0 ? -1 : length;
        return this;
    }

    public InputStream getStream() {
        return stream;
    }

    public Writer getWriter() {
        return writer;
    }

    public String getContentType() {
        return contentType;
    }

    /** Null if the response is not an attachment. */
    public String getFileName() {
        return fileName;
    }

    /** -1 if unknown. */
    public long getLength() {
        return length;
    }

    /** Closes the input stream, if there is one. Called by MineDash after sending. */
    @Override
    public void close() {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
                // Nothing we can do
            }
        }
    }
}
```

## WebSocketHandler

Callbacks of a WebSocket channel.

```java
package dev.mending.minedash.api;

/** Called on web server threads, not on the server main thread. */
public interface WebSocketHandler {

    default void onConnect(WebSocketConnection connection) throws Exception {
    }

    default void onMessage(WebSocketConnection connection, String message) throws Exception {
    }

    default void onClose(WebSocketConnection connection) {
    }
}
```

## WebSocketConnection

One open WebSocket connection.

```java
package dev.mending.minedash.api;

public interface WebSocketConnection {

    String getId();

    ApiUser getUser();

    boolean isOpen();

    /** Thread-safe. Does nothing if the connection is closed. */
    void send(String message);

    void close();
}
```

## Behaviour that the signatures do not show

| Topic | Behaviour |
| --- | --- |
| Route registration | `IllegalArgumentException` if the path does not start with `/`; `IllegalStateException` if the same method and path exist, or the module is disabled |
| Channel names | 1 to 32 characters of `A-Z a-z 0-9 _ -`; registering a name twice throws `IllegalStateException` |
| Route paths | Exact match; a trailing `/` is ignored |
| `route(..., permission, ...)` | `null` allows every logged-in user |
| Exceptions in handlers | `ApiException`: its status and message; any other exception: `500`, details in the server log |
| JSON bodies | At most 1 MB; larger bodies answer `413` before the handler runs |
| `getBodyStream(maxBytes)` | `413` as soon as the body exceeds `maxBytes` |
| `loadConfig` | Creates `data/<id>/config.json` with defaults, writes it back so new options appear |
| `runSync` | Runs on the next server tick; does not wait |
| WebSocket messages | Text only; the panel sends `ping` every 20 seconds |
| Disabling a module | `onDisable` runs, then routes and channels are dropped and all WebSocket connections are closed (without `onClose`) |
| Enabling again | A new instance of your class is created |
