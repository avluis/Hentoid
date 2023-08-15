package me.devsaki.hentoid.gles_renderer

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import me.devsaki.hentoid.gles_renderer.filter.GPUImageFilter

class GPUImage(val context: Context) {

    enum class ScaleType {
        CENTER_INSIDE, CENTER_CROP
    }

    companion object {
        const val SURFACE_TYPE_SURFACE_VIEW = 0
        const val SURFACE_TYPE_TEXTURE_VIEW = 1
    }

    private val renderer: GPUImageRenderer
    private var filter: GPUImageFilter

    init {
        check(supportsOpenGLES2(context)) { "OpenGL ES 2.0 is not supported on this phone." }
        val filter = GPUImageFilter()
        this.filter = filter
        renderer = GPUImageRenderer(filter)
    }

    fun clear() {
        renderer.clear()
    }

    /**
     * Checks if OpenGL ES 2.0 is supported on the current device.
     *
     * @param context the context
     * @return true, if successful
     */
    private fun supportsOpenGLES2(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val configurationInfo = activityManager.deviceConfigurationInfo
        return configurationInfo.reqGlEsVersion >= 0x20000
    }

    /**
     * Gets the images for multiple filters on a image. This can be used to
     * quickly get thumbnail images for filters. <br></br>
     * Whenever a new Bitmap is ready, the listener will be called with the
     * bitmap. The order of the calls to the listener will be the same as the
     * filter order.
     *
     * @param filters the filters which will be applied on the bitmap
     */
    fun getBitmapForMultipleFilters(
        filters: List<GPUImageFilter>,
        bitmap: Bitmap
    ): Bitmap {
        var tmpBmp = bitmap
        val renderer = GPUImageRenderer(GPUImageFilter())
        renderer.setRotation(null, flipHorizontal = false, flipVertical = true)
        val buffer = PixelBuffer(tmpBmp.width, tmpBmp.height)
        try {
            filters.forEach { filter ->
                val outputDims: Pair<Int, Int> =
                    if (filter.outputDimensions != null) filter.outputDimensions!!
                    else Pair(tmpBmp.width, tmpBmp.height)
                buffer.setRenderer(renderer)
                buffer.changeDims(outputDims.first, outputDims.second)
                try {
                    renderer.setImageBitmap(tmpBmp, true)
                    renderer.setFilter(filter)
                    tmpBmp = buffer.getBitmap()
                } finally {
                    renderer.deleteImage()
                }
            }
            return tmpBmp
        } finally {
            filters.forEach { filter -> filter.destroy() }
            buffer.destroy()
        }
    }
}