package dev.mending.minedash.core.web;

import dev.mending.minedash.api.ApiException;
import dev.mending.minedash.api.HttpMethod;
import dev.mending.minedash.api.ModuleInfo;
import dev.mending.minedash.api.StreamResponse;
import dev.mending.minedash.api.WebSocketHandler;
import dev.mending.minedash.api.WidgetInfo;
import dev.mending.minedash.core.auth.SessionManager;
import dev.mending.minedash.core.auth.User;
import dev.mending.minedash.core.auth.UserStore;
import dev.mending.minedash.core.module.LoadedModule;
import dev.mending.minedash.core.module.ModuleContextImpl;
import dev.mending.minedash.core.module.ModuleException;
import dev.mending.minedash.core.module.ModuleManager;
import dev.mending.minedash.core.module.ModuleState;
import io.javalin.Javalin;
import io.javalin.http.ConflictResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpResponseException;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.websocket.WsCloseContext;
import io.javalin.websocket.WsConnectContext;
import io.javalin.websocket.WsMessageContext;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Management API, asset delivery, and request/WebSocket dispatching for modules. */
public final class ModuleRoutes {

    private static final String CORE_PREFIX = "/api/core/modules/";
    private static final String ASSET_PREFIX = "/api/assets/";
    private static final String MODULE_PREFIX = "/api/modules/";
    private static final String MANAGE_PERMISSION = "modules.manage";

    private static final int MAX_CONNECTIONS_PER_USER = 20;
    private static final int SESSION_CHECK_SECONDS = 10;

    private static final class WidgetView {
        final String id;
        final String title;
        final String size;
        final String permission;

        WidgetView(WidgetInfo info) {
            this.id = info.getId();
            this.title = info.getTitle();
            this.size = info.getSize();
            this.permission = info.getPermission();
        }
    }

    private static final class ModuleView {
        final String id;
        final String name;
        final String version;
        final String description;
        final List<String> authors;
        final List<String> dependencies;
        final List<WidgetView> widgets;
        final String state;
        final String error;
        /** Cleaned SVG markup of the icon, or null if the module has none. */
        final String icon;
        /** URL of the frontend entry file, only present while the module is enabled. */
        final String frontend;

        ModuleView(LoadedModule module) {
            ModuleInfo info = module.getInfo();
            this.id = info.getId();
            this.name = info.getName();
            this.version = info.getVersion();
            this.description = info.getDescription();
            this.authors = new ArrayList<>(info.getAuthors());
            this.dependencies = new ArrayList<>(info.getDependencies());
            this.widgets = new ArrayList<>();
            for (WidgetInfo widget : info.getWidgets()) {
                this.widgets.add(new WidgetView(widget));
            }
            this.state = module.getState().name().toLowerCase(Locale.ROOT);
            this.error = module.getError();
            this.icon = module.getIcon();
            this.frontend = module.getState() == ModuleState.ENABLED && info.getFrontend() != null
                    ? ASSET_PREFIX + info.getId() + "/" + info.getFrontend()
                    : null;
        }
    }

    private static final class Binding {
        final LoadedModule module;
        final WebSocketHandler handler;
        final WsConnection connection;
        /** Session token the connection was opened with, checked regularly. */
        final String token;
        final String username;

        Binding(LoadedModule module, WebSocketHandler handler, WsConnection connection,
                String token, String username) {
            this.module = module;
            this.handler = handler;
            this.connection = connection;
            this.token = token;
            this.username = username;
        }
    }

    private final ModuleManager manager;
    private final UserStore users;
    private final SessionManager sessions;
    private final WebSecurity security;
    private final Plugin plugin;
    private final Logger logger;
    private final Map<String, Binding> connections = new ConcurrentHashMap<>();

    private ScheduledExecutorService sweeper;

    public ModuleRoutes(ModuleManager manager, UserStore users, SessionManager sessions,
                        WebSecurity security, Plugin plugin, Logger logger) {
        this.manager = manager;
        this.users = users;
        this.sessions = sessions;
        this.security = security;
        this.plugin = plugin;
        this.logger = logger;
    }

