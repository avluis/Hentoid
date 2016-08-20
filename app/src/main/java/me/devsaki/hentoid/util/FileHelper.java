package me.devsaki.hentoid.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;

/**
 * Created by avluis on 08/05/2016.
 * File related utility class
 */
public class FileHelper {
    private static final String TAG = LogHelper.makeLogTag(FileHelper.class);

    /**
     * All roots for which this app has permission
     */
    private List<UriPermission> uriPermissions = new ArrayList<>();

    /**
     * Get a list of external SD card paths. (Kitkat+)
     *
     * @return A list of external SD card paths.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static String[] getExtSdCardPaths() {
        Context cxt = HentoidApp.getAppContext();
        List<String> paths = new ArrayList<>();
        for (File file : cxt.getExternalFilesDirs("external")) {
            if (file != null && !file.equals(cxt.getExternalFilesDir("external"))) {
                int index = file.getAbsolutePath().lastIndexOf("/Android/data");
                if (index < 0) {
                    Log.w(TAG, "Unexpected external file dir: " + file.getAbsolutePath());
                } else {
                    String path = file.getAbsolutePath().substring(0, index);
                    try {
                        path = new File(path).getCanonicalPath();
                    } catch (IOException e) {
                        // Keep non-canonical path.
                    }
                    paths.add(path);
                }
            }
        }

        return paths.toArray(new String[paths.size()]);
    }

    public static boolean validateFolder(String folder) {
        return validateFolder(folder, false);
    }

    public static boolean validateFolder(String folder, boolean notify) {
        Context cxt = HentoidApp.getAppContext();
        SharedPreferences prefs = HentoidApp.getSharedPrefs();
        SharedPreferences.Editor editor = prefs.edit();
        // Validate folder
        File file = new File(folder);
        if (!file.exists() && !file.isDirectory() && !file.mkdirs()) {
            if (notify) {
                Helper.toast(cxt, R.string.error_creating_folder);
            }
            return false;
        }

        File nomedia = new File(folder, ".nomedia");
        boolean hasPermission;
        // Clean up (if any) nomedia file
        try {
            if (nomedia.exists()) {
                boolean deleted = nomedia.delete();
                if (deleted) {
                    LogHelper.d(TAG, ".nomedia file deleted");
                }
            }
            // Re-create nomedia file to confirm write permissions
            hasPermission = nomedia.createNewFile();
        } catch (IOException e) {
            hasPermission = false;
            HentoidApp.getInstance().trackException(e);
            LogHelper.e(TAG, "We couldn't confirm write permissions to this location: ", e);
        }

        if (!hasPermission) {
            if (notify) {
                Helper.toast(cxt, R.string.error_write_permission);
            }
            return false;
        }

        editor.putString(Consts.SETTINGS_FOLDER, folder);

        boolean directorySaved = editor.commit();
        if (!directorySaved) {
            if (notify) {
                Helper.toast(cxt, R.string.error_creating_folder);
            }
            return false;
        }

        return true;
    }

    public static void setSharedPrefsUri(Uri uri) {
        LogHelper.d(TAG, "Saving Uri: " + uri);
        SharedPreferences prefs = HentoidApp.getSharedPrefs();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(ConstsPrefs.PREF_SD_STORAGE_URI, uri.toString()).apply();
    }

    public static void clearSharedPrefsUri() {
        SharedPreferences prefs = HentoidApp.getSharedPrefs();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(ConstsPrefs.PREF_SD_STORAGE_URI, "").apply();
    }

    public static boolean createNoMedia() {
        if (Helper.isAtLeastAPI(Build.VERSION_CODES.KITKAT)) {
            FILE_MODE mode = new FileHelper().getFileMode();

            switch (mode) {
                case SAF:
                    // TODO: Add SAF
                    return true;
                case BASIC:
                default:
                    return noMediaHelper();
            }
        } else {
            return noMediaHelper();
        }
    }

    // !SAF
    private static boolean noMediaHelper() {
        SharedPreferences prefs = HentoidApp.getSharedPrefs();
        String settingDir = prefs.getString(Consts.SETTINGS_FOLDER, "");
        File nomedia = new File(settingDir, ".nomedia");
        if (!nomedia.exists()) {
            try {
                boolean createFile = nomedia.createNewFile();
                LogHelper.d(TAG, createFile);
            } catch (IOException e) {
                Helper.toast(R.string.error_creating_nomedia_file);
                return true;
            }
        }
        Helper.toast(R.string.nomedia_file_created);
        return true;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public List<UriPermission> getRootPermissions() {
        updatePermissions();
        return Collections.unmodifiableList(uriPermissions);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void updatePermissions() {
        uriPermissions = HentoidApp.getAppContext()
                .getContentResolver().getPersistedUriPermissions();
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private FILE_MODE getFileMode() {
        LogHelper.d(TAG, "Permission list: " + (
                getRootPermissions().isEmpty() ? "empty" : getRootPermissions()));
        return getRootPermissions().isEmpty() ? FILE_MODE.BASIC : FILE_MODE.SAF;
    }

    private enum FILE_MODE {BASIC, SAF}

    public class WritePermissionException extends IOException {
        public WritePermissionException(String message) {
            super(message);
        }
    }
}
