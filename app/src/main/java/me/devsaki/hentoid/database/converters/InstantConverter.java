package me.devsaki.hentoid.database.converters;

import androidx.annotation.Nullable;

import java.time.Instant;

import io.objectbox.converter.PropertyConverter;

public class InstantConverter implements PropertyConverter<Instant, Long> {
    @Override
    @Nullable
    public Instant convertToEntityProperty(Long databaseValue) {
        if (databaseValue == null) return null;
        return Instant.ofEpochMilli(databaseValue);
    }

    @Override
    @Nullable
    public Long convertToDatabaseValue(Instant entityProperty) {
        return entityProperty == null ? null : entityProperty.toEpochMilli();
    }
}