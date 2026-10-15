package dev.mending.minedash.core.web;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dev.mending.minedash.api.ApiException;
import dev.mending.minedash.api.ApiRequest;
import dev.mending.minedash.api.ApiUser;
import io.javalin.http.Context;

import java.io.InputStream;
import java.util.List;

final class ApiRequestImpl implements ApiRequest {

    private static final Gson GSON = new Gson();

    private final Context ctx;
    private final ApiUser user;
    private final String path;

    ApiRequestImpl(Context ctx, ApiUser user, String path) {
        this.ctx = ctx;
        this.user = user;
        this.path = path;
    }

    @Override
    public ApiUser getUser() {
        return user;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getQueryParam(String name) {
        return ctx.queryParam(name);
    }

    @Override
    public List<String> getQueryParams(String name) {
        return ctx.queryParams(name);
    }

    @Override
    public String getHeader(String name) {
        return ctx.header(name);
    }

    @Override
    public String getBody() {
        return ctx.body();
    }

    @Override
    public <T> T getBodyAs(Class<T> type) {
        T value;
        try {
            value = GSON.fromJson(ctx.body(), type);
        } catch (JsonParseException e) {
            throw new ApiException(400, "Invalid JSON");
        }
        if (value == null) {
            throw new ApiException(400, "Missing request body");
        }
        return value;
    }

    @Override
    public InputStream getBodyStream(long maxBytes) {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes must not be negative");
        }
        // Reject early when the client announces too much, so nothing is read at all
        if (declaredLength() > maxBytes) {
            throw new ApiException(413, "Payload too large");
        }
        return new LimitedInputStream(ctx.bodyAsInputStream(), maxBytes);
    }

    /** Value of the Content-Length header, or -1 if it is missing or invalid. */
    private long declaredLength() {
        String header = ctx.header("Content-Length");
        if (header == null) {
            return -1;
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}