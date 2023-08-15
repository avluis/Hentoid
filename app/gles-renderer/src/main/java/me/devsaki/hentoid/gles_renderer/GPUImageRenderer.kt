package me.devsaki.hentoid.gles_renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import me.devsaki.hentoid.gles_renderer.filter.GPUImageFilter
import me.devsaki.hentoid.gles_renderer.util.OpenGlUtils
import me.devsaki.hentoid.gles_renderer.util.Rotation
import me.devsaki.hentoid.gles_renderer.util.TextureRotationUtil
import me.devsaki.hentoid.gles_renderer.util.TextureRotationUtil.Companion.TEXTURE_NO_ROTATION
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.LinkedList
import java.util.Queue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GPUImageRenderer(private var filter: GPUImageFilter) : GLSurfaceView.Renderer {
    companion object {
        private const val NO_IMAGE = -1
        val CUBE = floatArrayOf(
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f
        )
    }

    private var glTextureId = NO_IMAGE
    private val glCubeBuffer: FloatBuffer = ByteBuffer.allocateDirect(CUBE.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    private val glTextureBuffer: FloatBuffer =
        ByteBuffer.allocateDirect(TEXTURE_NO_ROTATION.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    private var glRgbBuffer: IntBuffer? = null

    private var outputWidth = 0
    private var outputHeight = 0
    private var imageWidth = 0
    private var imageHeight = 0
    private var addedPadding = 0

    private val runOnDraw: Queue<Runnable> = LinkedList()
    private val runOnDrawEnd: Queue<Runnable> = LinkedList()
    private var rotation: Rotation? = null
    private var flipHorizontal = false
    private var flipVertical = false
    private var scaleType = GPUImage.ScaleType.CENTER_CROP

    private var backgroundRed = 0f
    private var backgroundGreen = 0f
    private var backgroundBlue = 0f

    init {
        glCubeBuffer.put(CUBE).position(0)
        setRotation(Rotation.NORMAL, flipHorizontal = false, flipVertical = false)
    }

    fun clear() {
        glCubeBuffer.clear()
        glTextureBuffer.clear()
        glRgbBuffer?.clear()
    }

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(backgroundRed, backgroundGreen, backgroundBlue, 1f)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        filter.ifNeedInit()
    }


    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        outputWidth = width
        outputHeight = height
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(filter.getProgram())
        filter.onOutputSizeChanged(width, height)
        adjustImageScaling()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        runAll(runOnDraw)
        filter.onDraw(glTextureId, glCubeBuffer, glTextureBuffer)
        runAll(runOnDrawEnd)
    }

    /**
     * Sets the background color
     *
     * @param red   red color value
     * @param green green color value
     * @param blue  red color value
     */
    fun setBackgroundColor(red: Float, green: Float, blue: Float) {
        backgroundRed = red
        backgroundGreen = green
        backgroundBlue = blue
    }

    private fun runAll(queue: Queue<Runnable>) {
        synchronized(queue) {
            while (!queue.isEmpty()) {
                queue.poll()!!.run()
            }
        }
    }

    fun setFilter(filter: GPUImageFilter) {
        //runOnDraw {
        val oldFilter = this.filter
        this.filter = filter
        oldFilter.destroy()
        this.filter.ifNeedInit()
        GLES20.glUseProgram(this.filter.getProgram())
        this.filter.onOutputSizeChanged(outputWidth, outputHeight)
        //}
    }

    fun deleteImage() {
        GLES20.glDeleteTextures(
            1, intArrayOf(
                glTextureId
            ), 0
        )
        glTextureId = NO_IMAGE
    }

    fun setImageBitmap(bitmap: Bitmap) {
        setImageBitmap(bitmap, true)
    }

    fun setImageBitmap(bitmap: Bitmap, recycle: Boolean) {
        //runOnDraw {
        // TODO optimize that
        var resizedBitmap: Bitmap? = null
        if (bitmap.width % 2 == 1) {
            resizedBitmap = Bitmap.createBitmap(
                bitmap.width + 1, bitmap.height,
                Bitmap.Config.ARGB_8888
            )
            resizedBitmap.density = bitmap.density

            val can = Canvas(resizedBitmap)
            can.drawARGB(0x00, 0x00, 0x00, 0x00)
            can.drawBitmap(bitmap, 0f, 0f, null)

            addedPadding = 1
        } else {
            addedPadding = 0
        }
        imageWidth = bitmap.width
        imageHeight = bitmap.height
        glTextureId = OpenGlUtils.loadTexture(
            resizedBitmap ?: bitmap, glTextureId, recycle
        )
        resizedBitmap?.recycle()
        adjustImageScaling()
        //}
    }

    fun setScaleType(scaleType: GPUImage.ScaleType) {
        this.scaleType = scaleType
    }

    fun getFrameWidth(): Int {
        return outputWidth
    }

    fun getFrameHeight(): Int {
        return outputHeight
    }

    private fun adjustImageScaling() {
        if (0 == imageWidth + imageHeight || 0 == outputWidth + outputHeight) return
        /*
        var outputWidth = outputWidth.toFloat()
        var outputHeight = outputHeight.toFloat()
        if (rotation === Rotation.ROTATION_270 || rotation === Rotation.ROTATION_90) {
            outputWidth = this.outputHeight.toFloat()
            outputHeight = this.outputWidth.toFloat()
        }
        val ratio1 = outputWidth / imageWidth
        val ratio2 = outputHeight / imageHeight
        val ratioMax = ratio1.coerceAtLeast(ratio2)
        val imageWidthNew = (imageWidth * ratioMax).roundToInt()
        val imageHeightNew = (imageHeight * ratioMax).roundToInt()
        val ratioWidth = imageWidthNew / outputWidth
        val ratioHeight = imageHeightNew / outputHeight
         */
        var cube = CUBE
        var textureCords: FloatArray =
            TextureRotationUtil.getRotation(rotation, flipHorizontal, flipVertical)
        /*
        if (scaleType === GPUImage.ScaleType.CENTER_CROP) {
            val distHorizontal = (1 - 1 / ratioWidth) / 2
            val distVertical = (1 - 1 / ratioHeight) / 2
            textureCords = floatArrayOf(
                addDistance(textureCords[0], distHorizontal),
                addDistance(textureCords[1], distVertical),
                addDistance(textureCords[2], distHorizontal),
                addDistance(textureCords[3], distVertical),
                addDistance(textureCords[4], distHorizontal),
                addDistance(textureCords[5], distVertical),
                addDistance(textureCords[6], distHorizontal),
                addDistance(textureCords[7], distVertical)
            )
        } else {
            cube = floatArrayOf(
                CUBE[0] / ratioHeight, CUBE[1] / ratioWidth,
                CUBE[2] / ratioHeight, CUBE[3] / ratioWidth,
                CUBE[4] / ratioHeight, CUBE[5] / ratioWidth,
                CUBE[6] / ratioHeight, CUBE[7] / ratioWidth
            )
        }
         */
        glCubeBuffer.clear()
        glCubeBuffer.put(cube).position(0)
        glTextureBuffer.clear()
        glTextureBuffer.put(textureCords).position(0)
    }

    private fun addDistance(coordinate: Float, distance: Float): Float {
        return if (coordinate == 0.0f) distance else 1 - distance
    }

    fun setRotationCamera(
        rotation: Rotation?, flipHorizontal: Boolean,
        flipVertical: Boolean
    ) {
        setRotation(rotation, flipVertical, flipHorizontal)
    }

    fun setRotation(rotation: Rotation?) {
        this.rotation = rotation
        adjustImageScaling()
    }

    fun setRotation(
        rotation: Rotation?,
        flipHorizontal: Boolean, flipVertical: Boolean
    ) {
        this.flipHorizontal = flipHorizontal
        this.flipVertical = flipVertical
        setRotation(rotation)
    }

    fun getRotation(): Rotation? {
        return rotation
    }

    fun isFlippedHorizontally(): Boolean {
        return flipHorizontal
    }

    fun isFlippedVertically(): Boolean {
        return flipVertical
    }

    fun runOnDraw(runnable: Runnable) {
        synchronized(runOnDraw) { runOnDraw.add(runnable) }
    }

    fun runOnDrawEnd(runnable: Runnable) {
        synchronized(runOnDrawEnd) { runOnDrawEnd.add(runnable) }
    }
}