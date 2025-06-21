package me.devsaki.hentoid.util.file

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.util.file.FileExplorer.DocumentProperties
import me.devsaki.hentoid.util.image.startsWith
import me.devsaki.hentoid.util.isSupportedArchivePdf
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

private val CHARSET_LATIN_1 = StandardCharsets.ISO_8859_1
private const val SNAPSHOT_LOCATION = "beholder.snapshot"
private val SNAPSHOT_VERSION_2 = "SC2".toByteArray(CHARSET_LATIN_1)
private val SNAPSHOT_VERSION_3 = "SC3".toByteArray(CHARSET_LATIN_1)

/**
 * A/ Takes a snapshot of a list of folders (called "roots") and records :
 * - Subfolders
 * - Useful files (archives, PDFs and JSONs)
 * - Number of files
 *
 * B/ Peforms a quick scan of all roots and identify which elements have changed, using :
 * - Name
 * - Size
 * - Number of files
 */
object Beholder {

    // Key : Root document Uri
    // Value : Description of the scanned folder
    private val snapshot: MutableMap<String, FolderEntry> = HashMap()

    // Key : Root document Id
    private val ignoreList = HashSet<String>()


    fun init(ctx: Context) {
        if (snapshot.isNotEmpty()) return

        val entries = loadSnapshot(ctx)
        entries.forEach {
            snapshot[it.uri] = FolderEntry(it.uri, it.nbFiles, it.isLeaf, it.documents)
        }
        logSnapshot()
    }

    /**
     * Scan folders for differences with latest snapshot
     * Snaphot is updated at the end of processing
     *
     * @param isCanceled Kill switch
     * @param onProgress Progress callback
     * @param onNew      New scanned DocumentFiles that weren't referenced in initial snapshot
     *      First : Root DocumentFile of those newly scanned files
     *      Second : New scanned DocumentFiles that weren't referenced in initial snapshot
     * @param onChanged  Folder whose number of files has changed
     * @param onDeleted  Removed Content IDs whose Document was referenced in initial, but not found when scanning
     */
    fun scanAll(
        ctx: Context,
        explorer: FileExplorer,
        isCanceled: () -> Boolean,
        onProgress: ((Int, Int) -> Unit)? = null,
        onNew: ((DocumentFile, Collection<DocumentProperties>) -> Unit)? = null,
        onChanged: ((DocumentFile) -> Unit)? = null,
        onDeleted: ((Long) -> Unit)? = null,
    ) {
        var index = 0

        val nodes = snapshot.filter { !it.value.isLeaf }
        val leaves = snapshot.minus(nodes.keys)
        if (BuildConfig.DEBUG) Timber.d("Snapshot : ${snapshot.entries.size} entries = ${nodes.size} nodes + ${leaves.size} leaves")

        // Process nodes first (should contain new and deleted content)
        nodes.forEach { (rootUriStr, entry) ->
            scanEntryForDelta(
                ctx,
                rootUriStr,
                entry,
                explorer,
                isCanceled,
                onNew,
                onChanged,
                onDeleted
            )
            onProgress?.invoke(++index, snapshot.size)
        }

        // Process leaves after (should just be updated pages)
        leaves.forEach { (rootUriStr, entry) ->
            scanEntryForDelta(
                ctx,
                rootUriStr,
                entry,
                explorer,
                isCanceled,
                onNew,
                onChanged,
                onDeleted
            )
            onProgress?.invoke(++index, snapshot.size)
        }

        // Save snapshot file
        saveSnapshot(ctx)
    }

