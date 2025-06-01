package me.devsaki.hentoid.json.sources.simply

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.util.parseDatetimeToEpoch

@JsonClass(generateAdapter = true)
data class SimplyContentMetadata(
    val data: Data? = null
) {
    @JsonClass(generateAdapter = true)
    data class Data(
        val slug: String? = null,
        val title: String? = null,

        @Json(name = "created_at")
        val createdAt: String = "",

        val preview: SimplyPageData? = null,

        @Json(name = "image_count")
        val imageCount: Int,

        //List<PageData> images; <-- only the first few
        val language: LanguageData? = null,
        val artists: List<MetadataEntry>? = null,
        val characters: List<MetadataEntry>? = null,
        val parodies: List<MetadataEntry?>? = null,
        val series: MetadataEntry? = null,
        val tags: List<MetadataEntry>? = null
    )

    data class MetadataEntry(
        val title: String? = null,
        val slug: String? = null
    )

    data class LanguageData(
        val name: String? = null,
        val slug: String? = null
    )

    @JsonClass(generateAdapter = true)
    data class SimplyPageData(
        @Json(name = "page_num")
        val pageNum: Int? = null,
        val sizes: Map<String, String>? = null,
    ) {
        fun getFullUrl(): String {
            if (null == sizes) return ""
            return sizes["full"] ?: sizes["giant_thumb"] ?: ""
        }

        fun getThumbUrl(): String {
            if (null == sizes) return ""
            return sizes["small_thumb"] ?: sizes["thumb"] ?: ""
        }
    }

    fun update(content: Content, updateImages: Boolean): Content {
        content.site = Site.SIMPLY

        if (null == data || null == data.title || null == data.slug) {
            content.status = StatusContent.IGNORED
            return content
        }

        val url = Site.SIMPLY.url + "manga/" + data.slug

        content.url = url
        if (!data.createdAt.isEmpty()) content.uploadDate = parseDatetimeToEpoch(
            data.createdAt,
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            true
        ) // e.g. 2022-10-23T19:47:08.717+02:00


        content.title = cleanup(data.title)

        content.qtyPages = data.imageCount
        data.preview?.let {
            content.coverImageUrl = it.getThumbUrl()
        }

        val attributes = AttributeMap()
        if (data.language != null) {
            val name = cleanup(data.language.name)
            val attribute = Attribute(
                AttributeType.LANGUAGE,
                name,
                Site.SIMPLY.url + "language/" + data.language.slug,
                Site.SIMPLY
            )
            attributes.add(attribute)
        }
        if (data.series != null) {
            val name = cleanup(data.series.title)
            val attribute = Attribute(
                AttributeType.SERIE,
                name,
                Site.SIMPLY.url + "series/" + data.series.slug,
                Site.SIMPLY
            )
            attributes.add(attribute)
        }

        populateAttributes(attributes, data.artists, AttributeType.ARTIST, "artist")
        populateAttributes(attributes, data.characters, AttributeType.CHARACTER, "character")
        //populateAttributes(attributes, data.parodies, AttributeType.SERIE, "parody");
        populateAttributes(attributes, data.tags, AttributeType.TAG, "tag")

        content.putAttributes(attributes)

        if (updateImages) {
            content.setImageFiles(mutableListOf<ImageFile>())
            content.qtyPages = 0
        }

        return content
    }

    private fun populateAttributes(
        attributes: AttributeMap,
        entries: List<MetadataEntry>?,
        type: AttributeType,
        typeUrl: String
    ) {
        entries?.forEach {
            val name = cleanup(it.title)
            val attribute =
                Attribute(type, name, Site.SIMPLY.url + typeUrl + "/" + it.slug, Site.SIMPLY)
            attributes.add(attribute)
        }
    }
}