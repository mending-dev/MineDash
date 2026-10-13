package dev.mending.minedash.api;

public interface ApiRequest {

    ApiUser getUser();

    /** Path relative to the module, e.g. "/command". */
    String getPath();

    /** Returns null if the parameter is missing. */
    String getQueryParam(String name);

    String getBody();

    /** Parses the JSON body. Throws ApiException with status 400 if it is missing or invalid. */
    <T> T getBodyAs(Class<T> type);
}