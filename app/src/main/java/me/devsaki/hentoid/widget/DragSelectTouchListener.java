package me.devsaki.hentoid.widget;

import android.content.Context;
import android.content.res.Resources;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import timber.log.Timber;

/**
 * Credits go to https://github.com/MFlisar/DragSelectRecyclerView for the pre-Jetpack version
 */
@SuppressWarnings("unused")
public class DragSelectTouchListener implements RecyclerView.OnItemTouchListener {
    private static final String TAG = "DSTL";

    private boolean mIsActive;
    private int mStart, mEnd;
    private boolean mInTopSpot, mInBottomSpot;
    private int mScrollDistance;
    private float mLastX, mLastY;
    private int mLastStart, mLastEnd;

    private OnDragSelectListener mSelectListener;
    private RecyclerView mRecyclerView;
    private OverScroller mScroller;
    private final Runnable mScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (mScroller != null && mScroller.computeScrollOffset()) {
                scrollBy(mScrollDistance);
                ViewCompat.postOnAnimation(mRecyclerView, mScrollRunnable);
            }
        }
    };

    // Definitions for touch auto scroll regions
    private int mTopBoundFrom, mTopBoundTo, mBottomBoundFrom, mBottomBoundTo;

    // User settings - default values
    private int mMaxScrollDistance = 16;
    private int mAutoScrollDistance = (int) (Resources.getSystem().getDisplayMetrics().density * 56);
    private int mTouchRegionTopOffset = 0;
    private int mTouchRegionBottomOffset = 0;
    private boolean mScrollAboveTopRegion = true;
    private boolean mScrollBelowTopRegion = true;
    private boolean mDebug = false;

    // -----------------------
    // Konstructur and Builder functions
    // -----------------------

    public DragSelectTouchListener() {
        reset();
    }

    /**
     * sets the listener
     * <p>
     *
     * @param selectListener the listener that will be notified when items are (un)selected
     */
    public DragSelectTouchListener withSelectListener(OnDragSelectListener selectListener) {
        this.mSelectListener = selectListener;
        return this;
    }

    /**
     * sets the distance that the RecyclerView is maximally scrolled (per scroll event)
     * higher values result in higher scrolling speed
     * <p>
     *
     * @param distance the distance in pixels
     */
    public DragSelectTouchListener withMaxScrollDistance(int distance) {
        mMaxScrollDistance = distance;
        return this;
    }

    /**
     * defines the height of the region at the top/bottom of the RecyclerView
     * which will make the RecyclerView scroll
     * <p>
     *
     * @param size height of region
     */
    public DragSelectTouchListener withTouchRegion(int size) {
        mAutoScrollDistance = size;
        return this;
    }

    /**
     * defines an offset for the TouchRegion from the top
     * useful, if RecyclerView is displayed underneath a semi transparent Toolbar at top or similar
     * <p>
     *
     * @param distance offset
     */
    public DragSelectTouchListener withTopOffset(int distance) {
        mTouchRegionTopOffset = distance;
        return this;
    }

    /**
     * defines an offset for the TouchRegion from the bottom
     * useful, if RecyclerView is displayed underneath a semi transparent navigation view at the bottom or similar
     * ATTENTION: to move the region upwards, set a negative value!
     * <p>
     *
     * @param distance offset
     */
    public DragSelectTouchListener withBottomOffset(int distance) {
        mTouchRegionBottomOffset = distance;
        return this;
    }

    /**
     * enables scrolling, if the user touches the region above the RecyclerView
     * respectively above the TouchRegion at the top
     * <p>
     *
     * @param enabled if true, scrolling will continue even if the touch moves above the top touch region
     */
    public DragSelectTouchListener withScrollAboveTopRegion(boolean enabled) {
        mScrollAboveTopRegion = enabled;
        return this;
    }

    /**
     * enables scrolling, if the user touches the region below the RecyclerView
     * respectively below the TouchRegion at the bottom
     * <p>
     *
     * @param enabled if true, scrolling will continue even if the touch moves below the bottom touch region
     */
    public DragSelectTouchListener withScrollBelowTopRegion(boolean enabled) {
        mScrollBelowTopRegion = enabled;
        return this;
    }

    public DragSelectTouchListener withDebug(boolean enabled) {
        mDebug = enabled;
        return this;
    }

    // -----------------------
    // Main functions
    // -----------------------

    /**
     * start the drag selection
     * <p>
     *
     * @param position the index of the first selected item
     */
    public void startDragSelection(int position) {
        setIsActive(true);
        mStart = position;
        mEnd = position;
        mLastStart = position;
        mLastEnd = position;
        if (mSelectListener != null && mSelectListener instanceof OnAdvancedDragSelectListener)
            ((OnAdvancedDragSelectListener) mSelectListener).onSelectionStarted(position);
    }

    // -----------------------
    // Functions
    // -----------------------

    @Override
    public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
        if (!mIsActive || rv.getAdapter().getItemCount() == 0)
            return false;

        int action = e.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_DOWN:
                reset();
                break;
        }

        mRecyclerView = rv;
        int height = rv.getHeight();
        mTopBoundFrom = mTouchRegionTopOffset;
        mTopBoundTo = mTouchRegionTopOffset + mAutoScrollDistance;
        mBottomBoundFrom = height + mTouchRegionBottomOffset - mAutoScrollDistance;
        mBottomBoundTo = height + mTouchRegionBottomOffset;
        return true;
    }

    public void startAutoScroll() {
        if (mRecyclerView == null)
            return;

        initScroller(mRecyclerView.getContext());
        if (mScroller.isFinished()) {
            mRecyclerView.removeCallbacks(mScrollRunnable);
            mScroller.startScroll(0, mScroller.getCurrY(), 0, 5000, 100000);
            ViewCompat.postOnAnimation(mRecyclerView, mScrollRunnable);
        }
    }

    private void initScroller(Context context) {
        if (mScroller == null)
            mScroller = new OverScroller(context, new LinearInterpolator());
    }

    public void stopAutoScroll() {
        if (mScroller != null && !mScroller.isFinished()) {
            mRecyclerView.removeCallbacks(mScrollRunnable);
            mScroller.abortAnimation();
        }
    }

    @Override
    public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
        if (!mIsActive)
            return;

        int action = e.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_MOVE:
                if (!mInTopSpot && !mInBottomSpot)
                    updateSelectedRange(rv, e);
                processAutoScroll(e);
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                reset();
                break;
        }
    }

    private void updateSelectedRange(RecyclerView rv, MotionEvent e) {
        updateSelectedRange(rv, e.getX(), e.getY());
    }

    private void updateSelectedRange(RecyclerView rv, float x, float y) {
        View child = rv.findChildViewUnder(x, y);
        if (child != null) {
            int position = rv.getChildAdapterPosition(child);
            if (position != RecyclerView.NO_POSITION && mEnd != position) {
                mEnd = position;
                notifySelectRangeChange();
            }
        }
    }


    private void processAutoScroll(MotionEvent event) {
        int y = (int) event.getY();

        if (mDebug)
            Timber.d("y = " + y +
                    " | rv.height = " + mRecyclerView.getHeight() +
                    " | mTopBoundFrom => mTopBoundTo = " + mTopBoundFrom + " => " + mTopBoundTo +
                    " | mBottomBoundFrom => mBottomBoundTo = " + mBottomBoundFrom + " => " + mBottomBoundTo +
                    " | mTouchRegionTopOffset = " + mTouchRegionTopOffset +
                    " | mTouchRegionBottomOffset = " + mTouchRegionBottomOffset);

        float mScrollSpeedFactor;
        if (y >= mTopBoundFrom && y <= mTopBoundTo) {
            mLastX = event.getX();
            mLastY = event.getY();
            mScrollSpeedFactor = (((float) mTopBoundTo - (float) mTopBoundFrom) - ((float) y - (float) mTopBoundFrom)) / ((float) mTopBoundTo - (float) mTopBoundFrom);
            mScrollDistance = (int) ((float) mMaxScrollDistance * mScrollSpeedFactor * -1f);
            if (mDebug)
                Timber.d("SCROLL - mScrollSpeedFactor=" + mScrollSpeedFactor + " | mScrollDistance=" + mScrollDistance);
            if (!mInTopSpot) {
                mInTopSpot = true;
                startAutoScroll();
            }
        } else if (mScrollAboveTopRegion && y < mTopBoundFrom) {
            mLastX = event.getX();
            mLastY = event.getY();
            mScrollDistance = mMaxScrollDistance * -1;
            if (!mInTopSpot) {
                mInTopSpot = true;
                startAutoScroll();
            }
        } else if (y >= mBottomBoundFrom && y <= mBottomBoundTo) {
            mLastX = event.getX();
            mLastY = event.getY();
            mScrollSpeedFactor = (((float) y - (float) mBottomBoundFrom)) / ((float) mBottomBoundTo - (float) mBottomBoundFrom);
            mScrollDistance = (int) ((float) mMaxScrollDistance * mScrollSpeedFactor);
            if (mDebug)
                Timber.d("SCROLL - mScrollSpeedFactor=" + mScrollSpeedFactor + " | mScrollDistance=" + mScrollDistance);
            if (!mInBottomSpot) {
                mInBottomSpot = true;
                startAutoScroll();
            }
        } else if (mScrollBelowTopRegion && y > mBottomBoundTo) {
            mLastX = event.getX();
            mLastY = event.getY();
            mScrollDistance = mMaxScrollDistance;
            if (!mInTopSpot) {
                mInTopSpot = true;
                startAutoScroll();
            }
        } else {
            mInBottomSpot = false;
            mInTopSpot = false;
            mLastX = Float.MIN_VALUE;
            mLastY = Float.MIN_VALUE;
            stopAutoScroll();
        }
    }

    private void notifySelectRangeChange() {
        if (mSelectListener == null)
            return;
        if (mStart == RecyclerView.NO_POSITION || mEnd == RecyclerView.NO_POSITION)
            return;

        int newStart, newEnd;
        newStart = Math.min(mStart, mEnd);
        newEnd = Math.max(mStart, mEnd);
        if (mLastStart == RecyclerView.NO_POSITION || mLastEnd == RecyclerView.NO_POSITION) {
            if (newEnd - newStart == 1)
                mSelectListener.onSelectChange(newStart, newStart, true);
            else
                mSelectListener.onSelectChange(newStart, newEnd, true);
        } else {
            if (newStart > mLastStart)
                mSelectListener.onSelectChange(mLastStart, newStart - 1, false);
            else if (newStart < mLastStart)
                mSelectListener.onSelectChange(newStart, mLastStart - 1, true);

            if (newEnd > mLastEnd)
                mSelectListener.onSelectChange(mLastEnd + 1, newEnd, true);
            else if (newEnd < mLastEnd)
                mSelectListener.onSelectChange(newEnd + 1, mLastEnd, false);
        }

        mLastStart = newStart;
        mLastEnd = newEnd;
    }

    private void reset() {
        setIsActive(false);
        if (mSelectListener != null && mSelectListener instanceof OnAdvancedDragSelectListener)
            ((OnAdvancedDragSelectListener) mSelectListener).onSelectionFinished(mEnd);
        mStart = RecyclerView.NO_POSITION;
        mEnd = RecyclerView.NO_POSITION;
        mLastStart = RecyclerView.NO_POSITION;
        mLastEnd = RecyclerView.NO_POSITION;
        mInTopSpot = false;
        mInBottomSpot = false;
        mLastX = Float.MIN_VALUE;
        mLastY = Float.MIN_VALUE;
        stopAutoScroll();
    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // ignore
    }

    private void scrollBy(int distance) {
        int scrollDistance;
        if (distance > 0)
            scrollDistance = Math.min(distance, mMaxScrollDistance);
        else
            scrollDistance = Math.max(distance, -mMaxScrollDistance);
        mRecyclerView.scrollBy(0, scrollDistance);
        if (mLastX != Float.MIN_VALUE && mLastY != Float.MIN_VALUE)
            updateSelectedRange(mRecyclerView, mLastX, mLastY);
    }

    public void setIsActive(boolean isActive) {
        this.mIsActive = isActive;
    }

    // -----------------------
    // Interfaces and simple default implementations
    // -----------------------

    public interface OnAdvancedDragSelectListener extends OnDragSelectListener {
        /**
         * @param start the item on which the drag selection was started at
         */
        void onSelectionStarted(int start);

        /**
         * @param end the item on which the drag selection was finished at
         */
        void onSelectionFinished(int end);
    }

    public interface OnDragSelectListener {
        /**
         * @param start      the newly (un)selected range start
         * @param end        the newly (un)selected range end
         * @param isSelected true, it range got selected, false if not
         */
        void onSelectChange(int start, int end, boolean isSelected);
    }
}
