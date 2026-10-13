package dev.mending.minedash.modules.console;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Pattern;

/** Log4j2 appender that copies every log event into the LogBuffer. */
final class LogAppender extends AbstractAppender {

    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]");
    private static final Pattern MC_COLOR = Pattern.compile("(?i)\u00A7[0-9A-FK-OR]");

    private final LogBuffer buffer;

    LogAppender(LogBuffer buffer) {
        super("MineDash-Console", null, null, true);
        this.buffer = buffer;
    }

    @Override
    public void append(LogEvent event) {
        try {
            // Read everything now, Log4j may reuse the event object afterwards
            String message = event.getMessage().getFormattedMessage();
            Throwable thrown = event.getThrown();
            if (thrown != null) {
                StringWriter writer = new StringWriter();
                thrown.printStackTrace(new PrintWriter(writer));
                message = message + "\n" + writer.toString().trim();
            }
            buffer.add(event.getLevel().name(), event.getLoggerName(), clean(message));
        } catch (RuntimeException ignored) {
            // Never log from here and never break server logging
        }
    }

    private static String clean(String message) {
        String result = ANSI.matcher(message).replaceAll("");
        return MC_COLOR.matcher(result).replaceAll("");
    }
}