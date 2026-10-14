package dev.mending.minedash.core.web;

import dev.mending.minedash.api.ApiUser;
import dev.mending.minedash.api.WebSocketConnection;
import io.javalin.websocket.WsContext;

final class WsConnection implements WebSocketConnection {

    private final WsContext ctx;
    private final ApiUser user;
    private volatile boolean open = true;

    WsConnection(WsContext ctx, ApiUser user) {
        this.ctx = ctx;
        this.user = user;
    }

    @Override
    public String getId() {
        return ctx.getSessionId();
    }

    @Override
    public ApiUser getUser() {
        return user;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public synchronized void send(String message) {
        if (open) {
            ctx.send(message);
        }
    }

    @Override
    public void close() {
        closeWith(1000, "Closed by server");
    }

    /** Closes with a specific code. The panel reacts to 4401 by returning to the login. */
    void closeWith(int code, String reason) {
        open = false;
        ctx.closeSession(code, reason);
    }

    void markClosed() {
        open = false;
    }
}