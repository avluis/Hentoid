package me.devsaki.hentoid.json;

import androidx.annotation.NonNull;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.database.domains.GroupItem;

class JsonGroupItem {

    private Integer groupingId;
    private String groupName;
    private Integer order;

    private JsonGroupItem() {
    }

    static JsonGroupItem fromEntity(GroupItem gi) {
        JsonGroupItem result = new JsonGroupItem();
        result.groupingId = gi.group.getTarget().grouping.getId();
        result.groupName = gi.group.getTarget().name;
        result.order = gi.order;
        return result;
    }

    GroupItem toEntity(@NonNull final Content content, @NonNull final Group group) {
        return new GroupItem(content, group, order);
    }

    public int getGroupingId() {
        return groupingId;
    }

    public String getGroupName() {
        return groupName;
    }

    public int getOrder() {
        return order;
    }
}
