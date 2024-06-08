package me.devsaki.hentoid.views

import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.activities.sources.CustomWebViewClient
import me.devsaki.hentoid.core.Consumer
import me.devsaki.hentoid.util.Helper
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

class WysiwygBackgroundWebView(
    context: Context,
    private val client: CustomWebViewClient
) : WebView(context) {

    var isLoaded = false
    var result: Document? = null

    init {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptThirdPartyCookies(this, true)
        val webSettings = settings
        webSettings.userAgentString = client.site.userAgent
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false
        webSettings.domStorageEnabled = true
        webSettings.useWideViewPort = true
        webSettings.javaScriptEnabled = true
        webSettings.loadWithOverviewMode = true
        if (BuildConfig.DEBUG) setWebContentsDebuggingEnabled(true)
        webViewClient = client

        addJavascriptInterface(JsContentInterface { result = it }, interfaceName)
    }

    inner class JsContentInterface(private val handler: Consumer<Document>) {
        @JavascriptInterface
        @Suppress("unused")
        fun ha(url: String, html: String) {
            handler.invoke(Jsoup.parse("<html>$html</html>", url))
            isLoaded = true
        }
    }

    suspend fun loadUrlBlocking(url: String, killSwitch: AtomicBoolean): Document? {
        isLoaded = false
        withContext(Dispatchers.Main) {
            loadUrl(url)
        }
        var remainingIterations = 15 * 2 // Timeout 15s
        while (!isLoaded && remainingIterations-- > 0 && !killSwitch.get()) Helper.pause(500)
        Timber.v("%s with %d iterations remaining", isLoaded, remainingIterations)
        return result
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
                val randomChar = 65 + Helper.getRandomInt(26)
                sb.append(randomChar.toChar())
            }
            return sb.toString()
        }
    }
}