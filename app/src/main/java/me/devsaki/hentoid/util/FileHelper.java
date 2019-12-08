package me.devsaki.hentoid.util;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import org.apache.commons.io.FileUtils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import timber.log.Timber;

import static android.os.Environment.getExternalStorageDirectory;

/**
 * Created by avluis on 08/05/2016.
 * Generic file-related utility class
 */
public class FileHelper {

    private FileHelper() {
        throw new IllegalStateException("Utility class");
    }

    public static final String AUTHORIZED_CHARS = "[^a-zA-Z0-9.-]";

    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".provider.FileProvider";

    private static final String PRIMARY_VOLUME_NAME = "primary";


    public static String getFileProviderAuthority() {
        return AUTHORITY;
    }

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
    static String getExtSdCardFolder(final File file) {
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
        Context context = HentoidApp.getInstance();
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

    // Credits go to https://stackoverflow.com/questions/34927748/android-5-0-documentfile-from-tree-uri/36162691#36162691
    @Nullable
    public static String getFullPathFromTreeUri(@Nullable final Uri treeUri, Context con) {
        if (treeUri == null) return null;
        String volumePath = getVolumePath(getVolumeIdFromTreeUri(treeUri), con);
        if (volumePath == null) return File.separator;
        if (volumePath.endsWith(File.separator))
            volumePath = volumePath.substring(0, volumePath.length() - 1);

        String documentPath = getDocumentPathFromTreeUri(treeUri);
        if (documentPath.endsWith(File.separator))
            documentPath = documentPath.substring(0, documentPath.length() - 1);

        if (documentPath.length() > 0) {
            if (documentPath.startsWith(File.separator))
                return volumePath + documentPath;
            else
                return volumePath + File.separator + documentPath;
        } else return volumePath;
    }


    @SuppressLint("ObsoleteSdkInt")
    private static String getVolumePath(final String volumeId, Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null;
        try {
            StorageManager mStorageManager =
                    (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            Class<?> storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            Method getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
            Method getUuid = storageVolumeClazz.getMethod("getUuid");
            Method getPath = storageVolumeClazz.getMethod("getPath");
            Method isPrimary = storageVolumeClazz.getMethod("isPrimary");
            Object result = getVolumeList.invoke(mStorageManager);

            final int length = Array.getLength(result);
            for (int i = 0; i < length; i++) {
                Object storageVolumeElement = Array.get(result, i);
                String uuid = (String) getUuid.invoke(storageVolumeElement);
                Boolean primary = (Boolean) isPrimary.invoke(storageVolumeElement);

                // primary volume?
                if (primary && PRIMARY_VOLUME_NAME.equals(volumeId))
                    return (String) getPath.invoke(storageVolumeElement);

                // other volumes?
                if (uuid != null && uuid.equals(volumeId))
                    return (String) getPath.invoke(storageVolumeElement);
            }
            // not found.
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static String getVolumeIdFromTreeUri(final Uri treeUri) {
        final String docId = DocumentsContract.getTreeDocumentId(treeUri);
        final String[] split = docId.split(":");
        if (split.length > 0) return split[0];
        else return null;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static String getDocumentPathFromTreeUri(final Uri treeUri) {
        final String docId = DocumentsContract.getTreeDocumentId(treeUri);
        final String[] split = docId.split(":");
        if ((split.length >= 2) && (split[1] != null)) return split[1];
        else return File.separator;
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

        boolean hasPermission;

        try {
            hasPermission = FileUtil.makeFile(testFile, true);
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

    static OutputStream getOutputStream(@NonNull final DocumentFile target) throws IOException {
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
    public static boolean cleanDirectory(@NonNull File target) {
        try {
            return FileUtil.tryCleanDirectory(target);
        } catch (Exception e) {
            Timber.e(e, "Failed to clean directory");
            return false;
        }
    }

    public static boolean checkAndSetRootFolder(String folder) {
        return checkAndSetRootFolder(folder, false);
    }

    public static boolean checkAndSetRootFolder(String folder, boolean notify) {
        Context context = HentoidApp.getInstance();

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
        hasPermission = FileUtil.makeFile(nomedia, true);

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

        if (FileUtil.makeFile(noMedia, false)) {
            ToastUtil.toast(R.string.nomedia_file_created);
        } else {
            Timber.d(".nomedia file already exists.");
        }
    }


    public static File getDefaultDir(Context context, String dir) {
        File file;
        try {
            file = new File(getExternalStorageDirectory() + File.separator
                    + Consts.DEFAULT_LOCAL_DIRECTORY + File.separator + dir);
        } catch (Exception e) {
            file = context.getDir("", Context.MODE_PRIVATE);
            file = new File(file, File.separator + Consts.DEFAULT_LOCAL_DIRECTORY);
        }

        if (!file.exists() && !FileUtil.makeDir(file)) {
            file = context.getDir("", Context.MODE_PRIVATE);
            file = new File(file, File.separator + Consts.DEFAULT_LOCAL_DIRECTORY + File.separator + dir);
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
                if (depth < 6)
                    files.addAll(findFilesRecursively(entry, extension, depth + 1)); // Hard recursive limit to avoid catastrophes
            } else {
                files.add(entry);
            }
        }
        return files;
    }

    /**
     * Open the given file using the device's app(s) of choice
     *
     * @param context Context
     * @param aFile   File to be opened
     */
    public static void openFile(Context context, File aFile) {
        File file = new File(aFile.getAbsolutePath());
        Intent myIntent = new Intent(Intent.ACTION_VIEW, FileProvider.getUriForFile(context, AUTHORITY, file));
        myIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            context.startActivity(myIntent);
        } catch (ActivityNotFoundException e) {
            Timber.e(e, "No activity found to open %s", aFile.getAbsolutePath());
            ToastUtil.toast(context, R.string.error_open, Toast.LENGTH_LONG);
        }
    }

    /**
     * Returns the extension of the given filename
     *
     * @param fileName Filename
     * @return Extension of the given filename
     */
    public static String getExtension(@NonNull final String fileName) {
        return fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.US) : "";
    }

    public static String getFileNameWithoutExtension(@NonNull final String fileName) {
        return fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
    }

    /**
     * Save the given binary content in the given file
     *
     * @param file          File to save content on
     * @param binaryContent Content to save
     * @throws IOException If any IOException occurs
     */
    public static void saveBinaryInFile(File file, byte[] binaryContent) throws IOException {
        byte[] buffer = new byte[1024];
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

    public static String getImageExtensionFromPictureHeader(byte[] header) {
        if (header.length < 12) return "";

        // In Java, byte type is signed !
        // => Converting all raw values to byte to be sure they are evaluated as expected
        if ((byte) 0xFF == header[0] && (byte) 0xD8 == header[1] && (byte) 0xFF == header[2])
            return "jpg";
        else if ((byte) 0x89 == header[0] && (byte) 0x50 == header[1] && (byte) 0x4E == header[2])
            return "png";
        else if ((byte) 0x47 == header[0] && (byte) 0x49 == header[1] && (byte) 0x46 == header[2])
            return "gif";
        else if ((byte) 0x52 == header[0] && (byte) 0x49 == header[1] && (byte) 0x46 == header[2] && (byte) 0x46 == header[3]
                && (byte) 0x57 == header[8] && (byte) 0x45 == header[9] && (byte) 0x42 == header[10] && (byte) 0x50 == header[11])
            return "webp";
        else if ((byte) 0x42 == header[0] && (byte) 0x4D == header[1]) return "bmp";
        else return "";
    }

    // Please don't delete this method!
    // I need some way to trace actions when working with SD card features - Robb
    public static void createFileWithMsg(@Nonnull String file, String msg) {
        try {
            FileHelper.saveBinaryInFile(new File(getDefaultDir(HentoidApp.getInstance(), ""), file + ".txt"), (null == msg) ? "NULL".getBytes() : msg.getBytes());
            Timber.i(">>file %s -> %s", file, msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Nullable
    public static DocumentFile getDocumentFile(@Nonnull final File file, final boolean isDirectory) {
        return FileUtil.getDocumentFile(file, isDirectory);
    }

    public static void shareFile(final @NonNull Context context, final @NonNull File f, final @NonNull String title) {
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("text/*");
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, title);
        sharingIntent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, FileHelper.AUTHORITY, f));
        context.startActivity(Intent.createChooser(sharingIntent, context.getString(R.string.send_to)));
    }

    public static class MemoryUsageFigures {
        private final long freeMemBytes;
        private final long totalMemBytes;


        public MemoryUsageFigures(File f) {
            this.freeMemBytes = f.getFreeSpace();
            this.totalMemBytes = f.getTotalSpace();
        }

        public double getFreeUsageRatio100() {
            return freeMemBytes * 100.0 / totalMemBytes;
        }

        public String formatFreeUsageMb() {
            return Math.round(freeMemBytes / 1e6) + "/" + Math.round(totalMemBytes / 1e6);
        }
    }
}
