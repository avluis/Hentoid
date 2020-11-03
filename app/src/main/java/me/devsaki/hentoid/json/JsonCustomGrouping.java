package me.devsaki.hentoid.json;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.enums.Grouping;

class JsonCustomGrouping {

    private Integer groupingId;
    private final List<JsonCustomGroup> groups = new ArrayList<>();

    private JsonCustomGrouping() {
    }

    static JsonCustomGrouping fromEntity(@NonNull final Grouping grouping, @NonNull final List<Group> groups) {
        JsonCustomGrouping result = new JsonCustomGrouping();
        result.groupingId = grouping.getId();
        for (Group g : groups)
            result.groups.add(JsonCustomGroup.fromEntity(g));
        return result;
    }

    public Integer getGroupingId() {
        return groupingId;
    }

    public List<JsonCustomGroup> getGroups() {
        return groups;
    }
}
