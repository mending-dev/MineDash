package dev.mending.minedash.modules.files;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** The version of a file is the SHA-256 hash of its content, as lower case hex. */
final class FileVersions {

    private FileVersions() {
    }

    static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Every Java runtime has to provide SHA-256
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Finishes the digest and returns the hex string. */
    static String hex(MessageDigest digest) {
        byte[] hash = digest.digest();
        StringBuilder result = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            result.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return result.toString();
    }

    static String of(byte[] data) {
        MessageDigest digest = newDigest();
        digest.update(data);
        return hex(digest);
    }

    /** Streams the file, so even big files need no memory. */
    static String of(Path file) throws IOException {
        MessageDigest digest = newDigest();
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return hex(digest);
    }
}