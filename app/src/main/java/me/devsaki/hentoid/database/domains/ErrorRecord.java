package me.devsaki.hentoid.database.domains;

import androidx.annotation.NonNull;

import org.threeten.bp.Instant;
import org.threeten.bp.ZoneId;
import org.threeten.bp.format.DateTimeFormatter;

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
    public ToOne<Content> content;
    @Convert(converter = ErrorType.ErrorTypeConverter.class, dbType = Integer.class)
    public ErrorType type;
    public String url;
    String contentPart;
    public String description;
    @Convert(converter = InstantConverter.class, dbType = Long.class)
    Instant timestamp;

    public ErrorRecord() {
    }

    public ErrorRecord(long contentId, ErrorType type, String url, String contentPart, String description, Instant timestamp) {
        content.setTargetId(contentId);
        this.type = type;
        this.url = url;
        this.contentPart = contentPart;
        this.description = description;
        this.timestamp = timestamp;
    }

    @NonNull
    @Override
    public String toString() {
        String timeStr = "";
        if (timestamp != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME; // e.g. 2011-12-03T10:15:30
            timeStr = timestamp.atZone(ZoneId.systemDefault()).format(formatter) + " ";
        }
        return String.format("%s%s - [%s] : %s @ %s", timeStr, contentPart, type.getName(), description, url);
    }
}
