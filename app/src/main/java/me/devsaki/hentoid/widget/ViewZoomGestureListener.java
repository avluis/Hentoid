package me.devsaki.hentoid.widget;

import android.content.Context;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/**
 * Credits go to the Tachiyomi team
 */
public class ViewZoomGestureListener extends GestureDetector {

    public ViewZoomGestureListener(Context context, Listener listener) {
        super(context, listener);
        this.listener = listener;
        scaledTouchSlopslop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    private Handler handler = new Handler();
    private final int scaledTouchSlopslop;
    private final int longTapTime = ViewConfiguration.getLongPressTimeout();
    private final int doubleTapTime = ViewConfiguration.getDoubleTapTimeout();

    private float downX = 0f;
    private float downY = 0f;
    private long lastUp = 0L;
    private MotionEvent lastDownEvent;
    protected Listener listener;

    /**
     * Runnable to execute when a long tap is confirmed.
     */
//    private Runnable longTapFn = new Runnable( () -> listener.onLongTapConfirmed(lastDownEvent!!) );
    private Runnable longTapFn = new Runnable() {
        @Override
        public void run() {
            listener.onLongTapConfirmed(lastDownEvent);
        }
    };


    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (lastDownEvent != null) lastDownEvent.recycle();
                lastDownEvent = MotionEvent.obtain(ev);

                // This is the key difference with the built-in detector. We have to ignore the
                // event if the last up and current down are too close in time (double tap).
                if (ev.getDownTime() - lastUp > doubleTapTime) {
                    downX = ev.getRawX();
                    downY = ev.getRawY();
                    handler.postDelayed(longTapFn, longTapTime);
                }
            }
            break;
            case MotionEvent.ACTION_MOVE: {
                if (Math.abs(ev.getRawX() - downX) > scaledTouchSlopslop || Math.abs(ev.getRawY() - downY) > scaledTouchSlopslop) {
                    handler.removeCallbacks(longTapFn);
                }
            }
            break;
            case MotionEvent.ACTION_UP: {
                lastUp = ev.getEventTime();
                handler.removeCallbacks(longTapFn);
            }
            break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_DOWN: {
                handler.removeCallbacks(longTapFn);
            }
            break;
            default:
                // Nothing specific to do for other events
        }

        return super.onTouchEvent(ev);
    }

    /**
     * Custom listener to also include a long tap confirmed
     */
    public static class Listener extends SimpleOnGestureListener {
        /**
         * Notified when a long tap occurs with the initial on down [ev] that triggered it.
         */
        public void onLongTapConfirmed(MotionEvent ev) {
            // Nothing to see here
        }

        public void onDoubleTapConfirmed(MotionEvent ev) {
            // Nothing to see heer
        }
    }
}
