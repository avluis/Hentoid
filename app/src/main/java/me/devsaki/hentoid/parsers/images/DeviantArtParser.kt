package me.devsaki.hentoid.parsers.images

import androidx.core.util.Pair
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.ParseHelper
import me.devsaki.hentoid.util.network.HttpHelper
import org.jsoup.nodes.Element

class DeviantArtParser : BaseImageListParser() {
    companion object {
        // Thumb, hi-res (download) URL, display URL
        fun parseDeviation(body: Element): Triple<String, String, String> {
            val dlLink = body.selectFirst("a[download]")?.attr("href") ?: ""
            var imgLink = ""
            var thumbLink = ""
            body.selectFirst("img[fetchpriority=high]")?.let {
                imgLink = ParseHelper.getImgSrc(it)
                thumbLink = it.attr("srcSet").split(" ")[0]
            }
            return Triple(thumbLink, dlLink, imgLink)
        }
    }

    override fun parseImageListImpl(
        onlineContent: Content,
        storedContent: Content?
    ): List<ImageFile> {
        val result: MutableList<ImageFile> = ArrayList()
        processedUrl = onlineContent.galleryUrl

        // TODO user gallery
        val urls = HttpHelper.getOnlineDocument(
            processedUrl,
            fetchHeaders(onlineContent),
            Site.DEVIANTART.useHentoidAgent(),
            Site.DEVIANTART.useWebviewAgent()
        )?.let {
            parseDeviation(it.body())
        }

        urls?.let {
            // Thumb
            if (urls.first.isNotEmpty())
                result.add(ImageFile.newCover(urls.first, StatusContent.SAVED))
            // Image
            val img = ImageFile.fromImageUrl(1, urls.first, StatusContent.SAVED, 1)
            img.backupUrl = urls.second
            result.add(img)
        }

        return result
    }

    override fun parseImagePage(
        url: String,
        headers: List<Pair<String, String>>
    ): Pair<String, String?> {
        HttpHelper.getOnlineDocument(
            url,
            headers,
            Site.DEVIANTART.useHentoidAgent(),
            Site.DEVIANTART.useWebviewAgent()
        )?.let {
            val urls = parseDeviation(it.body())
            return Pair(urls.second, urls.third)
        }
        return Pair("", null)
    }


    override fun isChapterUrl(url: String): Boolean {
        return false // no chapters on this source
    }

    override fun parseImages(content: Content): List<String> {
        // Already overrides parseImageListImpl
        return emptyList()
    }

    override fun parseImages(
        chapterUrl: String,
        downloadParams: String?,
        headers: List<Pair<String, String>>?
    ): List<String> {
        // No chapters for this source
        return emptyList()
    }
}