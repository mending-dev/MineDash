package dev.mending.minedash.core.web;

import dev.mending.minedash.core.auth.LoginRateLimiter;
import dev.mending.minedash.core.auth.SessionManager;
import dev.mending.minedash.core.auth.User;
import dev.mending.minedash.core.auth.UserStore;
import dev.mending.minedash.core.config.MineDashConfig;
import dev.mending.minedash.core.module.ModuleManager;
import io.javalin.Javalin;
import io.javalin.http.UnauthorizedResponse;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

public final class WebServer {

    private final MineDashConfig config;
    private final UserStore users;
    private final SessionManager sessions;
    private final LoginRateLimiter limiter;
    private final ModuleManager modules;
    private final Plugin plugin;
    private final Logger logger;

    private Javalin app;

    public WebServer(MineDashConfig config, UserStore users, SessionManager sessions,
                     LoginRateLimiter limiter, ModuleManager modules, Plugin plugin, Logger logger) {
        this.config = config;
        this.users = users;
        this.sessions = sessions;
        this.limiter = limiter;
        this.modules = modules;
        this.plugin = plugin;
        this.logger = logger;
    }

    public void start() {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        // Jetty loads services via the context class loader, it must see the plugin's classes
        thread.setContextClassLoader(WebServer.class.getClassLoader());
        try {
            app = Javalin.create(cfg -> cfg.showJavalinBanner = false);

            // Security headers for every response
            app.before(ctx -> {
                ctx.header("X-Content-Type-Options", "nosniff");
                ctx.header("X-Frame-Options", "DENY");
                ctx.header("Referrer-Policy", "same-origin");
            });

            // Auth guard for the whole API except login
            app.before("/api/*", ctx -> {
                ctx.header("Cache-Control", "no-store");
                if (ctx.path().equals("/api/auth/login")) {
                    return;
                }
                SessionManager.Session session = sessions.get(ctx.cookie(AuthRoutes.COOKIE_NAME));
                User user = session == null ? null : users.get(session.getUsername());
                if (user == null) {
                    throw new UnauthorizedResponse("Not logged in");
                }
                ctx.attribute(AuthRoutes.ATTR_USER, user);
            });

            new AuthRoutes(users, sessions, limiter, config.auth, logger).register(app);
            new ModuleRoutes(modules, users, sessions, plugin, logger).register(app);

            // Must be registered last, it is the catch-all for the frontend
            app.get("/*", new StaticFiles()::handle);

            app.start(config.web.host, config.web.port);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    public void stop() {
        if (app != null) {
            app.stop();
            app = null;
        }
    }
}