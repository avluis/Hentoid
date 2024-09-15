package me.devsaki.hentoid.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import androidx.annotation.DrawableRes
import androidx.documentfile.provider.DocumentFile
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.google.firebase.crashlytics.FirebaseCrashlytics
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.ReaderActivity
import me.devsaki.hentoid.activities.ReaderActivity.ReaderActivityMulti
import me.devsaki.hentoid.activities.UnlockActivity.Companion.wrapIntent
import me.devsaki.hentoid.activities.bundles.BaseWebActivityBundle
import me.devsaki.hentoid.activities.bundles.ReaderActivityBundle
import me.devsaki.hentoid.core.EXT_THUMB_FILE_PREFIX
import me.devsaki.hentoid.core.JSON_FILE_NAME_V2
import me.devsaki.hentoid.core.QUEUE_JSON_FILE_NAME
import me.devsaki.hentoid.core.THUMB_FILE_NAME
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DownloadMode
import me.devsaki.hentoid.database.domains.DuplicateEntry
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.database.domains.GroupItem
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.database.reach
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.events.DownloadCommandEvent
import me.devsaki.hentoid.json.JsonContent
import me.devsaki.hentoid.json.JsonContentCollection
import me.devsaki.hentoid.parsers.ContentParserFactory.getContentParserClass
import me.devsaki.hentoid.parsers.ContentParserFactory.getImageListParser
import me.devsaki.hentoid.util.AchievementsManager.trigger
import me.devsaki.hentoid.util.LanguageHelper.getFlagFromLanguage
import me.devsaki.hentoid.util.Settings.libraryGridCardWidthDP
import me.devsaki.hentoid.util.download.selectDownloadLocation
import me.devsaki.hentoid.util.exception.ContentNotProcessedException
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.exception.FileNotProcessedException
import me.devsaki.hentoid.util.exception.LimitReachedException
import me.devsaki.hentoid.util.file.ArchiveEntry
import me.devsaki.hentoid.util.file.Beholder
import me.devsaki.hentoid.util.file.Beholder.registerContent
import me.devsaki.hentoid.util.file.FileExplorer
import me.devsaki.hentoid.util.file.NameFilter
import me.devsaki.hentoid.util.file.URI_ELEMENTS_SEPARATOR
import me.devsaki.hentoid.util.file.cleanFileName
import me.devsaki.hentoid.util.file.copyFile
import me.devsaki.hentoid.util.file.extractArchiveEntriesBlocking
import me.devsaki.hentoid.util.file.findFolder
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.getFileFromSingleUriString
import me.devsaki.hentoid.util.file.getFileNameWithoutExtension
import me.devsaki.hentoid.util.file.getInputStream
import me.devsaki.hentoid.util.file.getMimeTypeFromFileName
import me.devsaki.hentoid.util.file.getOrCreateCacheFolder
import me.devsaki.hentoid.util.file.getOutputStream
import me.devsaki.hentoid.util.file.legacyFileFromUri
import me.devsaki.hentoid.util.file.listFiles
import me.devsaki.hentoid.util.file.listFoldersFilter
import me.devsaki.hentoid.util.file.removeFile
import me.devsaki.hentoid.util.image.MIME_IMAGE_GENERIC
import me.devsaki.hentoid.util.image.getMimeTypeFromPictureBinary
import me.devsaki.hentoid.util.image.getScaledDownBitmap
import me.devsaki.hentoid.util.image.imageNamesFilter
import me.devsaki.hentoid.util.image.isSupportedImage
import me.devsaki.hentoid.util.network.CloudflareHelper.CloudflareProtectedException
import me.devsaki.hentoid.util.network.HEADER_COOKIE_KEY
import me.devsaki.hentoid.util.network.HEADER_REFERER_KEY
import me.devsaki.hentoid.util.network.HEADER_USER_AGENT
import me.devsaki.hentoid.util.network.WebkitPackageHelper.getWebViewAvailable
import me.devsaki.hentoid.util.network.WebkitPackageHelper.getWebViewUpdating
import me.devsaki.hentoid.util.network.fetchBodyFast
import me.devsaki.hentoid.util.network.fixUrl
import me.devsaki.hentoid.util.network.getCookies
import me.devsaki.hentoid.util.network.getExtensionFromUri
import me.devsaki.hentoid.util.network.peekCookies
import me.devsaki.hentoid.util.string_similarity.Cosine
import me.devsaki.hentoid.util.string_similarity.StringSimilarity
import me.devsaki.hentoid.workers.PurgeWorker
import me.devsaki.hentoid.workers.data.DeleteData
import net.greypanther.natsort.CaseInsensitiveSimpleNaturalComparator
import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.StringUtils
import org.greenrobot.eventbus.EventBus
import pl.droidsonroids.jspoon.Jspoon
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.URL
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max


// == Used for queue management
enum class QueuePosition(val value: Int) {
    TOP(Preferences.Constant.QUEUE_NEW_DOWNLOADS_POSITION_TOP),
    BOTTOM(Preferences.Constant.QUEUE_NEW_DOWNLOADS_POSITION_BOTTOM),
    ASK(Preferences.Constant.QUEUE_NEW_DOWNLOADS_POSITION_ASK);

    companion object {
        fun fromValue(v: Int): QueuePosition {
            return entries.firstOrNull { it.value == v } ?: ASK
        }
    }
}


// == Used for advanced search
// NB : Needs to be in sync with the dropdown lists on the advanced search screen
enum class Location(val value: Int) {
    ANY(0),
    PRIMARY(1),  // Primary library - Any location
    PRIMARY_1(2), // Primary library - Location 1
    PRIMARY_2(3), // Primary library - Location 2
    EXTERNAL(4); // External library

    companion object {
        fun fromValue(v: Int): Location {
            return entries.firstOrNull { it.value == v } ?: ANY
        }
    }
}

enum class Type(val value: Int) {
    ANY(0),
    FOLDER(1),  // Images in a folder
    STREAMED(2), // Streamed book
    ARCHIVE(3), // Archive
    PLACEHOLDER(4); // "Empty book" placeholder created my metadata import

    companion object {
        fun fromValue(v: Int): Type {
            return entries.firstOrNull { it.value == v } ?: ANY
        }
    }
}


const val KEY_DL_PARAMS_NB_CHAPTERS = "nbChapters"
const val KEY_DL_PARAMS_UGOIRA_FRAMES = "ugo_frames"

private const val UNAUTHORIZED_CHARS = "[^a-zA-Z0-9.-]"
private val libraryStatus = intArrayOf(
    StatusContent.DOWNLOADED.code,
    StatusContent.MIGRATED.code,
    StatusContent.EXTERNAL.code,
    StatusContent.PLACEHOLDER.code
)
private val queueStatus =
    intArrayOf(StatusContent.DOWNLOADING.code, StatusContent.PAUSED.code, StatusContent.ERROR.code)
private val queueTabStatus = intArrayOf(StatusContent.DOWNLOADING.code, StatusContent.PAUSED.code)

// TODO empty this cache at some point
private val fileNameMatchCache: MutableMap<String, String> = HashMap()


fun getLibraryStatuses(): IntArray {
    return libraryStatus
}

fun isInLibrary(status: StatusContent): Boolean {
    return libraryStatus.contains(status.code)
}

fun getQueueStatuses(): IntArray {
    return queueStatus
}

fun getQueueTabStatuses(): IntArray {
    return queueTabStatus
}

fun isInQueue(status: StatusContent): Boolean {
    return queueStatus.contains(status.code)
}

private fun isInQueueTab(status: StatusContent): Boolean {
    return queueTabStatus.contains(status.code)
}

fun canBeArchived(content: Content): Boolean {
    return !(content.isArchive || content.downloadMode == DownloadMode.STREAM || content.status == StatusContent.PLACEHOLDER)
}

/**
 * Open the app's web browser to view the given Content's gallery page
 *
 * @param context Context to use for the action
 * @param content Content to view
 */
fun viewContentGalleryPage(context: Context, content: Content) {
    viewContentGalleryPage(context, content, false)
}

/**
 * Open the app's web browser to view the given Content's gallery page
 *
 * @param context Context to use for the action
 * @param content Content to view
 * @param wrapPin True if the intent should be wrapped with PIN protection
 */
fun viewContentGalleryPage(context: Context, content: Content, wrapPin: Boolean) {
    if (content.site == Site.NONE) return
    if (!content.site.isVisible) return  // Support is dropped


    if (!getWebViewAvailable()) {
        if (getWebViewUpdating()) context.toast(R.string.error_updating_webview)
        else context.toast(R.string.error_missing_webview)
        return
    }

    var intent = Intent(context, Content.getWebActivityClass(content.site))
    val bundle = BaseWebActivityBundle()
    bundle.url = content.galleryUrl
    intent.putExtras(bundle.bundle)
    if (wrapPin) intent = wrapIntent(context, intent)
    context.startActivity(intent)
}

/**
 * Update the given Content's JSON file with its current values
 *
 * @param context Context to use for the action
 * @param content Content whose JSON file to update
 */
fun updateJson(context: Context, content: Content): Boolean {
    assertNonUiThread()

    getFileFromSingleUriString(context, content.jsonUri)?.let { file ->
        try {
            getOutputStream(context, file)?.use { output ->
                updateJson(
                    JsonContent(content),
                    JsonContent::class.java, output
                )
                return true
            } ?: run { Timber.w("JSON file creation failed for %s", file.uri) }
        } catch (e: IOException) {
            Timber.e(e, "Error while writing to %s", content.jsonUri)
        }
    } ?: run {
        Timber.w("%s does not refer to a valid file", content.jsonUri)
    }
    return false
}

/**
 * Create the given Content's JSON file and populate it with its current values
 *
 * @param context Context to use
 * @param content Content whose JSON file to create
 * @return Created JSON file, or null if it couldn't be created
 */
