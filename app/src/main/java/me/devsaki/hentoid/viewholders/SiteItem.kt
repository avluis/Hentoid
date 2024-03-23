package me.devsaki.hentoid.viewholders

import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.drag.IExtendedDraggable
import com.mikepenz.fastadapter.items.AbstractItem
import com.mikepenz.fastadapter.utils.DragDropUtil.bindDragHandle
import me.devsaki.hentoid.R
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.getThemedColor

class SiteItem : AbstractItem<SiteItem.ViewHolder>, IExtendedDraggable<SiteItem.ViewHolder> {

    val site: Site
    private var showHandle = false
    private var mTouchHelper: ItemTouchHelper? = null

    constructor(site: Site, selected: Boolean, touchHelper: ItemTouchHelper) : super() {
        this.site = site
        isSelected = selected
        showHandle = true
        mTouchHelper = touchHelper
    }

    constructor(site: Site, selected: Boolean, showHandle: Boolean) : super() {
        this.site = site
        this.showHandle = showHandle
        isSelected = selected
        mTouchHelper = null
    }

    override val type: Int
        get() = R.id.drawer_edit
    override val layoutRes: Int
        get() = R.layout.item_drawer_edit
    override val isDraggable: Boolean
        get() = true
    override val touchHelper: ItemTouchHelper?
        get() = mTouchHelper

    override fun getDragView(viewHolder: ViewHolder): View {
        return viewHolder.dragHandle
    }

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v)
    }

    class ViewHolder internal constructor(view: View) : FastAdapter.ViewHolder<SiteItem>(view),
        IDraggableViewHolder {
        private val rootView: View = view
        val dragHandle: ImageView = view.findViewById(R.id.drawer_item_handle)
        private val icon: ImageView = view.findViewById(R.id.drawer_item_icon)
        private val title: TextView = view.findViewById(R.id.drawer_item_txt)
        private val chk: CheckBox = view.findViewById(R.id.drawer_item_chk)

        override fun bindView(item: SiteItem, payloads: List<Any>) {
            dragHandle.visibility = if (item.showHandle) View.VISIBLE else View.GONE
            @Suppress("UNCHECKED_CAST")
            if (item.showHandle) bindDragHandle(
                this,
                item as IExtendedDraggable<RecyclerView.ViewHolder>
            )
            title.text = item.site.description
            icon.setImageResource(item.site.ico)
            chk.isChecked = item.isSelected
            chk.setOnCheckedChangeListener { _, b -> item.isSelected = b }
        }

        override fun unbindView(item: SiteItem) {
            chk.setOnCheckedChangeListener(null)
        }

        override fun onDragged() {
            rootView.setBackgroundColor(rootView.context.getThemedColor(R.color.white_opacity_25))
        }

        override fun onDropped() {
            rootView.setBackgroundColor(rootView.context.getThemedColor(R.color.transparent))
        }
    }
}