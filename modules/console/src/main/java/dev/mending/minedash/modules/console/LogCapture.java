package dev.mending.minedash.modules.console;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;

/** Attaches and detaches the appender on the server's Log4j2 root logger. */
final class LogCapture {

    private final LogAppender appender;
    private Logger root;

    LogCapture(LogBuffer buffer) {
        this.appender = new LogAppender(buffer);
    }

    void start() {
        // Use the server's own class loader so we get the same context the server logs to
        LoggerContext context = (LoggerContext) LogManager.getContext(LogManager.class.getClassLoader(), false);
        root = context.getLogger(LogManager.ROOT_LOGGER_NAME);
        appender.start();
        root.addAppender(appender);
    }

    void stop() {
        if (root != null) {
            root.removeAppender(appender);
            root = null;
        }
        appender.stop();
    }
}