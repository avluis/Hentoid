package me.devsaki.hentoid.widget

import android.content.Context
import android.util.TypedValue
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max

// https://stackoverflow.com/a/30256880/8374722
class AutofitGridLayoutManager : GridLayoutManager {
    private var columnWidthPx = 0
    private var isColumnWidthChanged = true
    private var lastWidth = 0
    private var lastHeight = 0

    // Initially set spanCount to 1, will be changed automatically later
    constructor(context: Context, columnWidthPx: Int) : super(context, 1) {
        setColumnWidth(checkedColumnWidth(context, columnWidthPx))
    }

    // Initially set spanCount to 1, will be changed automatically later
    constructor(
        context: Context,
        columnWidth: Int,
        orientation: Int,
        reverseLayout: Boolean
    ) : super(context, 1, orientation, reverseLayout) {
        setColumnWidth(checkedColumnWidth(context, columnWidth))
    }

    private fun checkedColumnWidth(context: Context, columnWidthPx: Int): Int {
        return if (columnWidthPx <= 0) {
            /* Set default columnWidth value (48dp here). It is better to move this constant
                to static constant on top, but we need context to convert it to dp, so can't really
                do so. */
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 48f,
                context.resources.displayMetrics
            ).toInt()
        } else columnWidthPx
    }

    private fun setColumnWidth(newColumnWidthPx: Int) {
        if (newColumnWidthPx > 0 && newColumnWidthPx != columnWidthPx) {
            columnWidthPx = newColumnWidthPx
            isColumnWidthChanged = true
        }
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        val widthPx = width
        val heightPx = height
        if (columnWidthPx > 0 && widthPx > 0 && heightPx > 0 && (isColumnWidthChanged || lastWidth != widthPx || lastHeight != heightPx)) {
            val totalSpacePx: Int = if (orientation == VERTICAL) {
                widthPx - paddingRight - paddingLeft
            } else {
                heightPx - paddingTop - paddingBottom
            }
            val spanCount = max(1, totalSpacePx / columnWidthPx)
            setSpanCount(spanCount)
            isColumnWidthChanged = false
        }
        lastWidth = widthPx
        lastHeight = heightPx
        super.onLayoutChildren(recycler, state)
    }
}