package me.devsaki.hentoid.ai_upscale

import android.content.res.AssetManager
import android.graphics.Bitmap

class NativeLib {

    // TODO initialize and free the upscale engine from here to avoid init overhead during each call
    // NB : AssetManager must be kept alive to avoid it being garbage-collected
    external fun upscale(
        assetMgr: AssetManager,
        param : String,
        model: String,
        bitmap: Bitmap,
        outPath: String): Int

    companion object {
        // Used to load the 'ai_upscale' library on application startup.
        init {
            System.loadLibrary("ai_upscale")
        }
    }
}