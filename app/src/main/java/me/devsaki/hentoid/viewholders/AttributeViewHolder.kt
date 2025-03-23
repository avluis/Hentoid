package me.devsaki.hentoid.viewholders

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.domains.Attribute

class AttributeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val badge: TextView = itemView.findViewById(R.id.badge)

    fun bindTo(attribute: Attribute) {
        badge.text = formatAttrBadge(
            badge.context,
            attribute,
            attribute.count > 0,
            attribute.isExcluded
        )
        badge.tag = attribute
    }
}
