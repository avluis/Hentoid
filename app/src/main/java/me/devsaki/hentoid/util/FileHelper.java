package me.devsaki.hentoid.util;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.webkit.MimeTypeMap;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
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

    private static final Charset CHARSET_LATIN_1 = Charset.forName("ISO-8859-1");


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

    public static InputStream getInputStream(@NonNull final File target) throws IOException {
        return FileUtils.openInputStream(target);
    }

    public static InputStream getInputStream(@NonNull final DocumentFile target) throws IOException {
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
        Intent myIntent = new Intent(Intent.ACTION_VIEW);
        Uri dataUri = FileProvider.getUriForFile(context, AUTHORITY, file);
        if (file.isDirectory()) {
            myIntent.setDataAndType(dataUri, DocumentsContract.Document.MIME_TYPE_DIR);
        } else {
            myIntent.setDataAndTypeAndNormalize(dataUri, MimeTypeMap.getSingleton().getMimeTypeFromExtension(getExtension(aFile.getName())));
        }
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
        try (InputStream input = new ByteArrayInputStream(binaryContent)) {
            try (BufferedOutputStream output = new BufferedOutputStream(FileHelper.getOutputStream(file))) {
                copy(input, output);
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

    public static String getMimeTypeFromPictureBinary(byte[] binary) {
        if (binary.length < 12) return "";

        // In Java, byte type is signed !
        // => Converting all raw values to byte to be sure they are evaluated as expected
        if ((byte) 0xFF == binary[0] && (byte) 0xD8 == binary[1] && (byte) 0xFF == binary[2])
            return "image/jpeg";
        else if ((byte) 0x89 == binary[0] && (byte) 0x50 == binary[1] && (byte) 0x4E == binary[2]) {
            // Detect animated PNG : To be recognized as APNG an 'acTL' chunk must appear in the stream before any 'IDAT' chunks
            int acTlPos = findSequencePosition(binary, 0, "acTL".getBytes(CHARSET_LATIN_1), (int) (binary.length * 0.2));
            if (acTlPos > -1) {
                long idatPos = findSequencePosition(binary, acTlPos, "IDAT".getBytes(CHARSET_LATIN_1), (int) (binary.length * 0.1));
                if (idatPos > -1) return "image/apng";
            }
            return "image/png";
        } else if ((byte) 0x47 == binary[0] && (byte) 0x49 == binary[1] && (byte) 0x46 == binary[2])
            return "image/gif";
        else if ((byte) 0x52 == binary[0] && (byte) 0x49 == binary[1] && (byte) 0x46 == binary[2] && (byte) 0x46 == binary[3]
                && (byte) 0x57 == binary[8] && (byte) 0x45 == binary[9] && (byte) 0x42 == binary[10] && (byte) 0x50 == binary[11])
            return "image/webp";
        else if ((byte) 0x42 == binary[0] && (byte) 0x4D == binary[1]) return "image/bmp";
        else return "image/*";
    }

    @Nullable
    public static String getExtensionFromMimeType(@NonNull String mimeType) {
        if (mimeType.isEmpty()) return null;

        String result = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        // Exceptions that MimeTypeMap does not support
        if (null == result) {
            if (mimeType.equals("image/apng") || mimeType.equals("image/vnd.mozilla.apng"))
                return "png";
        }
        return result;
    }

    public static void shareFile(final @NonNull Context context, final @NonNull DocumentFile f, final @NonNull String title) {
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("text/*");
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, title);
        sharingIntent.putExtra(Intent.EXTRA_STREAM, f.getUri());
        context.startActivity(Intent.createChooser(sharingIntent, context.getString(R.string.send_to)));
    }

    public static void shareFile(final @NonNull Context context, final @NonNull File f, final @NonNull String title) {
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("text/*");
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, title);
        sharingIntent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, FileHelper.AUTHORITY, f));
        context.startActivity(Intent.createChooser(sharingIntent, context.getString(R.string.send_to)));
    }

    // TODO Performance leverage ContentProviderClient when doing repeated calls to listXXX
    // see https://stackoverflow.com/questions/5084896/using-contentproviderclient-vs-contentresolver-to-access-content-provider
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

    private static int findSequencePosition(byte[] data, int initialPos, byte[] sequence, int limit) {
//        int BUFFER_SIZE = 64;
//        byte[] readBuffer = new byte[BUFFER_SIZE];

        int remainingBytes;
//        int bytesToRead;
//        int dataPos = 0;
        int iSequence = 0;

        if (initialPos < 0 || initialPos > data.length) return -1;

        remainingBytes = (limit > 0) ? Math.min(data.length - initialPos, limit) : data.length;

//        while (remainingBytes > 0) {
//            bytesToRead = Math.min(remainingBytes, BUFFER_SIZE);
//            System.arraycopy(data, dataPos, readBuffer, 0, bytesToRead);
//            dataPos += bytesToRead;

//            stream.Read(readBuffer, 0, bytesToRead);

        for (int i = initialPos; i < remainingBytes; i++) {
            if (sequence[iSequence] == data[i]) iSequence++;
            else if (iSequence > 0) iSequence = 0;

            if (sequence.length == iSequence) return i - sequence.length;
        }

//            remainingBytes -= bytesToRead;
//        }

        // Target sequence not found
        return -1;
    }

    public static void copy(@NonNull File src, @NonNull File dst) throws IOException {
        try (InputStream in = new FileInputStream(src)) {
            try (OutputStream out = new FileOutputStream(dst)) {
                copy(in, out);
            }
        }
    }

    public static void copy(@NonNull InputStream in, @NonNull OutputStream out) throws IOException {
        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        out.flush();
    }

    public static File getDownloadsFolder() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }

    public static OutputStream openNewDownloadOutputStream(@NonNull final String fileName) throws IOException {
        // TODO implement when targetSDK = 29
        /*
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return openNewDownloadOutputStreamQ(fileName, mimeType)
        } else {*/
        return openNewDownloadOutputStreamLegacy(fileName);
        //}
    }

    private static OutputStream openNewDownloadOutputStreamLegacy(@NonNull final String fileName) throws IOException {
        File downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (null == downloadsFolder) throw new IOException("Downloads folder not found");

        File target = new File(downloadsFolder, fileName);
        if (!target.exists() && !target.createNewFile())
            throw new IOException("Could not create new file in downloads folder");

        return getOutputStream(target);
    }

    @TargetApi(29)
    private static OutputStream openNewDownloadOutputStreamQ(@NonNull final String fileName, @NonNull final String mimeType) throws IOException {
        // TODO implement when targetSDK = 29
        // https://gitlab.com/commonsguy/download-wrangler/blob/master/app/src/main/java/com/commonsware/android/download/DownloadRepository.kt
        return null;
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
