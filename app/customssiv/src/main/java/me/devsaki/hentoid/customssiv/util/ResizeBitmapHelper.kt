package me.devsaki.hentoid.customssiv.util

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.gles_renderer.GPUImage
import me.devsaki.hentoid.gles_renderer.filter.GPUImageFilter
import me.devsaki.hentoid.gles_renderer.filter.GPUImageGaussianBlurFilter
import me.devsaki.hentoid.gles_renderer.filter.GPUImageResizeFilter
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

// Credits go to https://medium.com/@petrakeas/alias-free-resize-with-renderscript-5bf15a86ce3

suspend fun resizeBitmap(
    glEsRenderer: GPUImage?,
    src: Bitmap,
    targetScale: Float
): Pair<Bitmap?, Float> = withContext(Dispatchers.Default) {
    if (null == glEsRenderer) {
        val resizeParams = computeResizeParams(targetScale)
        Timber.d(">> resizing successively to scale %s", resizeParams.second)
        Pair(successiveResize(src, resizeParams.first), resizeParams.second)
    } else {
        if (targetScale < 0.75 || (targetScale > 1.0 && targetScale < 1.55)) {
            // Don't use smooth resize above 0.75%; classic bilinear resize does the job well with more sharpness to the picture
            Pair(
                resizeGLES(glEsRenderer, src, targetScale, targetScale),
                targetScale
            )
        } else {
            Timber.d(">> No resize needed; keeping raw image")
            Pair(src, 1f)
        }
    }
}

/**
 * Compute resizing parameters according to the given target scale
 * TODO can that algorithm be merged with CustomSubsamplingScaleImageView.calculateInSampleSize ?
 *
 * @param targetScale target scale of the image to display (% of the raw dimensions)
 * @return Pair containing
 * - First : Number of half-resizes to perform (see ResizeBitmapHelper)
 * - Second : Corresponding scale
 */
private fun computeResizeParams(targetScale: Float): Pair<Int, Float> {
    var resultScale = 1f
    var nbResize = 0

    // Resize when approaching the target scale by 1/3 because there may already be artifacts displayed at that point
    // (seen with full-res pictures resized to 65% with Android's default bilinear filtering)
    for (i in 1..9) if (targetScale < 0.5.pow(i.toDouble()) * 1.33) nbResize++
    if (nbResize > 0) resultScale = 0.5.pow(nbResize.toDouble()).toFloat()

    return Pair(nbResize, resultScale)
}

private fun successiveResize(src: Bitmap, resizeNum: Int): Bitmap {
    if (0 == resizeNum) return src

    var srcWidth = src.width
    var srcHeight = src.height
    var output = src
    (0 until resizeNum).forEach {
        srcWidth /= 2
        srcHeight /= 2
        val temp = Bitmap.createScaledBitmap(output, srcWidth, srcHeight, true)
        output.recycle()
        output = temp
    }

    return output
}

private fun resizeGLES(
    glEsRenderer: GPUImage,
    src: Bitmap?,
    xScale: Float,
    yScale: Float
): Bitmap? {
    // Calculate gaussian's radius
    val sigma = (1 / xScale) / Math.PI.toFloat()
    // https://android.googlesource.com/platform/frameworks/rs/+/master/cpu_ref/rsCpuIntrinsicBlur.cpp
    var radius = 3f * sigma /* - 1.5f*/ // Works better that way
    radius = min(25.0, max(0.0001, radius.toDouble())).toFloat()
    Timber.v(">> using sigma=%s for xScale=%s => radius=%s", sigma, xScale, radius)

    // Defensive programming in case the threading/view recycling recycles a bitmap just before that methods is reached
    if (null == src || src.isRecycled) return src

    val srcWidth = src.width
    val srcHeight = src.height
    // Must be multiple of 2
    var dstWidth = (srcWidth * xScale).roundToInt()
    dstWidth += (dstWidth % 2)
    var dstHeight = (srcHeight * yScale).roundToInt()
    dstHeight += (dstHeight % 2)
    src.setHasAlpha(false)

    Timber.v(
        ">> bmp IN %dx%d DST %dx%d (scale %.2f)",
        srcWidth,
        srcHeight,
        dstWidth,
        dstHeight,
        xScale
    )

    val filterList: MutableList<GPUImageFilter> = ArrayList()
    filterList.add(GPUImageGaussianBlurFilter(radius))
    filterList.add(GPUImageResizeFilter(dstWidth, dstHeight))

    val out = glEsRenderer.getBitmapForMultipleFilters(filterList, src)
    src.recycle()

    return out
}