package me.devsaki.hentoid.json.sources

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@SuppressWarnings("unused, MismatchedQueryAndUpdateOfCollection", "squid:S1172", "squid:S1068")
@JsonClass(generateAdapter = true)
data class YoastGalleryMetadata(
    @Json(name = "@graph")
    val graph: List<GraphData>?
) {
    data class GraphData(
        @Json(name = "@type")
        val type: String?,
        val name: String?,
        val datePublished: String?,
        val itemListElement: List<ItemListElement>?
    )

    data class ItemListElement(
        val name: String?,
        @Json(name = "@item")
        val url: String?,
        val position: Int
    )

    fun getName(): String {
        graph?.forEach { data ->
            if (data.type != null && data.type.equals("webpage", ignoreCase = true)) {
                return data.name?.replace(" - NovelCrow", "") ?: ""
            }
        }
        return ""
    }

    fun getDatePublished(): String {
        graph?.forEach { data ->
            if (data.type != null && data.type.equals("webpage", ignoreCase = true)) {
                return data.datePublished ?: ""
            }
        }
        return ""
    }

    fun getBreadcrumbs(): List<Pair<String, String>> {
        graph?.forEach { data ->
            if (data.type != null && data.type.equals("BreadcrumbList", ignoreCase = true)) {
                return data.itemListElement
                    ?.sortedBy { it.position }
                    ?.map { Pair(it.name ?: "", it.url ?: "") }
                    ?: emptyList()
            }
        }
        return emptyList()
    }
}