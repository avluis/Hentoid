package me.devsaki.hentoid.util.file

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.Queue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.CRC32
import java.util.zip.Checksum
import java.util.zip.ZipException


class ArchiveStreamer(context: Context, val archiveUri: Uri, append: Boolean) {

    private val stream = ZipStream(context, archiveUri)
    private val filesQueue: Queue<Uri> = ConcurrentLinkedQueue()
    private val filesMatch: MutableMap<String, String> = ConcurrentHashMap()

    private val stop = AtomicBoolean(false)

    private val isQueueActive = AtomicBoolean(false)
    private val isQueueFailed = AtomicBoolean(false)
    private var queueFailMsg = ""

    init {
        Timber.d("Archive streamer : Init @ $archiveUri (append $append)")
    }

    val queueActive: Boolean
        get() = isQueueActive.get()

    val queueFailed: Boolean
        get() = isQueueFailed.get()

    val queueFailMessage: String
        get() = queueFailMsg

    val mappedUris: Map<String, String>
        get() = filesMatch


    fun close() {
        Timber.d("Archive streamer : Closing")
        stop.set(true)
        stream.close()
        filesMatch.clear()
        isQueueActive.set(false)
        isQueueFailed.set(false)
        queueFailMsg = ""
    }

    fun addFile(context: Context, uri: Uri) {
        filesQueue.add(uri)
        Timber.d("Adding file to archive queue : $uri")
        processQueue(context)
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun processQueue(context: Context) {
        synchronized(isQueueActive) {
            if (isQueueActive.get()) return

            Timber.d("Archive queue : activating")
            isQueueActive.set(true)
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                var uri = filesQueue.peek()
                while (uri != null && !stop.get()) {
                    try {
                        getDocumentProperties(context, uri)?.let { doc ->
                            Timber.d("Processing archive queue BEGIN (${filesQueue.size}) : $uri")
                            val name = doc.name
                            val crc = getInputStream(context, uri).use {
                                getChecksumValue(CRC32(), it)
                            }
                            stream.putStoredRecord(name, doc.size, crc)
                            getInputStream(context, uri).use { stream.transferData(it) }
                            stream.closeRecord()
                            filesMatch[uri.toString()] =
                                archiveUri.toString() + File.separator + name
                        } ?: throw IOException("Document not found : $uri")
                        Timber.d("Processing archive queue END : $uri")
                        removeFile(context, uri)
                        // Only remove from queue if all above has succeeded
                        filesQueue.remove(uri)
                        uri = filesQueue.peek()
                    } catch (z: ZipException) {
                        Timber.d(z)
                        if (z.message?.contains("duplicate entry") ?: false)
                            throw IOException(z)
                        // Retry directly inside the loop (e.g. CRC failed)
                        Timber.v(z, "Archive queue glitched; retrying")
                    }
                }
                Timber.d("Archive queue : nothing more to process")
            } catch (e: Exception) {
                Timber.w(e, "Archive queue FAILED")
                isQueueFailed.set(true)
                queueFailMsg = e.message ?: ""
            } finally {
                isQueueActive.set(false)
            }
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