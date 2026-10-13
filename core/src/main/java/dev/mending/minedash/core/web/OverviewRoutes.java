package dev.mending.minedash.core.web;

import dev.mending.minedash.core.auth.User;
import dev.mending.minedash.core.metrics.MetricsCollector;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;

public final class OverviewRoutes {

    private static final String PERMISSION = "overview.view";

    private final MetricsCollector collector;

    public OverviewRoutes(MetricsCollector collector) {
        this.collector = collector;
    }

    public void register(Javalin app) {
        app.get("/api/core/overview/info", ctx -> {
            requireAccess(ctx);
            Json.respond(ctx, collector.getInfo());
        });

        // ?since={seq} returns only samples the client does not have yet
        app.get("/api/core/overview/metrics", ctx -> {
            requireAccess(ctx);
            Json.respond(ctx, collector.read(parseSince(ctx.queryParam("since"))));
        });
    }

    private void requireAccess(Context ctx) {
        User user = ctx.attribute(AuthRoutes.ATTR_USER);
        if (!user.hasPermission(PERMISSION)) {
            throw new ForbiddenResponse("Missing permission: " + PERMISSION);
        }
    }

    private static long parseSince(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(0, Long.parseLong(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}