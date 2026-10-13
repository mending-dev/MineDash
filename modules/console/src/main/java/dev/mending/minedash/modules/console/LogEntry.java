package dev.mending.minedash.modules.console;

/** One console line. Serialized to JSON as is. */
final class LogEntry {

    final long id;
    final long time;
    final String level;
    final String logger;
    final String message;

    LogEntry(long id, long time, String level, String logger, String message) {
        this.id = id;
        this.time = time;
        this.level = level;
        this.logger = logger;
        this.message = message;
    }
}