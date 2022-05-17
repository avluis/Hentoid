package me.devsaki.hentoid.util;

import static me.devsaki.hentoid.util.FileExplorer.createNameFilterEquals;

import android.annotation.TargetApi;
import android.app.usage.StorageStatsManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.system.Os;
import android.system.StructStatVfs;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import com.annimon.stream.Stream;

import org.apache.commons.io.FileUtils;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import timber.log.Timber;

/**
 * Generic file-related utility class
 */
public class FileHelper {

    private FileHelper() {
        throw new IllegalStateException("Utility class");
    }

    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".provider.FileProvider";

    private static final String PRIMARY_VOLUME_NAME = "primary"; // DocumentsContract.PRIMARY_VOLUME_NAME
    private static final String NOMEDIA_FILE_NAME = ".nomedia";

    private static final String ILLEGAL_FILENAME_CHARS = "[\"*/:<>\\?\\\\|]"; // https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/os/FileUtils.java;l=972?q=isValidFatFilenameChar

    public static final int FILE_IO_BUFFER_SIZE = 32 * 1024;


    /**
     * Build a DocumentFile representing a file from the given Uri string
     *
     * @param context Context to use for the conversion
     * @param uriStr  Uri string to use
     * @return DocumentFile built from the given Uri string; null if the DocumentFile couldn't be built
     */
    @Nullable
    public static DocumentFile getFileFromSingleUriString(@NonNull final Context context, final String uriStr) {
        if (null == uriStr || uriStr.isEmpty()) return null;
        DocumentFile result = DocumentFile.fromSingleUri(context, Uri.parse(uriStr));
        if (null == result || !result.exists()) return null;
        else return result;
    }

    /**
     * Build a DocumentFile representing a folder from the given Uri string
     *
     * @param context    Context to use for the conversion
     * @param treeUriStr Uri string to use
     * @return DocumentFile built from the given Uri string; null if the DocumentFile couldn't be built
     */
    @Nullable
    public static DocumentFile getFolderFromTreeUriString(@NonNull final Context context, final String treeUriStr) {
        if (null == treeUriStr || treeUriStr.isEmpty()) return null;
        DocumentFile folder = DocumentFile.fromTreeUri(context, Uri.parse(treeUriStr));
        if (null == folder || !folder.exists()) return null;
        else return folder;
    }

