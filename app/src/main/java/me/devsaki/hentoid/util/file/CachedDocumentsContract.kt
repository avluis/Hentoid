package me.devsaki.hentoid.util.file

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import me.devsaki.hentoid.util.MaxSizeHashMap
import timber.log.Timber
import java.lang.reflect.Constructor

// Risky; these values are supposed to be hidden inside DocumentsProvider
private const val PATH_DOCUMENT = "document"
private const val PATH_TREE = "tree"
private const val PATH_CHILDREN = "children"

/**
 * WARNING Present class is full of tweaks of internal Android code to make it faster by caching calls
 */
class CachedDocumentsContract {
    private val providersCache: MutableMap<String?, Boolean> = HashMap()
    private val treeDocumentIdCache = MaxSizeHashMap<String, String?>(2000)
    private val documentIdCache = MaxSizeHashMap<String, String?>(2000)
    private val treeDocumentFileConstructor: Constructor<*> by lazy { initTreeDocumentFileConstructor() }

    fun close() {
        providersCache.clear()
        treeDocumentIdCache.clear()
        documentIdCache.clear()
    }

    // Original (uncached) is DocumentsContract.buildDocumentUriUsingTree
    // NB : appendPath got costly because of encoding operations
    fun buildDocumentUriUsingTree(treeUri: Uri, documentId: String?): Uri {
        return Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
            .authority(treeUri.authority)
            .appendPath(PATH_TREE) // Risky; this value is supposed to be hidden
            .appendPath(getTreeDocumentId(treeUri))
            .appendPath(PATH_DOCUMENT) // Risky; this value is supposed to be hidden
            .appendPath(documentId).build()
    }

    // Original (uncached) is DocumentsContract.buildChildDocumentsUriUsingTree
    fun buildChildDocumentsUriUsingTree(
        treeUri: Uri,
        parentDocumentId: String?
    ): Uri {
        return Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
            .authority(treeUri.authority).appendPath(PATH_TREE)
            .appendPath(getTreeDocumentId(treeUri))
            .appendPath(PATH_DOCUMENT)
            .appendPath(parentDocumentId).appendPath(PATH_CHILDREN).build()
    }

    // Original (uncached) is DocumentsContract.getTreeDocumentId
    fun getTreeDocumentId(uri: Uri): String? {
        val uriStr = uri.toString()
        // First look into cache
        var result = treeDocumentIdCache[uriStr]
        // If nothing found, try the long way
        if (null == result) {
            result = DocumentsContract.getTreeDocumentId(uri)
            treeDocumentIdCache[uriStr] = result
        }
        return result
    }

    // Original (uncached) is DocumentsContract.getDocumentId
    fun getDocumentId(uri: Uri): String? {
        val uriStr = uri.toString()
        // First look into cache
        var result = documentIdCache[uriStr]
        // If nothing found, try the long way
        if (null == result) {
            result = DocumentsContract.getDocumentId(uri)
            documentIdCache[uriStr] = result
        }
        return result
    }

    // Original (uncached) : DocumentsContract.isDocumentUri
    private fun isDocumentUri(context: Context, uri: Uri): Boolean {
        if (isContentUri(uri) && isDocumentsProvider(context, uri.authority)) {
            val paths = uri.pathSegments
            if (paths.size == 2) {
                return PATH_DOCUMENT == paths[0]
            } else if (paths.size == 4) {
                return PATH_TREE == paths[0] && PATH_DOCUMENT == paths[2]
            }
        }
        return false
    }

    // Original (uncached) : DocumentsContract.isDocumentsProvider
    private fun isDocumentsProvider(context: Context, authority: String?): Boolean {
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

    // Original (uncached) is DocumentFile.fromTreeUri
    fun fromTreeUri(context: Context, treeUri: Uri): DocumentFile? {
        var documentId = getTreeDocumentId(treeUri)
        if (isDocumentUri(context, treeUri)) {
            documentId = getDocumentId(treeUri)
        }
        return newTreeDocumentFile(
            null, context,
            buildDocumentUriUsingTree(treeUri, documentId)
        )
    }

    // Original : TreeDocumentFile constructor (package private visibility)
    private fun newTreeDocumentFile(
        parent: DocumentFile?,
        context: Context,
        uri: Uri
    ): DocumentFile? {
        //resultFiles[i] = new TreeDocumentFile(this, context, result[i]); <-- not visible
        try {
            return treeDocumentFileConstructor.newInstance(parent, context, uri) as DocumentFile
        } catch (ex: Exception) {
            Timber.e(ex)
        }
        return null
    }

    private fun initTreeDocumentFileConstructor(): Constructor<*> {
        val treeDocumentFileClazz =
            Class.forName("androidx.documentfile.provider.TreeDocumentFile")
        val constructor = treeDocumentFileClazz.getDeclaredConstructor(
            DocumentFile::class.java,
            Context::class.java,
            Uri::class.java
        )
        constructor.isAccessible = true
        return constructor
    }
}