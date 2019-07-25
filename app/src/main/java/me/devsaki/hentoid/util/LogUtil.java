package me.devsaki.hentoid.util;

import android.content.Context;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.List;

import javax.annotation.Nonnull;

import timber.log.Timber;

public class LogUtil {

    public static class LogInfo {
        public String fileName;
        public String logName;
        public String noDataMessage;
    }

    @Nullable
    public static File writeLog(@Nonnull Context context, @Nonnull List<String> log, @Nonnull LogInfo info) {
        // Create the log
        StringBuilder logStr = new StringBuilder();
        logStr.append(info.logName).append(" log : begin").append(System.getProperty("line.separator"));
        if (log.isEmpty())
            logStr.append("No activity to report - ").append(info.noDataMessage);
        else
            for (String line : log)
                logStr.append(line).append(System.getProperty("line.separator"));
        logStr.append(info.logName).append(" log : end");

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
            FileHelper.saveBinaryInFile(cleanupLogFile, logStr.toString().getBytes());
            return cleanupLogFile;
        } catch (Exception e) {
            Timber.e(e);
        }

        return null;
    }
}
