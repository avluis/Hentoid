package me.devsaki.hentoid.customssiv.decoder;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorSpace;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import me.devsaki.hentoid.customssiv.util.Helper;
import me.devsaki.hentoid.customssiv.util.ImageHelper;

/**
 * Default implementation of {@link ImageDecoder}
 * using Android's {@link android.graphics.BitmapFactory}, based on the Skia library. This
 * works well in most circumstances and has reasonable performance, however it has some problems
 * with grayscale, indexed and CMYK images.
 */
public class SkiaImageDecoder implements ImageDecoder {

    private static final String FILE_PREFIX = "file://";
    private static final String ASSET_PREFIX = FILE_PREFIX + "/android_asset/";
    private static final String RESOURCE_PREFIX = ContentResolver.SCHEME_ANDROID_RESOURCE + "://";

    private final Bitmap.Config bitmapConfig;


    public SkiaImageDecoder(@NonNull Bitmap.Config bitmapConfig) {
        this.bitmapConfig = bitmapConfig;
    }

    @Override
    @NonNull
    public Bitmap decode(@NonNull final Context context, @NonNull final Uri uri) throws IOException, PackageManager.NameNotFoundException {
        String uriString = uri.toString();
        BitmapFactory.Options options = new BitmapFactory.Options();
        Bitmap bitmap = null;
        options.inPreferredConfig = bitmapConfig;
        // If that is not set, some PNGs are read with a ColorSpace of code "Unknown" (-1),
        // which makes resizing buggy (generates a black picture)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            options.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB);

        if (uriString.startsWith(RESOURCE_PREFIX)) {
            int id = SkiaDecoderHelper.getResourceId(context, uri);
            bitmap = BitmapFactory.decodeResource(context.getResources(), id, options);
        } else if (uriString.startsWith(ASSET_PREFIX)) {
            String assetName = uriString.substring(ASSET_PREFIX.length());
            bitmap = BitmapFactory.decodeStream(context.getAssets().open(assetName), null, options);
        } else {
            InputStream fileStream = null;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input == null)
                    throw new RuntimeException("Content resolver returned null stream. Unable to initialise with uri.");

                // First examine header
                byte[] header = new byte[400];
                if (input.read(header) > 0) {
                    if (ImageHelper.isImageAnimated(header))
                        throw new RuntimeException("SSIV doesn't handle animated pictures");

                    // If it passes, load the whole picture
                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        baos.write(header, 0, 400);

                        Helper.copy(input, baos);

                        fileStream = new ByteArrayInputStream(baos.toByteArray());
                    }
                }
            }

            if (fileStream != null) {
                try {
                    bitmap = BitmapFactory.decodeStream(fileStream, null, options);
                } finally {
                    fileStream.close();
                }
            }
        }
        if (bitmap == null) {
            throw new RuntimeException("Skia image region decoder returned null bitmap - image format may not be supported");
        }

        return bitmap;
    }
}