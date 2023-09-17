package me.devsaki.hentoid.widget

import android.content.Context
import android.content.res.Resources
import android.view.MotionEvent
import android.view.animation.LinearInterpolator
import android.widget.OverScroller
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min

/**
 * Credits go to https://github.com/MFlisar/DragSelectRecyclerView for the pre-Jetpack version
 */
class DragSelectTouchListener// -----------------------
// Konstructur and Builder functions
// -----------------------
    () : RecyclerView.OnItemTouchListener {

    companion object {
        private const val TAG = "DSTL"
    }

    private var mIsActive = false
    private var mStart = 0
    private var mEnd = 0
    private var mInTopSpot = false
    private var mInBottomSpot = false
    private var mScrollDistance = 0
    private var mLastX = 0f
    private var mLastY = 0f
    private var mLastStart = 0
    private var mLastEnd = 0

    private var mSelectListener: OnDragSelectListener? = null
    private var mRecyclerView: RecyclerView? = null
    private var mScroller: OverScroller? = null
    private val mScrollRunnable: Runnable = object : Runnable {
        override fun run() {
            if (mScroller != null && mScroller!!.computeScrollOffset()) {
                scrollBy(mScrollDistance)
                ViewCompat.postOnAnimation(mRecyclerView!!, this)
            }
        }
    }

    // Definitions for touch auto scroll regions
    private var mTopBoundFrom = 0  // Definitions for touch auto scroll regions
    private var mTopBoundTo = 0  // Definitions for touch auto scroll regions
    private var mBottomBoundFrom = 0  // Definitions for touch auto scroll regions
    private var mBottomBoundTo = 0

    // User settings - default values
    private var mMaxScrollDistance = 16
    private var mAutoScrollDistance = (Resources.getSystem().displayMetrics.density * 56).toInt()
    private var mTouchRegionTopOffset = 0
    private var mTouchRegionBottomOffset = 0
    private var mScrollAboveTopRegion = true
    private var mScrollBelowTopRegion = true
    private var mDebug = false

    init {
        reset()
    }

    /**
     * sets the listener
     *
     * @param selectListener the listener that will be notified when items are (un)selected
     */
    fun withSelectListener(selectListener: OnDragSelectListener?): DragSelectTouchListener {
        mSelectListener = selectListener
        return this
    }

    /**
     * sets the distance that the RecyclerView is maximally scrolled (per scroll event)
     * higher values result in higher scrolling speed
     *
     *
     *
     * @param distance the distance in pixels
     */
    fun withMaxScrollDistance(distance: Int): DragSelectTouchListener {
        mMaxScrollDistance = distance
        return this
    }

    /**
     * defines the height of the region at the top/bottom of the RecyclerView
     * which will make the RecyclerView scroll
     *
     *
     *
     * @param size height of region
     */
    fun withTouchRegion(size: Int): DragSelectTouchListener {
        mAutoScrollDistance = size
        return this
    }

    /**
     * defines an offset for the TouchRegion from the top
     * useful, if RecyclerView is displayed underneath a semi transparent Toolbar at top or similar
     *
     *
     *
     * @param distance offset
     */
    fun withTopOffset(distance: Int): DragSelectTouchListener {
        mTouchRegionTopOffset = distance
        return this
    }

    /**
     * defines an offset for the TouchRegion from the bottom
     * useful, if RecyclerView is displayed underneath a semi transparent navigation view at the bottom or similar
     * ATTENTION: to move the region upwards, set a negative value!
     *
     *
     *
     * @param distance offset
     */
    fun withBottomOffset(distance: Int): DragSelectTouchListener {
        mTouchRegionBottomOffset = distance
        return this
    }

    /**
     * enables scrolling, if the user touches the region above the RecyclerView
     * respectively above the TouchRegion at the top
     *
     *
     *
     * @param enabled if true, scrolling will continue even if the touch moves above the top touch region
     */
    fun withScrollAboveTopRegion(enabled: Boolean): DragSelectTouchListener {
        mScrollAboveTopRegion = enabled
        return this
    }

    /**
     * enables scrolling, if the user touches the region below the RecyclerView
     * respectively below the TouchRegion at the bottom
     *
     *
     *
     * @param enabled if true, scrolling will continue even if the touch moves below the bottom touch region
     */
    fun withScrollBelowTopRegion(enabled: Boolean): DragSelectTouchListener {
        mScrollBelowTopRegion = enabled
        return this
    }

    fun withDebug(enabled: Boolean): DragSelectTouchListener {
        mDebug = enabled
        return this
    }

    // -----------------------
    // Main functions
    // -----------------------

    // -----------------------
    // Main functions
    // -----------------------
    /**
     * start the drag selection
     *
     *
     *
     * @param position the index of the first selected item
     */
    fun startDragSelection(position: Int) {
        setIsActive(true)
        mStart = position
        mEnd = position
        mLastStart = position
        mLastEnd = position
        if (mSelectListener != null && mSelectListener is OnAdvancedDragSelectListener) (mSelectListener as OnAdvancedDragSelectListener).onSelectionStarted(
            position
        )
    }

    // -----------------------
    // Functions
    // -----------------------

    // -----------------------
    // Functions
    // -----------------------
    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        if (!mIsActive || rv.adapter!!.itemCount == 0) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> reset()
            else -> {}
        }
        mRecyclerView = rv
        val height = rv.height
        mTopBoundFrom = mTouchRegionTopOffset
        mTopBoundTo = mTouchRegionTopOffset + mAutoScrollDistance
        mBottomBoundFrom = height + mTouchRegionBottomOffset - mAutoScrollDistance
        mBottomBoundTo = height + mTouchRegionBottomOffset
        return true
    }

    fun startAutoScroll() {
        if (mRecyclerView == null) return
        initScroller(mRecyclerView!!.context)
        if (mScroller!!.isFinished) {
            mRecyclerView!!.removeCallbacks(mScrollRunnable)
            mScroller!!.startScroll(0, mScroller!!.currY, 0, 5000, 100000)
            ViewCompat.postOnAnimation(mRecyclerView!!, mScrollRunnable)
        }
    }

    private fun initScroller(context: Context) {
        if (mScroller == null) mScroller = OverScroller(context, LinearInterpolator())
    }

    fun stopAutoScroll() {
        if (mScroller != null && !mScroller!!.isFinished) {
            mRecyclerView!!.removeCallbacks(mScrollRunnable)
            mScroller!!.abortAnimation()
        }
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        if (!mIsActive) return
        val action = e.actionMasked
        when (action) {
            MotionEvent.ACTION_MOVE -> {
                if (!mInTopSpot && !mInBottomSpot) updateSelectedRange(rv, e)
                processAutoScroll(e)
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> reset()
            else -> {}
        }
    }

    private fun updateSelectedRange(rv: RecyclerView, e: MotionEvent) {
        updateSelectedRange(rv, e.x, e.y)
    }

    private fun updateSelectedRange(rv: RecyclerView?, x: Float, y: Float) {
        val child = rv!!.findChildViewUnder(x, y)
        if (child != null) {
            val position = rv.getChildAdapterPosition(child)
            if (position != RecyclerView.NO_POSITION && mEnd != position) {
                mEnd = position
                notifySelectRangeChange()
            }
        }
    }


    private fun processAutoScroll(event: MotionEvent) {
        val y = event.y.toInt()
        if (mDebug) Timber.d(
            "y = " + y +
                    " | rv.height = " + mRecyclerView!!.height +
                    " | mTopBoundFrom => mTopBoundTo = " + mTopBoundFrom + " => " + mTopBoundTo +
                    " | mBottomBoundFrom => mBottomBoundTo = " + mBottomBoundFrom + " => " + mBottomBoundTo +
                    " | mTouchRegionTopOffset = " + mTouchRegionTopOffset +
                    " | mTouchRegionBottomOffset = " + mTouchRegionBottomOffset
        )
        val mScrollSpeedFactor: Float
        if (y in mTopBoundFrom..mTopBoundTo) {
            mLastX = event.x
            mLastY = event.y
            mScrollSpeedFactor =
                ((mTopBoundTo.toFloat() - mTopBoundFrom.toFloat()) - (y.toFloat() - mTopBoundFrom.toFloat())) / (mTopBoundTo.toFloat() - mTopBoundFrom.toFloat())
            mScrollDistance = ((mMaxScrollDistance.toFloat()) * mScrollSpeedFactor * -1f).toInt()
            if (mDebug) Timber.d("SCROLL - mScrollSpeedFactor=$mScrollSpeedFactor | mScrollDistance=$mScrollDistance")
            if (!mInTopSpot) {
                mInTopSpot = true
                startAutoScroll()
            }
        } else if (mScrollAboveTopRegion && y < mTopBoundFrom) {
            mLastX = event.x
            mLastY = event.y
            mScrollDistance = mMaxScrollDistance * -1
            if (!mInTopSpot) {
                mInTopSpot = true
                startAutoScroll()
            }
        } else if (y in mBottomBoundFrom..mBottomBoundTo) {
            mLastX = event.x
            mLastY = event.y
            mScrollSpeedFactor =
                (y.toFloat() - mBottomBoundFrom.toFloat()) / (mBottomBoundTo.toFloat() - mBottomBoundFrom.toFloat())
            mScrollDistance = (mMaxScrollDistance.toFloat() * mScrollSpeedFactor).toInt()
            if (mDebug) Timber.d("SCROLL - mScrollSpeedFactor=$mScrollSpeedFactor | mScrollDistance=$mScrollDistance")
            if (!mInBottomSpot) {
                mInBottomSpot = true
                startAutoScroll()
            }
        } else if (mScrollBelowTopRegion && y > mBottomBoundTo) {
            mLastX = event.x
            mLastY = event.y
            mScrollDistance = mMaxScrollDistance
            if (!mInTopSpot) {
                mInTopSpot = true
                startAutoScroll()
            }
        } else {
            mInBottomSpot = false
            mInTopSpot = false
            mLastX = Float.MIN_VALUE
            mLastY = Float.MIN_VALUE
            stopAutoScroll()
        }
    }

    private fun notifySelectRangeChange() {
        if (mSelectListener == null) return
        if (mStart == RecyclerView.NO_POSITION || mEnd == RecyclerView.NO_POSITION) return
        val newStart: Int = min(mStart, mEnd)
        val newEnd: Int = max(mStart, mEnd)
        if (mLastStart == RecyclerView.NO_POSITION || mLastEnd == RecyclerView.NO_POSITION) {
            if (newEnd - newStart == 1) mSelectListener!!.onSelectChange(
                newStart,
                newStart,
                true
            ) else mSelectListener!!.onSelectChange(newStart, newEnd, true)
        } else {
            if (newStart > mLastStart) mSelectListener!!.onSelectChange(
                mLastStart,
                newStart - 1,
                false
            ) else if (newStart < mLastStart) mSelectListener!!.onSelectChange(
                newStart,
                mLastStart - 1,
                true
            )
            if (newEnd > mLastEnd) mSelectListener!!.onSelectChange(
                mLastEnd + 1,
                newEnd,
                true
            ) else if (newEnd < mLastEnd) mSelectListener!!.onSelectChange(
                newEnd + 1,
                mLastEnd,
                false
            )
        }
        mLastStart = newStart
        mLastEnd = newEnd
    }

    private fun reset() {
        setIsActive(false)
        if (mSelectListener != null && mSelectListener is OnAdvancedDragSelectListener) (mSelectListener as OnAdvancedDragSelectListener).onSelectionFinished(
            mEnd
        )
        mStart = RecyclerView.NO_POSITION
        mEnd = RecyclerView.NO_POSITION
        mLastStart = RecyclerView.NO_POSITION
        mLastEnd = RecyclerView.NO_POSITION
        mInTopSpot = false
        mInBottomSpot = false
        mLastX = Float.MIN_VALUE
        mLastY = Float.MIN_VALUE
        stopAutoScroll()
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // ignore
    }

    private fun scrollBy(distance: Int) {
        val scrollDistance: Int = if (distance > 0) min(distance, mMaxScrollDistance) else max(
            distance,
            -mMaxScrollDistance
        )
        mRecyclerView!!.scrollBy(0, scrollDistance)
        if (mLastX != Float.MIN_VALUE && mLastY != Float.MIN_VALUE) updateSelectedRange(
            mRecyclerView,
            mLastX,
            mLastY
        )
    }

    fun setIsActive(isActive: Boolean) {
        mIsActive = isActive
    }

    // -----------------------
    // Interfaces and simple default implementations
    // -----------------------

    // -----------------------
    // Interfaces and simple default implementations
    // -----------------------
    interface OnAdvancedDragSelectListener : OnDragSelectListener {
        /**
         * @param start the item on which the drag selection was started at
         */
        fun onSelectionStarted(start: Int)

        /**
         * @param end the item on which the drag selection was finished at
         */
        fun onSelectionFinished(end: Int)
    }

    interface OnDragSelectListener {
        /**
         * @param start      the newly (un)selected range start
         * @param end        the newly (un)selected range end
         * @param isSelected true, it range got selected, false if not
         */
        fun onSelectChange(start: Int, end: Int, isSelected: Boolean)
    }
}