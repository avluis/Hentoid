package me.devsaki.hentoid.json;

import org.threeten.bp.Instant;

import me.devsaki.hentoid.database.domains.ErrorRecord;
import me.devsaki.hentoid.enums.ErrorType;

class JsonErrorRecord {

    private ErrorType type;
    private String url;
    private String contentPart;
    private String description;
    private Long timestamp;

    private JsonErrorRecord() {
    }

    static JsonErrorRecord fromEntity(ErrorRecord er) {
        JsonErrorRecord result = new JsonErrorRecord();
        result.type = er.type;
        result.url = er.url;
        result.contentPart = er.contentPart;
        result.description = er.description;
        result.timestamp = er.timestamp.toEpochMilli();
        return result;
    }

    ErrorRecord toEntity() {
        ErrorRecord result = new ErrorRecord();
        result.type = type;
        result.url = url;
        result.contentPart = contentPart;
        result.description = description;
        result.timestamp = Instant.ofEpochMilli(timestamp);

        return result;
    }
}
