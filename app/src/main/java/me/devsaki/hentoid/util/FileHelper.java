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
import android.support.annotation.NonNull;
import android.support.v4.provider.DocumentFile;
import android.webkit.MimeTypeMap;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
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

    private static final int KITKAT = Build.VERSION_CODES.KITKAT;
    private static final int LOLLIPOP = Build.VERSION_CODES.LOLLIPOP;

    /**
     * All roots for which this app has permission.
     */
    private List<UriPermission> uriPermissions = new ArrayList<>();

    /**
     * Determine if a file is on external sd card. (Kitkat+)
     *
     * @param file The file.
     * @return true if on external sd card.
     */
    @TargetApi(KITKAT)
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
    @TargetApi(KITKAT)
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
    @TargetApi(KITKAT)
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
     * Get a DocumentFile corresponding to the given file.
     * If the file does not exist, it is created.
     *
     * @param file        The file.
     * @param isDirectory flag indicating if the file should be a directory.
     * @return The DocumentFile.
     */
    public static DocumentFile getDocumentFile(final File file, final boolean isDirectory) {
        String baseFolder = getExtSdCardFolder(file);
        boolean originalDirectory = false;
        if (baseFolder == null) {
            return null;
        }

        String relativePath = null;
        try {
            String fullPath = file.getCanonicalPath();
            if (!baseFolder.equals(fullPath)) {
                relativePath = fullPath.substring(baseFolder.length() + 1);
            } else {
                originalDirectory = true;
            }
        } catch (IOException e) {
            return null;
        } catch (Exception f) {
            originalDirectory = true;
            //continue
        }

        String as = getUri();
        Uri treeUri = null;
        if (as != null) {
            treeUri = Uri.parse(as);
        }
        if (treeUri == null) {
            return null;
        }

        // start with root of SD card and then parse through document tree.
        Context cxt = HentoidApp.getAppContext();
        DocumentFile document = DocumentFile.fromTreeUri(cxt, treeUri);
        if (originalDirectory) {
            return document;
        }
        String[] parts = relativePath.split("/");
        for (int i = 0; i < parts.length; i++) {
            DocumentFile nextDocument = document.findFile(parts[i]);
            if (nextDocument == null) {
                if ((i < parts.length - 1) || isDirectory) {
                    nextDocument = document.createDirectory(parts[i]);
                } else {
                    nextDocument = document.createFile("image", parts[i]);
                }
            }
            document = nextDocument;
        }

        return document;
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
     * Copy a file.
     *
     * @param source The source file.
     * @param target The target file.
     * @return true if copying was successful.
     */
    public static boolean copyFile(final File source, final File target) {
        FileInputStream inStream = null;
        OutputStream outStream = null;
        FileChannel inChannel = null;
        FileChannel outChannel = null;
        try {
            inStream = new FileInputStream(source);
            // First try the normal way
            if (isWritable(target)) {
                // standard way
                outStream = new FileOutputStream(target);
                inChannel = inStream.getChannel();
                outChannel = ((FileOutputStream) outStream).getChannel();
                inChannel.transferTo(0, inChannel.size(), outChannel);
            } else {
                if (Helper.isAtLeastAPI(LOLLIPOP)) {
                    // Storage Access Framework
                    DocumentFile targetDocument = getDocumentFile(target, false);
                    if (targetDocument != null) {
                        Context cxt = HentoidApp.getAppContext();
                        outStream = cxt.getContentResolver().openOutputStream(
                                targetDocument.getUri());
                    }
                } else {
                    return false;
                }

                if (outStream != null) {
                    // Both for SAF and for Kitkat, write to output stream.
                    byte[] buffer = new byte[16384]; // MAGIC_NUMBER
                    int bytesRead;
                    while ((bytesRead = inStream.read(buffer)) != -1) {
                        outStream.write(buffer, 0, bytesRead);
                    }
                }
            }
        } catch (Exception e) {
            LogHelper.e(TAG, "Error while copying file from " + source.getAbsolutePath() + " to "
                    + target.getAbsolutePath() + ": ", e);
            return false;
        } finally {
            try {
                if (inStream != null) {
                    inStream.close();
                }
                if (outStream != null) {
                    outStream.close();
                }
                if (inChannel != null) {
                    inChannel.close();
                }
                if (outChannel != null) {
                    outChannel.close();
                }
            } catch (Exception e) {
                // ignore exception
            }
        }

        return true;
    }

    public static OutputStream getOutputStream(@NonNull final File target) {
        OutputStream outStream = null;
        try {
            // First try the normal way
            if (isWritable(target)) {
                // standard way
                outStream = new FileOutputStream(target);
            } else {
                if (Helper.isAtLeastAPI(LOLLIPOP)) {
                    // Storage Access Framework
                    DocumentFile targetDocument = getDocumentFile(target, false);
                    if (targetDocument != null) {
                        Context cxt = HentoidApp.getAppContext();
                        outStream = cxt.getContentResolver().openOutputStream(
                                targetDocument.getUri());
                    }
                }
            }
        } catch (Exception e) {
            LogHelper.e(TAG, "Error while copying file from " + target.getAbsolutePath() + ": ", e);
        }

        return outStream;
    }

    /**
     * Delete a file.
     * May be even on external SD card.
     *
     * @param file The file to be deleted.
     * @return true if successfully deleted.
     */
    public static boolean deleteFile(@NonNull final File file) {
        // First try the normal deletion
        boolean fileDelete = deleteFilesInFolder(file);
        if (file.delete() || fileDelete) {
            return true;
        }
        // Try with Storage Access Framework
        if (Helper.isAtLeastAPI(LOLLIPOP) && isOnExtSdCard(file)) {
            DocumentFile document = getDocumentFile(file, false);
            if (document != null) {
                return document.delete();
            }
        }

        return !file.exists();
    }

    /**
     * Delete all files in a folder.
     *
     * @param folder The folder.
     * @return true if successful.
     */
    public static boolean deleteFilesInFolder(@NonNull final File folder) {
        boolean totalSuccess = true;
        if (folder.isDirectory()) {
            for (File child : folder.listFiles()) {
                deleteFilesInFolder(child);
            }
            if (!folder.delete())
                totalSuccess = false;
        } else {
            if (!folder.delete())
                totalSuccess = false;
        }

        return totalSuccess;
    }

    /**
     * Move a file.
     * The target file may even be on external SD card.
     *
     * @param source The source file.
     * @param target The target file.
     * @return true if the copying was successful.
     */
    public static boolean moveFile(@NonNull final File source, @NonNull final File target) {
        // First try the normal rename
        if (source.renameTo(target)) {
            return true;
        }

        boolean success = copyFile(source, target);
        if (success) {
            success = deleteFile(source);
        }

        return success;
    }

    /**
     * Rename a folder.
     * In case of extSdCard in Kitkat, the old folder stays in place, but files are moved.
     *
     * @param source The source folder.
     * @param target The target folder.
     * @return true if the renaming was successful.
     */
    public static boolean renameFolder(@NonNull final File source, @NonNull final File target) {
        // First try the normal rename.
        if (rename(source, target.getName())) {
            return true;
        }
        if (target.exists()) {
            return false;
        }

        // Try the Storage Access Framework if it is just a rename within the same parent folder.
        if (Helper.isAtLeastAPI(LOLLIPOP) &&
                source.getParent().equals(target.getParent()) && isOnExtSdCard(source)) {
            DocumentFile document = getDocumentFile(source, true);
            if (document != null && document.renameTo(target.getName())) {
                return true;
            }
        }

        // Try the manual way, moving files individually.
        if (!mkDir(target)) {
            return false;
        }

        File[] sourceFiles = source.listFiles();
        if (sourceFiles == null) {
            return true;
        }

        for (File sourceFile : sourceFiles) {
            String fileName = sourceFile.getName();
            File targetFile = new File(target, fileName);
            if (!copyFile(sourceFile, targetFile)) {
                // stop on first error
                return false;
            }
        }
        // Only after successfully copying all files, delete files on source folder.
        for (File sourceFile : sourceFiles) {
            if (!deleteFile(sourceFile)) {
                // stop on first error
                return false;
            }
        }

        return true;
    }

    private static boolean rename(File f, String name) {
        String newName = f.getParent() + "/" + name;
        return !f.getParentFile().canWrite() || f.renameTo(new File(newName));
    }

    /**
     * Create a folder.
     *
     * @param file The folder to be created.
     * @return true if creation was successful.
     */
    public static boolean mkDir(@NonNull final File file) {
        if (file.exists()) {
            // nothing to create.
            return file.isDirectory();
        }

        // Try the normal way
        if (file.mkdirs()) {
            return true;
        }

        // Try with Storage Access Framework.
        if (Helper.isAtLeastAPI(LOLLIPOP) && isOnExtSdCard(file)) {
            DocumentFile document = getDocumentFile(file, true);
            // getDocumentFile implicitly creates the directory.
            if (document != null) {
                return document.exists();
            }
        }

        return false;
    }

    /**
     * Create a file.
     *
     * @param file The file to be created.
     * @return true if creation was successful.
     */
    public static boolean mkFile(@NonNull final File file) throws IOException {
        if (file.exists()) {
            // nothing to create.
            return !file.isDirectory();
        }

        // Try the normal way
        try {
            if (file.createNewFile()) {
                return true;
            }
        } catch (IOException e) {
            // Fail silently
        }
        // Try with Storage Access Framework.
        if (Helper.isAtLeastAPI(LOLLIPOP) && isOnExtSdCard(file)) {
            DocumentFile document = getDocumentFile(file.getParentFile(), true);
            // getDocumentFile implicitly creates the directory.
            try {
                if (document != null) {
                    return document.createFile(MimeTypes.getMimeType(file), file.getName()) != null;
                }
            } catch (Exception e) {
                return false;
            }
        }

        return false;
    }

    /**
     * Delete a folder.
     *
     * @param file The folder name.
     * @return true if successful.
     */
    public static boolean rmDir(@NonNull final File file) {
        if (!file.exists()) {
            return true;
        }
        if (!file.isDirectory()) {
            return false;
        }
        String[] fileList = file.list();
        if (fileList != null && fileList.length > 0) {
            //  empty the folder.
            rmDirHelper(file);
        }
        String[] fileList1 = file.list();
        if (fileList1 != null && fileList1.length > 0) {
            // Delete only empty folder.
            return false;
        }
        // Try the normal way
        if (file.delete()) {
            return true;
        }

        // Try with Storage Access Framework.
        if (Helper.isAtLeastAPI(LOLLIPOP)) {
            DocumentFile document = getDocumentFile(file, true);
            if (document != null) {
                return document.delete();
            }
        }

        return !file.exists();
    }

    private static boolean rmDirHelper(@NonNull final File file) {
        for (File file1 : file.listFiles()) {
            if (file1.isDirectory()) {
                if (!rmDirHelper(file1)) {
                    return false;
                }
            } else {
                if (!deleteFile(file1)) {
                    return false;
                }
            }
        }

        return true;
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

    private static String getUri() {
        SharedPreferences prefs = HentoidApp.getSharedPrefs();
        return prefs.getString(ConstsPrefs.PREF_SD_STORAGE_URI, null);
    }

    public static boolean createNoMedia() {
        MODE mode = new FileHelper().getFileMode();

        switch (mode) {
            case SAF:
                // TODO: Add SAF
                return true;
            case BASIC:
            default:
                return noMediaLegacy();
        }
    }

    // !SAF
    private static boolean noMediaLegacy() {
        SharedPreferences prefs = HentoidApp.getSharedPrefs();
        String settingDir = prefs.getString(Consts.SETTINGS_FOLDER, "");
        try {
            if (createFileLegacy(settingDir, ".nomedia")) {
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
    private static boolean createFileLegacy(String dir, String name) throws IOException {
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

    public static void removeContent(Context cxt, Content content) {
        MODE mode = new FileHelper().getFileMode();

        switch (mode) {
            case SAF:
                // TODO: Add SAF
                break;
            case BASIC:
            default:
                removeContentLegacy(cxt, content);
        }
    }

    // !SAF
    private static void removeContentLegacy(Context cxt, Content content) {
        File dir = getContentDownloadDir(cxt, content);

        try {
            FileUtils.deleteDirectory(dir);
        } catch (IOException e) {
            deleteDirLegacy(dir);
        } finally {
            LogHelper.d(TAG, "Directory removed: " + dir);
        }
    }

    // !SAF
    // Gathers list of files in a directory and deletes them
    // but only if the directory is NOT empty - it does NOT delete the target directory
    public static void cleanDir(File directory) {
        if (!isDirEmpty(directory)) {
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
    public static File getContentDownloadDir(Context cxt, Content content) {
        File file;
        SharedPreferences sp = HentoidApp.getSharedPrefs();
        String settingDir = sp.getString(Consts.SETTINGS_FOLDER, "");
        String folderDir = content.getSite().getFolder() + content.getUniqueSiteId();
        if (settingDir.isEmpty()) {
            return getDefaultDir(cxt, folderDir);
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
    public static File getDefaultDir(Context cxt, String dir) {
        File file;
        try {
            file = new File(Environment.getExternalStorageDirectory() + "/"
                    + Consts.DEFAULT_LOCAL_DIRECTORY + "/" + dir);
        } catch (Exception e) {
            file = cxt.getDir("", Context.MODE_PRIVATE);
            file = new File(file, "/" + Consts.DEFAULT_LOCAL_DIRECTORY);
        }

        if (!file.exists() && !file.mkdirs()) {
            file = cxt.getDir("", Context.MODE_PRIVATE);
            file = new File(file, "/" + Consts.DEFAULT_LOCAL_DIRECTORY + "/" + dir);
            if (!file.exists()) {
                boolean mkdirs = file.mkdirs();
                LogHelper.d(TAG, mkdirs);
            }
        }

        return file;
    }

    // !SAF
    public static File getSiteDownloadDir(Context cxt, Site site) {
        File file;
        SharedPreferences sp = HentoidApp.getSharedPrefs();
        String settingDir = sp.getString(Consts.SETTINGS_FOLDER, "");
        String folderDir = site.getFolder();
        if (settingDir.isEmpty()) {
            return getDefaultDir(cxt, folderDir);
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
    public static void openContent(final Context cxt, Content content) {
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
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        openFile(cxt, file);
                                    }
                                })
                        .setNegativeButton(R.string.open_perfect_viewer,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        openPerfectViewer(cxt, file);
                                    }
                                }).create().show();
            } else if (readContentPreference == ConstsPrefs.PREF_READ_CONTENT_PERFECT_VIEWER) {
                openPerfectViewer(cxt, imageFile);
            }
        }
    }

    // !SAF
    private static void openFile(Context cxt, File aFile) {
        Intent myIntent = new Intent(Intent.ACTION_VIEW);
        File file = new File(aFile.getAbsolutePath());
        String extension = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(file).toString());
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        myIntent.setDataAndType(Uri.fromFile(file), mimeType);
        cxt.startActivity(myIntent);
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
    // As long as there are files in a directory it will recursively delete them -
    // finally, once there are no files, it deletes the target directory
    public static boolean deleteDirLegacy(File directory) {
        if (directory.isDirectory())
            for (File child : directory.listFiles()) {
                deleteDirLegacy(child);
            }

        boolean delete = directory.delete();
        LogHelper.d(TAG, "File/directory deleted: " + delete);
        return delete;
    }

    @TargetApi(KITKAT)
    public List<UriPermission> getRootPermissions() {
        updatePermissions();
        return Collections.unmodifiableList(uriPermissions);
    }

    @TargetApi(KITKAT)
    private void updatePermissions() {
        uriPermissions = HentoidApp.getAppContext()
                .getContentResolver().getPersistedUriPermissions();
    }

    private MODE getFileMode() {
        if (Helper.isAtLeastAPI(KITKAT)) {
            return getRootPermissions().isEmpty() ? MODE.BASIC : MODE.SAF;
        } else {
            return MODE.BASIC;
        }
    }

    private enum MODE {BASIC, SAF}
}
