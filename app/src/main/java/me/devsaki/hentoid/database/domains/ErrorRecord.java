package me.devsaki.hentoid.database.domains;

import androidx.annotation.NonNull;

import org.threeten.bp.Instant;
import org.threeten.bp.ZoneId;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.Locale;

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
    public Instant timestamp;

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
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US);
            timeStr = timestamp.atZone(ZoneId.systemDefault()).format(formatter) + " ";
        }
        return String.format("%s%s - [%s] : %s @ %s", timeStr, contentPart, type.getName(), description, url);
    }
}
