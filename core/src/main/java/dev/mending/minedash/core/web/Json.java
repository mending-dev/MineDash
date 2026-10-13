package dev.mending.minedash.core.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;

public final class Json {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private Json() {
    }

    public static void respond(Context ctx, Object body) {
        ctx.contentType("application/json").result(GSON.toJson(body));
    }

    public static <T> T read(Context ctx, Class<T> type) {
        T value;
        try {
            value = GSON.fromJson(ctx.body(), type);
        } catch (JsonParseException e) {
            throw new BadRequestResponse("Invalid JSON");
        }
        if (value == null) {
            throw new BadRequestResponse("Missing request body");
        }
        return value;
    }
}