    fun scanFolders(
        ctx: Context,
        explorer: FileExplorer,
        folders: Set<String>,
        isCanceled: () -> Boolean,
        onProgress: ((Int, Int) -> Unit)? = null,
        onNew: ((DocumentFile, Collection<DocumentProperties>) -> Unit)? = null,
        onChanged: ((DocumentFile) -> Unit)? = null,
        onDeleted: ((Long) -> Unit)? = null,
    ) {
        val existingFolders = snapshot.filter { folders.contains(it.key) }
        val newFolders = folders.filter { !existingFolders.containsKey(it) }

        var index = 0
        existingFolders.forEach {
            scanEntryForDelta(
                ctx,
                it.key,
                it.value,
                explorer,
                isCanceled,
                onNew,
                onChanged,
                onDeleted
            )
            onProgress?.invoke(++index, folders.size)
        }

        newFolders.forEach {
            registerRoot(ctx, it, onNew, explorer)
            onProgress?.invoke(++index, folders.size)
        }

        // Save snapshot file
        saveSnapshot(ctx)
    }

    private fun scanEntryForDelta(
        ctx: Context,
        rootUriStr: String,
        entry: FolderEntry,
        explorer: FileExplorer,
        isCanceled: () -> Boolean,
        onNew: ((DocumentFile, Collection<DocumentProperties>) -> Unit)? = null,
        onChanged: ((DocumentFile) -> Unit)? = null,
        onDeleted: ((Long) -> Unit)? = null,
    ) {
        if (BuildConfig.DEBUG) Timber.d("Root : $rootUriStr ${if (entry.isLeaf) "LEAF" else "NODE"} (${entry.nbFiles} files, ${entry.documents} useful docs)")
        if (isCanceled.invoke()) return

        getDocumentFromTreeUriString(ctx, rootUriStr)?.let { root ->
            try {
                if (BuildConfig.DEBUG) Timber.d("  Folder found in storage")
                val files = explorer.listDocumentProperties(
                    root, null,
                    listFolders = true,
                    listFiles = true,
                    stopFirst = false
                ).associateBy({ it.uniqueHash }, { it })

                val usefulFiles =
                    files.filterNot { ignoreList.contains(it.value.documentId) }
                        .filter { isUseful(it.value) }

                // Node if empty or contains at least a subfolder, or contains at least an archive/PDF
                var isNode = files.isEmpty() || (files.values.any {
                    !it.isFile || (it.isFile && isSupportedArchivePdf(it.name))
                })
                if (BuildConfig.DEBUG) Timber.d("  Files found : ${files.size} (${(files.size - usefulFiles.size)} ignored)")

                // Select new docs
                val newKeys = usefulFiles.keys.minus(entry.documents.keys)
                val newDocs = usefulFiles.filterKeys { it in newKeys }.values
                if (newDocs.isNotEmpty()) onNew?.invoke(root, newDocs)
                if (BuildConfig.DEBUG) Timber.d("  New : ${newKeys.count()} - ${newDocs.size}")

                // Select deleted docs
                val deletedKeys = entry.documents.keys.minus(files.keys)
                deletedKeys.forEach { onDeleted?.invoke(it) }
                if (BuildConfig.DEBUG) Timber.d("  Deleted : ${deletedKeys.count()} - ${deletedKeys.size}")

                // Select docs with changed number of children
                if (entry.nbFiles != files.size) {
                    onChanged?.invoke(root)
                    if (BuildConfig.DEBUG) Timber.d("  Changed : ${entry.nbFiles} to ${files.size}")
                }

                // Update snapshot in memory
                snapshot[rootUriStr] = FolderEntry(
                    rootUriStr,
                    files.size,
                    !isNode,
                    entry.documents
                        .minus(deletedKeys)
                        .plus(newDocs.associateBy({ it.uniqueHash }, { -1L }))
                )
            } catch (e: IOException) {
                Timber.w(e)
            }
        } ?: run { // Snapshot root not found in storage
            if (BuildConfig.DEBUG) {
                Timber.d("  Folder not found in storage")
                snapshot.remove(rootUriStr)
                Timber.d("  Deleted : ${entry.documents.count()}")
            }
        } // Root Document
    }

    /**
     * Ignore given for all subsequent scanForDelta's until said folder is registered using registerContent
     */
    fun ignoreFolder(folder: DocumentFile) {
        ignoreList.add(DocumentsContract.getTreeDocumentId(folder.uri))
    }

