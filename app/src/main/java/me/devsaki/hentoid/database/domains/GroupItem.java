package me.devsaki.hentoid.database.domains;

import androidx.annotation.NonNull;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.relation.ToOne;

@Entity
public class GroupItem {

    @Id
    public long id;
    public ToOne<Content> content;
    public ToOne<Group> group;
    public int order;


    // Useful for unit tests not to fail on the CI environment
    private void initObjectBoxRelations() {
        this.content = new ToOne<>(this, GroupItem_.content);
        this.group = new ToOne<>(this, GroupItem_.group);
    }

    // No-arg constructor required by ObjectBox
    public GroupItem() {
        initObjectBoxRelations();
    }

    public GroupItem(@NonNull final Content content, @NonNull final Group group, int order) {
        initObjectBoxRelations();
        this.content.setTarget(content);
        this.group.setTarget(group);
        this.order = order;
    }

    public Content getContent() {
        return content.getTarget();
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
