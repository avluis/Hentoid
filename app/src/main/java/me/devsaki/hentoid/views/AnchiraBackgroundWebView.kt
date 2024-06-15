package me.devsaki.hentoid.views

import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.activities.sources.AnchiraActivity.AnchiraWebClient
import me.devsaki.hentoid.activities.sources.WebResultConsumer
import me.devsaki.hentoid.core.Consumer
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.ContentParserFactory
import me.devsaki.hentoid.util.getRandomInt
import pl.droidsonroids.jspoon.Jspoon

class AnchiraBackgroundWebView(
    context: Context,
    consumer: WebResultConsumer,
    site: Site,
    jsMode: Int
) :
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
        client = AnchiraWebClient(site, emptyArray(), consumer, jsMode)
        webViewClient = client

        if (0 == jsMode) {
            addJavascriptInterface(AnchiraJsContentInterface { c ->
                client.jsHandler(c, false)
            }, interfaceName)
        }
    }

    class AnchiraJsContentInterface(private val handler: Consumer<Content>) {

        @JavascriptInterface
        @Suppress("unused")
        fun ha(url: String, html: String) {
            val c = ContentParserFactory.getContentParserClass(Site.ANCHIRA)
            val jspoon = Jspoon.create()
            val adapter = jspoon.adapter(c) // Unchecked but alright
            val data = adapter.fromHtml("<html>$html</html>").toContent(url)
            handler.invoke(data)
        }
    }

    fun clear() {
        client.destroy()
        removeAllViews()
        destroy()
    }

    companion object {
        val interfaceName = generateName()
        const val functionName = "ha"

        private fun generateName(): String {
            val sb = StringBuilder()
            for (i in 1..10) {
                val randomChar = 65 + getRandomInt(26)
                sb.append(randomChar.toChar())
            }
            return sb.toString()
        }
    }
}