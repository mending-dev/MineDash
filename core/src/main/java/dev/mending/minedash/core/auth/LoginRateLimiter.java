package dev.mending.minedash.core.auth;

import java.util.HashMap;
import java.util.Map;

public final class LoginRateLimiter {

    private static final class Entry {
        int failures;
        long lastFailure;
        long lockedUntil;
    }

    private final Map<String, Entry> entries = new HashMap<>();
    private final int maxAttempts;
    private final long lockoutMillis;

    public LoginRateLimiter(int maxAttempts, long lockoutMillis) {
        this.maxAttempts = maxAttempts;
        this.lockoutMillis = lockoutMillis;
    }

    public synchronized boolean isBlocked(String key) {
        Entry entry = entries.get(key);
        return entry != null && System.currentTimeMillis() < entry.lockedUntil;
    }

    public synchronized void recordFailure(String key) {
        long now = System.currentTimeMillis();
        // Drop stale entries so the map cannot grow forever
        entries.values().removeIf(e -> now - e.lastFailure > lockoutMillis && now >= e.lockedUntil);

        Entry entry = entries.computeIfAbsent(key, k -> new Entry());
        entry.failures++;
        entry.lastFailure = now;
        if (entry.failures >= maxAttempts) {
            entry.lockedUntil = now + lockoutMillis;
            entry.failures = 0;
        }
    }

    public synchronized void reset(String key) {
        entries.remove(key);
    }
}