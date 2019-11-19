package me.devsaki.hentoid.collection.mikan;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;

public class MikanAttributeResponse {
    public String request;
    public final List<MikanAttribute> result = new ArrayList<>();

    public List<Attribute> toAttributeList() {
        List<Attribute> res = new ArrayList<>();

        for (MikanAttribute attr : result) {
            res.add(attr.toAttribute());
        }

        return res;
    }
}
