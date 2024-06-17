package me.devsaki.hentoid.database.domains

import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Transient
import io.objectbox.annotation.Uid
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne
import me.devsaki.hentoid.database.isReachable
import me.devsaki.hentoid.database.reach
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.AttributeType.AttributeTypeConverter
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.hash64
import timber.log.Timber
import java.util.Objects

@Entity
data class Attribute(
    @Id
    @Uid(5108740755096578000L)
    var dbId: Long = 0,
    @Index
    var name: String = "",
    @Index
    @Convert(converter = AttributeTypeConverter::class, dbType = Int::class)
    var type: AttributeType = AttributeType.UNDEFINED,
) {
    @Backlink(to = "attribute")
    lateinit var locations: ToMany<AttributeLocation> // One entry per site
    lateinit var group: ToOne<Group> // Associated group

    @Backlink(to = "attributes") // backed by the to-many relation in Content
    lateinit var contents: ToMany<Content>

    // Runtime attributes; no need to expose them nor to persist them
    @Transient
    var isExcluded = false

    @Transient
    var isNew = false

    @Transient
    var count = 0

    @Transient
    var externalId = 0

    @Transient
    private var m_displayName = ""

    @Transient
    var uniqueHash: Long = 0 // cached value of uniqueHash

    constructor(data: Attribute) : this(
        dbId = data.dbId,
        name = data.name,
        type = data.type
    ) {
        if (locations.isReachable(this)) {
            locations.clear()
            locations.addAll(data.locations) // this isn't a deep copy
        } else {
            this.locations = data.locations
        }
        if (data.group.isReachable(data)) {
            group.setTarget(data.group.target)
        } else {
            group.setTargetId(data.group.targetId)
        }
        this.isExcluded = data.isExcluded
        this.isNew = data.isNew
        this.count = data.count
        this.externalId = data.externalId
        if (contents.isReachable(this)) {
            contents.clear()
            contents.addAll(data.contents) // this isn't a deep copy
        } else {
            this.contents = data.contents
        }
        this.m_displayName = data.m_displayName
        this.uniqueHash = data.uniqueHash
    }

    constructor(site: Site) : this(
        type = AttributeType.SOURCE,
        name = site.description
    )

    constructor(type: AttributeType, name: String, url: String, site: Site) : this(
        type = type, name = name
    ) {
        computeLocation(site, url)
    }

    var id: Long
        get() = if (0 == externalId) this.dbId else externalId.toLong()
        set(value) {
            this.dbId = value
        }

    var displayName: String
        get() = m_displayName.ifEmpty { name }
        set(value) {
            this.m_displayName = value
        }

    fun getLinkedGroup(): Group? {
        return group.reach(this)
    }

    fun putGroup(group: Group) {
        this.group.setAndPutTarget(group)
    }

    private fun computeLocation(site: Site, url: String): Attribute {
        locations.add(AttributeLocation(site = site, url = url))
        return this
    }

    fun addLocationsFrom(sourceAttribute: Attribute) {
        for (sourceLocation in sourceAttribute.locations) {
            var foundSite = false
            for (loc in this.locations) {
                if (sourceLocation.site == loc.site) {
                    foundSite = true
                    if (sourceLocation.url != loc.url) Timber.w(
                        "'%s' Attribute location mismatch : current '%s' vs. add's target '%s'",
                        this.name, loc.url, sourceLocation.url
                    )
                    break
                }
            }
            if (!foundSite) locations.add(sourceLocation)
        }
    }

    override fun toString(): String {
        return type.name.lowercase() + ":" + name
    }

    // Hashcode (and by consequence equals) has to take into account fields that get visually updated on the app UI
    // If not done, FastAdapter's PagedItemListImpl cache won't detect changes to the object
    // and items won't be visually updated on screen
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val attribute = other as Attribute

        if ((externalId != 0 && attribute.externalId != 0) && externalId != attribute.externalId) return false
        if ((id != 0L && attribute.id != 0L) && id != attribute.id) return false
        if (name != attribute.name) return false
        return type == attribute.type
    }

    override fun hashCode(): Int {
        // Must be an int32, so we're bound to use Objects.hash
        var idComp = id
        if (externalId != 0) idComp = externalId.toLong()
        return Objects.hash(name, type, idComp)
    }

    fun uniqueHash(): Long {
        if (0L == uniqueHash) {
            var idComp = id
            if (externalId != 0) idComp = externalId.toLong()
            uniqueHash = hash64((idComp.toString() + "." + name + "." + type.code).toByteArray())
        }
        return uniqueHash
    }
}