package me.devsaki.hentoid.customssiv.decoder;

import static me.devsaki.hentoid.customssiv.decoder.SkiaDecoderHelperKt.getResourceId;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.ColorSpace;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Default implementation of {@link ImageRegionDecoder}
 * using Android's {@link android.graphics.BitmapRegionDecoder}, based on the Skia library. This
 * works well in most circumstances and has reasonable performance due to the cached decoder instance,
 * however it has some problems with grayscale, indexed and CMYK images.
 * <p>
 * A {@link ReadWriteLock} is used to delegate responsibility for multi threading behaviour to the
 * {@link BitmapRegionDecoder} instance on SDK &gt;= 21, whilst allowing this class to block until no
 * tiles are being loaded before recycling the decoder. In practice, {@link BitmapRegionDecoder} is
 * synchronized internally so this has no real impact on performance.
 */
public class SkiaImageRegionDecoder implements ImageRegionDecoder {

    private BitmapRegionDecoder decoder;
    private final ReadWriteLock decoderLock = new ReentrantReadWriteLock(true);

    private static final String FILE_PREFIX = "file://";
    private static final String ASSET_PREFIX = FILE_PREFIX + "/android_asset/";
    private static final String RESOURCE_PREFIX = ContentResolver.SCHEME_ANDROID_RESOURCE + "://";

    private final Bitmap.Config bitmapConfig;


    public SkiaImageRegionDecoder(@NonNull Bitmap.Config bitmapConfig) {
        this.bitmapConfig = bitmapConfig;
    }

    @Override
    @NonNull
    public Point init(Context context, @NonNull Uri uri) throws IOException, PackageManager.NameNotFoundException {
        String uriString = uri.toString();
        if (uriString.startsWith(RESOURCE_PREFIX)) {
            int id = getResourceId(context, uri);
            decoder = BitmapRegionDecoder.newInstance(context.getResources().openRawResource(id), false);
        } else if (uriString.startsWith(ASSET_PREFIX)) {
            String assetName = uriString.substring(ASSET_PREFIX.length());
            decoder = BitmapRegionDecoder.newInstance(context.getAssets().open(assetName, AssetManager.ACCESS_RANDOM), false);
        } else if (uriString.startsWith(FILE_PREFIX)) {
            decoder = BitmapRegionDecoder.newInstance(uriString.substring(FILE_PREFIX.length()), false);
        } else {
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input == null)
                    throw new RuntimeException("Content resolver returned null stream. Unable to initialise with uri.");
                decoder = BitmapRegionDecoder.newInstance(input, false);
            }
        }
        if (decoder != null && !decoder.isRecycled())
            return new Point(decoder.getWidth(), decoder.getHeight());
        else return new Point(-1, -1);
    }

    @Override
    @NonNull
    public Bitmap decodeRegion(@NonNull Rect sRect, int sampleSize) {
        getDecodeLock().lock();
        try {
            if (decoder != null && !decoder.isRecycled()) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sampleSize;
                options.inPreferredConfig = bitmapConfig;
                // If that is not set, some PNGs are read with a ColorSpace of code "Unknown" (-1),
                // which makes resizing buggy (generates a black picture)
                options.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB);

                Bitmap bitmap = decoder.decodeRegion(sRect, options);
                if (bitmap == null) {
                    throw new RuntimeException("Skia image decoder returned null bitmap - image format may not be supported");
                }

                return bitmap;
            } else {
                throw new IllegalStateException("Cannot decode region after decoder has been recycled");
            }
        } finally {
            getDecodeLock().unlock();
        }
    }

    @Override
    public synchronized boolean isReady() {
        return decoder != null && !decoder.isRecycled();
    }

    @Override
    public synchronized void recycle() {
        decoderLock.writeLock().lock();
        try {
            if (decoder != null) decoder.recycle();
            decoder = null;
        } finally {
            decoderLock.writeLock().unlock();
        }
    }

    /**
     * Before SDK 21, BitmapRegionDecoder was not synchronized internally. Any attempt to decode
     * regions from multiple threads with one decoder instance causes a segfault. For old versions
     * use the write lock to enforce single threaded decoding.
     */
    private Lock getDecodeLock() {
        return decoderLock.readLock();
    }
}