fun createJson(context: Context, content: Content): DocumentFile? {
    assertNonUiThread()
    if (content.isArchive) return null // Keep that as is, we can't find the parent folder anyway


    val folder = getDocumentFromTreeUriString(context, content.storageUri) ?: return null
    try {
        val newJson = jsonToFile(
            context, JsonContent(content),
            JsonContent::class.java, folder, JSON_FILE_NAME_V2
        )
        content.jsonUri = newJson.uri.toString()
        return newJson
    } catch (e: IOException) {
        Timber.e(e, "Error while writing to %s", content.storageUri)
    }
    return null
}

/**
 * Persist the given content's JSON file, whether it already exists or it needs to be created
 *
 * @param context Context to use
 * @param content Content to persist the JSON for
 */
fun persistJson(context: Context, content: Content) {
    var result = false
    if (content.jsonUri.isNotEmpty()) result = updateJson(context, content)
    if (!result) createJson(context, content)
}

/**
 * Update the JSON file that stores the queue with the current contents of the queue
 *
 * @param context Context to be used
 * @param dao     DAO to be used
 * @return True if the queue JSON file has been updated properly; false instead
 */
fun updateQueueJson(context: Context, dao: CollectionDAO): Boolean {
    assertNonUiThread()
    val queue = dao.selectQueue()
    val errors = dao.selectErrorContent()

    // Save current queue (to be able to restore it in case the app gets uninstalled)
    val queuedContent = queue.mapNotNull { qr ->
        val c = qr.content.target
        if (c != null) c.isFrozen = qr.frozen
        c
    }.toMutableList()
    queuedContent.addAll(errors)

    val rootFolder =
        getDocumentFromTreeUriString(context, Preferences.getStorageUri(StorageLocation.PRIMARY_1))
            ?: return false

    try {
        val contentCollection = JsonContentCollection()
        contentCollection.replaceQueue(queuedContent)

        jsonToFile(
            context,
            contentCollection,
            JsonContentCollection::class.java,
            rootFolder,
            QUEUE_JSON_FILE_NAME
        )
    } catch (e: IOException) {
        // NB : IllegalArgumentException might happen for an unknown reason on certain devices
        // even though all the file existence checks are in place
        // ("Failed to determine if primary:.Hentoid/queue.json is child of primary:.Hentoid: java.io.FileNotFoundException: Missing file for primary:.Hentoid/queue.json at /storage/emulated/0/.Hentoid/queue.json")
        Timber.e(e)
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.recordException(e)
        return false
    } catch (e: IllegalArgumentException) {
        Timber.e(e)
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.recordException(e)
        return false
    }
    return true
}

/**
 * Open the given Content in the built-in image viewer
 *
 * @param context          Context to use for the action
 * @param content          Content to view
 * @param pageNumber       Page number to view
 * @param searchParams     Current search parameters (so that the next/previous book feature
 * is faithful to the library screen's order)
 * @param forceShowGallery True to force the gallery screen to show first; false to follow app settings
 * @param newTask          True to open the reader as a new Task
 */
fun openReader(
    context: Context,
    content: Content,
    pageNumber: Int,
    searchParams: Bundle?,
    forceShowGallery: Boolean,
    newTask: Boolean
): Boolean {
    // Check if the book has at least its own folder
    if (content.storageUri.isEmpty()) return false
    if (content.status == StatusContent.PLACEHOLDER) return false

    Timber.d("Opening: %s from: %s", content.title, content.storageUri)

    val builder = ReaderActivityBundle()
    builder.contentId = content.id
    if (searchParams != null) builder.searchParams = searchParams
    if (pageNumber > -1) builder.pageNumber = pageNumber
    builder.isForceShowGallery = forceShowGallery

    val intent = Intent(
        context,
        if (newTask) ReaderActivityMulti::class.java else ReaderActivity::class.java
    )
    intent.putExtras(builder.bundle)

    if (newTask) intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    context.startActivity(intent)
    return true
}

/**
 * Update the given Content read stats in both DB and JSON file
 *
 * @param context                 Context to use for the action
 * @param dao                     DAO to use for the action
 * @param content                 Content to update
 * @param images                  Images to attach to the given Content
 * @param targetLastReadPageIndex Index of the last read page
 * @param updateReads             True to increment the reads counter and to set the last read date to now
 * @param markAsCompleted         True to mark as completed
 */
fun updateContentReadStats(
    context: Context,
    dao: CollectionDAO,
    content: Content,
    images: List<ImageFile?>,
    targetLastReadPageIndex: Int,
    updateReads: Boolean,
    markAsCompleted: Boolean
) {
    content.lastReadPageIndex = targetLastReadPageIndex
    if (updateReads) content.increaseReads().lastReadDate = Instant.now().toEpochMilli()
    if (markAsCompleted) content.completed = true
    dao.replaceImageList(content.id, images.filterNotNull())
    dao.insertContentCore(content)
    persistJson(context, content)
}

/**
 * Find the picture files for the given Content
 * NB1 : Pictures with non-supported formats are not included in the results
 * NB2 : Cover picture is not included in the results
 *
 * @param context Context to use
 * @param content Content to retrieve picture files for
 * @return List of picture files
 */
fun getPictureFilesFromContent(context: Context, content: Content): List<DocumentFile> {
    assertNonUiThread()
    val storageUri = content.storageUri

    Timber.d("Opening: %s from: %s", content.title, storageUri)
    val folder = getDocumentFromTreeUriString(context, storageUri)
    if (null == folder) {
        Timber.d("File not found!! Exiting method.")
        return ArrayList()
    }

    return listFoldersFilter(
        context, folder
    ) { displayName: String ->
        (displayName.lowercase(
            Locale.getDefault()
        ).startsWith(THUMB_FILE_NAME) && isSupportedImage(displayName))
    }
}

/**
 * Remove the given Content from the disk and the DB
 *
 * @param context Context to be used
 * @param dao     DAO to be used
 * @param content Content to be removed
 * @throws ContentNotProcessedException in case an issue prevents the content from being actually removed
 */
@Throws(ContentNotProcessedException::class)
fun removeContent(context: Context, dao: CollectionDAO, content: Content) {
    assertNonUiThread()
    // Remove from DB
    // NB : start with DB to have a LiveData feedback, because file removal can take much time
    dao.deleteContent(content)

    if (content.isArchive) { // Remove an archive
        val archive = getFileFromSingleUriString(context, content.storageUri)
            ?: throw FileNotProcessedException(
                content,
                "Failed to find archive " + content.storageUri
            )

        if (archive.delete()) {
            Timber.i("Archive removed : %s", content.storageUri)
        } else {
            throw FileNotProcessedException(
                content,
                "Failed to delete archive " + content.storageUri
            )
        }

        // Remove the cover stored in the app's persistent folder
        val appFolder = context.filesDir
        val images = appFolder.listFiles { _, name ->
            getFileNameWithoutExtension(name) == content.id.toString()
        }
        if (images != null) for (f in images) removeFile(f!!)
    } else if (content.storageUri.isNotEmpty()) { // Remove a folder and its content
        // If the book has just starting being downloaded and there are no complete pictures on memory yet, it has no storage folder => nothing to delete
        val folder = getDocumentFromTreeUriString(context, content.storageUri)
            ?: throw FileNotProcessedException(
                content,
                "Failed to find directory " + content.storageUri
            )

        if (folder.delete()) {
            Timber.i("Directory removed : %s", content.storageUri)
        } else {
            throw FileNotProcessedException(
                content,
                "Failed to delete directory " + content.storageUri
            )
        }
    }
}

/**
 * Remove the given Content
 * - from the queue
 * - from disk and the DB (optional)
 *
 * @param context       Context to be used
 * @param dao           DAO to be used
 * @param content       Content to be removed
 * @param deleteContent If true, the content itself is deleted from disk and DB
 * @throws ContentNotProcessedException in case an issue prevents the content from being actually removed
 */
@Throws(ContentNotProcessedException::class)
fun removeQueuedContent(
    context: Context,
    dao: CollectionDAO,
    content: Content,
    deleteContent: Boolean
) {
    assertNonUiThread()

    // Check if the content is on top of the queue; if so, send a CANCEL event
    if (isInQueueTab(content.status)) {
        val queue = dao.selectQueue()
        if (queue.isNotEmpty() && queue[0].content.targetId == content.id) EventBus.getDefault()
            .post(DownloadCommandEvent(DownloadCommandEvent.Type.EV_CANCEL, content))

        // Remove from queue
        dao.deleteQueue(content)
    }

    // Remove content itself
    if (deleteContent) removeContent(context, dao, content)
}

/**
 * Remove all external content from DB without removing files (=detach)
 *
 * @param context Context to use
 * @param dao     DAO to use
 */
fun detachAllExternalContent(context: Context, dao: CollectionDAO) {
    // Remove all external books from DB
    // NB : do NOT use ContentHelper.removeContent as it would remove files too
    // here we just want to remove DB entries without removing files
    dao.deleteAllExternalBooks()

    // Remove all images stored in the app's persistent folder (archive covers)
    val appFolder = context.filesDir
    val images = appFolder.listFiles { _, s: String? ->
        isSupportedImage(s ?: "")
    }
    if (images != null) for (f in images) removeFile(f!!)
}

/**
 * Remove all content from the given primary location from DB without removing files (=detach)
 *
 * @param dao      DAO to use
 * @param location Location to detach
 */
fun detachAllPrimaryContent(dao: CollectionDAO, location: StorageLocation?) {
    // Remove all external books from DB
    // NB : do NOT use ContentHelper.removeContent as it would remove files too
    // here we just want to remove DB entries without removing files
    dao.deleteAllInternalBooks(getPathRoot(location), true)

    // TODO groups
}

fun getPathRoot(location: StorageLocation?): String {
    return getPathRoot(Preferences.getStorageUri(location))
}

fun getPathRoot(locationUriStr: String): String {
    val pathDivider: Int = locationUriStr.lastIndexOf(URI_ELEMENTS_SEPARATOR)
    if (pathDivider > -1) return locationUriStr.substring(
        0,
        pathDivider + URI_ELEMENTS_SEPARATOR.length
    ) // Include separator

    return locationUriStr
}

