package dev.mending.minedash.api;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Return value for routes that send files or other large content.
 * The body is streamed, so it never has to fit into memory.
 */
public final class StreamResponse implements Closeable {

    /** Writes the body directly to the response, e.g. a ZIP archive that is built on the fly. */
    @FunctionalInterface
    public interface Writer {

        /** The writer must not close the stream. */
        void write(OutputStream out) throws IOException;
    }

    private final InputStream stream;
    private final Writer writer;
    private String contentType = "application/octet-stream";
    private String fileName;
    private long length = -1;

    private StreamResponse(InputStream stream, Writer writer) {
        this.stream = stream;
        this.writer = writer;
    }

    /** Streams the given input. It is closed after the response was sent. */
    public static StreamResponse of(InputStream stream) {
        if (stream == null) {
            throw new IllegalArgumentException("stream must not be null");
        }
        return new StreamResponse(stream, null);
    }

    /** Lets the writer produce the body while it is sent. */
    public static StreamResponse of(Writer writer) {
        if (writer == null) {
            throw new IllegalArgumentException("writer must not be null");
        }
        return new StreamResponse(null, writer);
    }

    public StreamResponse contentType(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()
                || contentType.indexOf('\r') >= 0 || contentType.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Invalid content type");
        }
        this.contentType = contentType.trim();
        return this;
    }

    /** The browser saves the response as a file with this name instead of showing it. */
    public StreamResponse attachment(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("fileName must not be empty");
        }
        this.fileName = fileName;
        return this;
    }

    /**
     * Size in bytes if known. Lets browsers show a progress bar.
     * With a stream, at most this many bytes are sent. With a writer the value must be exact.
     */
    public StreamResponse length(long length) {
        this.length = length < 0 ? -1 : length;
        return this;
    }

    public InputStream getStream() {
        return stream;
    }

    public Writer getWriter() {
        return writer;
    }

    public String getContentType() {
        return contentType;
    }

    /** Null if the response is not an attachment. */
    public String getFileName() {
        return fileName;
    }

    /** -1 if unknown. */
    public long getLength() {
        return length;
    }

    /** Closes the input stream, if there is one. Called by MineDash after sending. */
    @Override
    public void close() {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
                // Nothing we can do
            }
        }
    }
}