package me.devsaki.hentoid.util;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;

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
        try {
            if (createFile(settingDir, ".nomedia")) {
                Helper.toast(R.string.nomedia_file_created);
            } else {
                LogHelper.d(TAG, ".nomedia file already exists.");
            }
        } catch (IOException io) {
            LogHelper.e("Failed to create file: " + io);
        }

        return true;
    }

    // !SAF
    private static boolean createFile(String dir, String name) throws IOException {
        File file = new File(dir, name);
        if (!file.exists()) {
            boolean createFile = file.createNewFile();
            LogHelper.d(TAG, "File created successfully? " + createFile);

            return createFile;
        } else {
            LogHelper.d(TAG, "File: " + name + " already exists.");
            return true;
        }
    }

    // !SAF
    // TODO: Make helper function for non-SAF/SAF use
    public static void removeContent(Context cxt, Content content) {
        File dir = getContentDownloadDir(cxt, content);

        try {
            FileUtils.deleteDirectory(dir);
        } catch (IOException e) {
            deleteDir(dir);
        } finally {
            LogHelper.d(TAG, "Directory removed: " + dir);
        }
    }

    // !SAF
    public static File getContentDownloadDir(Context context, Content content) {
        File file;
        SharedPreferences sp = HentoidApp.getSharedPrefs();
        String settingDir = sp.getString(Consts.SETTINGS_FOLDER, "");
        String folderDir = content.getSite().getFolder() + content.getUniqueSiteId();
        if (settingDir.isEmpty()) {
            return getDefaultDir(context, folderDir);
        }

        file = new File(settingDir, folderDir);
        if (!file.exists() && !file.mkdirs()) {
            file = new File(settingDir + folderDir);
            if (!file.exists()) {
                boolean mkdirs = file.mkdirs();
                LogHelper.d(TAG, mkdirs);
            }
        }

        return file;
    }

    // !SAF
    public static File getDefaultDir(Context context, String dir) {
        File file;
        try {
            file = new File(Environment.getExternalStorageDirectory() + "/"
                    + Consts.DEFAULT_LOCAL_DIRECTORY + "/" + dir);
        } catch (Exception e) {
            file = context.getDir("", Context.MODE_PRIVATE);
            file = new File(file, "/" + Consts.DEFAULT_LOCAL_DIRECTORY);
        }

        if (!file.exists() && !file.mkdirs()) {
            file = context.getDir("", Context.MODE_PRIVATE);
            file = new File(file, "/" + Consts.DEFAULT_LOCAL_DIRECTORY + "/" + dir);
            if (!file.exists()) {
                boolean mkdirs = file.mkdirs();
                LogHelper.d(TAG, mkdirs);
            }
        }

        return file;
    }

    // !SAF
    public static File getSiteDownloadDir(Context context, Site site) {
        File file;
        SharedPreferences sp = HentoidApp.getSharedPrefs();
        String settingDir = sp.getString(Consts.SETTINGS_FOLDER, "");
        String folderDir = site.getFolder();
        if (settingDir.isEmpty()) {
            return getDefaultDir(context, folderDir);
        }
        file = new File(settingDir, folderDir);
        if (!file.exists() && !file.mkdirs()) {
            file = new File(settingDir + folderDir);
            if (!file.exists()) {
                boolean mkdirs = file.mkdirs();
                LogHelper.d(TAG, mkdirs);
            }
        }

        return file;
    }

    // !SAF
    // Method is used by onBindViewHolder(), speed is key
    public static String getThumb(Context context, Content content) {
        File dir = getContentDownloadDir(context, content);
        String coverUrl = content.getCoverImageUrl();
        String thumbExt = coverUrl.substring(coverUrl.length() - 3);
        String thumb;

        switch (thumbExt) {
            case "jpg":
            case "png":
            case "gif":
                thumb = new File(dir, "thumb" + "." + thumbExt).getAbsolutePath();
                // Some thumbs from nhentai were saved as jpg instead of png
                // Follow through to scan the directory instead
                // TODO: Rename the file instead
                if (!content.getSite().equals(Site.NHENTAI)) {
                    break;
                }
            default:
                File[] fileList = dir.listFiles(
                        new FileFilter() {
                            @Override
                            public boolean accept(File pathname) {
                                return pathname.getName().contains("thumb");
                            }
                        }
                );
                thumb = fileList.length > 0 ? fileList[0].getAbsolutePath() : coverUrl;
                break;
        }

        return thumb;
    }

    // !SAF
    public static void openContent(final Context context, Content content) {
        SharedPreferences sp = HentoidApp.getSharedPrefs();
        File dir = getContentDownloadDir(context, content);
        File imageFile = null;
        File[] files = dir.listFiles();
        Arrays.sort(files);
        for (File file : files) {
            String filename = file.getName();
            if (filename.endsWith(".jpg") ||
                    filename.endsWith(".png") ||
                    filename.endsWith(".gif")) {
                imageFile = file;
                break;
            }
        }
        if (imageFile == null) {
            String message = context.getString(
                    R.string.image_file_not_found).replace("@dir", dir.getAbsolutePath());
            Helper.toast(context, message);
        } else {
            int readContentPreference = Integer.parseInt(
                    sp.getString(
                            ConstsPrefs.PREF_READ_CONTENT_LISTS,
                            ConstsPrefs.PREF_READ_CONTENT_DEFAULT + ""));
            if (readContentPreference == ConstsPrefs.PREF_READ_CONTENT_ASK) {
                final File file = imageFile;
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setMessage(R.string.select_the_action)
                        .setPositiveButton(R.string.open_default_image_viewer,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        openFile(context, file);
                                    }
                                })
                        .setNegativeButton(R.string.open_perfect_viewer,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        openPerfectViewer(context, file);
                                    }
                                }).create().show();
            } else if (readContentPreference == ConstsPrefs.PREF_READ_CONTENT_PERFECT_VIEWER) {
                openPerfectViewer(context, imageFile);
            }
        }
    }

    // !SAF
    private static void openFile(Context context, File aFile) {
        Intent myIntent = new Intent(Intent.ACTION_VIEW);
        File file = new File(aFile.getAbsolutePath());
        String extension = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(file).toString());
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        myIntent.setDataAndType(Uri.fromFile(file), mimeType);
        context.startActivity(myIntent);
    }

    // !SAF
    private static void openPerfectViewer(Context cxt, File firstImage) {
        try {
            Intent intent = cxt
                    .getPackageManager()
                    .getLaunchIntentForPackage("com.rookiestudio.perfectviewer");
            intent.setAction(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(firstImage), "image/*");
            cxt.startActivity(intent);
        } catch (Exception e) {
            Helper.toast(cxt, R.string.error_open_perfect_viewer);
        }
    }

    // !SAF
    // Gathers list of files in a directory and deletes them
    // but only if the directory is NOT empty - it does NOT delete the target directory
    public static void cleanDir(File directory) {
        boolean isDirEmpty = isDirEmpty(directory);

        if (!isDirEmpty) {
            boolean delete = false;
            String[] children = directory.list();
            for (String child : children) {
                delete = new File(directory, child).delete();
            }
            LogHelper.d(TAG, "Directory cleaned: " + delete);
        }
    }

    // !SAF
    // Is the target directory empty or not
    private static boolean isDirEmpty(File directory) {
        if (directory.isDirectory()) {
            String[] files = directory.list();
            if (files.length == 0) {
                LogHelper.d(TAG, "Directory is empty!");
                return true;
            } else {
                LogHelper.d(TAG, "Directory is NOT empty!");
                return false;
            }
        } else {
            LogHelper.d(TAG, "This is not a directory!");
        }

        return false;
    }

    // !SAF
    // As long as there are files in a directory it will recursively delete them -
    // finally, once there are no files, it deletes the target directory
    public static boolean deleteDir(File directory) {
        if (directory.isDirectory())
            for (File child : directory.listFiles()) {
                deleteDir(child);
            }

        boolean delete = directory.delete();
        LogHelper.d(TAG, "File/directory deleted: " + delete);
        return delete;
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
