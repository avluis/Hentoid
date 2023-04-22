package me.devsaki.hentoid.widget

import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * Manages page snapping for a RecyclerView
 */
class PageSnapWidgetK(val recyclerView: RecyclerView) {

    private val snapHelper = SnapHelper()

    private var flingSensitivity = 0f

    private var isEnabled = false


    init {
        setPageSnapEnabled(true)
    }

    fun setPageSnapEnabled(pageSnapEnabled: Boolean) {
        isEnabled = if (pageSnapEnabled) {
            snapHelper.attachToRecyclerView(recyclerView)
            true
        } else {
            snapHelper.attachToRecyclerView(null)
            false
        }
    }

    fun isPageSnapEnabled(): Boolean {
        return isEnabled
    }

    /**
     * Sets the sensitivity of a fling.
     *
     * @param sensitivity floating point sensitivity where 0 means never fling and 1 means always
     * fling. Values beyond this range will have undefined behavior.
     */
    fun setFlingSensitivity(sensitivity: Float) {
        flingSensitivity = sensitivity
    }

    inner class SnapHelper : PagerSnapHelper() {
        override fun onFling(velocityX: Int, velocityY: Int): Boolean {
            val min: Int = recyclerView.minFlingVelocity
            val max: Int = recyclerView.maxFlingVelocity
            val threshold: Int = (max * (1.0 - flingSensitivity) + min * flingSensitivity).toInt()
            return if (abs(velocityX) > threshold) {
                false
            } else super.onFling(velocityX, velocityY)
        }
    }
}