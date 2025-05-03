package me.devsaki.hentoid.util.file

import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class DisplayFile {
    enum class Type {
        FOLDER, BOOK_FOLDER, ARCHIVE, PDF, ADD_BUTTON, UP_BUTTON, OTHER
    }

    val uri: Uri
    val name: String
    val lastModified: Long
    val type: Type

    var nbChildren = 0
    var coverUri: Uri? = null

    var isBeingProcessed: Boolean = false


    constructor(doc: DocumentFile, isBook: Boolean = false) {
        uri = doc.uri
        name = doc.name ?: ""
        lastModified = doc.lastModified()
        type = if (doc.isDirectory) if (isBook) Type.BOOK_FOLDER else Type.FOLDER
        else {
            if (isSupportedArchive(name)) Type.ARCHIVE
            else if (getExtension(name) == "pdf") Type.PDF
            else Type.OTHER
        }
    }

    // Used for the "Up one level" button
    constructor(uri: Uri, name: String) {
        this.uri = uri
        this.name = name
        lastModified = 0
        type = Type.UP_BUTTON
    }

    // Used for the "Add root" button
    constructor(name: String) {
        uri = Uri.EMPTY
        this.name = name
        lastModified = 0
        type = Type.ADD_BUTTON
    }
}