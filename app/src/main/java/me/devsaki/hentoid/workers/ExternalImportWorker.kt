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
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.ObjectBoxDAOContainer
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.notification.import_.ImportCompleteNotification
import me.devsaki.hentoid.notification.import_.ImportProgressNotification
import me.devsaki.hentoid.notification.import_.ImportStartNotification
import me.devsaki.hentoid.util.ProgressManager
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.addContent
import me.devsaki.hentoid.util.createJsonFileFor
import me.devsaki.hentoid.util.existsInCollection
import me.devsaki.hentoid.util.file.Beholder
import me.devsaki.hentoid.util.file.DiskCache.init
import me.devsaki.hentoid.util.file.FileExplorer
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.getExtension
import me.devsaki.hentoid.util.file.getFileNameWithoutExtension
import me.devsaki.hentoid.util.file.getFullPathFromUri
import me.devsaki.hentoid.util.file.isSupportedArchive
import me.devsaki.hentoid.util.jsonToContent
import me.devsaki.hentoid.util.logException
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.util.removeContent
import me.devsaki.hentoid.util.scanArchivePdf
import me.devsaki.hentoid.util.scanFolderRecursive
import me.devsaki.hentoid.workers.data.ExternalImportData
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import kotlin.math.roundToInt

class ExternalImportWorker(context: Context, parameters: WorkerParameters) :
    BaseWorker(context, parameters, R.id.external_import_service, "import_external") {

    private var booksOK = 0 // Number of books imported
    private var booksKO = 0 // Number of folders found with no valid book inside

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
            STEP_4_QUEUE_FINAL, booksOK + booksKO, booksOK, booksKO, logFile
        )
    }

    override fun runProgressNotification() {
        // Using custom implementation
    }

    override suspend fun getToWork(input: Data) {
        val data = ExternalImportData.Parser(inputData)
        withContext(Dispatchers.IO) {
            if (data.behold) updateWithBeholder(applicationContext)
            else startImport(applicationContext)
        }
    }

    /**
     * Import books from external folder
     */
    private fun startImport(context: Context) {
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
                dao.dao.flagAllExternalBooks()
            } finally {
                dao.reset()
            }

            Beholder.clearSnapshot(context)
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
                        { c -> onContentFound(context, explorer, dao, progress, addedContent, c) }
                    )
                } finally {
                    dao.reset()
                }

                try {
                    dao.dao.deleteAllFlaggedBooks(false, null)
                    dao.dao.cleanupOrphanAttributes()
                } finally {
                    dao.reset()
                }

                trace(
                    Log.INFO,
                    "Import books complete - $booksOK OK; $booksKO KO; ${booksOK + booksKO} final count"
                )
                eventComplete(
                    STEP_3_BOOKS, booksOK + booksKO, booksOK, booksKO, null
                )

                // Clear disk cache as import may reuse previous image IDs
                init(applicationContext)

                // Update the beholder
                Beholder.registerContent(context, addedContent)
            }
        } catch (e: IOException) {
            Timber.w(e)
            logException(e, context)
        } finally {
            notificationManager.notify(ImportCompleteNotification(booksOK, booksKO))
        }
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
            booksKO++
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

        booksOK++

        newContentEvent(content, explorer.root, progress)

        // Clear the DAO every 1000 iterations to optimize memory
        if (0 == booksOK % 1000) dao.reset()
    }

    private suspend fun updateWithBeholder(context: Context) {
        logName = "refresh_external_auto"
        Timber.d("delta init")
        Beholder.init(context)
        val delta = Beholder.scanForDelta(context)
        Timber.d("delta end")

        val dao = ObjectBoxDAO()
        try {
            Timber.d("delta+ : ${delta.first.size} roots")

            // == Content to add
            delta.first.forEach { deltaPlus ->
                val deltaPlusRoot = deltaPlus.first
                FileExplorer(context, deltaPlusRoot).use { explorer ->
                    scanAddedContent(context, explorer, deltaPlusRoot, deltaPlus, dao)
                } // explorer
            } // deltaPlus roots

            // == Content to remove
            val toRemove = delta.second.filter { it > 0 }
            Timber.d("delta- : ${toRemove.size} useful / ${delta.second.size} total")
            toRemove.forEach { idToRemove ->
                if (isStopped) return
                Timber.d("delta- => $idToRemove")
                try {
                    Content().apply {
                        id = idToRemove
                        site = Site.NONE
                        removeContent(context, dao, this)
                    }
                } catch (e: Exception) {
                    Timber.w(e)
                }
            }
        } catch (e: Exception) {
            Timber.w(e)
        } finally {
            dao.cleanup()
        }
    }

    private fun scanAddedContent(
        context: Context,
        explorer: FileExplorer,
        deltaPlusRoot: DocumentFile,
        deltaPlus: Pair<DocumentFile, List<DocumentFile>>,
        dao: CollectionDAO
    ): List<Content> {
        val addedContent: MutableList<Content> = ArrayList()
        if (isStopped) return addedContent
        if (BuildConfig.DEBUG)
            Timber.d("delta+ root has ${deltaPlus.second.size} documents : ${deltaPlusRoot.uri}")

        // Pair siblings with the same name (e.g. archives and JSON files)
        val deltaPlusPairs = deltaPlus.second.groupBy { getFileNameWithoutExtension(it.name ?: "") }

        // Forge parent names using folder root path minus ext library root path
        val extRootElts =
            getFullPathFromUri(context, Settings.externalLibraryUri.toUri())
                .split(File.separator)
        val parentNames = getFullPathFromUri(context, deltaPlusRoot.uri)
            .split(File.separator).toMutableList()
        for (i in extRootElts.indices - 1) parentNames.removeAt(0)
        Timber.d("  parents : $parentNames")

        deltaPlusPairs.values.forEach { docs ->
            if (isStopped) return addedContent
            if (BuildConfig.DEBUG) docs.forEach { Timber.d("delta+ => ${it.uri}") }
            val archivePdf = docs.firstOrNull { it.isFile && isSupportedArchivePdf(it.name ?: "") }
            val folder = docs.firstOrNull { it.isDirectory }

            // Import new archive
            if (archivePdf != null) {
                importArchivePdf(context, docs, deltaPlusRoot, archivePdf, dao)
                    ?.let { addedContent.add(it) }
            } else if (folder != null) { // Import new folder
                scanFolderRecursive(
                    context,
                    dao,
                    deltaPlusRoot,
                    folder,
                    explorer,
                    null,
                    parentNames,
                    logs,
                    isCanceled = this::isStopped
                ) { c -> onContentFound2(context, explorer, dao, deltaPlusRoot, c) }
            }
        }
        Timber.d("  addedContent ${addedContent.size}")
        return addedContent
    }

    // Write JSON file for every found book and persist it in the DB
    private fun onContentFound2(
        context: Context,
        explorer: FileExplorer,
        dao: CollectionDAO,
        deltaPlusRoot: DocumentFile,
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
                content.parentStorageUri ?: deltaPlusRoot.uri.toString(),
                storageDoc,
                content.id
            )
        }
    }

    // TODO factorize with the end of ImportHelper.scanFolderRecursive
    private fun importArchivePdf(
        context: Context,
        docs: List<DocumentFile>,
        deltaPlusRoot: DocumentFile,
        archivePdf: DocumentFile,
        dao: CollectionDAO
    ): Content? {
        val jsons =
            docs.filter {
                it.isFile && getExtension(it.name ?: "")
                    .equals("json", true)
            }
        val content = jsonToContent(context, dao, jsons, archivePdf.name ?: "")
        val c = scanArchivePdf(
            context,
            deltaPlusRoot,
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

    private fun isSupportedArchivePdf(fileName: String): Boolean {
        return isSupportedArchive(fileName) || getExtension(fileName).equals("pdf", true)
    }

    private fun eventProgress(step: Int, booksOK: Int, progressPc: Float) {
        EventBus.getDefault().post(
            ProcessEvent(
                ProcessEvent.Type.PROGRESS, R.id.import_external, step, booksOK, booksKO, progressPc
            )
        )
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun newContentEvent(content: Content, root: Uri, progress: ProgressManager) {
        // Handle notifications on another coroutine not to steal focus for unnecessary stuff
        GlobalScope.launch(Dispatchers.Default) {
            val progressPc = (100 * progress.getGlobalProgress()).roundToInt()
            val bookLocation = content.storageUri.replace(root.toString(), "")
            trace(Log.INFO, "Import book OK : ${URLDecoder.decode(bookLocation, "UTF-8")}")
            notificationManager.notify(
                ImportProgressNotification(content.title, progressPc, 100)
            )
            eventProgress(STEP_3_BOOKS, booksOK, progress.getGlobalProgress())
        }
    }

    private fun eventProcessed(step: Int, name: String) {
        EventBus.getDefault()
            .post(ProcessEvent(ProcessEvent.Type.PROGRESS, R.id.import_external, step, name))
    }

    private fun eventComplete(
        step: Int, nbBooks: Int, booksOK: Int, booksKO: Int, cleanupLogFile: DocumentFile?
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