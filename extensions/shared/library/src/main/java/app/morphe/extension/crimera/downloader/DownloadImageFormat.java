/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader;

import java.io.BufferedInputStream;
import java.io.IOException;

/** Identifies the response bytes before creating a document with its final name and MIME type. */
final class DownloadImageFormat {
    final String extension;
    final String mimeType;

    private DownloadImageFormat(String extension, String mimeType) {
        this.extension = extension;
        this.mimeType = mimeType;
    }

    static DownloadImageFormat inspect(BufferedInputStream input) throws IOException {
        byte[] header = new byte[12];
        int length = 0;
        input.mark(header.length);
        try {
            while (length < header.length) {
                int count = input.read(header, length, header.length - length);
                if (count < 0) break;
                length += count;
            }
        } finally {
            input.reset();
        }
        if (length >= 3 && (header[0] & 255) == 255
                && (header[1] & 255) == 216 && (header[2] & 255) == 255) {
            return new DownloadImageFormat(".jpg", "image/jpeg");
        }
        if (length >= 8 && (header[0] & 255) == 137 && header[1] == 'P'
                && header[2] == 'N' && header[3] == 'G' && header[4] == 13
                && header[5] == 10 && header[6] == 26 && header[7] == 10) {
            return new DownloadImageFormat(".png", "image/png");
        }
        if (length >= 12 && header[0] == 'R' && header[1] == 'I'
                && header[2] == 'F' && header[3] == 'F' && header[8] == 'W'
                && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return new DownloadImageFormat(".webp", "image/webp");
        }
        if (length >= 6 && header[0] == 'G' && header[1] == 'I'
                && header[2] == 'F' && header[3] == '8'
                && (header[4] == '7' || header[4] == '9') && header[5] == 'a') {
            return new DownloadImageFormat(".gif", "image/gif");
        }
        return null;
    }
}
