package me.devsaki.hentoid.workers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.WorkerParameters
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.notification.import_.ImportCompleteNotification
import me.devsaki.hentoid.notification.import_.ImportProgressNotification
import me.devsaki.hentoid.notification.import_.ImportStartNotification
import me.devsaki.hentoid.util.Preferences
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
import me.devsaki.hentoid.util.logException
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.util.removeContent
import me.devsaki.hentoid.util.scanArchivePdf
import me.devsaki.hentoid.util.scanFolderRecursive
import me.devsaki.hentoid.util.trace
import me.devsaki.hentoid.workers.data.ExternalImportData
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.io.File
import java.io.IOException

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

    override fun onClear(logFile: DocumentFile?) {
        // Final event; should be step 4
        eventComplete(
            STEP_4_QUEUE_FINAL, booksOK + booksKO, booksOK, booksKO, logFile
        )
    }

    override fun getToWork(input: Data) {
        val data = ExternalImportData.Parser(inputData)
        if (data.behold) updateWithBeholder(applicationContext)
        else startImport(applicationContext)
    }

    private fun eventProgress(step: Int, nbBooks: Int, booksOK: Int, booksKO: Int) {
        EventBus.getDefault().post(
            ProcessEvent(
                ProcessEvent.Type.PROGRESS, R.id.import_external, step, booksOK, booksKO, nbBooks
            )
        )
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

    /**
     * Import books from external folder
     */
    private fun startImport(context: Context) {
        logNoDataMessage = "No content detected."
        val rootFolder = getDocumentFromTreeUriString(context, Preferences.getExternalLibraryUri())
        if (null == rootFolder) {
            Timber.e("External folder is not defined (%s)", Preferences.getExternalLibraryUri())
            return
        }
        try {
            Beholder.clearSnapshot(context)
            FileExplorer(context, Uri.parse(Preferences.getExternalLibraryUri())).use { explorer ->
                val detectedContent: MutableList<Content> = ArrayList()
                // Deep recursive search starting from the place the user has selected
                var dao: CollectionDAO = ObjectBoxDAO()
                try {
                    scanFolderRecursive(
                        context,
                        dao,
                        null,
                        rootFolder,
                        explorer,
                        ArrayList(),
                        detectedContent,
                        { s -> eventProcessed(2, s) },
                        logs
                    )
                } finally {
                    dao.cleanup()
                }
                eventComplete(STEP_2_BOOK_FOLDERS, 0, 0, 0, null)

                // Write JSON file for every found book and persist it in the DB
                trace(
                    Log.DEBUG,
                    0,
                    logs,
                    "Import books starting - initial detected count : %s",
                    detectedContent.size.toString() + ""
                )

                // Flag DB content for cleanup
                dao = ObjectBoxDAO()
                try {
                    dao.flagAllExternalBooks()
                } finally {
                    dao.cleanup()
                }

                val addedContent = HashMap<String, MutableList<Pair<DocumentFile, Long>>>()
                dao = ObjectBoxDAO()
                try {
                    for (content in detectedContent) {
                        if (isStopped) break
                        if (existsInCollection(content, dao, true, logs)) {
                            booksKO++
                            continue
                        }
                        createJsonFileFor(context, content, explorer, logs)
                        addContent(context, dao, content)
                        trace(
                            Log.INFO, 1, logs, "Import book OK : %s", content.storageUri
                        )
                        booksOK++
                        notificationManager.notify(
                            ImportProgressNotification(
                                content.title, booksOK + booksKO, detectedContent.size
                            )
                        )
                        eventProgress(
                            STEP_3_BOOKS, detectedContent.size, booksOK, booksKO
                        )
                        content.parentStorageUri?.let { parentUri ->
                            val entry = addedContent[parentUri] ?: ArrayList()
                            addedContent[parentUri] = entry
                            content.getStorageDoc()?.let { doc ->
                                entry.add(Pair(doc, content.id))
                            }
                        }
                    } // detected content
                    dao.deleteAllFlaggedBooks(false, null)
                    dao.cleanupOrphanAttributes()
                } finally {
                    dao.cleanup()
                }
                trace(
                    Log.INFO,
                    2,
                    logs,
                    "Import books complete - %s OK; %s KO; %s final count",
                    booksOK.toString() + "",
                    booksKO.toString() + "",
                    detectedContent.size.toString() + ""
                )
                eventComplete(
                    STEP_3_BOOKS, detectedContent.size, booksOK, booksKO, null
                )

                // Clear disk cache as import may reuse previous image IDs
                init(applicationContext)

                // Update the beholder
                Beholder.registerContent(context, addedContent)
            }
        } catch (e: IOException) {
            Timber.w(e)
            logException(e)
        } finally {
            notificationManager.notify(ImportCompleteNotification(booksOK, booksKO))
        }
    }

    private fun updateWithBeholder(context: Context) {
        logName = "refresh_external"
        Timber.d("delta init")
        Beholder.init(context)
        val delta = Beholder.scanForDelta(context)
        Timber.d("delta end")

        val dao = ObjectBoxDAO()
        try {
            Timber.d("delta+ : " + delta.first.size + " roots")

            // == Content to add
            delta.first.forEach { deltaPlus ->
                val deltaPlusRoot = deltaPlus.first
                FileExplorer(context, deltaPlusRoot).use { explorer ->
                    val addedContent =
                        scanAddedContent(context, explorer, deltaPlusRoot, deltaPlus, dao)
                    if (isStopped) return

                    // Process added content
                    addedContent.forEach {
                        if (!existsInCollection(it, dao, true, logs)) {
                            createJsonFileFor(context, it, explorer, logs)
                            addContent(context, dao, it)
                        }
                        // Update the beholder
                        it.getStorageDoc()?.let { storageDoc ->
                            Beholder.registerContent(
                                context,
                                it.parentStorageUri ?: deltaPlusRoot.uri.toString(),
                                storageDoc,
                                it.id
                            )
                        }
                    }
                } // explorer
            } // deltaPlus roots

            // == Content to remove
            val toRemove = delta.second.filter { it > 0 }
            Timber.d("delta- : " + toRemove.size + " useful / " + delta.second.size + " total")
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
            getFullPathFromUri(context, Uri.parse(Preferences.getExternalLibraryUri()))
                .split(File.separator)
        val parentNames = getFullPathFromUri(context, deltaPlusRoot.uri)
            .split(File.separator).toMutableList()
        for (i in extRootElts.indices - 1) parentNames.removeFirst()
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
                    parentNames,
                    addedContent,
                    null,
                    logs
                )
            }
        }
        Timber.d("  addedContent ${addedContent.size}")
        return addedContent
    }

    // TODO factorize with the end of ImportHelper.scanFolderRecursive
    private fun importArchivePdf(
        context: Context,
        docs: List<DocumentFile>,
        deltaPlusRoot: DocumentFile,
        archivePdf: DocumentFile,
        dao: CollectionDAO
    ): Content? {
        val json =
            docs.firstOrNull {
                it.isFile && getExtension(it.name ?: "")
                    .equals("json", true)
            }
        val c = scanArchivePdf(
            context,
            deltaPlusRoot,
            archivePdf,
            emptyList(),
            StatusContent.EXTERNAL,
            dao,
            json
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
                0,
                logs,
                message,
                archivePdf.name ?: "<name not found>"
            )
        }
        return null
    }

    private fun isSupportedArchivePdf(fileName: String): Boolean {
        return isSupportedArchive(fileName) || getExtension(fileName).equals("pdf", true)
    }
}