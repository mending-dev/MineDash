package dev.mending.minedash.modules.files;

import dev.mending.minedash.api.ApiException;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Creates and extracts ZIP archives. */
final class ZipService {

    /** One entry of an archive that is going to be written. */
    static final class Item {
        final String entryName;
        /** Real path to read from. */
        final Path source;
        final boolean directory;
        final long modified;

        Item(String entryName, Path source, boolean directory, long modified) {
            this.entryName = entryName;
            this.source = source;
            this.directory = directory;
            this.modified = modified;
        }
    }

    /** Things that could not be added. Only the first ones are kept, the rest is counted. */
    static final class Skipped {
        private static final int MAX_LISTED = 1000;

        final List<String> listed = new ArrayList<>();
        int total;

        void add(String description) {
            total++;
            if (listed.size() < MAX_LISTED) {
                listed.add(display(description));
            }
        }
    }

    /** A prepared archive. Everything that can fail early has been checked. */
    static final class Archive {
        final List<Item> items;
        final Skipped skipped;
        final Set<String> topLevelNames;
        final String downloadName;

        Archive(List<Item> items, Skipped skipped, Set<String> topLevelNames, String downloadName) {
            this.items = items;
            this.skipped = skipped;
            this.topLevelNames = topLevelNames;
            this.downloadName = downloadName;
        }
    }

    static final class ArchiveResult {
        final int entries;
        final long size;
        final List<String> skipped;
        final int skippedTotal;

        ArchiveResult(int entries, long size, List<String> skipped, int skippedTotal) {
            this.entries = entries;
            this.size = size;
            this.skipped = skipped;
            this.skippedTotal = skippedTotal;
        }
    }

    static final class UnzipResult {
        final int entries;
        final long bytes;

        UnzipResult(int entries, long bytes) {
            this.entries = entries;
            this.bytes = bytes;
        }
    }

    private static final class Planned {
        final ZipEntry entry;
        final Path target;
        final boolean directory;

        Planned(ZipEntry entry, Path target, boolean directory) {
            this.entry = entry;
            this.target = target;
            this.directory = directory;
        }
    }

    private static final class Plan {
        final List<Planned> entries;
        final long declaredBytes;

        Plan(List<Planned> entries, long declaredBytes) {
            this.entries = entries;
            this.declaredBytes = declaredBytes;
        }
    }

    private static final class Pending {
        final Path directory;
        final String prefix;

        Pending(Path directory, String prefix) {
            this.directory = directory;
            this.prefix = prefix;
        }
    }

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAX_SELECTION = 1000;
    /** Archive jobs running at the same time. They compete with the game server for CPU and disk. */
    private static final int MAX_PARALLEL = 2;
    private static final String REPORT_NAME = "MINEDASH-SKIPPED.txt";
    /** Names in old archives can use a legacy encoding. ISO-8859-1 never fails, names just look odd. */
    private static final Charset[] CHARSETS = {StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1};

    private final PathGuard guard;
    private final FileService service;
    private final FilesConfig config;
    private final Logger logger;
    private final Semaphore permits = new Semaphore(MAX_PARALLEL);

    ZipService(PathGuard guard, FileService service, FilesConfig config, Logger logger) {
        this.guard = guard;
        this.service = service;
        this.config = config;
        this.logger = logger;
    }

    /** True if the path points to a folder. Used to decide between a file and a ZIP download. */
    boolean isDirectory(String path) {
        return Files.isDirectory(service.realOf(guard.resolve(path)));
    }

    // ---------------------------------------------------------------- creating

