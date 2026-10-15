package dev.mending.minedash.modules.files;

import dev.mending.minedash.api.ApiException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/** Read-only file operations: listing, reading and opening files for download. */
final class FileService {

    static final class Entry {
        final String name;
        final boolean directory;
        /** True if the entry is a symbolic link. Directory and size describe its target. */
        final boolean link;
        final long size;
        final long modified;

        Entry(String name, boolean directory, boolean link, long size, long modified) {
            this.name = name;
            this.directory = directory;
            this.link = link;
            this.size = size;
            this.modified = modified;
        }
    }

    static final class Listing {
        final String path;
        final List<Entry> entries;
        /** True if the folder has more entries than maxListEntries. */
        final boolean truncated;

        Listing(String path, List<Entry> entries, boolean truncated) {
            this.path = path;
            this.entries = entries;
            this.truncated = truncated;
        }
    }

    static final class FileContent {
        final String path;
        final String content;
        /** Hash of the content. It must be sent back when saving. */
        final String version;
        final long size;
        final long modified;

        FileContent(String path, String content, String version, long size, long modified) {
            this.path = path;
            this.content = content;
            this.version = version;
            this.size = size;
            this.modified = modified;
        }
    }

    static final class Download {
        final InputStream stream;
        final long size;
        final String name;

        Download(InputStream stream, long size, String name) {
            this.stream = stream;
            this.size = size;
            this.name = name;
        }
    }

    /** Folders first, then by name without regard to case. */
    private static final Comparator<Entry> ORDER = new Comparator<Entry>() {
        @Override
        public int compare(Entry a, Entry b) {
            if (a.directory != b.directory) {
                return a.directory ? -1 : 1;
            }
            return String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name);
        }
    };

    private static final int BUFFER_SIZE = 64 * 1024;

    private final PathGuard guard;
    private final FilesConfig config;
    private final Logger logger;

    FileService(PathGuard guard, FilesConfig config, Logger logger) {
        this.guard = guard;
        this.config = config;
        this.logger = logger;
    }

    Listing list(String path) {
        Path directory = guard.resolve(path);
        Path realDirectory = realOf(directory);
        if (!Files.exists(realDirectory)) {
            throw new ApiException(404, "Folder not found");
        }
        if (!Files.isDirectory(realDirectory)) {
            throw new ApiException(400, "Not a folder");
        }

        List<Entry> entries = new ArrayList<>();
        boolean truncated = false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path child : stream) {
                if (entries.size() >= config.maxListEntries) {
                    truncated = true;
                    break;
                }
                Entry entry = describe(child, realDirectory);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        } catch (DirectoryIteratorException e) {
            throw FileErrors.from(e.getCause(), logger);
        } catch (IOException e) {
            throw FileErrors.from(e, logger);
        }

        Collections.sort(entries, ORDER);
        return new Listing(guard.toClientPath(directory), entries, truncated);
    }

    FileContent read(String path) {
        Path logical = guard.resolve(path);
        Path real = regularFile(logical);
        try {
            if (Files.size(real) > config.maxEditBytes) {
                throw tooLarge();
            }
            byte[] bytes = readLimited(real, config.maxEditBytes);
            for (byte b : bytes) {
                if (b == 0) {
                    throw new ApiException(415, "Binary files cannot be edited");
                }
            }
            String text = decode(bytes);
            long modified = Files.getLastModifiedTime(real).toMillis();
            return new FileContent(guard.toClientPath(logical), text, FileVersions.of(bytes), bytes.length, modified);
        } catch (IOException e) {
            throw FileErrors.from(e, logger);
        }
    }

    /** Opens a file for download. The caller has to close the stream. */
    Download openDownload(String path) {
        Path logical = guard.resolve(path);
        Path real = regularFile(logical);
        try {
            InputStream in = Files.newInputStream(real);
            try {
                // The link name is used as file name, not the name of its target
                return new Download(in, Files.size(real), logical.getFileName().toString());
            } catch (IOException | RuntimeException e) {
                closeQuietly(in);
                throw e;
            }
        } catch (IOException e) {
            throw FileErrors.from(e, logger);
        }
    }

    /** Real path after following all links. */
    Path realOf(Path logical) {
        try {
            return guard.real(logical);
        } catch (IOException e) {
            throw FileErrors.from(e, logger);
        }
    }

    /** Real path of an existing regular file. Throws 404 if it is missing and 400 if it is not a file. */
    Path regularFile(Path logical) {
        Path real = realOf(logical);
        if (!Files.exists(real)) {
            throw new ApiException(404, "File not found");
        }
        if (!Files.isRegularFile(real)) {
            throw new ApiException(400, "Not a regular file");
        }
        return real;
    }

    // ---------------------------------------------------------------- helpers

    /** Returns null for entries that must not be shown. */
    private Entry describe(Path child, Path realDirectory) {
        String name = child.getFileName().toString();
        try {
            // Names the browser could not send back anyway are not listed
            PathGuard.checkName(name);
        } catch (ApiException e) {
            return null;
        }

        // Checked by the real folder, so a link in the path cannot expose a protected file
        if (guard.isHidden(realDirectory.resolve(name))) {
            return null;
        }

        try {
            BasicFileAttributes attributes = Files.readAttributes(child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            boolean link = attributes.isSymbolicLink();
            if (link) {
                Path target = guard.real(child);
                if (!target.startsWith(guard.getRoot()) || guard.isDenied(target)) {
                    return null;
                }
                attributes = Files.readAttributes(child, BasicFileAttributes.class);
            }
            boolean directory = attributes.isDirectory();
            return new Entry(name, directory, link, directory ? 0 : attributes.size(),
                    attributes.lastModifiedTime().toMillis());
        } catch (IOException e) {
            // Broken link, or the entry vanished while listing
            return null;
        }
    }

    private static byte[] readLimited(Path file, long limit) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(limit, BUFFER_SIZE));
            byte[] buffer = new byte[BUFFER_SIZE];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                // The file may have grown since its size was checked
                if (total > limit) {
                    throw tooLarge();
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new ApiException(415, "The file is not valid UTF-8 text");
        }
    }

    private static ApiException tooLarge() {
        return new ApiException(413, "The file is too large to edit. Download it instead.");
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // Nothing we can do
        }
    }
}