package me.devsaki.hentoid.json

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.ErrorRecord
import me.devsaki.hentoid.enums.ErrorType
import java.time.Instant

@JsonClass(generateAdapter = true)
data class JsonErrorRecord(
    val type: ErrorType,
    val url: String,
    val contentPart: String,
    val description: String,
    val timestamp: Long
) {
    constructor(er: ErrorRecord) : this(
        er.type,
        er.url,
        er.contentPart,
        er.description,
        er.timestamp.toEpochMilli()
    )

    fun toEntity(): ErrorRecord {
        return ErrorRecord(type, url, contentPart, description, Instant.ofEpochMilli(timestamp))
    }
}