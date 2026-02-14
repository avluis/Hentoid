package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.json.sources.manhwa18.Manhwa18BookMetadata
import me.devsaki.hentoid.json.sources.manhwa18.Manhwa18ChapterMetadata
import me.devsaki.hentoid.parsers.addSavedCookiesToHeader
import me.devsaki.hentoid.parsers.images.Manhwa18Parser
import me.devsaki.hentoid.util.jsonToObject
import me.devsaki.hentoid.util.network.getOnlineDocument
import timber.log.Timber
import java.io.IOException

class Manhwa18Content : BaseContentParser() {
    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        if (url.contains("/manga/")) { // Triggered by an API request
            val parts = url.split("/")
            val isChapter = (parts[parts.size - 1].startsWith("chap"))

            val headers: MutableList<Pair<String, String>> = ArrayList()
            addSavedCookiesToHeader(content.downloadParams, headers)
            try {
                getOnlineDocument(
                    url,
                    headers,
                    Site.MANHWA18.useHentoidAgent,
                    Site.MANHWA18.useWebviewAgent
                )?.let { doc ->
                    val data = Manhwa18Parser.getDocData(doc)
                    if (isChapter) {
                        jsonToObject(
                            data,
                            Manhwa18ChapterMetadata::class.java
                        )?.let { metadata ->
                            return metadata.update(content, updateImages)
                        }
                    } else {
                        jsonToObject(
                            data,
                            Manhwa18BookMetadata::class.java
                        )?.let { metadata ->
                            return metadata.update(content, updateImages)
                        }
                    }
                }
            } catch (e: IOException) {
                Timber.e(e, "Error parsing content from API.")
            }
        }
        return Content(site = Site.MANHWA18, status = StatusContent.IGNORED)
    }
}