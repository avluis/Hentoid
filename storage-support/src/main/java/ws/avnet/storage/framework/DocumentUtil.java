package ws.avnet.storage.framework;

import android.net.Uri;
import android.provider.DocumentsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.util.List;

public class DocumentUtil {
    private static final String PATH_DOCUMENT = "document";
    private static final String PATH_TREE = "tree";

    private static final String URL_SLASH = "%2F";
    private static final String URL_COLON = "%3A";

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * Extract the via {@link DocumentsContract.Document#COLUMN_DOCUMENT_ID} from the given URI.
     * From {@link DocumentsContract} but return null instead of throw
     */
    public static String getTreeDocumentId(Uri uri) {
        final List<String> paths = uri.getPathSegments();
        return paths.size() >= 2 && PATH_TREE.equals(paths.get(0)) ? paths.get(1) : null;
    }

    public static boolean isTreeUri(Uri uri) {
        final List<String> paths = uri.getPathSegments();
        return (paths.size() == 2 && PATH_TREE.equals(paths.get(0)));
    }

    /**
     * True if the uri has a tree segment
     */
    public static boolean hasTreeDocumentId(Uri uri) {
        return getTreeDocumentId(uri) != null;
    }

    /**
     * Extract the {@link DocumentsContract.Document#COLUMN_DOCUMENT_ID} from the given URI.
     * From {@link DocumentsContract} but return null instead of throw
     */
    @Nullable
    public static String getDocumentId(@NonNull Uri documentUri) {
        final List<String> paths = documentUri.getPathSegments();
        if (paths.size() >= 2 && PATH_DOCUMENT.equals(paths.get(0))) {
            return paths.get(1);
        }
        return paths.size() >= 4 && PATH_TREE.equals(paths.get(0))
                && PATH_DOCUMENT.equals(paths.get(2)) ? paths.get(3) : null;
    }

    /**
     * Given a typical document id this will return the root id.
     * <p/>
     * Example:
     * 0000-0000:folder/file.ext
     * will return '0000-0000'
     *
     * @param documentId A valid document Id
     * @return the root id of the document id or null
     * @see {@link #getDocumentId(Uri)}
     * @see {@link DocumentsContract#getDocumentId(Uri)}
     */
    @Nullable
    public static String getRoot(@NonNull String documentId) {
        String[] parts = documentId.split(":");
        return parts.length > 0 ? parts[0] : null;
    }

    /**
     * Given a typical document id this will split at the root id.
     * <p/>
     * Example:
     * 0000-0000:folder/file.ext
     * will return ['0000-0000','folder/file.ext']
     *
     * @param documentId A valid document Id
     * @return just the document portion of the id without the root
     * @see {@link #getDocumentId(Uri)}
     * @see {@link DocumentsContract#getDocumentId(Uri)}
     */
    @Nullable
    public static String[] getIdSegments(@NonNull String documentId) {
        return documentId.split(":");
    }

    /**
     * Given a typical document id this will split the path segments.
     * <p/>
     * Example:
     * 0000-0000:folder/file.ext
     * will return ['folder','file.ext']
     *
     * @param documentUri A valid document uri
     * @return tokenized path segments within the document portion of uri
     * @see {@link #getDocumentId(Uri)}
     * @see {@link DocumentsContract#getDocumentId(Uri)}
     */
    @Nullable
    public static String[] getPathSegments(Uri documentUri) {
        String documentId = getDocumentId(documentUri);
        return documentId == null ? null : getPathSegments(documentId);
    }

    /**
     * Given a typical document id this will split the path segments.
     * <p/>
     * Example:
     * 0000-0000:folder/file.ext
     * will return ['folder','file.ext']
     *
     * @param documentId A valid document Id
     * @return Tokenized path segments within documentId
     * @see {@link #getDocumentId(Uri)}
     * @see {@link DocumentsContract#getDocumentId(Uri)}
     */
    @Nullable
    public static String[] getPathSegments(@NonNull String documentId) {
        String[] idParts = getIdSegments(documentId);
        // If there's only one part it's a root
        if (idParts.length <= 1) {
            return null;
        }

        // The last part should be the path for both document and tree uris
        String path = idParts[idParts.length - 1];
        return path.split("/");
    }

    /**
     * Given a valid document root this will create a new id with the appended path.
     * <p/>
     * Example:
     * 0000-0000:folder/file.ext
     * will return ['folder','file.ext']
     *
     * @param root A valid document root
     * @return Document ID for the new file
     * @see {@link #getDocumentId(Uri)}
     * @see {@link #getRoot(String)}}
     * @see {@link DocumentsContract#getDocumentId(Uri)}
     */
    public static String createNewDocumentId(@NonNull String root, @NonNull String path) {
        return root + ":" + path;
    }

    /**
     * Processes the URL encoded path of a document uri to be easy on the eyes
     * <p/>
     * Example:
     * <p/>
     * ...tree/0000-0000%3A/document/0000-0000%3Afolder%2Ffile.ext
     * <p>returns
     * <p/>
     * '0000-0000:folder/file.ext'
     *
     * @param uri uri
     * @return path for display or null if invalid
     */
    public static String getNicePath(Uri uri) {
        String documentId = getDocumentId(uri);
        if (documentId == null) {
            documentId = getTreeDocumentId(uri); // If there's no document id resort to tree id
        }
        return documentId;
    }

    /**
     * Returns a uri to a child file within a folder.  This can be used to get an assumed uri
     * to a child within a folder.  This avoids heavy calls to DocumentFile.listFiles or
     * write-locked createFile
     * <p/>
     * This will only work with a uri that is an hierarchical tree similar to SCHEME_FILE
     *
     * @param hierarchicalTreeUri folder to install into
     * @param filename            filename of child file
     * @return Uri to the child file
     */
    public static Uri getChildUri(Uri hierarchicalTreeUri, String filename) {
        String parentDocumentId = DocumentUtil.getTreeDocumentId(hierarchicalTreeUri);
        String childDocumentId = parentDocumentId + "/" + filename;
        return DocumentsContract.buildDocumentUriUsingTree(hierarchicalTreeUri, childDocumentId);
    }

    /**
     * Returns a uri to a neighbor file within the same folder.  This can be used to get an assumed uri
     * to a neighbor within a folder.  This avoids heavy calls to DocumentFile.listFiles or
     * write-locked createFile
     * <p/>
     * This will only work with a uri that is an hierarchical tree similar to SCHEME_FILE
     *
     * @param hierarchicalTreeUri folder to install into
     * @param filename            filename of child file
     * @return Uri to the child file
     */
    @Nullable
    public static Uri getNeighborUri(@NonNull Uri hierarchicalTreeUri, String filename) {
        String documentId = getDocumentId(hierarchicalTreeUri);
        if (documentId == null) {
            return null;
        }

        String root = getRoot(documentId);
        if (root == null) {
            return null;
        }

        String[] parts = getPathSegments(documentId);
        if (parts == null) {
            return null;
        }

        parts[parts.length - 1] = filename; // replace the filename
        String path = TextUtils.join("/", parts);
        String neighborId = createNewDocumentId(root, path);
        return DocumentsContract.buildDocumentUriUsingTree(hierarchicalTreeUri, neighborId);
    }
}
