package dev.mending.minedash.core.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MineDashConfig {

    public Web web = new Web();
    public Auth auth = new Auth();

    public static class Web {
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

        auth.sessionTimeoutMinutes = Math.max(1, auth.sessionTimeoutMinutes);
        auth.maxSessionHours = Math.min(24 * 365, Math.max(1, auth.maxSessionHours));
        auth.maxLoginAttempts = Math.max(1, auth.maxLoginAttempts);
        auth.lockoutMinutes = Math.max(1, auth.lockoutMinutes);
    }
}