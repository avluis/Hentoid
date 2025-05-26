package me.devsaki.hentoid.database.domains

import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.converter.PropertyConverter
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.Grouping.Companion.searchById
import me.devsaki.hentoid.util.hash64
import java.util.Objects

@Entity
data class Group(
    @Id
    var id: Long = 0,
    @Index
    @Convert(converter = GroupingConverter::class, dbType = Int::class)
    var grouping: Grouping = Grouping.NONE,
    var name: String = "",
    // in Grouping.ARTIST : 0 = Artist; 1 = Group
    // in Grouping.CUSTOM : 0 = Custom; 1 = Ungrouped
    var subtype: Int = 0,
    var order: Int = 0,
    var hasCustomBookOrder: Boolean = false,
    var propertyMin: Int = 0,
    var propertyMax: Int = 0,
    var searchUri: String = "",
    var favourite: Boolean = false,
    var rating: Int = 0,
    // Needs to be in the DB to keep the information when deletion takes a long time
    // and user navigates away; no need to save that into JSON
    var isBeingProcessed: Boolean = false,
    // Useful only during cleanup operations; no need to get it into the JSON
    var isFlaggedForDeletion: Boolean = false
) {
    @Backlink(to = "group")
    private lateinit var items: ToMany<GroupItem>

    // Targetting the content instead of the picture itself because
    // 1- That's the logic of the UI
    // 2- Pictures within a given Content are sometimes entirely replaced, breaking that link
    lateinit var coverContent: ToOne<Content>


    constructor(grouping: Grouping, name: String, order: Int) : this(
        id = 0, grouping = grouping, name = name, order = order
    )


    fun getItems(): List<GroupItem> {
        return items
    }

    fun setItems(items: List<GroupItem>?): Group {
        // We do want to compare array references, not content
        if (items != null && items !== this.items) {
            this.items.clear()
            this.items.addAll(items)
        }
        return this
    }

    val contentIds: List<Long>
        get() = items.toList().sortedBy { it.order }.map(GroupItem::contentId)

    val isUngroupedGroup: Boolean
        get() = Grouping.CUSTOM == grouping && 1 == subtype


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val group = other as Group
        return grouping == group.grouping && name == group.name && subtype == group.subtype
    }

    // Must be an int32, so we're bound to use Objects.hash
    override fun hashCode(): Int {
        return Objects.hash(grouping.name, name, subtype)
    }

    fun uniqueHash(): Long {
        return hash64((grouping.name + "." + name + "." + subtype).toByteArray())
    }

    class GroupingConverter : PropertyConverter<Grouping?, Int?> {
        override fun convertToEntityProperty(databaseValue: Int?): Grouping? {
            if (databaseValue == null) return null
            return searchById(databaseValue)
        }

        override fun convertToDatabaseValue(entityProperty: Grouping?): Int? {
            return entityProperty?.id
        }
    }
}