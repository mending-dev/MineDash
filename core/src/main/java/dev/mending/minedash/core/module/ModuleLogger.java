package dev.mending.minedash.core.module;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Prefixes every message with the module id and forwards it to the plugin logger. */
final class ModuleLogger extends Logger {

    private final Logger parent;
    private final String prefix;

    ModuleLogger(Logger parent, String moduleId) {
        super("MineDash/" + moduleId, null);
        this.parent = parent;
        this.prefix = "[" + moduleId + "] ";
        setLevel(Level.ALL);
    }

    @Override
    public void log(LogRecord record) {
        record.setMessage(prefix + record.getMessage());
        parent.log(record);
    }
}