/**
 * Add new content to the library
 *
 * @param context Context to use
 * @param dao     DAO to use
 * @param content Content to add to the library
 * @return ID of the newly added Content
 */
fun addContent(context: Context, dao: CollectionDAO, content: Content): Long {
    assertNonUiThread()
    val newContentId = dao.insertContent(content)
    content.id = newContentId

    // Perform group operations only if
    //   - the book is in the library (i.e. not queued)
    //   - the book is linked to no group from the given grouping
    if (libraryStatus.contains(content.status.code)) {
        val staticGroupings: List<Grouping> = Grouping.entries.filter(Grouping::canReorderBooks)
        for (g in staticGroupings) if (content.getGroupItems(g).isEmpty()) {
            if (g == Grouping.ARTIST) {
                var nbGroups = dao.countGroupsFor(g).toInt()
                val attrs = content.attributeMap
                val artists: MutableList<Attribute> = ArrayList()
                var sublist = attrs[AttributeType.ARTIST]
                if (sublist != null) artists.addAll(sublist)
                sublist = attrs[AttributeType.CIRCLE]
                if (sublist != null) artists.addAll(sublist)

                if (artists.isEmpty()) { // Add to the "no artist" group if no artist has been found
                    val group = getOrCreateNoArtistGroup(context, dao)
                    val item = GroupItem(content, group, -1)
                    dao.insertGroupItem(item)
                } else {
                    for (a in artists) { // Add to the artist groups attached to the artists attributes
                        var group = a.group.target
                        if (null == group) {
                            group = Group(Grouping.ARTIST, a.name, ++nbGroups)
                            group.subtype =
                                if (a.type == AttributeType.ARTIST) Preferences.Constant.ARTIST_GROUP_VISIBILITY_ARTISTS else Preferences.Constant.ARTIST_GROUP_VISIBILITY_GROUPS
                            if (!a.contents.isEmpty()) group.coverContent.target = a.contents[0]
                        }
                        addContentToAttributeGroup(group, a, content, dao)
                    }
                }
            }
        }
    }

    // Extract the cover to the app's persistent folder if the book is an archive
    if (content.isArchive && content.imageFiles != null) {
        val archive = getFileFromSingleUriString(context, content.storageUri)
        if (archive != null) {
            try {
                val targetFolder = context.filesDir
                val extractInstructions: MutableList<Pair<String, String>> = ArrayList()
                extractInstructions.add(
                    Pair(
                        content.cover.fileUri.replace(
                            content.storageUri + File.separator,
                            ""
                        ), newContentId.toString() + ""
                    )
                )
                val results = context.extractArchiveEntriesBlocking(
                    archive.uri,
                    targetFolder,
                    extractInstructions
                )
                if (results.isNotEmpty()) {
                    var uri = results[0]

                    // Save the pic as low-res JPG
                    val extractedFile = File(uri.path ?: "") // These are file URI's
                    if (extractedFile.length() > 0) {
                        try {
                            getInputStream(context, uri).use { `is` ->
                                val b = BitmapFactory.decodeStream(`is`)
                                if (b != null) {
                                    val targetFileName: String =
                                        EXT_THUMB_FILE_PREFIX + extractedFile.name
                                    // Reuse existing file if exists
                                    val finalFile: File
                                    val existingFiles =
                                        targetFolder.listFiles { _, s: String -> s == targetFileName }
                                    finalFile =
                                        if (existingFiles != null && existingFiles.isNotEmpty()) {
                                            existingFiles[0]
                                        } else { // Create new file
                                            File(targetFolder, targetFileName)
                                        }
                                    getOutputStream(finalFile).use { os ->
                                        val resizedBitmap =
                                            getScaledDownBitmap(
                                                b,
                                                dimensAsPx(
                                                    context,
                                                    libraryGridCardWidthDP
                                                ),
                                                false
                                            )
                                        resizedBitmap.compress(
                                            Bitmap.CompressFormat.JPEG,
                                            85,
                                            os
                                        )
                                        resizedBitmap.recycle()
                                    }
                                    uri = Uri.fromFile(finalFile)
                                }
                            }
                        } finally {
                            if (!extractedFile.delete()) Timber.w(
                                "Failed deleting file %s",
                                extractedFile.absolutePath
                            )
                        }
                        Timber.i(">> Set cover for %s", content.title)
                        content.cover.fileUri = uri.toString()
                        content.cover.name = uri.lastPathSegment ?: ""
                        dao.replaceImageList(newContentId, content.imageList)
                    }
                }
            } catch (e: IOException) {
                Timber.w(e)
            }
        }
    }

    return newContentId
}

/**
 * Add a new Attribute to the library master data (and updates groups accordingly)
 *
 * @param type Attribute type of the new Attribute
 * @param name Name of the new Attribute
 * @param dao  DAO to use
 * @return Newly created Attribute
 */
fun addAttribute(
    type: AttributeType,
    name: String, dao: CollectionDAO
): Attribute {
    var artistGroup: Group? = null
    if (type == AttributeType.ARTIST || type == AttributeType.CIRCLE) artistGroup =
        addArtistToAttributesGroup(name, dao)
    val attr = Attribute(type = type, name = name)
    val newId = dao.insertAttribute(attr)
    attr.id = newId
    if (artistGroup != null) attr.putGroup(artistGroup)
    return attr
}

/**
 * Remove the given pages from the disk and the DB
 *
 * @param images  Pages to be removed
 * @param dao     DAO to be used
 * @param context Context to be used
 */
fun removePages(images: List<ImageFile>, dao: CollectionDAO, context: Context) {
    assertNonUiThread()
    // Remove from DB
    // NB : start with DB to have a LiveData feedback, because file removal can take much time
    dao.deleteImageFiles(images)

    // Remove the pages from disk
    for (image in images) removeFile(context, Uri.parse(image.fileUri))

    // Lists all relevant content
    val contents = images.map { it.content.targetId }.distinct()

    // Update content JSON if it exists (i.e. if book is not queued)
    for (contentId in contents) {
        val content = dao.selectContent(contentId)
        if (content != null && content.jsonUri.isNotEmpty()) updateJson(context, content)
    }
}

/**
 * Set one of the given Content's ImageFile as the Content's cover and persist that new setting to the DB
 *
 * @param newCover ImageFile to be used as a cover for the Content it is related to
 * @param dao      DAO to be used
 * @param context  Context to be used
 */
fun setAndSaveContentCover(newCover: ImageFile, dao: CollectionDAO, context: Context) {
    assertNonUiThread()

    // Get all images from the DB
    val content = dao.selectContent(newCover.content.targetId) ?: return
    val images = content.imageFiles ?: return

    // Remove current cover from the set
    setContentCover(content, images, newCover)

    // Update images directly
    dao.insertImageFiles(images)

    // Update the whole list
    dao.insertContent(content)

    // Update content JSON if it exists (i.e. if book is not queued)
    if (content.jsonUri.isNotEmpty()) updateJson(context, content)
}

/**
 * Set one of the given Content's ImageFile as the Content's cover
 * NB : That method doesn't persist the result state to the DB
 *
 * @param content  Content to set the new cover for
 * @param images   Images of the given Content
 * @param newCover ImageFile to be used as a cover for the Content it is related to
 */
fun setContentCover(content: Content, images: MutableList<ImageFile>, newCover: ImageFile) {
    // Remove current cover from the set
    for (i in images.indices) if (images[i].isCover) {
        if (images[i].isReadable) images[i].isCover = false
        else images.removeAt(i)
        break
    }

    // Duplicate given picture and set it as a cover
    val cover = ImageFile.newCover(newCover.url, newCover.status)
    cover.fileUri = newCover.fileUri
    cover.mimeType = newCover.mimeType
    images.add(0, cover)

    // Update cover URL to "ping" the content to be updated too (useful for library screen that only detects "direct" content updates)
    content.coverImageUrl = newCover.url
}

/**
 * Create the download directory of the given content
 *
 * @param context    Context to use
 * @param content    Content for which the directory to create
 * @param createOnly Set to true to exclusively create a new folder; set to false if one can reuse an existing folder
 * @return Created or existing directory
 */
fun getOrCreateContentDownloadDir(
    context: Context,
    content: Content,
    location: StorageLocation,
    createOnly: Boolean
): DocumentFile? {
    // == Site folder
    val siteDownloadDir = getOrCreateSiteDownloadDir(context, location, content.site) ?: return null

    // == Book folder
    val bookFolderName = formatBookFolderName(content)

    // First try finding the folder with new naming...
    if (!createOnly) {
        var bookFolder = findFolder(context, siteDownloadDir, bookFolderName.first)
        if (null == bookFolder) { // ...then with old (sanitized) naming
            bookFolder = findFolder(context, siteDownloadDir, bookFolderName.second)
        }
        if (bookFolder != null) return bookFolder
    }

    // If nothing found, or create-only, create a new folder with the new naming...
    val result = siteDownloadDir.createDirectory(bookFolderName.first)
    return result ?: // ...if it fails, create a new folder with the old naming
    siteDownloadDir.createDirectory(bookFolderName.second)
}

/**
 * Format the download directory path of the given content according to current user preferences
 *
 * @param content Content to get the path from
 * @return Pair containing the canonical naming of the given content :
 * - Left side : Naming convention allowing non-ANSI characters
 * - Right side : Old naming convention with ANSI characters alone
 */
fun formatBookFolderName(
    content: Content
): Pair<String, String> {
    var title = content.title
    title = if ((null == title)) "" else title
    val author = formatBookAuthor(content).lowercase(Locale.getDefault())

    return Pair(
        formatBookFolderName(content, cleanFileName(title), cleanFileName(author)),
        formatBookFolderName(
            content,
            title.replace(UNAUTHORIZED_CHARS.toRegex(), "_"),
            author.replace(UNAUTHORIZED_CHARS.toRegex(), "_")
        )
    )
}

