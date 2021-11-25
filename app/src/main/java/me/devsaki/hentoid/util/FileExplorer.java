package me.devsaki.hentoid.util;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.CachedDocumentFile;
import androidx.documentfile.provider.DocumentFile;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

public class FileExplorer implements Closeable {

    private static Constructor<?> treeDocumentFileConstructor = null;

    // Risky; these values are supposed to be hidden
    private static final String DOCPROVIDER_PATH_DOCUMENT = "document";
    private static final String DOCPROVIDER_PATH_TREE = "tree";

    private static final Map<String, Boolean> providersCache = new HashMap<>();
    private final Map<String, String> documentIdCache = new HashMap<>();

    private final ContentProviderClient client;


    private static synchronized void setTreeDocumentFileConstructor(@NonNull Constructor<?> value) {
        treeDocumentFileConstructor = value;
    }


    public FileExplorer(@NonNull final Context context, @NonNull final DocumentFile parent) {
        client = context.getContentResolver().acquireContentProviderClient(parent.getUri());
    }

    public FileExplorer(@NonNull final Context context, @NonNull final Uri parentUri) {
        client = context.getContentResolver().acquireContentProviderClient(parentUri);
    }

    @Override
    public void close() throws IOException {
        documentIdCache.clear();

        // ContentProviderClient.close only available on API level 24+
        if (client != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                client.close();
            else
                client.release();
        }
    }


    /**
     * List all subfolders inside the given parent folder (non recursive)
     *
     * @param context Context to use
     * @param parent  Parent folder to list subfolders from
     * @return Subfolders of the given parent folder
     */
    public List<DocumentFile> listFolders(@NonNull Context context, @NonNull DocumentFile parent) {
        return listDocumentFiles(context, parent, null, true, false, false);
    }

    /**
     * Returns true if the given parent folder contains at least one subfolder
     *
     * @param parent Parent folder to test
     * @return True if the given parent folder contains at least one subfolder; false instead
     */
    public boolean hasFolders(@NonNull DocumentFile parent) {
        return countDocumentFiles(parent, null, true, false, true) > 0;
    }

    /**
     * List all files (non-folders) inside the given parent folder (non recursive) that match the given name filter
     *
     * @param context Context to use
     * @param parent  Parent folder to list files from
     * @param filter  Name filter to use to filter the files to list
     * @return Files of the given parent folder matching the given name filter
     */
    public List<DocumentFile> listFiles(@NonNull Context context, @NonNull DocumentFile parent, final FileHelper.NameFilter filter) {
        return listDocumentFiles(context, parent, filter, false, true, false);
    }

