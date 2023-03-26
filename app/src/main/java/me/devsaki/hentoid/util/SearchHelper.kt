package me.devsaki.hentoid.util

import me.devsaki.hentoid.database.domains.Attribute

class SearchHelper {

    data class AttributeQueryResult(
        val attributes: List<Attribute>,
        val totalSelectedAttributes: Long
    )

    data class AdvancedSearchCriteria(
        var attributes: MutableList<Attribute> = ArrayList(),
        var query: String = "",
        @ContentHelper.Location var location: Int = ContentHelper.Location.ANY,
        @ContentHelper.Type var contentType: Int = ContentHelper.Type.ANY
    ) {
        fun clear() {
            attributes.clear()
            query = ""
            location = ContentHelper.Location.ANY
            contentType = ContentHelper.Type.ANY
        }

        fun isEmpty(): Boolean {
            return (query.isEmpty() && attributes.isEmpty() && ContentHelper.Location.ANY == location && ContentHelper.Type.ANY == contentType)
        }
    }
}