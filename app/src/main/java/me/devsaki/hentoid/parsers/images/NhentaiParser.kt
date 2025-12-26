package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.content.NO_TITLE
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.setDownloadParams
import me.devsaki.hentoid.parsers.urlsToImageFiles
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.network.fixUrl
import me.devsaki.hentoid.util.network.getOnlineDocument
import org.greenrobot.eventbus.EventBus
import org.jsoup.nodes.Element
import timber.log.Timber

class NhentaiParser : BaseImageListParser() {
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

    override fun parseImages(content: Content): List<String> {
        // We won't use that as parseImageList is overriden directly
        return emptyList()
    }

    @Throws(Exception::class)
    override fun parseImageListImpl(
        onlineContent: Content,
        storedContent: Content?
    ): List<ImageFile> {
        return if (onlineContent.url.contains("/favorites/"))
            parseFavs(onlineContent, storedContent)
        else parseGallery(onlineContent.galleryUrl)
    }

    private fun parseGallery(url: String, chap: Chapter? = null): List<ImageFile> {
        // Fetch the book gallery page
        Timber.d("Gallery URL: $url")
        val doc = getOnlineDocument(url)
            ?: throw ParseException("Document unreachable :$url")

        val thumbs = doc.select(THUMBS_SELECTOR).filterNotNull()
        val coverUrl = doc.select(COVER_SELECTOR).first()?.let {
            fixUrl(getImgSrc(it), Site.NHENTAI.url)
        } ?: ""
        val imgs = parseImages(coverUrl, thumbs)
        val result = urlsToImageFiles(
            imgs,
            StatusContent.SAVED,
            coverUrl,
            chap
        )
        setDownloadParams(result, Site.NHENTAI.url)
        return result
    }

    private fun parseFavs(
        onlineContent: Content,
        storedContent: Content?
    ): List<ImageFile> {
        val headers = fetchHeaders(onlineContent)
        Timber.d("Gallery URL: $FAV_URL")
        EventBus.getDefault().register(this)
        val result: MutableList<ImageFile> = ArrayList()
        try {
            val doc = getOnlineDocument(
                FAV_URL,
                headers,
                Site.NHENTAI.useHentoidAgent,
                Site.NHENTAI.useWebviewAgent
            )
                ?: throw ParseException("Document unreachable : $FAV_URL")

            val lastPage = doc.select(".pagination .last").firstOrNull()?.let {
                val href = it.attr("href")
                val index = href.lastIndexOf('=')
                if (index > -1) href.substring(index + 1).toInt()
                else 1
            } ?: 1

            // TODO implement "detect extra chapters" logic

            // Read all fav pages
            progressStart(onlineContent, storedContent, lastPage)
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
                        val chap = Chapter((25 * (i - 1)) + idx + 1, gUrl, b.second)
                        val imgs = parseGallery(gUrl, chap)
                        result.addAll(imgs)
                    }
                }
                progressNext()
            } // Pages
            progressComplete()

            // Renumber all pages
            result.forEachIndexed { i, p ->
                p.order = if (p.isCover && !p.isReadable) 0 else i + 1
                p.computeName(result.size)
            }
        } finally {
            EventBus.getDefault().unregister(this)
        }
        return result
    }
}