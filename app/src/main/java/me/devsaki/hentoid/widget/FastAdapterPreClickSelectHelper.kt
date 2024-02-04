package me.devsaki.hentoid.widget

import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.IItem
import com.mikepenz.fastadapter.select.SelectExtension

class FastAdapterPreClickSelectHelper<T : IItem<out RecyclerView.ViewHolder>>(
    private val fastAdapter: FastAdapter<T>,
    private val selectExtension: SelectExtension<T>
) {
    fun onPreClickListener(position: Int): Boolean {
        // Toggle selection while select mode is on
        if (!selectExtension.selectOnLongClick) {
            val selectedPositions = selectExtension.selections
            if (selectedPositions.contains(position) && 1 == selectedPositions.size)
                selectExtension.selectOnLongClick = true
            if (fastAdapter.getItem(position)?.isSelectable == true)
                selectExtension.toggleSelection(position)
            return true
        }
        return false
    }

    fun onPreLongClickListener(position: Int): Boolean {
        val selectedPositions = selectExtension.selections
        if (selectExtension.selectOnLongClick) {
            selectExtension.selectOnLongClick = false
            // No selection -> select things
            if (selectedPositions.isEmpty())
                selectExtension.select(position, fireEvent = false, considerSelectableFlag = true)
            return true
        }
        return false
    }
}