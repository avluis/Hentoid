package me.devsaki.hentoid.ai_upscale

import android.content.res.AssetManager
import java.nio.ByteBuffer

class AiUpscaler {

    private var upscalerHandle = 0L

    // AssetManager must be kept alive to avoid it being garbage-collected
    private var assetManager: AssetManager? = null

    init {
        // Only called when actually used; multiple calls have no effect
        System.loadLibrary("ai_upscale")
    }

    private external fun initEngine(
        assetMgr: AssetManager,
        param: String,
        model: String
    ): Long

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
        assetManager = assetMgr
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
            assetManager = null
        }
    }
}