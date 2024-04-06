package me.devsaki.hentoid.database.converters

import io.objectbox.converter.PropertyConverter
import java.time.Instant

class InstantConverter : PropertyConverter<Instant, Long> {
    override fun convertToEntityProperty(databaseValue: Long?): Instant? {
        return if (databaseValue == null) null else Instant.ofEpochMilli(databaseValue)
    }

    override fun convertToDatabaseValue(entityProperty: Instant?): Long? {
        return entityProperty?.toEpochMilli()
    }
}