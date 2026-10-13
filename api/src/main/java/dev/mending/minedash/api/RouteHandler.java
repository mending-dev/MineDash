package dev.mending.minedash.api;

@FunctionalInterface
public interface RouteHandler {

    /**
     * Returns the response body, which is serialized to JSON.
     * Returning null answers with 204 No Content.
     */
    Object handle(ApiRequest request) throws Exception;
}