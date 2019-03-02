package me.devsaki.hentoid.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;

import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.ObjectBoxDB;
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

    private static final String AUTHORIZED_CHARS = "[^a-zA-Z0-9.-]";

    private static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".provider.FileProvider";

    public static void saveUri(Uri uri) {
        Timber.d("Saving Uri: %s", uri);
        Preferences.setSdStorageUri(uri.toString());
    }

    public static void clearUri() {
        Preferences.setSdStorageUri("");
    }

    public static boolean isSAF() {
        return !Preferences.getSdStorageUri().isEmpty();
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

        return paths.toArray(new String[0]);
    }

    /**
     * Check if a file or directory is writable.
     * Detects write issues on external SD card.
     *
     * @param file The file or directory.
     * @return true if the file or directory is writable.
     */
    public static boolean isWritable(@NonNull final File file) {

        if (file.isDirectory()) return isDirectoryWritable(file);
        else return isFileWritable(file);
    }

    /**
     * Check if a directory is writable.
     * Detects write issues on external SD card.
     *
     * @param file The directory.
     * @return true if the directory is writable.
     */
    private static boolean isDirectoryWritable(@NonNull final File file) {
        File testFile = new File(file, "test.txt");

        boolean hasPermission = false;

        try {
            hasPermission = FileUtil.makeFile(testFile);
            if (hasPermission)
                try (OutputStream output = FileHelper.getOutputStream(testFile)) {
                    output.write("test".getBytes());
                    sync(output);
                    output.flush();
                } catch (NullPointerException npe) {
                    Timber.e(npe, "Invalid Stream");
                    hasPermission = false;
                } catch (IOException e) {
                    Timber.e(e, "IOException while checking permissions on %s", file.getAbsolutePath());
                    hasPermission = false;
                }
        } finally {
            if (testFile.exists()) {
                removeFile(testFile);
            }
        }
        return hasPermission;
    }

    /**
     * Check if a file is writable.
     * Detects write issues on external SD card.
     *
     * @param file The file.
     * @return true if the file is writable.
     */
    private static boolean isFileWritable(@NonNull final File file) {
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
    static boolean sync(@NonNull final OutputStream stream) {
        return (stream instanceof FileOutputStream) && FileUtil.sync((FileOutputStream) stream);
    }

    /**
     * Get OutputStream from file.
     *
     * @param target The file.
     * @return FileOutputStream.
     */
    static OutputStream getOutputStream(@NonNull final File target) throws IOException {
        return FileUtil.getOutputStream(target);
    }

    static InputStream getInputStream(@NonNull final File target) throws IOException {
        return FileUtil.getInputStream(target);
    }

    /**
     * Create a folder.
     *
     * @param file The folder to be created.
     * @return true if creation was successful or the folder already exists
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
            return tryCleanDirectory(target);
        } catch (Exception e) {
            Timber.e(e, "Failed to clean directory");
            return false;
        }
    }

    /**
     * Cleans a directory without deleting it.
     * <p>
     * Custom substitute for commons.io.FileUtils.cleanDirectory that supports devices without File.toPath
     *
     * @param directory directory to clean
     * @return true if directory has been successfully cleaned
     * @throws IOException              in case cleaning is unsuccessful
     * @throws IllegalArgumentException if {@code directory} does not exist or is not a directory
     */
    private static boolean tryCleanDirectory(@NonNull File directory) throws IOException, SecurityException {
        File[] files = directory.listFiles();
        if (files == null) throw new IOException("Failed to list content of " + directory);

        boolean isSuccess = true;

        for (File file : files) {
            if (file.isDirectory() && !tryCleanDirectory(file)) isSuccess = false;
            if (!file.delete() && file.exists()) isSuccess = false;
        }

        return isSuccess;
    }

    public static boolean checkAndSetRootFolder(String folder) {
        return checkAndSetRootFolder(folder, false);
    }

    public static boolean checkAndSetRootFolder(String folder, boolean notify) {
        Context context = HentoidApp.getAppContext();

        // Validate folder
        File file = new File(folder);
        if (!file.exists() && !file.isDirectory() && !FileUtil.makeDir(file)) {
            if (notify) {
                ToastUtil.toast(context, R.string.error_creating_folder);
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
                ToastUtil.toast(context, R.string.error_write_permission);
            }
            return false;
        }

        boolean directorySaved = Preferences.setRootFolderName(folder);
        if (!directorySaved) {
            if (notify) {
                ToastUtil.toast(context, R.string.error_creating_folder);
            }
            return false;
        }

        return true;
    }

    /**
     * Create the ".nomedia" file in the app's root folder
     */
    public static void createNoMedia() {
        String settingDir = Preferences.getRootFolderName();
        File noMedia = new File(settingDir, ".nomedia");

        if (FileUtil.makeFile(noMedia)) {
            ToastUtil.toast(R.string.nomedia_file_created);
        } else {
            Timber.d(".nomedia file already exists.");
        }
    }

    @WorkerThread
    public static void removeContent(Content content) {
        // If the book has just starting being downloaded and there are no complete pictures on memory yet, it has no storage folder => nothing to delete
        if (content.getStorageFolder().length() > 0) {
            String settingDir = Preferences.getRootFolderName();
            File dir = new File(settingDir, content.getStorageFolder());

            if (deleteQuietly(dir) || FileUtil.deleteWithSAF(dir)) {
                Timber.i("Directory %s removed.", dir);
            } else {
                Timber.w("Failed to delete directory: %s", dir);
            }
        }
    }

    /**
     * Create the download directory of the given content
     *
     * @param context Context
     * @param content Content for which the directory to create
     * @return Created directory
     */
    public static File createContentDownloadDir(Context context, Content content) {
        String folderDir = formatDirPath(content);

        String settingDir = Preferences.getRootFolderName();
        if (settingDir.isEmpty()) {
            settingDir = getDefaultDir(context, folderDir).getAbsolutePath();
        }

        Timber.d("New book directory %s in %s", folderDir, settingDir);

        File file = new File(settingDir, folderDir);
        if (!file.exists() && !FileUtil.makeDir(file)) {
            file = new File(settingDir + folderDir);
            if (!file.exists()) {
                FileUtil.makeDir(file);
            }
        }

        return file;
    }

    /**
     * Format the download directory path of the given content according to current user preferences
     *
     * @param content Content to get the path from
     * @return Canonical download directory path of the given content, according to current user preferences
     */
    public static String formatDirPath(Content content) {
        String siteFolder = content.getSite().getFolder();
        String result = siteFolder;
        int folderNamingPreference = Preferences.getFolderNameFormat();

        if (folderNamingPreference == Preferences.Constant.PREF_FOLDER_NAMING_CONTENT_AUTH_TITLE_ID) {
            result = result + content.getAuthor().replaceAll(AUTHORIZED_CHARS, "_") + " - ";
        }
        if (folderNamingPreference == Preferences.Constant.PREF_FOLDER_NAMING_CONTENT_AUTH_TITLE_ID || folderNamingPreference == Preferences.Constant.PREF_FOLDER_NAMING_CONTENT_TITLE_ID) {
            result = result + content.getTitle().replaceAll(AUTHORIZED_CHARS, "_") + " - ";
        }
        result = result + "[" + content.getUniqueSiteId() + "]";

        // Truncate folder dir to something manageable for Windows
        // If we are to assume NTFS and Windows, then the fully qualified file, with it's drivename, path, filename, and extension, altogether is limited to 260 characters.
        int truncLength = Preferences.getFolderTruncationNbChars();
        if (truncLength > 0) {
            if (result.length() - siteFolder.length() > truncLength)
                result = result.substring(0, siteFolder.length() + truncLength - 1);
        }

        return result;
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
     * Recursively search for files of a given type from a base directory
     *
     * @param workingDir the base directory
     * @return list containing all files with matching extension
     */
    public static List<File> findFilesRecursively(File workingDir, String extension) {
        return findFilesRecursively(workingDir, extension, 0);
    }
    private static List<File> findFilesRecursively(File workingDir, String extension, int depth) {
        List<File> files = new ArrayList<>();
        File[] baseDirs = workingDir.listFiles(pathname -> (pathname.isDirectory() || pathname.getName().toLowerCase().endsWith(extension)));

        for (File entry : baseDirs) {
            if (entry.isDirectory()) {
                if (depth < 6) files.addAll(findFilesRecursively(entry, extension, depth + 1)); // Hard recursive limit to avoid catastrophes
            } else {
                files.add(entry);
            }
        }
        return files;
    }

    /**
     * Method is used by onBindViewHolder(), speed is key
     */
    public static String getThumb(Content content) {
        String coverUrl = content.getCoverImageUrl();

        // If trying to access a non-downloaded book cover (e.g. viewing the download queue)
        if (content.getStorageFolder().equals("")) return coverUrl;

        String extension = getExtension(coverUrl);
        // Some URLs do not link the image itself (e.g Tsumino) => jpg by default
        // NB : ideal would be to get the content-type of the resource behind coverUrl, but that's too time-consuming
        if (extension.isEmpty() || extension.contains("/")) extension = "jpg";

        File f = new File(Preferences.getRootFolderName(), content.getStorageFolder() + "/thumb." + extension);
        return f.exists() ? f.getAbsolutePath() : coverUrl;
    }

    /**
     * Open the given content using the viewer defined in user preferences
     *
     * @param context Context
     * @param content Content to be opened
     */
    public static void openContent(final Context context, Content content) {
        Timber.d("Opening: %s from: %s", content.getTitle(), content.getStorageFolder());

        String rootFolderName = Preferences.getRootFolderName();
        File dir = new File(rootFolderName, content.getStorageFolder());

        Timber.d("Opening: %s from: %s", content.getTitle(), dir);
        if (isSAF() && getExtSdCardFolder(new File(rootFolderName)) == null) {
            Timber.d("File not found!! Exiting method.");
            ToastUtil.toast(R.string.sd_access_error);
            return;
        }

        ToastUtil.toast("Opening: " + content.getTitle());

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
            ToastUtil.toast(context, message);
        } else {
            int readContentPreference = Preferences.getContentReadAction();
            if (readContentPreference == Preferences.Constant.PREF_READ_CONTENT_DEFAULT) {
                openFile(context, imageFile);
            } else if (readContentPreference == Preferences.Constant.PREF_READ_CONTENT_PERFECT_VIEWER) {
                openPerfectViewer(context, imageFile);
            }
        }

        // TODO - properly dispose this Completable (for best practices' sake, even though it hasn't triggered any leak so far)
        Completable.fromRunnable(() -> updateContentReads(context, content.getId(), dir))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe();
    }

    private static void updateContentReads(Context context, long contentId, File dir) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        Content content = db.selectContentById(contentId);
        if (content != null) {
            content.increaseReads().setLastReadDate(new Date().getTime());
            db.updateContentReads(content);

            try {
                JsonHelper.saveJson(content.preJSONExport(), dir);
            } catch (IOException e) {
                Timber.e(e, "Error while writing to %s", dir.getAbsolutePath());
            }
        }
    }

    /**
     * Open the given file using the device's app(s) of choice
     *
     * @param context Context
     * @param aFile   File to be opened
     */
    public static void openFile(Context context, File aFile) {
        Intent myIntent = new Intent(Intent.ACTION_VIEW);
        File file = new File(aFile.getAbsolutePath());
        String extension = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(file).toString());
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        myIntent.setDataAndType(Uri.fromFile(file), mimeType);
        try {
            context.startActivity(myIntent);
        } catch (ActivityNotFoundException e) {
            Timber.e(e, "Activity not found to open %s", aFile.getAbsolutePath());
            ToastUtil.toast(context, R.string.error_open, Toast.LENGTH_LONG);
        }
    }

    /**
     * Open PerfectViewer telling it to display the given image
     *
     * @param context    Context
     * @param firstImage Image to be displayed
     */
    private static void openPerfectViewer(Context context, File firstImage) {
        try {
            Intent intent = context
                    .getPackageManager()
                    .getLaunchIntentForPackage("com.rookiestudio.perfectviewer");
            if (intent != null) {
                intent.setAction(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.fromFile(firstImage), "image/*");
                context.startActivity(intent);
            }
        } catch (Exception e) {
            ToastUtil.toast(context, R.string.error_open_perfect_viewer);
        }
    }

    /**
     * Returns the extension of the given filename
     *
     * @param fileName Filename
     * @return Extension of the given filename
     */
    public static String getExtension(String fileName) {
        return fileName.contains(".") ? fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase(Locale.getDefault()) : "";
    }

    public static void archiveContent(final Context context, Content content) {
        Timber.d("Building file list for: %s", content.getTitle());
        // Build list of files

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
            File dest = new File(context.getExternalCacheDir() + "/shared",
                    content.getTitle().replaceAll(AUTHORIZED_CHARS, "_") + ".zip");
            Timber.d("Destination file: %s", dest);

            // Convert ArrayList to Array
            File[] fileArray = fileList.toArray(new File[0]);
            // Compress files
            new AsyncUnzip(context, dest).execute(fileArray, dest);
        }
    }

    /**
     * Save the given binary content in the given file
     *
     * @param file          File to save content on
     * @param binaryContent Content to save
     * @throws IOException If any IOException occurs
     */
    public static void saveBinaryInFile(File file, byte[] binaryContent) throws IOException {
        byte buffer[] = new byte[1024];
        int count;

        try (InputStream input = new ByteArrayInputStream(binaryContent)) {
            try (BufferedOutputStream output = new BufferedOutputStream(FileHelper.getOutputStream(file))) {

                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }

                output.flush();
            }
        }
    }

    /**
     * Deletes a file, never throwing an exception. If file is a directory, delete it and all sub-directories.
     * <p>
     * The difference between File.delete() and this method are:
     * <ul>
     * <li>A directory to be deleted does not have to be empty.</li>
     * <li>No exceptions are thrown when a file or directory cannot be deleted.</li>
     * </ul>
     * <p>
     * Custom substitute for commons.io.FileUtils.deleteQuietly that works with devices that doesn't support File.toPath
     *
     * @param file file or directory to delete, can be {@code null}
     * @return {@code true} if the file or directory was deleted, otherwise
     * {@code false}
     */
    private static boolean deleteQuietly(final File file) {
        if (file == null) {
            return false;
        }
        try {
            if (file.isDirectory()) {
                tryCleanDirectory(file);
            }
        } catch (final Exception ignored) {
        }

        try {
            return file.delete();
        } catch (final Exception ignored) {
            return false;
        }
    }

    public static boolean renameDirectory(File srcDir, File destDir) {
        try {
            FileUtils.moveDirectory(srcDir, destDir);
            return true;
        } catch (IOException e) {
            return FileUtil.renameWithSAF(srcDir, destDir.getName());
        } catch (Exception e) {
            Timber.e(e);
        }
        return false;
    }

    private static class AsyncUnzip extends ZipUtil.ZipTask {
        final Context context; // TODO - omg leak !
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

    // Please keep that, I need some way to trace actions when working with SD card features - Robb
    public static void createFileWithMsg(@Nonnull String file, String msg) {
        try {
            FileHelper.saveBinaryInFile(new File(getDefaultDir(HentoidApp.getAppContext(), ""), file + ".txt"), (null == msg) ? "NULL".getBytes() : msg.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
