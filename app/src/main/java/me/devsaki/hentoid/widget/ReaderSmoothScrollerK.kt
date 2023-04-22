package me.devsaki.hentoid.widget

import android.content.Context
import android.graphics.PointF
import android.util.DisplayMetrics
import androidx.recyclerview.widget.LinearSmoothScroller
import timber.log.Timber

// See https://stackoverflow.com/questions/32459696/get-scroll-y-of-recyclerview-or-webview
class ReaderSmoothScrollerK(context: Context) : LinearSmoothScroller(context) {
    private var speed = 25f // LinearSmoothScroller.MILLISECONDS_PER_INCH

    private var currentScrollY = 0
    private var itemHeight = 0


    override fun getVerticalSnapPreference(): Int {
        return SNAP_TO_START
    }

    override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
        return speed / displayMetrics.densityDpi
    }

    fun setSpeed(speed: Float) {
        Timber.i("SPEED : %s", speed)
        this.speed = speed
    }

    fun setCurrentPositionY(position: Int) {
        currentScrollY = position
    }

    fun setItemHeight(height: Int) {
        itemHeight = height
    }

    override fun computeScrollVectorForPosition(targetPosition: Int): PointF {
        val yDelta = calculateCurrentDistanceToPosition(targetPosition)
        return PointF(0f, yDelta.toFloat())
    }

    private fun calculateCurrentDistanceToPosition(targetPosition: Int): Int {
        val targetScrollY = targetPosition * itemHeight
        return targetScrollY - currentScrollY
    }

    // Hack to avoid the scrolling distance being capped when target item has not been laid out
    override fun updateActionForInterimTarget(action: Action) {
        // find an interim target position
        val scrollVector = computeScrollVectorForPosition(targetPosition)
        if (scrollVector.x == 0f && scrollVector.y == 0f) {
            val target = targetPosition
            action.jumpTo(target)
            stop()
            return
        }
        //normalize(scrollVector); we don't want that
        mTargetVector = scrollVector
        mInterimTargetDx = scrollVector.x.toInt()
        mInterimTargetDy = scrollVector.y.toInt()
        val time = calculateTimeForScrolling(mInterimTargetDy)
        // To avoid UI hiccups, trigger a smooth scroll to a distance little further than the
        // interim target. Since we track the distance travelled in onSeekTargetStep callback, it
        // won't actually scroll more than what we need.
        action.update(mInterimTargetDx, mInterimTargetDy, time, mLinearInterpolator)
    }
}