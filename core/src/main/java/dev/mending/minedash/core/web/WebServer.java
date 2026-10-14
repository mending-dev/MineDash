package dev.mending.minedash.core.web;

import dev.mending.minedash.core.auth.LoginRateLimiter;
import dev.mending.minedash.core.auth.SessionManager;
import dev.mending.minedash.core.auth.User;
import dev.mending.minedash.core.auth.UserStore;
import dev.mending.minedash.core.config.MineDashConfig;
import dev.mending.minedash.core.metrics.MetricsCollector;
import dev.mending.minedash.core.module.ModuleManager;
import io.javalin.Javalin;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.UnauthorizedResponse;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class WebServer {

    private final MineDashConfig config;
    private final UserStore users;
    private final SessionManager sessions;
    private final LoginRateLimiter limiter;
    private final ModuleManager modules;
    private final MetricsCollector metrics;
    private final Plugin plugin;
    private final Logger logger;

    private Javalin app;
    private ModuleRoutes moduleRoutes;

    public WebServer(MineDashConfig config, UserStore users, SessionManager sessions,
                     LoginRateLimiter limiter, ModuleManager modules, MetricsCollector metrics,
                     Plugin plugin, Logger logger) {
        this.config = config;
        this.users = users;
        this.sessions = sessions;
        this.limiter = limiter;
        this.modules = modules;
        this.metrics = metrics;
        this.plugin = plugin;
        this.logger = logger;
    }

    public void start() {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        // Jetty loads services via the context class loader, it must see the plugin's classes
        thread.setContextClassLoader(WebServer.class.getClassLoader());
        try {
            WebSecurity security = new WebSecurity(config);
            app = Javalin.create(cfg -> cfg.showJavalinBanner = false);

            // Security headers (including the CSP) for every response
            app.before(security::applyHeaders);

            // Guard for the whole API
            app.before("/api/*", ctx -> {
                ctx.header("Cache-Control", "no-store");

                // Requests that change something must come from this site. SameSite=Strict on the
                // session cookie is the second layer of protection.
                String method = ctx.method();
                boolean readOnly = method.equals("GET") || method.equals("HEAD") || method.equals("OPTIONS");
                if (!readOnly && !security.originAllowed(
                        ctx.header("Origin"), ctx.header("Host"), ctx.header("X-Forwarded-Host"))) {
                    throw new ForbiddenResponse("Cross-origin request blocked");
                }

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

            new AuthRoutes(users, sessions, limiter, config.auth, security, logger).register(app);
            new OverviewRoutes(metrics).register(app);
            moduleRoutes = new ModuleRoutes(modules, users, sessions, security, plugin, logger);
            moduleRoutes.register(app);

            // Must be registered last, it is the catch-all for the frontend
            app.get("/*", new StaticFiles()::handle);

            app.start(config.web.host, config.web.port);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    public void stop() {
        if (moduleRoutes != null) {
            moduleRoutes.shutdown();
            moduleRoutes = null;
        }
        if (app != null) {
            try {
                app.stop();
            } catch (RuntimeException e) {
                // Also happens when start() failed halfway, which must not hide the original problem
                logger.log(Level.WARNING, "Error while stopping the web server", e);
            }
            app = null;
        }
    }
}