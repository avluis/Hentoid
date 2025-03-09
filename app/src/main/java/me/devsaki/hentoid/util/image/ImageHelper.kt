package me.devsaki.hentoid.util.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.os.Build
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.waynejo.androidndkgif.GifEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.util.assertNonUiThread
import me.devsaki.hentoid.util.duplicateInputStream
import me.devsaki.hentoid.util.file.NameFilter
import me.devsaki.hentoid.util.file.fileExists
import me.devsaki.hentoid.util.file.findSequencePosition
import me.devsaki.hentoid.util.file.getExtension
import me.devsaki.hentoid.util.file.getInputStream
import me.devsaki.hentoid.util.network.getExtensionFromUri
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.net.toUri

private val CHARSET_LATIN_1 = StandardCharsets.ISO_8859_1

const val MIME_IMAGE_GENERIC = "image/*"
const val MIME_IMAGE_WEBP = "image/webp"
const val MIME_IMAGE_JPEG = "image/jpeg"
const val MIME_IMAGE_GIF = "image/gif"
private const val MIME_IMAGE_BMP = "image/bmp"
const val MIME_IMAGE_PNG = "image/png"
const val MIME_IMAGE_APNG = "image/apng"
const val MIME_IMAGE_JXL = "image/jxl"

// In Java and Kotlin, byte type is signed !
// => Converting all raw values to byte to be sure they are evaluated as expected
private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
private val WEBP_SIGNATURE =
    byteArrayOf(0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte())
private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte())
private val GIF_SIGNATURE = byteArrayOf(0x47.toByte(), 0x49.toByte(), 0x46.toByte())
private val BMP_SIGNATURE = byteArrayOf(0x42.toByte(), 0x4D.toByte())

private val GIF_NETSCAPE = "NETSCAPE".toByteArray(CHARSET_LATIN_1)

private val PNG_ACTL = "acTL".toByteArray(CHARSET_LATIN_1)
private val PNG_IDAT = "IDAT".toByteArray(CHARSET_LATIN_1)

private val WEBP_VP8L = "VP8L".toByteArray(CHARSET_LATIN_1)
private val WEBP_ANIM = "ANIM".toByteArray(CHARSET_LATIN_1)

private val JXL_NAKED = byteArrayOf(0xFF.toByte(), 0x0A.toByte())
private val JXL_ISO = byteArrayOf(
    0x00.toByte(),
    0x00.toByte(),
    0x00.toByte(),
    0x0C.toByte(),
    0x4A.toByte(),
    0x58.toByte(),
    0x4C.toByte(),
    0x20.toByte(),
    0x0D.toByte(),
    0x0A.toByte(),
    0x87.toByte(),
    0x0A.toByte()
)

val imageNamesFilter = NameFilter { isImageExtensionSupported(getExtension(it)) }


/**
 * Determine if the given image file extension is supported by the app
 *
 * @param extension File extension to test
 * @return True if the app supports the reading of images with the given file extension; false if not
 */
fun isMimeTypeSupported(extension: String): Boolean {
    return (extension.equals(MIME_IMAGE_JPEG, ignoreCase = true)
            || extension.equals(MIME_IMAGE_WEBP, ignoreCase = true)
            || extension.equals(MIME_IMAGE_PNG, ignoreCase = true)
            || extension.equals(MIME_IMAGE_APNG, ignoreCase = true)
            || extension.equals(MIME_IMAGE_GIF, ignoreCase = true)
            || extension.equals(MIME_IMAGE_BMP, ignoreCase = true)
            || extension.equals(MIME_IMAGE_JXL, ignoreCase = true)
            )
}

/**
 * Determine if the given image MIME type is supported by the app
 *
 * @param mimeType MIME type to test
 * @return True if the app supports the reading of images with the given MIME type; false if not
 */
