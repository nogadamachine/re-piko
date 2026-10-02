/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.downloader;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class WebpJpegConverter {
    private WebpJpegConverter() {
    }

    static void write(InputStream input, OutputStream output) throws IOException {
        Bitmap source = null;
        Bitmap opaque = null;
        try {
            source = BitmapFactory.decodeStream(input);
            if (source == null) throw new IOException("Could not decode downloaded WebP image");
            Bitmap jpeg = source;
            if (source.hasAlpha()) {
                opaque = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(opaque);
                canvas.drawColor(Color.WHITE);
                canvas.drawBitmap(source, 0, 0, null);
                jpeg = opaque;
            }
            if (!jpeg.compress(Bitmap.CompressFormat.JPEG, 100, output)) {
                throw new IOException("Could not encode downloaded image as JPEG");
            }
            output.flush();
        } catch (OutOfMemoryError error) {
            throw new IOException("Not enough memory to convert downloaded WebP image", error);
        } finally {
            if (opaque != null) opaque.recycle();
            if (source != null) source.recycle();
        }
    }
}