    /**
     * Collects the items of an archive and validates them. Throws ApiException for invalid paths
     * and when the limits are exceeded, so this can be called before the response starts.
     */
    Archive prepare(List<String> paths) {
        Set<String> unique = uniquePaths(paths);
        List<Item> items = new ArrayList<>();
        Skipped skipped = new Skipped();
        Set<String> topLevelNames = new HashSet<>();
        String lastName = null;

        for (String path : unique) {
            Path logical = guard.resolve(path);
            if (guard.isRoot(logical)) {
                throw new ApiException(400, "Select files or folders, not the root folder");
            }
            Path real = service.realOf(logical);
            if (!Files.exists(real)) {
                throw new ApiException(404, "File or folder not found");
            }

            // A link is archived under its own name
            String name = uniqueName(logical.getFileName().toString(), topLevelNames);
            lastName = name;
            try {
                if (Files.isDirectory(real)) {
                    addTree(real, name, items, skipped);
                } else if (Files.isRegularFile(real)) {
                    requireRoom(items);
                    items.add(new Item(name, real, false, Files.getLastModifiedTime(real).toMillis()));
                } else {
                    throw new ApiException(400, "Not a regular file");
                }
            } catch (IOException e) {
                throw translate(e);
            }
        }

        String downloadName = unique.size() == 1 ? lastName + ".zip" : "files.zip";
        return new Archive(items, skipped, topLevelNames, downloadName);
    }

    /** Streams a prepared archive to a download. Runs when the response is written. */
    void writeDownload(Archive archive, OutputStream out) throws IOException {
        acquire();
        try {
            write(archive, out);
        } finally {
            permits.release();
        }
    }

