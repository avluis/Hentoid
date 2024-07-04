package me.devsaki.hentoid.database.domains

import android.net.Uri
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class SearchRecord(
    @Id
    var id: Long = 0,
    val searchString: String = "",
    var label: String = ""
) {
    constructor(searchString: String, label: String) : this(0, searchString, label)
    constructor(searchUri: Uri) : this(searchUri.toString(), searchUri.path?.substring(1) ?: "")
    constructor(searchUri: Uri, label: String) : this(searchUri.toString(), label)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val that = other as SearchRecord

        return searchString == that.searchString
    }

    override fun hashCode(): Int {
        return searchString.hashCode()
    }
}