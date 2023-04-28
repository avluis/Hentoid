package me.devsaki.hentoid.util

import android.content.Context
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.util.file.FileHelper
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.time.Instant

class LogHelper {
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
        var logName = ""
            private set

        // Message to show when the log contains no data (to avoid creating a totally empty log file)
        var noDataMessage = "no data"
            private set

        // Message to display at the very beginning of the log
        var header = ""
            private set
        var entries: MutableList<LogEntry> = ArrayList()
            private set

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
        fun setEntries(entries: MutableList<LogEntry>) {
            this.entries = entries
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
    }

    companion object {
        private val LINE_SEPARATOR = System.getProperty("line.separator")

        /**
         * Build the log text using the given LogInfo
         *
         * @param info LogInfo to build the log with
         * @return Log text
         */
        private fun buildLog(info: LogInfo): String {
            val logStr = StringBuilder()
            logStr.append(info.logName).append(" log : begin").append(LINE_SEPARATOR)
            logStr.append(
                String.format(
                    "Hentoid ver: %s (%s)",
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE
                )
            ).append(LINE_SEPARATOR)
            logStr.append(String.format("API: %s", Build.VERSION.SDK_INT)).append(LINE_SEPARATOR)
            logStr.append(String.format("Device: %s", Build.MODEL)).append(LINE_SEPARATOR)
            if (info.entries.isEmpty()) logStr.append("No activity to report - ")
                .append(info.noDataMessage).append(LINE_SEPARATOR) else {
                // Log beginning, end and duration
                // Unfortunately, Comparator.comparing is API24...
                val beginning = info.entries.minWith { a: LogEntry, b: LogEntry ->
                    a.timestamp.compareTo(
                        b.timestamp
                    )
                }.timestamp
                val end = info.entries.maxWith { a: LogEntry, b: LogEntry ->
                    a.timestamp.compareTo(
                        b.timestamp
                    )
                }.timestamp
                val durationMs = end.toEpochMilli() - beginning.toEpochMilli()
                logStr.append("Start : ").append(beginning).append(LINE_SEPARATOR)
                logStr.append("End : ").append(end).append(" (")
                    .append(Helper.formatDuration(durationMs)).append(")").append(LINE_SEPARATOR)
                logStr.append("-----").append(LINE_SEPARATOR)

                // Log header
                if (info.header.isNotEmpty()) logStr.append(info.header).append(LINE_SEPARATOR)
                // Log entries in chapter order, with errors first
                val logChapters = info.entries.groupBy { obj: LogEntry -> obj.chapter }
                for (chapter in logChapters.values) {
                    val logChapterWithErrorsFirst = chapter.sortedBy { l: LogEntry -> !l.isError }
                    for (entry in logChapterWithErrorsFirst) logStr.append(entry.message)
                        .append(LINE_SEPARATOR)
                }
            }
            logStr.append(info.logName).append(" log : end")
            return logStr.toString()
        }

        /**
         * Write the given log to the app's default storage location
         *
         * @param context Context to use
         * @param logInfo Log to write
         * @return DocumentFile of the created log file; null if it couldn't be created
         */
        fun writeLog(context: Context, logInfo: LogInfo): DocumentFile? {
            try {
                // Create the log
                var logFileName = logInfo.getFileName()
                if (!logFileName.endsWith("_log")) logFileName += "_log"
                logFileName += ".txt"
                val log = buildLog(logInfo)

                // Save the log; use primary folder by default
                val folder = FileHelper.getDocumentFromTreeUriString(
                    context, Preferences.getStorageUri(StorageLocation.PRIMARY_1)
                )
                if (folder != null) {
                    val logDocumentFile = FileHelper.findOrCreateDocumentFile(
                        context, folder, "text/plain", logFileName
                    )
                    if (logDocumentFile != null) FileHelper.saveBinary(
                        context,
                        logDocumentFile.uri,
                        log.toByteArray()
                    )
                    return logDocumentFile
                } else { // If it fails, use device's "download" folder (panic mode)
                    FileHelper.openNewDownloadOutputStream(
                        HentoidApp.getInstance(),
                        logFileName,
                        "text/plain"
                    ).use { newDownload ->
                        ByteArrayInputStream(log.toByteArray()).use { input ->
                            Helper.copy(input, newDownload)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
            return null
        }
    }
}