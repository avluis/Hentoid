package me.devsaki.hentoid.ai_upscale

import android.content.res.AssetManager
import java.nio.ByteBuffer

class AiUpscaler {

    private var upscalerHandle = 0L

    init {
        System.loadLibrary("ai_upscale")
    }

    private external fun initEngine(
        assetMgr: AssetManager,
        param: String,
        model: String
    ): Long

    // TODO initialize and free the upscale engine from here to avoid init overhead during each call
    // NB : AssetManager must be kept alive to avoid it being garbage-collected
    private external fun upscale(
        engineHandle: Long,
        dataIn: ByteBuffer,
        outPath: String,
        progress: ByteBuffer
    ): Int

    private external fun clear(upscalerHandle: Long)

    fun init(
        assetMgr: AssetManager,
        param: String,
        model: String
    ) {
        upscalerHandle = initEngine(assetMgr, param, model)
    }

    fun upscale(
        dataIn: ByteBuffer,
        outPath: String,
        progress: ByteBuffer
    ): Int {
        return upscale(upscalerHandle, dataIn, outPath, progress)
    }

    fun cleanup() {
        if (upscalerHandle != 0L) {
            clear(upscalerHandle)
            upscalerHandle = 0L
        }
    }

    /*
    companion object {
        // Used to load the 'ai_upscale' library on application startup.
        init {
            System.loadLibrary("ai_upscale")
        }
    }
     */
}