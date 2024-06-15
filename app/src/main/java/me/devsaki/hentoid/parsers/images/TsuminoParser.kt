package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.exception.CaptchaException
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.locateDigits
import me.devsaki.hentoid.util.network.getOnlineDocument

class TsuminoParser : BaseImageListParser() {

    override fun parseImages(content: Content): List<String> {
        val headers = fetchHeaders(content)

        // Fetch the reader page
        val doc = getOnlineDocument(
            content.readerUrl,
            headers,
            Site.TSUMINO.useHentoidAgent(),
            Site.TSUMINO.useWebviewAgent()
        )
        if (null != doc) {
            val captcha = doc.select(".g-recaptcha")
            if (!captcha.isEmpty()) throw CaptchaException()
            var nbPages = 0
            val nbPagesE = doc.select("h1").first()
            if (null != nbPagesE) {
                val digits = locateDigits(nbPagesE.text())
                if (digits.isNotEmpty()) nbPages = digits[digits.size - 1].third
            }
            if (-1 == nbPages) throw ParseException("Couldn't find the number of pages")
            val contents = doc.select("#image-container").first()
            if (null != contents) {
                val imgTemplate = contents.attr("data-cdn")
                return buildImageUrls(imgTemplate, nbPages)
            }
        }

        return emptyList()
    }

    private fun buildImageUrls(imgTemplate: String, nbPages: Int): List<String> {
        val imgUrls: MutableList<String> = ArrayList()
        for (i in 0 until nbPages) imgUrls.add(
            imgTemplate.replace("[PAGE]", (i + 1).toString())
        )
        return imgUrls
    }
}