private fun formatBookFolderName(
    content: Content,
    title: String, author: String
): String {
    var result = ""
    when (Preferences.getFolderNameFormat()) {
        Preferences.Constant.FOLDER_NAMING_CONTENT_TITLE_ID -> result += title
        Preferences.Constant.FOLDER_NAMING_CONTENT_AUTH_TITLE_ID -> result += "$author - $title"
        Preferences.Constant.FOLDER_NAMING_CONTENT_TITLE_AUTH_ID -> result += "$title - $author"
        else -> {}
    }
    result += " - "

    // Unique content ID
    val suffix = formatBookId(content)

    // Truncate folder dir to something manageable for Windows
    // If we are to assume NTFS and Windows, then the fully qualified file, with it's drivename, path, filename, and extension, altogether is limited to 260 characters.
    val truncLength = Preferences.getFolderTruncationNbChars()
    val titleLength = result.length
    if (truncLength > 0 && titleLength + suffix.length > truncLength) result =
        result.substring(0, truncLength - suffix.length - 1)

    // We always add the unique ID at the end of the folder name to avoid collisions between two books with the same title from the same source
    // (e.g. different scans, different languages)
    result += suffix

    return result
}

/**
 * Format the Content ID for folder naming purposes
 *
 * @param content Content whose ID to format
 * @return Formatted Content ID
 */
// Math.abs is used for formatting purposes only
fun formatBookId(content: Content): String {
    content.populateUniqueSiteId()
    var id = content.uniqueSiteId
    // For certain sources (8muses, fakku), unique IDs are strings that may be very long
    // => shorten them by using their hashCode
    if (id.length > 10) id = formatIntAsStr(abs(id.hashCode().toDouble()).toInt(), 10)
    return "[$id]"
}

/**
 * Format the given Content's artists and circles to form the "author" string
 *
 * @param content Content to use
 * @return Resulting author string
 */
fun formatBookAuthor(content: Content): String {
    var result = ""
    val attrMap = content.attributeMap
    // Try and get first Artist
    val artistAttributes = attrMap[AttributeType.ARTIST]
    if (!artistAttributes.isNullOrEmpty()) {
        val attr = artistAttributes.firstOrNull()
        if (attr != null) result = attr.name
    }

    // If no Artist found, try and get first Circle
    if (result.isEmpty()) {
        val circleAttributes = attrMap[AttributeType.CIRCLE]
        if (!circleAttributes.isNullOrEmpty()) {
            val attr = circleAttributes.firstOrNull()
            if (attr != null) result = attr.name
        }
    }

    return result
}

/**
 * Return the given site's download directory. Create it if it doesn't exist.
 *
 *
 * Avoid overloading the Android folder structure (not designed for that :/)
 * by preventing any site folder from storing more than 250 books/subfolders
 * => create a new "siteN" folder when needed (e.g. nhentai1, nhentai2, nhentai3...)
 *
 * @param context  Context to use for the action
 * @param location Location to get/create the folder in
 * @param site     Site to get the download directory for
 * @return Download directory of the given Site
 */
fun getOrCreateSiteDownloadDir(
    context: Context,
    location: StorageLocation,
    site: Site
): DocumentFile? {
    val appUriStr = Preferences.getStorageUri(location)
    if (appUriStr.isEmpty()) {
        Timber.e("No storage URI defined for location %s", location.name)
        return null
    }
    val appFolder = getDocumentFromTreeUriString(context, appUriStr)
    if (null == appFolder) {
        Timber.e("App folder %s does not exist", appUriStr)
        return null
    }

    try {
        FileExplorer(context, appFolder).use { explorer ->
            val siteFolderName = site.folder
            var siteFolders =
                explorer.listDocumentFiles(
                    context, appFolder,
                    { displayName: String ->
                        displayName.startsWith(
                            siteFolderName
                        )
                    }, listFolders = true, listFiles = false, stopFirst = false
                )
            // Order by name (nhentai, nhentai1, ..., nhentai10)
            siteFolders = siteFolders.sortedWith(InnerNameNumberFileComparator())
            if (siteFolders.isEmpty()) // Create
                return appFolder.createDirectory(siteFolderName)
            else {
                // Check number of subfolders
                for (siteFolder in siteFolders) {
                    val nbSubfolders = explorer.countFolders(siteFolder) { _ -> true }
                    if (nbSubfolders < 250) return siteFolder
                }

                // Create new one with the next number (taken from the name of the last folder itself, to handle cases where numbering is not contiguous)
                var newDigits = siteFolders.size
                val lastDigits = keepDigits(
                    (siteFolders[siteFolders.size - 1].name ?: "").lowercase(Locale.getDefault())
                        .replace(site.folder.lowercase(Locale.getDefault()), "")
                )
                if (lastDigits.isNotEmpty()) newDigits = lastDigits.toInt() + 1
                return appFolder.createDirectory(siteFolderName + newDigits)
            }
        }
    } catch (e: IOException) {
        Timber.w(e)
    }
    return null
}

/**
 * Open the "share with..." Android dialog for the given list of Content
 *
 * @param context Context to use for the action
 * @param items   List of Content to share
 */
fun shareContent(
    context: Context,
    items: List<Content>
) {
    if (items.isEmpty()) return

    val subject = if ((1 == items.size)) items[0].title else ""
    val text = StringUtils.join(items.map { it.galleryUrl }, System.lineSeparator())

    shareText(context, subject, text)
}

/**
 * Parse the given download parameters string into a map of strings
 *
 * @param downloadParamsStr String representation of the download parameters to parse
 * @return Map of strings describing the given download parameters
 */
fun parseDownloadParams(downloadParamsStr: String?): Map<String, String> {
    // Handle empty and {}
    if (null == downloadParamsStr || downloadParamsStr.trim().length <= 2) return HashMap()
    try {
        return jsonToObject(downloadParamsStr, MAP_STRINGS)!!
    } catch (e: IOException) {
        Timber.w(e)
    }
    return HashMap()
}


/**
 * Remove the leading zeroes and the file extension of the given string
 *
 * @param s String to be cleaned
 * @return Input string, without leading zeroes and extension
 */
private fun removeLeadingZeroesAndExtension(s: String?): String {
    if (null == s) return ""

    var beginIndex = 0
    if (s.startsWith("0")) beginIndex = -1

    for (i in s.indices) {
        if ('.' == s[i]) return if ((-1 == beginIndex)) "0" else s.substring(beginIndex, i)
        if (-1 == beginIndex && s[i] != '0') beginIndex = i
    }

    return if ((-1 == beginIndex)) "0" else s.substring(beginIndex)
}

/**
 * Remove the leading zeroes and the file extension of the given string using cached results
 *
 * @param s String to be cleaned
 * @return Input string, without leading zeroes and extension
 */
private fun removeLeadingZeroesAndExtensionCached(s: String): String {
    var result = fileNameMatchCache[s]
    if (null == result) {
        result = removeLeadingZeroesAndExtension(s)
        fileNameMatchCache[s] = result
    }
    return result
}

/**
 * Matches the given files to the given ImageFiles according to their name (without leading zeroes nor file extension)
 *
 * @param files  Files to be matched to the given ImageFiles
 * @param images ImageFiles to be matched to the given files
 * @return List of matched ImageFiles, with the Uri of the matching file
 */
fun matchFilesToImageList(
    files: List<DocumentFile>, images: List<ImageFile>
): List<ImageFile> {
    val fileNameProperties: MutableMap<String?, Pair<String, Long>> = HashMap(files.size)
    val result: MutableList<ImageFile> = ArrayList()
    var coverFound = false

    // Put file names into a Map to speed up the lookup
    for (file in files) if (file.name != null) fileNameProperties[removeLeadingZeroesAndExtensionCached(
        file.name!!
    )] = Pair(file.uri.toString(), file.length())

    // Look up similar names between images and file names
    var order: Int
    var previousOrder = -1
    val orderedImages = images.sortedBy { it.order }.toList()
    for (i in orderedImages.indices) {
        val img = orderedImages[i]
        val imgName = removeLeadingZeroesAndExtensionCached(img.name)

        var property: Pair<String, Long>?
        val isOnline = img.status == StatusContent.ONLINE
        if (isOnline) {
            property = Pair("", 0L)
        } else {
            // Detect gaps inside image numbering
            order = img.order
            // Look for files named with the forgotten number
            if (previousOrder > -1 && previousOrder < order - 1) {
                Timber.i("Numbering gap detected : %d to %d", previousOrder, order)
                for (j in previousOrder + 1 until order) {
                    val localProperty = fileNameProperties[j.toString() + ""]
                    if (localProperty != null) {
                        Timber.i("Numbering gap filled with a file : %d", j)
                        val newImage = ImageFile.fromImageUrl(
                            j,
                            orderedImages[i - 1].url,
                            StatusContent.DOWNLOADED,
                            orderedImages.size
                        )
                        newImage.fileUri = localProperty.first
                        newImage.size = localProperty.second
                        result.add(max(0.0, (result.size - 1).toDouble()).toInt(), newImage)
                    }
                }
            }
            previousOrder = order

            property = fileNameProperties[imgName]
        }
        if (property != null) {
            if (imgName.startsWith(THUMB_FILE_NAME)) {
                coverFound = true
                img.isCover = true
            }
            img.fileUri = property.first
            img.size = property.second
            img.status = if (isOnline) StatusContent.ONLINE else StatusContent.DOWNLOADED
            result.add(img)
        } else Timber.i(">> image not found among files : %s", imgName)
    }

    // If no thumb found, set the 1st image as cover
    if (!coverFound && result.isNotEmpty()) result[0].isCover = true
    return result
}

/**
 * Create the list of ImageFiles from the given folder
 *
 * @param context Context to be used
 * @param folder  Folder to read the images from
 * @return List of ImageFiles corresponding to all supported pictures inside the given folder, sorted numerically then alphabetically
 */
fun createImageListFromFolder(
    context: Context,
    folder: DocumentFile
): List<ImageFile> {
    val imageFiles = listFiles(context, folder, imageNamesFilter)
    return if (imageFiles.isNotEmpty()) createImageListFromFiles(imageFiles)
    else emptyList()
}

