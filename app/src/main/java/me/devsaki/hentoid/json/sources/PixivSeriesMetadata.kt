package me.devsaki.hentoid.json.sources

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.util.JsonHelper
import me.devsaki.hentoid.util.KEY_DL_PARAMS_NB_CHAPTERS
import me.devsaki.hentoid.util.StringHelper

/**
 * Data structure for Pixiv's "series" mobile endpoint
 */
@SuppressWarnings("unused, MismatchedQueryAndUpdateOfCollection", "squid:S1172", "squid:S1068")
data class PixivSeriesMetadata(
    private val error: Boolean,
    private val message: String?,
    private val body: SeriesBody?
) {
    data class SeriesBody(
        val series: SeriesData?
    )

    data class SeriesData(
        private val id: String?,
        private val userId: String?,
        private val title: String?,
        private val coverImage: String?,
        private val workCount: String?
    ) {
        fun getNbIllust(): String {
            return if (workCount != null && StringHelper.isNumeric(workCount)) workCount else "0"
        }

        fun getId(): String {
            return StringHelper.protect(id)
        }

        fun getUserId(): String {
            return StringHelper.protect(userId)
        }

        fun getTitle(): String {
            return StringHelper.protect(title)
        }

        fun getCoverUrl(): String {
            return StringHelper.protect(coverImage)
        }
    }

    fun update(content: Content, updateImages: Boolean): Content {
        // Determine the prefix the user is navigating with (i.e. with or without language path)
        content.setSite(Site.PIXIV)
        if (error || null == body || null == body.series) return content.setStatus(StatusContent.IGNORED)
        val data: SeriesData = body.series
        content.setTitle(cleanup(data.getTitle()))
        content.uniqueSiteId = data.getId()
        content.setUrl("user/" + data.getUserId() + "/series/" + data.getId())
        content.setCoverImageUrl(data.getCoverUrl())
        //        content.setUploadDate(
        val downloadParams: MutableMap<String, String> = HashMap()
        downloadParams[KEY_DL_PARAMS_NB_CHAPTERS] = data.getNbIllust()
        content.setDownloadParams(
            JsonHelper.serializeToJson<Map<String, String>>(
                downloadParams,
                JsonHelper.MAP_STRINGS
            )
        )
        if (updateImages) {
            content.setImageFiles(emptyList())
            content.setQtyPages(0)
        }
        return content
    }
}