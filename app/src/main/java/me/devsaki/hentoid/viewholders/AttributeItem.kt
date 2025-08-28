package me.devsaki.hentoid.viewholders

import android.content.Context
import android.os.Bundle
import android.text.SpannableString
import android.view.View
import androidx.core.content.ContextCompat
import cn.nekocode.badge.BadgeDrawable
import com.google.android.material.chip.Chip
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.AttributeItemBundle
import me.devsaki.hentoid.database.domains.Attribute

class AttributeItem(
    val attribute: Attribute,
    val showCount: Boolean,
    val isSuggestion: Boolean = false
) :
    AbstractItem<AttributeItem.ViewHolder>() {

    init {
        tag = attribute
        identifier = attribute.uniqueHash()
    }

    override val type: Int get() = R.id.attribute

    override val layoutRes: Int get() = if (isSuggestion) R.layout.item_badge_suggestion else R.layout.item_badge_input

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v)
    }

    class ViewHolder(view: View) : FastAdapter.ViewHolder<AttributeItem>(view) {
        val badge: Chip = itemView.findViewById(R.id.badge)

        override fun bindView(item: AttributeItem, payloads: List<Any>) {
            if (payloads.isNotEmpty()) {
                val bundleParser = AttributeItemBundle(payloads[0] as Bundle)
                val stringValue = bundleParser.name
                if (stringValue != null) item.attribute.name = stringValue
                val intValue = bundleParser.count
                if (intValue != null) item.attribute.count = intValue
            }
            formatAttrChip(badge, item.attribute, item.showCount)
        }

        override fun unbindView(item: AttributeItem) {
            // Nothing special here
        }
    }
}

fun formatAttrChip(
    badge: Chip,
    attribute: Attribute,
    showCount: Boolean = true
) {
    val sb = StringBuilder()
    if (attribute.isExcluded) sb.append("✖ ")
    sb.append(attribute.displayName.lowercase())
    if (showCount && attribute.count > 0) sb.append(" (").append(attribute.count).append(")")
    badge.text = sb.toString()
    badge.setChipBackgroundColorResource(attribute.type.color)
    badge.setChipStrokeColorResource(attribute.type.color)
    badge.chipIcon = null
    badge.tag = attribute
}