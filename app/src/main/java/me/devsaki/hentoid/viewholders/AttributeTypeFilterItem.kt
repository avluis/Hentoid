package me.devsaki.hentoid.viewholders

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.AttributeItemBundle
import me.devsaki.hentoid.enums.AttributeType

class AttributeTypeFilterItem(val attributeType: AttributeType, val selected: Boolean) :
    AbstractItem<AttributeTypeFilterItem.ViewHolder>() {

    init {
        isSelected = selected
        tag = attributeType
        identifier = attributeType.code.toLong()
    }


    override val type: Int get() = R.id.attribute_type_filter

    override val layoutRes: Int get() = R.layout.item_attribute_type_filter

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v)
    }

    class ViewHolder(view: View) : FastAdapter.ViewHolder<AttributeTypeFilterItem>(view) {
        private val badge: ConstraintLayout = itemView.findViewById(R.id.badge)
        private val colorDot: TextView = itemView.findViewById(R.id.colorDot)
        private val label: TextView = itemView.findViewById(R.id.label)

        override fun bindView(item: AttributeTypeFilterItem, payloads: List<Any>) {
            if (payloads.isNotEmpty()) {
                val bundleParser = AttributeItemBundle(payloads[0] as Bundle)
                val boolValue = bundleParser.selected
                item.isSelected = boolValue
            }
            badge.setBackgroundResource(if (item.isSelected) R.drawable.selector_chip_pressed else R.drawable.selector_chip_enabled)
            colorDot.setTextColor(ContextCompat.getColor(badge.context, item.attributeType.color))
            label.text = badge.context.getText(item.attributeType.displayName)
        }

        override fun unbindView(item: AttributeTypeFilterItem) {
            // Nothing special here
        }
    }
}