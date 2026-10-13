package dev.mending.minedash.core.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class JsonFiles {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private JsonFiles() {
    }

    /** Reads a JSON file. Returns null if the file is empty. */
    public static <T> T read(Path file, Class<T> type) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        } catch (JsonParseException e) {
            throw new IOException("Invalid JSON in " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    /** Writes a JSON file via a temp file so a crash never leaves a half-written file. */
    public static void write(Path file, Object data) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, GSON.toJson(data).getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}