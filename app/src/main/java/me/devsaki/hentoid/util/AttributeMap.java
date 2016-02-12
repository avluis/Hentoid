package me.devsaki.hentoid.util;

import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.enums.AttributeType;

/**
 * Created by Shiro on 1/11/2016.
 */
public class AttributeMap extends SparseArray<List<Attribute>> {

    public void add(Attribute attributeItem) {

        int key = attributeItem.getType().getCode();

        List<Attribute> list = get(key);
        if (list == null) {
            list = new ArrayList<>();
            put(key, list);
        }
        list.add(attributeItem);
    }

    public void add(List<Attribute> attributeList) {
        for (Attribute item : attributeList) {
            add(item);
        }
    }

    public List<Attribute> get(AttributeType type) {
        return get(type.getCode());
    }
}
