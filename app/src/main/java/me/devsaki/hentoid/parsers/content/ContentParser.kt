package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.Content

interface ContentParser {
    var canonicalUrl: String
    fun toContent(url: String): Content
    fun update(content: Content, url: String, updateImages: Boolean): Content
}