    /**
     * Count all files (non-folders) inside the given parent folder (non recursive) that match the given name filter
     *
     * @param parent Parent folder to count files from
     * @param filter Name filter to use to filter the files to count
     * @return Number of files inside the given parent folder matching the given name filter
     */
    public int countFiles(@NonNull DocumentFile parent, final FileHelper.NameFilter filter) {
        return countDocumentFiles(parent, filter, false, true);
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
    public DocumentFile findFolder(@NonNull Context context, @NonNull DocumentFile parent, @NonNull String subfolderName) {
        List<DocumentFile> result = listDocumentFiles(context, parent, createNameFilterEquals(subfolderName), true, false, true);
        if (!result.isEmpty()) return result.get(0);
        else return null;
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
    public DocumentFile findFile(@NonNull Context context, @NonNull DocumentFile parent, @NonNull String fileName) {
        List<DocumentFile> result = listDocumentFiles(context, parent, createNameFilterEquals(fileName), false, true, true);
        if (!result.isEmpty()) return result.get(0);
        else return null;
    }


    /**
     * List all folders _and_ files inside the given parent folder (non recursive)
     *
     * @param context Context to use
     * @param parent  Parent folder to list elements from
     * @return Folders and files of the given parent folder
     */
    public List<DocumentFile> listDocumentFiles(@NonNull final Context context,
                                                @NonNull final DocumentFile parent) {
        return listDocumentFiles(context, parent, null, true, true, false);
    }


    /**
     * Count the children of a given folder (non recursive) matching the given criteria
     *
     * @param parent       Folder containing the document to count
     * @param nameFilter   NameFilter defining which documents to include
     * @param countFolders true if matching folders have to be counted in the results
     * @param countFiles   true if matching files have to be counted in the results
     * @return Number of documents inside the given folder, matching the given criteria
     */
    int countDocumentFiles(
            @NonNull final DocumentFile parent,
            final FileHelper.NameFilter nameFilter,
            boolean countFolders,
            boolean countFiles) {
        return countDocumentFiles(parent, nameFilter, countFolders, countFiles, false);
    }

    /**
     * List the children of a given folder (non recursive) matching the given criteria
     *
     * @param context     Context to use for the query
     * @param parent      Folder containing the document to count
     * @param nameFilter  NameFilter defining which documents to include
     * @param listFolders true if matching folders have to be listed in the results
     * @param listFiles   true if matching files have to be listed in the results
     * @return List of documents inside the given folder, matching the given criteria
     */
    List<DocumentFile> listDocumentFiles(
            @NonNull final Context context,
            @NonNull final DocumentFile parent,
            final FileHelper.NameFilter nameFilter,
            boolean listFolders,
            boolean listFiles,
            boolean stopFirst) {
        final List<DocumentProperties> results = queryDocumentFiles(parent, nameFilter, listFolders, listFiles, stopFirst);
        return convertFromProperties(context, results);
    }

    private Cursor getCursorFor(Uri rootFolderUri) throws RemoteException {
        final Uri searchUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootFolderUri, DocumentsContract.getDocumentId(rootFolderUri));
        return client.query(searchUri, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE}, null, null, null);
    }

    /**
     * List the properties of the children of the given folder (non recursive) matching the given criteria
     *
     * @param parent      Folder containing the document to list
     * @param nameFilter  NameFilter defining which documents to include
     * @param listFolders true if matching folders have to be listed in the results
     * @param listFiles   true if matching files have to be listed in the results
     * @param stopFirst   true to stop at the first match (useful to optimize when the point is to check for emptiness)
     * @return List of properties of the children of the given folder, matching the given criteria
     */
    private List<DocumentProperties> queryDocumentFiles(
            @NonNull final DocumentFile parent,
            final FileHelper.NameFilter nameFilter,
            boolean listFolders,
            boolean listFiles,
            boolean stopFirst) {
        if (null == client) return Collections.emptyList();
        final List<DocumentProperties> results = new ArrayList<>();

        try (Cursor c = getCursorFor(parent.getUri())) {
            if (c != null)
                while (c.moveToNext()) {
                    final String documentId = c.getString(0);
                    final String documentName = c.getString(1);
                    boolean isFolder = c.getString(2).equals(DocumentsContract.Document.MIME_TYPE_DIR);
                    final long documentSize = c.getLong(3);

                    // FileProvider doesn't take query selection arguments into account, so the selection has to be done manually
                    if ((null == nameFilter || nameFilter.accept(documentName)) && ((listFiles && !isFolder) || (listFolders && isFolder)))
                        results.add(new DocumentProperties(buildDocumentUriUsingTreeCached(parent.getUri(), documentId), documentName, documentSize, isFolder));

                    // Don't do the whole loop if the point is to find a single element
                    if (stopFirst && !results.isEmpty()) break;
                }
        } catch (Exception e) {
            Timber.w(e, "Failed query");
        }
        return results;
    }

