package dev.mending.minedash.api;

public interface WebSocketConnection {

    String getId();

    ApiUser getUser();

    boolean isOpen();

    /** Thread-safe. Does nothing if the connection is closed. */
    void send(String message);

    void close();
}