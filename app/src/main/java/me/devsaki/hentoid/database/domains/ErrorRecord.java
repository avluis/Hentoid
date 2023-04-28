package me.devsaki.hentoid.database.domains;

import androidx.annotation.NonNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.relation.ToOne;
import me.devsaki.hentoid.database.converters.InstantConverter;
import me.devsaki.hentoid.enums.ErrorType;

@Entity
public class ErrorRecord {

    @Id
    public long id;
    private ToOne<Content> content;
    @Convert(converter = ErrorType.ErrorTypeConverter.class, dbType = Integer.class)
    private ErrorType type;
    private String url;
    private String contentPart;
    private String description;
    @Convert(converter = InstantConverter.class, dbType = Long.class)
    private Instant timestamp;


    public ErrorRecord() { // Required by ObjectBox when an alternate constructor exists
    }

    public ErrorRecord(ErrorType type, String url, String contentPart, String description, Instant timestamp) {
        this.type = type;
        this.url = url;
        this.contentPart = contentPart;
        this.description = description;
        this.timestamp = timestamp;
    }

    public ErrorRecord(long contentId, ErrorType type, String url, String contentPart, String description, Instant timestamp) {
        content.setTargetId(contentId);
        this.type = type;
        this.url = url;
        this.contentPart = contentPart;
        this.description = description;
        this.timestamp = timestamp;
    }


    public ErrorType getType() {
        return type;
    }

    public String getUrl() {
        return url;
    }

    public String getContentPart() {
        return contentPart;
    }

    public String getDescription() {
        return (null == description) ? "" : description;
    }

    public Instant getTimestamp() {
        return (null == timestamp) ? Instant.EPOCH : timestamp;
    }

    public ToOne<Content> getContent() {
        return content;
    }

    public void setContent(ToOne<Content> content) {
        this.content = content;
    }

    @NonNull
    @Override
    public String toString() {
        String timeStr = "";
        if (timestamp != null && !timestamp.equals(Instant.EPOCH)) {
            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME; // e.g. 2011-12-03T10:15:30
            timeStr = timestamp.atZone(ZoneId.systemDefault()).format(formatter) + " ";
        }

        return String.format("%s%s - [%s]: %s @ %s", timeStr, contentPart, type.getEngName(), description, url);
    }
}
