package dev.mending.minedash.core.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.mending.minedash.api.HttpMethod;
import dev.mending.minedash.api.ModuleContext;
import dev.mending.minedash.api.ModuleInfo;
import dev.mending.minedash.api.RouteHandler;
import dev.mending.minedash.api.WebSocketHandler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public final class ModuleContextImpl implements ModuleContext {

    private static final Pattern CHANNEL_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static final class Route {
        public final HttpMethod method;
        public final String path;
        public final String permission;
        public final RouteHandler handler;

        private Route(HttpMethod method, String path, String permission, RouteHandler handler) {
            this.method = method;
            this.path = path;
            this.permission = permission;
            this.handler = handler;
        }
    }

    public static final class Channel {
        public final String permission;
        public final WebSocketHandler handler;

        private Channel(String permission, WebSocketHandler handler) {
            this.permission = permission;
            this.handler = handler;
        }
    }

    private final Plugin plugin;
    private final ModuleInfo info;
    private final Logger logger;
    private final Path dataFolder;
    private final List<Route> routes = new CopyOnWriteArrayList<>();
    private final Map<String, Channel> channels = new ConcurrentHashMap<>();
    private volatile boolean closed;

    ModuleContextImpl(Plugin plugin, ModuleInfo info, Logger logger, Path dataFolder) {
        this.plugin = plugin;
        this.info = info;
        this.logger = logger;
        this.dataFolder = dataFolder;
    }

    @Override
    public ModuleInfo getInfo() {
        return info;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public Path getDataFolder() {
        return dataFolder;
    }

    @Override
    public Plugin getPlugin() {
        return plugin;
    }

    @Override
    public void route(HttpMethod method, String path, String permission, RouteHandler handler) {
        checkOpen();
        if (method == null || handler == null) {
            throw new IllegalArgumentException("method and handler must not be null");
        }
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("Route path must start with '/': " + path);
        }
        String normalized = path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        if (findRoute(method, normalized) != null) {
            throw new IllegalStateException("Route already registered: " + method + " " + normalized);
        }
        routes.add(new Route(method, normalized, permission, handler));
    }

    @Override
    public void webSocket(String channel, String permission, WebSocketHandler handler) {
        checkOpen();
        if (channel == null || !CHANNEL_PATTERN.matcher(channel).matches()) {
            throw new IllegalArgumentException("Invalid channel name: " + channel);
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null");
        }
        if (channels.putIfAbsent(channel, new Channel(permission, handler)) != null) {
            throw new IllegalStateException("Channel already registered: " + channel);
        }
    }

    @Override
    public void runSync(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public String toJson(Object value) {
        return GSON.toJson(value);
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return GSON.fromJson(json, type);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Invalid JSON", e);
        }
    }

    public Route findRoute(HttpMethod method, String path) {
        for (Route route : routes) {
            if (route.method == method && route.path.equals(path)) {
                return route;
            }
        }
        return null;
    }

    public Channel findChannel(String name) {
        return channels.get(name);
    }

    /** Drops all registrations. The context cannot be used afterwards. */
    void close() {
        closed = true;
        routes.clear();
        channels.clear();
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("Module is disabled");
        }
    }
}