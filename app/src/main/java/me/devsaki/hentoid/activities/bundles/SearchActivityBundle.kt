package me.devsaki.hentoid.activities.bundles

import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import androidx.core.net.toUri
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.util.Location
import me.devsaki.hentoid.util.SearchCriteria
import me.devsaki.hentoid.util.Type
import me.devsaki.hentoid.util.boolean
import me.devsaki.hentoid.util.intArrayList
import me.devsaki.hentoid.util.long
import me.devsaki.hentoid.util.string

private const val ATTR_EXCLUDED_TYPES = "excludedTypes"
private const val ATTR_LOCATION = "location"
private const val ATTR_CONTENT_TYPE = "contentType"

/**
 * Helper class to transfer data from any Activity to [me.devsaki.hentoid.activities.SearchActivity]
 * through a Bundle
 */
class SearchActivityBundle(val bundle: Bundle = Bundle()) {

    // Only used internally by SearchActivity to communicate with its bottom fragment
    var attributeTypes by bundle.intArrayList()

    // Used both internally and externally (i.e. to communicate with LibraryActivity) for display
    var excludeMode by bundle.boolean(default = false)

    // Used both internally and externally (i.e. to communicate with LibraryActivity) for actual search
    var groupId by bundle.long(default = -1)

    var uri: String by bundle.string(default = "")


    // Helper methods
    companion object {
        fun buildSearchUri(
            searchCriteria: SearchCriteria,
            query: String? = null
        ): Uri {
            return buildSearchUri(
                searchCriteria.attributes,
                searchCriteria.excludedAttributeTypes,
                query ?: searchCriteria.query,
                searchCriteria.location.value,
                searchCriteria.contentType.value
            )
        }

        fun buildSearchUri(
            attributes: Set<Attribute>?,
            excludedTypes: Collection<AttributeType>? = null,
            query: String = "",
            location: Int = 0,
            contentType: Int = 0
        ): Uri {
            val searchUri = Uri.Builder()
                .scheme("search")
                .authority("hentoid")

            if (query.isNotEmpty()) searchUri.path(query)

            if (attributes != null) addAttrs(attributes, searchUri)

            if (excludedTypes != null) addAttrTypeExclusion(excludedTypes, searchUri)

            if (location > 0) searchUri.appendQueryParameter(ATTR_LOCATION, location.toString())
            if (contentType > 0) searchUri.appendQueryParameter(
                ATTR_CONTENT_TYPE,
                contentType.toString()
            )

            return searchUri.build()
        }

        private fun addAttrs(attributes: Set<Attribute>, uri: Uri.Builder) {
            val metadataMap = AttributeMap()
            metadataMap.addAll(attributes)

            for ((attrType, attrs) in metadataMap) {
                for (attr in attrs)
                    uri.appendQueryParameter(
                        attrType.name,
                        "${attr.id};${attr.name};${attr.isExcluded}"
                    )
            }
        }

        private fun addAttrTypeExclusion(excludedTypes: Collection<AttributeType>, uri: Uri.Builder) {
            uri.appendQueryParameter(
                ATTR_EXCLUDED_TYPES,
                TextUtils.join(";", excludedTypes.map { it.code })
            )
        }

        fun parseSearchUri(uri: String): SearchCriteria {
            return parseSearchUri(uri.toUri())
        }

        fun parseSearchUri(uri: Uri): SearchCriteria {
            val attrs: MutableSet<Attribute> = HashSet()
            val excludedTypes: MutableSet<AttributeType> = HashSet()
            var location = 0
            var contentType = 0
            var query = uri.path ?: ""
            // Remove the leading '/'
            if (query.isNotEmpty()) query = query.substring(1)
            for (typeStr in uri.queryParameterNames) {
                val type = AttributeType.searchByName(typeStr)
                if (type != null) { // Parameter is an Attribute
                    for (attrStr in uri.getQueryParameters(typeStr)) {
                        val attrParams = attrStr.split(";").toTypedArray()
                        if (3 == attrParams.size) {
                            val attr = Attribute(
                                type = type,
                                name = attrParams[1],
                                dbId = attrParams[0].toLong()
                            )
                            attr.isExcluded = attrParams[2].toBoolean()
                            attrs.add(attr)
                        }
                    }
                } else {
                    if (ATTR_EXCLUDED_TYPES == typeStr)
                        excludedTypes.addAll(
                            uri.getQueryParameters(typeStr)[0].split(";")
                                .mapNotNull { AttributeType.searchByCode(it.toInt()) }
                        )
                    if (ATTR_LOCATION == typeStr)
                        location = uri.getQueryParameters(typeStr)[0].toInt()
                    if (ATTR_CONTENT_TYPE == typeStr)
                        contentType = uri.getQueryParameters(typeStr)[0].toInt()
                }
            }
            return SearchCriteria(
                query,
                attrs,
                excludedTypes,
                Location.fromValue(location),
                Type.fromValue(contentType)
            )
        }
    }
}