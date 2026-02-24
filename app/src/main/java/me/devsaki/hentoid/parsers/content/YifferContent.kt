package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.json.sources.yiffer.YifferData
import me.devsaki.hentoid.json.sources.yiffer.YifferLoaderData
import me.devsaki.hentoid.util.jsonToObject
import org.jsoup.nodes.Element
import pl.droidsonroids.jspoon.annotation.Selector
import timber.log.Timber

class YifferContent : BaseContentParser() {

    @Selector(value = "body script")
    private var scripts: List<Element>? = null


    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        var found = false

        scripts?.forEach { script ->
            if (found) return@forEach
            val str = script.toString()
            if (str.contains("\\\"loaderData\\\"", true)) {
                found = updateFromData(
                    str.replace("\\\\\\\"", "'").replace("\\\"", "\""),
                    content, url, updateImages, true
                )
            }
        }
        if (!found) return Content(status = StatusContent.IGNORED)

        return content
    }

    companion object {

        fun updateFromData(
            data: String,
            content: Content,
            url: String,
            updateImages: Boolean,
            fromHtml: Boolean
        ): Boolean {
            content.site = Site.YIFFER
            content.status = StatusContent.IGNORED
            if (url.isEmpty()) return false
            content.setRawUrl(url)

            val dataStr = deserialize(data)
            Timber.d(dataStr)
            if (fromHtml) {
                jsonToObject(dataStr, YifferLoaderData::class.java)?.update(content, updateImages)
            } else {
                jsonToObject(dataStr, YifferData::class.java)?.update(content, updateImages)
            }
            return (StatusContent.SAVED == content.status)
        }

        private fun deserialize(rsc: String): String {
            val elements = deserializeFirstPass(rsc)
            return populate(elements[0], elements)
        }

        private fun populate(resultIn: String, elements: List<String>, depth: Int = 0): String {
            val resultOut = StringBuilder()
            var isInsideNumber = false
            var number = 0
            if ((resultIn.startsWith('{') || resultIn.startsWith('[')) && !resultIn.startsWith("[\"D\"")) {
                resultIn.forEach { c ->
                    if (c.isDigit()) {
                        isInsideNumber = true
                        number = c.toString().toByte() + number * 10
                    } else {
                        if (isInsideNumber) {
                            if (depth < 8) {
                                resultOut.append(populate(elements[number], elements, depth + 1))
                            }
                            isInsideNumber = false
                            number = 0
                        }
                        resultOut.append(c)
                    }
                }
                return resultOut.toString()
                    .replace("\"\"", "\"")
                    .replace("\"_\"", "\"")
                    .replace("-\"", "\"") // Still don't know what those minuses are supposed to be
            } else {
                return if (resultIn.startsWith("[\"D\"")) {
                    resultIn.replace(" ", "").substringAfter(',').substringBeforeLast(']')
                } else resultIn
            }
        }

        private fun deserializeFirstPass(rsc: String): List<String> {
            val result: MutableList<String> = ArrayList()
            val cleanRsc =
                rsc.substringAfter('[').substringBeforeLast(']')

            val blockOpenings = arrayOf('[', '{', '"')
            val blockClosings = arrayOf(']', '}', '"')
            var insideBlock = -1
            val chunk = StringBuilder()
            cleanRsc.forEach { c ->
                if (blockOpenings.contains(c) && insideBlock < 0) {
                    insideBlock = blockOpenings.indexOf(c)
                    chunk.append(c)
                } else if (insideBlock > -1 && c == blockClosings[insideBlock]) {
                    insideBlock = -1
                    chunk.append(c)
                } else if (',' == c && insideBlock < 0) { // Only separate values when outside a block
                    result.add(chunk.toString())
                    chunk.clear()
                } else {
                    chunk.append(c)
                }
            }
            result.add(chunk.toString())
            return result
        }
    }
}