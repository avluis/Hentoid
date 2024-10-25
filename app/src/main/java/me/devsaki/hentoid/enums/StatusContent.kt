package me.devsaki.hentoid.enums

import io.objectbox.converter.PropertyConverter

/**
 * Content Status enumerator
 */
enum class StatusContent(val code: Int, val description: String) {
    // Content webpage has been accessed by the browser but hasn't been queued yet -> content is "pre-saved" to the DB and will be deleted upon next app restart if not queued
    SAVED(0, "Saved"),
    DOWNLOADED(1, "Downloaded"), // Content has been downloaded successfully
    DOWNLOADING(2, "Downloading"), // Content is in Hentoid's download queue and is being downloaded
    PAUSED(3, "Paused"), // Content is in Hentoid's download queue and is not being downloaded
    ERROR(4, "Error"), // Content download has failed
    MIGRATED(5, "Migrated"), // Unused value; kept for retrocompatibility

    // Transient status set by the web parser to indicate a content page that cannot be parsed
    IGNORED(6, "Ignored"),
    UNHANDLED_ERROR(7, "Unhandled Error"), // Default status for image files
    CANCELED(8, "Canceled"), // Unused value; kept for retrocompatibility

    // Used for ImageFiles only : image can be viewed on-demand (streamed content; undownloaded covers)
    ONLINE(9, "Online"),
    EXTERNAL(10, "External"), // Content is accessible in the external library

    // Content has been imported as an empty placeholder (couldn't be streamed)
    PLACEHOLDER(11, "Placeholder");

    companion object {
        fun searchByCode(code: Int): StatusContent? {
            return entries.firstOrNull { code == it.code }
        }
    }

    class Converter : PropertyConverter<StatusContent?, Int?> {
        override fun convertToEntityProperty(databaseValue: Int?): StatusContent? {
            if (databaseValue == null) return null
            for (statusContent in StatusContent.entries) {
                if (statusContent.code == databaseValue) return statusContent
            }
            return SAVED
        }

        override fun convertToDatabaseValue(entityProperty: StatusContent?): Int? {
            return entityProperty?.code
        }
    }
}