package me.devsaki.hentoid.viewholders

import android.view.View
import android.widget.TextView

import androidx.recyclerview.widget.RecyclerView

import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.domains.Attribute

class AttributeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val view: TextView = itemView.findViewById(R.id.attributeChip)

    fun bindTo(attribute: Attribute, useNamespace: Boolean) {
        view.text = attribute.formatLabel(itemView.resources, useNamespace)
        if (attribute.isExcluded) {
            view.text = "[x]" + view.text
        }
        view.tag = attribute
    }
}
