package me.devsaki.hentoid.json;

import java.time.Instant;

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
        result.type = er.getType();
        result.url = er.getUrl();
        result.contentPart = er.getContentPart();
        result.description = er.getDescription();
        result.timestamp = er.getTimestamp().toEpochMilli();
        return result;
    }

    ErrorRecord toEntity() {
        return new ErrorRecord(type, url, contentPart, description, Instant.ofEpochMilli(timestamp));
    }
}
