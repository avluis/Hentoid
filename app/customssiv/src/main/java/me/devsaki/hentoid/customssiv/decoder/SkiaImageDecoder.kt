package me.devsaki.hentoid.customssiv.decoder

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.net.Uri
import me.devsaki.hentoid.customssiv.exception.UnsupportedContentException
import me.devsaki.hentoid.customssiv.util.copy
import me.devsaki.hentoid.customssiv.util.isImageAnimated
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import kotlin.math.min


/**
 * Default implementation of {@link ImageDecoder}
 * using Android's {@link android.graphics.BitmapFactory}, based on the Skia library. This
 * works well in most circumstances and has reasonable performance, however it has some problems
 * with grayscale, indexed and CMYK images.
 */

private const val FILE_PREFIX = "file://"
private const val ASSET_PREFIX = "$FILE_PREFIX/android_asset/"
private const val RESOURCE_PREFIX = ContentResolver.SCHEME_ANDROID_RESOURCE + "://"

class ByteBufferBackedInputStream(private var buf: ByteBuffer) : InputStream() {
    @Throws(IOException::class)
    override fun read(): Int {
        if (!buf.hasRemaining()) {
            return -1
        }
        return buf.get().toInt() and 0xFF
    }

    @Throws(IOException::class)
    override fun read(bytes: ByteArray, off: Int, len: Int): Int {
        if (!buf.hasRemaining()) return -1

        val theLen = min(len, buf.remaining())
        buf[bytes, off, theLen]
        return theLen
    }

    override fun close() {
        buf.clear()
        super.close()
    }
}

class SkiaImageDecoder(private val bitmapConfig: Bitmap.Config) : ImageDecoder {
    override fun decode(context: Context, uri: Uri): Bitmap {
        val uriString = uri.toString()
        val options = BitmapFactory.Options()
        var bitmap: Bitmap? = null
        options.inPreferredConfig = bitmapConfig

        // If that is not set, some PNGs are read with a ColorSpace of code "Unknown" (-1),
        // which makes resizing buggy (generates a black picture)
        options.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)

        if (uriString.startsWith(RESOURCE_PREFIX)) {
            val id = getResourceId(context, uri)
            bitmap = BitmapFactory.decodeResource(context.resources, id, options)
        } else if (uriString.startsWith(ASSET_PREFIX)) {
            val assetName = uriString.substring(ASSET_PREFIX.length)
            bitmap = BitmapFactory.decodeStream(context.assets.open(assetName), null, options)
        } else {
            var fileStream: InputStream? = null
            var size = 0
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                size = it.statSize.toInt()
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                if (size > 0) {
                    // First examine header
                    val header = ByteArray(400)
                    if (input.read(header) > 0) {
                        if (isImageAnimated(header))
                            throw UnsupportedContentException("SSIV doesn't handle animated pictures")
                        val bb = MappedByteBuffer.allocate(size)
                        bb.put(header)
                        copy(input, bb)
                        bb.position(0)
                        fileStream = ByteBufferBackedInputStream(bb)
                    }
                } else {
                    Timber.e("Size is zero!")
                }
            }
                ?: throw RuntimeException("Content resolver returned null stream. Unable to initialise with uri.")
            fileStream?.use {
                bitmap = BitmapFactory.decodeStream(it, null, options)
                Timber.d("bitmap ${bitmap}")
            }
        }
        bitmap?.let {
            return it
        } ?: run {
            throw RuntimeException("Skia image decoder returned null bitmap - image format may not be supported")
        }
    }
}