package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Content

class DummyParser : BaseImageListParser() {
    override fun parseImages(content: Content): List<String> {
        return content.imageList.map { img -> img.url }
    }
}