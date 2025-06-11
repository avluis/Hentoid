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
    // Value
    //      First : Number of files
    //      Second : Useful DocumentFiles
    //          Key : hash64(name, size)
    //          Value : Content ID if any; -1 if none
    private val snapshot: MutableMap<String, Pair<Int, Map<Long, Long>>> = HashMap()

    // Key : Root document Id
    private val ignoreList = HashSet<String>()


    fun init(ctx: Context) {
        if (snapshot.isNotEmpty()) return

        val entries = loadSnapshot(ctx)
        entries.forEach {
            snapshot[it.uri] = Pair(it.nbFiles, it.documents)
        }
        logSnapshot()
    }

    /**
     * Scan folders
     *
     * @param ctx        Context to use
     * @param onProgress Progress callback, passes the total number of items
     * @return
     *  First : List of new scanned DocumentFiles that weren't referenced in initial
     *      First : Root DocumentFile of the files appearing in Second
     *      Second : New scanned DocumentFiles that weren't referenced in initial
     *  Second : List of folders whose number of files has changed
     *  Third : Removed Content IDs whose Document was referenced in initial, but not found when scanning
     */
    fun scanForDelta(
        ctx: Context,
        explorer: FileExplorer,
        isCanceled: () -> Boolean,
        onProgress: ((Int, Int) -> Unit)? = null,
        onNew: ((DocumentFile, Collection<DocumentProperties>) -> Unit)? = null,
        onChanged: ((DocumentFile) -> Unit)? = null,
        onDeleted: ((Long) -> Unit)? = null,
    ) {
        val allDeletedRoots = ArrayList<String>()
        var index = 0

        snapshot.forEach { (rootUriStr, docs) ->
            val nbFiles = docs.first
            val usefulDocs: Map<Long, Long> = docs.second
            if (BuildConfig.DEBUG) Timber.d("Root : $rootUriStr (${nbFiles} files, ${usefulDocs.size} useful docs)")
            if (isCanceled.invoke()) return@forEach
            onProgress?.invoke(++index, snapshot.size)

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
                    if (BuildConfig.DEBUG) Timber.d("  Files found : ${files.size} (${(files.size - usefulFiles.size)} ignored)")

                    // Select new docs
                    val newKeys = usefulFiles.keys.minus(usefulDocs.keys)
                    val newDocs = usefulFiles.filterKeys { it in newKeys }.values
                    if (newDocs.isNotEmpty()) onNew?.invoke(root, newDocs)
                    if (BuildConfig.DEBUG) Timber.d("  New : ${newKeys.count()} - ${newDocs.size}")

                    // Select deleted docs
                    val deletedKeys = usefulDocs.keys.minus(files.keys)
                    deletedKeys.forEach { onDeleted?.invoke(it) }
                    if (BuildConfig.DEBUG) Timber.d("  Deleted : ${deletedKeys.count()} - ${deletedKeys.size}")

                    // Select docs with changed number of children
                    if (nbFiles != files.size) {
                        onChanged?.invoke(root)
                        if (BuildConfig.DEBUG) Timber.d("  Changed : true")
                    }

                    // Update snapshot in memory
                    snapshot[rootUriStr] = Pair(
                        files.size,
                        usefulDocs
                            .minus(deletedKeys)
                            .plus(newDocs.associateBy({ it.uniqueHash }, { -1L }))
                    )
                } catch (e: IOException) {
                    Timber.w(e)
                }
            } ?: run { // Snapshot root not found in storage
                if (BuildConfig.DEBUG) {
                    Timber.d("  Folder not found in storage")
                    Timber.d("  Deleted : ${usefulDocs.count()}")
                }
            } // Root Document
        } // Snapshot elements

        // Update snapshot in memory
        allDeletedRoots.forEach { snapshot.remove(it) }

        // Save snapshot file
        saveSnapshot(ctx)
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
        contentDocs.forEach { (rootUri, docs) ->
            val contentDocsMap = HashMap<String, Pair<DocumentFile, Long>>()
            // Add new values to register
            docs.forEach {
                contentDocsMap[it.first.uri.toString()] = Pair(it.first, it.second)
                if (ignoreList.isNotEmpty())
                    ignoreList.remove(DocumentsContract.getTreeDocumentId(it.first.uri))
            }
            getDocumentFromTreeUriString(ctx, rootUri)?.let { doc ->
                try {
                    FileExplorer(ctx, doc).use { fe ->
                        val files = fe.listDocumentProperties(
                            doc, null,
                            listFolders = true,
                            listFiles = true,
                            stopFirst = false
                        )
                        val usefulFiles = files
                            .filter { isUseful(it) }
                            .mapNotNull { fe.convertFromProperties(ctx, doc, it) }
                        result.add(
                            FolderEntry(
                                rootUri,
                                files.size,
                                usefulFiles.associateBy(
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
            val targetMap: MutableMap<Long, Long> = HashMap(it.documents)
            val target: Pair<Int, MutableMap<Long, Long>> = Pair(it.nbFiles, targetMap)

            snapshot[it.uri]?.second?.forEach { snap ->
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
                dos.write(SNAPSHOT_VERSION_2)
                dos.writeInt(snapshot.size)
                snapshot.forEach { se ->
                    FolderEntry(se.key, se.value.first, se.value.second).toStream(dos)
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

        inFile.inputStream().use { fis ->
            BufferedInputStream(fis).use { bis ->
                DataInputStream(bis).use { dis ->
                    bis.mark(3)
                    val signature = ByteArray(3)
                    dis.read(signature)

                    var version = 2
                    if (!signature.startsWith(SNAPSHOT_VERSION_2)) {
                        version = 1
                        bis.reset()
                    }

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
                Timber.v("${se.key} : ${se.value.first} files, ${se.value.second} useful docs")
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
    val nbFiles: Int,
    // Useful DocumentFiles and associated Content ID
    //   Key : hash64(name, size)
    //   Value : Content ID if any; -1 if none
    val documents: Map<Long, Long>
) {
    companion object {
        fun fromStream(dis: DataInputStream, version: Int): FolderEntry {
            val entryUri = dis.readUTF()
            val nbFiles = if (version > 1) dis.readInt() else 0
            val nbDocs = dis.readInt()
            val docs = HashMap<Long, Long>()
            repeat(nbDocs) {
                val key = dis.readLong()
                val value = dis.readLong()
                docs[key] = value
            }
            return FolderEntry(entryUri, nbFiles, docs)
        }
    }

    fun toStream(dos: DataOutputStream) {
        dos.writeUTF(uri)
        dos.writeInt(nbFiles)
        dos.writeInt(documents.size)
        documents.forEach {
            dos.writeLong(it.key)
            dos.writeLong(it.value)
        }
    }
}