/**
 * Create the list of ImageFiles from the given files
 *
 * @param files Files to find images into
 * @return List of ImageFiles corresponding to all supported pictures among the given files, sorted numerically then alphabetically
 */
fun createImageListFromFiles(
    files: List<DocumentFile>
): List<ImageFile> {
    return createImageListFromFiles(files, StatusContent.DOWNLOADED, 0, "")
}

/**
 * Create the list of ImageFiles from the given files
 *
 * @param files         Files to find images into
 * @param targetStatus  Target status of the ImageFiles to create
 * @param startingOrder Starting order of the ImageFiles to create
 * @param namePrefix    Prefix to add in front of the name of the ImageFiles to create
 * @return List of ImageFiles corresponding to all supported pictures among the given files, sorted numerically then alphabetically
 */
fun createImageListFromFiles(
    files: List<DocumentFile>, targetStatus: StatusContent,
    startingOrder: Int, namePrefix: String
): List<ImageFile> {
    assertNonUiThread()
    val result: MutableList<ImageFile> = ArrayList()
    var order = startingOrder
    var coverFound = false
    // Sort files by anything that resembles a number inside their names
    val fileList = files.sortedWith(InnerNameNumberFileComparator())
    for (f in fileList) {
        val name = namePrefix + (f.name ?: "")
        val img = ImageFile()
        if (name.startsWith(THUMB_FILE_NAME)) {
            coverFound = true
            img.isCover = true
        } else order++
        img.name = getFileNameWithoutExtension(name)
        img.order = order
        img.url = f.uri.toString()
        img.status = targetStatus
        img.fileUri = f.uri.toString()
        img.size = f.length()
        img.mimeType = getMimeTypeFromFileName(name)
        result.add(img)
    }
    // If no thumb found, set the 1st image as cover
    if (!coverFound && result.isNotEmpty()) result[0].isCover = true
    return result
}

/**
 * Create a list of ImageFiles from the given archive entries
 *
 * @param archiveFileUri Uri of the archive file the entries have been read from
 * @param files          Entries to create the ImageFile list with
 * @param targetStatus   Target status of the ImageFiles
 * @param startingOrder  Starting order of the first ImageFile to add; will be numbered incrementally from that number on
 * @param namePrefix     Prefix to add to image names
 * @return List of ImageFiles contructed from the given parameters
 */
fun createImageListFromArchiveEntries(
    archiveFileUri: Uri, files: List<ArchiveEntry>,
    targetStatus: StatusContent, startingOrder: Int,
    namePrefix: String
): List<ImageFile> {
    assertNonUiThread()
    val result: MutableList<ImageFile> = ArrayList()
    var order = startingOrder
    // Sort files by anything that resembles a number inside their names (default entry order from ZipInputStream is chaotic)
    val fileList = files.sortedWith(InnerNameNumberArchiveComparator())
    for ((path1, size) in fileList) {
        val name = namePrefix + path1
        val path = archiveFileUri.toString() + File.separator + path1
        val img = ImageFile()
        if (name.startsWith(THUMB_FILE_NAME)) img.isCover = true
        else order++
        img.name = getFileNameWithoutExtension(name)
        img.order = order
        img.url = path
        img.status = targetStatus
        img.fileUri = path
        img.size = size
        img.mimeType = getMimeTypeFromFileName(name)
        result.add(img)
    }
    return result
}

/**
 * Launch the web browser for the given site and URL
 *
 * @param context   Context to be used
 * @param targetUrl Url to navigate to
 */
fun launchBrowserFor(
    context: Context,
    targetUrl: String
) {
    if (!getWebViewAvailable()) {
        if (getWebViewUpdating()) context.toast(R.string.error_updating_webview)
        else context.toast(R.string.error_missing_webview)
        return
    }
    val targetSite = Site.searchByUrl(targetUrl)
    if (null == targetSite || targetSite == Site.NONE) return

    val intent = Intent(context, Content.getWebActivityClass(targetSite))

    val bundle = BaseWebActivityBundle()
    bundle.url = targetUrl
    intent.putExtras(bundle.bundle)

    context.startActivity(intent)
}

/**
 * Get the blocked tags of the given Content
 * NB : Blocked tags are detected according to the current app Preferences
 *
 * @param content Content to extract blocked tags from
 * @return List of blocked tags from the given Content
 */
fun getBlockedTags(content: Content): List<String> {
    var result: MutableList<String> = ArrayList()
    if (Settings.blockedTags.isNotEmpty()) {
        val tags = content.attributes
            .filter { it.type == AttributeType.TAG || it.type == AttributeType.LANGUAGE }
            .map { it.name }
        for (blocked in Settings.blockedTags)
            for (tag in tags)
                if (blocked.equals(tag, ignoreCase = true) || isPresentAsWord(blocked, tag)) {
                    if (result.isEmpty()) result = ArrayList()
                    result.add(tag)
                    break
                }
        if (tags.isNotEmpty() && tags.size == result.size) trigger(2)
    }
    return result.toList()
}

/**
 * Update the given content's properties by parsing its webpage
 *
 * @param content   Content to parse again from its online source
 * @param keepUris  True to keep JSON and folder Uri values inside the result
 * @return Content updated from its online source, or null if something went wrong
 */
fun reparseFromScratch(content: Content, keepUris: Boolean = false): Content? {
    try {
        return reparseFromScratch(content.galleryUrl, content, keepUris)
    } catch (e: IOException) {
        Timber.w(e)
        return null
    } catch (e: CloudflareProtectedException) {
        Timber.w(e)
        return null
    }
}

/**
 * Create a new Content by parsing the webpage at the given URL
 *
 * @param url Webpage to parse to create the Content
 * @return Content created from the webpage at the given URL, or null if something went wrong
 * @throws IOException If something horrible happens during parsing
 */
@Throws(IOException::class, CloudflareProtectedException::class)
fun parseFromScratch(url: String): Content? {
    return reparseFromScratch(url, null)
}

/**
 * Parse the given webpage to update the given Content's properties
 *
 * @param url     Webpage to parse to update the given Content's properties
 * @param content Content which properties to update (read-only). If this parameter is null, the call returns a completely new Content
 * @param keepUris  True to keep JSON and folder Uri values inside the result
 * @return Content with updated properties, or null if something went wrong
 * @throws IOException If something horrible happens during parsing
 */
@Throws(IOException::class, CloudflareProtectedException::class)
private fun reparseFromScratch(
    url: String,
    content: Content?,
    keepUris: Boolean = false
): Content? {
    assertNonUiThread()

    val urlToLoad: String
    val site: Site?
    if (null == content) {
        urlToLoad = url
        site = Site.searchByUrl(url)
    } else {
        urlToLoad = content.galleryUrl
        site = content.site
    }
    if (null == site || Site.NONE == site) return null

    val fetchResponse = fetchBodyFast(urlToLoad, site, null, "text/html")
    fetchResponse.first.use { body ->
        if (null == body) return null
        val c = getContentParserClass(site)
        val jspoon = Jspoon.create()
        val htmlAdapter = jspoon.adapter(c) // Unchecked but alright

        val contentParser =
            htmlAdapter.fromInputStream(body.byteStream(), URL(urlToLoad))
        val newContent = if (null == content) contentParser.toContent(urlToLoad)
        else contentParser.update(content, urlToLoad, true)

        if (!keepUris) {
            newContent.jsonUri = ""
            newContent.clearStorageDoc()
            newContent.parentStorageUri = ""
        }

        if (newContent.status == StatusContent.IGNORED) {
            val canonicalUrl = contentParser.canonicalUrl
            return if (canonicalUrl.isNotEmpty() && !canonicalUrl.equals(
                    urlToLoad,
                    ignoreCase = true
                )
            ) reparseFromScratch(canonicalUrl, content)
            else null
        }

        // Clear existing chapters to avoid issues with extra chapter detection
        newContent.clearChapters()

        // Save cookies for future calls during download
        val params: MutableMap<String, String> =
            HashMap()
        val cookieStr = fetchResponse.second
        if (cookieStr.isNotEmpty()) params[HEADER_COOKIE_KEY] = cookieStr

        newContent.downloadParams = serializeToJson<Map<String, String>>(params, MAP_STRINGS)
        return newContent
    }
}

/**
 * Query source to fetch all image file names and URLs of a given book
 *
 * @param content           Book whose pages to retrieve
 * @param url               Url from which to parse pages from (e.g. gallery or chapter)
 * @param targetImageStatus Target status to set on the fetched images
 * @return List of pages with original URLs and file name
 */
@Throws(Exception::class)
fun fetchImageURLs(
    content: Content,
    url: String,
    targetImageStatus: StatusContent
): List<ImageFile> {
    val imgs: List<ImageFile>

    // If content doesn't have any download parameters, get them from the cookie manager
    var contentDownloadParamsStr = content.downloadParams
    if (null == contentDownloadParamsStr || contentDownloadParamsStr.isEmpty()) {
        val cookieStr = getCookies(url)
        if (cookieStr.isNotEmpty()) {
            val downloadParams: MutableMap<String, String> = HashMap()
            downloadParams[HEADER_COOKIE_KEY] = cookieStr
            content.downloadParams =
                serializeToJson<Map<String, String>>(downloadParams, MAP_STRINGS)
        }
    }

    // Use ImageListParser to query the source
    val parser = getImageListParser(content.site)
    try {
        imgs = parser.parseImageList(content, url)
    } finally {
        parser.clear()
    }

    // If no images found, or just the cover, image detection has failed
    if (imgs.isEmpty() || (1 == imgs.size && imgs[0].isCover)) throw EmptyResultException(url)

    // Add the content's download params to the images only if they have missing information
    contentDownloadParamsStr = content.downloadParams
    if (contentDownloadParamsStr != null && contentDownloadParamsStr.length > 2) {
        val contentDownloadParams = parseDownloadParams(contentDownloadParamsStr)
        for (i in imgs) {
            if (i.downloadParams.length > 2) {
                val imageDownloadParams = parseDownloadParams(i.downloadParams).toMutableMap()
                // Content's params
                contentDownloadParams.forEach {
                    if (!imageDownloadParams.containsKey(it.key)) imageDownloadParams[it.key] =
                        it.value
                }
                // Referer, just in case
                if (!imageDownloadParams.containsKey(HEADER_REFERER_KEY)) imageDownloadParams[HEADER_REFERER_KEY] =
                    content.site.url
                i.downloadParams = serializeToJson(imageDownloadParams, MAP_STRINGS)
            } else {
                i.downloadParams = contentDownloadParamsStr
            }
        }
    }

    // Cleanup and enrich generated objects
    for (img in imgs) {
        img.id = 0
        // Don't change the status if the picture has been downloaded during parsing (Mangago)
        if (img.status != StatusContent.DOWNLOADED) img.status = targetImageStatus
        img.contentId = content.id
    }

    return imgs
}

