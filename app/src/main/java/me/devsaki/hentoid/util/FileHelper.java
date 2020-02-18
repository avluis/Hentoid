package me.devsaki.hentoid.util;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import timber.log.Timber;

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
    private static String[] getExtSdCardPaths() {
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
        try {
            // StorageVolume exist since API21, but only visible since API24
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

    private static String getVolumeIdFromTreeUri(final Uri treeUri) {
        final String docId = DocumentsContract.getTreeDocumentId(treeUri);
        final String[] split = docId.split(":");
        if (split.length > 0) return split[0];
        else return null;
    }

    private static String getDocumentPathFromTreeUri(final Uri treeUri) {
        final String docId = DocumentsContract.getTreeDocumentId(treeUri);
        final String[] split = docId.split(":");
        if ((split.length >= 2) && (split[1] != null)) return split[1];
        else return File.separator;
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
        return FileUtils.openOutputStream(target);
    }

    static OutputStream getOutputStream(@NonNull final DocumentFile target) throws IOException {
        return FileUtil.getOutputStream(target);
    }

    static InputStream getInputStream(@NonNull final File target) throws IOException {
        return FileUtils.openInputStream(target);
    }

    static InputStream getInputStream(@NonNull final DocumentFile target) throws IOException {
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
     */
    public static void removeFile(File target) {
        FileUtil.deleteFile(target);
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

    public static boolean checkAndSetRootFolder(DocumentFile folder) {
        return checkAndSetRootFolder(folder, false);
    }

    public static boolean checkAndSetRootFolder(DocumentFile folder, boolean notify) {
        Context context = HentoidApp.getInstance();

        // Validate folder
        if (!folder.exists() && !folder.isDirectory()) {
            if (notify)
                ToastUtil.toast(context, R.string.error_creating_folder);
            return false;
        }

        DocumentFile nomedia = folder.createFile("application/octet-steam", ".nomedia");
        // Clean up (if any) nomedia file
        if (null != nomedia && nomedia.exists()) {
            boolean deleted = nomedia.delete();
            if (deleted) Timber.d(".nomedia file deleted");
        } else {
            if (notify)
                ToastUtil.toast(context, R.string.error_write_permission);
            return false;
        }

        Preferences.setStorageUri(folder.getUri().toString());
        return true;
    }

    /**
     * Create the ".nomedia" file in the app's root folder
     */
    public static boolean createNoMedia(@NonNull Context context) {
        DocumentFile rootDir = DocumentFile.fromTreeUri(context, Uri.parse(Preferences.getStorageUri()));
        if (null == rootDir || !rootDir.exists()) return false;

        DocumentFile nomedia = rootDir.createFile("application/octet-steam", ".nomedia");
        return (null != nomedia && nomedia.exists());
    }

    /**
     * Open the given file using the device's app(s) of choice
     *
     * @param context Context
     * @param aFile   File to be opened
     */
    public static void openFile(@NonNull Context context, @NonNull File aFile) {
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

    public static void openFile(@NonNull Context context, @NonNull DocumentFile aFile) {
        Intent myIntent = new Intent(Intent.ACTION_VIEW, aFile.getUri());
        myIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            context.startActivity(myIntent);
        } catch (ActivityNotFoundException e) {
            Timber.e(e, "No activity found to open %s", aFile.getUri());
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

    public static void saveBinaryInFile(DocumentFile file, byte[] binaryContent) throws IOException {
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

    public static void shareFile(final @NonNull Context context, final @NonNull File f, final @NonNull String title) {
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("text/*");
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, title);
        sharingIntent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, FileHelper.AUTHORITY, f));
        context.startActivity(Intent.createChooser(sharingIntent, context.getString(R.string.send_to)));
    }

    public static void shareFile(final @NonNull Context context, final @NonNull DocumentFile f, final @NonNull String title) {
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("text/*");
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, title);
        sharingIntent.putExtra(Intent.EXTRA_STREAM, f.getUri());
        context.startActivity(Intent.createChooser(sharingIntent, context.getString(R.string.send_to)));
    }

    public static List<DocumentFile> listFiles(@NonNull DocumentFile parent, FileFilter filter) {
        List<DocumentFile> result = new ArrayList<>();

        DocumentFile[] files = parent.listFiles();
        if (filter != null)
            for (DocumentFile file : files)
                if (filter.accept(file)) result.add(file);

        return result;
    }

    public static List<DocumentFile> listFolders(@NonNull Context context, @NonNull DocumentFile parent) {
        return FileUtil.listFolders(context, parent, null);
    }

    @Nullable
    public static DocumentFile findFolder(@NonNull Context context, @NonNull DocumentFile parent, @NonNull String subfolderName) {
        //List<DocumentFile> result = listFiles(parent, f -> f.isDirectory() && f.getName() != null && f.getName().equalsIgnoreCase(subfolderName));
        List<DocumentFile> result = FileUtil.listFolders(context, parent, subfolderName);
        if (!result.isEmpty()) return result.get(0);
        else return null;
    }

    @Nullable
    public static DocumentFile findFile(@NonNull Context context, @NonNull DocumentFile parent, @NonNull String fileName) {
        //List<DocumentFile> result = listFiles(parent, f -> f.isFile() && f.getName() != null && f.getName().equalsIgnoreCase(fileName));
        List<DocumentFile> result = FileUtil.listFiles(context, parent, fileName);
        if (!result.isEmpty()) return result.get(0);
        else return null;
    }

    @Nullable
    public static DocumentFile findDocumentFile(@NonNull Context context, @NonNull DocumentFile parent, @NonNull String documentFileName) {
        //List<DocumentFile> result = listFiles(parent, f -> f.isFile() && f.getName() != null && f.getName().equalsIgnoreCase(fileName));
        List<DocumentFile> result = FileUtil.listDocumentFiles(context, parent, documentFileName);
        if (!result.isEmpty()) return result.get(0);
        else return null;
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

    public static class MemoryUsageFiguresSaf {
        private final long freeMemBytes;
        private final long totalMemBytes;

        // Check https://stackoverflow.com/questions/56663624/how-to-get-free-and-total-size-of-each-storagevolume
        // to see if a better solution compatible with API21 has been found
        // TODO - encapsulate the reflection trick used by getVolumePath
        public MemoryUsageFiguresSaf(@NonNull Context context, @NonNull DocumentFile f) {
            String fullPath = getFullPathFromTreeUri(f.getUri(), context); // Oh so dirty !!
            if (fullPath != null) {
                File file = new File(fullPath);
                this.freeMemBytes = file.getFreeSpace();
                this.totalMemBytes = file.getTotalSpace();
            } else {
                this.freeMemBytes = 0;
                this.totalMemBytes = 0;
            }
        }

        public double getFreeUsageRatio100() {
            return freeMemBytes * 100.0 / totalMemBytes;
        }

        public String formatFreeUsageMb() {
            return Math.round(freeMemBytes / 1e6) + "/" + Math.round(totalMemBytes / 1e6);
        }
    }

    @FunctionalInterface
    public interface FileFilter {

        /**
         * Tests whether or not the specified abstract pathname should be
         * included in a pathname list.
         *
         * @param pathname The abstract pathname to be tested
         * @return <code>true</code> if and only if <code>pathname</code>
         * should be included
         */
        boolean accept(DocumentFile pathname);
    }

}
