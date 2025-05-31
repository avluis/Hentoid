package me.devsaki.hentoid.gles_renderer

import android.graphics.Bitmap
import android.opengl.GLES20.GL_RGBA
import android.opengl.GLES20.GL_UNSIGNED_BYTE
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface
import javax.microedition.khronos.opengles.GL10

private const val TAG = "PixelBuffer"
private const val LIST_CONFIGS = false
private const val EGL_CONTEXT_CLIENT_VERSION = 0x3098

internal class PixelBuffer(private var width: Int, private var height: Int) {
    // borrow this interface
    private var renderer: GLSurfaceView.Renderer? = null

    private val egl10: EGL10
    private val eglDisplay: EGLDisplay
    private val eglConfig: EGLConfig
    private val eglContext: EGLContext
    private val gl10: GL10
    private lateinit var eglConfigs: Array<EGLConfig?>
    private var eglSurface: EGLSurface

    private var mThreadOwner: String? = null

    init {
        val version = IntArray(2)
        val attribList = intArrayOf(
            EGL10.EGL_WIDTH, this.width, EGL10.EGL_HEIGHT, this.height, EGL10.EGL_NONE
        )

        // No error checking performed, minimum required code to elucidate logic
        egl10 = EGLContext.getEGL() as EGL10
        eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
        egl10.eglInitialize(eglDisplay, version)
        eglConfig = chooseConfig() // Choosing a config is a little more complicated

        // eglContext = egl10.eglCreateContext(eglDisplay, eglConfig,
        // EGL_NO_CONTEXT, null);
        val attribList2 = intArrayOf(
            EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE
        )
        eglContext =
            egl10.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attribList2)
        eglSurface = egl10.eglCreatePbufferSurface(eglDisplay, eglConfig, attribList)
        egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        gl10 = eglContext.gl as GL10

        // Record thread owner of OpenGL context
        mThreadOwner = Thread.currentThread().name
    }

    fun destroy() {
        renderer!!.onDrawFrame(gl10)
        egl10.eglMakeCurrent(
            eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT
        )
        egl10.eglDestroySurface(eglDisplay, eglSurface)
        egl10.eglDestroyContext(eglDisplay, eglContext)
        egl10.eglTerminate(eglDisplay)
    }

    fun changeDims(newWidth: Int, newHeight: Int) {
        if (newWidth == width && newHeight == height) return
        width = newWidth
        height = newHeight

        val attribList = intArrayOf(
            EGL10.EGL_WIDTH, this.width, EGL10.EGL_HEIGHT, height, EGL10.EGL_NONE
        )
        egl10.eglDestroySurface(eglDisplay, eglSurface)
        eglSurface = egl10.eglCreatePbufferSurface(eglDisplay, eglConfig, attribList)
        egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

        renderer?.onSurfaceChanged(gl10, width, height)
    }

    fun setRenderer(renderer: GLSurfaceView.Renderer) {
        this.renderer = renderer

        // Does this thread own the OpenGL context?
        check(Thread.currentThread().name == mThreadOwner)

        // Call the renderer initialization routines
        renderer.onSurfaceCreated(gl10, eglConfig)
        renderer.onSurfaceChanged(gl10, width, height)
    }

    fun getBitmap(outDimensions: Pair<Int, Int>? = null): Bitmap {
        // Do we have a renderer ?
        check(renderer != null)
        // Does this thread own the OpenGL context?
        check(Thread.currentThread().name == mThreadOwner)

        renderer!!.onDrawFrame(gl10)
        return convertToBitmap(outDimensions)
    }

    private fun convertToBitmap(outDimensions: Pair<Int, Int>?): Bitmap {
        val outX = outDimensions?.first ?: width
        val outY = outDimensions?.second ?: height

        val mPixelBuf = ByteBuffer.allocate(outX * outY * 4)
        mPixelBuf.order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, outX, outY, GL_RGBA, GL_UNSIGNED_BYTE, mPixelBuf)
        /*
        val fence = GLES30.glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
        GLES30.glClientWaitSync(fence, 0, 0)

        val length0 = IntArray(1)
        val status0 = IntArray(1)
        GLES30.glGetSynciv( fence, GLES30.GL_SYNC_STATUS, 1, length0, 0, status0, 0 );
*/
        mPixelBuf.rewind()

        val bmp = Bitmap.createBitmap(outX, outY, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(mPixelBuf)
        return bmp
    }

    private fun chooseConfig(): EGLConfig {
        val attribList = intArrayOf(
            EGL10.EGL_DEPTH_SIZE,
            0,
            EGL10.EGL_STENCIL_SIZE,
            0,
            EGL10.EGL_RED_SIZE,
            8,
            EGL10.EGL_GREEN_SIZE,
            8,
            EGL10.EGL_BLUE_SIZE,
            8,
            EGL10.EGL_ALPHA_SIZE,
            8,
            EGL10.EGL_RENDERABLE_TYPE,
            4,
            EGL10.EGL_NONE
        )

        // No error checking performed, minimum required code to elucidate logic
        // Expand on this logic to be more selective in choosing a configuration
        val numConfig = IntArray(1)
        egl10.eglChooseConfig(eglDisplay, attribList, null, 0, numConfig)
        val configSize = numConfig[0]
        eglConfigs = arrayOfNulls(configSize)
        egl10.eglChooseConfig(eglDisplay, attribList, eglConfigs, configSize, numConfig)
        if (LIST_CONFIGS) listConfig()
        return eglConfigs[0]!! // Best match is probably the first configuration
    }

    private fun listConfig() {
        Timber.tag(TAG).i("Config List {")
        for (config in eglConfigs) {
            // Expand on this logic to dump other attributes
            val d: Int = getConfigAttrib(config, EGL10.EGL_DEPTH_SIZE)
            val s: Int = getConfigAttrib(config, EGL10.EGL_STENCIL_SIZE)
            val r: Int = getConfigAttrib(config, EGL10.EGL_RED_SIZE)
            val g: Int = getConfigAttrib(config, EGL10.EGL_GREEN_SIZE)
            val b: Int = getConfigAttrib(config, EGL10.EGL_BLUE_SIZE)
            val a: Int = getConfigAttrib(config, EGL10.EGL_ALPHA_SIZE)
            Timber.tag(TAG)
                .i("    <d,s,r,g,b,a> = <%d,%d,%d,%d,%d,%d>", d, s, r, g, b, a)
        }
        Timber.tag(TAG).i("}")
    }

    private fun getConfigAttrib(config: EGLConfig?, attribute: Int): Int {
        val value = IntArray(1)
        return if (egl10.eglGetConfigAttrib(eglDisplay, config, attribute, value)) value[0] else 0
    }
}