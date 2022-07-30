package me.devsaki.hentoid.viewholders

import android.view.View
import android.widget.TextView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import com.mikepenz.fastadapter.ui.utils.StringHolder
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.domains.Attribute

class AttributeItem(val attribute: Attribute) : AbstractItem<AttributeItem.ViewHolder>() {

    var name: StringHolder? = null

    init {
        name = StringHolder(attribute.displayName)
        tag = attribute
        identifier = attribute.uniqueHash()
    }


    override val type: Int get() = R.id.attribute

    override val layoutRes: Int get() = R.layout.item_chip_choice

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v)
    }

    class ViewHolder(view: View) : FastAdapter.ViewHolder<AttributeItem>(view) {
        var name: TextView = itemView.findViewById(R.id.attributeChip)

        override fun bindView(item: AttributeItem, payloads: List<Any>) {
            //set the text for the name
            StringHolder.applyTo(item.name, name)
        }

        override fun unbindView(item: AttributeItem) {
            name.text = null
        }
    }
}