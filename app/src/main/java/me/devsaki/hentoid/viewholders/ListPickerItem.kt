package me.devsaki.hentoid.viewholders

import android.view.View
import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import com.mikepenz.fastadapter.ui.utils.FastAdapterUIUtils.adjustAlpha
import com.mikepenz.fastadapter.ui.utils.FastAdapterUIUtils.getSelectablePressedBackground
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.Consumer
import me.devsaki.hentoid.core.requireById
import me.devsaki.hentoid.util.getThemedColor
import me.devsaki.hentoid.views.ListPickerView

class ListPickerItem<T> : AbstractItem<ListPickerItem.ViewHolder<T>> {
    val text: String
    @DrawableRes
    val icon: Int
    val entries: List<String>
    val values: List<String>
    val value: String
    val onChanged: Consumer<String>
    private val mTag: T?

    constructor(
        text: String,
        @DrawableRes icon: Int,
        entries: List<String>,
        values: List<String>,
        value: String,
        onChanged: Consumer<String>,
        tag: T
    ) : super() {
        this.text = text
        this.icon = icon
        this.entries = ArrayList(entries)
        this.values = ArrayList(values)
        this.value = value
        this.onChanged = onChanged
        this.mTag = tag
        isSelectable = false
    }

    fun getObject(): T? {
        return mTag
    }

    override fun getViewHolder(v: View): ViewHolder<T> {
        return ViewHolder(v)
    }

    override val layoutRes: Int
        get() = R.layout.item_listpicker

    override val type: Int
        get() = R.id.list_picker

    class ViewHolder<T> internal constructor(root: View) :
        FastAdapter.ViewHolder<ListPickerItem<T>>(root) {
        private val icon: ImageView = root.requireById(R.id.icon)
        private val picker: ListPickerView = root.requireById(R.id.list_picker)

        init {
            val color = root.context.getThemedColor(R.color.secondary_light)
            root.background =
                getSelectablePressedBackground(root.context, adjustAlpha(color, 100), 50, true)
        }

        override fun bindView(item: ListPickerItem<T>, payloads: List<Any>) {
            icon.setImageResource(item.icon)
            picker.title = item.text
            picker.entries = item.entries
            picker.values = item.values
            picker.value = item.value
            picker.setOnValueChangeListener { s -> item.onChanged.invoke(s) }
        }

        override fun unbindView(item: ListPickerItem<T>) {
            // No specific behaviour to implement
        }
    }
}