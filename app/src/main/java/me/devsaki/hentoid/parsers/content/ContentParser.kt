package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.Content

interface ContentParser {
    var canonicalUrl: String
    fun toContent(url: String): Content

    /**
     * Update the given content by parsing the given Url
     *
     * @param content       Content to update
     * @param url           Url to use for parsing
     * @param updateImages  True to update images while parsing; false to keep them as is (i.e. metadata only)
     * @return Updated Content
     */
    fun update(content: Content, url: String, updateImages: Boolean): Content
}