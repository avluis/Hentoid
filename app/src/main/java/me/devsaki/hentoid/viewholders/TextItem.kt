package me.devsaki.hentoid.viewholders

import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.drag.IExtendedDraggable
import com.mikepenz.fastadapter.items.AbstractItem
import com.mikepenz.fastadapter.listeners.TouchEventHook
import com.mikepenz.fastadapter.ui.utils.FastAdapterUIUtils
import com.mikepenz.fastadapter.ui.utils.FastAdapterUIUtils.adjustAlpha
import com.mikepenz.fastadapter.ui.utils.FastAdapterUIUtils.getSelectablePressedBackground
import com.mikepenz.fastadapter.utils.DragDropUtil.bindDragHandle
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.Consumer
import me.devsaki.hentoid.core.requireById
import me.devsaki.hentoid.util.StringHelper
import me.devsaki.hentoid.util.ThemeHelper

class TextItem<T> : AbstractItem<TextItem.ViewHolder<T>>,
    IExtendedDraggable<TextItem.ViewHolder<T>> {
    val text: String
    private val mTag: T?

    private var centered = false
    private var draggable = false
    private var reformatCase = false
    private var highlighted = false
    var isSimple = false

    override var touchHelper: ItemTouchHelper? = null


    constructor(text: String, tag: T, centered: Boolean) : super() {
        this.text = text
        this.mTag = tag
        this.centered = centered
        draggable = false
        touchHelper = null
        reformatCase = true
        highlighted = false
        isSelectable = false
    }

    constructor(text: String, tag: T, reformatCase: Boolean, isSelected: Boolean) : super() {
        this.text = text
        this.mTag = tag
        centered = false
        draggable = false
        touchHelper = null
        this.reformatCase = reformatCase
        highlighted = false
        this.isSelected = isSelected
        isSelectable = true
    }

    constructor(
        text: String,
        tag: T,
        draggable: Boolean,
        reformatCase: Boolean,
        isHighlighted: Boolean,
        centered: Boolean,
        touchHelper: ItemTouchHelper? = null,
        selectable : Boolean = true
    ) : super() {
        this.text = text
        this.mTag = tag
        this.centered = centered
        this.draggable = draggable
        this.touchHelper = touchHelper
        this.reformatCase = reformatCase
        this.highlighted = isHighlighted
        isSelectable = selectable
    }

    fun getObject(): T? {
        return mTag
    }

    private fun getDisplayText(): String {
        return if (reformatCase) StringHelper.capitalizeString(text) else text
    }

    override val isDraggable: Boolean
        get() = draggable

    val isHighlighted: Boolean
        get() = highlighted

    override fun getViewHolder(v: View): ViewHolder<T> {
        return ViewHolder(v)
    }

    override fun getDragView(viewHolder: ViewHolder<T>): View? {
        return viewHolder.dragHandle
    }

    override val layoutRes: Int
        get() = if (isSimple) R.layout.item_text_simple else R.layout.item_text

    override val type: Int
        get() = R.id.text

    class ViewHolder<T> internal constructor(private val root: View) :
        FastAdapter.ViewHolder<TextItem<T>>(root), IDraggableViewHolder {
        private val title: TextView = root.requireById(R.id.item_txt)
        private val checkedIndicator: ImageView? = root.findViewById(R.id.checked_indicator)
        val dragHandle: ImageView? = root.findViewById(R.id.item_handle)

        init {
            val color = ThemeHelper.getColor(root.context, R.color.secondary_light)
            root.background =
                getSelectablePressedBackground(root.context, adjustAlpha(color, 100), 50, true)
        }

        override fun bindView(item: TextItem<T>, payloads: List<Any>) {
            if (item.draggable) {
                dragHandle?.visibility = View.VISIBLE
                @Suppress("UNCHECKED_CAST")
                bindDragHandle(this, item as IExtendedDraggable<RecyclerView.ViewHolder>)
            }

            checkedIndicator?.apply {
                visibility = if (item.isSelected) View.VISIBLE
                else if (item.isSelectable) View.INVISIBLE
                else View.GONE
            }

            title.text = item.getDisplayText()
            if (item.centered) title.gravity = Gravity.CENTER
            if (item.isHighlighted) title.setTypeface(null, Typeface.BOLD)
            else title.setTypeface(null, Typeface.NORMAL)
        }

        override fun unbindView(item: TextItem<T>) {
            // No specific behaviour to implement
        }

        override fun onDragged() {
            root.setBackgroundColor(
                ThemeHelper.getColor(
                    root.context,
                    R.color.white_opacity_25
                )
            )
        }

        override fun onDropped() {
            root.setBackgroundColor(ThemeHelper.getColor(root.context, R.color.transparent))
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