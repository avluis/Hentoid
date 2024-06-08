package me.devsaki.hentoid.activities.sources

import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceResponse
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.views.WysiwygBackgroundWebView
import java.util.regex.Pattern


private val MGG_IMG_PATTERN = Pattern.compile("/[0-9]+/[0-9]+_[0-9]+.[a-z]+$")

private const val DOMAIN_FILTER = "mangago.me"

val MGG_GALLERY = "//www.mangago.me/read-manga/[%\\w\\-_]+/$"
val MGG_CHAPTER = MGG_GALLERY.replace("$", "") + "[%\\w\\-_]+/[%\\w\\-_]+/pg-[%\\w\\-_]+/$"
val MGG_CHAPTER_PATTERN = Pattern.compile(MGG_CHAPTER)

class MangagoActivity : BaseWebActivity() {

    override fun getStartSite(): Site {
        return Site.MANGAGO
    }

    override fun createWebClient(): MangagoWebClient {
        val client = MangagoWebClient(this)
        client.restrictTo(DOMAIN_FILTER)
        client.adBlocker.addToJsUrlWhitelist(DOMAIN_FILTER)
        return client
    }


    class MangagoWebClient : CustomWebViewClient {

        constructor(
            resultConsumer: WebResultConsumer
        ) : super(Site.MANGAGO, arrayOf(MGG_GALLERY, MGG_CHAPTER), resultConsumer) {
            setJsStartupScripts("wysiwyg_parser.js")
            addJsReplacement("\$interface", WysiwygBackgroundWebView.interfaceName)
            addJsReplacement("\$fun", WysiwygBackgroundWebView.functionName)
            addJsReplacement("\$selector", "#pic_container img")
        }

        internal constructor(
            activity: CustomWebActivity
        ) : super(Site.MANGAGO, arrayOf(MGG_GALLERY, MGG_CHAPTER), activity) {
            setJsStartupScripts("wysiwyg_parser.js")
            addJsReplacement("\$interface", WysiwygBackgroundWebView.interfaceName)
            addJsReplacement("\$fun", WysiwygBackgroundWebView.functionName)
            addJsReplacement("\$selector", "#pic_container img")
        }

        override fun parseResponse(
            url: String,
            requestHeaders: Map<String, String>?,
            analyzeForDownload: Boolean,
            quickDownload: Boolean
        ): WebResourceResponse? {
            // Complete override of default behaviour because
            // - There's no HTML to be parsed for ads
            // - The interesting parts are loaded by JS, not now
            /*
            if (quickDownload && MGG_CHAPTER_PATTERN.matcher(url).find()) {
                // Use a background Wv to get book attributes when targeting another page (quick download)
                val parser = MangagoParser()
                try {
                    val content = parser.parseContentWithWebview(url)
                    content.status = StatusContent.SAVED
                    activity?.onGalleryPageStarted()
                    val contentFinal = super.processContent(content, url, quickDownload)
                    val handler = Handler(Looper.getMainLooper())
                    handler.post { resConsumer.onContentReady(contentFinal, true) }
                } catch (e: Exception) {
                    Timber.w(e)
                } finally {
                    parser.clear()
                }
                return null
            }

             */
            return super.parseResponse(url, requestHeaders, analyzeForDownload, quickDownload)
        }

        fun jsHandler(content: Content, quickDownload: Boolean) {
            val handler = Handler(Looper.getMainLooper())
            handler.post {
                processContent(content, content.galleryUrl, quickDownload)
                resConsumer.onContentReady(content, quickDownload)
            }
        }
    }
}