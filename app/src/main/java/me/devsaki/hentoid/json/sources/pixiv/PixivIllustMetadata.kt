package me.devsaki.hentoid.json.sources.pixiv

import com.squareup.moshi.Json
import com.squareup.moshi.Types
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.parsers.urlToImageFile
import me.devsaki.hentoid.util.KEY_DL_PARAMS_UGOIRA_FRAMES
import me.devsaki.hentoid.util.MAP_STRINGS
import me.devsaki.hentoid.util.isNumeric
import me.devsaki.hentoid.util.serializeToJson
import java.lang.reflect.Type

/**
 * Data structure for Pixiv's "illust details" mobile endpoint
 */
private val UGOIRA_FRAME_TYPE: Type = Types.newParameterizedType(
    Pair::class.java,
    String::class.java,
    Integer::class.java
)
val UGOIRA_FRAMES_TYPE: Type = Types.newParameterizedType(
    MutableList::class.java, UGOIRA_FRAME_TYPE
)


@SuppressWarnings("unused, MismatchedQueryAndUpdateOfCollection", "squid:S1172", "squid:S1068")
data class PixivIllustMetadata(
    val error: Boolean,
    val message: String?,
    val body: IllustBody?
) {
    data class IllustBody(
        @Json(name = "illust_details")
        val illustDetails: IllustDetails?,
        @Json(name = "author_details")
        val authorDetails: AuthorDetails?
    ) {
        val tags: List<Pair<String, String>>
            get() = illustDetails?.getTags() ?: emptyList()
        val imageFiles: List<ImageFile>
            get() = illustDetails?.getImageFiles() ?: emptyList()
        val thumbUrl: String
            get() = illustDetails?.getThumbUrl() ?: ""
        val illustId: String?
            get() = if (illustDetails != null) illustDetails.id else ""
        val title: String?
            get() = if (illustDetails != null) illustDetails.title else ""
        val uploadTimestamp: Long?
            get() = if (illustDetails != null) illustDetails.uploadTimestamp else 0L
        val pageCount: Int
            get() = if (illustDetails?.mangaA != null) illustDetails.mangaA.size else 0
        val userId: String
            get() = authorDetails?.id ?: ""
        val userName: String
            get() = authorDetails?.name ?: ""
        val canonicalUrl: String
            get() = illustDetails?.getCanonicalUrl() ?: ""
        val ugoiraSrc: String
            get() = illustDetails?.getUgoiraSrc() ?: ""
        val ugoiraFrames: List<Pair<String, Int>>
            get() = illustDetails?.getUgoiraFrames() ?: emptyList()
    }


    data class IllustDetails(
        val id: String?,
        val title: String?,
        @Json(name = "upload_timestamp")
        val uploadTimestamp: Long?,
        @Json(name = "page_count")
        val pageCount: String?,
        private val tags: List<String>?,
        @Json(name = "manga_a")
        val mangaA: List<PageData>?,
        @Json(name = "display_tags")
        val displayTags: List<TagData>?,
        val meta: MetaData?,
        @Json(name = "url_s")
        val urlS: String?,
        @Json(name = "url_big")
        val urlBig: String?,
        @Json(name = "ugoira_meta")
        val ugoiraMeta: UgoiraData?,
    ) {

        fun getThumbUrl(): String {
            return urlS ?: ""
        }

        fun getTags(): List<Pair<String, String>> {
            return displayTags?.map { obj -> obj.getTag() } ?: emptyList()
        }

        fun getImageFiles(): List<ImageFile> {
            var pageCount = 0
            if (this.pageCount != null && isNumeric(this.pageCount)) pageCount =
                this.pageCount.toInt()

            // TODO include cover in the page list (getThumbUrl) ?
            return if (1 == pageCount) {
                val img: ImageFile
                if (null == ugoiraMeta) { // One single page
                    img = urlToImageFile(urlBig!!, 1, 1, StatusContent.SAVED)
                } else { // One single ugoira
                    img = urlToImageFile(ugoiraMeta.src, 1, 1, StatusContent.SAVED)
                    val downloadParams: MutableMap<String, String> = HashMap()
                    val framesJson = serializeToJson(ugoiraMeta.getFrameList(), UGOIRA_FRAMES_TYPE)
                    downloadParams[KEY_DL_PARAMS_UGOIRA_FRAMES] = framesJson
                    img.downloadParams =
                        serializeToJson<Map<String, String>>(downloadParams, MAP_STRINGS)
                }
                listOf(img)
            } else { // Classic page list
                if (null == mangaA) return emptyList()
                var order = 1
                val result: MutableList<ImageFile> = ArrayList()
                for (pd in mangaA) {
                    result.add(
                        urlToImageFile(
                            pd.getUrl(),
                            order++,
                            mangaA.size,
                            StatusContent.SAVED
                        )
                    )
                }
                result
            }
        }

        fun getCanonicalUrl(): String {
            return meta?.getCanonicalUrl() ?: ""
        }

        fun getUgoiraSrc(): String {
            return ugoiraMeta?.src ?: ""
        }

        fun getUgoiraFrames(): List<Pair<String, Int>> {
            return ugoiraMeta?.getFrameList() ?: emptyList()
        }
    }


    data class PageData(
        private val page: Int?,
        private val url: String?,
        @Json(name = "url_small")
        private val urlSmall: String?,
        @Json(name = "url_big")
        private val urlBig: String?
    ) {
        fun getUrl(): String {
            var result = urlBig
            if (null == result) result = url
            return result ?: ""
        }
    }


    data class TagData(
        private val tag: String?,
        private val romaji: String?,
        private val translation: String?
    ) {
        fun getTag(): Pair<String, String> {
            var label = translation
            if (null == label) label = romaji
            if (null == label) label = tag
            if (null == label) label = ""
            return Pair(tag ?: "", label)
        }
    }


    data class MetaData(
        private val canonical: String?
    ) {
        fun getCanonicalUrl(): String {
            return canonical ?: ""
        }
    }


    data class AuthorDetails(
        @Json(name = "user_id")
        val id: String,
        @Json(name = "user_name")
        val name: String
    )


    data class UgoiraData(
        val src: String,
        @Json(name = "mime_type")
        val mimeType: String,
        val frames: List<UgoiraFrameData>?
    ) {
        fun getFrameList(): List<Pair<String, Int>> {
            return frames?.map { f -> Pair(f.file, f.delay) } ?: emptyList()
        }
    }


    data class UgoiraFrameData(
        val file: String,
        val delay: Int
    )

    fun getImageFiles(): List<ImageFile> {
        return if (error || (null == body) || (null == body.illustDetails)) emptyList() else body.imageFiles
    }

    fun getUrl(): String {
        return if (error || null == body) "" else body.canonicalUrl
    }

    fun getTitle(): String {
        return if (error || null == body) "" else body.title ?: ""
    }

    fun getId(): String {
        return if (error || null == body) "" else body.illustId ?: ""
    }

    fun getAttributes(): List<Attribute> {
        val result: MutableList<Attribute> = ArrayList()
        if (error || null == body || null == body.illustDetails) return result
        val illustData: IllustBody = body
        var attribute = Attribute(
            AttributeType.ARTIST,
            illustData.userName, Site.PIXIV.url + "user/" + illustData.userId, Site.PIXIV
        )
        result.add(attribute)
        for (tag in illustData.tags) {
            val name = cleanup(tag.second)
            val type = AttributeType.TAG
            attribute = Attribute(type, name, Site.PIXIV.url + "tags/" + tag.first, Site.PIXIV)
            result.add(attribute)
        }
        return result
    }

    fun update(content: Content, url: String, updateImages: Boolean): Content {
        // Determine the prefix the user is navigating with (i.e. with or without language path)
        content.site = Site.PIXIV
        if (error || null == body || null == body.illustDetails) {
            content.status = StatusContent.IGNORED
            return content
        }
        val illustData: IllustBody = body
        content.title = cleanup(illustData.title)
        content.uniqueSiteId = illustData.illustId!!
        var urlValue = illustData.canonicalUrl
        if (urlValue.isEmpty()) urlValue = url
        content.setRawUrl(urlValue)
        content.coverImageUrl = illustData.thumbUrl
        content.uploadDate = illustData.uploadTimestamp!! * 1000
        content.putAttributes(getAttributes())
        if (updateImages) {
            content.setImageFiles(illustData.imageFiles)
            content.qtyPages = illustData.pageCount
        }
        return content
    }
}