private fun isImageExtensionSupported(mimeType: String): Boolean {
    return (mimeType.equals("jpg", ignoreCase = true)
            || mimeType.equals("jpeg", ignoreCase = true)
            || mimeType.equals("webp", ignoreCase = true)
            || mimeType.equals("png", ignoreCase = true)
            || mimeType.equals("jfif", ignoreCase = true)
            || mimeType.equals("gif", ignoreCase = true)
            || mimeType.equals("jxl", ignoreCase = true))
}

fun isSupportedImage(fileName: String): Boolean {
    return isImageExtensionSupported(getExtension(fileName))
}

fun ByteArray.startsWith(data: ByteArray): Boolean {
    if (this.size < data.size) return false
    data.forEachIndexed { index, byte -> if (byte != this[index]) return false }
    return true
}

/**
 * Determine the MIME-type of the given binary data if it's a picture
 *
 * @param data Picture binary data to determine the MIME-type for
 * @return MIME-type of the given binary data; empty string if not supported
 */
fun getMimeTypeFromPictureBinary(data: ByteArray, limit: Int = -1): String {
    if (data.size < 12) return ""
    val theLimit = if (-1 == limit) min(data.size * 0.2f, 1000f).toInt() else limit

    return if (data.startsWith(JPEG_SIGNATURE)) MIME_IMAGE_JPEG
    else if (data.startsWith(JXL_NAKED)) MIME_IMAGE_JXL
    else if (data.startsWith(JXL_ISO)) MIME_IMAGE_JXL
    // WEBP : byte comparison is non-contiguous
    else if (data.startsWith(WEBP_SIGNATURE) && 0x57.toByte() == data[8] && 0x45.toByte() == data[9] && 0x42.toByte() == data[10] && 0x50.toByte() == data[11]
    ) MIME_IMAGE_WEBP
    else if (data.startsWith(PNG_SIGNATURE)) {
        // Detect animated PNG : To be recognized as APNG an 'acTL' chunk must appear in the stream before any 'IDAT' chunks
        val acTlPos = findSequencePosition(
            data,
            0,
            PNG_ACTL,
            theLimit
        )
        if (acTlPos > -1) {
            val idatPos = findSequencePosition(
                data,
                acTlPos,
                PNG_IDAT,
                theLimit
            ).toLong()
            if (idatPos > -1) return MIME_IMAGE_APNG
        }
        MIME_IMAGE_PNG
    } else if (data.startsWith(GIF_SIGNATURE)) MIME_IMAGE_GIF
    else if (data.startsWith(BMP_SIGNATURE)) MIME_IMAGE_BMP
    else MIME_IMAGE_GENERIC
}

/**
 * Analyze the given binary picture header to try and detect if the picture is animated.
 * If the format is supported by the app, returns true if animated (animated GIF, APNG, animated WEBP); false if not
 *
 * @param data Binary picture file header (400 bytes minimum)
 * @return True if the format is animated and supported by the app
 */
fun isImageAnimated(data: ByteArray): Boolean {
    // TODO JXL (specs aren't public :/)
    return if (data.size < 400) false
    else {
        val limit = min(data.size, 1000)
        when (getMimeTypeFromPictureBinary(data, limit)) {
            MIME_IMAGE_APNG -> true
            MIME_IMAGE_GIF -> findSequencePosition(
                data,
                0,
                GIF_NETSCAPE,
                limit
            ) > -1

            MIME_IMAGE_WEBP -> findSequencePosition(
                data,
                0,
                WEBP_ANIM,
                limit
            ) > -1

            else -> false
        }
    }
}

/**
 * Analyze the given binary picture header to try and detect if the picture is lossless.
 * If the format is supported by the app, returns true if lossless (PNG, lossless WEBP); false if not
 *
 * @param data Binary picture file header (16 bytes minimum)
 * @return True if the format is lossless and supported by the app
 */
