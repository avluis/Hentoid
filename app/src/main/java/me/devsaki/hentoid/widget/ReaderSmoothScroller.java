package me.devsaki.hentoid.widget;

import android.content.Context;
import android.util.DisplayMetrics;

import androidx.recyclerview.widget.LinearSmoothScroller;

import timber.log.Timber;

public class ReaderSmoothScroller extends LinearSmoothScroller {
    private float speed = 25f;// LinearSmoothScroller.MILLISECONDS_PER_INCH

    public ReaderSmoothScroller(Context context) {
        super(context);
    }

    @Override
    protected int getVerticalSnapPreference() {
        return LinearSmoothScroller.SNAP_TO_START;
    }

    @Override
    protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
        return speed / displayMetrics.densityDpi;
    }

    public void setSpeed(float speed) {
        Timber.i("SPEED : %s", speed);
        this.speed = speed;
    }
}
