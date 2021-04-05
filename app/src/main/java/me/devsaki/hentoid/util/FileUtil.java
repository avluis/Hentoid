package me.devsaki.hentoid.util;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.CachedDocumentFile;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * Created by avluis on 08/25/2016.
 * Methods for use by FileHelper
 */
class FileUtil {

    private FileUtil() {
        throw new IllegalStateException("Utility class");
    }

    private static Constructor<?> treeDocumentFileConstructor = null;

    private static final String DOCPROVIDER_PATH_DOCUMENT = "document";
    private static final String DOCPROVIDER_PATH_TREE = "tree";

    private static final Map<String, Boolean> providersCache = new HashMap<>();
    private static final Map<String, String> documentIdCache = new HashMap<>();


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

    /**
     * Count the children of a given folder (non recursive) matching the given criteria
     *
     * @param parent       Folder containing the document to count
     * @param client       ContentProviderClient to use for the query
     * @param nameFilter   NameFilter defining which documents to include
     * @param countFolders true if matching folders have to be counted in the results
     * @param countFiles   true if matching files have to be counted in the results
     * @return Number of documents inside the given folder, matching the given criteria
     */
    static int countDocumentFiles(
            @NonNull final DocumentFile parent,
            @NonNull final ContentProviderClient client,
            final FileHelper.NameFilter nameFilter,
            boolean countFolders,
            boolean countFiles) {
        final List<DocumentProperties> results = queryDocumentFiles(parent, client, nameFilter, countFolders, countFiles);
        return results.size();
    }

    /**
     * List the children of a given folder (non recursive) matching the given criteria
     *
     * @param context     Context to use for the query
     * @param parent      Folder containing the document to count
     * @param client      ContentProviderClient to use for the query
     * @param nameFilter  NameFilter defining which documents to include
     * @param listFolders true if matching folders have to be listed in the results
     * @param listFiles   true if matching files have to be listed in the results
     * @return List of documents inside the given folder, matching the given criteria
     */
    static List<DocumentFile> listDocumentFiles(
            @NonNull final Context context,
            @NonNull final DocumentFile parent,
            @NonNull final ContentProviderClient client,
            final FileHelper.NameFilter nameFilter,
            boolean listFolders,
            boolean listFiles) {
        final List<DocumentProperties> results = queryDocumentFiles(parent, client, nameFilter, listFolders, listFiles);
        return convertFromProperties(context, results);
    }

