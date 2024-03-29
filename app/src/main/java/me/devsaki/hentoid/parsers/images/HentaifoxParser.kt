package me.devsaki.hentoid.parsers.images

import androidx.core.util.Pair
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.parsers.ParseHelper
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.JsonHelper
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.network.HttpHelper
import org.jsoup.nodes.Element
import timber.log.Timber
import java.io.IOException

class HentaifoxParser : BaseImageListParser() {

    companion object {
        // Hentaifox have two image servers; each hosts the exact same files
        private val HOSTS = arrayOf("i.hentaifox.com", "i2.hentaifox.com")

        fun parseImages(
            content: Content,
            thumbs: List<Element>,
            scripts: List<Element>
        ): List<String> {
            content.populateUniqueSiteId()
            val result: MutableList<String> = ArrayList()

            // Parse the image format list to get the correct extensions
            // (thumbs extensions are _always_ jpg whereas images might be png; verified on one book)
            var imageFormats: Map<String, String>? = null
            for (s in scripts) {
                try {
                    val jsonBeginIndex = s.data().indexOf("'{\"1\"")
                    if (jsonBeginIndex > -1) {
                        imageFormats = JsonHelper.jsonToObject(
                            s.data().substring(jsonBeginIndex + 1).replace("\"}');", "\"}")
                                .replace("\n", ""), JsonHelper.MAP_STRINGS
                        )
                        break
                    }
                } catch (e: IOException) {
                    Timber.w(e)
                }
            }
            if (thumbs.isNotEmpty() && imageFormats != null) {
                val thumbUrl = ParseHelper.getImgSrc(thumbs[0])
                val thumbPath = thumbUrl.substring(
                    thumbUrl.indexOf("hentaifox.com") + 14,
                    thumbUrl.lastIndexOf("/") + 1
                )

                // Forge all page URLs
                for (i in 0 until imageFormats.size) {
                    val imgUrl =
                        "https://" + HOSTS[Helper.getRandomInt(HOSTS.size)] + "/" +
                                thumbPath +
                                (i + 1) + "." + ParseHelper.getExtensionFromFormat(imageFormats, i)
                    result.add(imgUrl)
                }
            }
            return result
        }
    }

    override fun isChapterUrl(url: String): Boolean {
        return false
    }

    override fun parseImages(content: Content): List<String> {
        // Fetch the book gallery page
        val doc = HttpHelper.getOnlineDocument(content.galleryUrl)
            ?: throw ParseException("Document unreachable : " + content.galleryUrl)

        val thumbs: List<Element> = doc.select(".g_thumb img")
        val scripts: List<Element> = doc.select("body script")

        return parseImages(content, thumbs, scripts)
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