/**
 * Remove all files from the given Content's folder.
 * The folder itself is left empty except if JSON and cover are to be kept.
 *
 *
 * NB : "Thanks to" Android SAF, it is faster to :
 * 1/ copy kept files to temp storage
 * 2/ delete the whole folder and its entire content
 * 3/ re-create it
 * 4/ copy back kept files into it
 * ...rather than to delete all image files one by one.
 *
 * => code is way more complex but exec time is way faster
 *
 * @param context     Context to use
 * @param content     Content to remove files from
 * @param removeJson  True to remove the Hentoid JSON file; false to keep it
 * @param removeCover True to remove the cover picture; false to keep it
 */
fun purgeFiles(
    context: Context,
    content: Content, removeJson: Boolean, removeCover: Boolean
) {
    var bookFolder = getDocumentFromTreeUriString(context, content.storageUri)
    if (bookFolder != null) {
        // Identify files to keep
        val namesToKeep = NameFilter { displayName: String ->
            val name = displayName.lowercase(Locale.getDefault())
            (!removeJson && name.endsWith("json"))
                    || (!removeCover && name.startsWith(THUMB_FILE_NAME))
        }
        val filesToKeep = listFiles(context, bookFolder, namesToKeep)

        // If any, copy them to temp storage
        val tempFiles: MutableList<File> = ArrayList()
        var tempFolder: File? = null
        if (filesToKeep.isNotEmpty()) {
            tempFolder = getOrCreateCacheFolder(context, "tmp" + content.id)
            for (file in filesToKeep) {
                try {
                    val uri = copyFile(
                        context,
                        file.uri,
                        Uri.fromFile(tempFolder),
                        file.type ?: "",
                        file.name ?: ""
                    )
                    if (uri != null) {
                        val tmpFile = legacyFileFromUri(uri)
                        if (tmpFile != null) tempFiles.add(tmpFile)
                    }
                } catch (e: IOException) {
                    Timber.w(e)
                }
            }
        }

        try {
            // Delete the whole initial folder
            bookFolder.delete()

            // Re-create an empty folder with the same name
            val siteFolder = getOrCreateSiteDownloadDir(
                context,
                getLocation(content),
                content.site
            )
            if (siteFolder != null) {
                val name = bookFolder.name
                bookFolder = if (name != null) siteFolder.createDirectory(name)
                else getOrCreateContentDownloadDir(context, content, getLocation(content), false)
            }

            if (bookFolder != null) {
                content.setStorageDoc(bookFolder)
                // Copy back the files to the new folder
                for (file in tempFiles) {
                    try {
                        val name = file.name.lowercase(Locale.getDefault())
                        val mimeType = getMimeTypeFromFileName(name)
                        val newUri = copyFile(
                            context,
                            Uri.fromFile(file),
                            bookFolder.uri,
                            mimeType,
                            file.name
                        )
                        if (newUri != null && name.endsWith("json"))
                            content.jsonUri = newUri.toString()
                    } catch (e: IOException) {
                        Timber.w(e)
                    }
                }
            }
        } finally {
            // Delete temp files
            tempFolder?.delete()
        }
    }
}

/**
 * Format the given Content's tags for display
 *
 * @param content Content to get the formatted tags for
 * @return Given Content's tags formatted for display
 */
fun formatTagsForDisplay(content: Content): String {
    val tagsAttributes = content.attributeMap[AttributeType.TAG]
        ?: return ""

    val allTags = tagsAttributes.map { it.name }.sorted().take(30)

    return TextUtils.join(", ", allTags)
}

/**
 * Get the resource ID for the given Content's language flag
 *
 * @param context Context to use
 * @param content Content to get the flag resource ID for
 * @return Resource ID (DrawableRes) of the given Content's language flag; 0 if no matching flag found
 */
@DrawableRes
fun getFlagResourceId(
    context: Context,
    content: Content
): Int {
    val langAttributes = content.attributeMap[AttributeType.LANGUAGE]
    if (!langAttributes.isNullOrEmpty()) for (lang in langAttributes) {
        @DrawableRes val resId = getFlagFromLanguage(context, lang.name)
        if (resId != 0) return resId
    }
    return 0
}

/**
 * Get the drawable ID for the given rating
 *
 * @param rating Rating to get the resource ID for (0 to 5)
 * @return Resource ID representing the given rating
 */
@DrawableRes
fun getRatingResourceId(rating: Int): Int {
    return when (rating) {
        1 -> R.drawable.ic_star_1
        2 -> R.drawable.ic_star_2
        3 -> R.drawable.ic_star_3
        4 -> R.drawable.ic_star_4
        5 -> R.drawable.ic_star_5
        else -> R.drawable.ic_star_empty
    }
}

/**
 * Format the given Content's artists for display
 *
 * @param context Context to use
 * @param content Content to get the formatted artists for
 * @return Given Content's artists formatted for display
 */
fun formatArtistForDisplay(
    context: Context,
    content: Content
): String {
    val attributes: MutableList<Attribute> = ArrayList()

    val artistAttributes = content.attributeMap[AttributeType.ARTIST]
    if (artistAttributes != null) attributes.addAll(artistAttributes)
    val circleAttributes = content.attributeMap[AttributeType.CIRCLE]
    if (circleAttributes != null) attributes.addAll(circleAttributes)

    if (attributes.isEmpty()) {
        return context.getString(
            R.string.work_artist,
            context.resources.getString(R.string.work_untitled)
        )
    } else {
        val allArtists: MutableList<String?> = ArrayList()
        for (attribute in attributes) {
            allArtists.add(attribute.name)
        }
        val artists = TextUtils.join(", ", allArtists)
        return context.getString(R.string.work_artist, artists)
    }
}

/**
 * Format the given Content's series for display on book cards
 *
 * @param context Context to use
 * @param content Content to format
 * @return "Series" caption ready to be displayed on a book card
 */
fun formatSeriesForDisplay(
    context: Context,
    content: Content
): String {
    val seriesAttributes = content.attributeMap[AttributeType.SERIE]
    if (seriesAttributes.isNullOrEmpty()) {
        return ""
    } else {
        val allSeries: MutableList<String?> = ArrayList()
        for (attribute in seriesAttributes) {
            allSeries.add(attribute.name)
        }
        val series = TextUtils.join(", ", allSeries)
        return context.getString(R.string.work_series, series)
    }
}

/**
 * Transform the given online URL into a working GlideUrl using the given Content's cookies
 * (useful when viewing queue screen before any image has been downloaded)
 *
 * @param imageUrl URL of the online picture to transform
 * @param content  Content to use for cookies / referer
 * @return Working GlideUrl pointing to the given image URL, using the correct cookies / referer
 */
fun bindOnlineCover(
    imageUrl: String,
    content: Content?
): GlideUrl? {
    if (getWebViewAvailable()) {
        var cookieStr: String? = null
        var referer: String? = null
        var builder = LazyHeaders.Builder()

        // Quickly skip JSON deserialization if there are no cookies in downloadParams
        if (content != null) {
            val downloadParamsStr = content.downloadParams
            if (downloadParamsStr != null && downloadParamsStr.contains(HEADER_COOKIE_KEY)) {
                val downloadParams = parseDownloadParams(downloadParamsStr)
                cookieStr = downloadParams[HEADER_COOKIE_KEY]
                referer = downloadParams[HEADER_REFERER_KEY]
            }
            if (null == cookieStr) cookieStr = getCookies(content.galleryUrl)
            if (null == referer) referer = content.galleryUrl
            builder = builder.addHeader(HEADER_COOKIE_KEY, cookieStr).addHeader(
                HEADER_REFERER_KEY,
                referer!!
            ).addHeader(HEADER_USER_AGENT, content.site.userAgent)
        }

        return GlideUrl(imageUrl, builder.build()) // From URL
    }
    return null
}

/**
 * Find the best match for the given Content inside the library and queue
 *
 * @param context     Context to use
 * @param content     Content to find the duplicate for
 * @param useTitle    Use title as a duplicate criteria
 * @param useArtist   Use artist as a duplicate criteria
 * @param useLanguage Use language as a duplicate criteria
 * @param useCover    Use cover picture perceptual hash as a duplicate criteria
 * @param sensitivity Sensitivity to use
 * @param pHashIn     Cover picture perceptual hash to use as an override for the given Content's cover hash; Long.MIN_VALUE not to override
 * @param dao         DAO to use
 * @return Pair containing
 * - left side : Best match for the given Content inside the library and queue
 * - Right side : Similarity score (between 0 and 1; 1=100%)
 */
