package me.devsaki.hentoid.viewholders

import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.drag.IExtendedDraggable
import com.mikepenz.fastadapter.items.AbstractItem
import com.mikepenz.fastadapter.listeners.TouchEventHook
import com.mikepenz.fastadapter.ui.utils.FastAdapterUIUtils.adjustAlpha
import com.mikepenz.fastadapter.ui.utils.FastAdapterUIUtils.getSelectablePressedBackground
import com.mikepenz.fastadapter.utils.DragDropUtil.bindDragHandle
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.Consumer
import me.devsaki.hentoid.util.StringHelper
import me.devsaki.hentoid.util.ThemeHelper

class TextItem<T> : AbstractItem<TextItem.ViewHolder<T>>,
    IExtendedDraggable<TextItem.ViewHolder<T>> {
    val text: String
    private val mTag: T?

    private var centered = false
    private var draggable = false
    private var reformatCase = false
    private var isHighlighted = false

    override var touchHelper: ItemTouchHelper? = null


    constructor(text: String, tag: T, centered: Boolean) : super() {
        this.text = text
        this.mTag = tag
        this.centered = centered
        draggable = false
        touchHelper = null
        reformatCase = true
        isHighlighted = false
        isSelectable = false
    }

    constructor(text: String, tag: T, reformatCase: Boolean, isSelected: Boolean) : super() {
        this.text = text
        this.mTag = tag
        centered = false
        draggable = false
        touchHelper = null
        this.reformatCase = reformatCase
        isHighlighted = false
        this.isSelected = isSelected
        isSelectable = true
    }

    constructor(
        text: String,
        tag: T,
        draggable: Boolean,
        reformatCase: Boolean,
        isHighlighted: Boolean,
        touchHelper: ItemTouchHelper?
    ) : super() {
        this.text = text
        this.mTag = tag
        centered = false
        this.draggable = draggable
        this.touchHelper = touchHelper
        this.reformatCase = reformatCase
        this.isHighlighted = isHighlighted
        isSelectable = true
    }

    fun getObject(): T? {
        return mTag
    }

    private fun getDisplayText(): String {
        return if (reformatCase) StringHelper.capitalizeString(text) else text
    }

    override val isDraggable: Boolean
        get() = draggable

    override fun getViewHolder(v: View): ViewHolder<T> {
        return ViewHolder(v)
    }

    override fun getDragView(viewHolder: ViewHolder<T>): View {
        return viewHolder.dragHandle
    }

    override val layoutRes: Int
        get() = R.layout.item_text

    override val type: Int
        get() = R.id.text

    class ViewHolder<T> internal constructor(private val rootView: View) :
        FastAdapter.ViewHolder<TextItem<T>>(rootView), IDraggableViewHolder {
        private val title: TextView = ViewCompat.requireViewById(rootView, R.id.item_txt)
        private val checkedIndicator: ImageView = rootView.findViewById(R.id.checked_indicator)
        val dragHandle: ImageView = rootView.findViewById(R.id.item_handle)

        init {
            val color = ThemeHelper.getColor(rootView.context, R.color.secondary_light)
            rootView.background =
                getSelectablePressedBackground(rootView.context, adjustAlpha(color, 100), 50, true)
        }

        override fun bindView(item: TextItem<T>, payloads: List<Any>) {
            if (item.draggable) {
                dragHandle.visibility = View.VISIBLE
                @Suppress("UNCHECKED_CAST")
                bindDragHandle(this, item as IExtendedDraggable<RecyclerView.ViewHolder>)
            }
            if (item.isSelected) checkedIndicator.visibility =
                View.VISIBLE else if (item.isSelectable) checkedIndicator.visibility =
                View.INVISIBLE else checkedIndicator.visibility = View.GONE
            title.text = item.getDisplayText()
            if (item.centered) title.gravity = Gravity.CENTER
            if (item.isHighlighted) title.setTypeface(null, Typeface.BOLD) else title.setTypeface(
                null,
                Typeface.NORMAL
            )
        }

        override fun unbindView(item: TextItem<T>) {
            // No specific behaviour to implement
        }

        override fun onDragged() {
            rootView.setBackgroundColor(
                ThemeHelper.getColor(
                    rootView.context,
                    R.color.white_opacity_25
                )
            )
        }

        override fun onDropped() {
            rootView.setBackgroundColor(ThemeHelper.getColor(rootView.context, R.color.transparent))
        }
    }

    class DragHandlerTouchEvent<T>(private val action: Consumer<Int>) :
        TouchEventHook<TextItem<T>>() {
        override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
            return if (viewHolder is ViewHolder<*>) viewHolder.dragHandle else null
        }

        override fun onTouch(
            v: View,
            event: MotionEvent,
            position: Int,
            fastAdapter: FastAdapter<TextItem<T>>,
            item: TextItem<T>
        ): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                action.invoke(position)
                return true
            }
            return false
        }
    }
}