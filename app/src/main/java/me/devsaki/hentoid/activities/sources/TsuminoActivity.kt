package me.devsaki.hentoid.activities.sources

import android.graphics.Bitmap
import android.webkit.WebView
import me.devsaki.hentoid.enums.Site

class TsuminoActivity : BaseWebActivity() {

    companion object {
        private const val DOMAIN_FILTER = "tsumino.com"
        private val GALLERY_FILTER = arrayOf("//www.tsumino.com/entry/")
        private val blockedContent = arrayOf("/static/")
        private val REMOVABLE_ELEMENTS = arrayOf(".ads-area", ".erogames_container")
        private var downloadFabPressed = false
        private var historyIndex = 0
    }

    override fun getStartSite(): Site {
        return Site.TSUMINO
    }

    override fun createWebClient(): CustomWebViewClient {
        val client: CustomWebViewClient = TsuminoWebViewClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.addRemovableElements(*REMOVABLE_ELEMENTS)
        client.adBlocker.addToUrlBlacklist(*blockedContent)
        return client
    }

    override fun onActionClick() {
        if (ActionMode.DOWNLOAD == actionButtonMode) {
            downloadFabPressed = true
            historyIndex = webView.copyBackForwardList().currentIndex

            // Hack to reach the first gallery page to initiate download, and go back to the book page
            val url = webView.url
            if (url != null) webView.loadUrl(url.replace("entry", "Read/Index"))
        } else {
            super.onActionClick()
        }
    }

    private inner class TsuminoWebViewClient(
        site: Site,
        filter: Array<String>,
        activity: CustomWebActivity
    ) : CustomWebViewClient(site, filter, activity) {
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)

            // Hack to reach the first gallery page to initiate download, and go back to the book page
            if (downloadFabPressed &&
                !(url.contains("//www.tsumino.com/Read/Index/") ||
                        url.contains("//www.tsumino.com/Read/Auth/") ||
                        url.contains("//www.tsumino.com/Read/AuthProcess"))
            ) {
                downloadFabPressed = false
            }
        }

        override fun onPageFinished(view: WebView?, url: String) {
            super.onPageFinished(view, url)

            // Hack to reach the first gallery page to initiate download, and go back to the book page
            if (downloadFabPressed && url.contains("//www.tsumino.com/Read/Index/")) {
                downloadFabPressed = false
                val currentIndex: Int = webView.copyBackForwardList().currentIndex
                webView.goBackOrForward(historyIndex - currentIndex)
                processDownload(null,false, isDownloadPlus = false, isReplaceDuplicate = false)
            }
        }
    }
}