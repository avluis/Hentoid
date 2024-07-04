package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.parsers.images.PIC_SELECTOR
import me.devsaki.hentoid.views.WysiwygBackgroundWebView
import java.util.regex.Pattern


private val DOMAIN_FILTER = arrayOf("mangago.me", "mangago.zone", "youhim.me")

const val MGG_GALLERY = "//www.mangago.me/read-manga/[%\\w\\-_]+/$"
val MGG_CHAPTER = MGG_GALLERY.replace("$", "") + "[%\\w\\-._]+/[%\\w\\-._]+(/pg-[%\\w\\-_]+)?/$"
val MGG_CHAPTER_PATTERN: Pattern = Pattern.compile(MGG_CHAPTER)

class MangagoActivity : BaseWebActivity() {

    override fun getStartSite(): Site {
        return Site.MANGAGO
    }

    override fun createWebClient(): MangagoWebClient {
        val client = MangagoWebClient(this)
        client.restrictTo(*DOMAIN_FILTER)
        client.adBlocker.addToJsUrlWhitelist(*DOMAIN_FILTER)
        return client
    }


    class MangagoWebClient : CustomWebViewClient {

        constructor() : super(Site.MANGAGO, arrayOf(MGG_GALLERY, MGG_CHAPTER)) {
            setJsStartupScripts("mangago_parser.js")
            addJsReplacement("\$interface", WysiwygBackgroundWebView.interfaceName)
            addJsReplacement("\$fun", WysiwygBackgroundWebView.functionName)
            addJsReplacement("\$selector", PIC_SELECTOR)
            addJsReplacement("\$force_page", "false")
        }

        internal constructor(
            activity: CustomWebActivity
        ) : super(Site.MANGAGO, arrayOf(MGG_GALLERY, MGG_CHAPTER), activity)
    }
}