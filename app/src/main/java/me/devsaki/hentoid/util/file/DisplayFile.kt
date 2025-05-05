package me.devsaki.hentoid.util.file

import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class DisplayFile {
    enum class Type {
        ADD_BUTTON, UP_BUTTON, FOLDER, BOOK_FOLDER, SUPPORTED_FILE, OTHER
    }

    enum class SubType {
        ARCHIVE, PDF, OTHER
    }

    val uri: Uri
    val parent: Uri
    val name: String
    val lastModified: Long
    val type: Type
    val subType: SubType

    var nbChildren = 0
    var coverUri: Uri? = null

    var isBeingProcessed: Boolean = false


    constructor(doc: DocumentFile, isBook: Boolean = false, parent: Uri = Uri.EMPTY) {
        uri = doc.uri
        this.parent = parent
        name = doc.name ?: ""
        lastModified = doc.lastModified()
        if (doc.isDirectory) if (isBook) {
            type = Type.BOOK_FOLDER
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
        parent = Uri.EMPTY
        this.name = name
        lastModified = 0
        this.type = type
        subType = SubType.OTHER
    }
}