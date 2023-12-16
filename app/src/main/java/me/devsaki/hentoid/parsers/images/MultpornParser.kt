package me.devsaki.hentoid.parsers.images

import androidx.core.util.Pair
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.network.HttpHelper
import org.jsoup.nodes.Element
import java.io.IOException
import java.util.Locale

class MultpornParser : BaseImageListParser() {
    companion object {
        fun getJuiceboxRequestUrl(scripts: List<Element>): String {
            for (e in scripts) {
                val scriptText = e.toString().lowercase(Locale.getDefault()).replace("\\/", "/")
                val juiceIndex = scriptText.indexOf("/juicebox/xml/")
                if (juiceIndex > -1) {
                    val juiceEndIndex = scriptText.indexOf("\"", juiceIndex)
                    return HttpHelper.fixUrl(
                        scriptText.substring(juiceIndex, juiceEndIndex),
                        Site.MULTPORN.url
                    )
                }
            }
            return ""
        }

        @Throws(IOException::class)
        fun getImagesUrls(juiceboxUrl: String, galleryUrl: String): List<String> {
            val result: MutableList<String> = java.util.ArrayList()
            val headers: MutableList<Pair<String, String>> = java.util.ArrayList()
            HttpHelper.addCurrentCookiesToHeader(juiceboxUrl, headers)
            headers.add(Pair(HttpHelper.HEADER_REFERER_KEY, galleryUrl))
            val doc = HttpHelper.getOnlineDocument(
                juiceboxUrl,
                headers,
                Site.MULTPORN.useHentoidAgent(),
                Site.MULTPORN.useWebviewAgent()
            )
            if (doc != null) {
                var images: List<Element> = doc.select("juicebox image")
                if (images.isEmpty()) images = doc.select("juicebox img")
                for (img in images) {
                    val link = img.attr("linkURL")
                    if (!result.contains(link)) result.add(link) // Make sure we're not adding duplicates
                }
            }
            return result
        }
    }

    override fun isChapterUrl(url: String): Boolean {
        return false
    }

    override fun parseImages(content: Content): List<String> {
        val result: MutableList<String> = ArrayList()
        processedUrl = content.galleryUrl

        val headers = fetchHeaders(content)

        val doc = HttpHelper.getOnlineDocument(
            content.galleryUrl,
            headers,
            Site.ALLPORNCOMIC.useHentoidAgent(),
            Site.ALLPORNCOMIC.useWebviewAgent()
        )
        if (doc != null) {
            val juiceboxUrl = getJuiceboxRequestUrl(doc.select("head script"))
            result.addAll(getImagesUrls(juiceboxUrl, processedUrl))
        }

        return result
    }

    override fun parseImages(
        chapterUrl: String,
        downloadParams: String?,
        headers: List<Pair<String, String>>?
    ): List<String> {
        // Nothing; no chapters for this source
        return emptyList()
    }
}