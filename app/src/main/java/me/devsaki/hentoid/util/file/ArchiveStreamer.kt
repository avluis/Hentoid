package me.devsaki.hentoid.util.file

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import me.devsaki.hentoid.util.copy
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.Hashtable
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.CRC32
import java.util.zip.Checksum
import java.util.zip.ZipEntry
import java.util.zip.ZipEntry.STORED
import java.util.zip.ZipOutputStream


class ArchiveStreamer(context: Context, val archiveUri: Uri) {

    private val stream = ZipOutputStream(getOutputStream(context, archiveUri))
    private val filesQueue: Queue<Uri> = LinkedList()
    private val filesMatch: MutableMap<String, String> = Hashtable()

    private val stop = AtomicBoolean(false)
    private val isQueueActive = AtomicBoolean(false)

    init {
        Timber.d("Archive streamer : Init @ $archiveUri")
        stream.setMethod(STORED)
    }

    val queueActive: Boolean
        get() = isQueueActive.get()

    val mappedUris: Map<String, String>
        get() = filesMatch


    fun close() {
        Timber.d("Archive streamer : Closing")
        stop.set(true)
        stream.flush()
        stream.close()
        filesMatch.clear()
        isQueueActive.set(false)
    }

    fun addFile(context: Context, uri: Uri) {
        filesQueue.add(uri)
        Timber.d("Adding file to archive queue : $uri")
        processQueue(context)
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun processQueue(context: Context) {
        if (isQueueActive.get()) return

        Timber.d("Archive queue : activating")

        GlobalScope.launch(Dispatchers.IO) {
            var uri = filesQueue.poll()
            while (uri != null && !stop.get()) {
                isQueueActive.set(true)
                getDocumentProperties(context, uri)?.let { doc ->
                    Timber.d("Processing archive queue (${filesQueue.size}) : $uri")
                    val name = doc.name
                    val entry = ZipEntry(name)
                    entry.size = doc.size
                    entry.method = STORED
                    getInputStream(context, uri).use {
                        entry.crc = getChecksumValue(CRC32(), it)
                    }
                    // TODO retry when CRC32 fails (ZipException)
                    stream.putNextEntry(entry)
                    getInputStream(context, uri).use {
                        copy(it, stream)
                    }
                    filesMatch[uri.toString()] = archiveUri.toString() + File.separator + name
                }
                removeFile(context, uri)
                uri = filesQueue.poll()
            }
            Timber.d("Archive queue : nothing more to process")
            isQueueActive.set(false)
        }
    }

    fun getChecksumValue(checksum: Checksum, fis: InputStream): Long {
        try {
            val bis = BufferedInputStream(fis)
            val bytes = ByteArray(1024)
            var len = 0

            while ((bis.read(bytes).also { len = it }) >= 0) {
                checksum.update(bytes, 0, len)
            }
            bis.close()
        } catch (e: IOException) {
            Timber.e(e)
        }
        return checksum.value
    }
}