fun isImageLossless(data: ByteArray): Boolean {
    // TODO JXL (specs aren't public :/)
    return if (data.size < 16) false else when (getMimeTypeFromPictureBinary(data)) {
        MIME_IMAGE_PNG -> true
        MIME_IMAGE_APNG -> true
        MIME_IMAGE_GIF -> true
        MIME_IMAGE_WEBP -> findSequencePosition(
            data,
            0,
            WEBP_VP8L,
            16
        ) > -1

        else -> false
    }
}

/**
 * Try to detect the mime-type of the picture file at the given URI
 * NB : Opens the resource -> file I/O inside
 *
 * @param context Context to use
 * @param uri     URI of the picture file to detect the mime-type for
 * @return Mime-type of the picture file at the given URI; MIME_IMAGE_GENERIC if no Mime-type detected
 */
fun getMimeTypeFromUri(context: Context, uri: Uri): String {
    assertNonUiThread()
    var result = MIME_IMAGE_GENERIC
    val buffer = ByteArray(12)
    try {
        getInputStream(context, uri).use { input ->
            if (buffer.size == input.read(buffer)) result = getMimeTypeFromPictureBinary(buffer)
        }
    } catch (e: IOException) {
        Timber.w(e)
    }
    return result
}

/**
 * Convert the given Drawable ID into a Bitmap
 *
 * @param context    Context to be used
 * @param drawableId Drawable ID to get the Bitmap from
 * @return Given drawable ID rendered into a Bitmap
 */
fun getBitmapFromVectorDrawable(context: Context, @DrawableRes drawableId: Int): Bitmap {
    val d = ContextCompat.getDrawable(context, drawableId)
    return if (d != null) {
        val b =
            createBitmap(d.intrinsicWidth, d.intrinsicHeight, Bitmap.Config.ARGB_8888, false)
        val c = Canvas(b)
        d.setBounds(0, 0, c.width, c.height)
        d.draw(c)
        b
    } else {
        createBitmap(0, 0, Bitmap.Config.ARGB_8888, false)
    }
}

@Suppress("DEPRECATION")
fun bitmapToWebp(bitmap: Bitmap): ByteArray {
    val output = ByteArrayOutputStream()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, output)
    else
        bitmap.compress(Bitmap.CompressFormat.WEBP, 100, output)
    return output.toByteArray()
}

/**
 * Tint the given Bitmap with the given color
 *
 * @param bitmap Bitmap to be tinted
 * @param color  Color to use as tint
 * @return Given Bitmap tinted with the given color
 */
fun tintBitmap(bitmap: Bitmap, @ColorInt color: Int): Bitmap {
    val p = Paint()
    p.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
    val b = createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888, false)
    val canvas = Canvas(b)
    canvas.drawBitmap(bitmap, 0f, 0f, p)
    return b
}

/**
 * Calculate sample size to load the picture with, according to raw and required dimensions
 *
 * @param rawWidth     Raw width of the picture, in pixels
 * @param rawHeight    Raw height of the picture, in pixels
 * @param targetWidth  Target width of the picture, in pixels
 * @param targetHeight Target height of the picture, in pixels
 * @return Sample size to use to load the picture with
 */
