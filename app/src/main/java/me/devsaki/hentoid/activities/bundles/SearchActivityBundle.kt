package me.devsaki.hentoid.activities.bundles

import android.net.Uri
import android.os.Bundle
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.util.*

/**
 * Helper class to transfer data from any Activity to [me.devsaki.hentoid.activities.SearchActivity]
 * through a Bundle
 */
class SearchActivityBundle(val bundle: Bundle = Bundle()) {

    var attributeTypes by bundle.intArrayList()

    var excludeMode by bundle.boolean(default = false)

    var mode by bundle.int(default = -1)

    var groupId by bundle.long(default = -1)

    var uri by bundle.string(default = "")

    // Helper methods
    companion object {
        fun buildSearchUri(attributes: List<Attribute>?): Uri {
            val metadataMap = AttributeMap()
            if (attributes != null) metadataMap.addAll(attributes)
            val searchUri = Uri.Builder()
                .scheme("search")
                .authority("hentoid")
            for ((attrType, attrs) in metadataMap) {
                if (attrs != null) for (attr in attrs) searchUri.appendQueryParameter(
                    attrType.name,
                    attr.id.toString() + ";" + attr.name + ";" + attr.isExcluded
                )
            }
            return searchUri.build()
        }

        fun parseSearchUri(uri: Uri?): List<Attribute> {
            val result: MutableList<Attribute> = ArrayList()
            if (uri != null) for (typeStr in uri.queryParameterNames) {
                val type = AttributeType.searchByName(typeStr)
                if (type != null) for (attrStr in uri.getQueryParameters(typeStr)) {
                    val attrParams = attrStr.split(";").toTypedArray()
                    if (3 == attrParams.size) {
                        result.add(
                            Attribute(type, attrParams[1])
                                .setId(attrParams[0].toLong())
                                .setExcluded(attrParams[2].toBoolean())
                        )
                    }
                }
            }
            return result
        }
    }
}