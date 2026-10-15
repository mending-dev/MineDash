package dev.mending.minedash.modules.files;

import dev.mending.minedash.api.ApiException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/** Operations that change files. All paths come from the PathGuard. */
final class FileOperations {

    static final class Version {
        final String version;
        final long size;
        final long modified;

        Version(String version, long size, long modified) {
            this.version = version;
            this.size = size;
            this.modified = modified;
        }
    }

    static final class Failure {
        final String path;
        final String error;

        Failure(String path, String error) {
            this.path = path;
            this.error = error;
        }
    }

    /** Result of an action on several items. Some may succeed while others fail. */
    static final class BatchResult {
        final List<String> done = new ArrayList<>();
        final List<Failure> failed = new ArrayList<>();
    }

    private static final int MAX_BATCH = 1000;
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final String HEX = "0123456789abcdef";

    private final PathGuard guard;
    private final FileService service;
    private final FilesConfig config;
    private final Logger logger;
    private final SecureRandom random = new SecureRandom();

    FileOperations(PathGuard guard, FileService service, FilesConfig config, Logger logger) {
        this.guard = guard;
        this.service = service;
        this.config = config;
        this.logger = logger;
    }

    /**
     * Saves text from the editor. Fails with 409 if the file changed since it was opened,
     * which is detected by comparing the hash of the current content with expectedVersion.
     * Another process can still change the file between this check and the replacement.
     */
    Version write(String path, InputStream body, String expectedVersion) {
        if (expectedVersion == null || expectedVersion.isEmpty()) {
            throw new ApiException(400, "Missing 'version' parameter");
        }
        Path logical = guard.resolve(path);
        // Follow links, so the link itself stays in place
        Path target = service.realOf(logical);
        if (!Files.exists(target)) {
            throw new ApiException(409, "The file no longer exists");
        }
        if (!Files.isRegularFile(target)) {
            throw new ApiException(400, "Not a regular file");
        }

        try {
            // The editor only opens files up to this size, so a bigger file has been replaced
            if (Files.size(target) > config.maxEditBytes || !expectedVersion.equals(FileVersions.of(target))) {
                throw new ApiException(409, "The file was changed by someone else. Reload it before saving.");
            }
            MessageDigest digest = FileVersions.newDigest();
            long size = store(target, body, digest, true);
            return new Version(FileVersions.hex(digest), size, Files.getLastModifiedTime(target).toMillis());
        } catch (IOException e) {
            throw FileErrors.from(e, logger);
        }
    }

    /** Stores an uploaded stream as a file. Returns the number of bytes written. */
    long upload(String path, InputStream body, boolean overwrite) {
        Path logical = guard.resolve(path);
        requireNotRoot(logical);

        Path parent = logical.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new ApiException(404, "The target folder does not exist");
        }

