package dev.mending.minedash.modules.console;

import dev.mending.minedash.api.ApiException;
import dev.mending.minedash.api.MineDashModule;
import dev.mending.minedash.api.ModuleContext;
import org.bukkit.Bukkit;

public class ConsoleModule implements MineDashModule {

    private static final int HISTORY_SIZE = 1000;
    private static final int MAX_COMMAND_LENGTH = 1000;

    private static final class CommandRequest {
        String command;
    }

    private LogCapture capture;
    private LogStream stream;

    @Override
    public void onEnable(final ModuleContext context) {
        LogBuffer buffer = new LogBuffer(HISTORY_SIZE);
        capture = new LogCapture(buffer);
        stream = new LogStream(context, buffer);

        context.webSocket("logs", "console.view", stream);

        context.post("/command", "console.execute", request -> {
            CommandRequest body = request.getBodyAs(CommandRequest.class);
            String raw = body.command == null ? "" : body.command.trim();
            if (raw.startsWith("/")) {
                raw = raw.substring(1).trim();
            }
            if (raw.isEmpty() || raw.length() > MAX_COMMAND_LENGTH
                    || raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0) {
                throw new ApiException(400, "Invalid command");
            }

            final String command = raw;
            // Audit trail: shows up in the console and in latest.log
            context.getLogger().info(request.getUser().getUsername() + " executed: " + command);
            // Bukkit commands must run on the main thread
            context.runSync(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
            return null;
        });

        // Start capturing last, so a failure above never leaves an appender behind
        capture.start();
        context.getLogger().info("Console module enabled.");
    }

    @Override
    public void onDisable() {
        if (capture != null) {
            capture.stop();
        }
        if (stream != null) {
            stream.shutdown();
        }
    }
}