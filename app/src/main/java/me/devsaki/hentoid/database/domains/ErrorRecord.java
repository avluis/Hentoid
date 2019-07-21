package me.devsaki.hentoid.database.domains;

import androidx.annotation.NonNull;

import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.relation.ToOne;
import me.devsaki.hentoid.enums.ErrorType;

@Entity
public class ErrorRecord {

    @Id
    public long id;
    public ToOne<Content> content;
    @Convert(converter = ErrorType.ErrorTypeConverter.class, dbType = Integer.class)
    public ErrorType type;
    public String url;
    String contentPart;
    public String description;

    public ErrorRecord() {
    }

    public ErrorRecord(long contentId, ErrorType type, String url, String contentPart, String description) {
        content.setTargetId(contentId);
        this.type = type;
        this.url = url;
        this.contentPart = contentPart;
        this.description = description;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format("%s - [%s] : %s @ %s", contentPart, type.getName(), description, url);
    }
}