        Path target = logical;
        boolean replace = false;
        if (Files.exists(logical, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isDirectory(logical)) {
                throw new ApiException(409, "A folder with this name already exists");
            }
            if (!overwrite) {
                throw new ApiException(409, "A file with this name already exists");
            }
            // Write through links, so the link itself stays in place
            target = service.regularFile(logical);
            replace = true;
        }
        return store(target, body, null, replace);
    }

    void createFile(String path) {
        create(path, false);
    }

    void createFolder(String path) {
        create(path, true);
    }

    void rename(String path, String newName) {
        if (newName == null || newName.isEmpty() || newName.indexOf('/') >= 0) {
            throw new ApiException(400, "Invalid name");
        }
        PathGuard.checkName(newName);

        Path source = guard.resolve(path);
        requireNotRoot(source);
        requireExists(source);
        requireNoProtectedContent(source);

        Path target = guard.resolve(guard.toClientPath(source.getParent()) + "/" + newName);
        try {
            if (isCaseOnlyChange(source, target)) {
                // On case-insensitive file systems the target "exists" because it is the source itself
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            } else {
                Files.move(source, target);
            }
        } catch (IOException e) {
            throw FileErrors.from(e, logger);
        }
    }

    BatchResult move(List<String> paths, String destination) {
        List<String> unique = checkBatch(paths);
        Path destinationDir = guard.resolve(destination == null ? "" : destination);
        Path realDestination = service.realOf(destinationDir);
        if (!Files.isDirectory(realDestination)) {
            throw new ApiException(404, "The destination folder does not exist");
        }

        BatchResult result = new BatchResult();
        for (String path : unique) {
            try {
                moveOne(path, destinationDir, realDestination);
                result.done.add(path);
            } catch (ApiException e) {
                result.failed.add(new Failure(path, e.getMessage()));
            } catch (IOException e) {
                result.failed.add(new Failure(path, FileErrors.from(e, logger).getMessage()));
            }
        }
        return result;
    }

    BatchResult delete(List<String> paths) {
        List<String> unique = checkBatch(paths);

        BatchResult result = new BatchResult();
        for (String path : unique) {
            try {
                deleteOne(path);
                result.done.add(path);
            } catch (ApiException e) {
                result.failed.add(new Failure(path, e.getMessage()));
            } catch (IOException e) {
                result.failed.add(new Failure(path, FileErrors.from(e, logger).getMessage()));
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- single steps

    private void create(String path, boolean folder) {
        Path target = guard.resolve(path);
        requireNotRoot(target);

        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new ApiException(404, "The parent folder does not exist");
        }
        try {
            if (folder) {
                Files.createDirectory(target);
            } else {
                Files.createFile(target);
            }
        } catch (IOException e) {
            throw FileErrors.from(e, logger);
        }
    }

    private void moveOne(String path, Path destinationDir, Path realDestination) throws IOException {
        Path source = guard.resolve(path);
        requireNotRoot(source);
        requireExists(source);
        requireNoProtectedContent(source);

        boolean realFolder = Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS);
        if (realFolder && realDestination.startsWith(service.realOf(source))) {
            throw new ApiException(400, "A folder cannot be moved into itself");
        }

        Path target = guard.resolve(guard.toClientPath(destinationDir) + "/" + source.getFileName());
        if (target.equals(source)) {
            throw new ApiException(409, "Already in this folder");
        }
        try {
            // A link is moved as a link, never its target
            Files.move(source, target);
        } catch (DirectoryNotEmptyException e) {
            // Thrown for folders that would have to be copied to another drive
            throw new ApiException(409, "This folder cannot be moved to another drive");
        }
    }

    private void deleteOne(String path) throws IOException {
        Path target = guard.resolve(path);
        requireNotRoot(target);
        requireExists(target);

        if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            requireNoProtectedContent(target);
            deleteTree(target);
        } else {
            // Also covers links: deleting a link never touches its target
            Files.delete(target);
        }
    }

    /** Deletes a folder with everything in it. Links are removed, never followed. */
    private static void deleteTree(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) {
                    throw error;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // ---------------------------------------------------------------- writing

    /**
     * Writes the stream to a temporary file next to the target and moves it into place, so a
     * failed or interrupted upload never leaves a half written target. Returns the bytes written.
     */
    private long store(Path target, InputStream body, MessageDigest digest, boolean replace) {
        Path temp = target.getParent().resolve(tempName());
        boolean moved = false;
        try {
            long size = copy(body, temp, digest);
            if (replace) {
                // Replacing a file would reset its permissions, e.g. the execute bit of start.sh
                copyPermissions(target, temp);
                moveReplacing(temp, target);
            } else {
                // Fails if a file with this name appeared in the meantime
                Files.move(temp, target);
            }
            moved = true;
            return size;
        } catch (IOException e) {
            throw FileErrors.from(e, logger);
        } finally {
            if (!moved) {
                deleteQuietly(temp);
            }
        }
    }

    private long copy(InputStream in, Path temp, MessageDigest digest) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0;
        try (OutputStream out = Files.newOutputStream(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            while (true) {
                int read;
                try {
                    read = in.read(buffer);
                } catch (IOException e) {
                    // The client went away. This is no problem of our file system.
                    throw new ApiException(400, "The upload was interrupted");
                }
                if (read == -1) {
                    break;
                }
                if (digest != null) {
                    digest.update(buffer, 0, read);
                }
                out.write(buffer, 0, read);
                total += read;
            }
        }
        return total;
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void copyPermissions(Path from, Path to) {
        try {
            Files.setPosixFilePermissions(to, Files.getPosixFilePermissions(from));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Not a POSIX file system, e.g. Windows
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Nothing we can do
        }
    }

    private String tempName() {
        byte[] bytes = new byte[4];
        random.nextBytes(bytes);
        StringBuilder name = new StringBuilder(".minedash-");
        for (byte b : bytes) {
            name.append(HEX.charAt((b >> 4) & 0xF)).append(HEX.charAt(b & 0xF));
        }
        return name.append(".tmp").toString();
    }

    // ---------------------------------------------------------------- checks

    private void requireNotRoot(Path path) {
        if (guard.isRoot(path)) {
            throw new ApiException(400, "The root folder cannot be changed");
        }
    }

    private static void requireExists(Path path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new ApiException(404, "File or folder not found");
        }
    }

    /** Folders that contain a protected path must not be renamed, moved or deleted. Links are fine. */
    private void requireNoProtectedContent(Path path) {
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && guard.containsDenied(path)) {
            throw new ApiException(403, "The folder contains protected files");
        }
    }

    private static boolean isCaseOnlyChange(Path source, Path target) throws IOException {
        return !source.equals(target)
                && source.getParent().equals(target.getParent())
                && source.getFileName().toString().equalsIgnoreCase(target.getFileName().toString())
                && Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && Files.isSameFile(source, target);
    }

    private static List<String> checkBatch(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            throw new ApiException(400, "No items selected");
        }
        if (paths.size() > MAX_BATCH) {
            throw new ApiException(400, "Too many items, the limit is " + MAX_BATCH);
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String path : paths) {
            if (path == null) {
                throw new ApiException(400, "Invalid path");
            }
            unique.add(path);
        }
        return new ArrayList<>(unique);
    }
}