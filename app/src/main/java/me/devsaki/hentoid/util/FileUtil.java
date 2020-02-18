package me.devsaki.hentoid.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.HentoidApp;
import timber.log.Timber;

/**
 * Created by avluis on 08/25/2016.
 * Methods for use by FileHelper
 */
class FileUtil {

    private FileUtil() {
        throw new IllegalStateException("Utility class");
    }

    private static Constructor treeDocumentFileConstructor = null;


    /**
     * Method ensures file creation from stream.
     *
     * @param stream - FileOutputStream.
     * @return true if all OK.
     */
    static boolean sync(@NonNull final FileOutputStream stream) {
        try {
            stream.getFD().sync();
            return true;
        } catch (IOException e) {
            Timber.e(e, "IO Error");
        }

        return false;
    }

    /**
     * Get the DocumentFile corresponding to the given file.
     * If the file does not exist, null is returned.
     *
     * @param file        The file.
     * @param isDirectory flag indicating if the given file should be a directory.
     * @return The DocumentFile.
     */
    @Nullable
    static DocumentFile getDocumentFile(@Nonnull final File file, final boolean isDirectory) {
        return getOrCreateDocumentFile(file, isDirectory, false);
    }

    /**
     * Get the DocumentFile corresponding to the given file.
     * If the file does not exist, null is returned.
     *
     * @param file        The file.
     * @param isDirectory flag indicating if the given file should be a directory.
     * @return The DocumentFile.
     */
    @Nullable
    private static DocumentFile getOrCreateDocumentFile(@Nonnull final File file, boolean isDirectory, boolean canCreate) {
        String baseFolder = FileHelper.getExtSdCardFolder(file);
        boolean returnSDRoot = false;

        // File is from phone memory
        if (baseFolder == null) return DocumentFile.fromFile(file);

        String relativePath = ""; // Path of the file relative to baseFolder
        try {
            String fullPath = file.getCanonicalPath();

            if (!baseFolder.equals(fullPath)) { // Selected file _is_ the base folder
                relativePath = fullPath.substring(baseFolder.length() + 1);
            } else {
                returnSDRoot = true;
            }
        } catch (IOException e) {
            return null;
        } catch (Exception f) {
            returnSDRoot = true;
            //continue
        }

        String sdStorageUriStr = Preferences.getStorageUri();
        if (sdStorageUriStr.isEmpty()) return null;

        Uri sdStorageUri = Uri.parse(sdStorageUriStr);

        // Shorten relativePath if part of it is already in sdStorageUri
        String[] uriContents = sdStorageUri.getPath().split(":");
        if (uriContents.length > 1) {
            String relativeUriPath = uriContents[1];
            if (relativePath.equals(relativeUriPath)) {
                relativePath = "";
            } else if (relativePath.startsWith(relativeUriPath)) {
                relativePath = relativePath.substring(relativeUriPath.length() + 1);
            }
        }

        return getOrCreateFromComponents(sdStorageUri, returnSDRoot, relativePath, isDirectory, canCreate);
    }

    /**
     * Get the DocumentFile corresponding to the given elements.
     * If it does not exist, it is created.
     *
     * @param rootURI      Uri representing root
     * @param returnRoot   True if method has just to return the DocumentFile representing the given root
     * @param relativePath Relative path to the Document to be found/created (relative to given root)
     * @param isDirectory  True if the given elements are supposed to be a directory; false if they are supposed to be a file
     * @param canCreate    Behaviour when not found : True => creates a new file/folder / False => returns null
     * @return DocumentFile corresponding to the given file.
     */
    @Nullable
    private static DocumentFile getOrCreateFromComponents(@Nonnull Uri rootURI, boolean returnRoot,
                                                          String relativePath, boolean isDirectory,
                                                          boolean canCreate) {
        // start with root and then parse through document tree.
        Context context = HentoidApp.getInstance();
        DocumentFile document = DocumentFile.fromTreeUri(context, rootURI);
        if (null == document) return null;

        if (returnRoot || null == relativePath || relativePath.isEmpty()) return document;

        String[] parts = relativePath.split(File.separator);
        for (int i = 0; i < parts.length; i++) {
            //DocumentFile nextDocument = document.findFile(parts[i]);
            DocumentFile nextDocument = FileHelper.findDocumentFile(context, document, parts[i]);

            // The folder might exist in its capitalized version (might happen with legacy installs from the FakkuDroid era)
            if (null == nextDocument)
                nextDocument = FileHelper.findDocumentFile(context, document, Helper.capitalizeString(parts[i]));
                //nextDocument = document.findFile(Helper.capitalizeString(parts[i]));

            // The folder definitely doesn't exist at all
            if (null == nextDocument) {
                if (canCreate) {
                    Timber.d("Document %s - part #%s : '%s' not found; creating", document.getName(), String.valueOf(i), parts[i]);

                    if ((i < parts.length - 1) || isDirectory) {
                        nextDocument = document.createDirectory(parts[i]);
                        if (null == nextDocument) {
                            Timber.e("Failed to create subdirectory %s/%s", document.getName(), parts[i]);
                        }
                    } else {
                        nextDocument = document.createFile("image", parts[i]);
                        if (null == nextDocument) {
                            Timber.e("Failed to create file %s/image%s", document.getName(), parts[i]);
                        }
                    }
                } else {
                    return null;
                }
            }
            document = nextDocument;
            if (null == document) break;
        }

        return document;
    }

    static OutputStream getOutputStream(@NonNull final DocumentFile target) throws FileNotFoundException {
        Context context = HentoidApp.getInstance();
        return context.getContentResolver().openOutputStream(target.getUri());
    }

