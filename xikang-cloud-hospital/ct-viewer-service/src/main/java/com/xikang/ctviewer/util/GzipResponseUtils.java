package com.xikang.ctviewer.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

public final class GzipResponseUtils {

    private static final int GZIP_MIN_BYTES = 64 * 1024;

    private GzipResponseUtils() {
    }

    public static boolean shouldGzip(byte[] payload) {
        return payload != null && payload.length >= GZIP_MIN_BYTES;
    }

    public static byte[] gzip(byte[] payload) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(256, payload.length / 4));
             GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(payload);
            gzip.finish();
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("GZIP 压缩失败", ex);
        }
    }
}
