package me.devsaki.hentoid.util

import android.content.Context
import android.content.res.Resources
import android.text.TextUtils
import androidx.annotation.StringRes
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.domains.Attribute

object SearchHelper {

    data class AttributeQueryResult(
        val attributes: List<Attribute>,
        val totalSelectedAttributes: Long
    )

    data class SearchCriteria(
        // From universal search
        var query: String = "",
        // From advanced search
        var attributes: MutableList<Attribute> = ArrayList(),
        @ContentHelper.Location var location: Int = ContentHelper.Location.ANY,
        @ContentHelper.Type var contentType: Int = ContentHelper.Type.ANY
    ) {
        fun clear() {
            query = ""
            attributes.clear()
            location = ContentHelper.Location.ANY
            contentType = ContentHelper.Type.ANY
        }

        fun isEmpty(): Boolean {
            return (query.isEmpty() && attributes.isEmpty() && ContentHelper.Location.ANY == location && ContentHelper.Type.ANY == contentType)
        }

        @StringRes
        private fun formatLocation(@ContentHelper.Location value: Int): Int {
            return when (value) {
                ContentHelper.Location.PRIMARY -> R.string.refresh_location_internal
                ContentHelper.Location.PRIMARY_1 -> R.string.refresh_location_internal_1
                ContentHelper.Location.PRIMARY_2 -> R.string.refresh_location_internal_2
                ContentHelper.Location.EXTERNAL -> R.string.refresh_location_external
                ContentHelper.Location.ANY -> R.string.search_location_entries_1
                else -> R.string.search_location_entries_1
            }
        }

        @StringRes
        private fun formatContentType(@ContentHelper.Type value: Int): Int {
            return when (value) {
                ContentHelper.Type.FOLDER -> R.string.search_type_entries_2
                ContentHelper.Type.STREAMED -> R.string.search_type_entries_3
                ContentHelper.Type.ARCHIVE -> R.string.search_type_entries_4
                ContentHelper.Type.PLACEHOLDER -> R.string.search_type_entries_5
                ContentHelper.Type.ANY -> R.string.search_type_entries_1
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
            if (location != ContentHelper.Location.ANY) labelElts.add(
                "loc:" + context.resources.getString(formatLocation(location)).lowercase()
            )
            if (contentType != ContentHelper.Type.ANY) labelElts.add(
                "type:" + context.resources.getString(formatContentType(contentType)).lowercase()
            )
            var label = TextUtils.join("|", labelElts)
            if (label.length > 50) label = label.substring(0, 50) + "…"
            return label
        }
    }
}