package dev.mending.minedash.core.web;

import dev.mending.minedash.api.StreamResponse;
import io.javalin.http.Context;
import org.eclipse.jetty.server.Response;

import javax.servlet.ServletOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Sends StreamResponse results.
 * The data goes straight to the servlet stream. Javalin's own output wrapper would compress it,
 * which breaks a Content-Length header, and could buffer it.
 */
final class StreamResponses {

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final String HEX = "0123456789ABCDEF";

    private StreamResponses() {
    }

    static void write(Context ctx, StreamResponse response) throws IOException {
        try {
            ctx.status(200);
            ctx.contentType(response.getContentType());
            if (response.getFileName() != null) {
                ctx.header("Content-Disposition", contentDisposition(response.getFileName()));
            }
            long length = response.getLength();
            if (length >= 0) {
                ctx.res.setContentLengthLong(length);
            }

            ServletOutputStream out = ctx.res.getOutputStream();
            try {
                if (response.getWriter() != null) {
                    response.getWriter().write(new NonClosingOutputStream(out));
                } else {
                    copy(response.getStream(), out, length);
                }
                out.flush();
            } catch (IOException | RuntimeException e) {
                if (!ctx.res.isCommitted()) {
                    // Nothing was sent yet, so a normal error response is still possible
                    ctx.res.reset();
                    throw e;
                }
                // Part of the data is already on its way. A clean end would make the download look
                // complete, so the connection is cut instead.
                abortConnection(ctx, e);
            }
        } finally {
            response.close();
        }
    }

    private static void copy(InputStream in, OutputStream out, long length) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        if (length < 0) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return;
        }

        // Files can grow while they are read (logs), never send more than announced
        long remaining = length;
        while (remaining > 0) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read == -1) {
                throw new IOException("Source ended before the announced length");
            }
            out.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private static void abortConnection(Context ctx, Throwable cause) throws IOException {
        if (ctx.res instanceof Response) {
            ((Response) ctx.res).getHttpChannel().abort(cause);
            return;
        }
        // Unknown servlet container, report the failure the normal way
        throw new IOException("Transfer failed", cause);
    }

    /** Builds a Content-Disposition value that cannot contain header injection and supports any name. */
    static String contentDisposition(String fileName) {
        // Plain ASCII fallback for old clients
        StringBuilder fallback = new StringBuilder();
        for (int i = 0; i < fileName.length(); i++) {
            char c = fileName.charAt(i);
            boolean plain = c >= 0x20 && c <= 0x7E && c != '"' && c != '\\' && c != ';' && c != '%';
            fallback.append(plain ? c : '_');
        }

        // RFC 5987 encoding of the real name
        StringBuilder encoded = new StringBuilder();
        for (byte b : fileName.getBytes(StandardCharsets.UTF_8)) {
            int value = b & 0xFF;
            boolean allowed = (value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z')
                    || (value >= '0' && value <= '9') || "!#$&+-.^_`|~".indexOf(value) >= 0;
            if (allowed) {
                encoded.append((char) value);
            } else {
                encoded.append('%').append(HEX.charAt(value >> 4)).append(HEX.charAt(value & 0x0F));
            }
        }

        return "attachment; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded;
    }

    /** Lets a writer finish its output without ending the response. */
    private static final class NonClosingOutputStream extends FilterOutputStream {

        NonClosingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            // FilterOutputStream would write byte by byte
            out.write(buffer, offset, length);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}