package me.devsaki.hentoid.database.domains;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.relation.ToOne;

@Entity
public class QueueRecord {


    @Id
    public long id;
    private ToOne<Content> content;
    private int rank;

    public QueueRecord() {
    }  // Required for ObjectBox to work

    public QueueRecord(long id, int order, @Content.DownloadMode int downloadMode) {
        content.setTargetId(id);
        rank = order;
        content.getTarget().setDownloadMode(downloadMode);
    }

    public ToOne<Content> getContent() {
        return content;
    }

    public void setContent(ToOne<Content> content) {
        this.content = content;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public void setDownloadMode(@Content.DownloadMode int downloadMode) {
        content.getTarget().setDownloadMode(downloadMode);
    }

    public int getDownloadMode() {
        return content.getTarget().getDownloadMode();
    }
}
