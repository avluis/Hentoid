package me.devsaki.hentoid.parsers.content

import androidx.core.util.Pair
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.json.sources.SimplyContentMetadata
import me.devsaki.hentoid.parsers.ParseHelper
import me.devsaki.hentoid.util.JsonHelper
import me.devsaki.hentoid.util.network.getOnlineDocument
import timber.log.Timber
import java.io.IOException

/**
 * Parser for Simply Hentai content data served by its API
 */
class SimplyApiContent : BaseContentParser() {
    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        if (url.contains("api.simply-hentai.com") && !url.endsWith("/status")) { // Triggered by an API request
            val headers: MutableList<Pair<String, String>> = ArrayList()
            ParseHelper.addSavedCookiesToHeader(content.downloadParams, headers)
            try {
                val doc = getOnlineDocument(
                    url,
                    headers,
                    Site.SIMPLY.useHentoidAgent(),
                    Site.SIMPLY.useWebviewAgent()
                )
                if (doc != null) {
                    val metadata = JsonHelper.jsonToObject(
                        doc.body().ownText(),
                        SimplyContentMetadata::class.java
                    )
                    if (metadata != null) return metadata.update(content, updateImages)
                }
            } catch (e: IOException) {
                Timber.e(e, "Error parsing content from API.")
            }
        }
        return Content().setSite(Site.SIMPLY).setStatus(StatusContent.IGNORED)
    }
}