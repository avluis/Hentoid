package me.devsaki.hentoid;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.enums.AttributeType;

/**
 * Created by Shiro on 1/11/2016.
 */
public class CustomMultiMap extends HashMap<AttributeType, List<Attribute>> {

    public void add(AttributeType type, Attribute x) {

        List<Attribute> list;

        if(containsKey(type)) {
            list = get(type);
        } else {
            list = new ArrayList<>();
            put(type, list);
        }

        list.add(x);
    }
}
