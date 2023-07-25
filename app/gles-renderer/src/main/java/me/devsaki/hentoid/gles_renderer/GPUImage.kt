package me.devsaki.hentoid.gles_renderer

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.view.WindowManager
import me.devsaki.hentoid.gles_renderer.filter.GPUImageFilter
import me.devsaki.hentoid.gles_renderer.filter.GPUImageFilterGroup
import me.devsaki.hentoid.gles_renderer.util.Rotation

class GPUImage(val context: Context) {

    enum class ScaleType {
        CENTER_INSIDE, CENTER_CROP
    }

    companion object {
        const val SURFACE_TYPE_SURFACE_VIEW = 0
        const val SURFACE_TYPE_TEXTURE_VIEW = 1
    }

    private val renderer: GPUImageRenderer
    private val surfaceType = SURFACE_TYPE_SURFACE_VIEW
    private var filter: GPUImageFilter
    private var currentBitmap: Bitmap? = null
    private var scaleType = ScaleType.CENTER_CROP
    private val scaleWidth = 0
    private var scaleHeight = 0

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
     * Sets the background color
     *
     * @param red   red color value
     * @param green green color value
     * @param blue  red color value
     */
    fun setBackgroundColor(red: Float, green: Float, blue: Float) {
        renderer.setBackgroundColor(red, green, blue)
    }

    /**
     * Request the preview to be rendered again.
     */
    fun requestRender() {
        // TODO nothing ?
    }

    /**
     * Sets the filter which should be applied to the image which was (or will
     * be) set by setImage(...).
     *
     * @param filter the new filter
     */
    fun setFilter(filter: GPUImageFilter) {
        this.filter = filter
        renderer.setFilter(filter)
        requestRender()
    }

    /**
     * Sets the image on which the filter should be applied.
     *
     * @param bitmap the new image
     */
    fun setImage(bitmap: Bitmap) {
        currentBitmap = bitmap
        renderer.setImageBitmap(bitmap, false)
        requestRender()
    }

    /**
     * This sets the scale type of GPUImage. This has to be run before setting the image.
     * If image is set and scale type changed, image needs to be reset.
     *
     * @param scaleType The new ScaleType
     */
    fun setScaleType(scaleType: ScaleType) {
        this.scaleType = scaleType
        renderer.setScaleType(scaleType)
        renderer.deleteImage()
        currentBitmap = null
        requestRender()
    }

    /**
     * This gets the size of the image. This makes it easier to adjust
     * the size of your imagePreview to the the size of the scaled image.
     *
     * @return array with width and height of bitmap image
     */
    fun getScaleSize(): IntArray {
        return intArrayOf(scaleWidth, scaleHeight)
    }

    /**
     * Sets the rotation of the displayed image.
     *
     * @param rotation new rotation
     */
    fun setRotation(rotation: Rotation) {
        renderer.setRotation(rotation)
    }

    /**
     * Sets the rotation of the displayed image with flip options.
     *
     * @param rotation new rotation
     */
    fun setRotation(rotation: Rotation, flipHorizontal: Boolean, flipVertical: Boolean) {
        renderer.setRotation(rotation, flipHorizontal, flipVertical)
    }

    /**
     * Deletes the current image.
     */
    fun deleteImage() {
        renderer.deleteImage()
        currentBitmap = null
        requestRender()
    }

    private fun getPath(uri: Uri): String? {
        val projection = arrayOf(
            MediaStore.Images.Media.DATA
        )
        val cursor = context.contentResolver
            .query(uri, projection, null, null, null)
        var path: String? = null
        if (cursor == null) {
            return null
        }
        if (cursor.moveToFirst()) {
            val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            path = cursor.getString(pathIndex)
        }
        cursor.close()
        return path
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
        try {
            val buffer = PixelBuffer(tmpBmp.width, tmpBmp.height)
            try {
                filters.forEach { filter ->
                    val outputDims: Pair<Int, Int> =
                        if (filter.outputDimensions != null) filter.outputDimensions!!
                        else Pair(tmpBmp.width, tmpBmp.height)
                    buffer.setRenderer(renderer)
                    buffer.changeDims(outputDims.first, outputDims.second)

                    renderer.setImageBitmap(tmpBmp, true)
                    renderer.setFilter(filter)

                    tmpBmp = buffer.getBitmap()
                }
                return tmpBmp
            } finally {
                filters.forEach { filter -> filter.destroy() }
                buffer.destroy()
            }
        } finally {
            renderer.deleteImage()
        }
    }