    fun registerRoot(
        ctx: Context,
        rootUri: Uri,
        onNew: ((DocumentFile, Collection<DocumentProperties>) -> Unit)? = null,
        explorer: FileExplorer? = null
    ) {
        registerRoot(ctx, rootUri.toString(), onNew, explorer)
    }

    fun registerRoot(
        ctx: Context,
        rootUriStr: String,
        onNew: ((DocumentFile, Collection<DocumentProperties>) -> Unit)? = null,
        explorer: FileExplorer? = null
    ) {
        val map = HashMap<String, List<Pair<DocumentFile, Long>>>()
        map[rootUriStr] = listOf()
        registerContent(ctx, map, onNew, explorer)
    }

    fun registerContent(
        ctx: Context,
        parentUri: String,
        contentDoc: DocumentFile,
        contentId: Long,
        onNew: ((DocumentFile, Collection<DocumentProperties>) -> Unit)? = null,
        explorer: FileExplorer? = null
    ) {
        val map = HashMap<String, List<Pair<DocumentFile, Long>>>()
        map[parentUri] = listOf(Pair(contentDoc, contentId))
        registerContent(ctx, map, onNew, explorer)
    }

    /**
     * @param contentDocs
     * Key = Root Uri
     * Value = List of documents inside the given root, with their associated content
     *      First = DocumentFile
     *      Second = Associated Content ID; -1 if no Content
     */
    fun registerContent(
        ctx: Context,
        contentDocs: Map<String, List<Pair<DocumentFile, Long>>>,
        onNew: ((DocumentFile, Collection<DocumentProperties>) -> Unit)? = null,
        inExplorer: FileExplorer? = null
    ) {
        val result: MutableList<FolderEntry> = ArrayList()
        contentDocs.forEach { (rootUri, docs) ->
            val contentDocsMap = HashMap<String, Pair<DocumentFile, Long>>()
            // Add new values to register
            docs.forEach {
                contentDocsMap[it.first.uri.toString()] = Pair(it.first, it.second)
                if (ignoreList.isNotEmpty())
                    ignoreList.remove(DocumentsContract.getTreeDocumentId(it.first.uri))
            }
            getDocumentFromTreeUriString(ctx, rootUri)?.let { doc ->
                val explorer = inExplorer ?: FileExplorer(ctx, doc)
                try {
                    val files = explorer.listDocumentProperties(
                        doc, null,
                        listFolders = true,
                        listFiles = true,
                        stopFirst = false
                    )
                    val usefulEntries = files.filter { isUseful(it) }
                    onNew?.invoke(doc, usefulEntries)

                    val usefulDocs = usefulEntries
                        .mapNotNull { explorer.convertFromProperties(ctx, doc, it) }

                    result.add(
                        FolderEntry(
                            rootUri,
                            files.size,
                            true, // A content is always a leaf
                            usefulDocs.associateBy(
                                { it.uniqueHash() },
                                { contentDocsMap[it.uri.toString()]?.second ?: -1 }
                            )
                        )
                    )
                } catch (e: IOException) {
                    Timber.w(e)
                } finally {
                    // Close the explorer if it has been created locally
                    if (null == inExplorer) explorer.close()
                }
            }
        }

        // Store result
        result.forEach {
            // Merge new values with known values
            val targetMap: MutableMap<Long, Long> = HashMap(it.documents)
            val target = FolderEntry(it.uri, it.nbFiles, it.isLeaf, targetMap)

            snapshot[it.uri]?.documents?.forEach { snap ->
                if (snap.value > -1) targetMap[snap.key] = snap.value
            }

            snapshot.remove(it.uri)
            snapshot[it.uri] = target
        }
        saveSnapshot(ctx)
    }

    private fun isUseful(doc: DocumentProperties): Boolean {
        return if (doc.isDirectory) true
        else if (doc.isFile && isSupportedArchivePdf(doc.name)) true
        else if (doc.extension == "json") true
        else false
    }

