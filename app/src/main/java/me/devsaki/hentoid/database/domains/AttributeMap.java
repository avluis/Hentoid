package me.devsaki.hentoid.database.domains;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import me.devsaki.hentoid.enums.AttributeType;

/**
 * Builds AttributeMaps
 */
public class AttributeMap extends HashMap<AttributeType, List<Attribute>> {

    public void add(Attribute attributeItem) {
        if (null == attributeItem) return;

        List<Attribute> list;
        AttributeType type = attributeItem.getType();

        if (containsKey(type)) {
            list = get(type);
        } else {
            list = new ArrayList<>();
            put(type, list);
        }
        if (list != null && !list.contains(attributeItem)) list.add(attributeItem);
    }

    public void addAll(List<Attribute> attributeList) {
        if (null == attributeList) return;

        for (Attribute item : attributeList) {
            add(item);
        }
    }
}
