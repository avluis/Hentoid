package me.devsaki.hentoid.customssiv.decoder;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorSpace;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import me.devsaki.hentoid.customssiv.CustomSubsamplingScaleImageView;

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

    @Keep
    @SuppressWarnings("unused")
    public SkiaImageDecoder() {
        this(null);
    }

    @SuppressWarnings({"WeakerAccess", "SameParameterValue"})
    public SkiaImageDecoder(@Nullable Bitmap.Config bitmapConfig) {
        Bitmap.Config globalBitmapConfig = CustomSubsamplingScaleImageView.getPreferredBitmapConfig();
        if (bitmapConfig != null) {
            this.bitmapConfig = bitmapConfig;
        } else if (globalBitmapConfig != null) {
            this.bitmapConfig = globalBitmapConfig;
        } else {
            this.bitmapConfig = Bitmap.Config.RGB_565;
        }
    }

    @Override
    @NonNull
    public Bitmap decode(@NonNull final Context context, @NonNull final Uri uri) throws IOException, PackageManager.NameNotFoundException {
        String uriString = uri.toString();
        BitmapFactory.Options options = new BitmapFactory.Options();
        Bitmap bitmap;
        options.inPreferredConfig = bitmapConfig;
        // If that is not set, some PNGs are read with a ColorSpace of code "Unknown" (-1),
        // which makes resizing buggy (generates a black picture)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            options.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB);

        if (uriString.startsWith(RESOURCE_PREFIX)) {
            Resources res;
            String packageName = uri.getAuthority();
            if (context.getPackageName().equals(packageName)) {
                res = context.getResources();
            } else {
                PackageManager pm = context.getPackageManager();
                res = pm.getResourcesForApplication(packageName);
            }

            int id = 0;
            List<String> segments = uri.getPathSegments();
            int size = segments.size();
            if (size == 2 && segments.get(0).equals("drawable")) {
                String resName = segments.get(1);
                id = res.getIdentifier(resName, "drawable", packageName);
            } else if (size == 1 && TextUtils.isDigitsOnly(segments.get(0))) {
                try {
                    id = Integer.parseInt(segments.get(0));
                } catch (NumberFormatException ignored) {
                }
            }

            bitmap = BitmapFactory.decodeResource(context.getResources(), id, options);
        } else if (uriString.startsWith(ASSET_PREFIX)) {
            String assetName = uriString.substring(ASSET_PREFIX.length());
            bitmap = BitmapFactory.decodeStream(context.getAssets().open(assetName), null, options);
        } else if (uriString.startsWith(FILE_PREFIX)) {
            bitmap = BitmapFactory.decodeFile(uriString.substring(FILE_PREFIX.length()), options);
        } else {
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input == null)
                    throw new RuntimeException("Content resolver returned null stream. Unable to initialise with uri.");
                bitmap = BitmapFactory.decodeStream(input, null, options);
            }
        }
        if (bitmap == null) {
            throw new RuntimeException("Skia image region decoder returned null bitmap - image format may not be supported");
        }

        return bitmap;
    }
}