package me.devsaki.hentoid.customssiv

import android.graphics.PointF
import me.devsaki.hentoid.customssiv.CustomSubsamplingScaleImageView.Orientation
import java.io.Serializable

/**
 * Wraps the scale, center and orientation of a displayed image for easy restoration on screen rotate.
 */
class ImageViewState(
    private var scale: Float,
    private var virtualScale: Float,
    center: PointF,
    private var orientation: Orientation
) : Serializable {
    private var centerX = 0f
    private var centerY = 0f

    init {
        this.centerX = center.x
        this.centerY = center.y
    }

    fun getScale(): Float {
        return scale
    }

    fun getVirtualScale(): Float {
        return virtualScale
    }

    fun getCenter(): PointF {
        return PointF(centerX, centerY)
    }

    fun getOrientation(): CustomSubsamplingScaleImageView.Orientation {
        return orientation
    }
}