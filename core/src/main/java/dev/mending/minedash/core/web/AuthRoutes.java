package dev.mending.minedash.core.web;

import dev.mending.minedash.core.auth.LoginRateLimiter;
import dev.mending.minedash.core.auth.SessionManager;
import dev.mending.minedash.core.auth.User;
import dev.mending.minedash.core.auth.UserStore;
import dev.mending.minedash.core.config.MineDashConfig;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class AuthRoutes {

    static final String COOKIE_NAME = "minedash_session";
    static final String ATTR_USER = "minedash.user";
    private static final int MAX_FIELD_LENGTH = 128;

    private static final class LoginRequest {
        String username;
        String password;
    }

    private static final class UserInfo {
        final String username;
        final List<String> permissions;

        UserInfo(User user) {
            this.username = user.username;
            this.permissions = new ArrayList<>(user.permissions);
        }
    }

    private final UserStore users;
    private final SessionManager sessions;
    private final LoginRateLimiter limiter;
    private final MineDashConfig.Auth authConfig;
    private final Logger logger;

    public AuthRoutes(UserStore users, SessionManager sessions, LoginRateLimiter limiter,
                      MineDashConfig.Auth authConfig, Logger logger) {
        this.users = users;
        this.sessions = sessions;
        this.limiter = limiter;
        this.authConfig = authConfig;
        this.logger = logger;
    }

    public void register(Javalin app) {
        app.post("/api/auth/login", this::login);
        app.post("/api/auth/logout", this::logout);
        app.get("/api/auth/me", this::me);
    }

    private void login(Context ctx) {
        String ip = ctx.ip();
        if (limiter.isBlocked(ip)) {
            ctx.status(429).result("Too many login attempts, try again later");
            return;
        }

        LoginRequest request = Json.read(ctx, LoginRequest.class);
        if (request.username == null || request.password == null
                || request.username.length() > MAX_FIELD_LENGTH
                || request.password.length() > MAX_FIELD_LENGTH) {
            throw new BadRequestResponse("Invalid request");
        }

        User user = users.authenticate(request.username, request.password);
        if (user == null) {
            limiter.recordFailure(ip);
            logger.warning("Failed login for '" + request.username.replaceAll("[^\\w.-]", "?") + "' from " + ip);
            throw new UnauthorizedResponse("Invalid username or password");
        }

        limiter.reset(ip);
        // Always issue a fresh token on login to prevent session fixation
        sessions.invalidate(ctx.cookie(COOKIE_NAME));
        SessionManager.Session session = sessions.create(user.username);
        setCookie(ctx, session.getToken(), false);

        logger.info(user.username + " logged in from " + ip);
        Json.respond(ctx, new UserInfo(user));
    }

    private void logout(Context ctx) {
        sessions.invalidate(ctx.cookie(COOKIE_NAME));
        setCookie(ctx, "", true);
        ctx.status(204);
    }

    private void me(Context ctx) {
        User user = ctx.attribute(ATTR_USER);
        Json.respond(ctx, new UserInfo(user));
    }

    private void setCookie(Context ctx, String value, boolean expire) {
        // No Max-Age: cookie lives until the browser closes, the server enforces the timeout
        StringBuilder cookie = new StringBuilder(COOKIE_NAME).append('=').append(value)
                .append("; Path=/; HttpOnly; SameSite=Strict");
        if (expire) cookie.append("; Max-Age=0");
        if (authConfig.secureCookie) cookie.append("; Secure");
        ctx.header("Set-Cookie", cookie.toString());
    }
}