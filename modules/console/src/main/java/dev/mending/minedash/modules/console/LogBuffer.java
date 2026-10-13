package dev.mending.minedash.modules.console;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Ring buffer of the latest log lines with live subscribers. */
final class LogBuffer {

    interface Listener {
        /** Called once on subscribe, before any live entry. */
        void onHistory(List<LogEntry> entries);

        void onEntry(LogEntry entry);
    }

    private final int capacity;
    private final Deque<LogEntry> entries = new ArrayDeque<>();
    private final List<Listener> listeners = new ArrayList<>();
    private long nextId = 1;

    LogBuffer(int capacity) {
        this.capacity = capacity;
    }

    synchronized void add(String level, String logger, String message) {
        LogEntry entry = new LogEntry(nextId++, System.currentTimeMillis(), level, logger, message);
        entries.addLast(entry);
        while (entries.size() > capacity) {
            entries.removeFirst();
        }
        for (Listener listener : listeners) {
            try {
                listener.onEntry(entry);
            } catch (RuntimeException ignored) {
                // A broken subscriber must never break server logging
            }
        }
    }

    /** History and registration happen under one lock, so no entry is lost or duplicated. */
    synchronized void subscribe(Listener listener) {
        listener.onHistory(new ArrayList<>(entries));
        listeners.add(listener);
    }

    synchronized void unsubscribe(Listener listener) {
        listeners.remove(listener);
    }
}