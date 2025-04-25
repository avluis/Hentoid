package me.devsaki.hentoid.util.file

import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class DisplayFile(doc: DocumentFile) {
    var uri: Uri = doc.uri
    var name: String = doc.name ?: ""

    var nbChildren = 0
    var coverUri : Uri? = null

    var isBeingProcessed: Boolean = false
}