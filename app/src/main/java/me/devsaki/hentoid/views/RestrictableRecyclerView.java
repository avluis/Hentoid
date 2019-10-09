package me.devsaki.hentoid.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import timber.log.Timber;

public class RestrictableRecyclerView extends RecyclerView {

    private int minIndexBound = 0;
    private int maxIndexBound = Integer.MAX_VALUE;
    private boolean bounded = false;

    private final Detector gestureDetector = new Detector(getContext(), new GestureListener());
    private LinearLayoutManager llm = null;


    public RestrictableRecyclerView(@NonNull Context context) {
        super(context);
        addOnScrollListener(new ScrollPositionListener());
    }

    public RestrictableRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        addOnScrollListener(new ScrollPositionListener());
    }

    public RestrictableRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        addOnScrollListener(new ScrollPositionListener());
    }

    public void setBounds(int min, int max) {
        minIndexBound = min;
        maxIndexBound = max;
        bounded = true;
    }

    public void resetBounds() {
        minIndexBound = 0;
        maxIndexBound = Integer.MAX_VALUE;
        bounded = false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (null == llm) llm = (LinearLayoutManager) getLayoutManager();

        if (!bounded || !gestureDetector.onTouchEvent(ev))
            return super.dispatchTouchEvent(ev);
        else return true;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (getScrollState() != SCROLL_STATE_IDLE)
            return false;
        return super.onInterceptTouchEvent(e);
    }

    /*
    class GestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            return handleScroll(distanceY);
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            return handleScroll(velocityY);
        }

        private boolean handleScroll(float movementY) {
            if (movementY > 0) { // scroll down
                if (llm.findLastCompletelyVisibleItemPosition() >= maxIndexBound - 1) {
                    Timber.i(">>STOP GESTURE DOWN %s", maxIndexBound);
                    stopScroll();
                    return true;
                }
            } else { // Scroll up
                if (llm.findFirstCompletelyVisibleItemPosition() <= minIndexBound + 1) {
                    Timber.i(">>STOP GESTURE UP %s", minIndexBound);
                    stopScroll();
                    return true;
                }
            }
            return false;
        }
    }
     */


    class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            gestureDetector.isDoubleTapping = true;
            return false;
        }
    }

    class Detector extends GestureDetector {

        private int scrollPointerId = 0;
        private int downX = 0;
        private int downY = 0;
        boolean isDoubleTapping = false;

        Detector(Context context, OnGestureListener listener) {
            super(context, listener);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            int action = ev.getActionMasked();
            int actionIndex = ev.getActionIndex();

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    motionActionDownLocal(ev);
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    motionActionPointerDown(ev, actionIndex);
                    break;
                case MotionEvent.ACTION_MOVE:
                    return motionActionMoveLocal(ev);
                case MotionEvent.ACTION_UP:
                    motionActionUpLocal(ev);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    motionActionCancel();
                    break;
                default:
                    // Nothing to process as default
            }
            return super.onTouchEvent(ev);
        }

        private void motionActionDownLocal(MotionEvent ev) {
            scrollPointerId = ev.getPointerId(0);
            downX = Math.round(ev.getX() + 0.5f);
            downY = Math.round(ev.getY() + 0.5f);
        }

        private void motionActionPointerDown(MotionEvent ev, int actionIndex) {
            scrollPointerId = ev.getPointerId(actionIndex);
            downX = Math.round(ev.getX(actionIndex) + 0.5f);
            downY = Math.round(ev.getY(actionIndex) + 0.5f);
        }

        private boolean motionActionMoveLocal(MotionEvent ev) {
            if (isDoubleTapping) {
                return true;
            }

            int index = ev.findPointerIndex(scrollPointerId);
            if (index < 0) {
                return false;
            }

  //          int x = Math.round(ev.getX(index) + 0.5f);
            int y = Math.round(ev.getY(index) + 0.5f);
//            int dx = x - downX;
            int dy = y - downY;

            if (dy < 0) { // scroll down
                if (llm.findLastCompletelyVisibleItemPosition() >= maxIndexBound - 1) {
                    Timber.i(">>STOP GESTURE DOWN %s", maxIndexBound);
                    stopScroll();
                    //llm.scrollToPosition(maxIndexBound - 1);
                    llm.scrollToPositionWithOffset(maxIndexBound, getHeight()+1);
                    return true;
                }
            } else { // Scroll up
                if (llm.findFirstCompletelyVisibleItemPosition() <= minIndexBound) {
                    Timber.i(">>STOP GESTURE UP %s", minIndexBound);
                    stopScroll();
                    llm.scrollToPositionWithOffset(minIndexBound, 0);
                    return true;
                }
            }

            return super.onTouchEvent(ev);
        }

        private void motionActionUpLocal(MotionEvent ev) {
            isDoubleTapping = false;
        }

        private void motionActionCancel() {
            isDoubleTapping = false;
        }
    }

    class ScrollPositionListener extends RecyclerView.OnScrollListener {

        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);

            if (!bounded || null == llm) return;

            if (dy > 0) { // scroll down
                if (llm.findLastCompletelyVisibleItemPosition() >= maxIndexBound - 1) {
                    Timber.i(">>STOP SCROLL DOWN %s", maxIndexBound);
                    stopScroll();
                    //llm.scrollToPosition(maxIndexBound - 1);
                    llm.scrollToPositionWithOffset(maxIndexBound, getHeight()+1);
                }
            } else { // Scroll up
                if (llm.findFirstCompletelyVisibleItemPosition() <= minIndexBound) {
                    Timber.i(">>STOP SCROLL UP %s", minIndexBound);
                    stopScroll();
                    llm.scrollToPositionWithOffset(minIndexBound, 0);
                }
            }
        }
    }

}
