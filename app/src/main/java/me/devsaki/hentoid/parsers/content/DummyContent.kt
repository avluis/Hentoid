package me.devsaki.hentoid.parsers.content

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent

class DummyContent : BaseContentParser() {
    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        content.site = Site.NONE
        content.status = StatusContent.IGNORED
        return content
    }
}