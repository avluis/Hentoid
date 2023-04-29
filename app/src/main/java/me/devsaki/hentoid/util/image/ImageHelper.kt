package me.devsaki.hentoid.util.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.waynejo.androidndkgif.GifEncoder
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.file.FileHelper
import org.apache.commons.lang3.tuple.ImmutablePair
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.math.pow

object ImageHelper {
    private val CHARSET_LATIN_1 = StandardCharsets.ISO_8859_1

    const val MIME_IMAGE_GENERIC = "image/*"
    const val MIME_IMAGE_WEBP = "image/webp"
    const val MIME_IMAGE_JPEG = "image/jpeg"
    const val MIME_IMAGE_GIF = "image/gif"
    const val MIME_IMAGE_PNG = "image/png"
    const val MIME_IMAGE_APNG = "image/apng"


    private var imageNamesFilter: FileHelper.NameFilter? = null

    /**
     * Determine if the given image file extension is supported by the app
     *
     * @param extension File extension to test
     * @return True if the app supports the reading of images with the given file extension; false if not
     */
    fun isImageExtensionSupported(extension: String): Boolean {
        return (extension.equals("jpg", ignoreCase = true)
                || extension.equals("jpeg", ignoreCase = true)
                || extension.equals("jfif", ignoreCase = true)
                || extension.equals("gif", ignoreCase = true)
                || extension.equals("png", ignoreCase = true)
                || extension.equals("webp", ignoreCase = true))
    }

    fun isSupportedImage(fileName: String): Boolean {
        return isImageExtensionSupported(FileHelper.getExtension(fileName))
    }

    /**
     * Build a [FileHelper.NameFilter] only accepting image files supported by the app
     *
     * @return [FileHelper.NameFilter] only accepting image files supported by the app
     */
    fun getImageNamesFilter(): FileHelper.NameFilter? {
        if (null == imageNamesFilter) imageNamesFilter =
            FileHelper.NameFilter { displayName: String ->
                isImageExtensionSupported(
                    FileHelper.getExtension(displayName)
                )
            }
        return imageNamesFilter
    }

    /**
     * Determine the MIME-type of the given binary data if it's a picture
     *
     * @param binary Picture binary data to determine the MIME-type for
     * @return MIME-type of the given binary data; empty string if not supported
     */
    fun getMimeTypeFromPictureBinary(binary: ByteArray): String {
        if (binary.size < 12) return ""

        // In Java, byte type is signed !
        // => Converting all raw values to byte to be sure they are evaluated as expected
        return if (0xFF.toByte() == binary[0] && 0xD8.toByte() == binary[1] && 0xFF.toByte() == binary[2]) MIME_IMAGE_JPEG else if (0x89.toByte() == binary[0] && 0x50.toByte() == binary[1] && 0x4E.toByte() == binary[2]) {
            // Detect animated PNG : To be recognized as APNG an 'acTL' chunk must appear in the stream before any 'IDAT' chunks
            val acTlPos = FileHelper.findSequencePosition(
                binary,
                0,
                "acTL".toByteArray(CHARSET_LATIN_1),
                (binary.size * 0.2).toInt()
            )
            if (acTlPos > -1) {
                val idatPos = FileHelper.findSequencePosition(
                    binary,
                    acTlPos,
                    "IDAT".toByteArray(CHARSET_LATIN_1),
                    (binary.size * 0.1).toInt()
                ).toLong()
                if (idatPos > -1) return MIME_IMAGE_APNG
            }
            MIME_IMAGE_PNG
        } else if (0x47.toByte() == binary[0] && 0x49.toByte() == binary[1] && 0x46.toByte() == binary[2]) MIME_IMAGE_GIF else if (0x52.toByte() == binary[0] && 0x49.toByte() == binary[1] && 0x46.toByte() == binary[2] && 0x46.toByte() == binary[3] && 0x57.toByte() == binary[8] && 0x45.toByte() == binary[9] && 0x42.toByte() == binary[10] && 0x50.toByte() == binary[11]) MIME_IMAGE_WEBP else if (0x42.toByte() == binary[0] && 0x4D.toByte() == binary[1]) "image/bmp" else MIME_IMAGE_GENERIC
    }

