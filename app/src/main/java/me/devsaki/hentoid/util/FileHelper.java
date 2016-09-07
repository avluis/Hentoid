package me.devsaki.hentoid.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;

import static android.os.Environment.MEDIA_MOUNTED;
import static android.os.Environment.getExternalStorageDirectory;
import static android.os.Environment.getExternalStorageState;

/**
 * Created by avluis on 08/05/2016.
 * File related utility class
 */
public class FileHelper {
    private static final String TAG = LogHelper.makeLogTag(FileHelper.class);

    private static final String AUTHORITY = "me.devsaki.hentoid.provider.FileProvider";
    private static final int KITKAT = Build.VERSION_CODES.KITKAT;
    // Note that many devices will report true (there are no guarantees of this being 'external')
    public static boolean isSDPresent = getExternalStorageState().equals(MEDIA_MOUNTED);

    public static void saveUri(Uri uri) {
        LogHelper.d(TAG, "Saving Uri: " + uri);
        SharedPreferences prefs = HentoidApp.getSharedPrefs();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(ConstsPrefs.PREF_SD_STORAGE_URI, uri.toString()).apply();
    }

    public static void clearUri() {
        SharedPreferences prefs = HentoidApp.getSharedPrefs();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(ConstsPrefs.PREF_SD_STORAGE_URI, "").apply();
    }