    private fun computeFilters(filters: List<GPUImageFilter>): GPUImageFilter {
        if (filters.isEmpty()) return GPUImageFilter()
        if (1 == filters.size) return filters[0]

        var filterGroup: GPUImageFilterGroup? =
            filters.find { f -> f is GPUImageFilterGroup } as GPUImageFilterGroup?
        if (null == filterGroup) {
            filterGroup = GPUImageFilterGroup(ArrayList())
        }
        filters.forEach {
            if (it !is GPUImageFilterGroup) filterGroup.addFilter(it)
        }
        return filterGroup
    }

    /**
     * Gets the images for multiple filters on a image. This can be used to
     * quickly get thumbnail images for filters. <br></br>
     * Whenever a new Bitmap is ready, the listener will be called with the
     * bitmap. The order of the calls to the listener will be the same as the
     * filter order.
     *
     * @param bitmap   the bitmap on which the filters will be applied
     * @param filters  the filters which will be applied on the bitmap
     * @param listener the listener on which the results will be notified
     */
    fun getBitmapForMultipleFilters(
        bitmap: Bitmap,
        filters: List<GPUImageFilter>, listener: ResponseListener<Bitmap>
    ) {
        if (filters.isEmpty()) {
            return
        }
        val renderer = GPUImageRenderer(filters[0])
        renderer.setImageBitmap(bitmap, false)
        val buffer = PixelBuffer(bitmap.width, bitmap.height)
        buffer.setRenderer(renderer)
        for (filter in filters) {
            renderer.setFilter(filter)
            listener.response(buffer.getBitmap())
            filter.destroy()
        }
        renderer.deleteImage()
        buffer.destroy()
    }

