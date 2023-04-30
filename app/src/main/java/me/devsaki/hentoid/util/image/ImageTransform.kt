package me.devsaki.hentoid.util.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.enums.PictureEncoder
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

object ImageTransform {

    data class Params(
        val resizeEnabled: Boolean,
        val resizeMethod: Int,
        val resize1Ratio: Int,
        val resize2Height: Int,
        val resize2Width: Int,
        val resize3Ratio: Int,
        val transcodeMethod: Int,
        val transcoderAll: PictureEncoder,
        val transcoderLossy: PictureEncoder,
        val transcoderLossless: PictureEncoder,
        val transcodeQuality: Int
    )

    private val screenWidth: Int = HentoidApp.getInstance().resources.displayMetrics.widthPixels
    private val screenHeight: Int = HentoidApp.getInstance().resources.displayMetrics.heightPixels

    /**
     * Transform the given raw picture data using the given params
     */
    fun transform(source: ByteArray, params: Params): ByteArray {
        if (ImageHelper.isImageAnimated(source)) return source

        var bitmapOut = BitmapFactory.decodeByteArray(source, 0, source.size)
        if (params.resizeEnabled) {
            when (params.resizeMethod) {
                0 -> bitmapOut = resizeScreenRatio(bitmapOut, params.resize1Ratio / 100f)
                1 -> bitmapOut = resizeDims(bitmapOut, params.resize2Height, params.resize2Width)
                2 -> bitmapOut = resizePlainRatio(bitmapOut, params.resize3Ratio / 100f)
            }
        }
        val isLossless = ImageHelper.isImageLossless(source)
        return transcodeTo(bitmapOut, determineEncoder(isLossless, params), params.transcodeQuality)
    }

    private fun resizeScreenRatio(bitmap: Bitmap, ratio: Float): Bitmap {
        val targetWidth = screenWidth * ratio
        val targetHeight = screenHeight * ratio
        val widthRatio = targetWidth / bitmap.width
        val heightRatio = targetHeight / bitmap.height
        val targetRatio =
            if (widthRatio > 1 && heightRatio > 1) max(widthRatio, heightRatio)
            else min(widthRatio, heightRatio)
        return resizePlainRatio(bitmap, targetRatio)
    }

    private fun resizeDims(bitmap: Bitmap, maxHeight: Int, maxWidth: Int): Bitmap {
        val isManhwa = bitmap.height / bitmap.width > 3
        val ratio = if (isManhwa) {
            if (bitmap.width > maxWidth) maxWidth * 1f / bitmap.width else 1f
        } else {
            val maxDim = max(bitmap.width, bitmap.height) // Portrait vs. landscape
            if (maxDim > maxHeight) maxHeight * 1f / maxDim else 1f
        }
        return resizePlainRatio(bitmap, ratio)
    }

    private fun resizePlainRatio(source: Bitmap, ratio: Float): Bitmap {
        // TODO use upscaler if needed
        return if (ratio > 0.99 && ratio < 1.01) source
        else {
            val rescaled = ImageHelper.smoothRescale(source, ratio)
            val final = Bitmap.createScaledBitmap(
                rescaled,
                (source.width * ratio).toInt(),
                (source.height * ratio).toInt(), true
            )
            if (rescaled != source) rescaled.recycle()
            return final
        }
    }

    fun determineEncoder(isLossless: Boolean, params: Params): PictureEncoder {
        return when (params.transcodeMethod) {
            0 -> params.transcoderAll
            else -> if (isLossless) params.transcoderLossless else params.transcoderLossy
        }
    }

    private fun transcodeTo(bitmap: Bitmap, encoder: PictureEncoder, quality: Int): ByteArray {
        val output = ByteArrayOutputStream()
        when (encoder) {
            PictureEncoder.WEBP_LOSSY ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, output)
                else
                    bitmap.compress(Bitmap.CompressFormat.WEBP, quality, output)

            PictureEncoder.WEBP_LOSSLESS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, output)
                else
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 100, output)

            PictureEncoder.PNG ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)

            PictureEncoder.JPEG ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        }
        return output.toByteArray()
    }
}