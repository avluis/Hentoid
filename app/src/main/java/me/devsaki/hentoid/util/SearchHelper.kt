package me.devsaki.hentoid.util

import me.devsaki.hentoid.database.domains.Attribute

class SearchHelper {

    data class AttributeQueryResult(
        val attributes: List<Attribute>,
        val totalSelectedAttributes: Long
    )

    data class AdvancedSearchCriteria(
        var attributes: MutableList<Attribute> = ArrayList(),
        @ContentHelper.Location var location: Int = ContentHelper.Location.ANY,
        @ContentHelper.Type var contentType: Int = ContentHelper.Type.ANY
    ) {
        fun clear() {
            attributes.clear()
            location = ContentHelper.Location.ANY
            contentType = ContentHelper.Type.ANY
        }

        fun isEmpty(): Boolean {
            return (attributes.isEmpty() && ContentHelper.Location.ANY == location && ContentHelper.Type.ANY == contentType)
        }
    }

    companion object {
        fun getEmptyAdvancedSearchCriteria(): AdvancedSearchCriteria {
            return AdvancedSearchCriteria()
        }
    }
}