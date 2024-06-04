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
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.notification.import_.ImportCompleteNotification
import me.devsaki.hentoid.notification.import_.ImportProgressNotification
import me.devsaki.hentoid.notification.import_.ImportStartNotification
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.createJsonFileFor
import me.devsaki.hentoid.util.existsInCollection
import me.devsaki.hentoid.util.file.Beholder
import me.devsaki.hentoid.util.file.DiskCache.init
import me.devsaki.hentoid.util.file.FileExplorer
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.getExtension
import me.devsaki.hentoid.util.file.getFileNameWithoutExtension
import me.devsaki.hentoid.util.file.isSupportedArchive
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.util.scanArchive
import me.devsaki.hentoid.util.scanFolderRecursive
import me.devsaki.hentoid.util.trace
import me.devsaki.hentoid.workers.data.ExternalImportData
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
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
            PrimaryImportWorker.STEP_4_QUEUE_FINAL, booksOK + booksKO, booksOK, booksKO, logFile
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
                eventComplete(PrimaryImportWorker.STEP_2_BOOK_FOLDERS, 0, 0, 0, null)

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
                        ContentHelper.addContent(context, dao, content)
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
                            PrimaryImportWorker.STEP_3_BOOKS, detectedContent.size, booksOK, booksKO
                        )
                        content.parentStorageUri?.let { parentUri ->
                            val entry = addedContent[parentUri] ?: ArrayList()
                            addedContent[parentUri] = entry
                            content.storageDoc?.let { doc ->
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
                    PrimaryImportWorker.STEP_3_BOOKS, detectedContent.size, booksOK, booksKO, null
                )

                // Clear disk cache as import may reuse previous image IDs
                init(applicationContext)

                // Update the beholder
                Beholder.registerContent(context, addedContent)
            }
        } catch (e: IOException) {
            Timber.w(e)
            Helper.logException(e)
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
            delta.first.forEach { deltaPlus ->
                val deltaPlusRoot = deltaPlus.first
                FileExplorer(context, deltaPlusRoot).use { explorer ->
                    val addedContent: MutableList<Content> = ArrayList()
                    if (isStopped) return

                    // Pair siblings with the same name (e.g. archives and JSON files)
                    val deltaPlusPairs =
                        deltaPlus.second.groupBy { f -> getFileNameWithoutExtension(f.name ?: "") }

                    deltaPlusPairs.values.forEach { docs ->
                        if (isStopped) return
                        if (BuildConfig.DEBUG) {
                            docs.forEach { doc ->
                                Timber.d("delta+ => " + doc.uri.toString())
                            }
                        }
                        val archive =
                            docs.firstOrNull { it.isFile && isSupportedArchive(it.name ?: "") }
                        val folder = docs.firstOrNull { it.isDirectory }

                        // Import new archive
                        if (archive != null) {
                            val json =
                                docs.firstOrNull {
                                    it.isFile && getExtension(it.name ?: "")
                                        .equals("json", true)
                                }
                            val c = scanArchive(
                                context,
                                deltaPlusRoot,
                                archive,
                                emptyList(),
                                StatusContent.EXTERNAL,
                                dao,
                                json
                            )
                            // Valid archive
                            if (0 == c.first) addedContent.add(c.second!!)
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
                                    archive.name ?: "<name not found>"
                                )
                            }
                        } else if (folder != null) { // Import new folder
                            scanFolderRecursive(
                                context,
                                dao,
                                deltaPlusRoot,
                                folder,
                                explorer,
                                emptyList(),
                                addedContent,
                                null,
                                logs
                            )
                        }
                    } // deltaPlus docs

                    // Process added content
                    addedContent.forEach {
                        if (!existsInCollection(it, dao, true, logs)) {
                            createJsonFileFor(context, it, explorer, logs)
                            ContentHelper.addContent(context, dao, it)
                        }
                    }
                } // explorer
            } // deltaPlus roots

            val toRemove = delta.second.filter { it > 0 }
            Timber.d("delta- : " + toRemove.size + " useful / " + delta.second.size + " total")
            toRemove.forEach { idToRemove ->
                if (isStopped) return
                Timber.d("delta- => $idToRemove")
                Content().apply {
                    id = idToRemove
                    ContentHelper.removeContent(context, dao, this)
                }
            }
        } catch (e: Exception) {
            Timber.w(e)
        } finally {
            dao.cleanup()
        }
    }
}