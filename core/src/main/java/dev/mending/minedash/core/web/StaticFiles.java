package dev.mending.minedash.core.web;

import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Serves the frontend from the plugin jar (resources/web). */
final class StaticFiles {

    private static final String ROOT = "web/";
    private static final Map<String, String> MIME = new HashMap<>();

    static {
        MIME.put("html", "text/html; charset=utf-8");
        MIME.put("js", "text/javascript; charset=utf-8");
        MIME.put("mjs", "text/javascript; charset=utf-8");
        MIME.put("css", "text/css; charset=utf-8");
        MIME.put("json", "application/json");
        MIME.put("map", "application/json");
        MIME.put("svg", "image/svg+xml");
        MIME.put("png", "image/png");
        MIME.put("ico", "image/x-icon");
        MIME.put("woff2", "font/woff2");
    }

    private final ClassLoader loader = StaticFiles.class.getClassLoader();

    static String contentType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String type = dot < 0 ? null : MIME.get(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
        return type != null ? type : "application/octet-stream";
    }

    void handle(Context ctx) {
        String path = ctx.path();
        if (path.equals("/api") || path.startsWith("/api/")) {
            throw new NotFoundResponse();
        }

        String name = path.equals("/") ? "index.html" : path.substring(1);
        if (name.contains("..") || name.contains("\\")) {
            throw new NotFoundResponse();
        }

        InputStream in = loader.getResourceAsStream(ROOT + name);
        if (in == null) {
            // Missing file with extension = real 404, otherwise SPA fallback
            if (name.lastIndexOf('.') > name.lastIndexOf('/')) {
                throw new NotFoundResponse();
            }
            name = "index.html";
            in = loader.getResourceAsStream(ROOT + name);
            if (in == null) {
                throw new NotFoundResponse();
            }
        }

        ctx.contentType(contentType(name));

        // Vite emits hashed filenames in assets/, so those can be cached forever
        if (name.startsWith("assets/")) {
            ctx.header("Cache-Control", "public, max-age=31536000, immutable");
        } else {
            ctx.header("Cache-Control", "no-cache");
        }
        ctx.result(in);
    }
}