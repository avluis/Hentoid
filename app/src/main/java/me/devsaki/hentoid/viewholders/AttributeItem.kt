package me.devsaki.hentoid.viewholders

import android.content.Context
import android.os.Bundle
import android.text.SpannableString
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import cn.nekocode.badge.BadgeDrawable
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.AttributeItemBundle
import me.devsaki.hentoid.database.domains.Attribute

class AttributeItem(val attribute: Attribute, val showCount: Boolean) :
    AbstractItem<AttributeItem.ViewHolder>() {

    init {
        tag = attribute
        identifier = attribute.uniqueHash()
    }

    override val type: Int get() = R.id.attribute

    override val layoutRes: Int get() = R.layout.item_badge

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v)
    }

    class ViewHolder(view: View) : FastAdapter.ViewHolder<AttributeItem>(view) {
        val badge: TextView = itemView.findViewById(R.id.badge)

        override fun bindView(item: AttributeItem, payloads: List<Any>) {
            if (payloads.isNotEmpty()) {
                val bundleParser = AttributeItemBundle(payloads[0] as Bundle)
                val stringValue = bundleParser.name
                if (stringValue != null) item.attribute.name = stringValue
                val intValue = bundleParser.count
                if (intValue != null) item.attribute.count = intValue
            }
            badge.text = formatAttrBadge(badge.context, item.attribute, item.showCount)
        }

        override fun unbindView(item: AttributeItem) {
            // Nothing special here
        }
    }

    companion object {
        fun formatAttrBadge(
            context: Context,
            attribute: Attribute,
            showCount: Boolean,
            excluded: Boolean = false
        ): SpannableString {
            val badgePaddingV = context.resources.getDimension(R.dimen.badge_padding_vertical)
            val badgePaddingH = context.resources.getDimension(R.dimen.badge_padding_horizontal)
            val badgeStroke = context.resources.getDimension(R.dimen.badge_stroke_width).toInt()
            val badgeType =
                if (!attribute.isNew && (0 == attribute.count || !showCount)) BadgeDrawable.TYPE_ONLY_ONE_TEXT else BadgeDrawable.TYPE_WITH_TWO_TEXT_COMPLEMENTARY
            val text2 =
                if (attribute.count > 0) attribute.count.toString() else if (attribute.isNew) "+" else ""
            val badgeDrawable = BadgeDrawable.Builder()
                .type(badgeType)
                .badgeColor(ContextCompat.getColor(context, attribute.type.color))
                .textColor(ContextCompat.getColor(context, R.color.white_opacity_87))
                .text1((if (excluded) "✖ " else "") + attribute.displayName.lowercase())
                .text2(text2)
                .padding(badgePaddingH, badgePaddingV, badgePaddingH, badgePaddingV, badgePaddingH)
                .strokeWidth(badgeStroke)
                .build()
            return badgeDrawable.toSpannable()
        }
    }
}