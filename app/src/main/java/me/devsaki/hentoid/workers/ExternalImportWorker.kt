package me.devsaki.hentoid.workers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.READER_CACHE
import me.devsaki.hentoid.core.THUMB_FILE_NAME
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.ObjectBoxDAOContainer
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.database.domains.ImageFile.UriImageFileComparator
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.notification.import_.ImportCompleteNotification
import me.devsaki.hentoid.notification.import_.ImportProgressNotification
import me.devsaki.hentoid.notification.import_.ImportStartNotification
import me.devsaki.hentoid.util.ProgressManager
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.addContent
import me.devsaki.hentoid.util.createImageListFromFiles
import me.devsaki.hentoid.util.createJsonFileFor
import me.devsaki.hentoid.util.existsInCollection
import me.devsaki.hentoid.util.file.Beholder
import me.devsaki.hentoid.util.file.FileExplorer
import me.devsaki.hentoid.util.file.FileExplorer.DocumentProperties
import me.devsaki.hentoid.util.file.StorageCache
import me.devsaki.hentoid.util.file.formatDisplay
import me.devsaki.hentoid.util.file.formatDisplayUri
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.getExtension
import me.devsaki.hentoid.util.file.getFileNameWithoutExtension
import me.devsaki.hentoid.util.file.getParent
import me.devsaki.hentoid.util.file.removeFile
import me.devsaki.hentoid.util.image.clearCoilCache
import me.devsaki.hentoid.util.image.imageNamesFilter
import me.devsaki.hentoid.util.image.isSupportedImage
import me.devsaki.hentoid.util.isSupportedArchivePdf
import me.devsaki.hentoid.util.jsonToContent
import me.devsaki.hentoid.util.logException
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.util.removeContent
import me.devsaki.hentoid.util.scanArchivePdf
import me.devsaki.hentoid.util.scanFolderRecursive
import me.devsaki.hentoid.workers.data.ExternalImportData
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.io.IOException
import java.net.URLDecoder
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.roundToInt