fun findDuplicate(
    context: Context,
    content: Content, useTitle: Boolean, useArtist: Boolean, useLanguage: Boolean,
    useCover: Boolean, sensitivity: Int, pHashIn: Long, dao: CollectionDAO
): Pair<Content, Float>? {
    // First find good rough candidates by searching for the longest word in the title
    var pHash = pHashIn
    val words =
        cleanMultipleSpaces(simplify(content.title)).split(" ")
    val longestWord = words.sortedWith(Comparator.comparingInt { it.length }).lastOrNull()
    // Too many resources consumed if the longest word is 1 character long
    if (null == longestWord || longestWord.length < 2) return null

    val contentStatuses = ArrayUtils.addAll(libraryStatus, *queueTabStatus)
    val roughCandidates = dao.searchTitlesWith(longestWord, contentStatuses)
    if (roughCandidates.isEmpty()) return null

    if (!useCover) pHash = Long.MIN_VALUE

    // Compute cover hashes for selected candidates
    for (c in roughCandidates) if (0L == c.cover.imageHash) computeAndSaveCoverHash(context, c, dao)

    // Refine by running the actual duplicate detection algorithm against the rough candidates
    val entries: MutableList<DuplicateEntry> = ArrayList()
    val cosine: StringSimilarity = Cosine()
    val reference =
        DuplicateCandidate(content, useTitle, useArtist, useLanguage, useCover, true, pHash)
    val candidates = roughCandidates.map { c ->
        DuplicateCandidate(
            c,
            useTitle,
            useArtist,
            useLanguage,
            useCover,
            true,
            Long.MIN_VALUE
        )
    }.toList()
    for (candidate in candidates) {
        val entry = processContent(
            reference,
            candidate, useTitle, useCover, useArtist, useLanguage, true, sensitivity, cosine
        )
        if (entry != null) entries.add(entry)
    }
    // Sort by similarity and size (unfortunately, Comparator.comparing is API24...)
    val bestMatch = entries.sortedWith { obj, other ->
        obj.compareTo(other!!)
    }.firstOrNull()
    if (bestMatch != null) {
        val resultContent = dao.selectContent(bestMatch.duplicateId)
        val resultScore = bestMatch.calcTotalScore()
        if (resultContent != null) return Pair(resultContent, resultScore)
    }

    return null
}

/**
 * Compute perceptual hash for the cover picture
 *
 * @param context Context to use
 * @param content Content to process
 * @param dao     Dao used to save cover hash
 */
fun computeAndSaveCoverHash(
    context: Context,
    content: Content, dao: CollectionDAO
) {
    val coverBitmap = getCoverBitmapFromContent(context, content)
    val pHash = calcPhash(getHashEngine(), coverBitmap)
    coverBitmap?.recycle()
    content.cover.imageHash = pHash
    dao.insertImageFile(content.cover)
}

/**
 * Test if online pages for the given Content are downloadable
 * NB : Implementation does not test all pages but one page picked randomly
 *
 * @param content Content whose pages to test
 * @return True if pages are downloadable; false if they aren't
 */
fun isDownloadable(content: Content): Boolean {
    val images: List<ImageFile> = content.imageFiles
    if (images.isEmpty()) return false

    // Pick a random picture
    val img = images[getRandomInt(images.size)]

    // Peek it to see if downloads work
    val headers: MutableList<Pair<String, String>> = ArrayList()
    // Useful for Hitomi and Toonily
    headers.add(Pair(HEADER_REFERER_KEY, content.readerUrl))

    try {
        if (img.needsPageParsing) {
            // Get cookies from the app jar
            var cookieStr = getCookies(img.pageUrl)
            // If nothing found, peek from the site
            if (cookieStr.isEmpty()) cookieStr = peekCookies(img.pageUrl)
            if (cookieStr.isNotEmpty()) headers.add(Pair(HEADER_COOKIE_KEY, cookieStr))
            return testDownloadPictureFromPage(content.site, img, headers)
        } else {
            // Get cookies from the app jar
            var cookieStr = getCookies(img.url)
            // If nothing found, peek from the site
            if (cookieStr.isEmpty()) cookieStr = peekCookies(content.galleryUrl)
            if (cookieStr.isNotEmpty()) headers.add(Pair(HEADER_COOKIE_KEY, cookieStr))
            return testDownloadPicture(content.site, img, headers)
        }
    } catch (e: IOException) {
        Timber.w(e)
    } catch (e: LimitReachedException) {
        Timber.w(e)
    } catch (e: EmptyResultException) {
        Timber.w(e)
    } catch (e: CloudflareProtectedException) {
        Timber.w(e)
    }
    return false
}

/**
 * Test if online pages for the given Chapter are downloadable
 * NB : Implementation does not test all pages but one page picked randomly
 *
 * @param chapter Chapter whose pages to test
 * @return True if pages are downloadable; false if they aren't
 */
fun isDownloadable(chapter: Chapter): Boolean {
    val images = chapter.imageList
    if (images.isEmpty()) return false

    val content = chapter.content.reach(chapter) ?: return false

    // Pick a random picture
    val img = images[getRandomInt(images.size)]

    // Peek it to see if downloads work
    val headers: MutableList<Pair<String, String>> = ArrayList()
    // Useful for Hitomi and Toonily
    headers.add(Pair(HEADER_REFERER_KEY, content.readerUrl))

    try {
        if (img.needsPageParsing) {
            // Get cookies from the app jar
            var cookieStr = getCookies(img.pageUrl)
            // If nothing found, peek from the site
            if (cookieStr.isEmpty()) cookieStr = peekCookies(img.pageUrl)
            if (cookieStr.isNotEmpty()) headers.add(Pair(HEADER_COOKIE_KEY, cookieStr))
            return testDownloadPictureFromPage(content.site, img, headers)
        } else {
            // Get cookies from the app jar
            var cookieStr = getCookies(img.url)
            // If nothing found, peek from the site
            if (cookieStr.isEmpty()) cookieStr = peekCookies(chapter.url)
            if (cookieStr.isNotEmpty()) headers.add(Pair(HEADER_COOKIE_KEY, cookieStr))
            return testDownloadPicture(content.site, img, headers)
        }
    } catch (e: IOException) {
        Timber.w(e)
    } catch (e: LimitReachedException) {
        Timber.w(e)
    } catch (e: EmptyResultException) {
        Timber.w(e)
    } catch (e: CloudflareProtectedException) {
        Timber.w(e)
    }
    return false
}

/**
 * Test if the given picture is downloadable using its page URL
 *
 * @param site           Corresponding Site
 * @param img            Picture to test
 * @param requestHeaders Request headers to use
 * @return True if the given picture is downloadable; false if not
 * @throws IOException           If something happens during the download attempt
 * @throws LimitReachedException If the site's download limit has been reached
 * @throws EmptyResultException  If no picture has been detected
 */
@Throws(
    IOException::class,
    LimitReachedException::class,
    EmptyResultException::class,
    CloudflareProtectedException::class
)
private fun testDownloadPictureFromPage(
    site: Site,
    img: ImageFile,
    requestHeaders: MutableList<Pair<String, String>>
): Boolean {
    val pageUrl = fixUrl(img.pageUrl, site.url)
    val parser = getImageListParser(site)
    val pages: Pair<String, String?>
    try {
        pages = parser.parseImagePage(pageUrl, requestHeaders)
    } finally {
        parser.clear()
    }
    img.url = pages.first
    // Download the picture
    try {
        return testDownloadPicture(site, img, requestHeaders)
    } catch (e: IOException) {
        if (pages.second != null) Timber.d("First download failed; trying backup URL")
        else throw e
    } catch (e: CloudflareProtectedException) {
        if (pages.second != null) Timber.d("First download failed; trying backup URL")
        else throw e
    }
    // Trying with backup URL
    img.url = pages.second ?: ""
    return testDownloadPicture(site, img, requestHeaders)
}

/**
 * Test if the given picture is downloadable using its own URL
 *
 * @param site           Corresponding Site
 * @param img            Picture to test
 * @param requestHeaders Request headers to use
 * @return True if the given picture is downloadable; false if not
 * @throws IOException If something happens during the download attempt
 */
@Throws(IOException::class, CloudflareProtectedException::class)
private fun testDownloadPicture(
    site: Site,
    img: ImageFile,
    requestHeaders: MutableList<Pair<String, String>>
): Boolean {
    val url = fixUrl(img.url, site.url)

    val response = fetchBodyFast(url, site, requestHeaders, null)
    val body = response.first
        ?: throw IOException("Could not read response : empty body for " + img.url)

    val buffer = ByteArray(50)
    body.byteStream().use { `in` ->
        if (`in`.read(buffer) > -1) {
            val mimeType = getMimeTypeFromPictureBinary(buffer)
            Timber.d(
                "Testing online picture accessibility : found %s at %s",
                mimeType,
                img.url
            )
            return (mimeType.isNotEmpty() && mimeType != MIME_IMAGE_GENERIC)
        }
    }
    return false
}

/**
 * Merge the given list of Content into one single new Content with the given title
 * NB : The Content's of the given list are _not_ removed
 *
 * @param context          Context to use
 * @param contentList      List of Content to merge together
 * @param newTitle         Title of the new merged Content
 * @param useBookAsChapter True to ignore existing chapters of the merged books and create one chapter per book instead
 * @param dao              DAO to use
 * @throws ContentNotProcessedException If something terrible happens
 */
