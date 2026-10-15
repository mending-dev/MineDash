package dev.mending.minedash.modules.files;

import dev.mending.minedash.api.ApiException;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Translates file system errors into API errors with messages that are safe to show. */
final class FileErrors {

    private FileErrors() {
    }

    static ApiException from(IOException e, Logger logger) {
        if (e instanceof NoSuchFileException) {
            return new ApiException(404, "File or folder not found");
        }
        if (e instanceof FileAlreadyExistsException) {
            return new ApiException(409, "A file or folder with this name already exists");
        }
        if (e instanceof DirectoryNotEmptyException) {
            return new ApiException(409, "The folder is not empty");
        }
        if (e instanceof NotDirectoryException) {
            return new ApiException(400, "Not a folder");
        }
        if (e instanceof AccessDeniedException) {
            return new ApiException(403, "The operating system denied access");
        }
        // Anything else is unexpected, so it goes to the log. The client only gets a generic message.
        logger.log(Level.WARNING, "File operation failed", e);
        return new ApiException(500, "The file operation failed, see the server log");
    }
}