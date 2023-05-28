package me.devsaki.hentoid.gpu_render.filter

import android.content.Context
import android.graphics.PointF
import android.opengl.GLES20
import me.devsaki.hentoid.gpu_render.util.OpenGlUtils
import java.io.InputStream
import java.nio.FloatBuffer
import java.util.LinkedList
import java.util.Scanner

open class GPUImageFilter {
    companion object {
        const val NO_FILTER_VERTEX_SHADER = """attribute vec4 position;
attribute vec4 inputTextureCoordinate;
 
varying vec2 textureCoordinate;
 
void main()
{
    gl_Position = position;
    textureCoordinate = inputTextureCoordinate.xy;
}"""
        const val NO_FILTER_FRAGMENT_SHADER = """varying highp vec2 textureCoordinate;
 
uniform sampler2D inputImageTexture;
 
void main()
{
     gl_FragColor = texture2D(inputImageTexture, textureCoordinate);
}"""
    }

    var outputDimensions : Pair<Int, Int>? = null
        protected set

    private val vertexShader: String
    private val fragmentShader: String
    private val runOnDraw: LinkedList<Runnable> = LinkedList()
    private var glProgId = 0
    private var glAttribPosition = 0
    private var glUniformTexture = 0
    private var glAttribTextureCoordinate = 0
    private var outputWidth = 0
    private var outputHeight = 0
    private var isInitialized = false

    constructor() {
        this.vertexShader = NO_FILTER_VERTEX_SHADER
        this.fragmentShader = NO_FILTER_FRAGMENT_SHADER
    }

    constructor(vertexShader: String, fragmentShader: String) {
        this.vertexShader = vertexShader
        this.fragmentShader = fragmentShader
    }

    private fun init() {
        onInit()
        onInitialized()
    }

    open fun onInit() {
        glProgId = OpenGlUtils.loadProgram(vertexShader, fragmentShader)
        glAttribPosition = GLES20.glGetAttribLocation(glProgId, "position")
        glUniformTexture = GLES20.glGetUniformLocation(glProgId, "inputImageTexture")
        glAttribTextureCoordinate = GLES20.glGetAttribLocation(glProgId, "inputTextureCoordinate")
        isInitialized = true
    }

    open fun onInitialized() {}

    fun ifNeedInit() {
        if (!isInitialized) init()
    }

    fun destroy() {
        isInitialized = false
        GLES20.glDeleteProgram(glProgId)
        onDestroy()
    }

    open fun onDestroy() {}

    open fun onOutputSizeChanged(width: Int, height: Int) {
        outputWidth = width
        outputHeight = height
    }

    open fun onDraw(
        textureId: Int, cubeBuffer: FloatBuffer,
        textureBuffer: FloatBuffer
    ) {
        GLES20.glUseProgram(glProgId)
        runPendingOnDrawTasks()
        if (!isInitialized) {
            return
        }
        cubeBuffer.position(0)
        GLES20.glVertexAttribPointer(glAttribPosition, 2, GLES20.GL_FLOAT, false, 0, cubeBuffer)
        GLES20.glEnableVertexAttribArray(glAttribPosition)
        textureBuffer.position(0)
        GLES20.glVertexAttribPointer(
            glAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0,
            textureBuffer
        )
        GLES20.glEnableVertexAttribArray(glAttribTextureCoordinate)
        if (textureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glUniform1i(glUniformTexture, 0)
        }
        onDrawArraysPre()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(glAttribPosition)
        GLES20.glDisableVertexAttribArray(glAttribTextureCoordinate)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    private fun onDrawArraysPre() {}

    fun runPendingOnDrawTasks() {
        synchronized(runOnDraw) {
            while (!runOnDraw.isEmpty()) {
                runOnDraw.removeFirst().run()
            }
        }
    }

    fun isInitialized(): Boolean {
        return isInitialized
    }

    fun getOutputWidth(): Int {
        return outputWidth
    }

    fun getOutputHeight(): Int {
        return outputHeight
    }

    fun getProgram(): Int {
        return glProgId
    }

    fun getAttribPosition(): Int {
        return glAttribPosition
    }

    fun getAttribTextureCoordinate(): Int {
        return glAttribTextureCoordinate
    }

    fun getUniformTexture(): Int {
        return glUniformTexture
    }

    protected fun setInteger(location: Int, intValue: Int) {
        runOnDraw {
            ifNeedInit()
            GLES20.glUniform1i(location, intValue)
        }
    }

    fun setFloat(location: Int, floatValue: Float) {
        runOnDraw {
            ifNeedInit()
            GLES20.glUniform1f(location, floatValue)
        }
    }

    protected fun setFloatVec2(location: Int, arrayValue: FloatArray) {
        runOnDraw {
            ifNeedInit()
            GLES20.glUniform2fv(location, 1, FloatBuffer.wrap(arrayValue))
        }
    }

    protected fun setFloatVec3(location: Int, arrayValue: FloatArray) {
        runOnDraw {
            ifNeedInit()
            GLES20.glUniform3fv(location, 1, FloatBuffer.wrap(arrayValue))
        }
    }

    protected fun setFloatVec4(location: Int, arrayValue: FloatArray) {
        runOnDraw {
            ifNeedInit()
            GLES20.glUniform4fv(location, 1, FloatBuffer.wrap(arrayValue))
        }
    }

    protected fun setFloatArray(location: Int, arrayValue: FloatArray) {
        runOnDraw {
            ifNeedInit()
            GLES20.glUniform1fv(location, arrayValue.size, FloatBuffer.wrap(arrayValue))
        }
    }

    protected fun setPoint(location: Int, point: PointF) {
        runOnDraw {
            ifNeedInit()
            val vec2 = FloatArray(2)
            vec2[0] = point.x
            vec2[1] = point.y
            GLES20.glUniform2fv(location, 1, vec2, 0)
        }
    }

    protected fun setUniformMatrix3f(location: Int, matrix: FloatArray) {
        runOnDraw {
            ifNeedInit()
            GLES20.glUniformMatrix3fv(location, 1, false, matrix, 0)
        }
    }

    protected fun setUniformMatrix4f(location: Int, matrix: FloatArray) {
        runOnDraw {
            ifNeedInit()
            GLES20.glUniformMatrix4fv(location, 1, false, matrix, 0)
        }
    }

    fun runOnDraw(runnable: Runnable) {
        synchronized(runOnDraw) { runOnDraw.addLast(runnable) }
    }

    fun loadShader(file: String, context: Context): String {
        try {
            val assetManager = context.assets
            val ims = assetManager.open(file)
            val re = convertStreamToString(ims)
            ims.close()
            return re
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    fun convertStreamToString(stream: InputStream): String {
        val s = Scanner(stream).useDelimiter("\\A")
        return if (s.hasNext()) s.next() else ""
    }
}