package me.devsaki.hentoid.database.domains

import me.devsaki.hentoid.enums.AttributeType

class AttributeMap : HashMap<AttributeType, MutableSet<Attribute>>() {
    fun add(attributeItem: Attribute?) {
        if (null == attributeItem) return

        val attrs: MutableSet<Attribute>?
        val type = attributeItem.type

        if (containsKey(type)) {
            attrs = get(type)
        } else {
            attrs = HashSet()
            put(type, attrs)
        }
        attrs?.add(attributeItem)
    }

    fun addAll(attrs: Collection<Attribute>?) {
        if (null == attrs) return
        for (item in attrs) add(item)
    }
}