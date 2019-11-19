package me.devsaki.hentoid.enums;

import javax.annotation.Nullable;

import io.objectbox.converter.PropertyConverter;

/**
 * Created by DevSaki on 10/05/2015.
 * Content Status enumerator
 */
public enum StatusContent {

    SAVED(0, "Saved"), DOWNLOADED(1, "Downloaded"), DOWNLOADING(2, "Downloading"),
    PAUSED(3, "Paused"), ERROR(4, "Error"), MIGRATED(5, "Migrated"), IGNORED(6, "Ignored"),
    UNHANDLED_ERROR(7, "Unhandled Error"), CANCELED(8, "Canceled"), ONLINE(9, "Online");

    private final int code;
    private final String description;

    StatusContent(int code, String description) {
        this.code = code;
        this.description = description;
    }

    @Nullable
    public static StatusContent searchByCode(int code) {
        for (StatusContent s : StatusContent.values()) {
            if (s.getCode() == code)
                return s;
        }

        return null;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }


    public static class StatusContentConverter implements PropertyConverter<StatusContent, Integer> {
        @Override
        public StatusContent convertToEntityProperty(Integer databaseValue) {
            if (databaseValue == null) {
                return null;
            }
            for (StatusContent statusContent : StatusContent.values()) {
                if (statusContent.getCode() == databaseValue) {
                    return statusContent;
                }
            }
            return StatusContent.SAVED;
        }

        @Override
        public Integer convertToDatabaseValue(StatusContent entityProperty) {
            return entityProperty == null ? null : entityProperty.getCode();
        }
    }

}
