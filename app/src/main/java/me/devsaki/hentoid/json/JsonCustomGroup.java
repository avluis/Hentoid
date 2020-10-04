package me.devsaki.hentoid.json;

import androidx.annotation.NonNull;

import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.enums.Grouping;

class JsonCustomGroup {

    private String name;
    private Integer order;

    private JsonCustomGroup() {
    }

    static JsonCustomGroup fromEntity(Group g) {
        JsonCustomGroup result = new JsonCustomGroup();
        result.name = g.name;
        result.order = g.order;
        return result;
    }

    Group toEntity(@NonNull final Grouping grouping) {
        return new Group(grouping, name, order);
    }
}
