package me.devsaki.hentoid.enums;

import javax.annotation.Nullable;

import io.objectbox.converter.PropertyConverter;

/**
 * Content Status enumerator
 */
public enum StatusContent {

    SAVED(0, "Saved"), // Content webpage has been accessed by the browser but hasn't been queued yet -> content is "pre-saved" to the DB and will be deleted upon next app restart if not queued
    DOWNLOADED(1, "Downloaded"), // Content has been downloaded successfully
    DOWNLOADING(2, "Downloading"), // Content is in Hentoid's download queue and is being downloaded
    PAUSED(3, "Paused"), // Content is in Hentoid's download queue and is not being downloaded
    ERROR(4, "Error"), // Content download has failed
    MIGRATED(5, "Migrated"), // Unused value; kept for retrocompatibility
    IGNORED(6, "Ignored"), // Transient status set by the web parser to indicate a content page that cannot be parsed
    UNHANDLED_ERROR(7, "Unhandled Error"), // Default status for image files
    CANCELED(8, "Canceled"), // Unused value; kept for retrocompatibility
    ONLINE(9, "Online"), // Used for ImageFiles only : image can be viewed on-demand (streamed content; undownloaded covers)
    EXTERNAL(10, "External"), // Content is accessible in the external library
    PLACEHOLDER(11, "Placeholder"); // Content has been imported as an empty placeholder (couldn't be streamed)

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
