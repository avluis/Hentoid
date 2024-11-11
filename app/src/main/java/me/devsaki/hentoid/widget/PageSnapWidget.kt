package me.devsaki.hentoid.widget

import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * Manages page snapping for a RecyclerView
 */
class PageSnapWidget(val recyclerView: RecyclerView) {

    var isEnabled = true
        private set

    private val snapHelper = BlockSnapHelper(1)


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

    fun setMaxFlingBlocks(value: Int) {
        snapHelper.maxFlingBlocks = value
    }

    /**
     * Sets the sensitivity of a fling.
     *
     * @param sensitivity floating point sensitivity where 0 means never fling and 1 means always
     * fling. Values beyond this range will have undefined behavior.
     */
    fun setFlingSensitivity(sensitivity: Float) {
        snapHelper.setFlingSensitivity(sensitivity)
    }

    inner class SnapHelper : PagerSnapHelper() {
        private var flingSensitivity = 0f

        fun setFlingSensitivity(sensitivity: Float) {
            flingSensitivity = sensitivity
        }

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