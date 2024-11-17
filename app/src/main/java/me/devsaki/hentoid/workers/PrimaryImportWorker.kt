package me.devsaki.hentoid.workers

import android.content.Context
import android.util.Log
import androidx.annotation.CheckResult
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.WorkerParameters
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.BOOKMARKS_JSON_FILE_NAME
import me.devsaki.hentoid.core.GROUPS_JSON_FILE_NAME
import me.devsaki.hentoid.core.JSON_FILE_NAME
import me.devsaki.hentoid.core.JSON_FILE_NAME_OLD
import me.devsaki.hentoid.core.JSON_FILE_NAME_V2
import me.devsaki.hentoid.core.QUEUE_JSON_FILE_NAME
import me.devsaki.hentoid.core.RENAMING_RULES_JSON_FILE_NAME
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.DuplicatesDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DownloadMode
import me.devsaki.hentoid.database.domains.ErrorRecord
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.database.domains.QueueRecord
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.ErrorType
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.events.DownloadCommandEvent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.json.ContentV1
import me.devsaki.hentoid.json.DoujinBuilder
import me.devsaki.hentoid.json.JsonContent
import me.devsaki.hentoid.json.JsonContentCollection
import me.devsaki.hentoid.json.URLBuilder
import me.devsaki.hentoid.notification.import_.ImportCompleteNotification
import me.devsaki.hentoid.notification.import_.ImportProgressNotification
import me.devsaki.hentoid.notification.import_.ImportStartNotification
import me.devsaki.hentoid.util.LogEntry
import me.devsaki.hentoid.util.LogInfo
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.addContent
import me.devsaki.hentoid.util.createImageListFromFiles
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.file.DiskCache.init
import me.devsaki.hentoid.util.file.FileExplorer
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.getExtension
import me.devsaki.hentoid.util.findDuplicateContentByUrl
import me.devsaki.hentoid.util.formatBookFolderName
import me.devsaki.hentoid.util.getPathRoot
import me.devsaki.hentoid.util.image.isSupportedImage
import me.devsaki.hentoid.util.importBookmarks
import me.devsaki.hentoid.util.importRenamingRules
import me.devsaki.hentoid.util.isInQueue
import me.devsaki.hentoid.util.jsonToFile
import me.devsaki.hentoid.util.jsonToObject
import me.devsaki.hentoid.util.matchFilesToImageList
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.util.persistJson
import me.devsaki.hentoid.util.removeExternalAttributes
import me.devsaki.hentoid.util.scanBookFolder
import me.devsaki.hentoid.util.trace
import me.devsaki.hentoid.util.writeLog
import me.devsaki.hentoid.workers.data.PrimaryImportData
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.io.IOException
import java.time.Instant
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max


const val STEP_GROUPS = 0
const val STEP_1 = 1
const val STEP_2_BOOK_FOLDERS = 2
const val STEP_3_BOOKS = 3
const val STEP_3_PAGES = 4
const val STEP_4_QUEUE_FINAL = 5