    /**
     * Analyze the given binary picture header to try and detect if the picture is animated.
     * If the format is supported by the app, returns true if animated (animated GIF, APNG, animated WEBP); false if not
     *
     * @param data Binary picture file header (400 bytes minimum)
     * @return True if the format is animated and supported by the app
     */
    fun isImageAnimated(data: ByteArray): Boolean {
        return if (data.size < 400) false else when (getMimeTypeFromPictureBinary(data)) {
            MIME_IMAGE_APNG -> true
            MIME_IMAGE_GIF -> FileHelper.findSequencePosition(
                data,
                0,
                "NETSCAPE".toByteArray(CHARSET_LATIN_1),
                400
            ) > -1

            MIME_IMAGE_WEBP -> FileHelper.findSequencePosition(
                data,
                0,
                "ANIM".toByteArray(CHARSET_LATIN_1),
                400
            ) > -1

            else -> false
        }
    }

    /**
     * Try to detect the mime-type of the picture file at the given URI
     *
     * @param context Context to use
     * @param uri     URI of the picture file to detect the mime-type for
     * @return Mime-type of the picture file at the given URI; MIME_IMAGE_GENERIC if no Mime-type detected
     */
    fun getMimeTypeFromUri(context: Context, uri: Uri): String? {
        var result: String? = MIME_IMAGE_GENERIC
        val buffer = ByteArray(12)
        try {
            FileHelper.getInputStream(context, uri).use { input ->
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
                Bitmap.createBitmap(d.intrinsicWidth, d.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            d.setBounds(0, 0, c.width, c.height)
            d.draw(c)
            b
        } else {
            Bitmap.createBitmap(0, 0, Bitmap.Config.ARGB_8888)
        }
    }

    fun bitmapToWebp(bitmap: Bitmap): ByteArray {
        val output = ByteArrayOutputStream()
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
        val b = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
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
    fun calculateInSampleSize(
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
        val streams = Helper.duplicateInputStream(stream, 2)
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
        frames: List<ImmutablePair<Uri, Int>>
    ): Uri? {
        require(frames.isNotEmpty()) { "No frames given" }
        var width: Int
        var height: Int
        FileHelper.getInputStream(context, frames[0].left).use { input ->
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
            for (frame in frames) {
                FileHelper.getInputStream(context, frame.left).use { input ->
                    val options = BitmapFactory.Options()
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888
                    val b = BitmapFactory.decodeStream(input, null, options)
                    if (b != null) {
                        try {
                            gifEncoder.encodeFrame(b, frame.right)
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
     * @param isNecessaryToKeepOrig is it necessary to keep the original bitmap? If not recycle the original bitmap to prevent memory leak.
     */
    fun getScaledDownBitmap(
        bitmap: Bitmap,
        threshold: Int,
        isNecessaryToKeepOrig: Boolean
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
        } else getResizedBitmap(bitmap, newWidth, newHeight, isNecessaryToKeepOrig)
    }

    private fun getResizedBitmap(
        bm: Bitmap,
        newWidth: Int,
        newHeight: Int,
        isNecessaryToKeepOrig: Boolean
    ): Bitmap {
        val width = bm.width
        val height = bm.height
        val scaleWidth = newWidth.toFloat() / width
        val scaleHeight = newHeight.toFloat() / height
        val resizedBitmap = resizeBitmap(bm, scaleHeight.coerceAtMost(scaleWidth))
        if (!isNecessaryToKeepOrig && bm != resizedBitmap) { // Don't recycle if the result is the same object as the source
            bm.recycle()
        }
        return resizedBitmap
    }

    private fun resizeBitmap(src: Bitmap, targetScale: Float): Bitmap {
        val resizeParams = computeResizeParams(targetScale)
        Timber.d(">> resizing successively to scale %s", resizeParams.right)
        return successiveResize(src, resizeParams.left)
    }

    /**
     * Compute resizing parameters according to the given target scale
     *
     * @param targetScale target scale of the image to display (% of the raw dimensions)
     * @return Pair containing
     * - First : Number of half-resizes to perform
     * - Second : Corresponding scale
     */
    private fun computeResizeParams(targetScale: Float): ImmutablePair<Int, Float> {
        var resultScale = 1f
        var nbResize = 0

        // Resize when approaching the target scale by 1/3 because there may already be artifacts displayed at that point
        // (seen with full-res pictures resized to 65% with Android's default bilinear filtering)
        for (i in 1..9) if (targetScale < 0.5.pow(i.toDouble()) * 1.33) nbResize++
        if (nbResize > 0) resultScale = 0.5.pow(nbResize.toDouble()).toFloat()
        return ImmutablePair(nbResize, resultScale)
    }

    private fun successiveResize(src: Bitmap, resizeNum: Int): Bitmap {
        if (0 == resizeNum) return src
        var srcWidth = src.width
        var srcHeight = src.height
        var output = src
        for (i in 0 until resizeNum) {
            srcWidth /= 2
            srcHeight /= 2
            val temp = Bitmap.createScaledBitmap(output, srcWidth, srcHeight, true)
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
}