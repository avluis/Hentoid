package me.devsaki.hentoid.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.webkit.MimeTypeMap;

import org.apache.commons.io.FileUtils;

import java.io.File;
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

    public static final String FORBIDDEN_CHARS = "[^a-zA-Z0-9.-]";

    private static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".provider.FileProvider";

    public static void saveUri(Uri uri) {
        Timber.d("Saving Uri: %s", uri);
        Preferences.setSdStorageUri(uri.toString());
    }

    public static void clearUri() {
        Preferences.setSdStorageUri("");
    }

    static String getStringUri() {
        return Preferences.getSdStorageUri();
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
        Context context = HentoidApp.getAppContext();
        List<String> paths = new ArrayList<>();
        for (File file : ContextCompat.getExternalFilesDirs(context, "external")) {
            if (file != null && !file.equals(context.getExternalFilesDir("external"))) {
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
     * Check if a file is writable.
     * Detects write issues on external SD card.
     *
     * @param file The file.
     * @return true if the file is writable.
     */
    public static boolean isWritable(@NonNull final File file) {
        if (!file.canWrite()) return false;

        // Ensure that it is indeed writable by opening an output stream
        try {
            FileOutputStream output = FileUtils.openOutputStream(file);
            output.close();
        } catch (IOException e) {
            return false;
        }

        // Ensure that file is not created during this process.
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }

        return true;
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
    static boolean cleanDirectory(@NonNull File target) {
        try {
            FileUtils.cleanDirectory(target);
            return true;
        } catch (IOException e) {
            Timber.e(e, "Failed to clean directory");
            return false;
        }
    }

    public static boolean validateFolder(String folder) {
        return validateFolder(folder, false);
    }

    public static boolean validateFolder(String folder, boolean notify) {
        Context context = HentoidApp.getAppContext();
        // Validate folder
        File file = new File(folder);
        if (!file.exists() && !file.isDirectory() && !FileUtil.makeDir(file)) {
            if (notify) {
                Helper.toast(context, R.string.error_creating_folder);
            }
            return false;
        }

        File nomedia = new File(folder, ".nomedia");
        boolean hasPermission;
        // Clean up (if any) nomedia file
        if (nomedia.exists()) {
            boolean deleted = FileUtil.deleteFile(nomedia);
            if (deleted) {
                Timber.d(".nomedia file deleted");
            }
        }
        // Re-create nomedia file to confirm write permissions
        hasPermission = FileUtil.makeFile(nomedia);

        if (!hasPermission) {
            if (notify) {
                Helper.toast(context, R.string.error_write_permission);
            }
            return false;
        }

        boolean directorySaved = Preferences.setRootFolderName(folder);
        if (!directorySaved) {
            if (notify) {
                Helper.toast(context, R.string.error_creating_folder);
            }
            return false;
        }

        return true;
    }

    public static boolean createNoMedia() {
        String settingDir = Preferences.getRootFolderName();
        File noMedia = new File(settingDir, ".nomedia");

        if (FileUtil.makeFile(noMedia)) {
            Helper.toast(R.string.nomedia_file_created);
        } else {
            Timber.d(".nomedia file already exists.");
        }

        return true;
    }

    @WorkerThread
    public static void removeContent(Content content) {
        // If the book has just starting being downloaded and there are no complete pictures on memory yet, it has no storage folder => nothing to delete
        if (content.getStorageFolder().length() > 0) {
            String settingDir = Preferences.getRootFolderName();
            File dir = new File(settingDir, content.getStorageFolder());

            if (FileUtils.deleteQuietly(dir) || FileUtil.deleteWithSAF(dir)) {
                Timber.d("Directory %s removed.", dir);
            } else {
                Timber.d("Failed to delete directory: %s", dir);
            }
        }
    }

    public static File getContentDownloadDir(Context context, Content content) {
        File file;
        String folderDir = content.getSite().getFolder();

        int folderNamingPreference = Preferences.getFolderNameFormat();

        if (folderNamingPreference == Preferences.Constant.PREF_FOLDER_NAMING_CONTENT_AUTH_TITLE_ID) {
            folderDir = folderDir + content.getAuthor().replaceAll(FORBIDDEN_CHARS, "_") + " - ";
        }
        if (folderNamingPreference == Preferences.Constant.PREF_FOLDER_NAMING_CONTENT_AUTH_TITLE_ID || folderNamingPreference == Preferences.Constant.PREF_FOLDER_NAMING_CONTENT_TITLE_ID) {
            folderDir = folderDir + content.getTitle().replaceAll(FORBIDDEN_CHARS, "_") + " - ";
        }
        folderDir = folderDir + "[" + content.getUniqueSiteId() + "]";

        String settingDir = Preferences.getRootFolderName();
        if (settingDir.isEmpty()) {
            return getDefaultDir(context, folderDir);
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

    public static File getDefaultDir(Context context, String dir) {
        File file;
        try {
            file = new File(getExternalStorageDirectory() + "/"
                    + Consts.DEFAULT_LOCAL_DIRECTORY + "/" + dir);
        } catch (Exception e) {
            file = context.getDir("", Context.MODE_PRIVATE);
            file = new File(file, "/" + Consts.DEFAULT_LOCAL_DIRECTORY);
        }

        if (!file.exists() && !FileUtil.makeDir(file)) {
            file = context.getDir("", Context.MODE_PRIVATE);
            file = new File(file, "/" + Consts.DEFAULT_LOCAL_DIRECTORY + "/" + dir);
            if (!file.exists()) {
                FileUtil.makeDir(file);
            }
        }

        return file;
    }

    public static File getSiteDownloadDir(Context context, Site site) {
        File file;
        String settingDir = Preferences.getRootFolderName();
        String folderDir = site.getFolder();
        if (settingDir.isEmpty()) {
            return getDefaultDir(context, folderDir);
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

    /**
     * Method is used by onBindViewHolder(), speed is key
     */
    public static String getThumb(Content content) {
        String settingDir = Preferences.getRootFolderName();
        Timber.d("GetThumb %s --- %s", settingDir, content.getStorageFolder());
        File dir = new File(settingDir, content.getStorageFolder());

        String coverUrl = content.getCoverImageUrl();

        String rootFolderName = Preferences.getRootFolderName();
        if (isSAF() && getExtSdCardFolder(new File(rootFolderName)) == null) {
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

    public static void openContent(final Context context, Content content) {
        Timber.d("Opening: %s from: %s", content.getTitle(), content.getStorageFolder());
        //        File dir = getContentDownloadDir(context, content);
        String rootFolderName = Preferences.getRootFolderName();
        File dir = new File(rootFolderName, content.getStorageFolder());

        Timber.d("Opening: " + content.getTitle() + " from: " + dir);
        if (isSAF() && getExtSdCardFolder(new File(rootFolderName)) == null) {
            Timber.d("File not found!! Exiting method.");
            Helper.toast(R.string.sd_access_error);
            return;
        }

        Helper.toast("Opening: " + content.getTitle());

        File imageFile = null;
        File[] files = dir.listFiles();
        if (files != null && files.length > 0) {
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
            String message = context.getString(R.string.image_file_not_found)
                    .replace("@dir", dir.getAbsolutePath());
            Helper.toast(context, message);
        } else {
            int readContentPreference = Preferences.getContentReadAction();
            if (readContentPreference == Preferences.Constant.PREF_READ_CONTENT_DEFAULT) {
                openFile(context, imageFile);
            } else if (readContentPreference == Preferences.Constant.PREF_READ_CONTENT_PERFECT_VIEWER) {
                openPerfectViewer(context, imageFile);
            }
        }
    }

    private static void openFile(Context context, File aFile) {
        Intent myIntent = new Intent(Intent.ACTION_VIEW);
        File file = new File(aFile.getAbsolutePath());
        String extension = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(file).toString());
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        myIntent.setDataAndType(Uri.fromFile(file), mimeType);
        context.startActivity(myIntent);
    }

    private static void openPerfectViewer(Context context, File firstImage) {
        try {
            Intent intent = context
                    .getPackageManager()
                    .getLaunchIntentForPackage("com.rookiestudio.perfectviewer");
            intent.setAction(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(firstImage), "image/*");
            context.startActivity(intent);
        } catch (Exception e) {
            Helper.toast(context, R.string.error_open_perfect_viewer);
        }
    }

    public static void archiveContent(final Context context, Content content) {
        Timber.d("Building file list for: %s", content.getTitle());
        // Build list of files

        //File dir = getContentDownloadDir(context, content);
        String settingDir = Preferences.getRootFolderName();
        File dir = new File(settingDir, content.getStorageFolder());

        File[] files = dir.listFiles();
        if (files != null && files.length > 0) {
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
            File sharedDir = new File(context.getExternalCacheDir() + "/shared");
            if (FileUtil.makeDir(sharedDir)) {
                Timber.d("Shared folder created.");
            }

            // Clean directory (in case of previous job)
            if (cleanDirectory(sharedDir)) {
                Timber.d("Shared folder cleaned up.");
            }

            // Build destination file
            File dest = new File(context.getExternalCacheDir() + "/shared/%s",
                    content.getTitle()
                            .replaceAll("[\\?\\\\/:|<>\\*]", " ")  //filter ? \ / : | < > *
                            .replaceAll("\\s+", "_")  // white space as underscores
                            + ".zip");
            Timber.d("Destination file: %s", dest);

            // Convert ArrayList to Array
            File[] fileArray = fileList.toArray(new File[fileList.size()]);
            // Compress files
            new AsyncUnzip(context, dest).execute(fileArray, dest);
        }
    }

    private static class AsyncUnzip extends ZipUtil.ZipTask {
        final Context context;
        final File dest;

        AsyncUnzip(Context context, File dest) {
            this.context = context;
            this.dest = dest;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            // Hentoid is FileProvider ready!!
            sendIntent.putExtra(Intent.EXTRA_STREAM,
                    FileProvider.getUriForFile(context, AUTHORITY, dest));
            sendIntent.setType(MimeTypes.getMimeType(dest));

            context.startActivity(sendIntent);
        }
    }
}
