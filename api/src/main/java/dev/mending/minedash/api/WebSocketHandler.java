package dev.mending.minedash.api;

/** Called on web server threads, not on the server main thread. */
public interface WebSocketHandler {

    default void onConnect(WebSocketConnection connection) throws Exception {
    }

    default void onMessage(WebSocketConnection connection, String message) throws Exception {
    }

    default void onClose(WebSocketConnection connection) {
    }
}