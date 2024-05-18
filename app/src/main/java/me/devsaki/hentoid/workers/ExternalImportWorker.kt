package me.devsaki.hentoid.workers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.WorkerParameters
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.notification.import_.ImportCompleteNotification
import me.devsaki.hentoid.notification.import_.ImportProgressNotification
import me.devsaki.hentoid.notification.import_.ImportStartNotification
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.LogEntry
import me.devsaki.hentoid.util.LogInfo
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.Trace
import me.devsaki.hentoid.util.createJsonFileFor
import me.devsaki.hentoid.util.existsInCollection
import me.devsaki.hentoid.util.file.Beholder
import me.devsaki.hentoid.util.file.DiskCache.init
import me.devsaki.hentoid.util.file.FileExplorer
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.util.scanFolderRecursive
import me.devsaki.hentoid.util.writeLog
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.io.IOException

class ExternalImportWorker(context: Context, parameters: WorkerParameters) :
    BaseWorker(context, parameters, R.id.external_import_service, null) {

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

    override fun onClear() {
        // Nothing
    }

    override fun getToWork(input: Data) {
        startImport(applicationContext)
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
        var booksOK = 0 // Number of books imported
        var booksKO = 0 // Number of folders found with no valid book inside
        val log: MutableList<LogEntry> = ArrayList()
        val rootFolder = getDocumentFromTreeUriString(context, Preferences.getExternalLibraryUri())
        if (null == rootFolder) {
            Timber.e("External folder is not defined (%s)", Preferences.getExternalLibraryUri())
            return
        }
        val logFile: DocumentFile?
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
                        log
                    )
                } finally {
                    dao.cleanup()
                }
                eventComplete(PrimaryImportWorker.STEP_2_BOOK_FOLDERS, 0, 0, 0, null)

                // Write JSON file for every found book and persist it in the DB
                Trace(
                    Log.DEBUG,
                    0,
                    log,
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
                        if (existsInCollection(content, dao, true, log)) {
                            booksKO++
                            continue
                        }
                        createJsonFileFor(context, content, explorer, log)
                        ContentHelper.addContent(context, dao, content)
                        Trace(
                            Log.INFO, 1, log, "Import book OK : %s", content.storageUri
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
                Trace(
                    Log.INFO,
                    2,
                    log,
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
                Beholder.updateSnapshot(context, addedContent)
                Beholder.saveSnapshot(context)
            }
        } catch (e: IOException) {
            Timber.w(e)
            Helper.logException(e)
        } finally {
            logFile = context.writeLog(buildLogInfo(log))
            eventComplete(
                PrimaryImportWorker.STEP_4_QUEUE_FINAL, booksOK + booksKO, booksOK, booksKO, logFile
            ) // Final event; should be step 4
            notificationManager.notify(ImportCompleteNotification(booksOK, booksKO))
        }
    }

    private fun buildLogInfo(log: List<LogEntry>): LogInfo {
        val logInfo = LogInfo("import_external_log")
        logInfo.setHeaderName("Import external")
        logInfo.setNoDataMessage("No content detected.")
        logInfo.setEntries(log)
        return logInfo
    }
}