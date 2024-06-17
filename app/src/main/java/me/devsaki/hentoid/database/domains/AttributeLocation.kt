package me.devsaki.hentoid.database.domains

import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToOne
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.Site.SiteConverter

@Entity
data class AttributeLocation(
    @Id
    var id: Long = 0,
    @Convert(converter = SiteConverter::class, dbType = Long::class)
    var site: Site = Site.NONE,
    var url: String = ""
) {
    lateinit var attribute: ToOne<Attribute>
}