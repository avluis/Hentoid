package me.devsaki.hentoid.gles_renderer.util

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import timber.log.Timber
import java.nio.IntBuffer

class OpenGlUtils {
    companion object {
        const val NO_TEXTURE = -1

        fun loadTexture(img: Bitmap, usedTexId: Int, recycle: Boolean): Int {
            val textures = IntArray(1)
            if (usedTexId == NO_TEXTURE) {
                GLES20.glGenTextures(1, textures, 0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
                GLES20.glTexParameterf(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat()
                )
                GLES20.glTexParameterf(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat()
                )
                GLES20.glTexParameterf(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE.toFloat()
                )
                GLES20.glTexParameterf(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE.toFloat()
                )
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, img, 0)
            } else {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, usedTexId)
                GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, img)
                textures[0] = usedTexId
            }
            if (recycle) {
                img.recycle()
            }
            return textures[0]
        }

        fun loadTexture(data: IntBuffer?, width: Int, height: Int, usedTexId: Int): Int {
            val textures = IntArray(1)
            if (usedTexId == NO_TEXTURE) {
                GLES20.glGenTextures(1, textures, 0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
                GLES20.glTexParameterf(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat()
                )
                GLES20.glTexParameterf(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat()
                )
                GLES20.glTexParameterf(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE.toFloat()
                )
                GLES20.glTexParameterf(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE.toFloat()
                )
                GLES20.glTexImage2D(
                    GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height,
                    0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, data
                )
            } else {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, usedTexId)
                GLES20.glTexSubImage2D(
                    GLES20.GL_TEXTURE_2D, 0, 0, 0, width,
                    height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, data
                )
                textures[0] = usedTexId
            }
            return textures[0]
        }

        fun loadTextureAsBitmap(data: IntBuffer, width: Int, height: Int, usedTexId: Int): Int {
            val bitmap = Bitmap.createBitmap(data.array(), width, height, Bitmap.Config.ARGB_8888)
            return loadTexture(bitmap, usedTexId, true)
        }

        fun loadShader(strSource: String?, iType: Int): Int {
            val compiled = IntArray(1)
            val iShader = GLES20.glCreateShader(iType)
            GLES20.glShaderSource(iShader, strSource)
            GLES20.glCompileShader(iShader)
            GLES20.glGetShaderiv(iShader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                Timber.tag("Load Shader Failed")
                    .d("\n     Compilation\n     %s\n", GLES20.glGetShaderInfoLog(iShader))
                return 0
            }
            return iShader
        }

        fun loadProgram(strVSource: String?, strFSource: String?): Int {
            val link = IntArray(1)
            val iVShader: Int = loadShader(strVSource, GLES20.GL_VERTEX_SHADER)
            if (iVShader == 0) {
                Timber.tag("Load Program").d("Vertex Shader Failed")
                return 0
            }
            val iFShader: Int = loadShader(strFSource, GLES20.GL_FRAGMENT_SHADER)
            if (iFShader == 0) {
                Timber.tag("Load Program").d("Fragment Shader Failed")
                return 0
            }
            val iProgId: Int = GLES20.glCreateProgram()
            GLES20.glAttachShader(iProgId, iVShader)
            GLES20.glAttachShader(iProgId, iFShader)
            GLES20.glLinkProgram(iProgId)
            GLES20.glGetProgramiv(iProgId, GLES20.GL_LINK_STATUS, link, 0)
            if (link[0] <= 0) {
                Timber.tag("Load Program").d("Linking Failed")
                return 0
            }
            GLES20.glDeleteShader(iVShader)
            GLES20.glDeleteShader(iFShader)
            return iProgId
        }

        fun rnd(min: Float, max: Float): Float {
            val fRandNum = Math.random().toFloat()
            return min + (max - min) * fRandNum
        }
    }
}