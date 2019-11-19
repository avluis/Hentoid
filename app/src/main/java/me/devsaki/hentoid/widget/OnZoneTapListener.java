package me.devsaki.hentoid.widget;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.view.GestureDetectorCompat;

import me.devsaki.hentoid.R;

public class OnZoneTapListener implements View.OnTouchListener {

    /**
     * This view's dimensions are used to determine which zone a tap belongs to
     */
    private final View view;

    private final GestureDetectorCompat gestureDetector;

    private final int pagerTapZoneWidth;

    private Runnable onLeftZoneTapListener;

    private Runnable onRightZoneTapListener;

    private Runnable onMiddleZoneTapListener;

    public OnZoneTapListener(View view) {
        this.view = view;
        Context context = view.getContext();
        gestureDetector = new GestureDetectorCompat(context, new OnGestureListener());
        pagerTapZoneWidth = context.getResources().getDimensionPixelSize(R.dimen.tap_zone_width);
    }

    public OnZoneTapListener setOnLeftZoneTapListener(Runnable onLeftZoneTapListener) {
        this.onLeftZoneTapListener = onLeftZoneTapListener;
        return this;
    }

    public OnZoneTapListener setOnRightZoneTapListener(Runnable onRightZoneTapListener) {
        this.onRightZoneTapListener = onRightZoneTapListener;
        return this;
    }

    public OnZoneTapListener setOnMiddleZoneTapListener(Runnable onMiddleZoneTapListener) {
        this.onMiddleZoneTapListener = onMiddleZoneTapListener;
        return this;
    }

    public boolean onSingleTapConfirmedAction(MotionEvent e) {
        if (e.getX() < pagerTapZoneWidth && onLeftZoneTapListener != null) {
            onLeftZoneTapListener.run();
        } else if (e.getX() > view.getWidth() - pagerTapZoneWidth && onRightZoneTapListener != null) {
            onRightZoneTapListener.run();
        } else {
            if (onMiddleZoneTapListener != null) onMiddleZoneTapListener.run();
        }
        return true;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    private final class OnGestureListener extends GestureDetector.SimpleOnGestureListener { // TODO remove if it proves useless
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            return onSingleTapConfirmedAction(e);
        }
    }
}