class PrimaryImportWorker(context: Context, parameters: WorkerParameters) :
    BaseWorker(context, parameters, R.id.import_service, null) {

    companion object {
        fun isRunning(context: Context): Boolean {
            return isRunning(context, R.id.import_service)
        }
    }

    // VARIABLES
    private var booksOK = 0 // Number of books imported

    private var booksKO = 0 // Number of folders found with no valid book inside

    private var nbFolders = 0 // Number of folders found with no content but subfolders


    override fun getStartNotification(): BaseNotification {
        return ImportStartNotification()
    }

    override fun onInterrupt() {
        // Nothing
    }

    override suspend fun onClear(logFile: DocumentFile?) {
        // Nothing
    }

    override fun runProgressNotification() {
        // Using custom method
    }

    override suspend fun getToWork(input: Data) {
        val data = PrimaryImportData.Parser(input)
        startImport(
            data.location,
            data.targetRoot,
            data.refreshRename,
            data.refreshRemovePlaceholders,
            data.refreshRenumberPages,
            data.refreshCleanNoJson,
            data.refreshCleanNoImages,
            data.importGroups
        )
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun eventProgress(
        step: Int,
        nbBooks: Int,
        booksOK: Int,
        booksKO: Int,
        name: String = ""
    ) {
        GlobalScope.launch(Dispatchers.Default) {
            EventBus.getDefault().post(
                ProcessEvent(
                    ProcessEvent.Type.PROGRESS,
                    R.id.import_primary,
                    step,
                    name,
                    booksOK,
                    booksKO,
                    nbBooks
                )
            )
        }
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
                R.id.import_primary,
                step,
                booksOK,
                booksKO,
                nbBooks,
                cleanupLogFile
            )
        )
    }

    /**
     * Import books from known source folders
     *
     * @param rename             True if the user has asked for a folder renaming when calling import from Preferences
     * @param removePlaceholders True if the user has asked for a removal of all books with the status PLACEHOLDER (that do not exist on storage)
     * @param renumberPages      True if the user has asked to renumber pages on books where there are numbering gaps
     * @param cleanNoJSON        True if the user has asked for a cleanup of folders with no JSONs when calling import from Preferences
     * @param cleanNoImages      True if the user has asked for a cleanup of folders with no images when calling import from Preferences
     * @param importGroups       True if the worker has to import groups from the groups JSON; false if existing groups should be kept
     */
    private suspend fun startImport(
        location: StorageLocation,
        targetRootUri: String,
        rename: Boolean,
        removePlaceholders: Boolean,
        renumberPages: Boolean,
        cleanNoJSON: Boolean,
        cleanNoImages: Boolean,
        importGroups: Boolean
    ) = withContext(Dispatchers.IO) {
        booksOK = 0
        booksKO = 0
        nbFolders = 0
        val log: MutableList<LogEntry> = ArrayList()
        val context = applicationContext

        // Stop downloads; it can get messy if downloading _and_ refresh / import happen at the same time
        EventBus.getDefault().post(DownloadCommandEvent(DownloadCommandEvent.Type.EV_PAUSE, null))
        var previousUriStr = Settings.getStorageUri(location)
        if (previousUriStr.isEmpty()) previousUriStr = "FAIL" // Auto-fails if location is not set
        Settings.setStorageUri(location, targetRootUri)

        val rootFolder = getDocumentFromTreeUriString(context, targetRootUri)
        if (null == rootFolder) {
            Timber.e("Root folder is invalid for location %s (%s)", location.name, targetRootUri)
            return@withContext
        }

        val bookFolders: MutableList<DocumentFile> = ArrayList()
        try {
            FileExplorer(context, rootFolder.uri).use { explorer ->
                // 1st pass : Import groups JSON
                if (importGroups) importGroups(context, rootFolder, explorer, log)

                // 2nd pass : count subfolders of every site folder
                eventProgress(
                    STEP_2_BOOK_FOLDERS,
                    1,
                    0,
                    0,
                    context.getString(R.string.refresh_step1)
                )
                val siteFolders =
                    explorer.listFolders(context, rootFolder)
                for ((foldersProcessed, f) in siteFolders.withIndex()) {
                    eventProgress(
                        STEP_2_BOOK_FOLDERS,
                        siteFolders.size,
                        foldersProcessed,
                        0,
                        f.name ?: ""
                    )
                    bookFolders.addAll(explorer.listFolders(context, f))
                }
                eventComplete(
                    STEP_2_BOOK_FOLDERS,
                    siteFolders.size,
                    siteFolders.size,
                    0,
                    null
                )
                notificationManager.notify(
                    ImportProgressNotification(
                        context.resources.getString(R.string.starting_import), 0, 0
                    )
                )

                // 3rd pass : scan every folder for a JSON file or subdirectories
                val enabled = context.resources.getString(R.string.enabled)
                val disabled = context.resources.getString(R.string.disabled)
                trace(
                    Log.DEBUG,
                    0,
                    log,
                    "Import books starting - initial detected count : %s",
                    bookFolders.size.toString() + ""
                )
                trace(
                    Log.INFO,
                    0,
                    log,
                    "Rename folders %s",
                    if (rename) enabled else disabled
                )
                trace(
                    Log.INFO,
                    0,
                    log,
                    "Remove folders with no JSONs %s",
                    if (cleanNoJSON) enabled else disabled
                )
                trace(
                    Log.INFO,
                    0,
                    log,
                    "Remove folders with no images %s",
                    if (cleanNoImages) enabled else disabled
                )

                // Cleanup previously detected duplicates
                // (as we're updating the collection, they're now obsolete)
                val duplicatesDAO = DuplicatesDAO()
                try {
                    duplicatesDAO.clearEntries()
                } finally {
                    duplicatesDAO.cleanup()
                }

                // Flag DB content for cleanup
                var dao: CollectionDAO = ObjectBoxDAO()
                try {
                    dao.flagAllInternalBooks(
                        getPathRoot(previousUriStr),
                        removePlaceholders
                    )
                    dao.flagAllErrorBooksWithJson()
                } finally {
                    dao.cleanup()
                }

                try {
                    dao = ObjectBoxDAO()
                    bookFolders.forEachIndexed { index, bookFolder ->
                        if (isStopped) throw InterruptedException()
                        importFolder(
                            context,
                            explorer,
                            dao,
                            bookFolders,
                            rootFolder,
                            bookFolder,
                            log,
                            rename,
                            renumberPages,
                            cleanNoJSON,
                            cleanNoImages
                        )
                        // Clear the DAO every 2500K iterations to optimize memory
                        if (0 == index % 2500) {
                            dao.cleanup()
                            dao = ObjectBoxDAO()
                        }
                    }
                } finally {
                    dao.cleanup()
                }
                trace(
                    Log.INFO,
                    STEP_3_BOOKS,
                    log,
                    "Import books complete - %s OK; %s KO; %s final count",
                    booksOK.toString() + "",
                    booksKO.toString() + "",
                    (bookFolders.size - nbFolders).toString() + ""
                )
                eventComplete(
                    STEP_3_BOOKS,
                    bookFolders.size,
                    booksOK,
                    booksKO,
                    null
                )
                // Clear disk cache as import may reuse previous image IDs
                init(applicationContext)

                // 4th pass : Import queue, bookmarks and renaming rules JSON
                dao = ObjectBoxDAO()
                try {
                    val queueFile =
                        explorer.findFile(context, rootFolder, QUEUE_JSON_FILE_NAME)
                    if (queueFile != null) importQueue(
                        context,
                        queueFile,
                        dao,
                        log
                    ) else trace(
                        Log.INFO,
                        STEP_4_QUEUE_FINAL,
                        log,
                        "No queue file found"
                    )
                    val bookmarksFile =
                        explorer.findFile(context, rootFolder, BOOKMARKS_JSON_FILE_NAME)
                    if (bookmarksFile != null) importBookmarks(
                        context,
                        bookmarksFile,
                        dao,
                        log
                    ) else trace(
                        Log.INFO,
                        STEP_4_QUEUE_FINAL,
                        log,
                        "No bookmarks file found"
                    )
                    val rulesFile =
                        explorer.findFile(context, rootFolder, RENAMING_RULES_JSON_FILE_NAME)
                    if (rulesFile != null) importRenamingRules(
                        context,
                        rulesFile,
                        dao,
                        log
                    ) else trace(
                        Log.INFO,
                        STEP_4_QUEUE_FINAL,
                        log,
                        "No renaming rules file found"
                    )
                } finally {
                    dao.cleanup()
                }
            }
        } catch (e: IOException) {
            Timber.w(e)
            // Restore interrupted state
            Thread.currentThread().interrupt()
        } catch (e: InterruptedException) {
            Timber.w(e)
            Thread.currentThread().interrupt()
        } finally {
            // Write log in root folder
            val logFile = context.writeLog(
                buildLogInfo(
                    rename || cleanNoJSON || cleanNoImages,
                    location,
                    log
                )
            )
            if (!isStopped) { // Should only be done when things have run properly
                val dao: CollectionDAO = ObjectBoxDAO()
                try {
                    dao.deleteAllFlaggedBooks(true, getPathRoot(previousUriStr))
                    dao.deleteAllFlaggedGroups()
                    dao.cleanupOrphanAttributes()
                } finally {
                    dao.cleanup()
                }
            }
            eventComplete(
                STEP_4_QUEUE_FINAL,
                bookFolders.size,
                booksOK,
                booksKO,
                logFile
            )
            notificationManager.notify(ImportCompleteNotification(booksOK, booksKO))
        }
    }

    private fun importGroups(
        context: Context,
        rootFolder: DocumentFile,
        explorer: FileExplorer,
        log: MutableList<LogEntry>
    ) {
        trace(Log.INFO, STEP_GROUPS, log, "Importing groups")
        val dao: CollectionDAO = ObjectBoxDAO()
        try {
            val groupsFile = explorer.findFile(context, rootFolder, GROUPS_JSON_FILE_NAME)
            if (groupsFile != null) {
                dao.flagAllGroups(Grouping.CUSTOM) // Flag existing groups for cleanup
                importGroups(context, groupsFile, dao, log)
            } else trace(Log.INFO, STEP_GROUPS, log, "No groups file found")
        } finally {
            dao.cleanup()
        }
    }

    private fun importFolder(
        context: Context,
        explorer: FileExplorer,
        dao: CollectionDAO,
        bookFolders: MutableList<DocumentFile>, // Passed here to add subfolders to it
        parent: DocumentFile,
        bookFolder: DocumentFile,
        log: MutableList<LogEntry>,
        rename: Boolean,
        renumberPages: Boolean,
        cleanNoJSON: Boolean,
        cleanNoImages: Boolean
    ) {
        var content: Content? = null
        var bookFiles: List<DocumentFile>? = null

        // Detect the presence of images if the corresponding cleanup option has been enabled
        if (cleanNoImages) {
            bookFiles = explorer.listFiles(context, bookFolder, null)
            val nbImages = bookFiles.count {
                isSupportedImage(it.name ?: "")
            }
            if (0 == nbImages && !explorer.hasFolders(bookFolder)) { // No supported images nor subfolders
                var doRemove = true
                try {
                    content = importJson(context, bookFolder, bookFiles, dao)
                    // Don't delete books that are _not supposed to_ have downloaded images
                    if (content != null && content.downloadMode == DownloadMode.STREAM)
                        doRemove = false
                } catch (e: ParseException) {
                    trace(
                        Log.WARN,
                        STEP_1,
                        log,
                        "[Remove no image] Folder %s : unreadable JSON",
                        bookFolder.uri.toString()
                    )
                }
                if (doRemove) {
                    booksKO++
                    val success = bookFolder.delete()
                    trace(
                        Log.INFO,
                        STEP_1,
                        log,
                        "[Remove no image %s] Folder %s",
                        if (success) "OK" else "KO",
                        bookFolder.uri.toString()
                    )
                    return
                }
            }
        }

        // Find the corresponding flagged book in the library
        val existingFlaggedContent = dao.selectContentByStorageUri(bookFolder.uri.toString(), true)

        // Detect JSON and try to parse it
        try {
            if (null == bookFiles) bookFiles = explorer.listFiles(context, bookFolder, null)
            if (null == content) content = importJson(context, bookFolder, bookFiles, dao)
            if (content != null) {
                // If the book exists and is flagged for deletion, delete it to make way for a new import (as intended)
                if (existingFlaggedContent != null) dao.deleteContent(existingFlaggedContent)

                // If the very same book still exists in the DB at this point, it means it's present in the queue
                // => don't import it even though it has a JSON file; it has been re-queued after being downloaded or viewed once
                val existingDuplicate = findDuplicateContentByUrl(content, dao)
                if (existingDuplicate != null && !existingDuplicate.isFlaggedForDeletion) {
                    booksKO++
                    val location =
                        if (isInQueue(existingDuplicate.status)) "queue" else "collection"
                    trace(
                        Log.INFO, STEP_2_BOOK_FOLDERS, log,
                        "Import book KO! (already in $location) : %s", bookFolder.uri.toString()
                    )
                    return
                }
                var contentImages = content.imageList
                if (rename) {
                    val canonicalBookFolderName = formatBookFolderName(content)
                    val currentPathParts = bookFolder.uri.pathSegments
                    val bookUriParts =
                        currentPathParts[currentPathParts.size - 1].split(":")
                    val bookPathParts = bookUriParts[bookUriParts.size - 1].split("/")
                    val bookFolderName = bookPathParts[bookPathParts.size - 1]
                    if (!canonicalBookFolderName.first.equals(bookFolderName, ignoreCase = true)) {
                        if (renameFolder(
                                context,
                                bookFolder,
                                content,
                                explorer,
                                canonicalBookFolderName.first
                            )
                        ) {
                            trace(
                                Log.INFO,
                                STEP_2_BOOK_FOLDERS,
                                log,
                                "[Rename OK] Folder %s renamed to %s",
                                bookFolderName,
                                canonicalBookFolderName.first
                            )
                            // Rescan files inside the renamed folder
                            bookFiles = explorer.listFiles(context, bookFolder, null)
                        } else {
                            trace(
                                Log.WARN,
                                STEP_2_BOOK_FOLDERS,
                                log,
                                "[Rename KO] Could not rename file %s to %s",
                                bookFolderName,
                                canonicalBookFolderName.first
                            )
                        }
                    }
                }

                // Attach image file Uri's to the book's images
                val imageFiles = bookFiles.filter { isSupportedImage(it.name ?: "") }
                if (imageFiles.isNotEmpty()) {
                    // No images described in the JSON -> recreate them
                    if (contentImages.isEmpty()) {
                        contentImages = createImageListFromFiles(imageFiles)
                        content.setImageFiles(contentImages)
                        content.cover.url = content.coverImageUrl
                    } else { // Existing images described in the JSON
                        // CLEANUPS
                        var cleaned = false

                        // Get basic stats + fix chapterless pages
                        var maxPageOrder = -1
                        var previousChapter: Chapter? = null
                        for (img in contentImages) {
                            maxPageOrder =
                                max(maxPageOrder.toDouble(), img.order.toDouble()).toInt()
                            if (!img.isCover) {
                                val chapter = img.linkedChapter
                                // If a page is chapterless while the book has chapters, attach it to the previous chapter
                                if (null == chapter && previousChapter != null) {
                                    img.setChapter(previousChapter)
                                    previousChapter.addImageFile(img)
                                    cleaned = true
                                } else {
                                    previousChapter = chapter
                                }
                            }
                        }

                        // Remove non-cover pages that have the cover URL (old issue about extra page downloads)
                        // (exclude the 1st page because it have a same url with the cover in some sites)
                        val coverUrl = content.coverImageUrl
                        val coverImgs = contentImages.filterNot { i: ImageFile ->
                            hasSameUrl(i, coverUrl) && !i.isCover && i.order != 1
                        }
                        if (coverImgs.size < contentImages.size) {
                            contentImages = coverImgs
                            val nbCovers = contentImages.count { it.isCover }
                            content.qtyPages = contentImages.size - nbCovers
                            cleaned = true
                        }

                        // Map files to image list
                        contentImages = matchFilesToImageList(imageFiles, contentImages)
                        content.setImageFiles(contentImages)
                        if (cleaned) persistJson(context, content)
                    }
                    if (renumberPages) renumberPages(context, content, contentImages, log)
                } else if (Settings.isImportQueueEmptyBooks
                    && !content.manuallyMerged && content.downloadMode == DownloadMode.DOWNLOAD
                ) { // If no image file found, it goes in the errors queue
                    if (!isInQueue(content.status)) content.status = StatusContent.ERROR
                    val errors: MutableList<ErrorRecord> = ArrayList()
                    errors.add(
                        ErrorRecord(
                            type = ErrorType.IMPORT,
                            contentPart = applicationContext.resources.getQuantityString(
                                R.plurals.book,
                                1
                            ),
                            description = "No local images found when importing - Please redownload",
                            timestamp = Instant.now()
                        )
                    )
                    content.setErrorLog(errors)
                }

                // If content has an external-library tag or an EXTERNAL status, remove it because we're importing for the primary library now
                removeExternalAttributes(content)
                content.computeSize()
                addContent(context, dao, content)
                val customGroups =
                    content.getGroupItems(Grouping.CUSTOM)
                        .mapNotNull { it.linkedGroup }
                        .map { it.name }
                val groupStr =
                    if (customGroups.isEmpty()) "" else " in " + customGroups.joinToString(", ")
                trace(
                    Log.INFO,
                    STEP_2_BOOK_FOLDERS,
                    log,
                    "Import book OK$groupStr : %s",
                    bookFolder.uri.toString()
                )
            } else { // JSON not found
                val subfolders = explorer.listFolders(context, bookFolder)
                if (subfolders.isNotEmpty()) { // Folder doesn't contain books but contains subdirectories
                    bookFolders.addAll(subfolders)
                    trace(
                        Log.INFO,
                        STEP_2_BOOK_FOLDERS,
                        log,
                        "Subfolders found in : %s",
                        bookFolder.uri.toString()
                    )
                    nbFolders++
                    return
                } else { // No JSON nor any subdirectory
                    trace(
                        Log.WARN,
                        STEP_2_BOOK_FOLDERS,
                        log,
                        "Import book KO! (no JSON found) : %s",
                        bookFolder.uri.toString()
                    )
                    // Deletes the folder if cleanup is active
                    if (cleanNoJSON) {
                        val success = bookFolder.delete()
                        trace(
                            Log.INFO,
                            STEP_2_BOOK_FOLDERS,
                            log,
                            "[Remove no JSON %s] Folder %s",
                            if (success) "OK" else "KO",
                            bookFolder.uri.toString()
                        )
                    }
                }
            }
            if (null == content) booksKO++ else booksOK++
        } catch (jse: ParseException) {
            // If the book is still present in the DB, regenerate the JSON and unflag the book
            if (existingFlaggedContent != null) {
                try {
                    val newJson = jsonToFile(
                        context, JsonContent(existingFlaggedContent),
                        JsonContent::class.java, bookFolder, JSON_FILE_NAME_V2
                    )
                    existingFlaggedContent.jsonUri = newJson.uri.toString()
                    existingFlaggedContent.isFlaggedForDeletion = false
                    dao.insertContent(existingFlaggedContent)
                    trace(
                        Log.INFO,
                        STEP_2_BOOK_FOLDERS,
                        log,
                        "Import book OK (JSON regenerated) : %s",
                        bookFolder.uri.toString()
                    )
                    booksOK++
                } catch (e: IOException) {
                    Timber.w(e)
                    trace(
                        Log.ERROR,
                        STEP_2_BOOK_FOLDERS,
                        log,
                        "Import book ERROR while regenerating JSON : %s for Folder %s",
                        jse.message!!,
                        bookFolder.uri.toString()
                    )
                    booksKO++
                } catch (e: JsonDataException) {
                    Timber.w(e)
                    trace(
                        Log.ERROR,
                        STEP_2_BOOK_FOLDERS,
                        log,
                        "Import book ERROR while regenerating JSON : %s for Folder %s",
                        jse.message!!,
                        bookFolder.uri.toString()
                    )
                    booksKO++
                }
            } else { // If not, rebuild the book and regenerate the JSON according to stored data
                try {
                    val parentNames: MutableList<String> = ArrayList()
                    // Try and detect the site according to the parent folder
                    val parents =
                        bookFolder.uri.path!!.split("/") // _not_ File.separator but the universal Uri separator
                    if (parents.size > 1) {
                        for (s in Site.entries) if (parents[parents.size - 2].equals(
                                s.folder,
                                ignoreCase = true
                            )
                        ) {
                            parentNames.add(s.folder)
                            break
                        }
                    }
                    // Scan the folder
                    val storedContent = scanBookFolder(
                        context,
                        parent,
                        bookFolder,
                        explorer,
                        parentNames,
                        StatusContent.DOWNLOADED,
                        dao,
                        null,
                        null
                    )
                    val newJson = jsonToFile(
                        context, JsonContent(storedContent),
                        JsonContent::class.java, bookFolder, JSON_FILE_NAME_V2
                    )
                    storedContent.jsonUri = newJson.uri.toString()
                    addContent(context, dao, storedContent)
                    trace(
                        Log.INFO,
                        STEP_2_BOOK_FOLDERS,
                        log,
                        "Import book OK (Content regenerated) : %s",
                        bookFolder.uri.toString()
                    )
                    booksOK++
                } catch (e: IOException) {
                    Timber.w(e)
                    trace(
                        Log.ERROR,
                        STEP_2_BOOK_FOLDERS,
                        log,
                        "Import book ERROR while regenerating Content : %s for Folder %s",
                        jse.message!!,
                        bookFolder.uri.toString()
                    )
                    booksKO++
                } catch (e: JsonDataException) {
                    Timber.w(e)
                    trace(
                        Log.ERROR,
                        STEP_2_BOOK_FOLDERS,
                        log,
                        "Import book ERROR while regenerating Content : %s for Folder %s",
                        jse.message!!,
                        bookFolder.uri.toString()
                    )
                    booksKO++
                }
            }
        } catch (e: Exception) {
            Timber.w(e)
            booksKO++
            trace(
                Log.ERROR,
                STEP_2_BOOK_FOLDERS,
                log,
                "Import book ERROR : %s for Folder %s",
                e.message!!,
                bookFolder.uri.toString()
            )
        }
        val bookName = bookFolder.name ?: ""
        notificationManager.notify(
            ImportProgressNotification(
                bookName,
                booksOK + booksKO,
                bookFolders.size - nbFolders
            )
        )
        eventProgress(
            STEP_3_BOOKS,
            bookFolders.size - nbFolders,
            booksOK,
            booksKO
        )
    }

    private fun hasSameUrl(i1: ImageFile, url: String): Boolean {
        return if (i1.pageUrl.isEmpty()) i1.url == url else i1.pageUrl == url
    }

    private fun buildLogInfo(
        cleanup: Boolean,
        location: StorageLocation,
        log: List<LogEntry>
    ): LogInfo {
        val logInfo = LogInfo((if (cleanup) "cleanup_log_" else "import_log_") + location.name)
        logInfo.setHeaderName(if (cleanup) "Cleanup" else "Import")
        logInfo.setNoDataMessage("No content detected.")
        logInfo.setEntries(log)
        return logInfo
    }

    private fun renameFolder(
        context: Context,
        folder: DocumentFile,
        content: Content,
        explorer: FileExplorer,
        newName: String
    ): Boolean {
        try {
            if (folder.renameTo(newName)) {
                // 1- Update the book folder's URI
                content.setStorageDoc(folder)
                // 2- Update the JSON's URI
                val jsonFile = explorer.findFile(context, folder, JSON_FILE_NAME_V2)
                if (jsonFile != null) content.jsonUri = jsonFile.uri.toString()
                // 3- Update the image's URIs -> will be done by the next block back in startImport
                return true
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
        return false
    }

    private fun renumberPages(
        context: Context,
        content: Content,
        contentImages: List<ImageFile>,
        log: MutableList<LogEntry>
    ) {
        var naturalOrder = 0
        var nbRenumbered = 0
        val orderedImages = contentImages.sortedBy { obj: ImageFile -> obj.order }
            .filter { obj: ImageFile -> obj.isReadable }
        val nbMaxDigits = (floor(log10(orderedImages.size.toDouble())) + 1).toInt()
        for (img in orderedImages) {
            naturalOrder++
            if (img.order != naturalOrder) {
                nbRenumbered++
                img.order = naturalOrder
                img.computeName(nbMaxDigits)
                val file = getDocumentFromTreeUriString(context, img.fileUri)
                if (file != null) {
                    val extension = getExtension(file.name ?: "")
                    file.renameTo(img.name + "." + extension)
                    img.fileUri = file.uri.toString()
                }
            }
            if (nbRenumbered > 0) EventBus.getDefault().post(
                ProcessEvent(
                    ProcessEvent.Type.PROGRESS,
                    R.id.import_primary_pages,
                    STEP_3_PAGES,
                    "Page $naturalOrder",
                    naturalOrder,
                    0,
                    orderedImages.size
                )
            )
        }
        if (nbRenumbered > 0) {
            EventBus.getDefault().postSticky(
                ProcessEvent(
                    ProcessEvent.Type.COMPLETE,
                    R.id.import_primary_pages,
                    STEP_3_PAGES,
                    orderedImages.size,
                    0,
                    orderedImages.size
                )
            )
            trace(
                Log.INFO,
                STEP_3_PAGES,
                log,
                "Renumbered %d pages",
                nbRenumbered
            )
            content.setImageFiles(contentImages)
            persistJson(context, content)
        }
    }

    private fun importQueue(
        context: Context,
        queueFile: DocumentFile,
        dao: CollectionDAO,
        log: MutableList<LogEntry>
    ) {
        trace(Log.INFO, STEP_4_QUEUE_FINAL, log, "Queue JSON found")
        eventProgress(STEP_4_QUEUE_FINAL, -1, 0, 0)
        val contentCollection = deserialiseCollectionJson(context, queueFile)
        if (null != contentCollection) {
            var queueSize = dao.countAllQueueBooks().toInt()
            val queuedContent = contentCollection.getEntityQueue(dao)
            eventProgress(STEP_4_QUEUE_FINAL, queuedContent.size, 0, 0)
            trace(
                Log.INFO,
                STEP_4_QUEUE_FINAL,
                log,
                "Queue JSON deserialized : %s books detected",
                queuedContent.size.toString() + ""
            )
            val lst: MutableList<QueueRecord> = ArrayList()
            var count = 1
            for (c in queuedContent) {
                val duplicate = dao.selectContentByUrlOrCover(c.site, c.url, "")
                if (null == duplicate) {
                    if (c.status == StatusContent.ERROR) {
                        // Add error books as library entries, not queue entries
                        c.computeSize()
                        addContent(context, dao, c)
                    } else {
                        // Only add at the end of the queue if it isn't a duplicate
                        val newContentId = addContent(context, dao, c)
                        lst.add(QueueRecord(newContentId, queueSize++))
                    }
                }
                eventProgress(
                    STEP_4_QUEUE_FINAL,
                    queuedContent.size,
                    count++,
                    0
                )
            }
            dao.updateQueue(lst)
            trace(Log.INFO, STEP_4_QUEUE_FINAL, log, "Import queue succeeded")
        } else {
            trace(
                Log.INFO,
                STEP_4_QUEUE_FINAL,
                log,
                "Import queue failed : JSON unreadable"
            )
        }
    }

    private fun importGroups(
        context: Context,
        groupsFile: DocumentFile,
        dao: CollectionDAO,
        log: MutableList<LogEntry>
    ) {
        trace(Log.INFO, STEP_GROUPS, log, "Groups JSON found")
        eventProgress(STEP_GROUPS, -1, 0, 0)
        val contentCollection = deserialiseCollectionJson(context, groupsFile)
        if (null != contentCollection) {
            trace(Log.INFO, STEP_GROUPS, log, "Groups JSON deserialized")
            importCustomGroups(contentCollection, dao, log)
            importEditedGroups(contentCollection, Grouping.ARTIST, dao, log)
            importEditedGroups(contentCollection, Grouping.DL_DATE, dao, log)
        } else {
            trace(
                Log.INFO,
                STEP_GROUPS,
                log,
                "Import groups failed : Groups JSON unreadable"
            )
        }
    }

    private fun importCustomGroups(
        contentCollection: JsonContentCollection,
        dao: CollectionDAO,
        log: MutableList<LogEntry>
    ) {
        val groups = contentCollection.getEntityGroups(Grouping.CUSTOM)
        eventProgress(STEP_GROUPS, groups.size, 0, 0)
        trace(
            Log.INFO,
            STEP_GROUPS,
            log,
            "%s custom groups detected",
            groups.size.toString()
        )
        groups.forEachIndexed { index, g ->
            val existing = dao.selectGroupByName(Grouping.CUSTOM.id, g.name)
            if (null == existing) { // Create brand new
                dao.insertGroup(g)
                trace(Log.INFO, STEP_GROUPS, log, "Import OK : %s", g.name)
            } else { // Unflag existing
                existing.isFlaggedForDeletion = false
                dao.insertGroup(existing)
                trace(
                    Log.INFO,
                    STEP_GROUPS,
                    log,
                    "Import KO (existing) : %s",
                    existing.name
                )
            }
            eventProgress(STEP_GROUPS, groups.size, index + 1, 0)
        }
        trace(Log.INFO, STEP_GROUPS, log, "Import custom groups succeeded")
    }

    private fun importEditedGroups(
        contentCollection: JsonContentCollection,
        grouping: Grouping,
        dao: CollectionDAO,
        log: MutableList<LogEntry>
    ) {
        val editedGroups = contentCollection.getEntityGroups(grouping)
        trace(
            Log.INFO,
            STEP_GROUPS,
            log,
            "%d edited %s groups detected",
            editedGroups.size,
            grouping.displayName
        )
        for (g in editedGroups) {
            // Only add if it isn't a duplicate
            val duplicate = dao.selectGroupByName(grouping.id, g.name)
            if (null == duplicate) dao.insertGroup(g) else { // If it is, copy attributes
                duplicate.favourite = g.favourite
                duplicate.rating = g.rating
                dao.insertGroup(duplicate)
            }
        }
        trace(
            Log.INFO,
            STEP_GROUPS,
            log,
            "Import edited %s groups succeeded",
            grouping.displayName
        )
    }

    private fun importBookmarks(
        context: Context,
        bookmarksFile: DocumentFile,
        dao: CollectionDAO,
        log: MutableList<LogEntry>
    ) {
        trace(Log.INFO, STEP_4_QUEUE_FINAL, log, "Bookmarks JSON found")
        eventProgress(STEP_4_QUEUE_FINAL, -1, 0, 0)
        val contentCollection = deserialiseCollectionJson(context, bookmarksFile)
        if (null != contentCollection) {
            val bookmarks = contentCollection.getEntityBookmarks()
            eventProgress(STEP_4_QUEUE_FINAL, bookmarks.size, 0, 0)
            trace(
                Log.INFO,
                STEP_4_QUEUE_FINAL,
                log,
                "Bookmarks JSON deserialized : %s items detected",
                bookmarks.size.toString() + ""
            )
            importBookmarks(dao, bookmarks)
            trace(
                Log.INFO,
                STEP_4_QUEUE_FINAL,
                log,
                "Import bookmarks succeeded"
            )
        } else {
            trace(
                Log.INFO,
                STEP_4_QUEUE_FINAL,
                log,
                "Import bookmarks failed : JSON unreadable"
            )
        }
    }

    private fun importRenamingRules(
        context: Context,
        rulesFile: DocumentFile,
        dao: CollectionDAO,
        log: MutableList<LogEntry>
    ) {
        trace(Log.INFO, STEP_4_QUEUE_FINAL, log, "Renaming rules JSON found")
        eventProgress(STEP_4_QUEUE_FINAL, -1, 0, 0)
        val contentCollection = deserialiseCollectionJson(context, rulesFile)
        if (null != contentCollection) {
            val rules = contentCollection.getEntityRenamingRules()
            eventProgress(STEP_4_QUEUE_FINAL, rules.size, 0, 0)
            trace(
                Log.INFO,
                STEP_4_QUEUE_FINAL,
                log,
                "Renaming rules JSON deserialized : %s items detected",
                rules.size.toString() + ""
            )
            importRenamingRules(dao, rules)
            trace(
                Log.INFO,
                STEP_4_QUEUE_FINAL,
                log,
                "Import renaming rules succeeded"
            )
        } else {
            trace(
                Log.INFO,
                STEP_4_QUEUE_FINAL,
                log,
                "Import renaming rules failed : JSON unreadable"
            )
        }
    }

    private fun deserialiseCollectionJson(
        context: Context,
        jsonFile: DocumentFile
    ): JsonContentCollection? {
        try {
            return jsonToObject(context, jsonFile, JsonContentCollection::class.java)
        } catch (e: IOException) {
            Timber.w(e)
        } catch (e: JsonDataException) {
            Timber.w(e)
        }
        return null
    }

    @Throws(ParseException::class)
    private fun importJson(
        context: Context,
        folder: DocumentFile,
        bookFiles: List<DocumentFile>,
        dao: CollectionDAO
    ): Content? {
        var file = bookFiles.firstOrNull { f ->
            (f.name ?: "") == JSON_FILE_NAME_V2
        }
        if (file != null) return importJsonV2(context, file, folder, dao)
        file = bookFiles.firstOrNull { f -> (f.name ?: "") == JSON_FILE_NAME }
        if (file != null) return importJsonV1(context, file, folder)
        file = bookFiles.firstOrNull { f -> (f.name ?: "") == JSON_FILE_NAME_OLD }
        return if (file != null) importJsonLegacy(context, file, folder) else null
    }

    @Suppress("deprecation")
    private fun from(urlBuilders: List<URLBuilder>?, site: Site): List<Attribute>? {
        var attributes: MutableList<Attribute>? = null
        if (urlBuilders == null) {
            return null
        }
        if (urlBuilders.isNotEmpty()) {
            attributes = ArrayList()
            for (urlBuilder in urlBuilders) {
                val attribute = from(urlBuilder, AttributeType.TAG, site)
                if (attribute != null) {
                    attributes.add(attribute)
                }
            }
        }
        return attributes
    }

    @Suppress("deprecation")
    private fun from(urlBuilder: URLBuilder?, type: AttributeType, site: Site): Attribute? {
        return if (urlBuilder == null) {
            null
        } else try {
            if (urlBuilder.description == null) {
                throw ParseException("Problems loading attribute v2.")
            }
            Attribute(type, urlBuilder.description, urlBuilder.getId(), site)
        } catch (e: Exception) {
            Timber.e(e, "Parsing URL to attribute")
            null
        }
    }

    @CheckResult
    @Suppress("deprecation")
    @Throws(ParseException::class)
    private fun importJsonLegacy(
        context: Context,
        json: DocumentFile,
        parentFolder: DocumentFile
    ): Content {
        try {
            jsonToObject(context, json, DoujinBuilder::class.java)?.let { doujinBuilder ->
                val content = ContentV1()
                content.setUrl(doujinBuilder.getId())
                content.htmlDescription = doujinBuilder.description
                content.title = doujinBuilder.title
                content.setSeries(
                    from(
                        doujinBuilder.series,
                        AttributeType.SERIE, content.getSite()
                    )
                )
                val artist = from(
                    doujinBuilder.artist,
                    AttributeType.ARTIST, content.getSite()
                )
                var artists: MutableList<Attribute?>? = null
                if (artist != null) {
                    artists = ArrayList(1)
                    artists.add(artist)
                }
                content.setArtists(artists)
                content.setCoverImageUrl(doujinBuilder.urlImageTitle)
                content.setQtyPages(doujinBuilder.qtyPages)
                val translator = from(
                    doujinBuilder.translator,
                    AttributeType.TRANSLATOR, content.getSite()
                )
                var translators: MutableList<Attribute?>? = null
                if (translator != null) {
                    translators = ArrayList(1)
                    translators.add(translator)
                }
                content.setTranslators(translators)
                content.setTags(from(doujinBuilder.lstTags, content.getSite()))
                content.setLanguage(
                    from(
                        doujinBuilder.language,
                        AttributeType.LANGUAGE,
                        content.getSite()
                    )
                )
                content.setMigratedStatus()
                content.setDownloadDate(Instant.now().toEpochMilli())
                val contentV2 = content.toV2Content()
                contentV2.setStorageDoc(parentFolder)
                val newJson = jsonToFile(
                    context, JsonContent(contentV2),
                    JsonContent::class.java, parentFolder, JSON_FILE_NAME_V2
                )
                contentV2.jsonUri = newJson.uri.toString()
                return contentV2
            } ?: throw ParseException("Error reading JSON (old) file")
        } catch (e: IOException) {
            Timber.e(e, "Error reading JSON (old) file")
            throw ParseException("Error reading JSON (old) file : " + e.message)
        } catch (e: JsonDataException) {
            Timber.e(e, "Error reading JSON (old) file")
            throw ParseException("Error reading JSON (old) file : " + e.message)
        }
    }

    @CheckResult
    @Suppress("deprecation")
    @Throws(ParseException::class)
    private fun importJsonV1(
        context: Context,
        json: DocumentFile,
        parentFolder: DocumentFile
    ): Content {
        try {
            jsonToObject(context, json, ContentV1::class.java)?.let { content ->
                if (content.status != StatusContent.DOWNLOADED
                    && content.status != StatusContent.ERROR
                ) {
                    content.setMigratedStatus()
                }
                val contentV2 = content.toV2Content()
                contentV2.setStorageDoc(parentFolder)
                val newJson = jsonToFile(
                    context, JsonContent(contentV2),
                    JsonContent::class.java, parentFolder, JSON_FILE_NAME_V2
                )
                contentV2.jsonUri = newJson.uri.toString()
                return contentV2
            } ?: throw ParseException("Error reading JSON (v1) file")
        } catch (e: IOException) {
            Timber.e(e, "Error reading JSON (v1) file")
            throw ParseException("Error reading JSON (v1) file : " + e.message)
        } catch (e: JsonDataException) {
            Timber.e(e, "Error reading JSON (v1) file")
            throw ParseException("Error reading JSON (v1) file : " + e.message)
        }
    }

    @CheckResult
    @Throws(ParseException::class)
    private fun importJsonV2(
        context: Context,
        json: DocumentFile,
        parentFolder: DocumentFile,
        dao: CollectionDAO
    ): Content {
        try {
            jsonToObject(context, json, JsonContent::class.java)?.let { content ->
                val result = content.toEntity(dao)
                result.jsonUri = json.uri.toString()
                result.setStorageDoc(parentFolder)
                return result
            } ?: throw ParseException("Error reading JSON (v2) file")
        } catch (e: IOException) {
            Timber.e(e, "Error reading JSON (v2) file")
            throw ParseException("Error reading JSON (v2) file : " + e.message, e)
        } catch (e: JsonDataException) {
            Timber.e(e, "Error reading JSON (v2) file")
            throw ParseException("Error reading JSON (v2) file : " + e.message, e)
        }
    }
}