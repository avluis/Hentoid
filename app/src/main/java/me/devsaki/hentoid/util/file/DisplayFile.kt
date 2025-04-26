package me.devsaki.hentoid.util.file

import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class DisplayFile {
    enum class Type {
        FOLDER, ARCHIVE, PDF, ADD_BUTTON, OTHER
    }

    val uri: Uri
    val name: String
    val type: Type

    var nbChildren = 0
    var coverUri: Uri? = null

    var isBeingProcessed: Boolean = false


    constructor(doc: DocumentFile) {
        uri = doc.uri
        name = doc.name ?: ""
        type = if (doc.isDirectory) Type.FOLDER
        else {
            if (isSupportedArchive(name)) Type.ARCHIVE
            else if (getExtension(name) == "pdf") Type.PDF
            else Type.OTHER
        }
    }

    constructor(name: String) {
        uri = Uri.EMPTY
        this.name = name
        type = Type.ADD_BUTTON
    }
}