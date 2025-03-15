package me.devsaki.hentoid.util.file

import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.RemoteException
import android.provider.DocumentsContract
import androidx.documentfile.provider.CachedDocumentFile
import androidx.documentfile.provider.DocumentFile
import me.devsaki.hentoid.util.MaxSizeHashMap
import timber.log.Timber
import java.io.Closeable
import java.io.IOException
import java.lang.reflect.Constructor

// Risky; these values are supposed to be hidden
private const val DOCPROVIDER_PATH_DOCUMENT = "document"
private const val DOCPROVIDER_PATH_TREE = "tree"

class FileExplorer : Closeable {
    val root: Uri

    private var treeDocumentFileConstructor: Constructor<*>? = null

    private val providersCache: MutableMap<String?, Boolean> = HashMap()
    private val documentIdCache = MaxSizeHashMap<String, String?>(2000)

    private val client: ContentProviderClient?


    @Synchronized
    private fun setTreeDocumentFileConstructor(value: Constructor<*>) {
        treeDocumentFileConstructor = value
    }


    constructor(context: Context, parent: DocumentFile) {
        root = parent.uri
        client = init(context, parent.uri)
    }

    constructor(context: Context, parentUri: Uri) {
        root = parentUri
        client = init(context, parentUri)
    }

    private fun init(context: Context, uri: Uri): ContentProviderClient? {
        return context.contentResolver.acquireContentProviderClient(uri)
    }

    @Throws(IOException::class)
    override fun close() {
        documentIdCache.clear()
        client?.close()
    }


    /**
     * List all subfolders inside the given parent folder (non recursive)
     *
     * @param context Context to use
     * @param parent  Parent folder to list subfolders from
     * @return Subfolders of the given parent folder
     */
    fun listFolders(context: Context, parent: DocumentFile): List<DocumentFile> {
        return listDocumentFiles(
            context, parent, null,
            listFolders = true,
            listFiles = false,
            stopFirst = false
        )
    }

    /**
     * Returns true if the given parent folder contains at least one subfolder
     *
     * @param parent Parent folder to test
     * @return True if the given parent folder contains at least one subfolder; false instead
     */
    fun hasFolders(parent: DocumentFile): Boolean {
        return countDocumentFiles(
            parent, null,
            listFolders = true,
            listFiles = false,
            stopFirst = true
        ) > 0
    }

    /**
     * List all files (non-folders) inside the given parent folder (non recursive) that match the given name filter
     *
     * @param context Context to use
     * @param parent  Parent folder to list files from
     * @param filter  Name filter to use to filter the files to list
     * @return Files of the given parent folder matching the given name filter
     */
    fun listFiles(
        context: Context,
        parent: DocumentFile,
        filter: NameFilter?
    ): List<DocumentFile> {
        return listDocumentFiles(
            context, parent, filter,
            listFolders = false,
            listFiles = true,
            stopFirst = false
        )
    }

    /**
     * Count all files (non-folders) inside the given parent folder (non recursive) that match the given name filter
     *
     * @param parent Parent folder to count files from
     * @param filter Name filter to use to filter the files to count
     * @return Number of files inside the given parent folder matching the given name filter
     */
    fun countFiles(parent: DocumentFile, filter: NameFilter?): Int {
        return countDocumentFiles(parent, filter, countFolders = false, countFiles = true)
    }

    /**
     * Count all folders inside the given parent folder (non recursive) that match the given name filter
     *
     * @param parent Parent folder to count folders from
     * @param filter Name filter to use to filter the folders to count
     * @return Number of folders inside the given parent folder matching the given name filter
     */
    fun countFolders(parent: DocumentFile, filter: NameFilter?): Int {
        return countDocumentFiles(parent, filter, countFolders = true, countFiles = false)
    }

    /**
     * Find the folder inside the given parent folder (non recursive) that has the given name
     *
     * @param context       Context to use
     * @param parent        Parent folder of the folder to find
     * @param subfolderName Name of the folder to find
     * @return Folder inside the given parent folder (non recursive) that has the given name; null if not found
     */
    fun findFolder(context: Context, parent: DocumentFile, subfolderName: String): DocumentFile? {
        val result = listDocumentFiles(
            context,
            parent,
            createNameFilterEquals(subfolderName),
            listFolders = true,
            listFiles = false,
            stopFirst = true
        )
        return if (result.isNotEmpty()) result[0]
        else null
    }

    /**
     * Find the file inside the given parent folder (non recursive) that has the given name
     *
     * @param context  Context to use
     * @param parent   Parent folder of the file to find
     * @param fileName Name of the file to find
     * @return File inside the given parent folder (non recursive) that has the given name; null if not found
     */
    fun findFile(context: Context, parent: DocumentFile, fileName: String): DocumentFile? {
        val result =
            listDocumentFiles(
                context, parent, createNameFilterEquals(fileName),
                listFolders = false,
                listFiles = true,
                stopFirst = true
            )
        return if (result.isNotEmpty()) result[0]
        else null
    }


