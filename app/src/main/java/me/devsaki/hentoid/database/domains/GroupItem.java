package me.devsaki.hentoid.database.domains;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.relation.ToOne;
import me.devsaki.hentoid.database.DBHelper;

@Entity
public class GroupItem {

    @Id
    public long id;
    public ToOne<Content> content;
    public ToOne<Group> group;
    public int order;

    public GroupItem() { // Required by ObjectBox when an alternate constructor exists
    }

    public GroupItem(@NonNull final Content content, @NonNull final Group group, int order) {
        this.content.setTarget(content);
        this.group.setTarget(group);
        this.order = order;
    }

    public GroupItem(long contentId, @NonNull final Group group, int order) {
        this.content.setTargetId(contentId);
        this.group.setTarget(group);
        this.order = order;
    }

    @Nullable
    public Content getContent() {
        return content.isResolved() ? content.getTarget() : null;
    }

    public Group getGroup() {
        return group.getTarget();
    }

    public Group reachGroup() {
        return DBHelper.reach(this, group);
    }

    public long getContentId() {
        return content.getTargetId();
    }

    public long getGroupId() {
        return group.getTargetId();
    }

    public int getOrder() {
        return order;
    }
}
