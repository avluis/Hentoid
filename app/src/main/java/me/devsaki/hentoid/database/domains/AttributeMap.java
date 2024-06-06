package me.devsaki.hentoid.database.domains;

import java.util.Collection;
import org.apache.commons.collections4.map.HashedMap;
import java.util.HashSet;
import java.util.Set;

import me.devsaki.hentoid.enums.AttributeType;

/**
 * Builds AttributeMaps
 */
public class AttributeMap extends HashedMap<AttributeType, Set<Attribute>> {
    public void add(Attribute attributeItem) {
        if (null == attributeItem) return;

        Set<Attribute> attrs;
        AttributeType type = attributeItem.getType();

        if (containsKey(type)) {
            attrs = get(type);
        } else {
            attrs = new HashSet<>();
            put(type, attrs);
        }
        if (attrs != null) attrs.add(attributeItem);
    }

    public void addAll(Collection<Attribute> attrs) {
        if (null == attrs) return;
        for (Attribute item : attrs) add(item);
    }
}
