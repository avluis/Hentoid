package me.devsaki.hentoid.customssiv

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import java.io.File
import java.io.UnsupportedEncodingException
import java.net.URLDecoder

/**
 * Helper class used to set the source and additional attributes from a variety of sources. Supports
 * use of a bitmap, asset, external file or any other URI.
 * <p>
 * When you are using a preview image, you must set the dimensions of the full size image on the
 * ImageSource object for the full size image using the {@link #dimensions(int, int)} method.
 */
const val FILE_SCHEME = "file:///"
const val ASSET_SCHEME = "file:///android_asset/"


/**
 * Create an instance from an asset name.
 *
 * @param assetName asset name.
 * @return an [ImageSource] instance.
 */
fun asset(assetName: String): ImageSource {
    return uri(ASSET_SCHEME + assetName)
}

/**
 * Create an instance from a URI. If the URI does not start with a scheme, it's assumed to be the URI
 * of a file.
 *
 * @param uri image URI.
 * @return an [ImageSource] instance.
 */
fun uri(uri: String): ImageSource {
    var theUri = uri
    if (!theUri.contains("://")) {
        if (theUri.startsWith("/")) {
            theUri = theUri.substring(1)
        }
        theUri = FILE_SCHEME + theUri
    }
    return ImageSource(Uri.parse(theUri))
}

/**
 * Create an instance from a URI.
 *
 * @param uri image URI.
 * @return an [ImageSource] instance.
 */
fun uri(uri: Uri): ImageSource {
    return ImageSource(uri)
}

/**
 * Provide a loaded bitmap for display.
 *
 * @param bitmap bitmap to be displayed.
 * @return an [ImageSource] instance.
 */
fun bitmap(bitmap: Bitmap): ImageSource {
    return ImageSource(bitmap, false)
}

@SuppressWarnings("unused", "WeakerAccess")
class ImageSource {
    private var uri: Uri? = null
    private var bitmap: Bitmap? = null
    private var tile = false
    private var sWidth = 0
    private var sHeight = 0
    private var sRegion: Rect? = null
    private var cached = false

    internal constructor(bitmap: Bitmap, cached: Boolean) {
        this.bitmap = bitmap
        this.uri = null
        this.tile = false
        this.sWidth = bitmap.width
        this.sHeight = bitmap.height
        this.cached = cached
    }

    internal constructor(uri: Uri) {
        // #114 If file doesn't exist, attempt to url decode the URI and try again
        var theUri = uri
        val uriString = theUri.toString()
        if (uriString.startsWith(FILE_SCHEME)) {
            val uriFile = File(uriString.substring(FILE_SCHEME.length - 1))
            if (!uriFile.exists()) {
                try {
                    theUri = Uri.parse(URLDecoder.decode(uriString, "UTF-8"))
                } catch (_: UnsupportedEncodingException) {
                    // Fallback to encoded URI. This exception is not expected.
                }
            }
        }
        this.bitmap = null
        this.uri = theUri
        this.tile = true
    }

    /**
     * Enable tiling of the image. This does not apply to preview images which are always loaded as a single bitmap.,
     * and tiling cannot be disabled when displaying a region of the source image.
     *
     * @return this instance for chaining.
     */
    fun enableTiling(): ImageSource {
        return tiling(true)
    }

    /**
     * Disable tiling of the image. This does not apply to preview images which are always loaded as a single bitmap,
     * and tiling cannot be disabled when displaying a region of the source image.
     *
     * @return this instance for chaining.
     */
    fun disableTiling(): ImageSource {
        return tiling(false)
    }

    /**
     * Enable or disable tiling of the image. This does not apply to preview images which are always loaded as a single bitmap,
     * and tiling cannot be disabled when displaying a region of the source image.
     *
     * @param tile whether tiling should be enabled.
     * @return this instance for chaining.
     */
    private fun tiling(tile: Boolean): ImageSource {
        this.tile = tile
        return this
    }

    /**
     * Use a region of the source image. Region must be set independently for the full size image and the preview if
     * you are using one.
     *
     * @param sRegion the region of the source image to be displayed.
     * @return this instance for chaining.
     */
    fun region(sRegion: Rect?): ImageSource {
        this.sRegion = sRegion
        setInvariants()
        return this
    }

    /**
     * Declare the dimensions of the image. This is only required for a full size image, when you are specifying a URI
     * and also a preview image. When displaying a bitmap object, or not using a preview, you do not need to declare
     * the image dimensions. Note if the declared dimensions are found to be incorrect, the view will reset.
     *
     * @param sWidth  width of the source image.
     * @param sHeight height of the source image.
     * @return this instance for chaining.
     */
    fun dimensions(sWidth: Int, sHeight: Int): ImageSource {
        if (bitmap == null) {
            this.sWidth = sWidth
            this.sHeight = sHeight
        }
        setInvariants()
        return this
    }

    private fun setInvariants() {
        sRegion?.let {
            this.tile = true
            this.sWidth = it.width()
            this.sHeight = it.height()
        }
    }

    fun getUri(): Uri? {
        return uri
    }

    fun getBitmap(): Bitmap? {
        return bitmap
    }

    fun getTile(): Boolean {
        return tile
    }

    fun getSWidth(): Int {
        return sWidth
    }

    fun getSHeight(): Int {
        return sHeight
    }

    fun getSRegion(): Rect? {
        return sRegion
    }

    fun isCached(): Boolean {
        return cached
    }
}