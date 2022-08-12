package me.devsaki.hentoid.util;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.threeten.bp.Instant;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.util.file.FileHelper;
import timber.log.Timber;

/**
 * Helper class for log generation
 */
public class LogHelper {

    private LogHelper() {
        throw new IllegalStateException("Utility class");
    }

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    /**
     * Represents a log entry
     */
    public static class LogEntry {
        private final Instant timestamp;
        private final String message;
        private final boolean isError;
        /**
         * Chapter number can be used to organize entries in a non-chronological order
         * (e.g. general information entries before detailed entries, regardless of their timestamp)
         */
        private final int chapter;

        public LogEntry(@NonNull String message) {
            this.timestamp = Instant.now();
            this.message = message;
            this.chapter = 1;
            this.isError = false;
        }

        public LogEntry(@NonNull String message, Object... formatArgs) {
            this.timestamp = Instant.now();
            this.message = String.format(message, formatArgs);
            this.chapter = 1;
            this.isError = false;
        }

        public LogEntry(@NonNull String message, boolean isError) {
            this.timestamp = Instant.now();
            this.message = message;
            this.chapter = 1;
            this.isError = isError;
        }

        public LogEntry(@NonNull String message, int chapter, boolean isError) {
            this.timestamp = Instant.now();
            this.message = message;
            this.chapter = chapter;
            this.isError = isError;
        }

        public LogEntry(@NonNull Instant timestamp, @NonNull String message) {
            this.timestamp = timestamp;
            this.message = message;
            this.chapter = 0;
            this.isError = false;
        }

        public Integer getChapter() {
            return chapter;
        }
    }

    public static class LogInfo {
        // Log file name, without the extension
        private String fileName = "";
        // Display name of the log, for use in its header
        private String logName = "";
        // Message to show when the log contains no data (to avoid creating a totally empty log file)
        private String noDataMessage = "no data";
        // Message to display at the very beginning of the log
        private String header = "";
        private List<LogEntry> entries = Collections.emptyList();

        public LogInfo() {
        }

        public LogInfo(@NonNull String fileName) {
            this.fileName = fileName;
        }

        /**
         * Set the log file name, without the extension
         *
         * @param fileName Log file name
         */
        public void setFileName(@NonNull String fileName) {
            this.fileName = fileName;
            if (this.logName.isEmpty()) logName = fileName;
        }

        /**
         * Set display name of the log, for use in its header
         *
         * @param headerName Display name
         */
        public void setHeaderName(@NonNull String headerName) {
            this.logName = headerName;
            if (this.fileName.isEmpty()) this.fileName = headerName;
        }

        /**
         * Set message to display when the log contains no data
         * (to avoid creating a totally empty log file)
         *
         * @param noDataMessage Message to display when the log contains no data
         */
        public void setNoDataMessage(@NonNull String noDataMessage) {
            this.noDataMessage = noDataMessage;
        }

        /**
         * Set message to display at the very beginning of the log
         *
         * @param header Message to display at the very beginning of the log
         */
        public void setHeader(@NonNull String header) {
            this.header = header;
        }

        /**
         * Set log entries
         *
         * @param entries Log entries
         */
        public void setEntries(@NonNull List<LogEntry> entries) {
            this.entries = entries;
        }

        /**
         * Add the given message as a log entry
         *
         * @param message Message to add as a log entry
         */
        public void addEntry(@NonNull String message) {
            if (entries.isEmpty()) entries = new ArrayList<>();
            entries.add(new LogEntry(message));
        }

        /**
         * Add the given message as a log entry
         *
         * @param message    Message to add as a log entry
         * @param formatArgs Formatting arguments
         */
        public void addEntry(@NonNull String message, Object... formatArgs) {
            if (entries.isEmpty()) entries = new ArrayList<>();
            entries.add(new LogEntry(message, formatArgs));
        }

        /**
         * Clear all log entries
         */
        public void clear() {
            entries.clear();
        }
    }

    /**
     * Build the log text using the given LogInfo
     *
     * @param info LogInfo to build the log with
     * @return Log text
     */
    private static String buildLog(@Nonnull LogInfo info) {
        StringBuilder logStr = new StringBuilder();
        logStr.append(info.logName).append(" log : begin").append(LINE_SEPARATOR);
        logStr.append(String.format("Hentoid ver: %s (%s)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)).append(LINE_SEPARATOR);
        logStr.append(String.format("API: %s", Build.VERSION.SDK_INT)).append(LINE_SEPARATOR);
        logStr.append(String.format("Device: %s", Build.MODEL)).append(LINE_SEPARATOR);
        if (info.entries.isEmpty())
            logStr.append("No activity to report - ").append(info.noDataMessage).append(LINE_SEPARATOR);
        else {
            // Log beginning, end and duration
            // Unfortunately, Comparator.comparing is API24...
            Instant beginning = Stream.of(info.entries).withoutNulls().min((a, b) -> a.timestamp.compareTo(b.timestamp)).get().timestamp;
            Instant end = Stream.of(info.entries).withoutNulls().max((a, b) -> a.timestamp.compareTo(b.timestamp)).get().timestamp;
            long durationMs = end.toEpochMilli() - beginning.toEpochMilli();
            logStr.append("Start : ").append(beginning).append(LINE_SEPARATOR);
            logStr.append("End : ").append(end).append(" (").append(Helper.formatDuration(durationMs)).append(")").append(LINE_SEPARATOR);
            logStr.append("-----").append(LINE_SEPARATOR);

            // Log header
            if (!info.header.isEmpty()) logStr.append(info.header).append(LINE_SEPARATOR);
            // Log entries in chapter order, with errors first
            Map<Integer, List<LogEntry>> logChapters = Stream.of(info.entries).collect(Collectors.groupingBy(LogEntry::getChapter));
            if (logChapters != null)
                for (List<LogEntry> chapter : logChapters.values()) {
                    List<LogEntry> logChapterWithErrorsFirst = Stream.of(chapter).sortBy(l -> !l.isError).toList();
                    for (LogEntry entry : logChapterWithErrorsFirst)
                        logStr.append(entry.message).append(LINE_SEPARATOR);
                }
        }

        logStr.append(info.logName).append(" log : end");

        return logStr.toString();
    }

    /**
     * Write the given log to the app's default storage location
     *
     * @param context Context to use
     * @param logInfo Log to write
     * @return DocumentFile of the created log file; null if it couldn't be created
     */
    @Nullable
    public static DocumentFile writeLog(@Nonnull Context context, @Nonnull LogInfo logInfo) {
        try {
            // Create the log
            String logFileName = logInfo.fileName;
            if (!logFileName.endsWith("_log")) logFileName += "_log";
            logFileName += ".txt";
            String log = buildLog(logInfo);

            // Save the log; use primary folder by default
            DocumentFile folder = FileHelper.getFolderFromTreeUriString(context, Preferences.getStorageUri());
            if (folder != null) {
                DocumentFile logDocumentFile = FileHelper.findOrCreateDocumentFile(context, folder, "text/plain", logFileName);
                if (logDocumentFile != null)
                    FileHelper.saveBinary(context, logDocumentFile.getUri(), log.getBytes());
                return logDocumentFile;
            } else { // If it fails, use device's "download" folder (panic mode)
                try (OutputStream newDownload = FileHelper.openNewDownloadOutputStream(HentoidApp.getInstance(), logFileName, "text/plain");) {
                    try (InputStream input = new ByteArrayInputStream(log.getBytes())) {
                        Helper.copy(input, newDownload);
                    }
                }
            }
        } catch (Exception e) {
            Timber.e(e);
        }

        return null;
    }
}
