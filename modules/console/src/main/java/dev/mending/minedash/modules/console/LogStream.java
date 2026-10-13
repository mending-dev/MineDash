package dev.mending.minedash.modules.console;

import dev.mending.minedash.api.ModuleContext;
import dev.mending.minedash.api.WebSocketConnection;
import dev.mending.minedash.api.WebSocketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** WebSocket channel "logs": sends the history first, then every new line. */
final class LogStream implements WebSocketHandler {

    private static final class HistoryMessage {
        final String type = "history";
        final List<LogEntry> entries;

        HistoryMessage(List<LogEntry> entries) {
            this.entries = entries;
        }
    }

    private static final class EntryMessage {
        final String type = "log";
        final LogEntry entry;

        EntryMessage(LogEntry entry) {
            this.entry = entry;
        }
    }

    private final ModuleContext context;
    private final LogBuffer buffer;
    private final Map<String, LogBuffer.Listener> listeners = new ConcurrentHashMap<>();

    LogStream(ModuleContext context, LogBuffer buffer) {
        this.context = context;
        this.buffer = buffer;
    }

    @Override
    public void onConnect(final WebSocketConnection connection) {
        LogBuffer.Listener listener = new LogBuffer.Listener() {
            @Override
            public void onHistory(List<LogEntry> entries) {
                connection.send(context.toJson(new HistoryMessage(entries)));
            }

            @Override
            public void onEntry(LogEntry entry) {
                connection.send(context.toJson(new EntryMessage(entry)));
            }
        };
        listeners.put(connection.getId(), listener);
        buffer.subscribe(listener);
    }

    @Override
    public void onMessage(WebSocketConnection connection, String message) {
        // Clients ping regularly so idle connections are not dropped
        if ("ping".equals(message.trim())) {
            connection.send("{\"type\":\"pong\"}");
        }
    }

    @Override
    public void onClose(WebSocketConnection connection) {
        LogBuffer.Listener listener = listeners.remove(connection.getId());
        if (listener != null) {
            buffer.unsubscribe(listener);
        }
    }

    /** Called on module disable. The core closes the connections without calling onClose. */
    void shutdown() {
        for (LogBuffer.Listener listener : new ArrayList<>(listeners.values())) {
            buffer.unsubscribe(listener);
        }
        listeners.clear();
    }
}