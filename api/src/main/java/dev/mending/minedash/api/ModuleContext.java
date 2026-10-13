package dev.mending.minedash.api;

import org.bukkit.plugin.Plugin;

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
}