    /**
     * Count the children of the given folder (non recursive) matching the given criteria
     *
     * @param parent      Folder containing the document to count
     * @param nameFilter  NameFilter defining which documents to include
     * @param listFolders true if matching folders have to be listed in the results
     * @param listFiles   true if matching files have to be listed in the results
     * @param stopFirst   true to stop at the first match (useful to optimize when the point is to check for emptiness)
     * @return Number of children of the given folder, matching the given criteria
     */
    private int countDocumentFiles(
            @NonNull final DocumentFile parent,
            final FileHelper.NameFilter nameFilter,
            boolean listFolders,
            boolean listFiles,
            boolean stopFirst) {
        if (null == client) return 0;
        int result = 0;

        try (Cursor c = getCursorFor(parent.getUri())) {
            if (c != null)
                while (c.moveToNext()) {
                    final String documentName = c.getString(1);
                    boolean isFolder = c.getString(2).equals(DocumentsContract.Document.MIME_TYPE_DIR);

                    // FileProvider doesn't take query selection arguments into account, so the selection has to be done manually
                    if ((null == nameFilter || nameFilter.accept(documentName)) && ((listFiles && !isFolder) || (listFolders && isFolder)))
                        result++;

                    // Don't do the whole loop if the point is to check for emptiness
                    if (stopFirst && result > 0) break;
                }
        } catch (Exception e) {
            Timber.w(e, "Failed query");
        }
        return result;
    }

    /**
     * Convert the given document properties to DocumentFile's
     *
     * @param context    Context to use for the conversion
     * @param properties Properties to convert
     * @return List of DocumentFile's built from the given properties
     */
    private List<DocumentFile> convertFromProperties(@NonNull final Context context, @NonNull final List<DocumentProperties> properties) {
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
    public Uri buildDocumentUriUsingTreeCached(Uri treeUri, String documentId) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(treeUri.getAuthority()).appendPath(DOCPROVIDER_PATH_TREE) // Risky; this value is supposed to be hidden
                .appendPath(getTreeDocumentIdCached(treeUri)).appendPath(DOCPROVIDER_PATH_DOCUMENT) // Risky; this value is supposed to be hidden
                .appendPath(documentId).build();
    }


    // Original (uncached) is DocumentFile.fromTreeUri
    @Nullable
    private DocumentFile fromTreeUriCached(@NonNull final Context context, @NonNull final Uri treeUri) {
        String documentId = getTreeDocumentIdCached(treeUri);
        if (isDocumentUriCached(context, treeUri)) {
            documentId = DocumentsContract.getDocumentId(treeUri);
        }
        return newTreeDocumentFile(null, context,
                buildDocumentUriUsingTreeCached(treeUri, documentId));
        //DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId));
    }

    // Original (uncached) is DocumentsContract.getTreeDocumentId
    private String getTreeDocumentIdCached(@NonNull final Uri uri) {
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
    private boolean isDocumentUriCached(@NonNull final Context context, @Nullable final Uri uri) {
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
    private boolean isDocumentsProviderCached(Context context, String authority) {
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
    private DocumentFile newTreeDocumentFile(@Nullable final DocumentFile parent, @NonNull final Context context, @NonNull final Uri uri) {
        //resultFiles[i] = new TreeDocumentFile(this, context, result[i]); <-- not visible
        try {
            if (null == treeDocumentFileConstructor) {
                Class<?> treeDocumentFileClazz = Class.forName("androidx.documentfile.provider.TreeDocumentFile");
                Constructor<?> constructor = treeDocumentFileClazz.getDeclaredConstructor(DocumentFile.class, Context.class, Uri.class);
                constructor.setAccessible(true);
                setTreeDocumentFileConstructor(constructor);
            }
            return (DocumentFile) treeDocumentFileConstructor.newInstance(parent, context, uri);
        } catch (Exception ex) {
            Timber.e(ex);
        }
        return null;
    }


    /**
     * Create a NameFilter that filters all names equal'ing the given string
     *
     * @param name String to be used for filtering names
     * @return NameFilter that filters all names equal'ing the given string
     */
    static FileHelper.NameFilter createNameFilterEquals(@NonNull final String name) {
        return displayName -> displayName.equalsIgnoreCase(name);
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
