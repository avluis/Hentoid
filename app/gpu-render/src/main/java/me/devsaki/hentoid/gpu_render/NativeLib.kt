package me.devsaki.hentoid.gpu_render

import android.graphics.Bitmap

internal class NativeLib {

    companion object {
        // Used to load the 'gpu_render' library on application startup.
        init {
            System.loadLibrary("gpu_render")
        }

        external fun YUVtoRBGA(yuv: ByteArray, width: Int, height: Int, out: IntArray)

        external fun YUVtoARBG(yuv: ByteArray, width: Int, height: Int, out: IntArray)

        external fun adjustBitmap(srcBitmap: Bitmap)
    }
}