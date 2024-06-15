package me.devsaki.hentoid.workers

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.JSON_FILE_NAME_V2
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ErrorRecord
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.database.domains.QueueRecord
import me.devsaki.hentoid.enums.ErrorType
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.fragments.tools.MetaImportDialogFragment
import me.devsaki.hentoid.json.JsonContent
import me.devsaki.hentoid.json.JsonContentCollection
import me.devsaki.hentoid.notification.import_.ImportCompleteNotification
import me.devsaki.hentoid.notification.import_.ImportProgressNotification
import me.devsaki.hentoid.notification.import_.ImportStartNotification
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.addContent
import me.devsaki.hentoid.util.createImageListFromFolder
import me.devsaki.hentoid.util.file.findFile
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.getFileFromSingleUriString
import me.devsaki.hentoid.util.file.listFolders
import me.devsaki.hentoid.util.formatBookId
import me.devsaki.hentoid.util.importBookmarks
import me.devsaki.hentoid.util.isDownloadable
import me.devsaki.hentoid.util.isInQueue
import me.devsaki.hentoid.util.jsonToObject
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.util.updateGroupsJson
import me.devsaki.hentoid.util.updateQueueJson
import me.devsaki.hentoid.workers.data.MetadataImportData
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.io.IOException
import java.time.Instant
import java.util.EnumMap
import java.util.Locale


/**
 * Service responsible for importing metadata
 */
