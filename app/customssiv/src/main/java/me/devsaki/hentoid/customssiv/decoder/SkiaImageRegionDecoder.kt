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
import android.os.Build
import java.io.IOException

/**
 * Default implementation of {@link ImageRegionDecoder}
 * using Android's {@link android.graphics.BitmapRegionDecoder}, based on the Skia library. This
 * works well in most circumstances and has reasonable performance due to the cached decoder instance,
 * however it has some problems with grayscale, indexed and CMYK images.
 */
private const val FILE_PREFIX = "file://"
private const val ASSET_PREFIX = "$FILE_PREFIX/android_asset/"
private const val RESOURCE_PREFIX = ContentResolver.SCHEME_ANDROID_RESOURCE + "://"

internal class SkiaImageRegionDecoder(private val bitmapConfig: Bitmap.Config) : ImageRegionDecoder {
    private var decoder: BitmapRegionDecoder? = null

    @Suppress("DEPRECATION")
    @Throws(IOException::class, PackageManager.NameNotFoundException::class)
    override fun init(context: Context, uri: Uri): Point {
        val uriString = uri.toString()
        if (uriString.startsWith(RESOURCE_PREFIX)) {
            val id = getResourceId(context, uri)
            context.resources.openRawResource(id).use {
                decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BitmapRegionDecoder.newInstance(it)
                } else {
                    BitmapRegionDecoder.newInstance(it, false)
                }
            }
        } else if (uriString.startsWith(ASSET_PREFIX)) {
            val assetName = uriString.substring(ASSET_PREFIX.length)
            context.assets.open(assetName, AssetManager.ACCESS_RANDOM).use {
                decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BitmapRegionDecoder.newInstance(it)
                } else {
                    BitmapRegionDecoder.newInstance(it, false)
                }
            }
        } else if (uriString.startsWith(FILE_PREFIX)) {
            decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BitmapRegionDecoder.newInstance(uriString.substring(FILE_PREFIX.length))
            } else {
                BitmapRegionDecoder.newInstance(uriString.substring(FILE_PREFIX.length), false)
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use {
                decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BitmapRegionDecoder.newInstance(it)
                } else {
                    BitmapRegionDecoder.newInstance(it, false)
                }
            }
                ?: throw RuntimeException("Content resolver returned null stream. Unable to initialise with uri.")
        }
        decoder?.apply {
            if (!isRecycled) return Point(width, height)
        }
        return Point(-1, -1)
    }

    override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap {
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
    }

    @Synchronized
    override fun isReady(): Boolean {
        return decoder != null && !decoder!!.isRecycled
    }

    @Synchronized
    override fun recycle() {
        decoder?.recycle()
        decoder = null
    }
}