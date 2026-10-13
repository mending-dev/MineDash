package dev.mending.minedash.core.auth;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {

    public static final class Session {
        private final String token;
        private final String username;
        private volatile long expiresAt;

        private Session(String token, String username, long expiresAt) {
            this.token = token;
            this.username = username;
            this.expiresAt = expiresAt;
        }

        public String getToken() {
            return token;
        }

        public String getUsername() {
            return username;
        }
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final long timeoutMillis;

    public SessionManager(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public Session create(String username) {
        long now = System.currentTimeMillis();
        // Purge expired sessions on every login, no scheduler needed
        sessions.values().removeIf(s -> now > s.expiresAt);

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Session session = new Session(token, username, now + timeoutMillis);
        sessions.put(token, session);
        return session;
    }

    /** Returns the session and extends its lifetime, or null if missing/expired. */
    public Session get(String token) {
        if (token == null) return null;
        Session session = sessions.get(token);
        if (session == null) return null;

        long now = System.currentTimeMillis();
        if (now > session.expiresAt) {
            sessions.remove(token);
            return null;
        }
        session.expiresAt = now + timeoutMillis;
        return session;
    }

    public void invalidate(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }
}