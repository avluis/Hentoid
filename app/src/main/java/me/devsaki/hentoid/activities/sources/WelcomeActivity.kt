package me.devsaki.hentoid.activities.sources

import me.devsaki.hentoid.enums.Site

class WelcomeActivity : BaseBrowserActivity() {

    override fun getStartSite(): Site {
        return Site.NONE
    }

    override fun createWebClient(): CustomWebViewClient {
        return CustomWebViewClient(getStartSite(), arrayOf(""))
    }
}