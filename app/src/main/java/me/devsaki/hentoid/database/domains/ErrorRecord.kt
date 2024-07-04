package me.devsaki.hentoid.database.domains

import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToOne
import me.devsaki.hentoid.database.converters.InstantConverter
import me.devsaki.hentoid.enums.ErrorType
import me.devsaki.hentoid.enums.ErrorType.ErrorTypeConverter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Entity
data class ErrorRecord(
    @Id
    var id: Long = 0,
    @Convert(converter = ErrorTypeConverter::class, dbType = Int::class)
    var type: ErrorType = ErrorType.UNDEFINED,
    val url: String = "",
    val contentPart: String = "",
    val description: String = "",
    @Convert(converter = InstantConverter::class, dbType = Long::class)
    val timestamp: Instant = Instant.EPOCH
) {
    lateinit var content: ToOne<Content>

    constructor(
        contentId: Long,
        errorType: ErrorType,
        url: String,
        contentPart: String,
        description: String
    ) : this(
        type = errorType,
        url = url,
        contentPart = contentPart,
        description = description,
        timestamp = Instant.now()
    ) {
        content.targetId = contentId
    }

    override fun toString(): String {
        var timeStr = ""
        if (timestamp != Instant.EPOCH) {
            val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME // e.g. 2011-12-03T10:15:30
            timeStr = timestamp.atZone(ZoneId.systemDefault()).format(formatter) + " "
        }

        return String.format(
            "%s%s - [%s]: %s @ %s",
            timeStr,
            contentPart,
            type.engName,
            description,
            url
        )
    }
}