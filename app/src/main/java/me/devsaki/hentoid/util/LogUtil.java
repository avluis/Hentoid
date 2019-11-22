package me.devsaki.hentoid.util;

import android.content.Context;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.BuildConfig;
import timber.log.Timber;

public class LogUtil {

    private LogUtil() {
        throw new IllegalStateException("Utility class");
    }

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    public static class LogInfo {
        private String fileName;
        private String logName;
        private String noDataMessage;
        private List<String> log = Collections.emptyList();

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public void setLogName(String logName) {
            this.logName = logName;
        }

        public void setNoDataMessage(String noDataMessage) {
            this.noDataMessage = noDataMessage;
        }

        public void setLog(List<String> log) {
            this.log = log;
        }
    }

    public static String buildLog(@Nonnull LogInfo info) {
        StringBuilder logStr = new StringBuilder();
        logStr.append(info.logName).append(" log : begin").append(LINE_SEPARATOR);
        logStr.append(String.format("Hentoid ver: %s (%s)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)).append(LINE_SEPARATOR);
        if (info.log.isEmpty())
            logStr.append("No activity to report - ").append(info.noDataMessage);
        else
            for (String line : info.log)
                logStr.append(line).append(LINE_SEPARATOR);
        logStr.append(info.logName).append(" log : end");

        return logStr.toString();
    }

    @Nullable
    public static File writeLog(@Nonnull Context context, @Nonnull LogInfo info) {
        // Create the log
        String log = buildLog(info);

        // Save it
        File rootFolder;
        try {
            String settingDir = Preferences.getRootFolderName();
            if (!settingDir.isEmpty() && FileHelper.isWritable(new File(settingDir))) {
                rootFolder = new File(settingDir); // Use selected and output-tested location (possibly SD card)
            } else {
                rootFolder = FileHelper.getDefaultDir(context, ""); // Fallback to default location (phone memory)
            }
            File cleanupLogFile = new File(rootFolder, info.fileName + ".txt");
            FileHelper.saveBinaryInFile(cleanupLogFile, log.getBytes());
            return cleanupLogFile;
        } catch (Exception e) {
            Timber.e(e);
        }

        return null;
    }
}
