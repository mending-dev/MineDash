package dev.mending.minedash.modules.files;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/** Content of plugins/MineDash/data/files/config.json. Filled by Gson. */
final class FilesConfig {

    /** Folder the explorer manages. A relative path is resolved against the server folder. */
    String root = ".";

    /**
     * Paths relative to root that can never be listed, read, changed or archived.
     * Each entry also protects everything below it.
     */
    List<String> denyPaths = new ArrayList<>(Collections.singletonList("plugins/MineDash/users.json"));

    /** Largest accepted upload in bytes. */
    long maxUploadBytes = 256L * 1024 * 1024;

    /** Largest file that can be opened in the editor, in bytes. */
    long maxEditBytes = 2L * 1024 * 1024;

    /** Entries shown per folder, the rest is cut off. */
    int maxListEntries = 10000;

    /** Largest total size of an archive after extraction, in bytes. Protects against ZIP bombs. */
    long maxUnzipBytes = 10L * 1024 * 1024 * 1024;

    /** Largest number of entries when creating or extracting an archive. */
    int maxZipEntries = 100000;

    /** Fixes missing or out-of-range values and tells the admin about it. */
    void validate(Logger logger) {
        if (root == null || root.trim().isEmpty()) {
            root = ".";
        }
        if (denyPaths == null) {
            denyPaths = new ArrayList<>();
        }
        maxUploadBytes = clamp("maxUploadBytes", maxUploadBytes, 1024L, 64L * 1024 * 1024 * 1024, logger);
        maxEditBytes = clamp("maxEditBytes", maxEditBytes, 1024L, 16L * 1024 * 1024, logger);
        maxListEntries = (int) clamp("maxListEntries", maxListEntries, 100L, 100000L, logger);
        maxUnzipBytes = clamp("maxUnzipBytes", maxUnzipBytes, 1024L * 1024, 1024L * 1024 * 1024 * 1024, logger);
        maxZipEntries = (int) clamp("maxZipEntries", maxZipEntries, 100L, 1000000L, logger);
    }

    private static long clamp(String name, long value, long min, long max, Logger logger) {
        long clamped = Math.max(min, Math.min(max, value));
        if (clamped != value) {
            logger.warning("config.json: '" + name + "' is out of range, using " + clamped);
        }
        return clamped;
    }
}