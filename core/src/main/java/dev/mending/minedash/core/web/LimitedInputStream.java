package dev.mending.minedash.core.web;

import dev.mending.minedash.api.ApiException;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Fails with HTTP 413 as soon as more than the allowed number of bytes was read. */
final class LimitedInputStream extends FilterInputStream {

    private final long max;
    private long count;

    LimitedInputStream(InputStream in, long max) {
        super(in);
        this.max = max;
    }

    @Override
    public int read() throws IOException {
        int value = super.read();
        if (value >= 0) {
            add(1);
        }
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int read = super.read(buffer, offset, length);
        if (read > 0) {
            add(read);
        }
        return read;
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped = super.skip(n);
        if (skipped > 0) {
            add(skipped);
        }
        return skipped;
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    private void add(long bytes) {
        count += bytes;
        if (count > max) {
            throw new ApiException(413, "Payload too large");
        }
    }
}