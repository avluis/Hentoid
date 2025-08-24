package me.devsaki.hentoid.viewholders

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.domains.Attribute

class AttributeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val badge: Chip = itemView.findViewById(R.id.badge)

    fun bindTo(attribute: Attribute, showCount: Boolean = true) {
        formatAttrChip(badge, attribute, showCount)
    }
}