private fun calculateInSampleSize(
    rawWidth: Int,
    rawHeight: Int,
    targetWidth: Int,
    targetHeight: Int
): Int {
    // Raw height and width of image
    var inSampleSize = 1
    if (rawHeight > targetHeight || rawWidth > targetWidth) {
        val halfHeight = rawHeight / 2
        val halfWidth = rawWidth / 2

        // Calculate the largest inSampleSize value that is a power of 2 and keeps both
        // height and width larger than the requested height and width.
        while (halfHeight / inSampleSize >= targetHeight
            && halfWidth / inSampleSize >= targetWidth
        ) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

/**
 * Create a Bitmap from the given InputStream, optimizing resources according to the given required width and height
 *
 * @param stream       Stream to load the bitmap from
 * @param targetWidth  Target picture width, in pixels
 * @param targetHeight Target picture height, in pixels
 * @return Bitmap created from the given InputStream
 * @throws IOException If anything bad happens at load-time
 */
@Throws(IOException::class)
fun decodeSampledBitmapFromStream(
    stream: InputStream,
    targetWidth: Int,
    targetHeight: Int
): Bitmap? {
    val streams = duplicateInputStream(stream, 2)
    val workStream1 = streams[0]
    var workStream2 = streams[1]

    // First decode with inJustDecodeBounds=true to check dimensions
    val options = BitmapFactory.Options()
    options.inJustDecodeBounds = true
    BitmapFactory.decodeStream(workStream1, null, options)
    if (null == workStream2) {
        workStream1.reset()
        workStream2 = workStream1
    } else {
        workStream1.close()
    }

    // Calculate inSampleSize
    options.inSampleSize =
        calculateInSampleSize(options.outWidth, options.outHeight, targetWidth, targetHeight)

    // Decode final bitmap with inSampleSize set
    options.inJustDecodeBounds = false
    return try {
        BitmapFactory.decodeStream(workStream2, null, options)
    } finally {
        workStream2.close()
    }
}

@Throws(IOException::class, IllegalArgumentException::class)
fun assembleGif(
    context: Context,
    folder: File,  // GIF encoder only work with paths...
    frames: List<Pair<Uri, Int>>
): Uri? {
    require(frames.isNotEmpty()) { "No frames given" }
    var width: Int
    var height: Int
    getInputStream(context, frames[0].first).use { input ->
        val b = BitmapFactory.decodeStream(input)
        width = b.width
        height = b.height
    }
    val path = File(folder, "tmp.gif").absolutePath
    val gifEncoder = GifEncoder()
    try {
        gifEncoder.init(
            width,
            height,
            path,
            GifEncoder.EncodingType.ENCODING_TYPE_NORMAL_LOW_MEMORY
        )
        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        for (frame in frames) {
            getInputStream(context, frame.first).use { input ->
                BitmapFactory.decodeStream(input, null, options)?.let { b ->
                    try {
                        // Warning : if frame.second is <= 10, GIFs will be read slower on most readers
                        // (see https://android.googlesource.com/platform/frameworks/base/+/2be87bb707e2c6d75f668c4aff6697b85fbf5b15)
                        gifEncoder.encodeFrame(b, frame.second)
                    } finally {
                        b.recycle()
                    }
                }
            }
        }
    } finally {
        gifEncoder.close()
    }
    return Uri.fromFile(File(path))
}

/**
 * @param bitmap                the Bitmap to be scaled
 * @param threshold             the maxium dimension (either width or height) of the scaled bitmap
 * @param noRecycle is it necessary to keep the original bitmap? If not recycle the original bitmap to prevent memory leak.
 */
fun getScaledDownBitmap(
    bitmap: Bitmap,
    threshold: Int,
    noRecycle: Boolean
): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    var newWidth = width
    var newHeight = height
    if (width > height && width > threshold) {
        newWidth = threshold
        newHeight = (height * newWidth.toFloat() / width).toInt()
    }
    if (width in (height + 1)..threshold) {
        //the bitmap is already smaller than our required dimension, no need to resize it
        return bitmap
    }
    if (width < height && height > threshold) {
        newHeight = threshold
        newWidth = (width * newHeight.toFloat() / height).toInt()
    }
    if (height in (width + 1)..threshold) {
        //the bitmap is already smaller than our required dimension, no need to resize it
        return bitmap
    }
    if (width == height && width > threshold) {
        newWidth = threshold
        newHeight = newWidth
    }
    return if (width == height && width <= threshold) {
        //the bitmap is already smaller than our required dimension, no need to resize it
        bitmap
    } else sharpRescale(bitmap, newWidth, newHeight, noRecycle)
}

private fun sharpRescale(
    source: Bitmap,
    newWidth: Int,
    newHeight: Int,
    noRecycle: Boolean
): Bitmap {
    val width = source.width
    val height = source.height
    val scaleWidth = newWidth.toFloat() / width
    val scaleHeight = newHeight.toFloat() / height
    val rescaled = sharpRescale(source, scaleHeight.coerceAtMost(scaleWidth))
    if (!noRecycle && source != rescaled) { // Don't recycle if the result is the same object as the source
        source.recycle()
    }
    return rescaled
}

fun sharpRescale(src: Bitmap, targetScale: Float): Bitmap {
    val resizeParams = computeRescaleParams(targetScale)
    Timber.d(">> resizing successively to scale %s", resizeParams.second)
    return successiveRescale(src, resizeParams.first)
}

/**
 * Compute resizing parameters according to the given target scale
 *
 * @param targetScale target scale of the image to display (% of the raw dimensions)
 * @return Pair containing
 * - First : Number of half-resizes to perform
 * - Second : Corresponding scale
 */
private fun computeRescaleParams(targetScale: Float): Pair<Int, Float> {
    var resultScale = 1f
    var nbResize = 0

    // Resize when approaching the target scale by 1/3 because there may already be artifacts displayed at that point
    // (seen with full-res pictures resized to 65% with Android's default bilinear filtering)
    for (i in 1..9) if (targetScale < 0.5.pow(i.toDouble()) * 1.33) nbResize++
    if (nbResize > 0) resultScale = 0.5.pow(nbResize.toDouble()).toFloat()
    return Pair(nbResize, resultScale)
}

private fun successiveRescale(src: Bitmap, resizeNum: Int): Bitmap {
    if (0 == resizeNum) return src
    var srcWidth = src.width
    var srcHeight = src.height
    var output = src
    for (i in 0 until resizeNum) {
        srcWidth /= 2
        srcHeight /= 2
        val temp = output.scale(srcWidth, srcHeight)
        if (i != 0) { // don't recycle the src bitmap
            output.recycle()
        }
        output = temp
    }
    return output
}

/**
 * Indicates if the picture needs to be rotated 90°, according to the given picture proportions (auto-rotate feature)
 * The goal is to align the picture's proportions with the phone screen's proportions
 *
 * @param screenWidth  Screen width
 * @param screenHeight Screen height
 * @param width        Picture width
 * @param height       Picture height
 * @return True if the picture needs to be rotated 90°
 */
fun needsRotating(screenWidth: Int, screenHeight: Int, width: Int, height: Int): Boolean {
    val isSourceSquare = abs(height - width) < width * 0.1
    if (isSourceSquare) return false
    val isSourceLandscape = width > height * 1.33
    val isScreenLandscape = screenWidth > screenHeight * 1.33
    return isSourceLandscape != isScreenLandscape
}

/**
 * Return the given image's dimensions
 *
 * @param context Context to be used
 * @param uri     Uri of the image to be read
 * @return Dimensions (x,y) of the given image
 */
suspend fun getImageDimensions(context: Context, uri: String): Point = withContext(Dispatchers.IO) {
    val fileUri = uri.toUri()
    if (!fileExists(context, fileUri)) return@withContext Point(0, 0)

    if (getExtensionFromUri(uri) == "jxl") {
        return@withContext getDimensions(context, uri)
    } else { // Natively supported by Android
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        return@withContext try {
            BitmapFactory.decodeStream(getInputStream(context, fileUri), null, options)
            Point(options.outWidth, options.outHeight)
        } catch (e: IOException) {
            Timber.w(e)
            Point(0, 0)
        } catch (e: IllegalArgumentException) {
            Timber.w(e)
            Point(0, 0)
        }
    }
}

fun loadBitmap(context: Context, file: DocumentFile): Bitmap? {
    if (!file.exists()) return null
    val options = BitmapFactory.Options()
    options.inPreferredConfig = Bitmap.Config.ARGB_8888
    return try {
        BitmapFactory.decodeStream(getInputStream(context, file), null, options)
    } catch (e: Exception) {
        Timber.w(e)
        null
    }
}