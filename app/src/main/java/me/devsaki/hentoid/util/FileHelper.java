package me.devsaki.hentoid.util;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import me.devsaki.hentoid.BuildConfig;
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
    private static final String NOMEDIA_FILE_NAME = ".nomedia";

    private static final Charset CHARSET_LATIN_1 = StandardCharsets.ISO_8859_1;


    public static String getFileProviderAuthority() {
        return AUTHORITY;
    }

    public static DocumentFile getFileFromUriString(@NonNull final Context context, @NonNull final String uriStr) {
        Uri fileUri = Uri.parse(uriStr);
        return DocumentFile.fromSingleUri(context, fileUri);
    }

    // Credits go to https://stackoverflow.com/questions/34927748/android-5-0-documentfile-from-tree-uri/36162691#36162691
    public static String getFullPathFromTreeUri(@NonNull final Context context, @NonNull final Uri uri, boolean isFolder) {
        String volumePath = getVolumePath(getVolumeIdFromUri(uri, isFolder), context);
        if (volumePath == null) return File.separator;
        if (volumePath.endsWith(File.separator))
            volumePath = volumePath.substring(0, volumePath.length() - 1);

        String documentPath = getDocumentPathFromUri(uri, isFolder);
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

    private static String getVolumeIdFromUri(final Uri uri, boolean isFolder) {
        final String docId;
        if (isFolder) docId = DocumentsContract.getTreeDocumentId(uri);
        else docId = DocumentsContract.getDocumentId(uri);

        final String[] split = docId.split(":");
        if (split.length > 0) return split[0];
        else return null;
    }

    private static String getDocumentPathFromUri(final Uri uri, boolean isFolder) {
        final String docId;
        if (isFolder) docId = DocumentsContract.getTreeDocumentId(uri);
        else docId = DocumentsContract.getDocumentId(uri);

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
    private static OutputStream getOutputStream(@NonNull final File target) throws IOException {
        return FileUtils.openOutputStream(target);
    }

    public static OutputStream getOutputStream(@NonNull final Context context, @NonNull final DocumentFile target) throws IOException {
        return context.getContentResolver().openOutputStream(target.getUri(), "rwt"); // Always truncate file to whatever data needs to be written
    }

    public static InputStream getInputStream(@NonNull final Context context, @NonNull final DocumentFile target) throws IOException {
        return context.getContentResolver().openInputStream(target.getUri());
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

    /**
     * Return the DocumentFile with the given display name located in the given folder
     * If it doesn't exist, create a new one and return it
     *
     * @param context     Context to use
     * @param folder      Containing folder
     * @param mimeType    Mime-type to use if the document has to be created
     * @param displayName Display name of the document
     * @return Usable DocumentFile; null if creation failed
     */
    @Nullable
    public static DocumentFile findOrCreateDocumentFile(@NonNull final Context context, @NonNull final DocumentFile folder, @Nullable String mimeType, @NonNull final String displayName) {
        // Look for it first
        DocumentFile file = findFile(context, folder, displayName);
        if (null == file) { // Create it
            if (null == mimeType) mimeType = "application/octet-steam";
            return folder.createFile(mimeType, displayName);
        } else return file;
    }

    public static boolean checkAndSetRootFolder(@NonNull final Context context, @NonNull final DocumentFile folder, boolean notify) {
        // Validate folder
        if (!folder.exists() && !folder.isDirectory()) {
            if (notify)
                ToastUtil.toast(context, R.string.error_creating_folder);
            return false;
        }

        // Remove and add back the nomedia file to test if the user has the I/O rights to the selected folder
        DocumentFile nomedia = findFile(context, folder, NOMEDIA_FILE_NAME);
        if (nomedia != null) nomedia.delete();

        nomedia = folder.createFile("application/octet-steam", NOMEDIA_FILE_NAME);
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

        DocumentFile nomedia = findOrCreateDocumentFile(context, rootDir, null, NOMEDIA_FILE_NAME);
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
        Uri dataUri = FileProvider.getUriForFile(context, AUTHORITY, file);
        tryOpenFile(context, dataUri, aFile.getName(), aFile.isDirectory());
    }

    /**
     * Open the given file using the device's app(s) of choice
     *
     * @param context Context
     * @param aFile   File to be opened
     */
    public static void openFile(@NonNull Context context, @NonNull DocumentFile aFile) {
        String fileName = (null == aFile.getName()) ? "" : aFile.getName();
        tryOpenFile(context, aFile.getUri(), fileName, aFile.isDirectory());
    }

    private static void tryOpenFile(@NonNull Context context, @NonNull Uri uri, @NonNull String fileName, boolean isDirectory) {
        try {
            if (isDirectory) {
                try {
                    openFileWithIntent(context, uri, DocumentsContract.Document.MIME_TYPE_DIR);
                } catch (ActivityNotFoundException e1) {
                    try {
                        openFileWithIntent(context, uri, "resource/folder");
                    } catch (ActivityNotFoundException e2) {
                        ToastUtil.toast(R.string.select_file_manager);
                        openFileWithIntent(context, uri, "*/*");
                    }
                }
            } else
                openFileWithIntent(context, uri, MimeTypeMap.getSingleton().getMimeTypeFromExtension(getExtension(fileName)));
        } catch (ActivityNotFoundException e) {
            Timber.e(e, "No activity found to open %s", uri.toString());
            ToastUtil.toast(context, R.string.error_open, Toast.LENGTH_LONG);
        }
    }

    private static void openFileWithIntent(@NonNull Context context, @NonNull Uri uri, @Nullable String mimeType) {
        Intent myIntent = new Intent(Intent.ACTION_VIEW);
        myIntent.setDataAndTypeAndNormalize(uri, mimeType);
        myIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(myIntent);
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

    public static void saveBinaryInFile(@NonNull final Context context, @NonNull final DocumentFile file, byte[] binaryContent) throws IOException {
        byte[] buffer = new byte[1024];
        int count;

        try (InputStream input = new ByteArrayInputStream(binaryContent)) {
            try (BufferedOutputStream output = new BufferedOutputStream(FileHelper.getOutputStream(context, file))) {

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

    // see https://stackoverflow.com/questions/5084896/using-contentproviderclient-vs-contentresolver-to-access-content-provider
    public static List<DocumentFile> listFolders(@NonNull Context context, @NonNull DocumentFile parent) {
        return listFoldersFilter(context, parent, null);
    }

    public static List<DocumentFile> listFolders(@NonNull Context context, @NonNull DocumentFile parent, @NonNull ContentProviderClient client) {
        return FileUtil.listDocumentFiles(context, parent, client, null, true, false);
    }

    public static List<DocumentFile> listFoldersFilter(@NonNull Context context, @NonNull DocumentFile parent, final FileHelper.NameFilter filter) {
        ContentProviderClient client = context.getContentResolver().acquireContentProviderClient(parent.getUri());
        if (null == client) return Collections.emptyList();
        try {
            return FileUtil.listDocumentFiles(context, parent, client, filter, true, false);
        } finally {
            // ContentProviderClient.close only available on API level 24+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                client.close();
            else
                client.release();
        }
    }

    public static List<DocumentFile> listDocumentFiles(@NonNull Context context, @NonNull DocumentFile parent, @NonNull ContentProviderClient client, final FileHelper.NameFilter filter) {
        return FileUtil.listDocumentFiles(context, parent, client, filter, false, true);
    }

    public static List<DocumentFile> listDocumentFiles(@NonNull Context context, @NonNull DocumentFile parent, final FileHelper.NameFilter filter) {
        ContentProviderClient client = context.getContentResolver().acquireContentProviderClient(parent.getUri());
        if (null == client) return Collections.emptyList();
        try {
            return FileUtil.listDocumentFiles(context, parent, client, filter, false, true);
        } finally {
            // ContentProviderClient.close only available on API level 24+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                client.close();
            else
                client.release();
        }
    }

    @Nullable
    public static DocumentFile findFolder(@NonNull Context context, @NonNull DocumentFile parent, @NonNull ContentProviderClient client, @NonNull String subfolderName) {
        List<DocumentFile> result = FileUtil.listDocumentFiles(context, parent, client, FileHelper.createNameFilterEquals(subfolderName), true, false);
        if (!result.isEmpty()) return result.get(0);
        else return null;
    }

    @Nullable
    public static DocumentFile findFile(@NonNull Context context, @NonNull DocumentFile parent, @NonNull ContentProviderClient client, @NonNull String fileName) {
        List<DocumentFile> result = FileUtil.listDocumentFiles(context, parent, client, FileHelper.createNameFilterEquals(fileName), false, true);
        if (!result.isEmpty()) return result.get(0);
        else return null;
    }

    @Nullable
    public static DocumentFile findFolder(@NonNull Context context, @NonNull DocumentFile parent, @NonNull String subfolderName) {
        List<DocumentFile> result = listDocumentFiles(context, parent, subfolderName, true, false);
        if (!result.isEmpty()) return result.get(0);
        else return null;
    }

    @Nullable
    public static DocumentFile findFile(@NonNull Context context, @NonNull DocumentFile parent, @NonNull String fileName) {
        List<DocumentFile> result = listDocumentFiles(context, parent, fileName, false, true);
        if (!result.isEmpty()) return result.get(0);
        else return null;
    }

    private static List<DocumentFile> listDocumentFiles(@NonNull final Context context,
                                                        @NonNull final DocumentFile parent,
                                                        final String nameFilter,
                                                        boolean listFolders,
                                                        boolean listFiles) {
        ContentProviderClient client = context.getContentResolver().acquireContentProviderClient(parent.getUri());
        if (null == client) return Collections.emptyList();
        try {
            return FileUtil.listDocumentFiles(context, parent, client, FileHelper.createNameFilterEquals(nameFilter), listFolders, listFiles);
        } finally {
            // ContentProviderClient.close only available on API level 24+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                client.close();
            else
                client.release();
        }
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

    public static OutputStream openNewDownloadOutputStream(
            @NonNull final Context context,
            @NonNull final String fileName,
            @NonNull final String mimeType
    ) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return openNewDownloadOutputStreamQ(context, fileName, mimeType);
        } else {
            return openNewDownloadOutputStreamLegacy(fileName);
        }
    }

    private static OutputStream openNewDownloadOutputStreamLegacy(@NonNull final String fileName) throws IOException {
        File downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (null == downloadsFolder) throw new IOException("Downloads folder not found");

        File target = new File(downloadsFolder, fileName);
        if (!target.exists() && !target.createNewFile())
            throw new IOException("Could not create new file in downloads folder");

        return getOutputStream(target);
    }

    // https://gitlab.com/commonsguy/download-wrangler/blob/master/app/src/main/java/com/commonsware/android/download/DownloadRepository.kt
    @TargetApi(29)
    private static OutputStream openNewDownloadOutputStreamQ(
            @NonNull final Context context,
            @NonNull final String fileName,
            @NonNull final String mimeType) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);

        ContentResolver resolver = context.getContentResolver();
        Uri targetFileUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (null == targetFileUri) throw new IOException("Target URI could not be formed");

        return resolver.openOutputStream(targetFileUri);
    }

    public static class MemoryUsageFigures {
        private final long freeMemBytes;
        private final long totalMemBytes;

        // Check https://stackoverflow.com/questions/56663624/how-to-get-free-and-total-size-of-each-storagevolume
        // to see if a better solution compatible with API21 has been found
        // TODO - encapsulate the reflection trick used by getVolumePath
        public MemoryUsageFigures(@NonNull Context context, @NonNull DocumentFile f) {
            String fullPath = getFullPathFromTreeUri(context, f.getUri(), true); // Oh so dirty !!
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

    public static void revokePreviousPermissions(@NonNull final ContentResolver resolver, @NonNull final Uri newUri) {
        // Unfortunately, the content Uri of the selected resource is not exactly the same as the one stored by ContentResolver
        // -> solution is to compare their TreeDocumentId instead
        String treeUriId = DocumentsContract.getTreeDocumentId(newUri);

        for (UriPermission p : resolver.getPersistedUriPermissions())
            if (!DocumentsContract.getTreeDocumentId(p.getUri()).equals(treeUriId))
                resolver.releasePersistableUriPermission(p.getUri(),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (resolver.getPersistedUriPermissions().isEmpty()) {
            Timber.d("Permissions revoked successfully.");
        } else {
            Timber.d("Permissions failed to be revoked.");
        }
    }


    private static NameFilter createNameFilterEquals(@NonNull final String name) {
        return displayName -> displayName.equalsIgnoreCase(name);
    }

    @FunctionalInterface
    public interface NameFilter {

        /**
         * Tests whether or not the specified abstract display name should be included in a pathname list.
         *
         * @param displayName The abstract display name to be tested
         * @return <code>true</code> if and only if <code>displayName</code> should be included
         */
        boolean accept(@NonNull String displayName);
    }
}