@Throws(ContentNotProcessedException::class)
fun mergeContents(
    context: Context,
    contentList: List<Content>,
    newTitle: String,
    useBookAsChapter: Boolean,
    dao: CollectionDAO,
    isCanceled: () -> Boolean,
    onProgress: (Int, Int, String) -> Unit,
    onComplete: () -> Unit
) {
    assertNonUiThread()

    // New book inherits properties of the first content of the list
    // which takes "precedence" as the 1st chapter
    val firstContent = contentList[0]

    // Initiate a new Content
    val mergedContent = Content(
        site = firstContent.site,
        dbUrl = firstContent.url,
        uniqueSiteId = firstContent.uniqueSiteId + "_", // Not to create a copy of firstContent
        downloadMode = firstContent.downloadMode,
        title = newTitle,
        coverImageUrl = firstContent.coverImageUrl,
        uploadDate = firstContent.uploadDate,
        downloadDate = Instant.now().toEpochMilli(),
        downloadCompletionDate = Instant.now().toEpochMilli(),
        status = firstContent.status,
        favourite = firstContent.favourite,
        rating = firstContent.rating,
        bookPreferences = firstContent.bookPreferences,
        manuallyMerged = true
    )

    // Merge attributes
    val mergedAttributes = contentList.flatMap { it.attributes }
    mergedContent.addAttributes(mergedAttributes)

    // Create destination folder for new content
    val parentFolder: DocumentFile?
    var targetFolder: DocumentFile?
    // TODO destination is an archive when all source contents are archives

    // External library root for external content
    if (mergedContent.status == StatusContent.EXTERNAL) {
        val externalRootFolder =
            getDocumentFromTreeUriString(context, Preferences.getExternalLibraryUri())
        if (null == externalRootFolder || !externalRootFolder.exists()) throw ContentNotProcessedException(
            mergedContent,
            "Could not create target directory : external root unreachable"
        )

        val bookFolderName = formatBookFolderName(mergedContent)
        // First try finding the folder with new naming...
        targetFolder = findFolder(context, externalRootFolder, bookFolderName.first)
        if (null == targetFolder) { // ...then with old (sanitized) naming...
            targetFolder = findFolder(context, externalRootFolder, bookFolderName.second)
            if (null == targetFolder) { // ...if not, create a new folder with the new naming...
                targetFolder = externalRootFolder.createDirectory(bookFolderName.first)
                if (null == targetFolder) { // ...if it fails, create a new folder with the old naming
                    targetFolder = externalRootFolder.createDirectory(bookFolderName.second)
                }
            }
        }
        parentFolder = externalRootFolder
    } else { // Primary folder for non-external content; using download strategy
        val location = selectDownloadLocation(context)
        targetFolder = getOrCreateContentDownloadDir(context, mergedContent, location, true)
        parentFolder = getDocumentFromTreeUriString(context, Preferences.getStorageUri(location))
    }
    if (null == targetFolder || !targetFolder.exists())
        throw ContentNotProcessedException(mergedContent, "Could not create target directory")
    mergedContent.setStorageDoc(targetFolder)
    // Ignore the new folder as it is being merged
    Beholder.ignoreFolder(targetFolder)

    // Renumber all picture files and dispatch chapters
    val nbImages = contentList.flatMap { it.imageList }.count { it.isReadable }
    val nbMaxDigits = (floor(log10(nbImages.toDouble())) + 1).toInt()

    val mergedImages: MutableList<ImageFile> = ArrayList()
    val mergedChapters: MutableList<Chapter> = ArrayList()

    var isError = false
    var tempFolder: File? = null

    try {
        // Merge images and chapters
        var chapterOrder = 0
        var pictureOrder = 1
        var nbProcessedPics = 1
        var coverFound = false

        for (c in contentList) {
            if (isCanceled.invoke()) break
            var newChapter: Chapter? = null
            // Create a default "content chapter" that represents the original book before merging
            val contentChapter = Chapter(chapterOrder++, c.galleryUrl, c.title)
            contentChapter.uniqueId = c.uniqueSiteId + "-" + contentChapter.order

            val imgs = c.imageList
            val firstImageIsCover = !imgs.any { it.isCover }
            var imgIndex = -1
            for (img in imgs) {
                if (isCanceled.invoke()) break
                imgIndex++
                // Unarchive images by chunks of 80MB max
                if (c.isArchive) {
                    tempFolder?.delete()
                    tempFolder = getOrCreateCacheFolder(context, "tmp-merge-archive")
                    if (null == tempFolder) throw ContentNotProcessedException(
                        mergedContent,
                        "Could not create temp unarchive folder"
                    )
                    var unarchivedBytes = 0L
                    val picsToUnarchive: MutableList<ImageFile> = ArrayList()
                    var idx = -1
                    while (unarchivedBytes < 80.0 * 1024 * 1024) { // 80MB
                        idx++
                        if (idx + imgIndex >= imgs.size) break
                        val picToUnarchive = imgs[imgIndex + idx]
                        if (!picToUnarchive.fileUri.startsWith(c.storageUri)) continue // thumb
                        picsToUnarchive.add(picToUnarchive)
                        unarchivedBytes += picToUnarchive.size
                    }
                    val toExtract = picsToUnarchive.map {
                        Pair(
                            it.fileUri.replace(c.storageUri + File.separator, ""),
                            it.id.toString()
                        )
                    }
                    val unarchivedFiles = context.extractArchiveEntriesBlocking(
                        Uri.parse(c.storageUri),
                        tempFolder,
                        toExtract
                    )
                    if (unarchivedFiles.size < picsToUnarchive.size) throw ContentNotProcessedException(
                        mergedContent,
                        "Issue when unarchiving " + unarchivedFiles.size + " " + picsToUnarchive.size
                    )

                    // Replace intial file URIs with unarchived files URIs
                    picsToUnarchive.forEachIndexed { index, imageFile ->
                        Timber.d(
                            "Replacing %s with %s",
                            imageFile.fileUri,
                            unarchivedFiles[index].toString()
                        )
                        imageFile.fileUri = unarchivedFiles[index].toString()
                    }
                }

                if (!img.isReadable && coverFound) continue // Skip thumbs from 2+ rank merged books
                val newImg = ImageFile(img, populateContent = false, populateChapter = false)
                newImg.id = 0 // Force working on a new picture
                newImg.fileUri = "" // Clear initial URI
                if (newImg.isReadable) {
                    newImg.order = pictureOrder++
                    newImg.computeName(nbMaxDigits)
                } else {
                    newImg.isCover = true
                    newImg.order = 0
                }
                if (firstImageIsCover && !coverFound) newImg.isCover = true

                if (newImg.isCover) coverFound = true

                if (newImg.isReadable) {
                    val chapLink = img.linkedChapter
                    // No chapter -> set content chapter
                    if (null == chapLink || useBookAsChapter) {
                        newChapter = contentChapter
                    } else {
                        if (chapLink.uniqueId.isEmpty()) chapLink.populateUniqueId()
                        if (null == newChapter || chapLink.uniqueId != newChapter.uniqueId) {
                            newChapter = Chapter(chapLink)
                            newChapter.order = chapterOrder++
                        }
                    }
                    if (!mergedChapters.contains(newChapter))
                        mergedChapters.add(newChapter)
                    newImg.setChapter(newChapter)
                }

                // If exists, move the picture file to the merged books' folder
                if (isInLibrary(newImg.status)) {
                    val newUri = copyFile(
                        context,
                        Uri.parse(img.fileUri),
                        targetFolder.uri,
                        newImg.mimeType,
                        newImg.name + "." + getExtensionFromUri(img.fileUri)
                    )
                    if (newUri != null) newImg.fileUri = newUri.toString()
                    else Timber.w("Could not move file %s", img.fileUri)
                    onProgress.invoke(nbProcessedPics++, nbImages, c.title)
                }
                mergedImages.add(newImg)
            }
        }
    } catch (e: IOException) {
        Timber.w(e)
        isError = true
    } finally {
        // Delete temp files
        tempFolder?.delete()
    }

    // Remove target folder and merged images if manually canceled
    if (isCanceled.invoke()) {
        targetFolder.delete()
    }

    if (!isError && !isCanceled.invoke()) {
        mergedContent.setImageFiles(mergedImages)
        mergedContent.setChapters(mergedChapters) // Chapters have to be attached to Content too
        mergedContent.qtyPages = mergedImages.size - 1
        mergedContent.computeSize()

        val jsonFile = createJson(context, mergedContent)
        if (jsonFile != null) mergedContent.jsonUri = jsonFile.uri.toString()

        // Save new content (incl. non-custom group operations)
        addContent(context, dao, mergedContent)

        // Merge custom groups and update
        // Merged book can be a member of one custom group only
        val customGroup = contentList.flatMap { it.groupItems }
            .mapNotNull { it.getGroup() }
            .distinct().firstOrNull { it.grouping == Grouping.CUSTOM }
        if (customGroup != null) moveContentToCustomGroup(mergedContent, customGroup, dao)

        // If merged book is external, register it to the Beholder
        if (StatusContent.EXTERNAL == mergedContent.status && parentFolder != null)
            registerContent(
                context,
                parentFolder.uri.toString(),
                targetFolder,
                mergedContent.id
            )
    }

    if (!isCanceled.invoke()) onComplete.invoke()
}

fun getLocation(content: Content): StorageLocation {
    for (location in StorageLocation.entries) {
        val rootUri = Preferences.getStorageUri(location)
        if (rootUri.isNotEmpty() && content.storageUri.startsWith(rootUri)) return location
    }
    return StorageLocation.NONE
}

fun purgeContent(
    context: Context,
    content: Content, keepCover: Boolean, isDownloadPrepurge: Boolean
) {
    val builder = DeleteData.Builder()
    builder.setContentPurgeIds(listOf(content.id))
    builder.setContentPurgeKeepCovers(keepCover)
    builder.setDownloadPrepurge(isDownloadPrepurge)

    val workManager = WorkManager.getInstance(context)
    workManager.enqueueUniqueWork(
        R.id.delete_service_purge.toString(),
        ExistingWorkPolicy.APPEND_OR_REPLACE,
        OneTimeWorkRequest.Builder(
            PurgeWorker::class.java
        ).setInputData(builder.data).build()
    )
}


/**
 * Comparator to be used to sort files according to their names
 */
private class InnerNameNumberFileComparator : Comparator<DocumentFile?> {
    override fun compare(o1: DocumentFile?, o2: DocumentFile?): Int {
        return CaseInsensitiveSimpleNaturalComparator.getInstance<CharSequence>()
            .compare(o1?.name ?: "", o2?.name ?: "")
    }
}

/**
 * Comparator to be used to sort archive entries according to their names
 */
private class InnerNameNumberArchiveComparator : Comparator<ArchiveEntry> {
    override fun compare(o1: ArchiveEntry, o2: ArchiveEntry): Int {
        return CaseInsensitiveSimpleNaturalComparator.getInstance<CharSequence>()
            .compare(o1.path, o2.path)
    }
}

/**
 * Comparator to be used to sort Contents according to their titles
 */
class InnerNameNumberContentComparator : Comparator<Content> {
    override fun compare(c1: Content, c2: Content): Int {
        return CaseInsensitiveSimpleNaturalComparator.getInstance<CharSequence>()
            .compare(c1.title, c2.title)
    }
}