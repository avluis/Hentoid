package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.json.sources.SimplyContentMetadata
import me.devsaki.hentoid.parsers.addSavedCookiesToHeader
import me.devsaki.hentoid.util.jsonToObject
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
            addSavedCookiesToHeader(content.downloadParams, headers)
            try {
                val doc = getOnlineDocument(
                    url,
                    headers,
                    Site.SIMPLY.useHentoidAgent,
                    Site.SIMPLY.useWebviewAgent
                )
                if (doc != null) {
                    val metadata =
                        jsonToObject(doc.body().ownText(), SimplyContentMetadata::class.java)
                    if (metadata != null) return metadata.update(content, updateImages)
                }
            } catch (e: IOException) {
                Timber.e(e, "Error parsing content from API.")
            }
        }
        return Content(site = Site.SIMPLY, status = StatusContent.IGNORED)
    }
}