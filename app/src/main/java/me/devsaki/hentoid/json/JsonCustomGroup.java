package me.devsaki.hentoid.json;

import androidx.annotation.NonNull;

import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.enums.Grouping;

class JsonCustomGroup {

    private String name;
    private Integer order;
    private Integer subtype;
    private Boolean favourite;
    private Boolean hasCustomBookOrder;

    private JsonCustomGroup() {
    }

    static JsonCustomGroup fromEntity(Group g) {
        JsonCustomGroup result = new JsonCustomGroup();
        result.name = g.name;
        result.order = g.order;
        result.subtype = g.subtype;
        result.favourite = g.favourite;
        result.hasCustomBookOrder = g.hasCustomBookOrder;
        return result;
    }

    Group toEntity(@NonNull final Grouping grouping) {
        return new Group(grouping, name, order).setSubtype(subtype).setFavourite(favourite).setHasCustomBookOrder(hasCustomBookOrder);
    }
}
