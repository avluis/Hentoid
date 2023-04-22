package me.devsaki.hentoid.widget

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler

// Courtesy of https://blog.usejournal.com/improve-recyclerview-performance-ede5cec6c5bf
class PrefetchLinearLayoutManager(context: Context) : LinearLayoutManager(context) {

    private var extraLayoutSpace = -1

    // "delta distance" used during the last scroll action
    var rawDeltaPx = 0
        private set

    fun setExtraLayoutSpace(extraLayoutSpace: Int) {
        this.extraLayoutSpace = extraLayoutSpace
    }

    // {@code extraLayoutSpace[0]} should be used for the extra space at the top/left, and
    // {@code extraLayoutSpace[1]} should be used for the extra space at the bottom/right (depending on the orientation)
    override fun calculateExtraLayoutSpace(state: RecyclerView.State, extraLayoutSpace: IntArray) {
        extraLayoutSpace[0] = this.extraLayoutSpace // Used for RTL
        extraLayoutSpace[1] = this.extraLayoutSpace // Used for LTR & Top to bottom
    }

    // Following overrides only used to capture rawDeltaPx
    override fun scrollHorizontallyBy(
        dx: Int,
        recycler: Recycler?,
        state: RecyclerView.State?
    ): Int {
        if (canScrollHorizontally()) rawDeltaPx = dx
        return super.scrollHorizontallyBy(dx, recycler, state)
    }

    override fun scrollVerticallyBy(dy: Int, recycler: Recycler?, state: RecyclerView.State?): Int {
        if (canScrollVertically()) rawDeltaPx = dy
        return super.scrollVerticallyBy(dy, recycler, state)
    }
}