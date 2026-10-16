package dev.mending.minedash.modules.files;

import dev.mending.minedash.api.ApiException;
import dev.mending.minedash.api.ApiRequest;
import dev.mending.minedash.api.MineDashModule;
import dev.mending.minedash.api.ModuleContext;
import dev.mending.minedash.api.StreamResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class FilesModule implements MineDashModule {

    private static final String VIEW = "files.view";
    private static final String WRITE = "files.write";
    private static final String DELETE = "files.delete";

    private static final class InfoResult {
        final long maxUploadBytes;
        final long maxEditBytes;
        final int maxListEntries;

        InfoResult(FilesConfig config) {
            this.maxUploadBytes = config.maxUploadBytes;
            this.maxEditBytes = config.maxEditBytes;
            this.maxListEntries = config.maxListEntries;
        }
    }

    private static final class UploadResult {
        final long size;

        UploadResult(long size) {
            this.size = size;
        }
    }

    // Request bodies, filled by Gson
    private static final class PathRequest {
        String path;
    }

    private static final class RenameRequest {
        String path;
        String name;
    }

    private static final class MoveRequest {
        List<String> paths;
        String destination;
    }

    private static final class PathsRequest {
        List<String> paths;
    }

    private static final class ZipRequest {
        List<String> paths;
        String target;
    }

    private static final class UnzipRequest {
        String path;
        String destination;
    }

    private Logger logger;

    @Override
    public void onEnable(ModuleContext context) throws Exception {
        logger = context.getLogger();

        final FilesConfig config = context.loadConfig(FilesConfig.class);
        config.validate(logger);

        // The accounts file holds the password hashes. It stays protected even if "root" or
        // "denyPaths" are changed in a way that would no longer cover it.
        List<Path> protectedPaths = Collections.singletonList(
                context.getPlugin().getDataFolder().toPath().resolve("users.json"));

        PathGuard guard = new PathGuard(resolveRoot(config.root), config.denyPaths, protectedPaths, logger);
        final FileService service = new FileService(guard, config, logger);
        final FileOperations operations = new FileOperations(guard, service, config, logger);
        final ZipService zips = new ZipService(guard, service, config, logger);
        final InfoResult info = new InfoResult(config);

        // Limits for the frontend, so it can check files before uploading them
        context.get("/info", VIEW, request -> info);

        context.get("/list", VIEW, request -> service.list(param(request, "path")));

        context.get("/content", VIEW, request -> service.read(param(request, "path")));

        // The body is the new text. It is streamed, so it is not limited to 1 MB like JSON bodies.
        // With version=<hash> the save fails if the file changed in the meantime.
        // With force=true the version is ignored: an existing file is overwritten and a missing one is created.
        context.put("/content", WRITE, request -> {
            String path = param(request, "path");
            boolean force = "true".equals(request.getQueryParam("force"));
            FileOperations.Version version = operations.write(
                    path, request.getBodyStream(config.maxEditBytes), request.getQueryParam("version"), force);
            audit(request, (force ? "saved (forced) " : "saved ") + path);
            return version;
        });

        // One file is sent as it is. A folder or several items are packed into a ZIP while sending.
        context.get("/download", VIEW, request -> {
            List<String> paths = request.getQueryParams("path");
            if (paths.isEmpty()) {
                throw new ApiException(400, "Missing 'path' parameter");
            }

            if (paths.size() == 1 && !zips.isDirectory(paths.get(0))) {
                FileService.Download download = service.openDownload(paths.get(0));
                audit(request, "downloaded " + paths.get(0));
                return StreamResponse.of(download.stream).length(download.size).attachment(download.name);
            }

            // Validated here, so errors still produce a normal error response
            final ZipService.Archive archive = zips.prepare(paths);
            audit(request, "downloaded as ZIP: " + String.join(", ", paths));
            return StreamResponse.of(out -> zips.writeDownload(archive, out))
                    .contentType("application/zip")
                    .attachment(archive.downloadName);
        });

        // The body is the raw file content
        context.put("/upload", WRITE, request -> {
            String path = param(request, "path");
            boolean overwrite = "true".equals(request.getQueryParam("overwrite"));
            long size = operations.upload(path, request.getBodyStream(config.maxUploadBytes), overwrite);
            audit(request, "uploaded " + path + " (" + size + " bytes)");
            return new UploadResult(size);
        });

        context.post("/folder", WRITE, request -> {
            String path = request.getBodyAs(PathRequest.class).path;
            operations.createFolder(path);
            audit(request, "created folder " + path);
            return null;
        });

        context.post("/file", WRITE, request -> {
            String path = request.getBodyAs(PathRequest.class).path;
            operations.createFile(path);
            audit(request, "created file " + path);
            return null;
        });

        context.post("/rename", WRITE, request -> {
            RenameRequest body = request.getBodyAs(RenameRequest.class);
            operations.rename(body.path, body.name);
            audit(request, "renamed " + body.path + " to " + body.name);
            return null;
        });

        context.post("/move", WRITE, request -> {
            MoveRequest body = request.getBodyAs(MoveRequest.class);
            FileOperations.BatchResult result = operations.move(body.paths, body.destination);
            for (String path : result.done) {
                audit(request, "moved " + path + " to " + (body.destination == null ? "" : body.destination));
            }
            return result;
        });

        context.post("/delete", DELETE, request -> {
            PathsRequest body = request.getBodyAs(PathsRequest.class);
            FileOperations.BatchResult result = operations.delete(body.paths);
            for (String path : result.done) {
                audit(request, "deleted " + path);
            }
            return result;
        });

        context.post("/zip", WRITE, request -> {
            ZipRequest body = request.getBodyAs(ZipRequest.class);
            ZipService.ArchiveResult result = zips.createArchive(body.paths, body.target);
            audit(request, "created archive " + body.target + " (" + result.entries + " entries)");
            return result;
        });

        context.post("/unzip", WRITE, request -> {
            UnzipRequest body = request.getBodyAs(UnzipRequest.class);
            ZipService.UnzipResult result = zips.unzip(body.path, body.destination);
            audit(request, "extracted " + body.path + " to "
                    + (body.destination == null ? "" : body.destination) + " (" + result.entries + " entries)");
            return result;
        });

        logger.info("Managing " + guard.getRoot());
    }

    /** Query parameter, or an empty string (the root folder) if it is missing. */
    private static String param(ApiRequest request, String name) {
        String value = request.getQueryParam(name);
        return value == null ? "" : value;
    }

    /** Audit trail. Paths were validated before, so they cannot contain line breaks. */
    private void audit(ApiRequest request, String action) {
        logger.info(request.getUser().getUsername() + " " + action);
    }

    /** A relative root is resolved against the folder the server was started in. */
    private static Path resolveRoot(String configured) {
        Path base = Paths.get("").toAbsolutePath();
        Path root = base.resolve(configured).normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("config.json: 'root' is not a folder: " + configured);
        }
        return root;
    }
}