package me.devsaki.hentoid.parsers.images

import android.os.Handler
import android.os.Looper
import android.webkit.URLUtil
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.devsaki.hentoid.core.HentoidApp.Companion.getInstance
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.json.sources.HitomiGalleryInfo
import me.devsaki.hentoid.parsers.setDownloadParams
import me.devsaki.hentoid.parsers.urlToImageFile
import me.devsaki.hentoid.util.LIST_STRINGS
import me.devsaki.hentoid.util.MAP_STRINGS
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.file.getAssetAsString
import me.devsaki.hentoid.util.jsonToObject
import me.devsaki.hentoid.util.logException
import me.devsaki.hentoid.util.network.HEADER_REFERER_KEY
import me.devsaki.hentoid.util.network.getOnlineResourceFast
import me.devsaki.hentoid.util.pause
import me.devsaki.hentoid.util.serializeToJson
import me.devsaki.hentoid.views.HitomiBackgroundWebView
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class HitomiParser : BaseImageListParser() {

    private var webview: HitomiBackgroundWebView? = null


    override fun parseImages(content: Content): List<String> {
        // We won't use that as parseImageListImpl is overriden directly
        return emptyList()
    }

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
            setDownloadParams(result, onlineContent.site.url)
        } catch (e: Exception) {
            logException(e)
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
        downloadParams[HEADER_REFERER_KEY] = pageUrl
        val downloadParamsStr = serializeToJson<Map<String, String>>(downloadParams, MAP_STRINGS)
        val galleryJsonUrl = "https://ltn.hitomi.la/galleries/" + onlineContent.uniqueSiteId + ".js"

        // Get the gallery JSON
        val headers: MutableList<Pair<String, String>> = ArrayList()
        headers.add(Pair(HEADER_REFERER_KEY, pageUrl))
        val response = getOnlineResourceFast(
            galleryJsonUrl,
            headers,
            Site.HITOMI.useMobileAgent(),
            Site.HITOMI.useHentoidAgent(),
            Site.HITOMI.useWebviewAgent()
        )
        val body = response.body ?: throw IOException("Empty body")
        val galleryInfo = body.string()
        updateContentInfo(onlineContent, galleryInfo)

        // Get pages URL
        val done = AtomicBoolean(false)
        val imagesStr = AtomicReference<String>()
        val handler = Handler(Looper.getMainLooper())
        if (null == webview) {
            handler.post {
                this.webview = HitomiBackgroundWebView(getInstance(), Site.HITOMI)
                Timber.d(">> loading url %s", pageUrl)

                this.webview?.apply {
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
            pause(1000)
        } while (!done.get() && !processHalted.get() && remainingIterations-- > 0)
        if (processHalted.get()) throw EmptyResultException("Unable to detect pages (empty result)")
        var jsResult = imagesStr.get()
        if (null == jsResult || jsResult.isEmpty()) throw EmptyResultException("Unable to detect pages (empty result)")
        val result: MutableList<ImageFile> = ArrayList()
        jsResult = jsResult.replace("\"[", "[").replace("]\"", "]").replace("\\\"", "\"")
        val imageUrls = jsonToObject<List<String>>(jsResult, LIST_STRINGS)
        if (!imageUrls.isNullOrEmpty()) {
            onlineContent.setCoverImageUrl(imageUrls[0])
            result.add(ImageFile.newCover(imageUrls[0], StatusContent.SAVED))
            var order = 1
            for (s in imageUrls) {
                val img = urlToImageFile(s, order++, imageUrls.size, StatusContent.SAVED)
                img.downloadParams = downloadParamsStr
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
            imagesStr.set(s ?: "")
            done.set(true)
        }
    }

    // TODO optimize
    private fun getJsPagesScript(galleryInfo: String): String {
        val sb = StringBuilder()
        getAssetAsString(getInstance().assets, "hitomi_pages.js", sb)
        return sb.toString().replace("\$galleryInfo", galleryInfo)
    }

    // TODO doc
    @Throws(Exception::class)
    private fun updateContentInfo(content: Content, galleryInfoStr: String) {
        val firstBrace = galleryInfoStr.indexOf("{")
        val lastBrace = galleryInfoStr.lastIndexOf("}")
        if (firstBrace > -1 && lastBrace > -1) {
            val galleryJson = galleryInfoStr.substring(firstBrace, lastBrace + 1)
            val galleryInfo = jsonToObject(galleryJson, HitomiGalleryInfo::class.java)
            galleryInfo?.updateContent(content)
        } else throw EmptyResultException("Couldn't find gallery information")
    }

    override fun clear() {
        CoroutineScope(Dispatchers.Main).launch {
            webview?.clear()
            webview = null
        }
    }
}