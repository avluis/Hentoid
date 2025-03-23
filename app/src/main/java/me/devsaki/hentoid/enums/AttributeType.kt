package me.devsaki.hentoid.enums

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import io.objectbox.converter.PropertyConverter
import me.devsaki.hentoid.R

enum class AttributeType(
    val code: Int,
    @StringRes val displayName: Int,
    @StringRes val accusativeName: Int,
    val icon: Int,
    @ColorRes val color: Int
) {
    // Attributes stored in Attributes table of the DB
    ARTIST(
        0,
        R.string.category_artist,
        R.string.object_artist,
        R.drawable.ic_attribute_artist,
        R.color.purple_dark
    ),
    PUBLISHER(
        1,
        R.string.category_publisher,
        R.string.object_publisher,
        R.drawable.ic_hentoid_shape,
        R.color.black
    ),
    LANGUAGE(
        2,
        R.string.category_language,
        R.string.object_language,
        R.drawable.ic_attribute_language,
        R.color.red
    ),
    TAG(
        3,
        R.string.category_tag,
        R.string.object_tag,
        R.drawable.ic_attribute_tag,
        R.color.medium_gray
    ),
    TRANSLATOR(
        4,
        R.string.category_translator,
        R.string.object_translator,
        R.drawable.ic_hentoid_shape,
        R.color.black
    ),
    SERIE(
        5,
        R.string.category_series,
        R.string.object_series,
        R.drawable.ic_attribute_serie,
        R.color.blue
    ),
    UPLOADER(
        6,
        R.string.category_uploader,
        R.string.object_uploader,
        R.drawable.ic_hentoid_shape,
        R.color.black
    ),
    CIRCLE(
        7,
        R.string.category_circle,
        R.string.object_circle,
        R.drawable.ic_hentoid_shape,
        R.color.purple_light
    ),
    CHARACTER(
        8,
        R.string.category_character,
        R.string.object_character,
        R.drawable.ic_attribute_character,
        R.color.orange_light
    ),
    CATEGORY(
        9,
        R.string.category_category,
        R.string.object_category,
        R.drawable.ic_hentoid_shape,
        R.color.black
    ),

    // Attributes displayed on screen and stored elsewhere
    SOURCE(
        10,
        R.string.category_source,
        R.string.object_source,
        R.drawable.ic_attribute_source,
        R.color.green
    ),
    UNDEFINED(
        99,
        R.string.category_undefined,
        R.string.object_undefined,
        R.drawable.ic_attribute_tag,
        R.color.medium_gray
    ); // Specific to the metadata editor


    @Suppress("unused")
    class AttributeTypeAdapter {
        @ToJson
        fun toJson(attrType: AttributeType): String {
            return attrType.name
        }

        @FromJson
        fun fromJson(name: String): AttributeType? {
            var attrType = AttributeType.searchByName(name)
            if (null == attrType && name.equals("series", ignoreCase = true))
                attrType = SERIE // Fix the issue with v1.6.5
            return attrType
        }
    }

    class AttributeTypeConverter : PropertyConverter<AttributeType, Int> {
        override fun convertToEntityProperty(databaseValue: Int?): AttributeType? {
            if (databaseValue == null) {
                return null
            }
            for (type in entries) {
                if (type.code == databaseValue) {
                    return type
                }
            }
            return TAG
        }

        override fun convertToDatabaseValue(entityProperty: AttributeType): Int {
            return entityProperty.code
        }
    }

    companion object {
        fun searchByCode(code: Int): AttributeType? {
            for (s in entries) {
                if (s.code == code) {
                    return s
                }
            }
            return null
        }

        fun searchByName(name: String?): AttributeType? {
            for (s in entries) {
                if (s.name.equals(name, ignoreCase = true)) {
                    return s
                }
            }
            return null
        }
    }
}