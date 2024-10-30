package me.devsaki.hentoid.activities.sources

import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.parsers.content.ContentParser
import me.devsaki.hentoid.parsers.content.ExhentaiContent
import me.devsaki.hentoid.parsers.images.EHentaiParser
import me.devsaki.hentoid.parsers.images.EHentaiParser.EhAuthState
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.file.findOrCreateDocumentFile
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.saveBinary
import timber.log.Timber
import java.io.IOException

class ExHentaiActivity : BaseWebActivity() {

    companion object {
        private val DOMAIN_FILTER = arrayOf("exhentai.org", "e-hentai.org", "ehtracker.org")
        private const val DOMAIN = ".exhentai.org"
        private val GALLERY_FILTER = arrayOf("exhentai.org/g/[0-9]+/[\\w\\-]+")
    }

    override fun getStartSite(): Site {
        return Site.EXHENTAI
    }

    override fun createWebClient(): CustomWebViewClient {
        val client: CustomWebViewClient = ExHentaiWebClient(getStartSite(), GALLERY_FILTER, this)
        // Show thumbs in results page ("extended display")
        CookieManager.getInstance().setCookie(DOMAIN, "sl=dm_2")
        // nw=1 (always) avoids the Offensive Content popup (equivalent to clicking the "Never warn me again" link)
        CookieManager.getInstance().setCookie(DOMAIN, "nw=1")
        client.restrictTo(*DOMAIN_FILTER)
        // ExH serves images through hosts that use http connections, which is detected as "mixed content" by the app
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        return client
    }

    private inner class ExHentaiWebClient constructor(
        site: Site?,
        filter: Array<String>?,
        activity: CustomWebActivity?
    ) :
        CustomWebViewClient(site!!, filter!!, activity!!) {
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            val authState = EHentaiParser.getAuthState(url)
            if (url.startsWith("https://exhentai.org") && authState != EhAuthState.LOGGED) {
                CookieManager.getInstance().removeAllCookies(null)
                webView.loadUrl("https://forums.e-hentai.org/index.php?act=Login&CODE=00/")
                if (authState == EhAuthState.UNLOGGED_ABNORMAL) tooltip(
                    R.string.help_web_incomplete_exh_credentials,
                    true
                ) else tooltip(R.string.help_web_invalid_exh_credentials, true)
            }
            if (url.startsWith("https://forums.e-hentai.org/index.php") && authState == EhAuthState.LOGGED) {
                webView.loadUrl("https://exhentai.org/")
            }
            tooltip(R.string.help_web_exh_account, false)
        }

        private fun logCookies(prefix: String, cookieStr: String) {
            try {
                val root = getDocumentFromTreeUriString(
                    application,
                    Settings.getStorageUri(StorageLocation.PRIMARY_1)
                )
                if (root != null) {
                    val cookiesLog = findOrCreateDocumentFile(
                        application,
                        root,
                        "text/plain",
                        "cookies_" + prefix + "_log.txt"
                    )
                    if (cookiesLog != null) saveBinary(
                        application,
                        cookiesLog.uri,
                        cookieStr.toByteArray()
                    )
                }
            } catch (e: IOException) {
                Timber.e(e)
            }
        }

        // We call the API without using BaseWebActivity.parseResponse
        override fun parseResponse(
            url: String,
            requestHeaders: Map<String, String>?,
            analyzeForDownload: Boolean,
            quickDownload: Boolean
        ): WebResourceResponse? {
            if (analyzeForDownload || quickDownload) {
                activity?.onGalleryPageStarted()
                val contentParser: ContentParser = ExhentaiContent()

                lifecycleScope.launch {
                    try {
                        var content = withContext(Dispatchers.IO) {
                            contentParser.toContent(url)
                        }
                        content = super.processContent(content, url, quickDownload)
                        resConsumer?.onContentReady(content, quickDownload)
                    } catch (t: Throwable) {
                        Timber.w(t)
                    }
                }
            }
            return if (isMarkDownloaded() || isMarkMerged() || isMarkBlockedTags() || isMarkQueued())
                super.parseResponse(
                    url,
                    requestHeaders,
                    analyzeForDownload = false,
                    quickDownload = false
                ) // Rewrite HTML
            else null
        }
    }
}