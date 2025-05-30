package me.devsaki.hentoid.util.file

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import me.devsaki.hentoid.util.hash64

class DisplayFile {
    enum class Type {
        ADD_BUTTON, UP_BUTTON, ROOT_FOLDER, FOLDER, BOOK_FOLDER, SUPPORTED_FILE, OTHER
    }

    enum class SubType {
        ARCHIVE, PDF, OTHER
    }

    val id: Long
    val uri: Uri
    val parent: Uri
    val name: String
    val lastModified: Long
    var contentId: Long = 0
    var type: Type
    var subType: SubType

    var nbChildren = 0
    var coverUri: Uri = Uri.EMPTY

    var isBeingProcessed: Boolean = false


    constructor(doc: DocumentFile, isBook: Boolean = false, parent: Uri = Uri.EMPTY) {
        uri = doc.uri
        id = hash64(doc.uri.toString())
        this.parent = parent
        name = doc.name ?: ""
        lastModified = doc.lastModified()
        if (doc.isDirectory) if (isBook) {
            type = Type.BOOK_FOLDER
            subType = SubType.OTHER
        } else if (parent == Uri.EMPTY) {
            type = Type.ROOT_FOLDER
            subType = SubType.OTHER
        } else {
            type = Type.FOLDER
            subType = SubType.OTHER
        }
        else {
            if (isSupportedArchive(name)) {
                type = Type.SUPPORTED_FILE
                subType = SubType.ARCHIVE
            } else if (getExtension(name) == "pdf") {
                type = Type.SUPPORTED_FILE
                subType = SubType.PDF
            } else {
                type = Type.OTHER
                subType = SubType.OTHER
            }
        }
    }

    // Used for the "Add root" and "Go up one level" buttons
    constructor(name: String, type: Type) {
        uri = Uri.EMPTY
        id = hash64(type.name.toByteArray())
        parent = Uri.EMPTY
        this.name = name
        lastModified = 0
        this.type = type
        subType = SubType.OTHER
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DisplayFile

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}