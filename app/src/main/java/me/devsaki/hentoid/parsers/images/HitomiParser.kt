package me.devsaki.hentoid.parsers.images

import android.os.Handler
import android.os.Looper
import android.webkit.URLUtil
import android.webkit.WebView
import androidx.core.util.Pair
import me.devsaki.hentoid.core.HentoidApp.Companion.getInstance
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.json.sources.HitomiGalleryInfo
import me.devsaki.hentoid.parsers.ParseHelper
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.JsonHelper
import me.devsaki.hentoid.util.StringHelper
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.network.HttpHelper
import me.devsaki.hentoid.views.HitomiBackgroundWebView
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class HitomiParser : BaseImageListParser() {

    private var hitomiWv: HitomiBackgroundWebView? = null

    @Throws(Exception::class)
    override fun parseImageListImpl(
        onlineContent: Content,
        storedContent: Content?
    ): List<ImageFile> {
        val readerUrl = onlineContent.readerUrl
        processedUrl = onlineContent.galleryUrl
        require(URLUtil.isValidUrl(readerUrl)) { "Invalid gallery URL : $readerUrl" }
        Timber.d("Gallery URL: %s", readerUrl)
        EventBus.getDefault().register(this)
        var result: List<ImageFile>
        try {
            result = parseImageListWithWebview(onlineContent, null)
            ParseHelper.setDownloadParams(result, onlineContent.site.url)
        } catch (e: Exception) {
            Helper.logException(e)
            result = ArrayList()
        } finally {
            EventBus.getDefault().unregister(this)
            clear()
        }
        return result
    }

    @Throws(Exception::class)
    fun parseImageListWithWebview(onlineContent: Content, webview: WebView?): List<ImageFile> {
        val pageUrl = onlineContent.readerUrl

        // Add referer information to downloadParams for future image download
        val downloadParams: MutableMap<String, String> = HashMap()
        downloadParams[HttpHelper.HEADER_REFERER_KEY] = pageUrl
        val downloadParamsStr =
            JsonHelper.serializeToJson<Map<String, String>>(downloadParams, JsonHelper.MAP_STRINGS)
        val galleryJsonUrl = "https://ltn.hitomi.la/galleries/" + onlineContent.uniqueSiteId + ".js"

        // Get the gallery JSON
        val headers: MutableList<Pair<String, String>> = ArrayList()
        headers.add(Pair(HttpHelper.HEADER_REFERER_KEY, pageUrl))
        val response = HttpHelper.getOnlineResourceFast(
            galleryJsonUrl,
            headers,
            Site.HITOMI.useMobileAgent(),
            Site.HITOMI.useHentoidAgent(),
            Site.HITOMI.useWebviewAgent()
        )
        val body = response.body ?: throw IOException("Empty body")
        val galleryInfo = body.string()
        updateContentInfo(onlineContent, galleryInfo)
        onlineContent.isUpdatedProperties = true

        // Get pages URL
        val done = AtomicBoolean(false)
        val imagesStr = AtomicReference<String>()
        val handler = Handler(Looper.getMainLooper())
        if (null == webview) {
            handler.post {
                hitomiWv = HitomiBackgroundWebView(getInstance(), Site.HITOMI)
                Timber.d(">> loading url %s", pageUrl)

                hitomiWv?.apply {
                    loadUrl(pageUrl) {
                        evaluateJs(this, galleryInfo, imagesStr, done)
                    }
                }
                Timber.i(">> loading wv")
            }
        } else { // We suppose the caller is the main thread if the webview is provided
            handler.post { evaluateJs(webview, galleryInfo, imagesStr, done) }
        }
        var remainingIterations = 15 // Timeout
        do {
            Helper.pause(1000)
        } while (!done.get() && !processHalted.get() && remainingIterations-- > 0)
        if (processHalted.get()) throw EmptyResultException("Unable to detect pages (empty result)")
        var jsResult = imagesStr.get()
        if (null == jsResult || jsResult.isEmpty()) throw EmptyResultException("Unable to detect pages (empty result)")
        val result: MutableList<ImageFile> = ArrayList()
        jsResult = jsResult.replace("\"[", "[").replace("]\"", "]").replace("\\\"", "\"")
        val imageUrls = JsonHelper.jsonToObject<List<String>>(jsResult, JsonHelper.LIST_STRINGS)
        if (!imageUrls.isNullOrEmpty()) {
            onlineContent.setCoverImageUrl(imageUrls[0])
            result.add(ImageFile.newCover(imageUrls[0], StatusContent.SAVED))
            var order = 1
            for (s in imageUrls) {
                val img =
                    ParseHelper.urlToImageFile(s, order++, imageUrls.size, StatusContent.SAVED)
                img.setDownloadParams(downloadParamsStr)
                result.add(img)
            }
        }
        return result
    }

    // TODO doc
    private fun evaluateJs(
        webview: WebView,
        galleryInfo: String,
        imagesStr: AtomicReference<String>,
        done: AtomicBoolean
    ) {
        Timber.d(">> evaluating JS")
        webview.evaluateJavascript(getJsPagesScript(galleryInfo)) { s: String? ->
            Timber.d(">> JS evaluated")
            imagesStr.set(StringHelper.protect(s))
            done.set(true)
        }
    }

    // TODO optimize
    private fun getJsPagesScript(galleryInfo: String): String {
        val sb = StringBuilder()
        FileHelper.getAssetAsString(getInstance().assets, "hitomi_pages.js", sb)
        return sb.toString().replace("\$galleryInfo", galleryInfo)
    }

    // TODO doc
    @Throws(Exception::class)
    private fun updateContentInfo(content: Content, galleryInfoStr: String) {
        val firstBrace = galleryInfoStr.indexOf("{")
        val lastBrace = galleryInfoStr.lastIndexOf("}")
        if (firstBrace > -1 && lastBrace > -1) {
            val galleryJson = galleryInfoStr.substring(firstBrace, lastBrace + 1)
            val galleryInfo = JsonHelper.jsonToObject(
                galleryJson,
                HitomiGalleryInfo::class.java
            )
            galleryInfo.updateContent(content)
        } else throw EmptyResultException("Couldn't find gallery information")
    }


    override fun isChapterUrl(url: String): Boolean {
        return false
    }

    override fun parseImages(content: Content): List<String> {
        // We won't use that as parseImageListImpl is overriden directly
        return emptyList()
    }

    override fun parseImages(
        chapterUrl: String,
        downloadParams: String?,
        headers: List<Pair<String, String>>?
    ): List<String> {
        // Nothing; no chapters for this source
        return emptyList()
    }

    override fun clear() {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            hitomiWv?.destroy()
            hitomiWv = null
        }
    }
}