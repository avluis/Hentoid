package me.devsaki.hentoid.activities.sources

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.images.HitomiParser
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.network.parseParameters
import org.jsoup.nodes.Document
import timber.log.Timber
import java.util.Locale

class HitomiActivity : BaseWebActivity() {
    companion object {
        private const val DOMAIN_FILTER = "hitomi.la"
        private val GALLERY_FILTER =
            arrayOf("//hitomi.la/[manga|doujinshi|gamecg|cg|imageset]+/[^/]+-[0-9]{2,}.html(#[0-9]{1,2}){0,1}$")
        private val RESULTS_FILTER = arrayOf(
            "//hitomi.la[/]{0,1}$",
            "//hitomi.la[/]{0,1}\\?",
            "//hitomi.la/search.html",
            "//hitomi.la/index-[\\w%\\-\\.\\?]+",
            "//hitomi.la/(series|artist|tag|character)/[\\w%\\-\\.\\?]+"
        )
        private val BLOCKED_CONTENT =
            arrayOf("hitomi-horizontal.js", "hitomi-vertical.js", "invoke.js", "ion.sound")
        private val JS_URL_PATTERN_WHITELIST =
            arrayOf(
                "//hitomi.la[/]{0,1}$",
                "galleries/[\\w%\\-]+.js$",
                "//hitomi.la/[?]page=[0-9]+"
            )
        private val JS_URL_WHITELIST = arrayOf(
            "nozomiurlindex",
            "languagesindex",
            "tagindex",
            "filesaver",
            "common",
            "date",
            "download",
            "gallery",
            "jquery",
            "cookie",
            "jszip",
            "limitlists",
            "moment-with-locales",
            "moveimage",
            "pagination",
            "search",
            "searchlib",
            "yall",
            "reader",
            "decode_webp",
            "bootstrap",
            "gg.js",
            "paging",
            "language_support"
        )
        private val JS_CONTENT_BLACKLIST = arrayOf(
            "exoloader",
            "popunder",
            "da_etirw", // Specific to Hitomi
            "ad_trigger_class",
            "ad_popup_force",
            "exosrv.com",
            "realsrv.com",
            "ad-provider",
            "adprovider"
        )
        private val REMOVABLE_ELEMENTS = arrayOf(
            ".content div[class^=hitomi-]",
            ".container div[class^=hitomi-]",
            ".top-content > div:not(.list-title)",
            ".content > div:not(.gallery,.cover-column,.gallery-preview)",
            ".wnvtqvsW"
        )
    }

    override fun getStartSite(): Site {
        return Site.HITOMI
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        languageFilterButton?.setOnClickListener { onLangFilterButtonClick() }
    }

    override fun onPageStarted(
        url: String?,
        isGalleryPage: Boolean,
        isHtmlLoaded: Boolean,
        isBookmarkable: Boolean
    ) {
        languageFilterButton?.visibility = View.INVISIBLE
        super.onPageStarted(url, isGalleryPage, isHtmlLoaded, isBookmarkable)
    }

    override fun onPageFinished(isResultsPage: Boolean, isGalleryPage: Boolean) {
        if (Preferences.isLanguageFilterButton() && isUrlFilterable(webView.url ?: ""))
            languageFilterButton?.visibility = View.VISIBLE
        else languageFilterButton?.visibility = View.INVISIBLE
        super.onPageFinished(isResultsPage, isGalleryPage)
    }

    override fun createWebClient(): CustomWebViewClient {
        val client = HitomiWebClient(getStartSite(), GALLERY_FILTER, this)
        client.restrictTo(DOMAIN_FILTER)
        client.addRemovableElements(*REMOVABLE_ELEMENTS)
        client.addJavascriptBlacklist(*JS_CONTENT_BLACKLIST)
        client.setResultsUrlPatterns(*RESULTS_FILTER)
        client.setResultUrlRewriter { resultsUri: Uri, page: Int ->
            rewriteResultsUrl(resultsUri, page)
        }
        client.adBlocker.addToUrlBlacklist(*BLOCKED_CONTENT)
        client.adBlocker.addToJsUrlWhitelist(*JS_URL_WHITELIST)
        for (s in JS_URL_PATTERN_WHITELIST) client.adBlocker.addJsUrlPatternWhitelist(s)
        for (s in JS_CONTENT_BLACKLIST) client.adBlocker.addJsContentBlacklist(s)
        return client
    }

    private fun rewriteResultsUrl(resultsUri: Uri, page: Int): String {
        val builder = resultsUri.buildUpon()
        if (resultsUri.toString()
                .contains("search")
        ) builder.fragment(page.toString() + "") // https://hitomi.la/search.html?<searchTerm>#<page>
        else {
            val params = parseParameters(resultsUri).toMutableMap()
            params["page"] = page.toString() + ""
            builder.clearQuery()
            params.forEach { (key, value) ->
                builder.appendQueryParameter(key, value)
            }
        }
        return builder.toString()
    }

    private fun onLangFilterButtonClick() {
        val temp =
            Preferences.getLanguageFilterButtonValue() + ".html".lowercase(Locale.getDefault())
        if (webView.url!!.contains("-all.html")) webView.loadUrl(
            webView.url!!.replace("all.html", temp)
        ) else webView.loadUrl(webView.url + "index-" + temp)
    }

    private fun isUrlFilterable(url: String): Boolean {
        //only works on 1st page
        return url == "https://hitomi.la/" || url == "https://hitomi.la/?page=1"
                || url.endsWith("-all.html") || url.endsWith("-all.html?page=1")
    }

    private open inner class HitomiWebClient(
        site: Site,
        filter: Array<String>,
        activity: CustomWebActivity
    ) : CustomWebViewClient(site, filter, activity) {
        init {
            setCustomHtmlRewriter { doc: Document -> processDoc(doc) }
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val url = request.url.toString()
            if ((isMarkDownloaded() || isMarkMerged() || isMarkBlockedTags()) && url.contains("galleryblock")) { // Process book blocks to mark existing ones
                val result = parseResponse(
                    url, request.requestHeaders,
                    analyzeForDownload = false,
                    quickDownload = false
                )
                return result ?: sendRequest(request)
            }
            return super.shouldInterceptRequest(view, request)
        }

        override fun processContent(
            content: Content,
            url: String,
            quickDownload: Boolean
        ): Content {
            // Wait until the page's resources are all loaded
            if (!quickDownload) {
                Timber.v(">> Hitomi : not loading ! %s", url)
                while (!isLoading()) Helper.pause(20)
                Timber.v(">> Hitomi : loading %s", url)
                while (isLoading()) Helper.pause(100)
                Timber.v(">> Hitomi : done")
            }
            val parser = HitomiParser()
            try {
                // Only fetch them when queue is processed
                parser.parseImageListWithWebview(content, webView)
                content.status = StatusContent.SAVED
            } catch (e: Exception) {
                Helper.logException(e)
                Timber.i(e)
                content.status = StatusContent.IGNORED
            }
            return super.processContent(content, url, quickDownload)
        }

        protected fun processDoc(doc: Document) {
            for (e in doc.select("script")) {
                if (e.hasAttr("async") || e.attr("data-cfasync")
                        .equals("false", ignoreCase = true)
                ) {
                    Timber.d("Removing script %s", e.toString())
                    e.remove()
                }
            }
            for (e in doc.select("head style")) {
                if (e.data().trim { it <= ' ' }.isNotEmpty()) {
                    Timber.d("Removing style %s", e.toString())
                    e.remove()
                }
            }
        }
    }
}