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
import me.devsaki.hentoid.parsers.content.ContentParser
import me.devsaki.hentoid.parsers.content.EhentaiContent
import me.devsaki.hentoid.parsers.images.EHentaiParser
import me.devsaki.hentoid.parsers.images.EHentaiParser.EhAuthState
import me.devsaki.hentoid.util.Settings
import timber.log.Timber

class EHentaiActivity : BaseWebActivity() {

    companion object {
        private val DOMAIN_FILTER = arrayOf("e-hentai.org", "ehtracker.org")
        private val GALLERY_FILTER = arrayOf("e-hentai.org/g/[0-9]+/[\\w\\-]+")
    }

    override fun getStartSite(): Site {
        return Site.EHENTAI
    }

    override fun createWebClient(): CustomWebViewClient {
        val client: CustomWebViewClient = EHentaiWebClient(getStartSite(), GALLERY_FILTER, this)
        CookieManager.getInstance().setCookie(
            Site.EHENTAI.url,
            "sl=dm_2"
        ) // Show thumbs in results page ("extended display")
        CookieManager.getInstance().setCookie(
            Site.EHENTAI.url,
            "nw=1"
        ) // nw=1 (always) avoids the Offensive Content popup (equivalent to clicking the "Never warn me again" link)
        client.restrictTo(*DOMAIN_FILTER)
        // E-h serves images through hosts that use http connections, which is detected as "mixed content" by the app
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        return client
    }

    private inner class EHentaiWebClient(
        site: Site,
        filter: Array<String>,
        activity: CustomWebActivity
    ) : CustomWebViewClient(site, filter, activity) {
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            val authState = EHentaiParser.getAuthState(url)
            if (Settings.isDownloadEhHires && authState != EhAuthState.LOGGED && !url.startsWith(
                    "https://forums.e-hentai.org/index.php"
                )
            ) {
                view.loadUrl("https://forums.e-hentai.org/index.php?act=Login&CODE=00/")
                tooltip(R.string.help_web_hires_eh_account, true)
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
                val contentParser: ContentParser = EhentaiContent()

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