    public static String getFullPathFromUri(@NonNull final Context context, @NonNull final Uri uri) {
        if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
            return uri.getPath();
        } else {
            return getFullPathFromTreeUri(context, uri);
        }
    }

    /**
     * Get the full, human-readable access path from the given Uri
     * <p>
     * Credits go to https://stackoverflow.com/questions/34927748/android-5-0-documentfile-from-tree-uri/36162691#36162691
     *
     * @param context Context to use for the conversion
     * @param uri     Uri to get the full path from
     * @return Full, human-readable access path from the given Uri
     */
    public static String getFullPathFromTreeUri(@NonNull final Context context, @NonNull final Uri uri) {
        if (uri.toString().isEmpty()) return "";

        String volumePath = getVolumePath(context, getVolumeIdFromUri(uri));
        if (volumePath == null) return File.separator;
        if (volumePath.endsWith(File.separator))
            volumePath = volumePath.substring(0, volumePath.length() - 1);

        String documentPath = getDocumentPathFromUri(uri);
        if (documentPath.endsWith(File.separator))
            documentPath = documentPath.substring(0, documentPath.length() - 1);

        if (documentPath.length() > 0) {
            if (documentPath.startsWith(File.separator))
                return volumePath + documentPath;
            else
                return volumePath + File.separator + documentPath;
        } else return volumePath;
    }

    /**
     * Get the human-readable access path for the given volume ID
     *
     * @param context  Context to use
     * @param volumeId Volume ID to get the path from
     * @return Human-readable access path of the given volume ID
     */
    @Nullable
    private static String getVolumePath(@NonNull Context context, final String volumeId) {
        try {
            // StorageVolume exists since API19, has an uiid since API21 but is only visible since API24
            StorageManager mStorageManager =
                    (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            Class<?> storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            Method getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
            Method getUuid = storageVolumeClazz.getMethod("getUuid");
            Method isPrimary = storageVolumeClazz.getMethod("isPrimary");
            Object result = getVolumeList.invoke(mStorageManager);
            if (null == result) return null;

            final int length = Array.getLength(result);
            for (int i = 0; i < length; i++) {
                Object storageVolumeElement = Array.get(result, i);
                if (storageVolumeElement != null) {
                    String uuid = StringHelper.protect((String) getUuid.invoke(storageVolumeElement));
                    Boolean primary = (Boolean) isPrimary.invoke(storageVolumeElement);
                    if (null == primary) primary = false;

                    if (volumeIdMatch(uuid, primary, volumeId))
                        return getVolumePath(storageVolumeElement);
                }
            }
            // not found.
            return null;
        } catch (Exception e) {
            Timber.w(e);
            return null;
        }
    }

    /**
     * Returns the human-readable access path of the root of the given storage volume
     *
     * @param storageVolume android.os.storage.StorageVolume to return the path from
     * @return Human-readable access path of the root of the given storage volume; empty string if not found
     */
    @SuppressWarnings("JavaReflectionMemberAccess") // Access to getPathFile is limited to API<30
    private static String getVolumePath(@NonNull Object storageVolume) {
        String path = "";
        String absolutePath = "";
        String canonicalPath = "";
        try {
            File pathFile;
            Class<?> storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            if (Build.VERSION.SDK_INT < 30) {
                Method getPathFile = storageVolumeClazz.getMethod("getPathFile"); // Removed in API30
                pathFile = (File) getPathFile.invoke(storageVolume);
            } else {
                Method getDirectory = storageVolumeClazz.getMethod("getDirectory");
                pathFile = (File) getDirectory.invoke(storageVolume);
            }

            if (pathFile != null) {
                path = pathFile.getPath();
                absolutePath = pathFile.getAbsolutePath();
                canonicalPath = pathFile.getCanonicalPath();
            }
        } catch (Exception e) {
            Timber.w(e);
        }
        if (path.isEmpty() && absolutePath.isEmpty()) return canonicalPath;
        if (path.isEmpty()) return absolutePath;
        return path;
    }

    /**
     * Get the volume ID of the given Uri
     *
     * @param uri Uri to get the volume ID for
     * @return Volume ID of the given Uri
     */
    @Nullable
    private static String getVolumeIdFromUri(final Uri uri) {
        String docId;

        try {
            docId = DocumentsContract.getDocumentId(uri);
        } catch (IllegalArgumentException e) {
            docId = DocumentsContract.getTreeDocumentId(uri);
        }

        final String[] split = docId.split(":");
        if (split.length > 0) return split[0];
        else return null;
    }

    /**
     * Get the human-readable document path of the given Uri
     *
     * @param uri Uri to get the path for
     * @return Human-readable document path of the given Uri
     */
    private static String getDocumentPathFromUri(final Uri uri) {
        String docId;
        try {
            docId = DocumentsContract.getDocumentId(uri);
        } catch (IllegalArgumentException e) {
            docId = DocumentsContract.getTreeDocumentId(uri);
        }

        final String[] split = docId.split(":");
        if ((split.length >= 2) && (split[1] != null)) return split[1];
        else return File.separator;
    }

    /**
     * Ensure file creation from stream.
     *
     * @param stream - OutputStream
     */
    static void sync(@NonNull final FileOutputStream stream) {
        FileUtil.sync(stream);
    }

    /**
     * Create an OutputStream opened the given file
     * NB : File length will be truncated to the length of the written data
     *
     * @param target File to open the OutputStream on
     * @return New OutputStream opened on the given file
     */
    public static OutputStream getOutputStream(@NonNull final File target) throws IOException {
        return FileUtils.openOutputStream(target);
    }

    /**
     * Create an OutputStream for the given file
     * NB : File length will be truncated to the length of the written data
     *
     * @param context Context to use
     * @param target  File to open the OutputStream on
     * @return New OutputStream opened on the given file
     * @throws IOException In case something horrible happens during I/O
     */
    @Nullable
    public static OutputStream getOutputStream(@NonNull final Context context, @NonNull final DocumentFile target) throws IOException {
        return context.getContentResolver().openOutputStream(target.getUri(), "rwt"); // Always truncate file to whatever data needs to be written
    }

    /**
     * Create an OutputStream for the file at the given Uri
     * NB : File length will be truncated to the length of the written data
     *
     * @param context Context to use
     * @param fileUri Uri of the file to open the OutputStream on
     * @return New OutputStream opened on the given file
     * @throws IOException In case something horrible happens during I/O
     */
    @Nullable
    public static OutputStream getOutputStream(@NonNull final Context context, @NonNull final Uri fileUri) throws IOException {
        if (ContentResolver.SCHEME_FILE.equals(fileUri.getScheme())) {
            String path = fileUri.getPath();
            if (null != path)
                return getOutputStream(new File(fileUri.getPath()));
        } else {
            DocumentFile doc = FileHelper.getFileFromSingleUriString(context, fileUri.toString());
            if (doc != null) return getOutputStream(context, doc);
        }
        return null;
    }

    /**
     * Create an InputStream opened the given file
     *
     * @param context Context to use
     * @param target  File to open the InputStream on
     * @return New InputStream opened on the given file
     * @throws IOException In case something horrible happens during I/O
     */
    public static InputStream getInputStream(@NonNull final Context context, @NonNull final DocumentFile target) throws IOException {
        return context.getContentResolver().openInputStream(target.getUri());
    }

    /**
     * Create an InputStream opened the file at the given Uri
     *
     * @param context Context to use
     * @param fileUri Uri to open the InputStream on
     * @return New InputStream opened on the given file
     * @throws IOException In case something horrible happens during I/O
     */
    public static InputStream getInputStream(@NonNull final Context context, @NonNull final Uri fileUri) throws IOException {
        return context.getContentResolver().openInputStream(fileUri);
    }

    /**
     * Delete the given file
     *
     * @param target File to delete
     */
    public static void removeFile(File target) {
        FileUtil.deleteFile(target);
    }

    /**
     * Delete the file represented by the given Uri
     *
     * @param context Context to be used
     * @param fileUri Uri to the file to delete
     */
    public static void removeFile(@NonNull final Context context, @NonNull final Uri fileUri) {
        if (ContentResolver.SCHEME_FILE.equals(fileUri.getScheme())) {
            String path = fileUri.getPath();
            if (null != path)
                removeFile(new File(fileUri.getPath()));
        } else {
            DocumentFile doc = FileHelper.getFileFromSingleUriString(context, fileUri.toString());
            if (doc != null) doc.delete();
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

    /**
     * Check if the given folder is valid; if it is, set it as the app's root folder
     *
     * @param context Context to use
     * @param folder  Folder to check and set
     * @return 0 if the given folder is valid and has been set; -1 if the given folder is invalid; -2 if write credentials could not be set
     */
    public static int checkAndSetRootFolder(@NonNull final Context context, @NonNull final DocumentFile folder) {
        int result = createNoMedia(context, folder);
        if (0 == result) Preferences.setStorageUri(folder.getUri().toString());
        return result;
    }

    /**
     * Try to create the .nomedia file inside the given folder
     *
     * @param context Context to use
     * @param folder  Folder to create the file into
     * @return 0 if the given folder is valid and has been set; -1 if the given folder is invalid; -2 if write credentials are insufficient
     */
    public static int createNoMedia(@NonNull final Context context, @NonNull final DocumentFile folder) {
        // Validate folder
        if (!folder.exists() && !folder.isDirectory()) return -1;

        // Make sure the nomedia file is created
        DocumentFile nomedia = findFile(context, folder, NOMEDIA_FILE_NAME);
        if (null == nomedia) {
            nomedia = folder.createFile("application/octet-steam", NOMEDIA_FILE_NAME);
            if (null == nomedia || !nomedia.exists()) return -3;
        }

        // Remove and add back a test file to test if the user has the I/O rights to the selected folder
        String tempFileName = "delete." + Helper.getRandomInt(10000) + ".me";
        DocumentFile testFile = findOrCreateDocumentFile(context, folder, "application/octet-steam", tempFileName);
        if (null == testFile || !testFile.exists()) return -3;
        if (!testFile.delete()) return -2;

        return 0;
    }


    /**
     * Find the folder inside the given parent folder (non recursive) that has the given name
     *
     * @param context       Context to use
     * @param parent        Parent folder of the folder to find
     * @param subfolderName Name of the folder to find
     * @return Folder inside the given parent folder (non recursive) that has the given name; null if not found
     */
    @Nullable
    public static DocumentFile findFolder(@NonNull Context context, @NonNull DocumentFile parent, @NonNull String subfolderName) {
        return findDocumentFile(context, parent, subfolderName, true, false);
    }

    /**
     * Find the file inside the given parent folder (non recursive) that has the given name
     *
     * @param context  Context to use
     * @param parent   Parent folder of the file to find
     * @param fileName Name of the file to find
     * @return File inside the given parent folder (non recursive) that has the given name; null if not found
     */
    @Nullable
    public static DocumentFile findFile(@NonNull Context context, @NonNull DocumentFile parent, @NonNull String fileName) {
        return findDocumentFile(context, parent, fileName, false, true);
    }

    /**
     * List all subfolders inside the given parent folder (non recursive)
     *
     * @param context Context to use
     * @param parent  Parent folder to list subfolders from
     * @return Subfolders of the given parent folder
     */
    // see https://stackoverflow.com/questions/5084896/using-contentproviderclient-vs-contentresolver-to-access-content-provider
    public static List<DocumentFile> listFolders(@NonNull Context context, @NonNull DocumentFile parent) {
        return listFoldersFilter(context, parent, null);
    }

    /**
     * List all subfolders inside the given parent folder (non recursive) that match the given name filter
     *
     * @param context Context to use
     * @param parent  Parent folder to list subfolders from
     * @param filter  Name filter to use to filter the folders to list
     * @return Subfolders of the given parent folder matching the given name filter
     */
    public static List<DocumentFile> listFoldersFilter(@NonNull Context context, @NonNull DocumentFile parent, final FileHelper.NameFilter filter) {
        List<DocumentFile> result = Collections.emptyList();
        try (FileExplorer fe = new FileExplorer(context, parent)) {
            result = fe.listDocumentFiles(context, parent, filter, true, false, false);
        } catch (IOException e) {
            Timber.w(e);
        }
        return result;
    }

    /**
     * List all files (non-folders) inside the given parent folder (non recursive) that match the given name filter
     *
     * @param context Context to use
     * @param parent  Parent folder to list files from
     * @param filter  Name filter to use to filter the files to list
     * @return Files of the given parent folder matching the given name filter
     */
    public static List<DocumentFile> listFiles(@NonNull Context context, @NonNull DocumentFile parent, final FileHelper.NameFilter filter) {
        List<DocumentFile> result = Collections.emptyList();
        try (FileExplorer fe = new FileExplorer(context, parent)) {
            result = fe.listDocumentFiles(context, parent, filter, false, true, false);
        } catch (IOException e) {
            Timber.w(e);
        }
        return result;
    }

    /**
     * List the first element inside the given parent folder (non recursive) that matches the given criteria
     *
     * @param context     Context to use
     * @param parent      Parent folder to search into
     * @param nameFilter  Name filter to use to filter the element to find
     * @param listFolders True if the element to find can be a folder
     * @param listFiles   True if the element to find can be a file (i.e. non folder)
     * @return First element of the given parent folder matching the given criteria
     */
    @Nullable
    private static DocumentFile findDocumentFile(@NonNull final Context context,
                                                 @NonNull final DocumentFile parent,
                                                 final String nameFilter,
                                                 boolean listFolders,
                                                 boolean listFiles) {
        List<DocumentFile> result = Collections.emptyList();
        try (FileExplorer fe = new FileExplorer(context, parent)) {
            result = fe.listDocumentFiles(context, parent, createNameFilterEquals(nameFilter), listFolders, listFiles, true);
        } catch (IOException e) {
            Timber.w(e);
        }
        return result.isEmpty() ? null : result.get(0);
    }

    /**
     * Open the given file using the device's app(s) of choice
     *
     * @param context Context to use
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
     * @param context Context to use
     * @param aFile   File to be opened
     */
    public static void openFile(@NonNull Context context, @NonNull DocumentFile aFile) {
        String fileName = (null == aFile.getName()) ? "" : aFile.getName();
        tryOpenFile(context, aFile.getUri(), fileName, aFile.isDirectory());
    }

    /**
     * Open the given Uri using the device's app(s) of choice
     *
     * @param context Context to use
     * @param uri     Uri of the resource to be opened
     */
    public static void openUri(@NonNull Context context, @NonNull Uri uri) {
        tryOpenFile(context, uri, StringHelper.protect(uri.getLastPathSegment()), false);
    }

    /**
     * Attempt to open the file or folder at the given Uri using the device's app(s) of choice
     *
     * @param context     Context to use
     * @param uri         Uri of the file or folder to be opened
     * @param fileName    Display name of the file or folder to be opened
     * @param isDirectory true if the given Uri represents a folder; false if it represents a file
     */
    private static void tryOpenFile(@NonNull Context context, @NonNull Uri uri, @NonNull String fileName, boolean isDirectory) {
        try {
            if (isDirectory) {
                try {
                    openFileWithIntent(context, uri, DocumentsContract.Document.MIME_TYPE_DIR);
                } catch (ActivityNotFoundException e1) {
                    try {
                        openFileWithIntent(context, uri, "resource/folder");
                    } catch (ActivityNotFoundException e2) {
                        ToastHelper.toast(R.string.select_file_manager);
                        openFileWithIntent(context, uri, "*/*");
                        // TODO if it also crashes after this call, tell the user to get DocumentsUI.apk ? (see #670)
                    }
                }
            } else
                openFileWithIntent(context, uri, MimeTypeMap.getSingleton().getMimeTypeFromExtension(getExtension(fileName)));
        } catch (ActivityNotFoundException e) {
            Timber.e(e, "No activity found to open %s", uri.toString());
            ToastHelper.toastLong(context, R.string.error_open, Toast.LENGTH_LONG);
        }
    }

    /**
     * Opens the given Uri using the device's app(s) of choice
     *
     * @param context  Context to use
     * @param uri      Uri of the file or folder to be opened
     * @param mimeType Mime-type to use (determines the apps the device will suggest for opening the resource)
     */
    private static void openFileWithIntent(@NonNull Context context, @NonNull Uri uri, @Nullable String mimeType) {
        Intent myIntent = new Intent(Intent.ACTION_VIEW);
        myIntent.setDataAndTypeAndNormalize(uri, mimeType);
        myIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(myIntent);
    }

    /**
     * Returns the extension of the given filename, without the "."
     *
     * @param fileName Filename
     * @return Extension of the given filename, without the "."
     */
    public static String getExtension(@NonNull final String fileName) {
        return fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ENGLISH) : "";
    }

    /**
     * Returns the filename of the given file path, without the extension
     *
     * @param filePath File path
     * @return Name of the given file, without the extension
     */
    public static String getFileNameWithoutExtension(@NonNull final String filePath) {
        int folderSeparatorIndex = filePath.lastIndexOf(File.separator);

        String fileName;
        if (-1 == folderSeparatorIndex) fileName = filePath;
        else fileName = filePath.substring(folderSeparatorIndex + 1);

        return fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
    }

    /**
     * Save the given binary data in the given file, truncating the file length to the given data
     *
     * @param context    Context to use
     * @param uri        Uri of the file to write to
     * @param binaryData Data to write
     * @throws IOException In case something horrible happens during I/O
     */
    public static void saveBinary(@NonNull final Context context, @NonNull final Uri uri, byte[] binaryData) throws IOException {
        byte[] buffer = new byte[FILE_IO_BUFFER_SIZE];
        int count;

        try (InputStream input = new ByteArrayInputStream(binaryData)) {
            OutputStream out = FileHelper.getOutputStream(context, uri);
            if (out != null) {
                try (BufferedOutputStream output = new BufferedOutputStream(out)) {
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                    output.flush();
                }
            }
        }
    }

    /**
     * Get the relevant file extension (without the ".") from the given mime-type
     *
     * @param mimeType Mime-type to get a file extension from
     * @return Most relevant file extension (without the ".") corresponding to the given mime-type; null if none has been found
     */
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

    /**
     * Get the most relevant mime-type for the given file extension
     *
     * @param extension File extension to get the mime-type for (without the ".")
     * @return Most relevant mime-type for the given file extension; generic mime-type if none found
     */
    private static String getMimeTypeFromExtension(@NonNull String extension) {
        if (extension.isEmpty()) return "application/octet-stream";
        String result = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        if (null == result) return "application/octet-stream";
        else return result;
    }

    /**
     * Get the most relevant mime-type for the given file name
     *
     * @param fileName File name to get the mime-type for
     * @return Most relevant mime-type for the given file name; generic mime-type if none found
     */
    public static String getMimeTypeFromFileName(@NonNull String fileName) {
        return getMimeTypeFromExtension(getExtension(fileName));
    }

    /**
     * Share the given file using the device's app(s) of choice
     *
     * @param context Context to use
     * @param fileUri Uri of the file to share
     * @param title   Title of the user dialog
     */
    public static void shareFile(final @NonNull Context context, final @NonNull Uri fileUri, final @NonNull String title) {
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("text/*");
        if (!title.isEmpty()) sharingIntent.putExtra(Intent.EXTRA_SUBJECT, title);
        if (fileUri.toString().startsWith("file")) {
            Uri legitUri = FileProvider.getUriForFile(
                    context,
                    AUTHORITY,
                    new File(fileUri.toString()));
            sharingIntent.putExtra(Intent.EXTRA_STREAM, legitUri);
        } else {
            sharingIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        }
        context.startActivity(Intent.createChooser(sharingIntent, context.getString(R.string.send_to)));
    }

    /**
     * Return the position of the given sequence in the given data array
     *
     * @param data       Data where to find the sequence
     * @param initialPos Initial position to start from
     * @param sequence   Sequence to look for
     * @param limit      Limit not to cross (in bytes counted from the initial position); 0 for unlimited
     * @return Position of the sequence in the data array; -1 if not found within the given initial position and limit
     */
    static int findSequencePosition(byte[] data, int initialPos, byte[] sequence, int limit) {
        int remainingBytes;
        int iSequence = 0;

        if (initialPos < 0 || initialPos > data.length) return -1;

        remainingBytes = (limit > 0) ? Math.min(data.length - initialPos, limit) : data.length;

        for (int i = initialPos; i < remainingBytes; i++) {
            if (sequence[iSequence] == data[i]) iSequence++;
            else if (iSequence > 0) iSequence = 0;

            if (sequence.length == iSequence) return i - sequence.length;
        }

        // Target sequence not found
        return -1;
    }

    /**
     * Copy the given file to the target location, giving the copy the given name
     *
     * @param context         Context to use
     * @param sourceFileUri   Uri of the source file to copy
     * @param targetFolderUri Uri of the folder where to copy the source file
     * @param mimeType        Mime-type of the source file
     * @param newName         Filename to give of the copy
     * @return Uri of the copied file, if successful; null if failed
     * @throws IOException If something terrible happens
     */
    @Nullable
    public static Uri copyFile(
            @NonNull final Context context,
            @NonNull final Uri sourceFileUri,
            @NonNull final Uri targetFolderUri,
            @NonNull String mimeType,
            @NonNull String newName) throws IOException {
        if (!fileExists(context, sourceFileUri)) return null;
        DocumentFile targetFolder = DocumentFile.fromTreeUri(context, targetFolderUri);
        if (null == targetFolder || !targetFolder.exists()) return null;
        DocumentFile newFile = targetFolder.createFile(mimeType, newName);
        if (null == newFile || !newFile.exists()) return null;
        try (OutputStream newDownload = FileHelper.getOutputStream(context, newFile)) {
            try (InputStream input = FileHelper.getInputStream(context, sourceFileUri)) {
                Helper.copy(input, newDownload);
            }
        }
        return newFile.getUri();
    }

    /**
     * Get the device's Downloads folder
     *
     * @return Device's Downloads folder
     */
    public static File getDownloadsFolder() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }

    /**
     * Return an opened OutputStream in a brand new file created in the device's Downloads folder
     *
     * @param context  Context to use
     * @param fileName Name of the file to create
     * @param mimeType Mime-type of the file to create
     * @return Opened OutputStream in a brand new file created in the device's Downloads folder
     * @throws IOException If something horrible happens during I/O
     */
    // TODO document what happens when a file with the same name already exists there before the call
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

    /**
     * Legacy (non-SAF, pre-Android 10) version of openNewDownloadOutputStream
     * Return an opened OutputStream in a brand new file created in the device's Downloads folder
     *
     * @param fileName Name of the file to create
     * @return Opened OutputStream in a brand new file created in the device's Downloads folder
     * @throws IOException If something horrible happens during I/O
     */
    private static OutputStream openNewDownloadOutputStreamLegacy(@NonNull final String fileName) throws IOException {
        File downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (null == downloadsFolder) throw new IOException("Downloads folder not found");

        File target = new File(downloadsFolder, fileName);
        if (!target.exists() && !target.createNewFile())
            throw new IOException("Could not create new file in downloads folder");

        return getOutputStream(target);
    }

    /**
     * Android 10 version of openNewDownloadOutputStream
     * https://gitlab.com/commonsguy/download-wrangler/blob/master/app/src/main/java/com/commonsware/android/download/DownloadRepository.kt
     * Return an opened OutputStream in a brand new file created in the device's Downloads folder
     *
     * @param context  Context to use
     * @param fileName Name of the file to create
     * @param mimeType Mime-type of the file to create
     * @return Opened OutputStream in a brand new file created in the device's Downloads folder
     * @throws IOException If something horrible happens during I/O
     */
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

    /**
     * Format the given file size using human-readable units, two decimals precision
     * e.g. if the size represents more than 1M Bytes, the result is formatted as megabytes
     *
     * @param bytes Size to format, in bytes
     * @return Given file size using human-readable units, two decimals precision
     */
    public static String formatHumanReadableSize(long bytes, final Resources res) {
        return FileUtil.byteCountToDisplayRoundedSize(bytes, 2, res);
    }

    /**
     * Class to use to obtain information about memory usage
     */
    public static class MemoryUsageFigures {
        private long freeMemBytes = 0;
        private long totalMemBytes = 0;

        /**
         * Get memory usage figures for the volume containing the given folder
         *
         * @param context Context to use
         * @param f       Folder to get the figures from
         */
        public MemoryUsageFigures(@NonNull Context context, @NonNull DocumentFile f) {
            if (Build.VERSION.SDK_INT >= 26) init26(context, f);
            if (0 == totalMemBytes) init21(context, f);
            if (0 == totalMemBytes) initLegacy(context, f);
        }

        // Old way of measuring memory (inaccurate on certain devices)
        private void initLegacy(@NonNull Context context, @NonNull DocumentFile f) {
            String fullPath = getFullPathFromTreeUri(context, f.getUri()); // Oh so dirty !!
            if (fullPath != null) {
                File file = new File(fullPath);
                this.freeMemBytes = file.getFreeSpace(); // should actually have been getUsableSpace
                this.totalMemBytes = file.getTotalSpace();
            }
        }

        // Init for API 21 to 25
        private void init21(@NonNull Context context, @NonNull DocumentFile f) {
            String fullPath = getFullPathFromTreeUri(context, f.getUri()); // Oh so dirty !!
            if (fullPath != null) {
                StatFs stat = new StatFs(fullPath);

                long blockSize = stat.getBlockSizeLong();
                totalMemBytes = stat.getBlockCountLong() * blockSize;
                freeMemBytes = stat.getAvailableBlocksLong() * blockSize;
            }
        }

        // Init for API 26+
        // Inspired by https://github.com/Cheticamp/Storage_Volumes/
        @TargetApi(26)
        private void init26(@NonNull Context context, @NonNull DocumentFile f) {
            String volumeId = getVolumeIdFromUri(f.getUri());
            StorageManager mgr = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);

            List<StorageVolume> volumes = mgr.getStorageVolumes();
            StorageVolume targetVolume = null;
            StorageVolume primaryVolume = null;
            // No need to test anything, there's just one single volume
            if (1 == volumes.size()) targetVolume = volumes.get(0);
            else { // Look for a match among listed volumes
                for (StorageVolume v : volumes) {
                    if (v.isPrimary()) primaryVolume = v;

                    if (volumeIdMatch(v, StringHelper.protect(volumeId))) {
                        targetVolume = v;
                        break;
                    }
                }
            }

            // If no volume matches, default to Primary
            // NB : necessary to avoid defaulting to the root on rooted phones
            // (rooted phone's root is a separate volume with specific memory usage figures)
            if (null == targetVolume) {
                targetVolume = primaryVolume;
            }

            // Process target volume
            if (targetVolume != null) {
                if (targetVolume.isPrimary()) {
                    processPrimary(context, targetVolume);
                } else {
                    processSecondary(targetVolume);
                }
            }
        }

        // Use StorageStatsManager on primary volume
        @TargetApi(26)
        private void processPrimary(@NonNull Context context, @NonNull StorageVolume volume) {
            UUID uuid = StorageManager.UUID_DEFAULT;
            try {
                StorageStatsManager storageStatsManager =
                        (StorageStatsManager) context.getSystemService(Context.STORAGE_STATS_SERVICE);
                totalMemBytes = storageStatsManager.getTotalBytes(uuid);
                freeMemBytes = storageStatsManager.getFreeBytes(uuid);
            } catch (IOException e) {
                Timber.w(e);
            }
        }

        // StorageStatsManager doesn't work for volumes other than the primary volume since
        // the "UUID" available for non-primary volumes is not acceptable to
        // StorageStatsManager. We must revert to statvfs(path) for non-primary volumes.
        @TargetApi(26)
        private void processSecondary(@NonNull StorageVolume volume) {
            try {
                String volumePath = getVolumePath(volume);
                if (!volumePath.isEmpty()) {
                    StructStatVfs stats = Os.statvfs(volumePath);
                    long blockSize = stats.f_bsize;
                    totalMemBytes = stats.f_blocks * blockSize;
                    freeMemBytes = stats.f_bavail * blockSize;
                }
            } catch (Exception e) { // On some devices, Os.statvfs can throw other exceptions than ErrnoException
                Timber.w(e);
            }
        }

        /**
         * Get free usage ratio (0 = all memory full; 100 = all memory free)
         */
        public double getFreeUsageRatio100() {
            return freeMemBytes * 100.0 / totalMemBytes;
        }

        /**
         * Get total storage capacity in bytes
         */
        public long getTotalSpaceBytes() {
            return totalMemBytes;
        }

        /**
         * Get free storage capacity in bytes
         */
        public long getfreeUsageBytes() {
            return freeMemBytes;
        }
    }

    /**
     * Indicate whether the given volume IDs match
     *
     * @param volume       Volume to compare against
     * @param treeVolumeId Volume ID extracted from an Uri
     * @return True if both IDs match
     */
    @TargetApi(26)
    private static boolean volumeIdMatch(@NonNull final StorageVolume volume, @NonNull final String treeVolumeId) {
        return volumeIdMatch(StringHelper.protect(volume.getUuid()), volume.isPrimary(), treeVolumeId);
    }

    /**
     * Indicate whether the given volume IDs match
     *
     * @param volumeUuid      Volume UUID to compare against
     * @param isVolumePrimary True if the volume to compare against is primary
     * @param treeVolumeId    Volume ID extracted from an Uri
     * @return True if the given volume matches the given volume ID
     */
    private static boolean volumeIdMatch(@NonNull final String volumeUuid, boolean isVolumePrimary, @NonNull final String treeVolumeId) {
        if (volumeUuid.equals(treeVolumeId.replace("/", ""))) return true;
        else return (isVolumePrimary && treeVolumeId.equals(PRIMARY_VOLUME_NAME));
    }

    /**
     * Reset the app's persisted I/O permissions :
     * - persist I/O permissions for the given new Uri
     * - keep existing persisted I/O permissions for the given optional Uri
     * <p>
     * NB : if the optional Uri has no persisted permissions, this call won't create them
     *
     * @param context Context to use
     * @param newUri  New Uri to add to the persisted I/O permission
     * @param keepUri Uri to keep in the persisted I/O permissions, if already set
     */
    public static void persistNewUriPermission(@NonNull final Context context, @NonNull final Uri newUri, @Nullable final Uri keepUri) {
        ContentResolver contentResolver = context.getContentResolver();
        if (!isUriPermissionPersisted(contentResolver, newUri)) {
            Timber.d("Persisting Uri permission for %s", newUri);
            // Release previous access permissions, if different than the new one
            revokePreviousPermissions(contentResolver, newUri, keepUri);
            // Persist new access permission
            contentResolver.takePersistableUriPermission(newUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }
    }

    /**
     * Check if the given Uri has persisted I/O permissions
     *
     * @param resolver ContentResolver to use
     * @param uri      Uri to check
     * @return true if the given Uri has persisted I/O permissions
     */
    public static boolean isUriPermissionPersisted(@NonNull final ContentResolver resolver, @NonNull final Uri uri) {
        String treeUriId = DocumentsContract.getTreeDocumentId(uri);
        for (UriPermission p : resolver.getPersistedUriPermissions()) {
            if (DocumentsContract.getTreeDocumentId(p.getUri()).equals(treeUriId)) {
                Timber.d("Uri permission already persisted for %s", uri);
                return true;
            }
        }
        return false;
    }

    /**
     * Revoke persisted Uri I/O permissions to the exception of given Uri's
     *
     * @param resolver   ContentResolver to use
     * @param exceptions Uri's whose permissions won't be revoked
     */
    private static void revokePreviousPermissions(@NonNull final ContentResolver resolver, @NonNull final Uri... exceptions) {
        // Unfortunately, the content Uri of the selected resource is not exactly the same as the one stored by ContentResolver
        // -> solution is to compare their TreeDocumentId instead
        List<String> exceptionIds = Stream.of(exceptions).withoutNulls().map(DocumentsContract::getTreeDocumentId).toList();
        for (UriPermission p : resolver.getPersistedUriPermissions())
            if (!exceptionIds.contains(DocumentsContract.getTreeDocumentId(p.getUri())))
                resolver.releasePersistableUriPermission(p.getUri(),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        if (resolver.getPersistedUriPermissions().size() <= exceptionIds.size()) {
            Timber.d("Permissions revoked successfully");
        } else {
            Timber.d("Failed to revoke permissions");
        }
    }


    /**
     * Return the content of the given file as an UTF-8 string
     * Leading BOMs are ignored
     *
     * @param context Context to be used
     * @param f       File to read from
     * @return Content of the given file as a string; empty string if an error occurred
     */
    static String readFileAsString(@NonNull final Context context, @NonNull DocumentFile f) {
        try {
            return readStreamAsString(FileHelper.getInputStream(context, f));
        } catch (IOException | IllegalArgumentException e) {
            Timber.e(e, "Error while reading %s", f.getUri().toString());
        }
        return "";
    }

    /**
     * Read the given InputStream as a continuous string, ignoring line breaks and BOMs
     * WARNING : Designed to be used on text files only
     *
     * @param input InputStream to read from
     * @return String built from the given InputStream
     * @throws IOException              In case something horrible happens
     * @throws IllegalArgumentException In case something horrible happens
     */
    public static String readStreamAsString(@NonNull final InputStream input) throws IOException, IllegalArgumentException {
        StringBuilder result = new StringBuilder();
        String sCurrentLine;
        boolean isFirst = true;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(input))) {
            while ((sCurrentLine = br.readLine()) != null) {
                if (isFirst) {
                    // Strip UTF-8 BOMs if any
                    if (sCurrentLine.charAt(0) == '\uFEFF')
                        sCurrentLine = sCurrentLine.substring(1);
                    isFirst = false;
                }
                result.append(sCurrentLine);
            }
        }
        return result.toString();
    }

    /**
     * Indicate whether the file at the given Uri exists or not
     *
     * @param context Context to be used
     * @param fileUri Uri to the file whose existence is to check
     * @return True if the given Uri points to an existing file; false instead
     */
    public static boolean fileExists(@NonNull final Context context, @NonNull final Uri fileUri) {
        if (ContentResolver.SCHEME_FILE.equals(fileUri.getScheme())) {
            String path = fileUri.getPath();
            if (path != null)
                return new File(path).exists();
            else return false;
        } else {
            DocumentFile doc = FileHelper.getFileFromSingleUriString(context, fileUri.toString());
            return (doc != null);
        }
    }

    /**
     * Return the size of the file at the given Uri, in bytes
     *
     * @param context Context to be used
     * @param fileUri Uri to the file whose size to retrieve
     * @return Size of the file at the given Uri; -1 if it cannot be found
     */
    public static long fileSizeFromUri(@NonNull final Context context, @NonNull final Uri fileUri) {
        if (ContentResolver.SCHEME_FILE.equals(fileUri.getScheme())) {
            String path = fileUri.getPath();
            if (path != null) return new File(path).length();
        } else {
            DocumentFile doc = FileHelper.getFileFromSingleUriString(context, fileUri.toString());
            if (doc != null) return doc.length();
        }
        return -1;
    }

    /**
     * Get a valid Uri for the given File
     *
     * @param context Context to use
     * @param file    File to get the Uri for
     * @return Valid Uri
     */
    public static Uri getFileUriCompat(@NonNull final Context context, @NonNull final File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return FileProvider.getUriForFile(context, AUTHORITY, file);
        } else {
            return Uri.fromFile(file);
        }
    }

    /**
     * Remove all illegal characters from the given string to make it a valid Android file name
     *
     * @param fileName String to clean up
     * @return Cleaned string
     */
    public static String cleanFileName(@NonNull final String fileName) {
        return fileName.replaceAll(ILLEGAL_FILENAME_CHARS, "");
    }

    /**
     * Empty the given subfolder inside the cache folder
     *
     * @param context    Context to use
     * @param folderName Name of the subfolder to empty
     */
    public static void emptyCacheFolder(@NonNull Context context, @NonNull String folderName) {
        File cacheFolder = getOrCreateCacheFolder(context, folderName);
        if (cacheFolder != null) {
            File[] files = cacheFolder.listFiles();
            if (files != null)
                for (File f : files)
                    if (!f.delete()) Timber.w("Unable to delete file %s", f.getAbsolutePath());
        }
    }

    /**
     * Retrieve or create the subfolder with the given name inside the cache folder
     *
     * @param context    Context to use
     * @param folderName Name of the subfolder to retrieve or create; may contain subfolders separated by File.separator
     * @return Subfolder as a File, or null if it couldn't be found nor created
     */
    @Nullable
    public static File getOrCreateCacheFolder(@NonNull Context context, @NonNull String folderName) {
        File root = context.getCacheDir();
        String[] subfolders = folderName.split(File.separator);
        for (String subfolderName : subfolders) {
            File cacheFolder = new File(root, subfolderName);
            if (cacheFolder.exists()) root = cacheFolder;
            else if (cacheFolder.mkdir()) root = cacheFolder;
            else return null;
        }
        return root;
    }

    public static void getAssetAsString(@NonNull AssetManager mgr, @NonNull String assetName, @NonNull StringBuilder sb) {
        try (InputStream is = mgr.open(assetName); BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String sCurrentLine;
            while ((sCurrentLine = br.readLine()) != null) {
                sb.append(sCurrentLine).append(System.lineSeparator());
            }
        } catch (Exception e) {
            Timber.e(e);
        }
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
