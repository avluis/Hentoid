package me.devsaki.hentoid.widget

import androidx.recyclerview.widget.RecyclerView

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
}