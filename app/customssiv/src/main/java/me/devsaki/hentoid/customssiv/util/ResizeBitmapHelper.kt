package me.devsaki.hentoid.customssiv.util

import android.graphics.Bitmap
import android.graphics.PointF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.gles_renderer.GPUImage
import me.devsaki.hentoid.gles_renderer.filter.GPUImageFilter
import me.devsaki.hentoid.gles_renderer.filter.GPUImageGaussianBlurFilter
import me.devsaki.hentoid.gles_renderer.filter.GPUImageResizeFilter
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

// Credits go to https://medium.com/@petrakeas/alias-free-resize-with-renderscript-5bf15a86ce3

suspend fun resizeBitmap(
    glEsRenderer: GPUImage?,
    src: Bitmap,
    targetScale: Float
): Pair<Bitmap, Float> = withContext(Dispatchers.Default) {
    resizeBitmap(glEsRenderer, src, PointF(targetScale, targetScale))
}

suspend fun resizeBitmap(
    glEsRenderer: GPUImage?,
    src: Bitmap,
    targetScale: PointF
): Pair<Bitmap, Float> = withContext(Dispatchers.Default) {
    if (abs(targetScale.x - targetScale.y) < 0.001)
        resizeBitmapProportional(glEsRenderer, src, targetScale.x)
    else
        resizeBitmapNonProportional(glEsRenderer, src, targetScale)
}

private fun resizeBitmapProportional(
    glEsRenderer: GPUImage?,
    src: Bitmap,
    targetScale: Float
): Pair<Bitmap, Float> {
    Timber.d(">> target scale $targetScale")
    if (null == glEsRenderer) {
        val resizeParams = computeResizeParams(targetScale)
        Timber.d(">> resizing to scale %s", resizeParams.second)
        return Pair(successiveResizeProportional(src, resizeParams.first), resizeParams.second)
    } else {
        if (targetScale < 0.75 || (targetScale > 1.0 && targetScale < 1.55)) {
            // Don't use smooth resize above 0.75%; classic bilinear resize does the job well with more sharpness to the picture
            return Pair(
                resizeGLES(glEsRenderer, src, targetScale, targetScale),
                targetScale
            )
        } else {
            Timber.d(">> No resize needed; keeping raw bitmap")
            return Pair(src, 1f)
        }
    }
}

private fun resizeBitmapNonProportional(
    glEsRenderer: GPUImage?,
    src: Bitmap,
    targetScale: PointF
): Pair<Bitmap, Float> {
    val meanScale = (targetScale.x + targetScale.y) / 2f
    Timber.d(">> target meanScale $meanScale")
    if (null == glEsRenderer) {
        val xAmplitude = abs(1 - targetScale.x)
        val yAmplitude = abs(1 - targetScale.y)
        val resizeParams =
            computeResizeParams(if (xAmplitude < yAmplitude) targetScale.x else targetScale.y)
        if (resizeParams.first > 0) {
            Timber.d(">> resizing to scale %s", resizeParams.second)
            return Pair(
                successiveResizeNonProportional(src, resizeParams.first, targetScale),
                resizeParams.second
            )
        }
    } else if (meanScale < 0.75 || (meanScale > 1.0 && meanScale < 1.55)) {
        // Don't use smooth resize above 0.75%; classic bilinear resize does the job well with more sharpness to the picture
        return Pair(resizeGLES(glEsRenderer, src, targetScale.x, targetScale.y), 1f)
    }
    // One shot bilinear
    // No successive resize   or   0.75 <= Scale <= 1   or   scale >= 1.55
    val xTarget = (src.width * targetScale.x).roundToInt()
    val yTarget = (src.height * targetScale.y).roundToInt()
    Timber.d(">> Using native bilinear => ${xTarget}x$yTarget")
    try {
        return Pair(Bitmap.createScaledBitmap(src, xTarget, yTarget, true), 1f)
    } finally {
        src.recycle()
    }
}


/**
 * Compute resizing parameters according to the given target scale
 * TODO can that algorithm be merged with CustomSubsamplingScaleImageView.calculateInSampleSize ?
 *
 * @param targetScale target scale of the image to display (% of the raw dimensions)
 * @return Pair containing
 * - First : Number of successive half-resizes to perform
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

private fun successiveResizeProportional(src: Bitmap, resizeNum: Int): Bitmap {
    if (0 == resizeNum) return src

    var srcWidth = src.width
    var srcHeight = src.height
    var output = src
    (0 until resizeNum).forEach {
        srcWidth /= 2
        srcHeight /= 2
        // Using bilinear filtering
        val temp = Bitmap.createScaledBitmap(output, srcWidth, srcHeight, true)
        output.recycle()
        output = temp
    }

    return output
}

private fun successiveResizeNonProportional(
    src: Bitmap,
    resizeNum: Int,
    targetScale: PointF
): Bitmap {
    if (0 == resizeNum) return src
    val factorx = targetScale.x.pow(1f / resizeNum)
    val factory = targetScale.y.pow(1f / resizeNum)

    var srcWidth = src.width.toFloat()
    var srcHeight = src.height.toFloat()
    var output = src
    (0 until resizeNum).forEach {
        srcWidth *= factorx
        srcHeight *= factory
        // Using bilinear filtering
        val temp = Bitmap.createScaledBitmap(output, srcWidth.toInt(), srcHeight.toInt(), true)
        output.recycle()
        output = temp
    }

    return output
}

private fun resizeGLES(
    glEsRenderer: GPUImage,
    src: Bitmap,
    xScale: Float,
    yScale: Float
): Bitmap {
    val meanScale = (xScale + yScale) / 2f
    // Calculate gaussian's radius
    val sigma = (1 / meanScale) / Math.PI.toFloat()
    // https://android.googlesource.com/platform/frameworks/rs/+/master/cpu_ref/rsCpuIntrinsicBlur.cpp
    var radius = 3f * sigma /* - 1.5f*/ // Works better that way
    radius = min(25.0, max(0.0001, radius.toDouble())).toFloat()
    Timber.v(">> using sigma=%s for meanScale=%s => radius=%s", sigma, meanScale, radius)

    // Defensive programming in case the threading/view recycling recycles a bitmap just before that methods is reached
    if (src.isRecycled) return src

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