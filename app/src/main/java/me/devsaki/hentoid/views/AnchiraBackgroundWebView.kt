package me.devsaki.hentoid.views

import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.annimon.stream.function.Consumer
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.activities.sources.AnchiraActivity.AnchiraWebClient
import me.devsaki.hentoid.activities.sources.WebResultConsumer
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.ContentParserFactory
import pl.droidsonroids.jspoon.Jspoon

class AnchiraBackgroundWebView(context: Context, consumer: WebResultConsumer, site: Site) :
    WebView(context) {
    private val client: AnchiraWebClient

    init {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptThirdPartyCookies(this, true)
        val webSettings = settings
        webSettings.userAgentString = site.userAgent
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false
        webSettings.domStorageEnabled = true
        webSettings.useWideViewPort = true
        webSettings.javaScriptEnabled = true
        webSettings.loadWithOverviewMode = true
        if (BuildConfig.DEBUG) setWebContentsDebuggingEnabled(true)
        client = AnchiraWebClient(site, emptyArray(), consumer)
        webViewClient = client

        addJavascriptInterface(AnchiraJsContentInterface { c ->
            client.jsHandler(c, false)
        }, "wysiwygInterface")
    }

    class AnchiraJsContentInterface(private val handler: Consumer<Content>) {
        @JavascriptInterface
        @Suppress("unused")
        fun transmit(url: String, html: String) {
            val c = ContentParserFactory.getInstance().getContentParserClass(Site.ANCHIRA)
            val jspoon = Jspoon.create()
            val adapter = jspoon.adapter(c) // Unchecked but alright
            val data = adapter.fromHtml("<html>$html</html>").toContent(url)
            handler.accept(data)
        }
    }

    fun clear() {
        client.destroy()
        removeAllViews()
        destroy()
    }

    companion object {

    }
}