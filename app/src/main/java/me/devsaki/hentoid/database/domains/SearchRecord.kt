package me.devsaki.hentoid.database.domains

import android.net.Uri
import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.converter.PropertyConverter

@Entity
data class SearchRecord(
    @Id
    var id: Long = 0,
    @Index
    @Convert(converter = EntityTypeConverter::class, dbType = Int::class)
    var entityType: EntityType = EntityType.CONTENT,
    val searchString: String = "",
    var label: String = "",
    var timestamp: Long = 0
) {
    enum class EntityType(val code: Int) {
        CONTENT(0),
        GROUP(1)
    }

    companion object {
        fun contentSearch(searchUri: Uri): SearchRecord {
            return SearchRecord(
                0,
                EntityType.CONTENT,
                searchUri.toString(),
                searchUri.path?.substring(1) ?: ""
            )
        }

        fun contentSearch(searchUri: Uri, label: String): SearchRecord {
            return SearchRecord(0, EntityType.CONTENT, searchUri.toString(), label)
        }

        fun groupSearch(searchUri: Uri): SearchRecord {
            return SearchRecord(
                0,
                EntityType.GROUP,
                searchUri.toString(),
                searchUri.path?.substring(1) ?: ""
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val that = other as SearchRecord

        return searchString == that.searchString
    }

    override fun hashCode(): Int {
        return searchString.hashCode()
    }

    class EntityTypeConverter : PropertyConverter<EntityType, Int> {
        override fun convertToEntityProperty(databaseValue: Int?): EntityType {
            if (databaseValue != null) {
                for (type in EntityType.entries) {
                    if (type.code == databaseValue) return type
                }
            }
            return EntityType.CONTENT
        }

        override fun convertToDatabaseValue(entityProperty: EntityType): Int {
            return entityProperty.code
        }
    }
}