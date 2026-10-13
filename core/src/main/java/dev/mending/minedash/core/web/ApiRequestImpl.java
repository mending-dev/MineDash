package dev.mending.minedash.core.web;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dev.mending.minedash.api.ApiException;
import dev.mending.minedash.api.ApiRequest;
import dev.mending.minedash.api.ApiUser;
import io.javalin.http.Context;

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
}