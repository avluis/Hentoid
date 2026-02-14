package me.devsaki.hentoid.json.sources.manhwa18

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.util.network.fixUrl

@JsonClass(generateAdapter = true)
data class Manhwa18ChapterMetadata(
    val props: Manhwa18ChapProperties,
    val url: String
) {
    @JsonClass(generateAdapter = true)
    data class Manhwa18ChapProperties(
        val mangaName: String,
        val mangaCover: String?,
        val chapterName: String,
        val chapterContent: String
    )

    fun update(content: Content, updateImages: Boolean): Content {
        content.site = Site.MANHWA18
        content.url = fixUrl(url, Site.MANHWA18.url)

        content.title = cleanup(props.mangaName + " " + props.chapterName)

        props.mangaCover?.let { content.coverImageUrl = it }

        if (updateImages) {
            content.setImageFiles(emptyList())
            content.qtyPages = 0
        }
        return content
    }
}