class MetadataImportWorker(val context: Context, val params: WorkerParameters) :
    BaseWorker(context, params, R.id.metadata_import_service, "metadata-import") {

    // Variable used during the import process
    private lateinit var dao: CollectionDAO
    private var totalItems = 0
    private var nbOK = 0
    private var nbKO = 0
    private var queueSize = 0
    private val siteFoldersCache: Map<Site, List<DocumentFile>> by lazy { getSiteFolders(context) }
    private val bookFoldersCache: MutableMap<Site, List<DocumentFile>> = EnumMap(Site::class.java)

    fun isRunning(context: Context): Boolean {
        return isRunning(context, R.id.metadata_import_service)
    }

    override fun getStartNotification(): BaseNotification {
        return ImportStartNotification()
    }

    override fun onInterrupt() {
        // Nothing
    }

    override fun onClear(logFile: DocumentFile?) {
        dao.cleanup()
    }

    override fun getToWork(input: Data) {
        val data = MetadataImportData.Parser(inputData)

        startImport(
            applicationContext,
            data.jsonUri ?: "",
            data.isAdd,
            data.isImportLibrary,
            data.emptyBooksOption,
            data.isImportQueue,
            data.isImportCustomGroups,
            data.isImportBookmarks
        )
    }

    /**
     * Import books from external folder
     */
    private fun startImport(
        context: Context,
        jsonUri: String,
        add: Boolean,
        importLibrary: Boolean,
        emptyBooksOption: Int,
        importQueue: Boolean,
        importCustomGroups: Boolean,
        importBookmarks: Boolean
    ) {
        val jsonFile = getFileFromSingleUriString(context, jsonUri)
        if (null == jsonFile) {
            trace(Log.ERROR, "Couldn't find metadata JSON file at %s", jsonUri)
            return
        }
        val collection = deserialiseJson(context, jsonFile)
        if (null == collection) {
            trace(Log.ERROR, "Couldn't deserialize JSON file")
            return
        }
        dao = ObjectBoxDAO()
        if (!add) {
            if (importLibrary) dao.deleteAllInternalBooks("", false)
            if (importQueue) dao.deleteAllQueuedBooks()
            if (importCustomGroups) dao.deleteAllGroups(Grouping.CUSTOM)
            if (importBookmarks) dao.deleteAllBookmarks()
        }

        // Done in one shot
        if (importBookmarks) {
            val bookmarks = collection.bookmarks
            totalItems += bookmarks.size
            importBookmarks(dao, bookmarks)
            nbOK += bookmarks.size
        }
        val contentToImport: MutableList<JsonContent> = ArrayList()
        if (importLibrary) contentToImport.addAll(collection.jsonLibrary)
        if (importQueue) contentToImport.addAll(collection.jsonQueue)
        queueSize = dao.countAllQueueBooks().toInt()
        totalItems += contentToImport.size
        if (importCustomGroups) {
            val customGroups = collection.getGroups(Grouping.CUSTOM)
            totalItems += customGroups.size
            // Chain group import followed by content import
            runImportItems(
                context,
                customGroups,
                dao,
                true,
                emptyBooksOption
            ) {
                runImportItems(
                    context, contentToImport, dao, false, emptyBooksOption
                ) { finish() }
            }
        } else  // Run content import alone
            runImportItems(context, contentToImport, dao, false, emptyBooksOption) { finish() }
    }

    private fun runImportItems(
        context: Context,
        items: List<Any>,
        dao: CollectionDAO,
        isGroup: Boolean,
        emptyBooksOption: Int,
        onFinish: Runnable
    ) {
        for (c in items) {
            if (isStopped) break
            try {
                importItem(context, c, emptyBooksOption, dao)
                if (isGroup) updateGroupsJson(context, dao)
                nextOK(context)
            } catch (e: Exception) {
                nextKO(context, e)
            }
        }
        updateQueueJson(context, dao)
        if (!isStopped) onFinish.run()
    }

    private fun importItem(context: Context, o: Any, emptyBooksOption: Int, dao: CollectionDAO) {
        if (o is JsonContent) importContent(
            context,
            o,
            emptyBooksOption,
            dao
        ) else if (o is Group) importGroup(
            o, dao
        )
    }

    // Try to map the given imported content to an existing book in the downloads folders
    // Folder names can be formatted in many ways _but_ they always contain the book unique ID !
    private fun importContent(
        context: Context,
        jsonContent: JsonContent,
        emptyBooksOption: Int,
        dao: CollectionDAO
    ) {
        val c = jsonContent.toEntity(dao)
        val duplicate = dao.selectContentByUrlOrCover(c.site, c.url, "")
        if (duplicate != null) return
        var mappedToFiles = false
        val siteFolders = siteFoldersCache[c.site]
        if (siteFolders != null) {
            for (siteFolder in siteFolders) {
                mappedToFiles = mapFilesToContent(context, c, siteFolder)
                if (mappedToFiles) break
            }
        }

        // If no local storage found for the book, it goes in the errors queue (except if it already was in progress)
        if (!mappedToFiles) {
            // Insert queued content into the queue
            if (c.status == StatusContent.DOWNLOADING || c.status == StatusContent.PAUSED) {
                val newContentId = addContent(context, dao, c)
                val lst: MutableList<QueueRecord> = ArrayList()
                val qr = QueueRecord(newContentId, queueSize++)
                qr.isFrozen = c.isFrozen
                lst.add(qr)
                dao.updateQueue(lst)
                return
            }
            when (emptyBooksOption) {
                MetaImportDialogFragment.IMPORT_AS_STREAMED -> {
                    // Greenlighted if images exist and are available online
                    if (c.imageList.size > 0
                        && isDownloadable(c)
                    ) {
                        c.downloadMode = Content.DownloadMode.STREAM
                        c.status = StatusContent.DOWNLOADED
                        val imgs: List<ImageFile>? = c.imageFiles
                        if (imgs != null) {
                            val newImages = imgs.map { i: ImageFile ->
                                ImageFile.fromImageUrl(
                                    i.order,
                                    i.url,
                                    StatusContent.ONLINE,
                                    imgs.size
                                )
                            }
                            c.setImageFiles(newImages)
                        }
                        c.forceSize(0)
                    } else { // import as empty if content unavailable online
                        c.setImageFiles(emptyList())
                        c.clearChapters()
                        c.status = StatusContent.PLACEHOLDER
                    }
                }

                MetaImportDialogFragment.IMPORT_AS_EMPTY -> {
                    c.setImageFiles(emptyList())
                    c.clearChapters()
                    c.status = StatusContent.PLACEHOLDER
                }

                MetaImportDialogFragment.IMPORT_AS_ERROR -> {
                    if (!isInQueue(c.status)) c.status = StatusContent.ERROR
                    val errors: MutableList<ErrorRecord> = ArrayList()
                    errors.add(
                        ErrorRecord(
                            ErrorType.IMPORT,
                            "",
                            context.resources.getQuantityString(R.plurals.book, 1),
                            "No local images found when importing - Please redownload",
                            Instant.now()
                        )
                    )
                    c.setErrorLog(errors)
                }

                MetaImportDialogFragment.DONT_IMPORT -> return
                else -> return
            }
        }

        // All checks successful => create the content
        addContent(context, dao, c)
    }

    private fun mapFilesToContent(context: Context, c: Content, siteFolder: DocumentFile): Boolean {
        val bookfolders: List<DocumentFile>?
        if (bookFoldersCache.containsKey(c.site)) bookfolders = bookFoldersCache[c.site] else {
            bookfolders = listFolders(context, siteFolder)
            bookFoldersCache[c.site] = bookfolders
        }
        var filesFound = false
        if (bookfolders != null) {
            // Look for the book ID
            c.populateUniqueSiteId()
            for (f in bookfolders) if (f.name != null && f.name!!.contains(
                    formatBookId(c)
                )
            ) {
                // Cache folder Uri
                c.storageDoc = f
                // Cache JSON Uri
                val json = findFile(context, f, JSON_FILE_NAME_V2)
                if (json != null) c.jsonUri = json.uri.toString()
                // Create the images from detected files
                c.setImageFiles(createImageListFromFolder(context, f))
                filesFound = true
                break
            }
        }
        return filesFound
    }

    private fun getSiteFolders(context: Context): Map<Site, List<DocumentFile>> {
        val result: MutableMap<Site, MutableList<DocumentFile>> = EnumMap(
            Site::class.java
        )
        var storageUri = Preferences.getStorageUri(StorageLocation.PRIMARY_1)
        if (storageUri.isNotEmpty()) mapSiteFolders(context, result, storageUri)
        storageUri = Preferences.getStorageUri(StorageLocation.PRIMARY_2)
        if (storageUri.isNotEmpty()) mapSiteFolders(context, result, storageUri)
        return result
    }

    private fun mapSiteFolders(
        context: Context,
        data: MutableMap<Site, MutableList<DocumentFile>>,
        storageUri: String
    ) {
        val rootFolder = getDocumentFromTreeUriString(context, storageUri)
        if (null != rootFolder) {
            val subfolders = listFolders(context, rootFolder)
            var folderName: String
            for (f in subfolders) if (f.name != null) {
                folderName = f.name!!.lowercase(Locale.getDefault())
                for (s in Site.entries) {
                    if (folderName.equals(s.folder, ignoreCase = true)) {
                        if (data.containsKey(s)) {
                            val list = data[s]
                            list?.add(f)
                        } else {
                            data[s] = mutableListOf(f)
                        }
                        break
                    }
                }
            }
        }
    }

    private fun importGroup(group: Group, dao: CollectionDAO) {
        if (null == dao.selectGroupByName(Grouping.CUSTOM.id, group.name)) dao.insertGroup(group)
    }

    private fun deserialiseJson(
        context: Context,
        jsonFile: DocumentFile
    ): JsonContentCollection? {
        try {
            return jsonToObject(
                context, jsonFile,
                JsonContentCollection::class.java
            )
        } catch (e: IOException) {
            Timber.w(e)
        }
        return null
    }


    private fun nextOK(context: Context) {
        nbOK++
        notifyProcessProgress(context)
    }

    private fun nextKO(context: Context, e: Throwable) {
        nbKO++
        Timber.w(e)
        notifyProcessProgress(context)
    }

    private fun notifyProcessProgress(context: Context) {
        CoroutineScope(Dispatchers.Default).launch {
            withContext(Dispatchers.Main) {
                doNotifyProcessProgress(context)
            }
        }
    }

    private fun doNotifyProcessProgress(context: Context) {
        notificationManager.notify(
            ImportProgressNotification(
                context.resources.getString(R.string.importing_metadata),
                nbOK + nbKO,
                totalItems
            )
        )
        EventBus.getDefault().post(
            ProcessEvent(
                ProcessEvent.Type.PROGRESS,
                R.id.import_metadata,
                0,
                nbOK,
                nbKO,
                totalItems
            )
        )
    }

    private fun finish() {
        notificationManager.notify(ImportCompleteNotification(nbOK, nbKO))
        EventBus.getDefault().postSticky(
            ProcessEvent(
                ProcessEvent.Type.COMPLETE,
                R.id.import_metadata,
                0,
                nbOK,
                nbKO,
                totalItems
            )
        )
    }
}