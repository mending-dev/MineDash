package dev.mending.minedash.core.web;

import dev.mending.minedash.core.config.MineDashConfig;
import io.javalin.http.Context;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Security headers, origin checks and client IP detection. */
public final class WebSecurity {

    /** Host with optional port, e.g. "panel.example.com:8765" or "[::1]:8765". Nothing else gets into the CSP. */
    private static final Pattern SAFE_HOST =
            Pattern.compile("[A-Za-z0-9.\\-]+(:\\d{1,5})?|\\[[0-9A-Fa-f:.]+](:\\d{1,5})?");
    /** Characters of an IPv4 or IPv6 address. Keeps forged header values out of logs and maps. */
    private static final Pattern IP_CHARS = Pattern.compile("[0-9A-Fa-f:.]{1,45}");

    private final boolean trustProxy;
    private final boolean secure;
    private final Set<String> allowedOrigins;

    public WebSecurity(MineDashConfig config) {
        this.trustProxy = config.web.trustProxy;
        this.secure = config.auth.secureCookie;
        this.allowedOrigins = new HashSet<>(config.web.allowedOrigins);
    }

    /** Sets the security headers. Runs for every response. */
    void applyHeaders(Context ctx) {
        ctx.header("X-Content-Type-Options", "nosniff");
        ctx.header("X-Frame-Options", "DENY");
        ctx.header("Referrer-Policy", "same-origin");
        ctx.header("Cross-Origin-Opener-Policy", "same-origin");
        ctx.header("Cross-Origin-Resource-Policy", "same-origin");
        ctx.header("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()");
        ctx.header("Content-Security-Policy", contentSecurityPolicy(ctx));
        if (secure) {
            ctx.header("Strict-Transport-Security", "max-age=15552000");
        }
    }

    /**
     * Returns false if a browser request comes from a foreign site.
     * Requests without Origin are not sent by browsers on same-site navigation, so they pass.
     */
    boolean originAllowed(String origin, String host, String forwardedHost) {
        if (origin == null) {
            return true;
        }
        String value = origin.trim().toLowerCase(Locale.ROOT);
        // "null" is sent from sandboxed frames and similar contexts
        if (value.isEmpty() || value.equals("null")) {
            return false;
        }
        if (allowedOrigins.contains(value)) {
            return true;
        }
        int scheme = value.indexOf("://");
        if (scheme < 0) {
            return false;
        }
        String originHost = value.substring(scheme + 3);
        if (host != null && originHost.equalsIgnoreCase(host.trim())) {
            return true;
        }
        String forwarded = trustProxy ? firstValue(forwardedHost) : null;
        return forwarded != null && originHost.equalsIgnoreCase(forwarded);
    }

    /** IP address used for the login rate limit. */
    String clientIp(Context ctx) {
        if (trustProxy) {
            String forwarded = ctx.header("X-Forwarded-For");
            if (forwarded != null) {
                // The last entry was written by our own proxy, earlier ones can be forged by the client
                String[] parts = forwarded.split(",");
                String last = parts[parts.length - 1].trim();
                if (IP_CHARS.matcher(last).matches()) {
                    return last;
                }
            }
        }
        return ctx.ip();
    }

    private String contentSecurityPolicy(Context ctx) {
        StringBuilder connect = new StringBuilder("connect-src 'self'");
        String host = publicHost(ctx);
        if (host != null) {
            // Some browsers do not let 'self' match ws:// and wss://
            connect.append(" ws://").append(host).append(" wss://").append(host);
        }
        return "default-src 'self'; "
                + "script-src 'self'; "
                // Module bundles inject their CSS at runtime
                + "style-src 'self' 'unsafe-inline'; "
                + "img-src 'self' data:; "
                + "font-src 'self'; "
                + connect + "; "
                + "object-src 'none'; "
                + "base-uri 'none'; "
                + "frame-ancestors 'none'; "
                + "form-action 'self'";
    }

    /** The host the browser used, or null if the header looks suspicious. */
    private String publicHost(Context ctx) {
        String host = trustProxy ? firstValue(ctx.header("X-Forwarded-Host")) : null;
        if (host == null) {
            host = ctx.header("Host");
        }
        return host != null && SAFE_HOST.matcher(host.trim()).matches() ? host.trim() : null;
    }

    private static String firstValue(String header) {
        if (header == null) {
            return null;
        }
        int comma = header.indexOf(',');
        String value = (comma < 0 ? header : header.substring(0, comma)).trim();
        return value.isEmpty() ? null : value;
    }
}