    /** Creates an archive file next to the other files. Never overwrites and never includes itself. */
    ArchiveResult createArchive(List<String> paths, String targetPath) {
        Path target = guard.resolve(targetPath == null ? "" : targetPath);
        if (guard.isRoot(target)) {
            throw new ApiException(400, "Invalid archive name");
        }
        if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new ApiException(400, "The archive name must end with .zip");
        }
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new ApiException(404, "The target folder does not exist");
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new ApiException(409, "A file or folder with this name already exists");
        }

        acquire();
        // The archive is collected before the temp file exists, so it cannot contain itself
        Path temp = parent.resolve(TempFiles.newName());
        boolean moved = false;
        try {
            Archive archive = prepare(paths);
            try (OutputStream out = Files.newOutputStream(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                write(archive, out);
            }
            Files.move(temp, target);
            moved = true;
            return new ArchiveResult(archive.items.size(), Files.size(target),
                    archive.skipped.listed, archive.skipped.total);
        } catch (IOException e) {
            throw translate(e);
        } finally {
            if (!moved) {
                deleteQuietly(temp);
            }
            permits.release();
        }
    }

    /** Writes the archive and closes the given stream. */
    private void write(Archive archive, OutputStream target) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(target, StandardCharsets.UTF_8);
        // Fast compression, the CPU is shared with the game server
        zip.setLevel(Deflater.BEST_SPEED);
        byte[] buffer = new byte[BUFFER_SIZE];

        for (Item item : archive.items) {
            if (item.directory) {
                putEntry(zip, item.entryName, item.modified);
                zip.closeEntry();
                continue;
            }

            InputStream in;
            try {
                in = Files.newInputStream(item.source);
            } catch (IOException e) {
                // Gone, locked or not readable: noted in the report instead of failing the whole archive
                archive.skipped.add(item.entryName + " (" + reason(e) + ")");
                continue;
            }
            try {
                putEntry(zip, item.entryName, item.modified);
                int read;
                while ((read = in.read(buffer)) != -1) {
                    zip.write(buffer, 0, read);
                }
                zip.closeEntry();
            } finally {
                closeQuietly(in);
            }
        }

        if (archive.skipped.total > 0) {
            writeReport(zip, archive);
        }
        zip.close();
    }

    private void writeReport(ZipOutputStream zip, Archive archive) throws IOException {
        StringBuilder text = new StringBuilder("MineDash could not add these items to the archive:\n\n");
        for (String line : archive.skipped.listed) {
            text.append("- ").append(line).append('\n');
        }
        int more = archive.skipped.total - archive.skipped.listed.size();
        if (more > 0) {
            text.append("... and ").append(more).append(" more\n");
        }

        putEntry(zip, uniqueName(REPORT_NAME, archive.topLevelNames), System.currentTimeMillis());
        zip.write(text.toString().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void putEntry(ZipOutputStream zip, String name, long modified) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(modified);
        zip.putNextEntry(entry);
    }

    /** Adds a folder and everything in it. Links are skipped, they are never followed. */
    private void addTree(Path realRoot, String rootName, List<Item> items, Skipped skipped) throws IOException {
        requireRoom(items);
        items.add(new Item(rootName + "/", realRoot, true, Files.getLastModifiedTime(realRoot).toMillis()));

        Deque<Pending> stack = new ArrayDeque<>();
        stack.push(new Pending(realRoot, rootName + "/"));

        while (!stack.isEmpty()) {
            Pending current = stack.pop();
            try (DirectoryStream<Path> children = Files.newDirectoryStream(current.directory)) {
                for (Path child : children) {
                    String name = child.getFileName().toString();
                    String entryPath = current.prefix + name;

                    // Protected files are left out without a trace
                    if (guard.isHidden(child)) {
                        continue;
                    }
                    if (!isSupportedName(name)) {
                        skipped.add(entryPath + " (unsupported name)");
                        continue;
                    }

                    BasicFileAttributes attributes;
                    try {
                        attributes = Files.readAttributes(child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    } catch (IOException e) {
                        skipped.add(entryPath + " (" + reason(e) + ")");
                        continue;
                    }

                    if (attributes.isSymbolicLink()) {
                        skipped.add(entryPath + " (symbolic link)");
                    } else if (attributes.isDirectory()) {
                        requireRoom(items);
                        items.add(new Item(entryPath + "/", child, true, attributes.lastModifiedTime().toMillis()));
                        stack.push(new Pending(child, entryPath + "/"));
                    } else if (attributes.isRegularFile()) {
                        requireRoom(items);
                        items.add(new Item(entryPath, child, false, attributes.lastModifiedTime().toMillis()));
                    } else {
                        skipped.add(entryPath + " (not a regular file)");
                    }
                }
            } catch (IOException | DirectoryIteratorException e) {
                skipped.add(current.prefix + " (folder cannot be read)");
            }
        }
    }

    private void requireRoom(List<Item> items) {
        if (items.size() >= config.maxZipEntries) {
            throw new ApiException(413, "Too many files for one archive, the limit is " + config.maxZipEntries);
        }
    }

    // ---------------------------------------------------------------- extracting

    /**
     * Extracts an archive into a folder, which is created if it does not exist.
     * Existing files are never overwritten. If anything fails, everything created is removed again.
     */
    UnzipResult unzip(String archivePath, String destination) {
        Path archive = service.regularFile(guard.resolve(archivePath));
        Path destinationDir = guard.resolve(destination == null ? "" : destination);

        if (Files.exists(destinationDir) && !Files.isDirectory(destinationDir)) {
            throw new ApiException(409, "The destination is not a folder");
        }
        Path parent = destinationDir.getParent();
        if (!Files.exists(destinationDir) && (parent == null || !Files.isDirectory(parent))) {
            throw new ApiException(404, "The parent of the destination folder does not exist");
        }

        acquire();
        try {
            return extract(archive, destinationDir);
        } finally {
            permits.release();
        }
    }

    private UnzipResult extract(Path archive, Path destinationDir) {
        String base = guard.toClientPath(destinationDir);
        ZipFile zip = null;
        try {
            Plan plan = null;
            for (int attempt = 0; plan == null && attempt < CHARSETS.length; attempt++) {
                try {
                    zip = new ZipFile(archive.toFile(), CHARSETS[attempt]);
                    plan = plan(zip, base);
                } catch (IllegalArgumentException | ZipException e) {
                    // Entry names that are not valid in this encoding, try the next one
                    closeQuietly(zip);
                    zip = null;
                }
            }
            if (plan == null) {
                throw new ApiException(400, "The file is not a valid ZIP archive");
            }

            checkSpace(destinationDir, plan.declaredBytes);
            return execute(zip, plan.entries);
        } catch (ZipException e) {
            throw new ApiException(400, "The archive is damaged or uses an unsupported feature, such as a password");
        } catch (IOException e) {
            throw translate(e);
        } finally {
            closeQuietly(zip);
        }
    }

    /** Validates every entry and checks for conflicts. Nothing is written yet. */
    private Plan plan(ZipFile zip, String base) {
        if (zip.size() > config.maxZipEntries) {
            throw new ApiException(413, "The archive has too many entries, the limit is " + config.maxZipEntries);
        }

        List<Planned> planned = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<String> conflicts = new ArrayList<>();
        int conflictTotal = 0;
        long declared = 0;

        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            List<String> names = entryNames(entry.getName());
            if (names.isEmpty()) {
                continue;
            }
            String relative = String.join("/", names);
            boolean directory = entry.isDirectory();
            if (!seen.add(relative)) {
                throw new ApiException(400, "The archive contains the same path twice: " + display(relative));
            }

            Path target;
            try {
                // Also checks that the real path stays in the root and is not protected
                target = guard.resolve(base + "/" + relative);
            } catch (ApiException e) {
                throw new ApiException(e.getStatus(), e.getMessage() + ": " + display(relative));
            }

            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !(directory && Files.isDirectory(target))) {
                conflictTotal++;
                if (conflicts.size() < 3) {
                    conflicts.add(display(relative));
                }
            }

            if (!directory && entry.getSize() > 0) {
                declared += entry.getSize();
                if (declared > config.maxUnzipBytes) {
                    throw new ApiException(413, "The archive is too large once extracted, the limit is "
                            + config.maxUnzipBytes + " bytes");
                }
            }
            planned.add(new Planned(entry, target, directory));
        }

        if (planned.isEmpty()) {
            throw new ApiException(400, "The archive is empty");
        }
        if (conflictTotal > 0) {
            String more = conflictTotal > conflicts.size() ? " and " + (conflictTotal - conflicts.size()) + " more" : "";
            throw new ApiException(409, "These already exist: " + String.join(", ", conflicts) + more
                    + ". Delete or rename them first.");
        }
        return new Plan(planned, declared);
    }

    private UnzipResult execute(ZipFile zip, List<Planned> plan) throws IOException {
        List<Path> createdFiles = new ArrayList<>();
        List<Path> createdDirectories = new ArrayList<>();
        long total = 0;

        try {
            for (Planned item : plan) {
                if (item.directory) {
                    createDirectories(item.target, createdDirectories);
                    continue;
                }
                createDirectories(item.target.getParent(), createdDirectories);
                total = extractFile(zip, item, createdFiles, total);
            }
            return new UnzipResult(plan.size(), total);
        } catch (IOException | RuntimeException e) {
            rollback(createdFiles, createdDirectories);
            throw e;
        }
    }

    /** Returns the new total of extracted bytes. The sizes in the archive may lie, so real bytes are counted. */
    private long extractFile(ZipFile zip, Planned item, List<Path> createdFiles, long totalSoFar) throws IOException {
        long total = totalSoFar;
        byte[] buffer = new byte[BUFFER_SIZE];

        try (InputStream in = zip.getInputStream(item.entry);
             OutputStream out = Files.newOutputStream(item.target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            // Only recorded after CREATE_NEW succeeded, a file that already existed must never be deleted
            createdFiles.add(item.target);

            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > config.maxUnzipBytes) {
                    throw new ApiException(413, "The archive is too large once extracted, the limit is "
                            + config.maxUnzipBytes + " bytes");
                }
                out.write(buffer, 0, read);
            }
        }

        if (item.entry.getTime() >= 0) {
            try {
                Files.setLastModifiedTime(item.target, FileTime.fromMillis(item.entry.getTime()));
            } catch (IOException ignored) {
                // The date is cosmetic
            }
        }
        return total;
    }

    /** Creates a folder with all missing parents and remembers which ones are new. */
    private static void createDirectories(Path directory, List<Path> created) throws IOException {
        Deque<Path> missing = new ArrayDeque<>();
        Path current = directory;
        while (current != null && !Files.exists(current)) {
            missing.push(current);
            current = current.getParent();
        }
        // push() puts the topmost missing folder first
        for (Path path : missing) {
            Files.createDirectory(path);
            created.add(path);
        }
    }

    private static void rollback(List<Path> files, List<Path> directories) {
        for (Path file : files) {
            deleteQuietly(file);
        }
        // Folders were created top down, so deleting backwards removes the deepest first
        for (int i = directories.size() - 1; i >= 0; i--) {
            deleteQuietly(directories.get(i));
        }
    }

    private static void checkSpace(Path destinationDir, long needed) {
        if (needed <= 0) {
            return;
        }
        Path existing = destinationDir;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return;
        }
        try {
            if (Files.getFileStore(existing).getUsableSpace() < needed) {
                throw new ApiException(507, "Not enough free disk space for this archive");
            }
        } catch (IOException ignored) {
            // The free space cannot be determined, so extraction simply goes ahead
        }
    }

    /**
     * Turns an entry name of the archive into validated path segments. Backslashes count as
     * separators (archives made on Windows), "." and empty segments are dropped, and ".." fails.
     */
    private static List<String> entryNames(String raw) {
        StringBuilder kept = new StringBuilder();
        for (String part : raw.replace('\\', '/').split("/")) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (kept.length() > 0) {
                kept.append('/');
            }
            kept.append(part);
        }
        try {
            return PathGuard.parse(kept.toString());
        } catch (ApiException e) {
            throw new ApiException(400, "Invalid entry name in the archive: " + display(raw) + " (" + e.getMessage() + ")");
        }
    }

    // ---------------------------------------------------------------- helpers

    private void acquire() {
        if (!permits.tryAcquire()) {
            throw new ApiException(429, "Too many archive operations are running, try again in a moment");
        }
    }

    private ApiException translate(IOException e) {
        String message = e.getMessage();
        if (message != null && message.toLowerCase(Locale.ROOT).contains("no space left")) {
            return new ApiException(507, "Not enough free disk space");
        }
        return FileErrors.from(e, logger);
    }

    private static Set<String> uniquePaths(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            throw new ApiException(400, "No items selected");
        }
        if (paths.size() > MAX_SELECTION) {
            throw new ApiException(400, "Too many items, the limit is " + MAX_SELECTION);
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String path : paths) {
            if (path == null) {
                throw new ApiException(400, "Invalid path");
            }
            unique.add(path);
        }
        return unique;
    }

    /** Adds " (2)", " (3)" and so on before the extension until the name is unused. */
    private static String uniqueName(String base, Set<String> used) {
        if (used.add(base)) {
            return base;
        }
        int dot = base.lastIndexOf('.');
        String stem = dot > 0 ? base.substring(0, dot) : base;
        String extension = dot > 0 ? base.substring(dot) : "";
        for (int i = 2; ; i++) {
            String candidate = stem + " (" + i + ")" + extension;
            if (used.add(candidate)) {
                return candidate;
            }
        }
    }

    private static boolean isSupportedName(String name) {
        try {
            PathGuard.checkName(name);
            return true;
        } catch (ApiException e) {
            return false;
        }
    }

    private static String reason(IOException e) {
        if (e instanceof NoSuchFileException) {
            return "no longer exists";
        }
        if (e instanceof AccessDeniedException) {
            return "access denied";
        }
        return "cannot be read";
    }

    /** Text for messages: no control characters and not endless. */
    private static String display(String text) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length() && i < 200; i++) {
            char c = text.charAt(i);
            result.append(c < 0x20 || c == 0x7F ? '?' : c);
        }
        return result.toString();
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Folders that are not empty stay, nothing else we can do
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Nothing we can do
        }
    }
}