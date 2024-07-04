package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.locateDigits
import me.devsaki.hentoid.util.network.getOnlineDocument

class ASMHentaiParser : BaseImageListParser() {
    override fun parseImages(content: Content): List<String> {
        val result: MutableList<String> = ArrayList()

        // Fetch the reader page
        val doc = getOnlineDocument(content.readerUrl)
        if (doc != null) {
            var nbPages = -1
            val nbPagesE = doc.select(".pages_btn").first()
            if (nbPagesE != null) {
                val digits = locateDigits(nbPagesE.text())
                if (digits.isNotEmpty()) nbPages = digits[digits.size - 1].third
            }
            if (-1 == nbPages) throw ParseException("Couldn't find the number of pages")
            var imgContainer = doc.select("div.reader_overlay") // New ASM layout
            if (imgContainer.isEmpty()) imgContainer =
                doc.select("div.full_image") // Old ASM layout
            if (imgContainer.isEmpty()) imgContainer =
                doc.select("div.full_gallery") // Older ASM layout
            val imgElt = imgContainer.select("a").select("img").first()
            if (imgElt != null) {
                var imgUrl = getImgSrc(imgElt)
                if (!imgUrl.startsWith("http")) imgUrl = "https:$imgUrl"
                val ext = imgUrl.substring(imgUrl.lastIndexOf('.'))
                for (i in 0 until nbPages) {
                    val img = imgUrl.substring(0, imgUrl.lastIndexOf('/') + 1) + (i + 1) + ext
                    result.add(img)
                }
            }
        }

        return result
    }
}