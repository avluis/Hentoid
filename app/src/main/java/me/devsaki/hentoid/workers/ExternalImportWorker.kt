package me.devsaki.hentoid.workers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.WorkerParameters
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.JSON_FILE_NAME_V2
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.json.JsonContent
import me.devsaki.hentoid.notification.import_.ImportCompleteNotification
import me.devsaki.hentoid.notification.import_.ImportProgressNotification
import me.devsaki.hentoid.notification.import_.ImportStartNotification
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.JsonHelper
import me.devsaki.hentoid.util.LogEntry
import me.devsaki.hentoid.util.LogInfo
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.StringHelper
import me.devsaki.hentoid.util.file.DiskCache.init
import me.devsaki.hentoid.util.file.FileExplorer
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.file.getArchiveNamesFilter
import me.devsaki.hentoid.util.getContentJsonNamesFilter
import me.devsaki.hentoid.util.getFileWithName
import me.devsaki.hentoid.util.image.imageNamesFilter
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.util.scanArchive
import me.devsaki.hentoid.util.scanBookFolder
import me.devsaki.hentoid.util.scanChapterFolders
import me.devsaki.hentoid.util.scanForArchives
import me.devsaki.hentoid.util.writeLog
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.io.IOException
import java.util.regex.Pattern

class ExternalImportWorker(context: Context, parameters: WorkerParameters) :
    BaseWorker(context, parameters, R.id.external_import_service, null) {

    companion object {
        private val ENDS_WITH_NUMBER = Pattern.compile(".*\\d+(\\.\\d+)?$")

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
                ProcessEvent.Type.PROGRESS,
                R.id.import_external,
                step,
                booksOK,
                booksKO,
                nbBooks
            )
        )
    }

    private fun eventProcessed(step: Int, name: String) {
        EventBus.getDefault()
            .post(ProcessEvent(ProcessEvent.Type.PROGRESS, R.id.import_external, step, name))
    }

    private fun eventComplete(
        step: Int,
        nbBooks: Int,
        booksOK: Int,
        booksKO: Int,
        cleanupLogFile: DocumentFile?
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

    private fun trace(
        priority: Int,
        chapter: Int,
        memoryLog: MutableList<LogEntry>?,
        str: String,
        vararg t: String
    ) {
        val s = String.format(str, *t)
        Timber.log(priority, s)
        val isError = priority > Log.INFO
        memoryLog?.add(LogEntry(s, chapter, isError))
    }


    /**
     * Import books from external folder
     */
    private fun startImport(context: Context) {
        var booksOK = 0 // Number of books imported
        var booksKO = 0 // Number of folders found with no valid book inside
        val log: MutableList<LogEntry> = ArrayList()
        val rootFolder =
            FileHelper.getDocumentFromTreeUriString(context, Preferences.getExternalLibraryUri())
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
                        rootFolder,
                        explorer,
                        ArrayList(),
                        detectedContent,
                        dao,
                        log
                    )
                } finally {
                    dao.cleanup()
                }
                eventComplete(PrimaryImportWorker.STEP_2_BOOK_FOLDERS, 0, 0, 0, null)

                // Write JSON file for every found book and persist it in the DB
                trace(
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
                dao = ObjectBoxDAO()
                try {
                    for (content in detectedContent) {
                        if (isStopped) break
                        // If the same book folder is already in the DB, that means the user is trying to import
                        // a subfolder of the Hentoid main folder (yes, it has happened) => ignore these books
                        var duplicateOrigin = "folder"
                        var existingDuplicate =
                            dao.selectContentByStorageUri(content.storageUri, false)

                        // The very same book may also exist in the DB under a different folder,
                        if (null == existingDuplicate && content.url.trim()
                                .isNotEmpty() && content.site != Site.NONE
                        ) {
                            existingDuplicate =
                                dao.selectContentBySourceAndUrl(content.site, content.url, "")
                            // Ignore the duplicate if it is queued; we do prefer to import a full book
                            if (existingDuplicate != null) {
                                if (ContentHelper.isInQueue(existingDuplicate.status)) existingDuplicate =
                                    null else duplicateOrigin = "book"
                            }
                        }
                        if (existingDuplicate != null && !existingDuplicate.isFlaggedForDeletion) {
                            booksKO++
                            trace(
                                Log.INFO,
                                1,
                                log,
                                "Import book KO! ($duplicateOrigin already in collection) : %s",
                                content.storageUri
                            )
                            continue
                        }
                        if (content.jsonUri.isEmpty()) {
                            var jsonUri: Uri? = null
                            try {
                                jsonUri = createJsonFileFor(context, content, explorer)
                            } catch (ioe: IOException) {
                                Timber.w(ioe) // Not blocking
                                trace(
                                    Log.WARN,
                                    1,
                                    log,
                                    "Could not create JSON in %s",
                                    content.storageUri
                                )
                            }
                            if (jsonUri != null) content.jsonUri = jsonUri.toString()
                        }
                        ContentHelper.addContent(context, dao, content)
                        trace(
                            Log.INFO,
                            1,
                            log,
                            "Import book OK : %s",
                            content.storageUri
                        )
                        booksOK++
                        notificationManager.notify(
                            ImportProgressNotification(
                                content.title,
                                booksOK + booksKO,
                                detectedContent.size
                            )
                        )
                        eventProgress(
                            PrimaryImportWorker.STEP_3_BOOKS,
                            detectedContent.size,
                            booksOK,
                            booksKO
                        )
                    } // detected content
                    dao.deleteAllFlaggedBooks(false, null)
                    dao.cleanupOrphanAttributes()
                } finally {
                    dao.cleanup()
                }
                trace(
                    Log.INFO,
                    2,
                    log,
                    "Import books complete - %s OK; %s KO; %s final count",
                    booksOK.toString() + "",
                    booksKO.toString() + "",
                    detectedContent.size.toString() + ""
                )
                eventComplete(
                    PrimaryImportWorker.STEP_3_BOOKS,
                    detectedContent.size,
                    booksOK,
                    booksKO,
                    null
                )
                // Clear disk cache as import may reuse previous image IDs
                init(applicationContext)
            }
        } catch (e: IOException) {
            Timber.w(e)
            Helper.logException(e)
        } finally {
            logFile = context.writeLog(buildLogInfo(log))
            eventComplete(
                PrimaryImportWorker.STEP_4_QUEUE_FINAL,
                booksOK + booksKO,
                booksOK,
                booksKO,
                logFile
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

    private fun scanFolderRecursive(
        context: Context,
        root: DocumentFile,
        explorer: FileExplorer,
        parentNames: List<String>,
        library: MutableList<Content>,
        dao: CollectionDAO,
        log: MutableList<LogEntry>
    ) {
        if (parentNames.size > 4) return  // We've descended too far
        val rootName = if (null == root.name) "" else root.name!!
        eventProcessed(2, rootName)
        Timber.d(">>>> scan root %s", root.uri)
        val files = explorer.listDocumentFiles(context, root)
        val subFolders: MutableList<DocumentFile> = ArrayList()
        val images: MutableList<DocumentFile> = ArrayList()
        val archives: MutableList<DocumentFile> = ArrayList()
        val jsons: MutableList<DocumentFile> = ArrayList()
        val contentJsons: MutableList<DocumentFile> = ArrayList()

        // Look for the interesting stuff
        for (file in files) if (file.name != null) {
            if (file.isDirectory) subFolders.add(file) else if (imageNamesFilter.accept(file.name!!)) images.add(
                file
            ) else if (getArchiveNamesFilter().accept(
                    file.name!!
                )
            ) archives.add(file) else if (JsonHelper.getJsonNamesFilter().accept(file.name!!)) {
                jsons.add(file)
                if (getContentJsonNamesFilter().accept(file.name!!)) contentJsons.add(file)
            }
        }

        // If at least 2 subfolders and everyone of them ends with a number, we've got a multi-chapter book
        if (subFolders.size >= 2) {
            val allSubfoldersEndWithNumber =
                subFolders.mapNotNull { obj: DocumentFile -> obj.name }
                    .all { n: String ->
                        ENDS_WITH_NUMBER.matcher(n).matches()
                    }
            if (allSubfoldersEndWithNumber) {
                // Make certain folders contain actual books by peeking the 1st one (could be a false positive, i.e. folders per year '1990-2000')
                val nbPicturesInside = explorer.countFiles(subFolders[0], imageNamesFilter)
                if (nbPicturesInside > 1) {
                    val json = getFileWithName(jsons, JSON_FILE_NAME_V2)
                    library.add(
                        scanChapterFolders(
                            context,
                            root,
                            subFolders,
                            explorer,
                            parentNames,
                            dao,
                            json
                        )
                    )
                }
                // Look for archives inside
                val nbArchivesInside = explorer.countFiles(subFolders[0], getArchiveNamesFilter())
                if (nbArchivesInside > 0) {
                    val c = scanForArchives(context, subFolders, explorer, parentNames, dao)
                    library.addAll(c)
                }
            }
        }
        if (archives.isNotEmpty()) { // We've got an archived book
            for (archive in archives) {
                val json = getFileWithName(jsons, archive.name)
                val c = scanArchive(
                    context,
                    root,
                    archive,
                    parentNames,
                    StatusContent.EXTERNAL,
                    dao,
                    json
                )
                if (c.status != StatusContent.IGNORED) library.add(c) else trace(
                    Log.DEBUG,
                    0,
                    log,
                    "Archive ignored (unsupported pictures or corrupted archive) : %s",
                    archive.name ?: "<name not found>"
                )
            }
        }
        if (images.size > 2 || contentJsons.isNotEmpty()) { // We've got a book
            val json = getFileWithName(contentJsons, JSON_FILE_NAME_V2)
            library.add(
                scanBookFolder(
                    context,
                    root,
                    explorer,
                    parentNames,
                    StatusContent.EXTERNAL,
                    dao,
                    images,
                    json
                )
            )
        }

        // Go down one level
        val newParentNames: MutableList<String> = ArrayList(parentNames)
        newParentNames.add(rootName)
        for (subfolder in subFolders) scanFolderRecursive(
            context,
            subfolder,
            explorer,
            newParentNames,
            library,
            dao,
            log
        )
    }

    @Throws(IOException::class)
    private fun createJsonFileFor(
        context: Context,
        content: Content,
        explorer: FileExplorer
    ): Uri? {
        if (null == content.storageUri || content.storageUri.isEmpty()) return null

        // Check if the storage URI is valid
        val contentFolder: DocumentFile? = if (content.isArchive) {
            FileHelper.getDocumentFromTreeUriString(context, content.archiveLocationUri)
        } else {
            FileHelper.getDocumentFromTreeUriString(context, content.storageUri)
        }
        if (null == contentFolder) return null

        // If a JSON file already exists at that location, use it as is, don't overwrite it
        val jsonName = if (content.isArchive) {
            val archiveFile = FileHelper.getFileFromSingleUriString(context, content.storageUri)
            FileHelper.getFileNameWithoutExtension(
                StringHelper.protect(
                    archiveFile!!.name
                )
            ) + ".json"
        } else {
            JSON_FILE_NAME_V2
        }
        val jsonFile = explorer.findFile(context, contentFolder, jsonName)
        return if (jsonFile != null && jsonFile.exists()) jsonFile.uri else JsonHelper.jsonToFile(
            context, JsonContent.fromEntity(content),
            JsonContent::class.java, contentFolder, jsonName
        ).uri
    }
}