    static InputStream getInputStream(@NonNull final DocumentFile target) throws IOException {
        Context context = HentoidApp.getInstance();
        return context.getContentResolver().openInputStream(target.getUri());
    }

    /**
     * Create a folder.
     *
     * @param file The folder to be created.
     * @return true if creation was successful or the folder already exists
     */
    static boolean makeDir(@NonNull final File file) {
        // Nothing to create ?
        if (file.exists()) return file.isDirectory();

        // Try the normal way
        return file.mkdirs();
    }

    /**
     * Delete a file.
     *
     * @param file The file to be deleted.
     * @return true if successfully deleted or if the file does not exist.
     */
    static boolean deleteFile(@NonNull final File file) {
        return !file.exists() || deleteQuietly(file);
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

    /**
     * Cleans a directory without deleting it.
     * <p>
     * Custom substitute for commons.io.FileUtils.cleanDirectory that supports devices without File.toPath
     *
     * @param directory directory to clean
     * @return true if directory has been successfully cleaned
     * @throws IOException in case cleaning is unsuccessful
     */
    static boolean tryCleanDirectory(@NonNull File directory) throws IOException {
        File[] files = directory.listFiles();
        if (files == null) throw new IOException("Failed to list content of " + directory);

        boolean isSuccess = true;

        for (File file : files) {
            if (file.isDirectory() && !tryCleanDirectory(file)) isSuccess = false;
            if (!file.delete() && file.exists()) isSuccess = false;
        }

        return isSuccess;
    }

    public static List<DocumentFile> listFilesDefault(@NonNull final Context context, @NonNull final DocumentFile parent) {
        final ContentResolver resolver = context.getContentResolver();
        final Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent.getUri(),
                DocumentsContract.getDocumentId(parent.getUri()));
        final List<Uri> results = new ArrayList<>();

        try (Cursor c = resolver.query(childrenUri, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID}, null, null, null)) {
            if (c != null)
                while (c.moveToNext()) {
                    final String documentId = c.getString(0);
                    final Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(parent.getUri(), documentId);
                    results.add(documentUri);
                }
        } catch (Exception e) {
            Timber.w(e, "Failed query");
        }
        return convertFromUris(context, parent, results);
    }

    static List<DocumentFile> listFolders(@NonNull final Context context, @NonNull final DocumentFile parent, final String nameFilter) {
        return listDocumentFiles(context, parent, nameFilter, true, false);
    }

    static List<DocumentFile> listFiles(@NonNull final Context context, @NonNull final DocumentFile parent, final String nameFilter) {
        return listDocumentFiles(context, parent, nameFilter, false, true);
    }

    static List<DocumentFile> listDocumentFiles(@NonNull final Context context, @NonNull final DocumentFile parent, final String nameFilter) {
        return listDocumentFiles(context, parent, nameFilter, true, true);
    }

    private static List<DocumentFile> listDocumentFiles(@NonNull final Context context, @NonNull final DocumentFile parent, final String nameFilter, boolean listFolders, boolean listFiles) {
        final ContentResolver resolver = context.getContentResolver();
        /*
        final Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent.getUri(),
                DocumentsContract.getDocumentId(parent.getUri()));
         */
        final Uri searchUri = DocumentsContract.buildChildDocumentsUri(FileHelper.getFileProviderAuthority(), DocumentsContract.getDocumentId(parent.getUri()));
        final List<Uri> results = new ArrayList<>();

        String selectionClause = null;
        if (listFolders && !listFiles)
            selectionClause = DocumentsContract.Document.COLUMN_MIME_TYPE + "=" + DocumentsContract.Document.MIME_TYPE_DIR;
        else if (!listFolders && listFiles)
            selectionClause = DocumentsContract.Document.COLUMN_MIME_TYPE + "!=" + DocumentsContract.Document.MIME_TYPE_DIR;

        String[] selectionArgs = null;
        if (nameFilter != null) {
            if (selectionClause != null) selectionClause += " AND ";
            else selectionClause = "";
            selectionClause += DocumentsContract.Document.COLUMN_DISPLAY_NAME + "=" + nameFilter;
        }

        try (Cursor c = resolver.query(searchUri, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID}, selectionClause, selectionArgs, null)) {
            if (c != null)
                while (c.moveToNext()) {
                    final String documentId = c.getString(0);
                    final Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(parent.getUri(), documentId);
                    results.add(documentUri);
                }
        } catch (Exception e) {
            Timber.w(e, "Failed query");
        }
        return convertFromUris(context, parent, results);
    }

    private static List<DocumentFile> convertFromUris(@NonNull final Context context, @NonNull final DocumentFile parent, @NonNull final List<Uri> uris) {
        final List<DocumentFile> resultFiles = new ArrayList<>();
        for (Uri uri : uris) {
            DocumentFile docFile = newTreeDocumentFile(parent, context, uri);
            if (docFile != null) resultFiles.add(docFile);
        }
        return resultFiles;
    }

    @Nullable
    private static DocumentFile newTreeDocumentFile(@NonNull final DocumentFile parent, @NonNull final Context context, @NonNull final Uri uri) {
        try {
            if (null == treeDocumentFileConstructor) {
                Class<?> treeDocumentFileClazz = Class.forName("androidx.documentfile.provider.TreeDocumentFile");
                treeDocumentFileConstructor = treeDocumentFileClazz.getConstructor(DocumentFile.class, Context.class, Uri.class);
            }
            return (DocumentFile) treeDocumentFileConstructor.newInstance(parent, context, uri);
            //resultFiles[i] = new TreeDocumentFile(this, context, result[i]);
        } catch (Exception ex) {
            Timber.e(ex);
        }
        return null;
    }

}
