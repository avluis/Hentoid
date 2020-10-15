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

    // Useful for unit tests not to fail on the CI environment
    private void initObjectBoxRelations() {
        this.content = new ToOne<>(this, QueueRecord_.content);
    }

    // No-arg constructor required by ObjectBox
    public QueueRecord() {
        initObjectBoxRelations();
    }

    public QueueRecord(long id, int order) {
        initObjectBoxRelations();
        content.setTargetId(id);
        rank = order;
    }
}
