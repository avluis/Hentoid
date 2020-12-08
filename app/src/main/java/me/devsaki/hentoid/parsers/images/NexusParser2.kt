package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.json.sources.NexusGallery
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.JsonHelper
import me.devsaki.hentoid.util.network.HttpHelper
import org.jsoup.nodes.Document
import java.util.*
import kotlin.experimental.xor

class NexusParser2 : BaseParser() {
    override fun parseImages(content: Content): MutableList<String> {
        val result: MutableList<String> = ArrayList()

        val doc = HttpHelper.getOnlineDocument(content.readerUrl)
        if (doc != null) result.addAll(parseDoc(doc))

        return result
    }

    private fun parseDoc(doc: Document): List<String> {
        return doc.select("script:containsData(initreader)").first().data()
                .substringAfter("initReader(\"")
                .substringBefore("\", ")
                .let(::decodePages)
    }

    private fun decodePages(code: String): List<String> {
        val bin: ByteArray = Helper.decode64(code)
        val result: MutableList<String> = ArrayList()
        result.add(String(bin))
        return result
    }
}