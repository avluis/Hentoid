package me.devsaki.hentoid.util.file

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.util.copy
import timber.log.Timber
import java.io.OutputStream
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipEntry.STORED
import java.util.zip.ZipOutputStream

class ArchiveStreamer(fStream: OutputStream) {

    private val stream = ZipOutputStream(fStream)
    private val filesQueue: Queue<Uri> = LinkedList()
    private val stop = AtomicBoolean(false)
    private val isQueueActive = AtomicBoolean(false)

    init {
        stream.setMethod(STORED)
    }

    val queueActive: Boolean
        get() = isQueueActive.get()

    fun close() {
        stop.set(true)
        stream.flush()
        stream.close()
    }

    suspend fun addFile(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        filesQueue.add(uri)
        Timber.d("Adding file to archive queue : $uri")
        processQueue(context)
    }

    private fun processQueue(context: Context) {
        if (isQueueActive.get()) return

        var uri = filesQueue.poll()
        while (uri != null && !stop.get()) {
            isQueueActive.set(true)
            getDocumentFromTreeUri(context, uri)?.let { doc ->
                Timber.d("Processing archive queue (${filesQueue.size}) : $uri")
                val entry = ZipEntry(doc.name ?: "")
                entry.size = doc.length()
                entry.method = STORED
                stream.putNextEntry(entry)
                copy(getInputStream(context, doc), stream)
            }
            removeFile(context, uri)
            uri = filesQueue.poll()
        }
        Timber.d("Archive queue : nothing more to process")
        isQueueActive.set(false)
    }
}