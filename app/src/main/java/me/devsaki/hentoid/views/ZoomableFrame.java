package me.devsaki.hentoid.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Frame layout which contains a [ZoomableRecyclerView]. It's needed to handle touch events,
 * because the recyclerview is scaled and its touch events are translated, which breaks the
 * detectors.
 * <p>
 * Credits for this go to the Tachiyomi team
 */
public class ZoomableFrame extends FrameLayout {

    private boolean enabled = true;


    public ZoomableFrame(@NonNull Context context) {
        super(context);
    }

    public ZoomableFrame(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ZoomableFrame(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    /**
     * Scale detector, either with pinch or quick scale.
     */
    private final ScaleGestureDetector scaleDetector = new ScaleGestureDetector(getContext(), new ScaleListener());

    /**
     * Fling detector.
     */
    private final GestureDetector flingDetector = new GestureDetector(getContext(), new FlingListener());

    /**
     * Recycler view added in this frame.
     */
    private ZoomableRecyclerView recycler;

    private ZoomableRecyclerView getRecycler() {
        if (null == recycler && getChildCount() > 0)
            recycler = (ZoomableRecyclerView) getChildAt(0);
        return recycler;
    }


    /**
     * Dispatches a touch event to the detectors.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (enabled) {
            scaleDetector.onTouchEvent(ev);
            flingDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * Scale listener used to delegate events to the recycler view.
     */
    class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            if (enabled && null != getRecycler()) getRecycler().onScaleBegin();
            return enabled;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (enabled && null != getRecycler()) getRecycler().onScale(detector.getScaleFactor());
            return enabled;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            if (enabled && null != getRecycler()) getRecycler().onScaleEnd();
        }
    }

    /**
     * Fling listener used to delegate events to the recycler view.
     */
    class FlingListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (enabled && null != getRecycler())
                return getRecycler().zoomFling(Math.round(velocityX), Math.round(velocityY));
            else return false;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return enabled;
        }
    }

    public void enable() {
        enabled = true;
    }

    public void disable() {
        enabled = false;
    }
}