    /**
     * List all folders _and_ files inside the given parent folder (non recursive)
     *
     * @param context Context to use
     * @param parent  Parent folder to list elements from
     * @return Folders and files of the given parent folder
     */
    fun listDocumentFiles(
        context: Context,
        parent: DocumentFile
    ): List<DocumentFile> {
        return listDocumentFiles(
            context, parent, null,
            listFolders = true,
            listFiles = true,
            stopFirst = false
        )
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
    private fun countDocumentFiles(
        parent: DocumentFile,
        nameFilter: NameFilter?,
        countFolders: Boolean,
        countFiles: Boolean
    ): Int {
        return countDocumentFiles(parent, nameFilter, countFolders, countFiles, false)
    }

    /**
     * List the children of a given folder (non recursive) matching the given criteria
     *
     * @param context     Context to use for the query
     * @param parent      Folder containing the documents to list
     * @param nameFilter  NameFilter defining which documents to include
     * @param listFolders true if matching folders have to be listed in the results
     * @param listFiles   true if matching files have to be listed in the results
     * @return List of documents inside the given folder, matching the given criteria
     */
    fun listDocumentFiles(
        context: Context,
        parent: DocumentFile,
        nameFilter: NameFilter?,
        listFolders: Boolean,
        listFiles: Boolean,
        stopFirst: Boolean
    ): List<DocumentFile> {
        val results = queryDocumentFiles(parent, nameFilter, listFolders, listFiles, stopFirst)
        return convertFromProperties(context, results)
    }

    @Throws(RemoteException::class)
    private fun getCursorFor(rootFolderUri: Uri): Cursor? {
        val searchUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            rootFolderUri,
            DocumentsContract.getDocumentId(rootFolderUri)
        )
        return client?.query(
            searchUri, arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            ), null, null, null
        )
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
    private fun queryDocumentFiles(
        parent: DocumentFile,
        nameFilter: NameFilter?,
        listFolders: Boolean,
        listFiles: Boolean,
        stopFirst: Boolean
    ): List<DocumentProperties> {
        if (null == client) return emptyList()
        val results: MutableList<DocumentProperties> = ArrayList()

        try {
            getCursorFor(parent.uri).use { c ->
                if (c != null) while (c.moveToNext()) {
                    val documentId = c.getString(0)
                    val documentName = c.getString(1)
                    val isFolder =
                        c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                    val documentSize = c.getLong(3)

                    // FileProvider doesn't take query selection arguments into account, so the selection has to be done manually
                    if ((null == nameFilter || nameFilter.accept(documentName)) && ((listFiles && !isFolder) || (listFolders && isFolder)))
                        results.add(
                            DocumentProperties(
                                buildDocumentUriUsingTreeCached(parent.uri, documentId),
                                documentName,
                                documentSize,
                                isFolder
                            )
                        )

                    // Don't do the whole loop if the point is to find a single element
                    if (stopFirst && results.isNotEmpty()) break
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed query")
        }
        return results
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
    private fun countDocumentFiles(
        parent: DocumentFile,
        nameFilter: NameFilter?,
        listFolders: Boolean,
        listFiles: Boolean,
        stopFirst: Boolean
    ): Int {
        if (null == client) return 0
        var result = 0

        try {
            getCursorFor(parent.uri).use { c ->
                if (c != null) while (c.moveToNext()) {
                    val documentName = c.getString(1)
                    val isFolder =
                        c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR

                    // FileProvider doesn't take query selection arguments into account, so the selection has to be done manually
                    if ((null == nameFilter || nameFilter.accept(documentName)) && ((listFiles && !isFolder) || (listFolders && isFolder))) result++

                    // Don't do the whole loop if the point is to check for emptiness
                    if (stopFirst && result > 0) break
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed query")
        }
        return result
    }

    /**
     * Convert the given document properties to DocumentFile's
     *
     * @param context    Context to use for the conversion
     * @param properties Properties to convert
     * @return List of DocumentFile's built from the given properties
     */
    private fun convertFromProperties(
        context: Context,
        properties: List<DocumentProperties>
    ): List<DocumentFile> {
        val resultFiles: MutableList<DocumentFile> = ArrayList()
        for ((uri, name, size, isDirectory) in properties) {
            val docFile = fromTreeUriCached(context, uri)
            // Following line should be the proper way to go but it's inefficient as it calls queryIntentContentProviders from scratch repeatedly
            //DocumentFile docFile = DocumentFile.fromTreeUri(context, uri.left);
            if (docFile != null) resultFiles.add(
                CachedDocumentFile(
                    docFile,
                    name,
                    size,
                    isDirectory
                )
            )
        }
        return resultFiles
    }

    /**
     * WARNING Following methods are tweaks of internal Android code to make it faster by caching calls to queryIntentContentProviders
     */
    // Original (uncached) is DocumentsContract.buildDocumentUriUsingTree
    // NB : appendPath got costly because of encoding operations
    private fun buildDocumentUriUsingTreeCached(treeUri: Uri, documentId: String?): Uri {
        return Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
            .authority(treeUri.authority)
            .appendPath(DOCPROVIDER_PATH_TREE) // Risky; this value is supposed to be hidden
            .appendPath(getTreeDocumentIdCached(treeUri))
            .appendPath(DOCPROVIDER_PATH_DOCUMENT) // Risky; this value is supposed to be hidden
            .appendPath(documentId).build()
    }


    // Original (uncached) is DocumentFile.fromTreeUri
    private fun fromTreeUriCached(context: Context, treeUri: Uri): DocumentFile? {
        var documentId = getTreeDocumentIdCached(treeUri)
        if (isDocumentUriCached(context, treeUri)) {
            documentId = DocumentsContract.getDocumentId(treeUri)
        }
        return newTreeDocumentFile(
            null, context,
            buildDocumentUriUsingTreeCached(treeUri, documentId)
        )
        //DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId));
    }

    // Original (uncached) is DocumentsContract.getTreeDocumentId
    private fun getTreeDocumentIdCached(uri: Uri): String? {
        val uriStr = uri.toString()
        // First look into cache
        var result = documentIdCache[uri.toString()]
        // If nothing found, try the long way
        if (null == result) {
            result = DocumentsContract.getTreeDocumentId(uri)
            documentIdCache[uriStr] = result
        }
        return result
    }

    // Original (uncached) : DocumentsContract.isDocumentUri
    private fun isDocumentUriCached(context: Context, uri: Uri?): Boolean {
        if (isContentUri(uri) && isDocumentsProviderCached(context, uri!!.authority)) {
            val paths = uri.pathSegments
            if (paths.size == 2) {
                return DOCPROVIDER_PATH_DOCUMENT == paths[0]
            } else if (paths.size == 4) {
                return DOCPROVIDER_PATH_TREE == paths[0] && DOCPROVIDER_PATH_DOCUMENT == paths[2]
            }
        }
        return false
    }

    // Original (uncached) : DocumentsContract.isDocumentsProvider
    private fun isDocumentsProviderCached(context: Context, authority: String?): Boolean {
        // First look into cache
        val b = providersCache[authority]
        if (b != null) return b
        // If nothing found, try the long way
        val intent = Intent(DocumentsContract.PROVIDER_INTERFACE)
        val infos = context.packageManager
            .queryIntentContentProviders(intent, 0)
        for (info in infos) {
            if (authority == info.providerInfo.authority) {
                providersCache[authority] = true
                return true
            }
        }
        providersCache[authority] = false
        return false
    }

    // Original : DocumentsContract.isContentUri
    private fun isContentUri(uri: Uri?): Boolean {
        return uri != null && ContentResolver.SCHEME_CONTENT == uri.scheme
    }

    private fun newTreeDocumentFile(
        parent: DocumentFile?,
        context: Context,
        uri: Uri
    ): DocumentFile? {
        //resultFiles[i] = new TreeDocumentFile(this, context, result[i]); <-- not visible
        try {
            if (null == treeDocumentFileConstructor) {
                val treeDocumentFileClazz =
                    Class.forName("androidx.documentfile.provider.TreeDocumentFile")
                val constructor = treeDocumentFileClazz.getDeclaredConstructor(
                    DocumentFile::class.java,
                    Context::class.java,
                    Uri::class.java
                )
                constructor.isAccessible = true
                setTreeDocumentFileConstructor(constructor)
            }
            return treeDocumentFileConstructor!!.newInstance(parent, context, uri) as DocumentFile
        } catch (ex: Exception) {
            Timber.e(ex)
        }
        return null
    }


    /**
     * Create a NameFilter that filters all names equal'ing the given string
     *
     * @param name String to be used for filtering names
     * @return NameFilter that filters all names equal'ing the given string
     */
    fun createNameFilterEquals(name: String): NameFilter {
        return NameFilter { displayName: String ->
            displayName.equals(
                name,
                ignoreCase = true
            )
        }
    }

    /**
     * Properties of a stored document
     */
    internal data class DocumentProperties(
        val uri: Uri,
        val name: String,
        val size: Long,
        val isDirectory: Boolean
    )
}