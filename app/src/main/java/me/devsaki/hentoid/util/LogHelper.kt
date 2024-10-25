package me.devsaki.hentoid.util

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.util.file.findOrCreateDocumentFile
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.openNewDownloadOutputStream
import me.devsaki.hentoid.util.file.saveBinary
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.time.Instant

private val LINE_SEPARATOR = System.lineSeparator()

/**
 * Represents a log entry
 */
class LogEntry {
    val timestamp: Instant
    val message: String
    val isError: Boolean

    /**
     * Chapter number can be used to organize entries in a non-chronological order
     * (e.g. general information entries before detailed entries, regardless of their timestamp)
     */
    val chapter: Int

    constructor(message: String) {
        timestamp = Instant.now()
        this.message = message
        chapter = 1
        isError = false
    }

    constructor(message: String, vararg formatArgs: Any?) {
        timestamp = Instant.now()
        this.message = String.format(message, *formatArgs)
        chapter = 1
        isError = false
    }

    constructor(message: String, isError: Boolean) {
        timestamp = Instant.now()
        this.message = message
        chapter = 1
        this.isError = isError
    }

    constructor(message: String, chapter: Int, isError: Boolean) {
        timestamp = Instant.now()
        this.message = message
        this.chapter = chapter
        this.isError = isError
    }

    constructor(timestamp: Instant, message: String) {
        this.timestamp = timestamp
        this.message = message
        chapter = 0
        isError = false
    }
}

class LogInfo(// Log file name, without the extension
    private var fileName: String
) {

    // Display name of the log, for use in its header
    private var logName = ""

    // Message to show when the log contains no data (to avoid creating a totally empty log file)
    private var noDataMessage = "no data"

    // Message to display at the very beginning of the log
    var header = ""
        private set

    private var entries: MutableList<LogEntry> = ArrayList()

    /**
     * Set the log file name, without the extension
     *
     * @param fileName Log file name
     */
    fun setFileName(fileName: String) {
        this.fileName = fileName
        if (logName.isEmpty()) logName = fileName
    }

    fun getFileName(): String {
        return fileName
    }

    /**
     * Set display name of the log, for use in its header
     *
     * @param headerName Display name
     */
    fun setHeaderName(headerName: String) {
        logName = headerName
        if (fileName.isEmpty()) fileName = headerName
    }

    /**
     * Set message to display when the log contains no data
     * (to avoid creating a totally empty log file)
     *
     * @param noDataMessage Message to display when the log contains no data
     */
    fun setNoDataMessage(noDataMessage: String) {
        this.noDataMessage = noDataMessage
    }

    /**
     * Set message to display at the very beginning of the log
     *
     * @param header Message to display at the very beginning of the log
     */
    fun setHeader(header: String) {
        this.header = header
    }

    /**
     * Set log entries
     *
     * @param entries Log entries
     */
    fun setEntries(entries: List<LogEntry>) {
        this.entries.clear()
        this.entries.addAll(entries)
    }

    /**
     * Add the given message as a log entry
     *
     * @param message Message to add as a log entry
     */
    fun addEntry(message: String) {
        if (entries.isEmpty()) entries = ArrayList()
        entries.add(LogEntry(message))
    }

    /**
     * Add the given message as a log entry
     *
     * @param message    Message to add as a log entry
     * @param formatArgs Formatting arguments
     */
    fun addEntry(message: String, vararg formatArgs: Any?) {
        if (entries.isEmpty()) entries = ArrayList()
        entries.add(LogEntry(message, *formatArgs))
    }

    /**
     * Clear all log entries
     */
    fun clear() {
        entries.clear()
    }

    /**
     * Build the log text using the given LogInfo
     *
     * @return Log text
     */
    fun build(): String {
        val logStr = StringBuilder()
        logStr.append(logName).append(" log : begin").append(LINE_SEPARATOR)
        logStr.append(
            String.format(
                "Hentoid ver: %s (%s)",
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE
            )
        ).append(LINE_SEPARATOR)
        logStr.append(String.format("API: %s", Build.VERSION.SDK_INT)).append(LINE_SEPARATOR)
        logStr.append(String.format("Device: %s", Build.MODEL)).append(LINE_SEPARATOR)
        if (entries.isEmpty()) logStr.append("No activity to report - ")
            .append(noDataMessage).append(LINE_SEPARATOR) else {
            // Log beginning, end and duration
            val beginning = entries.minWith { a: LogEntry, b: LogEntry ->
                a.timestamp.compareTo(b.timestamp)
            }.timestamp
            val end = entries.maxWith { a: LogEntry, b: LogEntry ->
                a.timestamp.compareTo(b.timestamp)
            }.timestamp
            val durationMs = end.toEpochMilli() - beginning.toEpochMilli()
            logStr.append("Start : ").append(beginning).append(LINE_SEPARATOR)
            logStr.append("End : ").append(end).append(" (")
                .append(formatDuration(durationMs)).append(")").append(LINE_SEPARATOR)
            logStr.append("-----").append(LINE_SEPARATOR)

            // Log header
            if (header.isNotEmpty()) logStr.append(header).append(LINE_SEPARATOR)
            // Log entries in chapter order, with errors first
            val logChapters = entries.groupBy { obj: LogEntry -> obj.chapter }
            for (chapter in logChapters.values) {
                val logChapterWithErrorsFirst = chapter.sortedBy { l: LogEntry -> !l.isError }
                for (entry in logChapterWithErrorsFirst) logStr.append(entry.message)
                    .append(LINE_SEPARATOR)
            }
        }
        logStr.append(logName).append(" log : end")
        return logStr.toString()
    }
}

fun trace(
    priority: Int,
    chapter: Int,
    memoryLog: MutableList<LogEntry>?,
    str: String,
    vararg t: Any
) {
    val s = String.format(str, *t)
    Timber.log(priority, s)
    val isError = priority > Log.INFO
    memoryLog?.add(LogEntry(s, chapter, isError))
}

/**
 * Write the given log to the app's default storage location
 *
 * @param logInfo Log to write
 * @return DocumentFile of the created log file; null if it couldn't be created
 */
fun Context.writeLog(logInfo: LogInfo): DocumentFile? {
    try {
        // Create the log
        var logFileName = logInfo.getFileName()
        if (!logFileName.endsWith("_log")) logFileName += "_log"
        logFileName += ".txt"
        val log = logInfo.build()

        // Save the log; use primary folder by default
        val folder = getDocumentFromTreeUriString(
            this, Preferences.getStorageUri(StorageLocation.PRIMARY_1)
        )
        if (folder != null) {
            val logDocumentFile = findOrCreateDocumentFile(
                this, folder, "text/plain", logFileName
            )
            if (logDocumentFile != null) saveBinary(
                this,
                logDocumentFile.uri,
                log.toByteArray()
            )
            return logDocumentFile
        } else { // If it fails, use device's "download" folder (panic mode)
            openNewDownloadOutputStream(
                HentoidApp.getInstance(),
                logFileName,
                "text/plain"
            )?.use { newDownload ->
                ByteArrayInputStream(log.toByteArray()).use { input ->
                    copy(input, newDownload)
                }
            }
        }
    } catch (e: Exception) {
        Timber.e(e)
    }
    return null
}