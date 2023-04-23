package me.devsaki.hentoid.activities.bundles

import android.net.Uri
import android.os.Bundle
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.util.SearchHelper
import me.devsaki.hentoid.util.StringHelper
import me.devsaki.hentoid.util.boolean
import me.devsaki.hentoid.util.intArrayList
import me.devsaki.hentoid.util.long
import me.devsaki.hentoid.util.string

/**
 * Helper class to transfer data from any Activity to [me.devsaki.hentoid.activities.SearchActivityK]
 * through a Bundle
 */
class SearchActivityBundle(val bundle: Bundle = Bundle()) {

    // Only used internally by SearchActivity to communicate with its bottom fragment
    var attributeTypes by bundle.intArrayList()

    // Used both internally and externally (i.e. to communicate with LibraryActivity) for display
    var excludeMode by bundle.boolean(default = false)

    // Used both internally and externally (i.e. to communicate with LibraryActivity) for actual search
    var groupId by bundle.long(default = -1)

    var uri by bundle.string(default = "")


    // Helper methods
    companion object {
        fun buildSearchUri(
            advancedSearchCriteria: SearchHelper.AdvancedSearchCriteria,
            query: String? = null
        ): Uri {
            return buildSearchUri(
                advancedSearchCriteria.attributes,
                query ?: advancedSearchCriteria.query,
                advancedSearchCriteria.location,
                advancedSearchCriteria.contentType
            )
        }

        fun buildSearchUri(
            attributes: List<Attribute>?,
            query: String = "",
            location: Int = 0,
            contentType: Int = 0
        ): Uri {
            val searchUri = Uri.Builder()
                .scheme("search")
                .authority("hentoid")

            if (query.isNotEmpty()) searchUri.path(query)

            if (attributes != null) enrichAttrs(attributes, searchUri)

            if (location > 0) searchUri.appendQueryParameter("location", location.toString())
            if (contentType > 0) searchUri.appendQueryParameter(
                "contentType",
                contentType.toString()
            )

            return searchUri.build()
        }

        private fun enrichAttrs(attributes: List<Attribute>, uri: Uri.Builder) {
            val metadataMap = AttributeMap()
            metadataMap.addAll(attributes)

            for ((attrType, attrs) in metadataMap) {
                if (attrs != null) for (attr in attrs) uri.appendQueryParameter(
                    attrType.name,
                    attr.id.toString() + ";" + attr.name + ";" + attr.isExcluded
                )
            }
        }

        fun parseSearchUri(uri: Uri?): SearchHelper.AdvancedSearchCriteria {
            val attrs: MutableList<Attribute> = ArrayList()
            var location = 0
            var contentType = 0
            var query = ""
            if (uri != null) {
                query = StringHelper.protect(uri.path)
                for (typeStr in uri.queryParameterNames) {
                    val type = AttributeType.searchByName(typeStr)
                    if (type != null) { // Parameter is an Attribute
                        for (attrStr in uri.getQueryParameters(typeStr)) {
                            val attrParams = attrStr.split(";").toTypedArray()
                            if (3 == attrParams.size) {
                                attrs.add(
                                    Attribute(type, attrParams[1])
                                        .setId(attrParams[0].toLong())
                                        .setExcluded(attrParams[2].toBoolean())
                                )
                            }
                        }
                    } else {
                        if ("location" == typeStr) location =
                            uri.getQueryParameters(typeStr)[0].toInt()
                        if ("contentType" == typeStr) contentType =
                            uri.getQueryParameters(typeStr)[0].toInt()
                    }
                }
            }
            return SearchHelper.AdvancedSearchCriteria(attrs, query, location, contentType)
        }
    }
}