package me.devsaki.hentoid.viewholders

import android.content.Context
import android.text.SpannableString
import android.view.View
import android.widget.TextView
import cn.nekocode.badge.BadgeDrawable
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import com.mikepenz.fastadapter.ui.utils.StringHolder
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.domains.Attribute

class AttributeItem(val attribute: Attribute, val showCount: Boolean) :
    AbstractItem<AttributeItem.ViewHolder>() {

    var name: StringHolder? = null

    init {
        name = StringHolder(attribute.displayName)
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
            showCount: Boolean
        ): SpannableString {
            val badgePaddingV = context.resources.getDimension(R.dimen.badge_padding_vertical)
            val badgePaddingH = context.resources.getDimension(R.dimen.badge_padding_horizontal)
            val badgeStroke = context.resources.getDimension(R.dimen.badge_stroke_width).toInt()
            val badgeType =
                if (!attribute.isNew && (0 == attribute.count || !showCount)) BadgeDrawable.TYPE_ONLY_ONE_TEXT else BadgeDrawable.TYPE_WITH_TWO_TEXT_COMPLEMENTARY
            val text2 =
                if (attribute.count > 0) attribute.count.toString() else if (attribute.isNew) "+" else "";
            val badgeDrawable = BadgeDrawable.Builder()
                .type(badgeType)
                .badgeColor(context.getColor(attribute.type.color))
                .text1(attribute.displayName.lowercase())
                .text2(text2)
                .padding(badgePaddingH, badgePaddingV, badgePaddingH, badgePaddingV, badgePaddingH)
                .strokeWidth(badgeStroke)
                .build()
            return badgeDrawable.toSpannable()
        }
    }
}