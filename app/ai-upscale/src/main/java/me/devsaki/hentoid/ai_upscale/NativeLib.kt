package me.devsaki.hentoid.ai_upscale

class NativeLib {

    /**
     * A native method that is implemented by the 'ai_upscale' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'ai_upscale' library on application startup.
        init {
            System.loadLibrary("ai_upscale")
        }
    }
}