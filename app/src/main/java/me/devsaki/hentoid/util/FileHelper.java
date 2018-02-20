package me.devsaki.hentoid.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import timber.log.Timber;

import static android.os.Environment.MEDIA_MOUNTED;
import static android.os.Environment.getExternalStorageDirectory;
import static android.os.Environment.getExternalStorageState;

/**
 * Created by avluis on 08/05/2016.
 * File related utility class
 */
public class FileHelper {
    // Note that many devices will report true (there are no guarantees of this being 'external')
    public static final boolean isSDPresent = getExternalStorageState().equals(MEDIA_MOUNTED);

    private static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".provider.FileProvider";

    public static void saveUri(Uri uri) {
        Timber.d("Saving Uri: %s", uri);
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

    public static boolean isSAF() {
        return getStringUri() != null && !getStringUri().equals("");
    }

    /**
     * Determine if a file is on external sd card. (Kitkat+)
     *
     * @param file The file.
     * @return true if on external sd card.
     */
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
    public static String[] getExtSdCardPaths() {
        Context cxt = HentoidApp.getAppContext();
        List<String> paths = new ArrayList<>();
        for (File file : ContextCompat.getExternalFilesDirs(cxt, "external")) {
            if (file != null && !file.equals(cxt.getExternalFilesDir("external"))) {
                int index = file.getAbsolutePath().lastIndexOf("/Android/data");
                if (index < 0) {
                    Timber.w("Unexpected external file dir: %s", file.getAbsolutePath());
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
            if (!file.isDirectory()) {
                return false;
            }
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
     * @param file - The file (as a String).
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
    public static boolean isReadable(@NonNull final File file) {
        if (!file.isFile()) {
            Timber.d("isReadable(): Not a File");

            return false;
        }

        return file.exists() && file.canRead();
    }

    /**
     * Method ensures file creation from stream.
     *
     * @param stream - OutputStream
     * @return true if all OK.
     */
    public static boolean sync(@NonNull final OutputStream stream) {
        return (stream instanceof FileOutputStream) && FileUtil.sync((FileOutputStream) stream);
    }

    /**
     * Get OutputStream from file.
     *
     * @param target The file.
     * @return FileOutputStream.
     */
    public static OutputStream getOutputStream(@NonNull final File target) {
        return FileUtil.getOutputStream(target);
    }

    /**
     * Create a file.
     *
     * @param file The file to be created.
     * @return true if creation was successful.
     */
    public static boolean createFile(@NonNull File file) throws IOException {
        return FileUtil.makeFile(file);
    }

    /**
     * Create a folder.
     *
     * @param file The folder to be created.
     * @return true if creation was successful.
     */
    public static boolean createDirectory(@NonNull File file) {
        return FileUtil.makeDir(file);
    }

    /**
     * Delete a file.
     *
     * @param target The file.
     * @return true if deleted successfully.
     */
    public static boolean removeFile(File target) {
        return FileUtil.deleteFile(target);
    }

    /**
     * Delete files in a target directory.
     *
     * @param target The folder.
     * @return true if cleaned successfully.
     */
    public static boolean cleanDirectory(@NonNull File target) {
        // Delete directory -- create directory
        return FileUtil.deleteDir(target) && FileUtil.makeDir(target);
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
        if (!file.exists() && !file.isDirectory() && !FileUtil.makeDir(file)) {
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
                    Timber.d(".nomedia file deleted");
                }
            }
            // Re-create nomedia file to confirm write permissions
            hasPermission = FileUtil.makeFile(nomedia);
        } catch (IOException e) {
            hasPermission = false;
            Timber.e(e, "We couldn't confirm write permissions to this location: ");
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
        String settingDir = getRoot();
        File noMedia = new File(settingDir, ".nomedia");

        try {
            if (FileUtil.makeFile(noMedia)) {
                Helper.toast(R.string.nomedia_file_created);
            } else {
                Timber.d(".nomedia file already exists.");
            }
        } catch (IOException io) {
            if (!isReadable(noMedia)) {
                Timber.e(io, "Failed to create file.");
                Helper.toast(R.string.error_creating_nomedia_file);

                return false;
            } else {
                Helper.toast(R.string.nomedia_file_created);
            }
        }

        return true;
    }

    // Run method in background thread
    public static void removeContent(Context cxt, Content content) {
        //File dir = getContentDownloadDir(cxt, content);
        String settingDir = getRoot();
        File dir = new File(settingDir, content.getStorageFolder());

        if (FileUtil.deleteDir(dir)) {
            Timber.d("Directory %s removed.", dir);
        } else {
            Timber.d("Failed to delete directory: %s", dir);
        }
    }

    public static File getContentDownloadDir(Context cxt, Content content) {
        File file;
        String folderDir = content.getSite().getFolder();
        SharedPreferences sp = HentoidApp.getSharedPrefs();

        int folderNamingPreference = Integer.parseInt(
                sp.getString(
                        ConstsPrefs.PREF_FOLDER_NAMING_CONTENT_LISTS,
                        ConstsPrefs.PREF_FOLDER_NAMING_CONTENT_DEFAULT + ""));

        if (folderNamingPreference == ConstsPrefs.PREF_FOLDER_NAMING_CONTENT_AUTH_TITLE_ID) {
            folderDir = folderDir + content.getAuthor().replaceAll("[^a-zA-Z0-9.-]", "_") + " - ";
        }
        if (folderNamingPreference == ConstsPrefs.PREF_FOLDER_NAMING_CONTENT_AUTH_TITLE_ID || folderNamingPreference == ConstsPrefs.PREF_FOLDER_NAMING_CONTENT_TITLE_ID) {
            folderDir = folderDir + content.getTitle().replaceAll("[^a-zA-Z0-9.-]", "_") + " - ";
        }
        folderDir = folderDir +"["+content.getUniqueSiteId()+"]";

        String settingDir = getRoot();
        if (settingDir.isEmpty()) {
            return getDefaultDir(cxt, folderDir);
        }

        Timber.d("New book directory %s in %s", folderDir, settingDir);

        file = new File(settingDir, folderDir);
        if (!file.exists() && !FileUtil.makeDir(file)) {
            file = new File(settingDir + folderDir);
            if (!file.exists()) {
                FileUtil.makeDir(file);
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

        if (!file.exists() && !FileUtil.makeDir(file)) {
            file = cxt.getDir("", Context.MODE_PRIVATE);
            file = new File(file, "/" + Consts.DEFAULT_LOCAL_DIRECTORY + "/" + dir);
            if (!file.exists()) {
                FileUtil.makeDir(file);
            }
        }

        return file;
    }

    public static File getSiteDownloadDir(Context cxt, Site site) {
        File file;
        String settingDir = getRoot();
        String folderDir = site.getFolder();
        if (settingDir.isEmpty()) {
            return getDefaultDir(cxt, folderDir);
        }
        file = new File(settingDir, folderDir);
        if (!file.exists() && !FileUtil.makeDir(file)) {
            file = new File(settingDir + folderDir);
            if (!file.exists()) {
                FileUtil.makeDir(file);
            }
        }

        return file;
    }

    // Method is used by onBindViewHolder(), speed is key
    public static String getThumb(Context cxt, Content content) {
//        File dir = getContentDownloadDir(cxt, content);
        String settingDir = getRoot();
        File dir = new File(settingDir, content.getStorageFolder());

        String coverUrl = content.getCoverImageUrl();

        if (isSAF() && getExtSdCardFolder(new File(getRoot())) == null) {
            Timber.d("File not found!! Returning online resource.");
            return coverUrl;
        }

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
                        pathname -> pathname.getName().contains("thumb")
                );
                thumb = (fileList != null && fileList.length > 0) ? fileList[0].getAbsolutePath() : coverUrl;
                break;
        }

        return thumb;
    }

    public static void openContent(final Context cxt, Content content) {
        Timber.d("Opening: %s from: %s", content.getTitle(), content.getStorageFolder());
        //        File dir = getContentDownloadDir(cxt, content);
        String settingDir = getRoot();
        File dir = new File(settingDir, content.getStorageFolder());


        Timber.d("Opening: " + content.getTitle() + " from: " + dir);

        if (isSAF() && getExtSdCardFolder(new File(getRoot())) == null) {
            Timber.d("File not found!! Exiting method.");
            Helper.toast(R.string.sd_access_error);
            return;
        }

        Helper.toast("Opening: " + content.getTitle());
        SharedPreferences sp = HentoidApp.getSharedPrefs();

        File imageFile = null;
        File[] files = dir.listFiles();
        if (files != null && files.length > 0)
        {
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
            if (readContentPreference == ConstsPrefs.PREF_READ_CONTENT_DEFAULT) {
                openFile(cxt, imageFile);
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

    public static void archiveContent(final Context cxt, Content content) {
        Timber.d("Building file list for: %s", content.getTitle());
        // Build list of files

        //File dir = getContentDownloadDir(cxt, content);
        String settingDir = getRoot();
        File dir = new File(settingDir, content.getStorageFolder());

        File[] files = dir.listFiles();
        Arrays.sort(files);
        ArrayList<File> fileList = new ArrayList<>();
        for (File file : files) {
            String filename = file.getName();
            if (filename.endsWith(".json") || filename.contains("thumb")) {
                break;
            }
            fileList.add(file);
        }

        // Create folder to share from
        File sharedDir = new File(cxt.getExternalCacheDir() + "/shared");
        if (FileUtil.makeDir(sharedDir)) {
            Timber.d("Shared folder created.");
        }

        // Clean directory (in case of previous job)
        if (cleanDirectory(sharedDir)) {
            Timber.d("Shared folder cleaned up.");
        }

        // Build destination file
        File dest = new File(cxt.getExternalCacheDir() + "/shared/%s",
                content.getTitle()
                        .replaceAll("[\\?\\\\/:|<>\\*]", " ")  //filter ? \ / : | < > *
                        .replaceAll("\\s+", "_")  // white space as underscores
                + ".zip");
        Timber.d("Destination file: %s", dest);

        // Convert ArrayList to Array
        File[] fileArray = fileList.toArray(new File[fileList.size()]);
        // Compress files
        new AsyncUnzip(cxt, dest).execute(fileArray, dest);
    }

    public static String getRoot() {
        SharedPreferences prefs = HentoidApp.getSharedPrefs();
        return prefs.getString(Consts.SETTINGS_FOLDER, "");
    }

    private static class AsyncUnzip extends ZipUtil.ZipTask {
        final Context cxt;
        final File dest;

        AsyncUnzip(Context cxt, File dest) {
            this.cxt = cxt;
            this.dest = dest;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            // Hentoid is FileProvider ready!!
            sendIntent.putExtra(Intent.EXTRA_STREAM,
                    FileProvider.getUriForFile(cxt, AUTHORITY, dest));
            sendIntent.setType(MimeTypes.getMimeType(dest));

            cxt.startActivity(sendIntent);
        }
    }
}