    public void register(Javalin app) {
        app.get("/api/core/modules", this::list);
        app.post(CORE_PREFIX + "*", this::toggle);

        app.get(ASSET_PREFIX + "*", this::asset);

        app.get(MODULE_PREFIX + "*", ctx -> dispatch(ctx, HttpMethod.GET));
        app.post(MODULE_PREFIX + "*", ctx -> dispatch(ctx, HttpMethod.POST));
        app.put(MODULE_PREFIX + "*", ctx -> dispatch(ctx, HttpMethod.PUT));
        app.delete(MODULE_PREFIX + "*", ctx -> dispatch(ctx, HttpMethod.DELETE));

        app.ws("/api/ws", ws -> {
            ws.onConnect(this::wsConnect);
            ws.onMessage(this::wsMessage);
            ws.onClose(this::wsClose);
        });

        // Open WebSockets must not outlive the session they were opened with
        sweeper = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "MineDash-WsSessions");
            thread.setDaemon(true);
            return thread;
        });
        sweeper.scheduleWithFixedDelay(this::closeExpiredConnections,
                SESSION_CHECK_SECONDS, SESSION_CHECK_SECONDS, TimeUnit.SECONDS);
    }

    public void shutdown() {
        if (sweeper != null) {
            sweeper.shutdownNow();
            sweeper = null;
        }
    }

    // ---------------------------------------------------------------- management

    private void list(Context ctx) {
        List<ModuleView> views = new ArrayList<>();
        for (LoadedModule module : manager.getModules()) {
            views.add(new ModuleView(module));
        }
        Json.respond(ctx, views);
    }

    /** POST /api/core/modules/{id}/enable or /disable */
    private void toggle(Context ctx) {
        User user = ctx.attribute(AuthRoutes.ATTR_USER);
        if (!user.hasPermission(MANAGE_PERMISSION)) {
            throw new ForbiddenResponse("Missing permission: " + MANAGE_PERMISSION);
        }

        String[] parts = ctx.path().substring(CORE_PREFIX.length()).split("/");
        if (parts.length != 2 || !(parts[1].equals("enable") || parts[1].equals("disable"))) {
            throw new NotFoundResponse();
        }
        final String id = parts[0];
        final boolean enable = parts[1].equals("enable");
        if (manager.getModule(id) == null) {
            throw new NotFoundResponse("Unknown module");
        }

        try {
            // Module lifecycle must run on the server main thread
            Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                if (enable) {
                    manager.enable(id);
                } else {
                    manager.disable(id);
                }
                return null;
            }).get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ModuleException) {
                throw new ConflictResponse(cause.getMessage());
            }
            logger.log(Level.WARNING, "Could not change state of module '" + id + "'", cause);
            throw new InternalServerErrorResponse();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InternalServerErrorResponse();
        } catch (TimeoutException e) {
            throw new InternalServerErrorResponse();
        }

        Json.respond(ctx, new ModuleView(manager.getModule(id)));
    }

    // ---------------------------------------------------------------- assets

    /** GET /api/assets/{id}/{file} serves web/{file} from the module jar. */
    private void asset(Context ctx) {
        String rest = ctx.path().substring(ASSET_PREFIX.length());
        int slash = rest.indexOf('/');
        if (slash < 1) {
            throw new NotFoundResponse();
        }

        LoadedModule module = manager.getModule(rest.substring(0, slash));
        String file = rest.substring(slash + 1);
        if (module == null || module.getState() != ModuleState.ENABLED || !isSafePath(file)) {
            throw new NotFoundResponse();
        }

        InputStream in;
        try {
            in = module.openResource("web/" + file);
        } catch (IOException e) {
            throw new NotFoundResponse();
        }
        if (in == null) {
            throw new NotFoundResponse();
        }
        ctx.contentType(StaticFiles.contentType(file));
        ctx.result(in);
    }

    private static boolean isSafePath(String file) {
        return !file.isEmpty()
                && !file.startsWith("/")
                && !file.contains("..")
                && !file.contains("\\")
                && !file.contains("\0");
    }

    // ---------------------------------------------------------------- module routes

    private void dispatch(Context ctx, HttpMethod method) {
        String rest = ctx.path().substring(MODULE_PREFIX.length());
        int slash = rest.indexOf('/');
        String id = slash < 0 ? rest : rest.substring(0, slash);
        String path = slash < 0 ? "/" : rest.substring(slash);
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        LoadedModule module = manager.getModule(id);
        ModuleContextImpl context = module == null ? null : module.getContext();
        if (module == null || module.getState() != ModuleState.ENABLED || context == null) {
            throw new NotFoundResponse("Module not available");
        }
        ModuleContextImpl.Route route = context.findRoute(method, path);
        if (route == null) {
            throw new NotFoundResponse();
        }

        User user = ctx.attribute(AuthRoutes.ATTR_USER);
        if (route.permission != null && !user.hasPermission(route.permission)) {
            throw new ForbiddenResponse("Missing permission: " + route.permission);
        }

        try {
            Object result = route.handler.handle(new ApiRequestImpl(ctx, user, path));
            if (result == null) {
                ctx.status(204);
            } else if (result instanceof StreamResponse) {
                StreamResponses.write(ctx, (StreamResponse) result);
            } else {
                Json.respond(ctx, result);
            }
        } catch (ApiException e) {
            ctx.status(e.getStatus()).result(e.getMessage() == null ? "" : e.getMessage());
        } catch (HttpResponseException e) {
            // Raised by Javalin itself, e.g. 413 for a JSON body over 1 MB. It already carries the right status.
            throw e;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Module '" + id + "' failed on " + method + " " + path, e);
            throw new InternalServerErrorResponse();
        }
    }

    // ---------------------------------------------------------------- websocket

    private void wsConnect(WsConnectContext ctx) {
        // The HTTP guard does not cover WebSocket upgrades, so everything is checked here
        if (!security.originAllowed(ctx.header("Origin"), ctx.header("Host"), ctx.header("X-Forwarded-Host"))) {
            ctx.closeSession(4403, "Forbidden origin");
            return;
        }

        String token = ctx.cookie(AuthRoutes.COOKIE_NAME);
        SessionManager.Session session = sessions.get(token);
        User user = session == null ? null : users.get(session.getUsername());
        if (user == null) {
            ctx.closeSession(4401, "Not logged in");
            return;
        }
        if (countConnections(user.getUsername()) >= MAX_CONNECTIONS_PER_USER) {
            ctx.closeSession(4429, "Too many connections");
            return;
        }

        String channelName = ctx.queryParam("channel");
        LoadedModule module = manager.getModule(ctx.queryParam("module"));
        ModuleContextImpl context = module == null ? null : module.getContext();
        ModuleContextImpl.Channel channel = context == null || channelName == null
                || module.getState() != ModuleState.ENABLED ? null : context.findChannel(channelName);
        if (channel == null) {
            ctx.closeSession(4404, "Unknown channel");
            return;
        }
        if (channel.permission != null && !user.hasPermission(channel.permission)) {
            ctx.closeSession(4403, "Forbidden");
            return;
        }

        WsConnection connection = new WsConnection(ctx, user);
        connections.put(ctx.getSessionId(),
                new Binding(module, channel.handler, connection, token, user.getUsername()));
        module.addConnection(connection);
        try {
            channel.handler.onConnect(connection);
        } catch (Exception e) {
            logger.log(Level.WARNING, "WebSocket onConnect failed in module '" + module.getInfo().getId() + "'", e);
            connection.close();
        }
    }

    private void wsMessage(WsMessageContext ctx) {
        Binding binding = connections.get(ctx.getSessionId());
        if (binding == null || binding.module.getState() != ModuleState.ENABLED) {
            return;
        }
        try {
            binding.handler.onMessage(binding.connection, ctx.message());
        } catch (Exception e) {
            logger.log(Level.WARNING, "WebSocket onMessage failed in module '"
                    + binding.module.getInfo().getId() + "'", e);
        }
    }

    private void wsClose(WsCloseContext ctx) {
        Binding binding = connections.remove(ctx.getSessionId());
        if (binding == null) {
            return;
        }
        binding.connection.markClosed();
        binding.module.removeConnection(binding.connection);
        if (binding.module.getState() != ModuleState.ENABLED) {
            return;
        }
        try {
            binding.handler.onClose(binding.connection);
        } catch (Exception e) {
            logger.log(Level.WARNING, "WebSocket onClose failed in module '"
                    + binding.module.getInfo().getId() + "'", e);
        }
    }

    private int countConnections(String username) {
        int count = 0;
        for (Binding binding : connections.values()) {
            if (binding.username.equals(username)) {
                count++;
            }
        }
        return count;
    }

    /** Closes connections whose session ended. Uses peek() so the check never extends a session. */
    private void closeExpiredConnections() {
        try {
            for (Binding binding : connections.values()) {
                if (sessions.peek(binding.token) == null) {
                    binding.connection.closeWith(4401, "Session expired");
                }
            }
        } catch (RuntimeException e) {
            // A scheduled task that throws is never run again, so everything is caught here
            logger.log(Level.WARNING, "WebSocket session check failed", e);
        }
    }
}