    /**
     * Runs the given Runnable on the OpenGL thread.
     *
     * @param runnable The runnable to be run on the OpenGL thread.
     */
    fun runOnGLThread(runnable: Runnable) {
        renderer.runOnDrawEnd(runnable)
    }
/*
    private fun getOutputWidth(): Int {
        return if (renderer.getFrameWidth() != 0) {
            renderer.getFrameWidth()
        } else if (currentBitmap != null) {
            currentBitmap!!.width
        } else {
            val windowManager =
                context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            display.width
        }
    }

    private fun getOutputHeight(): Int {
        return if (renderer.getFrameHeight() != 0) {
            renderer.getFrameHeight()
        } else if (currentBitmap != null) {
            currentBitmap!!.height
        } else {
            val windowManager =
                context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            display.height
        }
    }

 */
    /*
        inner class LoadImageUriTask(gpuImage: GPUImage, private val uri: Uri) :
            LoadImageTask(gpuImage) {
            override fun decode(options: BitmapFactory.Options?): Bitmap? {
                try {
                    val inputStream: InputStream? = if (uri.scheme!!.startsWith("http") || uri.scheme!!.startsWith("https")) {
                            URL(uri.toString()).openStream()
                        } else if (uri.path!!.startsWith("/android_asset/")) {
                            context.assets.open(uri.path!!.substring("/android_asset/".length))
                        } else {
                            context.contentResolver.openInputStream(uri)
                        }
                    return BitmapFactory.decodeStream(inputStream, null, options)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return null
            }

            @get:Throws(IOException::class)
            override val imageOrientation: Int
                get() {
                    val cursor: Cursor? = context.contentResolver.query(
                        uri,
                        arrayOf(MediaStore.Images.ImageColumns.ORIENTATION),
                        null,
                        null,
                        null
                    )
                    cursor?.let {
                        if (it.count != 1) {
                            return 0
                        }
                        it.moveToFirst()
                        val orientation = it.getInt(0)
                        it.close()
                        return orientation
                    }
                    return 0
                }
        }

        inner class LoadImageTask(private val gpuImage: GPUImage) : AsyncTask<Void?, Void?, Bitmap?>() {
            private var outputWidth = 0
            private var outputHeight = 0

            override fun doInBackground(vararg params: Void?): Bitmap? {
                if (renderer.getFrameWidth() == 0) {
                    try {
                        synchronized(renderer.surfaceChangedWaiter) {

                        }
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
                outputWidth = getOutputWidth()
                outputHeight = getOutputHeight()
                return loadResizedImage()
            }

            override fun onPostExecute(bitmap: Bitmap?) {
                super.onPostExecute(bitmap)
                gpuImage.deleteImage()
                gpuImage.setImage(bitmap)
            }

            fun decode(options: BitmapFactory.Options?): Bitmap?

            private fun loadResizedImage(): Bitmap? {
                var options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                decode(options)
                var scale = 1
                while (checkSize(
                        options.outWidth / scale > outputWidth,
                        options.outHeight / scale > outputHeight
                    )
                ) {
                    scale++
                }
                scale--
                if (scale < 1) {
                    scale = 1
                }
                options = BitmapFactory.Options()
                options.inSampleSize = scale
                options.inPreferredConfig = Bitmap.Config.RGB_565
                options.inPurgeable = true
                options.inTempStorage = ByteArray(32 * 1024)
                var bitmap: Bitmap? = decode(options) ?: return null
                bitmap = rotateImage(bitmap)
                bitmap = scaleBitmap(bitmap)
                return bitmap
            }

            private fun scaleBitmap(bitmap: Bitmap?): Bitmap? {
                // resize to desired dimensions
                var bitmap = bitmap
                val width = bitmap!!.width
                val height = bitmap.height
                val newSize = getScaleSize(width, height)
                var workBitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    newSize[0], newSize[1], true
                )
                if (workBitmap != bitmap) {
                    bitmap.recycle()
                    bitmap = workBitmap
                    System.gc()
                }
                if (scaleType == ScaleType.CENTER_CROP) {
                    // Crop it
                    val diffWidth = newSize[0] - outputWidth
                    val diffHeight = newSize[1] - outputHeight
                    workBitmap = Bitmap.createBitmap(
                        bitmap, diffWidth / 2, diffHeight / 2,
                        newSize[0] - diffWidth, newSize[1] - diffHeight
                    )
                    if (workBitmap != bitmap) {
                        bitmap.recycle()
                        bitmap = workBitmap
                    }
                }
                return bitmap
            }

            /**
             * Retrieve the scaling size for the image dependent on the ScaleType.<br></br>
             * <br></br>
             * If CROP: sides are same size or bigger than output's sides<br></br>
             * Else   : sides are same size or smaller than output's sides
             */
            private fun getScaleSize(width: Int, height: Int): IntArray {
                val newWidth: Float
                val newHeight: Float
                val withRatio = width.toFloat() / outputWidth
                val heightRatio = height.toFloat() / outputHeight
                val adjustWidth =
                    if (scaleType == ScaleType.CENTER_CROP) withRatio > heightRatio else withRatio < heightRatio
                if (adjustWidth) {
                    newHeight = outputHeight.toFloat()
                    newWidth = newHeight / height * width
                } else {
                    newWidth = outputWidth.toFloat()
                    newHeight = newWidth / width * height
                }
                scaleWidth = Math.round(newWidth)
                scaleHeight = Math.round(newHeight)
                return intArrayOf(Math.round(newWidth), Math.round(newHeight))
            }

            private fun checkSize(widthBigger: Boolean, heightBigger: Boolean): Boolean {
                return if (scaleType == ScaleType.CENTER_CROP) {
                    widthBigger && heightBigger
                } else {
                    widthBigger || heightBigger
                }
            }

            private fun rotateImage(bitmap: Bitmap?): Bitmap? {
                if (bitmap == null) {
                    return null
                }
                var rotatedBitmap = bitmap
                try {
                    val orientation = imageOrientation
                    if (orientation != 0) {
                        val matrix = Matrix()
                        matrix.postRotate(orientation.toFloat())
                        rotatedBitmap = Bitmap.createBitmap(
                            bitmap, 0, 0, bitmap.width,
                            bitmap.height, matrix, true
                        )
                        bitmap.recycle()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                return rotatedBitmap
            }

            @get:Throws(IOException::class)
            protected abstract val imageOrientation: Int
        }

     */

    interface ResponseListener<T> {
        fun response(item: T?)
    }

    fun getRenderer(): GPUImageRenderer {
        return renderer
    }
}