class ExternalImportWorker(context: Context, parameters: WorkerParameters) :
    BaseWorker(context, parameters, R.id.external_import_service, "import_external") {

    private var itemsOK = 0 // Number of books imported
    private var itemsKO = 0 // Number of folders found with no valid book inside
    private var totalItems = 0 // Total number of items to display the progress bar

    companion object {
        fun isRunning(context: Context): Boolean {
            return isRunning(context, R.id.external_import_service)
        }
    }

    override fun getStartNotification(): BaseNotification {
        return ImportStartNotification()
    }

    override fun onInterrupt() {
        // Nothing
    }

    override suspend fun onClear(logFile: DocumentFile?) {
        // Final event; should be step 4
        eventComplete(
            STEP_4_QUEUE_FINAL, itemsOK + itemsKO, itemsOK, itemsKO, logFile
        )
    }

    /**
     * Run progress notification for Beholder import
     * Manual import is using a custom implementation
     */
    override fun runProgressNotification() {
        if (itemsOK < totalItems) {
            eventProgress(STEP_3_BOOKS, itemsOK, itemsOK * 1f / totalItems)
            notificationManager.notify(
                ImportProgressNotification(
                    applicationContext.resources.getString(
                        R.string.refresh_auto_processing,
                        itemsOK,
                        totalItems
                    ),
                    itemsOK,
                    totalItems
                )
            )
        } else {
            eventComplete(STEP_3_BOOKS, totalItems, itemsOK, 0)
            notificationManager.notify(ImportCompleteNotification(itemsOK, itemsKO))
        }
    }

    override suspend fun getToWork(input: Data) {
        val data = ExternalImportData.Parser(inputData)
        withContext(Dispatchers.IO) {
            if (data.behold) updateWithBeholder(applicationContext, data.folders)
            else startImport(applicationContext)
        }
    }

    /**
     * Import books from external folder
     */
    private suspend fun startImport(context: Context) {
        logNoDataMessage = "No content detected."
        val rootFolder = getDocumentFromTreeUriString(context, Settings.externalLibraryUri)
        if (null == rootFolder) {
            Timber.e("External folder is not defined (%s)", Settings.externalLibraryUri)
            return
        }
        trace(Log.INFO, "Import from ${URLDecoder.decode(rootFolder.uri.toString(), "UTF-8")}")
        try {
            eventComplete(STEP_2_BOOK_FOLDERS, 0, 0, 0, null) // Artificial

            val dao = ObjectBoxDAOContainer()
            // Flag DB content for cleanup
            try {
                dao.dao.flagAllExternalContents()
            } finally {
                dao.reset()
            }

            Beholder.clearSnapshot(context)

            // Remove all images stored in the app's persistent folder (archive covers)
            val appFolder = context.filesDir
            appFolder.listFiles { _, s: String? -> isSupportedImage(s ?: "") }
                ?.forEach { removeFile(it) }

            val addedContent = HashMap<String, MutableList<Pair<DocumentFile, Long>>>()
            val progress = ProgressManager()
            FileExplorer(context, rootFolder.uri).use { explorer ->
                // Deep recursive search starting from the place the user has selected
                try {
                    scanFolderRecursive(
                        context,
                        dao.dao,
                        null,
                        rootFolder,
                        explorer,
                        progress,
                        ArrayList(),
                        logs,
                        isCanceled = this::isStopped,
                        { f -> Beholder.registerRoot(context, f.uri) },
                        { c -> onContentFound(context, explorer, dao, progress, addedContent, c) }
                    )
                } finally {
                    dao.reset()
                }

                try {
                    dao.dao.deleteAllFlaggedContents(false, null)
                    dao.dao.cleanupOrphanAttributes()
                } finally {
                    dao.reset()
                }

                trace(
                    Log.INFO,
                    "Import books complete - $itemsOK OK; $itemsKO KO; ${itemsOK + itemsKO} final count"
                )
                eventComplete(
                    STEP_3_BOOKS, itemsOK + itemsKO, itemsOK, itemsKO, null
                )

                // Update the beholder
                Beholder.registerContent(context, addedContent)
            }
        } catch (e: IOException) {
            Timber.w(e)
            logException(e, context)
        } finally {
            notificationManager.notify(ImportCompleteNotification(itemsOK, itemsKO))
        }

        // Clear disk cache as import may reuse previous image IDs
        StorageCache.clear(applicationContext, READER_CACHE)
        clearCoilCache(applicationContext)
    }

    // Write JSON file for every found book and persist it in the DB
    private fun onContentFound(
        context: Context,
        explorer: FileExplorer,
        dao: ObjectBoxDAOContainer,
        progress: ProgressManager,
        addedContent: MutableMap<String, MutableList<Pair<DocumentFile, Long>>>,
        content: Content,
    ) {
        if (existsInCollection(content, dao.dao, true, logs)) {
            itemsKO++
            return
        }
        createJsonFileFor(context, content, explorer, logs)
        addContent(context, dao.dao, content)

        // Prepare structure for Beholder
        content.parentStorageUri?.let { parentUri ->
            val entry = addedContent[parentUri] ?: ArrayList()
            addedContent[parentUri] = entry
            content.getStorageDoc()?.let { it -> entry.add(Pair(it, content.id)) }
        }
        itemsOK++

        newContentEvent(content, explorer.root, progress.getGlobalProgress())

        // Clear the DAO every 1000 iterations to optimize memory
        if (0 == itemsOK % 1000) dao.reset()
    }

    private suspend fun updateWithBeholder(context: Context, folders: List<String>) {
        logName = "refresh_external_auto"
        val externalUri = Settings.externalLibraryUri.toUri()
        val libraryPathSize = (externalUri.path ?: "").split('/').size
        eventComplete(STEP_2_BOOK_FOLDERS, 0, 0, 0, null) // Artificial

        Timber.d("delta init")
        Beholder.init(context)

        val dao = ObjectBoxDAO()
        try {
            FileExplorer(context, externalUri).use { explorer ->
                if (folders.isEmpty()) {
                    Beholder.scanAll(
                        context,
                        explorer,
                        this::isStopped,
                        onProgress = { idx, count ->
                            // Don't refresh constantly
                            if (!(0 == idx % 10 || idx == totalItems)) return@scanAll
                            itemsOK = idx
                            totalItems = count
                            launchProgressNotification()
                        },
                        onNew = { parent, usefulFiles ->
                            onNewBH(context, parent, usefulFiles, explorer, dao, libraryPathSize)
                        },
                        onChanged = { onChangedBH(it, explorer, dao, libraryPathSize) },
                        onDeleted = { onDeletedBH(context, it, dao) }
                    )
                } else {
                    Beholder.scanFolders(
                        context,
                        explorer,
                        folders.toSet(),
                        this::isStopped,
                        onProgress = { idx, count ->
                            itemsOK = idx
                            totalItems = count
                            launchProgressNotification()
                        },
                        onNew = { parent, usefulFiles ->
                            onNewBH(context, parent, usefulFiles, explorer, dao, libraryPathSize)
                        },
                        onChanged = { onChangedBH(it, explorer, dao, libraryPathSize) },
                        onDeleted = { onDeletedBH(context, it, dao) }
                    )
                }
            }
        } catch (e: Exception) {
            Timber.w(e)
        } finally {
            dao.cleanup()
        }
        Timber.d("delta end")

        // Clear disk cache as import may reuse previous image IDs
        StorageCache.clear(applicationContext, READER_CACHE)
        clearCoilCache(applicationContext)
    }

    private fun onNewBH(
        context: Context,
        parent: DocumentFile,
        usefulFiles: Collection<DocumentProperties>,
        explorer: FileExplorer,
        dao: CollectionDAO,
        libraryPathSize: Int
    ) {
        Timber.d("delta+ : ${usefulFiles.size} roots")
        scanAddedContentBH(
            context,
            explorer,
            parent.uri,
            usefulFiles.mapNotNull {
                explorer.convertFromProperties(
                    applicationContext,
                    parent,
                    it
                )
            },
            dao,
            libraryPathSize
        )
    }

    private fun onChangedBH(
        changed: DocumentFile,
        explorer: FileExplorer,
        dao: CollectionDAO,
        libraryPathSize: Int
    ) {
        Timber.d("delta* => ${changed.formatDisplayUri(Settings.externalLibraryUri)}")
        try {
            dao.selectContentByStorageUri(changed.uri.toString(), false)?.let {
                // Existing content => Add new images
                scanChangedUpdatedContentBH(
                    applicationContext,
                    explorer,
                    it,
                    changed,
                    dao
                )
            } ?: run {
                // New content
                scanChangedNewContentBH(
                    applicationContext,
                    explorer,
                    changed,
                    dao,
                    libraryPathSize
                )
            }
        } catch (e: Exception) {
            Timber.w(e)
        }
    }

    private fun onDeletedBH(
        context: Context,
        deleted: Long,
        dao: CollectionDAO
    ) {
        Timber.d("delta- => $deleted")
        try {
            Content().apply {
                id = deleted
                site = Site.NONE
                GlobalScope.launch(Dispatchers.IO) {
                    removeContent(context, dao, this@apply)
                }
            }
        } catch (e: Exception) {
            Timber.w(e)
        }
    }

    private fun scanAddedContentBH(
        context: Context,
        explorer: FileExplorer,
        parent: Uri,
        deltaPlusDocs: List<DocumentFile>,
        dao: CollectionDAO,
        nbLibraryPathParts: Int
    ) {
        if (isStopped) return

        if (BuildConfig.DEBUG) {
            val nbFiles = deltaPlusDocs.count { it.isFile }
            val nbFolders = deltaPlusDocs.count { it.isDirectory }
            Timber.d(
                "delta+ root has $nbFiles useful files and $nbFolders useful folders : ${
                    parent.formatDisplay(Settings.externalLibraryUri)
                }"
            )
        }
        if (deltaPlusDocs.isEmpty()) return

        // Pair siblings with the same name (e.g. archives and JSON files)
        val deltaPlusPairs = deltaPlusDocs.groupBy { getFileNameWithoutExtension(it.name ?: "") }

        // Forge parent names using folder root path minus ext library root path
        val rootParts = (parent.path ?: "").split('/')
        val parentNames = rootParts.subList(nbLibraryPathParts, rootParts.size)
        Timber.d("  parents : $parentNames")

        deltaPlusPairs.values.forEach { docs ->
            if (isStopped) return
            if (BuildConfig.DEBUG)
                docs.forEach { Timber.d("delta+ => ${it.formatDisplayUri(Settings.externalLibraryUri)}") }
            val archivePdf = docs.firstOrNull { it.isFile && isSupportedArchivePdf(it.name ?: "") }
            val folder = docs.firstOrNull { it.isDirectory }

            // Import new archive
            if (archivePdf != null) {
                importArchivePdf(context, docs, parent, archivePdf, dao)
                    ?.let { onContentFoundBH(context, explorer, dao, parent, it) }
            } else if (folder != null) { // Import new folder
                scanFolderRecursive(
                    context,
                    dao,
                    parent,
                    folder,
                    explorer,
                    null,
                    parentNames,
                    logs,
                    isCanceled = this::isStopped,
                    { f -> Beholder.registerRoot(context, f.uri) },
                    { c -> onContentFoundBH(context, explorer, dao, parent, c) }
                )
            }
        }
    }

    private fun scanChangedNewContentBH(
        context: Context,
        explorer: FileExplorer,
        folder: DocumentFile,
        dao: CollectionDAO,
        nbLibraryPathParts: Int
    ) {
        // Forge parent names using folder root path minus ext library root path
        val rootParts = (folder.uri.path ?: "").split('/')
        val parentNames = rootParts.subList(nbLibraryPathParts, rootParts.size)
        Timber.d("  parents : $parentNames")

        val parent = getParent(
            applicationContext,
            Settings.externalLibraryUri.toUri(),
            folder.uri
        ) ?: throw IOException("Couldn't find parent")

        scanFolderRecursive(
            context,
            dao,
            parent,
            folder,
            explorer,
            null,
            parentNames,
            logs,
            isCanceled = this::isStopped,
            { f -> Beholder.registerRoot(context, f.uri) },
            { c -> onContentFoundBH(context, explorer, dao, parent, c) }
        )
    }

    private fun scanChangedUpdatedContentBH(
        context: Context,
        explorer: FileExplorer,
        content: Content,
        folder: DocumentFile,
        dao: CollectionDAO
    ) {
        val targetImgs = ArrayList<ImageFile>()

        val imageFiles = explorer.listFiles(context, folder, imageNamesFilter)
            .associateBy({ it.uri.toString() }, { it })
        val contentImgKeys = content.imageList.associateBy({ it.fileUri }, { it })

        // Keep images that are present on storage
        targetImgs.addAll(
            contentImgKeys.entries
                .filter { imageFiles.containsKey(it.key) }
                .map { it.value })

        // Add extra detected images
        val newImageFiles = imageFiles.entries
            .filter { !contentImgKeys.containsKey(it.key) }
            .map { it.value }

        if (newImageFiles.isNotEmpty()) {
            targetImgs.addAll(
                createImageListFromFiles(
                    newImageFiles,
                    StatusContent.EXTERNAL,
                    targetImgs.maxOf { it.order } + 1)
            )
        }

        // Sort all images (old + new) according to their filename
        targetImgs.sortWith(UriImageFileComparator())

        // Rebuild order
        var idx = 1
        val nbMaxChars = floor(log10(targetImgs.size.toDouble()) + 1).toInt()
        targetImgs.forEach {
            if (!it.isCover) {
                it.order = idx++
                it.computeName(nbMaxChars)
            } else {
                it.order = 0
                it.name = THUMB_FILE_NAME
            }
        }

        content.setImageFiles(targetImgs)
        content.qtyPages = content.getNbDownloadedPages()
        content.computeSize()

        dao.replaceImageList(content.id, targetImgs)
        dao.insertContentCore(content)
    }

    // Write JSON file for every found book and persist it in the DB
    private fun onContentFoundBH(
        context: Context,
        explorer: FileExplorer,
        dao: CollectionDAO,
        parent: Uri,
        content: Content,
    ) {
        if (!existsInCollection(content, dao, true, logs)) {
            createJsonFileFor(context, content, explorer, logs)
            addContent(context, dao, content)
        }
        // Update the beholder
        content.getStorageDoc()?.let { storageDoc ->
            Beholder.registerContent(
                context,
                content.parentStorageUri ?: parent.toString(),
                storageDoc,
                content.id
            )
        }
    }

    // TODO factorize with the end of ImportHelper.scanFolderRecursive
    private fun importArchivePdf(
        context: Context,
        docs: List<DocumentFile>,
        parent: Uri,
        archivePdf: DocumentFile,
        dao: CollectionDAO
    ): Content? {
        val jsons =
            docs.filter { it.isFile && it.getExtension().equals("json", true) }
        val content = jsonToContent(context, dao, jsons, archivePdf.name ?: "")
        val c = scanArchivePdf(
            context,
            parent,
            archivePdf,
            emptyList(),
            StatusContent.EXTERNAL,
            content
        )
        // Valid archive
        if (0 == c.first) return c.second
        else {
            // Invalid archive
            val message = when (c.first) {
                1 -> "Archive ignored (contains another archive) : %s"
                else -> "Archive ignored (unsupported pictures or corrupted archive) : %s"
            }
            trace(
                Log.INFO,
                message,
                archivePdf.name ?: "<name not found>"
            )
        }
        return null
    }

    private fun eventProgress(step: Int, booksOK: Int, progressPc: Float) {
        EventBus.getDefault().post(
            ProcessEvent(
                ProcessEvent.Type.PROGRESS, R.id.import_external, step, booksOK, itemsKO, progressPc
            )
        )
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun newContentEvent(content: Content, root: Uri, progress: Float) {
        // Handle notifications on another coroutine not to steal focus for unnecessary stuff
        GlobalScope.launch(Dispatchers.Default) {
            val progressPc = (100 * progress).roundToInt()
            val bookLocation = content.storageUri.replace(root.toString(), "")
            trace(Log.INFO, "Import book OK : ${URLDecoder.decode(bookLocation, "UTF-8")}")
            notificationManager.notify(
                ImportProgressNotification(content.title, progressPc, 100)
            )
            eventProgress(STEP_3_BOOKS, itemsOK, progress)
        }
    }

    private fun eventComplete(
        step: Int, nbBooks: Int, booksOK: Int, booksKO: Int, cleanupLogFile: DocumentFile? = null
    ) {
        EventBus.getDefault().postSticky(
            ProcessEvent(
                ProcessEvent.Type.COMPLETE,
                R.id.import_external,
                step,
                booksOK,
                booksKO,
                nbBooks,
                cleanupLogFile
            )
        )
    }
}