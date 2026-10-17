package dev.mending.minedash.core.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MineDashConfig {

    public Web web = new Web();
    public Auth auth = new Auth();

    public static class Web {
        public static final String DEFAULT_AVATAR_URL = "https://api.mcheads.org/head/{name}/{size}";

        /** Address to bind to. Use 127.0.0.1 when running behind a reverse proxy. */
        public String host = "0.0.0.0";
        public int port = 8765;
        /**
         * Set to true only if the panel is reachable exclusively through your own reverse proxy.
         * Then the client IP is taken from X-Forwarded-For and X-Forwarded-Host is accepted.
         */
        public boolean trustProxy = false;
        /** Additional browser origins allowed to call the API, e.g. "https://panel.example.com". */
        public List<String> allowedOrigins = new ArrayList<>();
        /**
         * Where the player heads in front of the user names come from. {name} is replaced by the user
         * name, {size} by the edge length in pixels. A head is only requested for names that are valid
         * Minecraft names; if the service has no image, the first letter is shown. The host of this URL
         * is the only foreign host the panel may load images from. Empty switches heads off.
         */
        public String avatarUrl = DEFAULT_AVATAR_URL;

        /** Scheme, host and port of the avatar URL, or null if heads are switched off. */
        public String avatarOrigin() {
            return avatarUrl == null || avatarUrl.isEmpty() ? null : originOf(avatarUrl);
        }
    }

    public static class Auth {
        /** Time without activity after which a session ends. */
        public int sessionTimeoutMinutes = 60;
        /** Absolute lifetime of a session, no matter how active it is. */
        public int maxSessionHours = 12;
        public int maxLoginAttempts = 5;
        public int lockoutMinutes = 5;
        /** Set to true when the panel is served over HTTPS. */
        public boolean secureCookie = false;
    }

    /** Scheme, host and optional port, then an optional path or query. Nothing else gets into the CSP. */
    private static final Pattern AVATAR_URL =
            Pattern.compile("(https?)://([A-Za-z0-9.\\-]+)(:\\d{1,5})?(?:[/?][^\\s\"'<>\\\\]*)?");

    /** Origin (scheme, host, port) of a URL template, or null if the template is not a plain http(s) URL. */
    static String originOf(String template) {
        Matcher matcher = AVATAR_URL.matcher(template);
        if (!matcher.matches()) {
            return null;
        }
        return matcher.group(1).toLowerCase(Locale.ROOT) + "://" + matcher.group(2).toLowerCase(Locale.ROOT)
                + (matcher.group(3) == null ? "" : matcher.group(3));
    }

    private static String normalizeAvatarUrl(String value) {
        if (value == null) {
            return Web.DEFAULT_AVATAR_URL;
        }
        String url = value.trim();
        if (url.isEmpty()) {
            return "";
        }
        if (url.length() > 300 || !url.contains("{name}") || originOf(url) == null) {
            throw new IllegalStateException("web.avatarUrl must be an http(s) URL that contains {name}, e.g. "
                    + Web.DEFAULT_AVATAR_URL + " (leave it empty to switch heads off)");
        }
        return url;
    }

    /** Fixes missing sections and invalid values. */
    public void validate() {
        if (web == null) web = new Web();
        if (auth == null) auth = new Auth();

        if (web.host == null || web.host.trim().isEmpty()) {
            web.host = "0.0.0.0";
        }
        if (web.port < 1 || web.port > 65535) {
            throw new IllegalStateException("web.port must be between 1 and 65535");
        }

        // Normalize origins: trimmed, lower case, no trailing slash, no duplicates
        Set<String> origins = new LinkedHashSet<>();
        if (web.allowedOrigins != null) {
            for (String origin : web.allowedOrigins) {
                if (origin == null) continue;
                String normalized = origin.trim().toLowerCase(Locale.ROOT);
                while (normalized.endsWith("/")) {
                    normalized = normalized.substring(0, normalized.length() - 1);
                }
                if (!normalized.isEmpty()) {
                    origins.add(normalized);
                }
            }
        }
        web.allowedOrigins = new ArrayList<>(origins);
        web.avatarUrl = normalizeAvatarUrl(web.avatarUrl);

        auth.sessionTimeoutMinutes = Math.max(1, auth.sessionTimeoutMinutes);
        auth.maxSessionHours = Math.min(24 * 365, Math.max(1, auth.maxSessionHours));
        auth.maxLoginAttempts = Math.max(1, auth.maxLoginAttempts);
        auth.lockoutMinutes = Math.max(1, auth.lockoutMinutes);
    }
}