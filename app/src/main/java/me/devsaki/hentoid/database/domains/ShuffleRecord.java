package me.devsaki.hentoid.database.domains;

import javax.annotation.Nullable;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;

@Entity
public class ShuffleRecord {

    @Id
    public long id;
    private Long contentId;

    public ShuffleRecord() {
    } // Required for ObjectBox to work

    public ShuffleRecord(Long contentId) {
        this.contentId = contentId;
    }

    @Nullable
    public Long getContentId() {
        return contentId;
    }
}
