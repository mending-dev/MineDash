package dev.mending.minedash.core.auth;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {

    public static final class Session {
        private final String token;
        private final String username;
        private final long createdAt;
        private volatile long expiresAt;

        private Session(String token, String username, long createdAt, long expiresAt) {
            this.token = token;
            this.username = username;
            this.createdAt = createdAt;
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
    private final long idleTimeoutMillis;
    private final long maxLifetimeMillis;

    /**
     * @param idleTimeoutMillis time without activity after which a session ends
     * @param maxLifetimeMillis absolute lifetime, activity does not extend it
     */
    public SessionManager(long idleTimeoutMillis, long maxLifetimeMillis) {
        this.idleTimeoutMillis = idleTimeoutMillis;
        this.maxLifetimeMillis = maxLifetimeMillis;
    }

    public Session create(String username) {
        long now = System.currentTimeMillis();
        // Purge expired sessions on every login, no scheduler needed
        sessions.values().removeIf(s -> isExpired(s, now));

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Session session = new Session(token, username, now, now + idleTimeoutMillis);
        sessions.put(token, session);
        return session;
    }

    /** Returns the session and extends its idle timeout, or null if missing/expired. */
    public Session get(String token) {
        Session session = find(token);
        if (session != null) {
            session.expiresAt = System.currentTimeMillis() + idleTimeoutMillis;
        }
        return session;
    }

    /** Like get(), but does not extend the session. Used for background checks. */
    public Session peek(String token) {
        return find(token);
    }

    public void invalidate(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    private Session find(String token) {
        if (token == null) return null;
        Session session = sessions.get(token);
        if (session == null) return null;
        if (isExpired(session, System.currentTimeMillis())) {
            sessions.remove(token);
            return null;
        }
        return session;
    }

    private boolean isExpired(Session session, long now) {
        return now > session.expiresAt || now - session.createdAt > maxLifetimeMillis;
    }
}