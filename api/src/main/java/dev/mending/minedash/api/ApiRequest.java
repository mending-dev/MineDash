package dev.mending.minedash.api;

import java.io.InputStream;
import java.util.List;

public interface ApiRequest {

    ApiUser getUser();

    /** Path relative to the module, e.g. "/command". */
    String getPath();

    /** Returns null if the parameter is missing. */
    String getQueryParam(String name);

    /** All values of a query parameter in order. Empty if the parameter is missing. */
    List<String> getQueryParams(String name);

    /** Returns null if the header is missing. */
    String getHeader(String name);

    /** The body as text. Limited to 1 MB, use getBodyStream for larger payloads. */
    String getBody();

    /** Parses the JSON body. Throws ApiException with status 400 if it is missing or invalid. */
    <T> T getBodyAs(Class<T> type);

    /**
     * Streams the raw request body, for uploads and large payloads.
     * Throws ApiException with status 413 as soon as the body is larger than maxBytes,
     * either immediately (Content-Length header) or while it is read.
     * The stream must be consumed inside the handler.
     */
    InputStream getBodyStream(long maxBytes);
}