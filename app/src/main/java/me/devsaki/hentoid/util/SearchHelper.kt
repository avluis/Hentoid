package me.devsaki.hentoid.util

import android.content.Context
import android.content.res.Resources
import android.text.TextUtils
import androidx.annotation.StringRes
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.domains.Attribute

data class AttributeQueryResult(
    // Ordered list of results
    val attributes: List<Attribute>,
    val totalSelectedAttributes: Long
)

data class SearchCriteria(
    // From universal search
    var query: String = "",
    // From advanced search
    var attributes: MutableSet<Attribute> = HashSet(),
    var location: Location = Location.ANY,
    var contentType: Type = Type.ANY
) {
    fun clear() {
        query = ""
        attributes.clear()
        location = Location.ANY
        contentType = Type.ANY
    }

    fun isEmpty(): Boolean {
        return (query.isEmpty() && attributes.isEmpty() && Location.ANY == location && Type.ANY == contentType)
    }

    @StringRes
    private fun formatLocation(value: Location): Int {
        return when (value) {
            Location.PRIMARY -> R.string.refresh_location_internal
            Location.PRIMARY_1 -> R.string.refresh_location_internal_1
            Location.PRIMARY_2 -> R.string.refresh_location_internal_2
            Location.EXTERNAL -> R.string.refresh_location_external
            Location.ANY -> R.string.search_location_entries_1
        }
    }

    @StringRes
    private fun formatContentType(value: Type): Int {
        return when (value) {
            Type.FOLDER -> R.string.search_type_entries_2
            Type.STREAMED -> R.string.search_type_entries_3
            Type.ARCHIVE -> R.string.search_type_entries_4
            Type.PLACEHOLDER -> R.string.search_type_entries_5
            Type.PDF -> R.string.search_type_entries_6
            else -> R.string.search_type_entries_1
        }
    }

    private fun formatAttribute(a: Attribute, res: Resources): String {
        return String.format(
            "%s%s:%s",
            if (a.isExcluded) "[x]" else "",
            res.getString(a.type.displayName),
            a.displayName
        )
    }

    fun toString(context: Context): String {
        // Universal search
        if (query.isNotEmpty()) return query
        // Advanced search
        val labelElts: MutableList<String> =
            attributes.map { a -> formatAttribute(a, context.resources) }.toMutableList()
        if (location != Location.ANY) labelElts.add(
            "loc:" + context.resources.getString(formatLocation(location)).lowercase()
        )
        if (contentType != Type.ANY) labelElts.add(
            "type:" + context.resources.getString(formatContentType(contentType)).lowercase()
        )
        var label = TextUtils.join("|", labelElts)
        if (label.length > 50) label = label.substring(0, 50) + "…"
        return label
    }
}