    static String getStringUri() {
        SharedPreferences prefs = HentoidApp.getSharedPrefs();
        return prefs.getString(ConstsPrefs.PREF_SD_STORAGE_URI, null);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static boolean isSAF() {
        return getStringUri() != null && !getStringUri().equals("");
    }

    /**
     * Determine if a file is on external sd card. (Kitkat+)
     *
     * @param file The file.
     * @return true if on external sd card.
     */
    @RequiresApi(api = KITKAT)
    public static boolean isOnExtSdCard(final File file) {
        return getExtSdCardFolder(file) != null;
    }

    /**
     * Determine the main folder of the external SD card containing the given file. (Kitkat+)
     *
     * @param file The file.
     * @return The main folder of the external SD card containing this file,
     * if the file is on an SD card. Otherwise, null is returned.
     */
    @RequiresApi(api = KITKAT)
    public static String getExtSdCardFolder(final File file) {
        String[] extSdPaths = getExtSdCardPaths();
        try {
            for (String extSdPath : extSdPaths) {
                if (file.getCanonicalPath().startsWith(extSdPath)) {
                    return extSdPath;
                }
            }
        } catch (IOException e) {
            return null;
        }

        return null;
    }

    /**
     * Get a list of external SD card paths. (Kitkat+)
     *
     * @return A list of external SD card paths.
     */
    @RequiresApi(api = KITKAT)
    public static String[] getExtSdCardPaths() {
        Context cxt = HentoidApp.getAppContext();
        List<String> paths = new ArrayList<>();
        for (File file : cxt.getExternalFilesDirs("external")) {
            if (file != null && !file.equals(cxt.getExternalFilesDir("external"))) {
                int index = file.getAbsolutePath().lastIndexOf("/Android/data");
                if (index < 0) {
                    LogHelper.w(TAG, "Unexpected external file dir: " + file.getAbsolutePath());
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

    /**
     * Check is a file is writable.
     * Detects write issues on external SD card.
     *
     * @param file The file.
     * @return true if the file is writable.
     */
    public static boolean isWritable(@NonNull final File file) {
        boolean isExisting = file.exists();

        try {
            FileOutputStream output = new FileOutputStream(file, true);
            try {
                output.close();
            } catch (IOException e) {
                // do nothing.
            }
        } catch (FileNotFoundException e) {
            return false;
        }
        boolean result = file.canWrite();

        // Ensure that file is not created during this process.
        if (!isExisting) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }

        return result;
    }

    /**
     * Checks if file could be read or created
     *
     * @param file - The file.
     * @return true if file's is writable.
     */
    public static boolean isReadable(@NonNull final String file) {
        return isReadable(new File(file));
    }

    /**
     * Checks if file could be read or created
     *
     * @param file - The file.
     * @return true if file's is writable.
     */
    private static boolean isReadable(@NonNull final File file) {
        if (!file.isFile()) {
            LogHelper.e(TAG, "isReadable(): Not a File");

            return false;
        }

        return file.exists() && file.canRead();
    }

    /**
     * Get OutputStream from file.
     *
     * @param target The file.
     * @return FileOutputStream.
     */
    public static OutputStream getOutputStream(@NonNull final File target) {
        if (isWritable(target)) {
            if (!target.exists()) {
                if (target.isFile()) {
                    try {
                        FileUtil.mkFile(target);
                    } catch (IOException e) {
                        LogHelper.d(TAG, "Unable to create file!", e);
                    }
                } else if (target.isDirectory()) {
                    LogHelper.e(TAG, "Invalid target -- must be a file.");
                }

                return FileUtil.getOutputStream(target);
            } else {
                return FileUtil.getOutputStream(target);
            }
        } else {
            // Attempt anyways
            return FileUtil.getOutputStream(target);
        }
    }

    /**
     * Create a folder.
     *
     * @param file The folder to be created.
     * @return true if creation was successful.
     */
    public static boolean createDirectory(@NonNull File file) {
        return FileUtil.mkDir(file);
    }

    /**
     * Delete files in a target directory.
     *
     * @param target The folder.
     * @return true if cleaned successfully.
     */
    public static boolean cleanDirectory(@NonNull File target) {
        // Delete directory -- create directory
        return FileUtil.rmDir(target) && FileUtil.mkDir(target);
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
        if (!file.exists() && !file.isDirectory() && !FileUtil.mkDir(file)) {
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
                boolean deleted = FileUtil.deleteFile(nomedia);
                if (deleted) {
                    LogHelper.d(TAG, ".nomedia file deleted");
                }
            }
            // Re-create nomedia file to confirm write permissions
            hasPermission = FileUtil.mkFile(nomedia);
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

    public static boolean createNoMedia() {
        SharedPreferences prefs = HentoidApp.getSharedPrefs();
        String settingDir = prefs.getString(Consts.SETTINGS_FOLDER, "");

        try {
            if (FileUtil.mkFile(new File(settingDir, ".nomedia"))) {
                Helper.toast(R.string.nomedia_file_created);
            } else {
                LogHelper.d(TAG, ".nomedia file already exists.");
            }
        } catch (IOException io) {
            LogHelper.e("Failed to create file: " + io);
            return false;
        }

        return true;
    }

    // Run method in background thread
    public static void removeContent(Context cxt, Content content) {
        File dir = getContentDownloadDir(cxt, content);
        if (FileUtil.rmDir(dir)) {
            LogHelper.d(TAG, "Directory " + dir + " removed.");
        } else {
            LogHelper.d(TAG, "Failed to delete directory: " + dir);
        }
    }

    public static File getContentDownloadDir(Context cxt, Content content) {
        File file;
        SharedPreferences sp = HentoidApp.getSharedPrefs();
        String settingDir = sp.getString(Consts.SETTINGS_FOLDER, "");
        String folderDir = content.getSite().getFolder() + content.getUniqueSiteId();

        if (settingDir.isEmpty()) {
            return getDefaultDir(cxt, folderDir);
        }

        file = new File(settingDir, folderDir);
        if (!file.exists() && !FileUtil.mkDir(file)) {
            file = new File(settingDir + folderDir);
            if (!file.exists()) {
                FileUtil.mkDir(file);
            }
        }

        return file;
    }

    public static File getDefaultDir(Context cxt, String dir) {
        File file;
        try {
            file = new File(getExternalStorageDirectory() + "/"
                    + Consts.DEFAULT_LOCAL_DIRECTORY + "/" + dir);
        } catch (Exception e) {
            file = cxt.getDir("", Context.MODE_PRIVATE);
            file = new File(file, "/" + Consts.DEFAULT_LOCAL_DIRECTORY);
        }

        if (!file.exists() && !FileUtil.mkDir(file)) {
            file = cxt.getDir("", Context.MODE_PRIVATE);
            file = new File(file, "/" + Consts.DEFAULT_LOCAL_DIRECTORY + "/" + dir);
            if (!file.exists()) {
                FileUtil.mkDir(file);
            }
        }

        return file;
    }

    public static File getSiteDownloadDir(Context cxt, Site site) {
        File file;
        SharedPreferences sp = HentoidApp.getSharedPrefs();
        String settingDir = sp.getString(Consts.SETTINGS_FOLDER, "");
        String folderDir = site.getFolder();
        if (settingDir.isEmpty()) {
            return getDefaultDir(cxt, folderDir);
        }
        file = new File(settingDir, folderDir);
        if (!file.exists() && !FileUtil.mkDir(file)) {
            file = new File(settingDir + folderDir);
            if (!file.exists()) {
                FileUtil.mkDir(file);
            }
        }

        return file;
    }

    // Method is used by onBindViewHolder(), speed is key
    public static String getThumb(Context cxt, Content content) {
        File dir = getContentDownloadDir(cxt, content);
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
                        pathname -> {
                            return pathname.getName().contains("thumb");
                        }
                );
                thumb = fileList.length > 0 ? fileList[0].getAbsolutePath() : coverUrl;
                break;
        }

        return thumb;
    }

    public static void openContent(final Context cxt, Content content) {
        LogHelper.d(TAG, "Opening: " + content.getTitle() + " from: " +
                getContentDownloadDir(cxt, content));
        SharedPreferences sp = HentoidApp.getSharedPrefs();
        File dir = getContentDownloadDir(cxt, content);
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
            String message = cxt.getString(R.string.image_file_not_found)
                    .replace("@dir", dir.getAbsolutePath());
            Helper.toast(cxt, message);
        } else {
            int readContentPreference = Integer.parseInt(
                    sp.getString(
                            ConstsPrefs.PREF_READ_CONTENT_LISTS,
                            ConstsPrefs.PREF_READ_CONTENT_DEFAULT + ""));
            if (readContentPreference == ConstsPrefs.PREF_READ_CONTENT_ASK) {
                final File file = imageFile;
                AlertDialog.Builder builder = new AlertDialog.Builder(cxt);
                builder.setMessage(R.string.select_the_action)
                        .setPositiveButton(R.string.open_default_image_viewer,
                                (dialog, id) -> openFile(cxt, file))
                        .setNegativeButton(R.string.open_perfect_viewer,
                                (dialog, id) -> openPerfectViewer(cxt, file)).create().show();
            } else if (readContentPreference == ConstsPrefs.PREF_READ_CONTENT_PERFECT_VIEWER) {
                openPerfectViewer(cxt, imageFile);
            }
        }
    }

    private static void openFile(Context cxt, File aFile) {
        Intent myIntent = new Intent(Intent.ACTION_VIEW);
        File file = new File(aFile.getAbsolutePath());
        String extension = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(file).toString());
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        myIntent.setDataAndType(Uri.fromFile(file), mimeType);
        cxt.startActivity(myIntent);
    }

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

    public static String getRoot() {
        SharedPreferences prefs = HentoidApp.getSharedPrefs();
        return prefs.getString(Consts.SETTINGS_FOLDER, "");
    }
}
