package me.devsaki.hentoid.database.domains;

import io.objectbox.annotation.Backlink;
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

    public GroupItem() {
    }  // Required for ObjectBox to work
}
