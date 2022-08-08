package me.devsaki.hentoid.viewholders

import android.view.View
import android.widget.TextView
import cn.nekocode.badge.BadgeDrawable
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

    override val layoutRes: Int get() = R.layout.item_badge

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v)
    }

    class ViewHolder(view: View) : FastAdapter.ViewHolder<AttributeItem>(view) {
        val badge: TextView = itemView.findViewById(R.id.badge)
        lateinit var badgeDrawable: BadgeDrawable

        override fun bindView(item: AttributeItem, payloads: List<Any>) {
            val badgePaddingV = badge.resources.getDimension(R.dimen.badge_padding_vertical)
            val badgePaddingH = badge.resources.getDimension(R.dimen.badge_padding_horizontal)
            val badgeStroke = badge.resources.getDimension(R.dimen.badge_stroke_width).toInt()
            val badgeType =
                if (0 == item.attribute.count) BadgeDrawable.TYPE_ONLY_ONE_TEXT else BadgeDrawable.TYPE_WITH_TWO_TEXT_COMPLEMENTARY
            badgeDrawable = BadgeDrawable.Builder()
                .type(badgeType)
                .badgeColor(badge.context.getColor(item.attribute.type.color))
                .text1(item.attribute.displayName)
                .text2(item.attribute.count.toString())
                .padding(badgePaddingH, badgePaddingV, badgePaddingH, badgePaddingV, badgePaddingH)
                .strokeWidth(badgeStroke)
                .build()
            badge.text = badgeDrawable.toSpannable()
        }

        override fun unbindView(item: AttributeItem) {
            // Nothing special here
        }
    }
}