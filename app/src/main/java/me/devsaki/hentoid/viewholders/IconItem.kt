package me.devsaki.hentoid.viewholders

import android.view.View
import android.widget.ImageView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import com.mikepenz.fastadapter.ui.utils.FastAdapterUIUtils.adjustAlpha
import com.mikepenz.fastadapter.ui.utils.FastAdapterUIUtils.getSelectablePressedBackground
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.requireById
import me.devsaki.hentoid.util.getThemedColor

class IconItem<T> : AbstractItem<IconItem.ViewHolder<T>> {
    val res: Int
    private val mTag: T?

    private var highlighted = false

    constructor(res: Int, tag: T) : super() {
        this.res = res
        this.mTag = tag
        highlighted = false
        isSelectable = false
    }

    fun getObject(): T? {
        return mTag
    }

    val isHighlighted: Boolean
        get() = highlighted

    override fun getViewHolder(v: View): ViewHolder<T> {
        return ViewHolder(v)
    }

    override val layoutRes: Int
        get() = R.layout.item_icon_simple

    override val type: Int
        get() = R.id.icon

    class ViewHolder<T> internal constructor(private val root: View) :
        FastAdapter.ViewHolder<IconItem<T>>(root) {
        private val icon: ImageView = root.requireById(R.id.item_icon)

        init {
            val color = root.context.getThemedColor(R.color.secondary_light)
            root.background =
                getSelectablePressedBackground(root.context, adjustAlpha(color, 100), 50, true)
        }

        override fun bindView(item: IconItem<T>, payloads: List<Any>) {
            icon.setImageResource(item.res)
        }

        override fun unbindView(item: IconItem<T>) {
            // No specific behaviour to implement
        }
    }
}