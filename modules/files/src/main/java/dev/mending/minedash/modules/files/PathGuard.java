package dev.mending.minedash.modules.files;

import dev.mending.minedash.api.ApiException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Turns paths from the browser into safe paths inside the managed folder.
 * Everything that touches the file system must get its paths from here.
 *
 * Rules: no ".." and no backslashes, the real path (after following links) must stay inside
 * the root, and paths on the deny list are never accessible.
 */
final class PathGuard {

    private static final boolean WINDOWS = File.separatorChar == '\\';
    private static final int MAX_PATH_LENGTH = 4096;
    private static final int MAX_SEGMENTS = 64;
    private static final int MAX_NAME_LENGTH = 255;
    private static final String WINDOWS_INVALID_CHARS = "<>:\"|?*";
    private static final Set<String> WINDOWS_RESERVED = new HashSet<>(Arrays.asList(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"));

    private static final class DenyEntry {
        /** Absolute path of the entry. It does not have to exist. */
        final Path path;
        /** Lower case names relative to the root. */
        final List<String> names;

        DenyEntry(Path path, List<String> names) {
            this.path = path;
            this.names = names;
        }
    }

    /** The real path of the root, so every comparison works on resolved paths. */
    private final Path root;
    private final List<DenyEntry> deny;

    PathGuard(Path root, List<String> denyPaths, Logger logger) throws IOException {
        this.root = root.toRealPath();

        List<DenyEntry> entries = new ArrayList<>();
        for (String raw : denyPaths) {
            if (raw == null) {
                continue;
            }
            try {
                List<String> names = parse(raw);
                if (names.isEmpty()) {
                    logger.warning("Ignoring denyPaths entry '" + raw + "': it would block everything");
                    continue;
                }
                Path path = this.root;
                List<String> lower = new ArrayList<>();
                for (String name : names) {
                    path = path.resolve(name);
                    lower.add(name.toLowerCase(Locale.ROOT));
                }
                entries.add(new DenyEntry(path, lower));
            } catch (ApiException | InvalidPathException e) {
                logger.warning("Ignoring invalid denyPaths entry '" + raw + "': " + e.getMessage());
            }
        }
        this.deny = Collections.unmodifiableList(entries);
    }

    /** The real path of the managed folder. */
    Path getRoot() {
        return root;
    }

    boolean isRoot(Path path) {
        return path.equals(root);
    }

    /**
     * Resolves a path sent by the browser, relative to the root and separated by "/".
     * The result may not exist yet. Throws ApiException with status 400 for invalid paths
     * and 403 for paths outside of the root or on the deny list.
     */
    Path resolve(String relative) {
        List<String> names = parse(relative);

        Path logical = root;
        try {
            for (String name : names) {
                logical = logical.resolve(name);
            }
        } catch (InvalidPathException e) {
            throw bad("Invalid path");
        }

        Path real;
        try {
            real = real(logical);
        } catch (IOException e) {
            throw new ApiException(403, "Broken or unreadable link");
        }
        if (!real.startsWith(root)) {
            throw new ApiException(403, "Path is outside of the managed folder");
        }
        // Check both, so a link cannot be used to reach a protected path
        if (isDenied(logical) || isDenied(real)) {
            throw new ApiException(403, "Access to this path is not allowed");
        }
        // Operations use the logical path, so deleting a link deletes the link and not its target
        return logical;
    }

    /**
     * The real path after following all links. Works for paths that do not exist yet by
     * resolving the closest existing parent. Throws IOException for broken links.
     */
    Path real(Path path) throws IOException {
        Path existing = path;
        List<Path> missing = new ArrayList<>();
        // A broken link still exists without following it, so it fails in toRealPath() below
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            missing.add(existing.getFileName());
            existing = existing.getParent();
        }
        if (existing == null) {
            throw new IOException("No existing parent");
        }

        Path real = existing.toRealPath();
        for (int i = missing.size() - 1; i >= 0; i--) {
            real = real.resolve(missing.get(i));
        }
        return real;
    }

    /**
     * Quick check by name only, without file access. Used to hide entries in listings.
     * Paths outside of the root count as hidden.
     */
    boolean isHidden(Path absolute) {
        if (!absolute.startsWith(root)) {
            return true;
        }
        List<String> names = relativeNames(absolute, true);
        for (DenyEntry entry : deny) {
            if (startsWith(names, entry.names)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Full check: by name, and by file identity for paths that exist. The identity check catches
     * hard links and spelling variants on case-insensitive file systems.
     */
    boolean isDenied(Path absolute) {
        if (isHidden(absolute)) {
            return true;
        }
        if (!Files.exists(absolute)) {
            return false;
        }
        for (DenyEntry entry : deny) {
            try {
                if (Files.exists(entry.path) && Files.isSameFile(absolute, entry.path)) {
                    return true;
                }
            } catch (IOException e) {
                // Cannot compare, so play it safe
                return true;
            }
        }
        return false;
    }

    /**
     * True if a protected path lies inside the given folder. Such folders must not be deleted,
     * renamed or moved, because that would remove or expose the protected path.
     */
    boolean containsDenied(Path directory) {
        for (DenyEntry entry : deny) {
            if (entry.path.startsWith(directory)) {
                return true;
            }
            try {
                if (real(entry.path).startsWith(real(directory))) {
                    return true;
                }
            } catch (IOException e) {
                return true;
            }
        }
        return false;
    }

    /** Path as the browser sees it: relative to the root, separated by "/". The root itself is "". */
    String toClientPath(Path absolute) {
        return String.join("/", relativeNames(absolute, false));
    }

    // ---------------------------------------------------------------- parsing

    /** Splits and validates a path from the browser. Throws ApiException with status 400. */
    static List<String> parse(String relative) {
        String value = relative == null ? "" : relative;
        if (value.length() > MAX_PATH_LENGTH) {
            throw bad("Path is too long");
        }

        List<String> names = new ArrayList<>();
        for (String part : value.split("/")) {
            // Leading, trailing and doubled slashes carry no meaning
            if (part.isEmpty()) {
                continue;
            }
            checkName(part);
            names.add(part);
        }
        if (names.size() > MAX_SEGMENTS) {
            throw bad("Path is too deep");
        }
        return names;
    }

    /** Validates a single file or folder name. */
    static void checkName(String name) {
        if (name.equals(".") || name.equals("..")) {
            throw bad("'.' and '..' are not allowed in paths");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw bad("Name is too long");
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c == 0x7F || c == '\\') {
                throw bad("Invalid character in name");
            }
        }

        if (WINDOWS) {
            for (int i = 0; i < name.length(); i++) {
                if (WINDOWS_INVALID_CHARS.indexOf(name.charAt(i)) >= 0) {
                    throw bad("Invalid character in name");
                }
            }
            // Windows drops trailing dots and spaces, so "users.json." would reach "users.json"
            if (name.endsWith(".") || name.endsWith(" ")) {
                throw bad("Names must not end with a dot or a space");
            }
            int dot = name.indexOf('.');
            String base = (dot < 0 ? name : name.substring(0, dot)).trim().toUpperCase(Locale.ROOT);
            if (WINDOWS_RESERVED.contains(base)) {
                throw bad("Reserved name");
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private List<String> relativeNames(Path absolute, boolean lowerCase) {
        List<String> names = new ArrayList<>();
        if (absolute.equals(root)) {
            return names;
        }
        for (Path part : root.relativize(absolute)) {
            String name = part.toString();
            names.add(lowerCase ? name.toLowerCase(Locale.ROOT) : name);
        }
        return names;
    }

    private static boolean startsWith(List<String> names, List<String> prefix) {
        if (names.size() < prefix.size()) {
            return false;
        }
        for (int i = 0; i < prefix.size(); i++) {
            if (!names.get(i).equals(prefix.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static ApiException bad(String message) {
        return new ApiException(400, message);
    }
}