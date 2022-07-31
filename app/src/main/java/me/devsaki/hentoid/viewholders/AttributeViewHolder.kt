package me.devsaki.hentoid.viewholders

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.enums.AttributeType
import java.util.*

class AttributeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val view: TextView = itemView.findViewById(R.id.attributeChip)

    fun bindTo(attribute: Attribute, useNamespace: Boolean) {
        view.text = formatAttribute(attribute, useNamespace)
        if (attribute.isExcluded) view.text = String.format("[x] %s", view.text)
        view.tag = attribute
    }

    private fun formatAttribute(attribute: Attribute, useNamespace: Boolean): String {
        return String.format(
            "%s%s %s",
            if (useNamespace && !attribute.type.equals(AttributeType.TAG))
                itemView.resources.getString(attribute.type.displayName)
                    .lowercase(Locale.getDefault()) + ":"
            else "",
            attribute.displayName,
            if (attribute.count > 0) "(" + attribute.count + ")" else ""
        )
    }
}
