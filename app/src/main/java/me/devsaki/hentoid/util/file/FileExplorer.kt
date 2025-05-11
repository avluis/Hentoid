package me.devsaki.hentoid.util.file

import android.content.ContentProviderClient
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.RemoteException
import android.provider.DocumentsContract
import androidx.documentfile.provider.CachedDocumentFile
import androidx.documentfile.provider.DocumentFile
import timber.log.Timber
import java.io.Closeable
import java.io.IOException


class FileExplorer : Closeable {
    val root: Uri
    private val contract: CachedDocumentsContract
    private val client: ContentProviderClient?

    constructor(context: Context, parent: DocumentFile) {
        root = parent.uri
        client = init(context, parent.uri)
        contract = CachedDocumentsContract()
    }

    constructor(context: Context, parentUri: Uri) {
        root = parentUri
        client = init(context, parentUri)
        contract = CachedDocumentsContract()
    }

    private fun init(context: Context, uri: Uri): ContentProviderClient? {
        return context.contentResolver.acquireContentProviderClient(uri)
    }

    @Throws(IOException::class)
    override fun close() {
        contract.close()
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
            countFolders = true,
            countFiles = false,
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
    fun countFiles(parent: DocumentFile, filter: NameFilter? = null): Int {
        return countDocumentFiles(parent, filter, countFolders = false, countFiles = true)
    }

    /**
     * Count all folders inside the given parent folder (non recursive) that match the given name filter
     *
     * @param parent Parent folder to count folders from
     * @param filter Name filter to use to filter the folders to count
     * @return Number of folders inside the given parent folder matching the given name filter
     */
    fun countFolders(parent: DocumentFile, filter: NameFilter? = null): Int {
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
     * List the children of a given folder (non recursive) matching the given criteria
     *
     * @param context     Context to use for the query
     * @param parent      Folder containing the documents to list
     * @param nameFilter  NameFilter defining which documents to include
     * @param listFolders true if matching folders have to be listed in the results
     * @param listFiles   true if matching files have to be listed in the results
     * @param stopFirst   true to stop at the first match (useful to optimize when the point is to check for emptiness)
     * @return List of documents inside the given folder, matching the given criteria
     */
    fun listDocumentFiles(
        context: Context,
        parent: DocumentFile,
        nameFilter: NameFilter? = null,
        listFolders: Boolean = true,
        listFiles: Boolean = true,
        stopFirst: Boolean = false
    ): List<DocumentFile> {
        val results = queryDocumentFiles(parent, nameFilter, listFolders, listFiles, stopFirst)
        return convertFromProperties(context, results)
    }

    @Throws(RemoteException::class)
    private fun getCursorFor(rootFolderUri: Uri): Cursor? {
        val searchUri = contract.buildChildDocumentsUriUsingTree(
            rootFolderUri,
            contract.getDocumentId(rootFolderUri)
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
                                contract.buildDocumentUriUsingTree(parent.uri, documentId),
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
     * @param countFolders true if matching folders have to be listed in the results
     * @param countFiles   true if matching files have to be listed in the results
     * @param stopFirst   true to stop at the first match (useful to optimize when the point is to check for emptiness); default is false
     * @return Number of children of the given folder, matching the given criteria
     */
    private fun countDocumentFiles(
        parent: DocumentFile,
        nameFilter: NameFilter?,
        countFolders: Boolean,
        countFiles: Boolean,
        stopFirst: Boolean = false
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
                    if ((null == nameFilter || nameFilter.accept(documentName)) && ((countFiles && !isFolder) || (countFolders && isFolder))) result++

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
        return properties.mapNotNull {
            // Following line should be the proper way to go but it's inefficient as it calls queryIntentContentProviders from scratch repeatedly
            //DocumentFile docFile = DocumentFile.fromTreeUri(context, uri.left);
            contract.fromTreeUri(context, it.uri)?.let { doc ->
                CachedDocumentFile(
                    doc,
                    it.name,
                    it.size,
                    it.isDirectory
                )
            }
        }
    }

    fun getDocumentFromTreeUri(context: Context, treeUri: Uri): DocumentFile? {
        val result = contract.fromTreeUri(context, treeUri)
        return if (null == result || !result.exists()) null
        else result
    }

    /**
     * Create a NameFilter that filters all names equal'ing the given string
     *
     * @param name String to be used for filtering names
     * @return NameFilter that filters all names equal'ing the given string
     */
    fun createNameFilterEquals(name: String): NameFilter {
        return NameFilter { it.equals(name, ignoreCase = true) }
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