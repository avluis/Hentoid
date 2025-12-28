package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.Content.Companion.transformRawUrl
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.content.NO_TITLE
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.setDownloadParams
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.getLibraryStatuses
import me.devsaki.hentoid.util.getQueueStatuses
import me.devsaki.hentoid.util.network.fixUrl
import me.devsaki.hentoid.util.network.getOnlineDocument
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import timber.log.Timber

class NhentaiParser : BaseChapteredImageListParser() {
    override fun isChapterUrl(url: String): Boolean {
        return false // Chapter parser is exclusively used for the favourites page
    }

    override fun getChapterSelector(): ChapterSelector {
        return ChapterSelector(listOf(".gallery-favorite a.cover"))
    }

    override fun parseChapterImageFiles(
        content: Content,
        chp: Chapter,
        targetOrder: Int,
        headers: List<Pair<String, String>>?,
        fireProgressEvents: Boolean
    ): List<ImageFile> {
        return parseGallery(content, chp.url, chp, targetOrder, headers)
    }

    companion object {
        const val THUMBS_SELECTOR = "#thumbnail-container img[data-src]"
        const val COVER_SELECTOR = "#cover img"
        private const val FAV_URL = "https://nhentai.net/favorites/"

        fun parseImages(coverImageUrl: String, thumbs: List<Element>): List<String> {
            val coverParts = coverImageUrl.split("/")
            val mediaId = coverParts[coverParts.size - 2]
            // We infer the whole book is stored on the same server
            val serverUrl = "https://i2.nhentai.net/galleries/$mediaId/"
            val result: MutableList<String> = ArrayList()
            var index = 1
            for (e in thumbs) {
                val s = getImgSrc(e)
                if (s.isEmpty()) continue
                result.add(serverUrl + index++ + "." + getNhGalleryExtension(s))
            }
            return result
        }

        private fun getNhGalleryExtension(uri: String): String {
            val name = uri.substring(uri.lastIndexOf("/") + 1)
            if (!name.contains(".")) return ""
            val parts = name.split(".")
            // Classic case
            if (2 == parts.size) return parts[1].lowercase()
            // Nhentai-specific stuff (name.jpg.webp) => last but one is the correct one
            return parts[parts.size - 2].lowercase()
        }
    }

    override fun getChapters(
        content: Content,
        galleryPage: Document
    ): List<Chapter> {
        Timber.i("Gallery : ${content.galleryUrl}")

        // PLAIN BOOK (=> bogus chapter)
        if (!content.url.contains("/favorites/")) {
            val chp = Chapter(
                order = 0,
                url = content.galleryUrl,
                name = content.title,
                uniqueId = content.uniqueSiteId
            )
            chp.setContentId(content.id)
            return listOf(chp)
        }

        // FAVOURITES
        val chapters: MutableList<Chapter> = ArrayList()
        // TODO refactor not to instanciate a DAO inside an ImageListParser
        val dao = ObjectBoxDAO()
        try {
            val lastPage = galleryPage.select(".pagination .last").firstOrNull()?.let {
                val href = it.attr("href")
                val index = href.lastIndexOf('=')
                if (index > -1) href.substring(index + 1).toInt()
                else 1
            } ?: 1

            val headers = fetchHeaders(content)
            val libraryQueueStatus = getLibraryStatuses() + getQueueStatuses()
            // Read all fav pages
            for (i in 1..lastPage) {
                if (processHalted.get()) break
                getOnlineDocument(
                    "$FAV_URL?page=$i",
                    headers,
                    Site.NHENTAI.useHentoidAgent,
                    Site.NHENTAI.useWebviewAgent
                )?.let { favDoc ->
                    val books = favDoc.select(".gallery-favorite")
                        .map {
                            Pair(
                                it.attr("data-id"),
                                it.select(".caption").first()?.text() ?: NO_TITLE
                            )
                        }
                        .filter { it.first.isNotEmpty() }

                    books.forEachIndexed { idx, b ->
                        progressPlus(idx / books.size.toFloat())
                        if (processHalted.get()) return@forEachIndexed
                        val gUrl = Content.getGalleryUrlFromId(Site.NHENTAI, b.first)
                        if (dao.selectContentsByUrl(
                                Site.NHENTAI,
                                transformRawUrl(Site.NHENTAI, gUrl)
                            ).count { libraryQueueStatus.contains(it.status.code) } == 0
                        ) {
                            val chp = Chapter(
                                order = (25 * (i - 1)) + idx + 1,
                                url = gUrl,
                                name = b.second,
                                uniqueId = b.first
                            )
                            chp.setContentId(content.id)
                            chapters.add(chp)
                        } else {
                            Timber.i("Duplicate found : $gUrl")
                        }
                    } // Books
                } // Document
            } // Pages
        } finally {
            dao.cleanup()
        }
        return chapters
    }

    private fun parseGallery(
        content: Content,
        url: String,
        chap: Chapter? = null,
        targetOrder: Int = 0,
        headers: List<Pair<String, String>>? = null
    ): List<ImageFile> {
        // Fetch the book gallery page
        Timber.d("Gallery URL: $url")
        val doc = getOnlineDocument(
            url,
            headers ?: fetchHeaders(content),
            Site.NHENTAI.useHentoidAgent,
            Site.NHENTAI.useWebviewAgent
        ) ?: throw ParseException("Document unreachable :$url")

        val thumbs = doc.select(THUMBS_SELECTOR).filterNotNull()
        val coverUrl = doc.select(COVER_SELECTOR).first()?.let {
            fixUrl(getImgSrc(it), Site.NHENTAI.url)
        } ?: ""
        val imgs = parseImages(coverUrl, thumbs)
        val result = urlsToImageFiles(
            imgs,
            targetOrder,
            StatusContent.SAVED,
            1000,
            chap
        )
        setDownloadParams(result, Site.NHENTAI.url)
        return result
    }
}