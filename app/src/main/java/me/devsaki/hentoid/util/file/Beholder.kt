package me.devsaki.hentoid.util.file

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.util.file.FileExplorer.DocumentProperties
import timber.log.Timber
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException

private const val SNAPSHOT_LOCATION = "beholder.snapshot"

// TODO don't save _all_ files but only useful files and number of children
object Beholder {

    // Key : Root document Uri
    // Value
    //      Key : hash64(name, size)
    //      Value : Content ID if any; -1 if none
    private val snapshot: MutableMap<String, Map<Long, Long>> = HashMap()

    // Key : Root document Id
    private val ignoreList = HashSet<String>()


    fun init(ctx: Context) {
        if (snapshot.isNotEmpty()) return

        val entries = loadSnapshot(ctx)
        entries.forEach {
            snapshot[it.uri] = it.documents
        }
        logSnapshot()
    }

    /**
     * Scan folders
     *
     * @param ctx Context to use
     * @return
     *  First : List of new scanned DocumentFiles that weren't referenced in initial
     *      First : Root DocumentFile of the files appearing in Second
     *      Second : New scanned DocumentFiles that weren't referenced in initial
     *  Second : Removed Content IDs whose Document was referenced in initial, but not found when scanning
     */
    fun scanForDelta(ctx: Context): Pair<List<Pair<DocumentFile, List<DocumentProperties>>>, Set<Long>> {
        val allNewDocs = ArrayList<Pair<DocumentFile, List<DocumentProperties>>>()
        val allDeletedDocs = HashSet<Long>()
        val allDeletedRoots = HashSet<String>()

        snapshot.forEach { (rootUriStr, docs) ->
            if (BuildConfig.DEBUG) Timber.d("Root : $rootUriStr (${docs.size} docs)")
            getDocumentFromTreeUriString(ctx, rootUriStr)?.let { root ->
                try {
                    val newDocs = ArrayList<DocumentProperties>()
                    val deletedDocs = HashSet<Long>()

                    if (BuildConfig.DEBUG) Timber.d("  Folder found in storage")
                    FileExplorer(ctx, root).use { fe ->
                        val files = fe.listDocumentProperties(
                            root, null,
                            listFolders = true,
                            listFiles = true,
                            stopFirst = false
                        ).associateBy({ it.uniqueHash() }, { it })
                        val validFiles =
                            files.filterNot { ignoreList.contains(it.value.documentId) }
                        if (BuildConfig.DEBUG) Timber.d("  Files found : ${files.size} (${(files.size - validFiles.size)} ignored)")

                        // Select new docs
                        val newKeys = validFiles.keys.asSequence().minus(docs.keys)
                        newDocs.addAll(validFiles.filterKeys { it in newKeys }.values)
                        if (BuildConfig.DEBUG) Timber.d("  New : ${newKeys.count()} - ${newDocs.size}")

                        // Select deleted docs
                        val deletedKeys = docs.keys.asSequence().minus(files.keys).toSet()
                        deletedDocs.addAll(docs.filterKeys { it in deletedKeys }.values)
                        if (BuildConfig.DEBUG) Timber.d("  Deleted : ${deletedKeys.count()} - ${deletedDocs.size}")

                        // Update snapshot in memory
                        snapshot[rootUriStr] = docs
                            .minus(deletedKeys)
                            .plus(newDocs.associateBy({ it.uniqueHash() }, { -1 }))

                        // Update global result
                        if (newDocs.isNotEmpty()) allNewDocs.add(Pair(root, newDocs))
                        allDeletedDocs.addAll(deletedDocs)
                    }
                } catch (e: IOException) {
                    Timber.w(e)
                }
            } ?: run { // Snapshot root not found in storage
                if (BuildConfig.DEBUG) {
                    Timber.d("  Folder not found in storage")
                    Timber.d("  Deleted : ${docs.count()}")
                }

                // Update global result
                allDeletedRoots.add(rootUriStr)
                allDeletedDocs.addAll(docs.values)
            } // Root Document
        } // Snapshot elements

        // Update snapshot in memory
        allDeletedRoots.forEach { snapshot.remove(it) }

        // Save snapshot file
        saveSnapshot(ctx)

        return Pair(allNewDocs, allDeletedDocs)
    }

