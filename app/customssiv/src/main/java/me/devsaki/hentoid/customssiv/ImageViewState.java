package me.devsaki.hentoid.customssiv;

import android.graphics.PointF;

import androidx.annotation.NonNull;

import java.io.Serializable;

/**
 * Wraps the scale, center and orientation of a displayed image for easy restoration on screen rotate.
 */
@SuppressWarnings("WeakerAccess")
public class ImageViewState implements Serializable {

    private final float scale;
    private final float virtualScale;
    private final float centerX;
    private final float centerY;
    private final int orientation;

    public ImageViewState(float scale, float virtualScale, @NonNull PointF center, int orientation) {
        this.scale = scale;
        this.virtualScale = virtualScale;
        this.centerX = center.x;
        this.centerY = center.y;
        this.orientation = orientation;
    }

    public float getScale() {
        return scale;
    }

    public float getVirtualScale() {
        return virtualScale;
    }

    @NonNull
    public PointF getCenter() {
        return new PointF(centerX, centerY);
    }

    public int getOrientation() {
        return orientation;
    }

}