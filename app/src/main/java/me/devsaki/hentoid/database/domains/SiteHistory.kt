package me.devsaki.hentoid.database.domains

import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.Site.SiteConverter

/**
 * Site browsing history
 */
@Entity
data class SiteHistory(
    @Id
    var id: Long = 0,
    @Convert(converter = SiteConverter::class, dbType = Long::class)
    val site: Site = Site.NONE,
    var url: String = ""
) {
    constructor() : this(Site.NONE, "")
    constructor(site: Site, url: String) : this(0, site, url)
}