    /**
     * Ignore given for all subsequent scanForDelta's until said folder is registered using registerContent
     */
    fun ignoreFolder(folder: DocumentFile) {
        ignoreList.add(DocumentsContract.getTreeDocumentId(folder.uri))
    }

    fun registerRoot(
        ctx: Context,
        rootUri: Uri
    ) {
        val map = HashMap<String, List<Pair<DocumentFile, Long>>>()
        map[rootUri.toString()] = listOf()
        registerContent(ctx, map)
    }

    fun registerContent(
        ctx: Context,
        parentUri: String,
        contentDoc: DocumentFile,
        contentId: Long
    ) {
        val map = HashMap<String, List<Pair<DocumentFile, Long>>>()
        map[parentUri] = listOf(Pair(contentDoc, contentId))
        registerContent(ctx, map)
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
        contentDocs: Map<String, List<Pair<DocumentFile, Long>>>
    ) {
        val result: MutableList<FolderEntry> = ArrayList()
        contentDocs.forEach { (uri, docs) ->
            val contentDocsMap = HashMap<String, Pair<DocumentFile, Long>>()
            // Add new values to register
            docs.forEach {
                contentDocsMap[it.first.uri.toString()] = Pair(it.first, it.second)
                ignoreList.remove(it.first.uri.toString())
            }
            getDocumentFromTreeUriString(ctx, uri)?.let { doc ->
                try {
                    FileExplorer(ctx, doc).use { fe ->
                        val files = fe.listDocumentFiles(
                            ctx, doc, null,
                            listFolders = true,
                            listFiles = true,
                            stopFirst = false
                        )
                        result.add(
                            FolderEntry(
                                uri,
                                files.associateBy(
                                    { it.uniqueHash() },
                                    { contentDocsMap[it.uri.toString()]?.second ?: -1 }
                                )
                            )
                        )
                    }
                } catch (e: IOException) {
                    Timber.w(e)
                }
            }
        }

        // Store result
        result.forEach {
            // Merge new values with known values
            val target: MutableMap<Long, Long> = HashMap(it.documents)
            if (snapshot.contains(it.uri)) {
                snapshot[it.uri]?.forEach { snap ->
                    if (snap.value > -1) target[snap.key] = snap.value
                }
            }

            snapshot.remove(it.uri)
            snapshot[it.uri] = target
        }
        saveSnapshot(ctx)
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
                dos.writeInt(snapshot.size)
                snapshot.forEach { se ->
                    FolderEntry(se.key, se.value).toStream(dos)
                }
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

        inFile.inputStream().use {
            DataInputStream(it).use { dis ->
                val nbEntries = dis.readInt()
                repeat(nbEntries) { result.add(FolderEntry.fromStream(dis)) }
            }
        }
        return result
    }

    private fun logSnapshot() {
        if (BuildConfig.DEBUG) {
            Timber.v("beholder dump start")
            snapshot.forEach { se ->
                Timber.v("${se.key} : ${se.value.size} elements")
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

data class FolderEntry(
    val uri: String,
    // Key : hash64(name, size)
    // Value : Content ID if any; -1 if none
    val documents: Map<Long, Long>
) {
    companion object {
        fun fromStream(dis: DataInputStream): FolderEntry {
            val entryUri = dis.readUTF()
            val nbDocs = dis.readInt()
            val docs = HashMap<Long, Long>()
            repeat(nbDocs) {
                val key = dis.readLong()
                val value = dis.readLong()
                docs[key] = value
            }
            return FolderEntry(entryUri, docs)
        }
    }

    fun toStream(dos: DataOutputStream) {
        dos.writeUTF(uri)
        dos.writeInt(documents.size)
        documents.forEach { doc ->
            dos.writeLong(doc.key)
            dos.writeLong(doc.value)
        }
    }
}