    /**
     * List the properties of the children of the given folder (non recursive) matching the given criteria
     *
     * @param parent      Folder containing the document to count
     * @param client      ContentProviderClient to use for the query
     * @param nameFilter  NameFilter defining which documents to include
     * @param listFolders true if matching folders have to be listed in the results
     * @param listFiles   true if matching files have to be listed in the results
     * @return List of properties of the children of the given folder, matching the given criteria
     */
    private static List<DocumentProperties> queryDocumentFiles(
            @NonNull final DocumentFile parent,
            @NonNull final ContentProviderClient client,
            final FileHelper.NameFilter nameFilter,
            boolean listFolders,
            boolean listFiles) {
        final List<DocumentProperties> results = new ArrayList<>();

        final Uri searchUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent.getUri(), DocumentsContract.getDocumentId(parent.getUri()));
        try (Cursor c = client.query(searchUri, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE}, null, null, null)) {
            if (c != null)
                while (c.moveToNext()) {
                    final String documentId = c.getString(0);
                    final String documentName = c.getString(1);
                    boolean isFolder = c.getString(2).equals(DocumentsContract.Document.MIME_TYPE_DIR);
                    final long documentSize = c.getLong(3);

                    // FileProvider doesn't take query selection arguments into account, so the selection has to be done manually
                    if ((null == nameFilter || nameFilter.accept(documentName)) && ((listFiles && !isFolder) || (listFolders && isFolder)))
                        results.add(new DocumentProperties(buildDocumentUriUsingTreeCached(parent.getUri(), documentId), documentName, documentSize, isFolder));
                }
        } catch (Exception e) {
            Timber.w(e, "Failed query");
        }
        return results;
    }

    /**
     * Convert the given document properties to DocumentFile's
     *
     * @param context    Context to use for the conversion
     * @param properties Properties to convert
     * @return List of DocumentFile's built from the given properties
     */
    private static List<DocumentFile> convertFromProperties(@NonNull final Context context, @NonNull final List<DocumentProperties> properties) {
        final List<DocumentFile> resultFiles = new ArrayList<>();
        for (DocumentProperties result : properties) {
            DocumentFile docFile = fromTreeUriCached(context, result.uri);
            // Following line should be the proper way to go but it's inefficient as it calls queryIntentContentProviders from scratch repeatedly
            //DocumentFile docFile = DocumentFile.fromTreeUri(context, uri.left);
            if (docFile != null)
                resultFiles.add(new CachedDocumentFile(docFile, result.name, result.size, result.isDirectory));
        }
        return resultFiles;
    }

    /**
     * WARNING Following methods are tweaks of internal Android code to make it faster by caching calls to queryIntentContentProviders
     */

    // Original (uncached) is DocumentsContract.buildDocumentUriUsingTree
    // NB : appendPath got costly because of encoding operations
    public static Uri buildDocumentUriUsingTreeCached(Uri treeUri, String documentId) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(treeUri.getAuthority()).appendPath("tree") // Risky; this value is supposed to be hidden
                .appendPath(getTreeDocumentIdCached(treeUri)).appendPath("document") // Risky; this value is supposed to be hidden
                .appendPath(documentId).build();
    }


    // Original (uncached) is DocumentFile.fromTreeUri
    @Nullable
    private static DocumentFile fromTreeUriCached(@NonNull final Context context, @NonNull final Uri treeUri) {
        String documentId = getTreeDocumentIdCached(treeUri);
        if (isDocumentUriCached(context, treeUri)) {
            documentId = DocumentsContract.getDocumentId(treeUri);
        }
        return newTreeDocumentFile(null, context,
                buildDocumentUriUsingTreeCached(treeUri, documentId));
        //DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId));
    }

    // Original (uncached) is DocumentsContract.getTreeDocumentId
    private static String getTreeDocumentIdCached(@NonNull final Uri uri) {
        String uriStr = uri.toString();
        // First look into cache
        String result = documentIdCache.get(uri.toString());
        // If nothing found, try the long way
        if (null == result) {
            result = DocumentsContract.getTreeDocumentId(uri);
            documentIdCache.put(uriStr, result);
        }
        return result;
    }

    // Original (uncached) : DocumentsContract.isDocumentUri
    private static boolean isDocumentUriCached(@NonNull final Context context, @Nullable final Uri uri) {
        if (isContentUri(uri) && isDocumentsProviderCached(context, uri.getAuthority())) {
            final List<String> paths = uri.getPathSegments();
            if (paths.size() == 2) {
                return DOCPROVIDER_PATH_DOCUMENT.equals(paths.get(0));
            } else if (paths.size() == 4) {
                return DOCPROVIDER_PATH_TREE.equals(paths.get(0)) && DOCPROVIDER_PATH_DOCUMENT.equals(paths.get(2));
            }
        }
        return false;
    }

    // Original (uncached) : DocumentsContract.isDocumentsProvider
    private static boolean isDocumentsProviderCached(Context context, String authority) {
        // First look into cache
        Boolean b = providersCache.get(authority);
        if (b != null) return b;
        // If nothing found, try the long way
        final Intent intent = new Intent(DocumentsContract.PROVIDER_INTERFACE);
        final List<ResolveInfo> infos = context.getPackageManager()
                .queryIntentContentProviders(intent, 0);
        for (ResolveInfo info : infos) {
            if (authority.equals(info.providerInfo.authority)) {
                providersCache.put(authority, true);
                return true;
            }
        }
        providersCache.put(authority, false);
        return false;
    }

    // Original : DocumentsContract.isContentUri
    private static boolean isContentUri(Uri uri) {
        return uri != null && ContentResolver.SCHEME_CONTENT.equals(uri.getScheme());
    }

    @Nullable
    private static DocumentFile newTreeDocumentFile(@Nullable final DocumentFile parent, @NonNull final Context context, @NonNull final Uri uri) {
        //resultFiles[i] = new TreeDocumentFile(this, context, result[i]); <-- not visible
        try {
            if (null == treeDocumentFileConstructor) {
                Class<?> treeDocumentFileClazz = Class.forName("androidx.documentfile.provider.TreeDocumentFile");
                treeDocumentFileConstructor = treeDocumentFileClazz.getDeclaredConstructor(DocumentFile.class, Context.class, Uri.class);
                treeDocumentFileConstructor.setAccessible(true);
            }
            return (DocumentFile) treeDocumentFileConstructor.newInstance(parent, context, uri);
        } catch (Exception ex) {
            Timber.e(ex);
        }
        return null;
    }

    /**
     * Properties of a stored document
     */
    static class DocumentProperties {
        final Uri uri;
        final String name;
        final long size;
        final boolean isDirectory;

        public DocumentProperties(Uri uri, String name, long size, boolean isDirectory) {
            this.uri = uri;
            this.name = name;
            this.size = size;
            this.isDirectory = isDirectory;
        }
    }

}
