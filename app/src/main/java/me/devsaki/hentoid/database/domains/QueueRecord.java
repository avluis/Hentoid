package me.devsaki.hentoid.database.domains;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.relation.ToOne;

@Entity
public class QueueRecord {

    @Id
    public long id;
    public ToOne<Content> content;
    public int rank;

    public QueueRecord() {
    }  // Required for ObjectBox to work

    public QueueRecord(long id, int order) {
        content.setTargetId(id);
        rank = order;
    }
}
