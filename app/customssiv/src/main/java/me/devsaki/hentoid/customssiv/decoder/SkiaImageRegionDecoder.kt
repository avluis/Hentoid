package me.devsaki.hentoid.customssiv.decoder

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.ColorSpace
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import java.io.IOException
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

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
private const val FILE_PREFIX = "file://"
private const val ASSET_PREFIX = "$FILE_PREFIX/android_asset/"
private const val RESOURCE_PREFIX = ContentResolver.SCHEME_ANDROID_RESOURCE + "://"

class SkiaImageRegionDecoder(private val bitmapConfig: Bitmap.Config) : ImageRegionDecoder {
    private var decoder: BitmapRegionDecoder? = null
    private val decoderLock: ReadWriteLock = ReentrantReadWriteLock(true)

    @Throws(IOException::class, PackageManager.NameNotFoundException::class)
    override fun init(context: Context, uri: Uri): Point {
        val uriString = uri.toString()
        if (uriString.startsWith(RESOURCE_PREFIX)) {
            val id = getResourceId(context, uri)
            context.resources.openRawResource(id).use {
                decoder = BitmapRegionDecoder.newInstance(it, false)
            }
        } else if (uriString.startsWith(ASSET_PREFIX)) {
            val assetName = uriString.substring(ASSET_PREFIX.length)
            context.assets.open(assetName, AssetManager.ACCESS_RANDOM).use {
                decoder = BitmapRegionDecoder.newInstance(it, false)
            }
        } else if (uriString.startsWith(FILE_PREFIX)) {
            decoder =
                BitmapRegionDecoder.newInstance(uriString.substring(FILE_PREFIX.length), false)
        } else {
            context.contentResolver.openInputStream(uri)?.use {
                decoder = BitmapRegionDecoder.newInstance(it, false)
            }
                ?: throw RuntimeException("Content resolver returned null stream. Unable to initialise with uri.")
        }
        decoder?.apply {
            if (!isRecycled) return Point(width, height)
        }
        return Point(-1, -1)
    }

    override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
        getDecodeLock().lock()
        try {
            if (decoder != null && !decoder!!.isRecycled) {
                val options = BitmapFactory.Options()
                options.inSampleSize = sampleSize
                options.inPreferredConfig = bitmapConfig
                // If that is not set, some PNGs are read with a ColorSpace of code "Unknown" (-1),
                // which makes resizing buggy (generates a black picture)
                options.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)

                val bitmap = decoder!!.decodeRegion(sRect, options)
                    ?: throw RuntimeException("Skia image decoder returned null bitmap - image format may not be supported")

                return bitmap
            } else {
                throw IllegalStateException("Cannot decode region after decoder has been recycled")
            }
        } finally {
            getDecodeLock().unlock()
        }
    }

    @Synchronized
    override fun isReady(): Boolean {
        return decoder != null && !decoder!!.isRecycled
    }

    @Synchronized
    override fun recycle() {
        decoderLock.writeLock().lock()
        try {
            decoder?.recycle()
            decoder = null
        } finally {
            decoderLock.writeLock().unlock()
        }
    }

    /**
     * Before SDK 21, BitmapRegionDecoder was not synchronized internally. Any attempt to decode
     * regions from multiple threads with one decoder instance causes a segfault. For old versions
     * use the write lock to enforce single threaded decoding.
     */
    private fun getDecodeLock(): Lock {
        return decoderLock.readLock()
    }
}