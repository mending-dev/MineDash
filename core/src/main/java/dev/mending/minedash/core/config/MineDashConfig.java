package dev.mending.minedash.core.config;

public class MineDashConfig {

    public Web web = new Web();
    public Auth auth = new Auth();

    public static class Web {
        /** Address to bind to. Use 127.0.0.1 when running behind a reverse proxy. */
        public String host = "0.0.0.0";
        public int port = 8765;
    }

    public static class Auth {
        public int sessionTimeoutMinutes = 60;
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
        auth.sessionTimeoutMinutes = Math.max(1, auth.sessionTimeoutMinutes);
        auth.maxLoginAttempts = Math.max(1, auth.maxLoginAttempts);
        auth.lockoutMinutes = Math.max(1, auth.lockoutMinutes);
    }
}