    fun clearSnapshot(ctx: Context) {
        snapshot.clear()
        saveSnapshot(ctx)
    }

    private fun saveSnapshot(ctx: Context): Boolean {
        val outFile =
            ctx.filesDir.listFiles { it.name == SNAPSHOT_LOCATION }?.let {
                if (it.isNotEmpty()) it[0]
                else File(ctx.filesDir, SNAPSHOT_LOCATION)
            }

        if (null == outFile) return false
        outFile.createNewFile()

        getOutputStream(outFile).use {
            DataOutputStream(it).use { dos ->
                dos.write(SNAPSHOT_VERSION_3)
                dos.writeInt(snapshot.size)
                snapshot.forEach { se -> se.value.toStream(dos) }
            }
        }
        logSnapshot()
        return true
    }

    private fun loadSnapshot(ctx: Context): List<FolderEntry> {
        val result: MutableList<FolderEntry> = ArrayList()
        val inFile =
            ctx.filesDir.listFiles { it.name == SNAPSHOT_LOCATION }?.let {
                if (it.isNotEmpty()) it[0]
                else null
            }
        if (null == inFile || 0L == inFile.length()) return result

        if (BuildConfig.DEBUG)
            Timber.d(
                "Beholder snapshot : ${
                    formatHumanReadableSize(
                        inFile.length(),
                        ctx.resources
                    )
                }"
            )

        inFile.inputStream().use { fis ->
            BufferedInputStream(fis).use { bis ->
                DataInputStream(bis).use { dis ->
                    bis.mark(3) // Otto would appreciate
                    val signature = ByteArray(3)
                    dis.read(signature)

                    var version = 1
                    if (signature.startsWith(SNAPSHOT_VERSION_3)) version = 3
                    else if (signature.startsWith(SNAPSHOT_VERSION_2)) version = 2
                    else bis.reset()

                    val nbEntries = dis.readInt()
                    repeat(nbEntries) { result.add(FolderEntry.fromStream(dis, version)) }
                }
            }
        }
        return result
    }

    private fun logSnapshot() {
        if (BuildConfig.DEBUG) {
            Timber.v("beholder dump start")
            snapshot.forEach { se ->
                Timber.v("${se.key} : ${se.value.nbFiles} files, ${se.value.documents.size} useful docs")
                /*
                se.value.forEach { sev ->
                    Timber.d("${sev.key} => ${sev.value}")
                }
                 */
            }
            Timber.v("beholder dump end")
        }
    }
}

/**
 * Pivot class for snapshot serialization & deserialization
 */
data class FolderEntry(
    val uri: String,
    // Number of files found inside the folder
    val nbFiles: Int,
    // Is the folder a leaf or a node ?
    // NB : A "node" is a folder that contains nothing at all, or at least one subfolder
    val isLeaf: Boolean,
    // Useful DocumentFiles and associated Content ID
    //   Key : hash64(name, size)
    //   Value : Content ID if any; -1 if none
    val documents: Map<Long, Long>
) {
    companion object {
        fun fromStream(dis: DataInputStream, version: Int): FolderEntry {
            val entryUri = dis.readUTF()
            val nbFiles = if (version > 1) dis.readInt() else 0
            val isLeaf = if (version > 2) dis.readBoolean() else false
            val nbDocs = dis.readInt()
            val docs = HashMap<Long, Long>()
            repeat(nbDocs) {
                val key = dis.readLong()
                val value = dis.readLong()
                docs[key] = value
            }
            return FolderEntry(entryUri, nbFiles, isLeaf, docs)
        }
    }

    fun toStream(dos: DataOutputStream) {
        dos.writeUTF(uri)
        dos.writeInt(nbFiles)
        dos.writeBoolean(isLeaf)
        dos.writeInt(documents.size)
        documents.forEach {
            dos.writeLong(it.key)
            dos.writeLong(it.value)
        }
    }
}