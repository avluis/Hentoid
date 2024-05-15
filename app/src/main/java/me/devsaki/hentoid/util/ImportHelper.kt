package me.devsaki.hentoid.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract
import androidx.documentfile.provider.DocumentFile
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.squareup.moshi.JsonDataException
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.DEFAULT_PRIMARY_FOLDER
import me.devsaki.hentoid.core.DEFAULT_PRIMARY_FOLDER_OLD
import me.devsaki.hentoid.core.HentoidApp.LifeCycleListener.Companion.disable
import me.devsaki.hentoid.core.JSON_FILE_NAME
import me.devsaki.hentoid.core.JSON_FILE_NAME_OLD
import me.devsaki.hentoid.core.JSON_FILE_NAME_V2
import me.devsaki.hentoid.core.WORK_CLOSEABLE
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.database.domains.RenamingRule
import me.devsaki.hentoid.database.domains.SiteBookmark
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.json.JsonContent
import me.devsaki.hentoid.notification.import_.ImportNotificationChannel
import me.devsaki.hentoid.util.file.ArchiveEntry
import me.devsaki.hentoid.util.file.FileExplorer
import me.devsaki.hentoid.util.file.NameFilter
import me.devsaki.hentoid.util.file.createNoMedia
import me.devsaki.hentoid.util.file.getArchiveEntries
import me.devsaki.hentoid.util.file.getArchiveNamesFilter
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.getFileNameWithoutExtension
import me.devsaki.hentoid.util.file.getFullPathFromUri
import me.devsaki.hentoid.util.file.isSupportedArchive
import me.devsaki.hentoid.util.file.listFoldersFilter
import me.devsaki.hentoid.util.file.persistNewUriPermission
import me.devsaki.hentoid.util.image.imageNamesFilter
import me.devsaki.hentoid.util.image.isSupportedImage
import me.devsaki.hentoid.workers.ExternalImportWorker
import me.devsaki.hentoid.workers.PrimaryImportWorker
import me.devsaki.hentoid.workers.data.PrimaryImportData
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.Locale


private const val EXTERNAL_LIB_TAG = "external-library"

enum class PickerResult {
    OK,  // OK - Returned a valid URI
    KO_NO_URI,  // No URI selected
    KO_CANCELED, // Operation canceled
    KO_OTHER // Any other issue
}

enum class ProcessFolderResult {
    OK_EMPTY_FOLDER, // OK - Existing, empty Hentoid folder
    OK_LIBRARY_DETECTED, // OK - En existing Hentoid folder with books
    OK_LIBRARY_DETECTED_ASK, // OK - Existing Hentoid folder with books + we need to ask the user if he wants to import them
    KO_INVALID_FOLDER, // File or folder is invalid, cannot be found
    KO_APP_FOLDER, // Selected folder is the primary location and can't be used as an external location
    KO_DOWNLOAD_FOLDER, // Selected folder is the device's download folder and can't be used as a primary folder (downloads visibility + storage calculation issues)
    KO_CREATE_FAIL, // Hentoid folder could not be created
    KO_ALREADY_RUNNING, // Import is already running
    KO_OTHER_PRIMARY, // Selected folder is inside or contains the other primary location
    KO_PRIMARY_EXTERNAL, // Selected folder is inside or contains the external location
    KO_OTHER // Any other issue
}

private val hentoidFolderNames =
    NameFilter { displayName: String ->
        (displayName.equals(DEFAULT_PRIMARY_FOLDER, ignoreCase = true)
                || displayName.equals(DEFAULT_PRIMARY_FOLDER_OLD, ignoreCase = true))
    }

private val hentoidContentJson =
    NameFilter { displayName: String ->
        displayName.equals(JSON_FILE_NAME_V2, ignoreCase = true)
                || displayName.equals(JSON_FILE_NAME, ignoreCase = true)
                || displayName.equals(JSON_FILE_NAME_OLD, ignoreCase = true)
    }

/**
 * Import options for the Hentoid folder
 */
data class ImportOptions(
    val rename: Boolean = false, // If true, rename folders with current naming convention
    val removePlaceholders: Boolean = false, // If true, books & folders with status PLACEHOLDER will be removed
    val renumberPages: Boolean = false, // If true, renumber pages from books that have numbering gaps
    val cleanNoJson: Boolean = false, // If true, delete folders where no JSON file is found
    val cleanNoImages: Boolean = false, // If true, delete folders where no supported images are found
    val importGroups: Boolean = false // If true, reimport groups from the groups JSON
)


/**
 * Indicate whether the given folder name is a valid Hentoid folder name
 *
 * @param folderName Folder name to test
 * @return True if the given folder name is a valid Hentoid folder name; false if not
 */
fun isHentoidFolderName(folderName: String): Boolean {
    return hentoidFolderNames.accept(folderName)
}


class PickFolderContract : ActivityResultContract<StorageLocation, Pair<PickerResult, Uri>>() {
    override fun createIntent(context: Context, input: StorageLocation): Intent {
        disable() // Prevents the app from displaying the PIN lock when returning from the SAF dialog
        return getFolderPickerIntent(context, input)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Pair<PickerResult, Uri> {
        disable() // Restores autolock on app going to background
        return parsePickerResult(resultCode, intent)
    }
}


class PickFileContract : ActivityResultContract<Int, Pair<PickerResult, Uri>>() {
    override fun createIntent(context: Context, input: Int): Intent {
        disable() // Prevents the app from displaying the PIN lock when returning from the SAF dialog
        return getFilePickerIntent()
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Pair<PickerResult, Uri> {
        disable() // Restores autolock on app going to background
        return parsePickerResult(resultCode, intent)
    }
}


private fun parsePickerResult(resultCode: Int, intent: Intent?): Pair<PickerResult, Uri> {
    // Return from the SAF picker
    if (resultCode == Activity.RESULT_OK && intent != null) {
        // Get Uri from Storage Access Framework
        val uri = intent.data
        return if (uri != null) Pair(PickerResult.OK, uri)
        else Pair(PickerResult.KO_NO_URI, Uri.EMPTY)
    } else if (resultCode == Activity.RESULT_CANCELED) {
        return Pair(PickerResult.KO_CANCELED, Uri.EMPTY)
    }
    return Pair(PickerResult.KO_OTHER, Uri.EMPTY)
}

/**
 * Get the intent for the SAF folder picker properly set up, positioned on the Hentoid primary folder
 *
 * @param context Context to be used
 * @return Intent for the SAF folder picker
 */
private fun getFolderPickerIntent(context: Context, location: StorageLocation): Intent {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
    intent.putExtra(DocumentsContract.EXTRA_PROMPT, context.getString(R.string.dialog_prompt))
    // http://stackoverflow.com/a/31334967/1615876
    intent.putExtra("android.content.extra.SHOW_ADVANCED", true)

    // Start the SAF at the specified location
    if (Build.VERSION.SDK_INT >= VERSION_CODES.O
        && Preferences.getStorageUri(location).isNotEmpty()
    ) {
        val file = getDocumentFromTreeUriString(
            context,
            Preferences.getStorageUri(location)
        )
        if (file != null) intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, file.uri)
    }
    return intent
}

/**
 * Get the intent for the SAF file picker properly set up
 *
 * @return Intent for the SAF folder picker
 */
private fun getFilePickerIntent(): Intent {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
    intent.addCategory(Intent.CATEGORY_OPENABLE)
    intent.setType("*/*")
    // http://stackoverflow.com/a/31334967/1615876
    intent.putExtra("android.content.extra.SHOW_ADVANCED", true)
    disable() // Prevents the app from displaying the PIN lock when returning from the SAF dialog
    return intent
}

/**
 * Scan the given tree URI for a primary folder
 * If none is found there, try to create one
 *
 * @param context         Context to be used
 * @param treeUri         Tree URI of the folder where to find or create the Hentoid folder
 * @param location        Location to associate the folder with
 * @param askScanExisting If true and an existing non-empty Hentoid folder is found, the user will be asked if he wants to import its contents
 * @param options         Import options - See ImportHelper.ImportOptions
 * @return Pair containing :
 * - Left : Standardized result - see ImportHelper.Result
 * - Right : URI of the detected or created primary folder
 */
fun setAndScanPrimaryFolder(
    context: Context,
    treeUri: Uri,
    location: StorageLocation,
    askScanExisting: Boolean,
    options: ImportOptions?
): Pair<ProcessFolderResult, String> {
    // Persist I/O permissions; keep existing ones if present
    val locs: MutableList<StorageLocation> = ArrayList()
    locs.add(StorageLocation.EXTERNAL)
    if (location == StorageLocation.PRIMARY_1) locs.add(StorageLocation.PRIMARY_2) else locs.add(
        StorageLocation.PRIMARY_1
    )
    persistLocationCredentials(context, treeUri, locs)

    // Check if the folder exists
    val docFile = DocumentFile.fromTreeUri(context, treeUri)
    if (null == docFile || !docFile.exists()) {
        Timber.e("Could not find the selected file %s", treeUri.toString())
        return Pair(ProcessFolderResult.KO_INVALID_FOLDER, treeUri.toString())
    }

    // Check if the folder is not the device's Download folder
    val pathSegments = treeUri.pathSegments
    if (pathSegments.size > 1) {
        var firstSegment = pathSegments[1].lowercase(Locale.getDefault())
        firstSegment =
            firstSegment.split(File.separator.toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()[0]
        if (firstSegment.startsWith("download") || firstSegment.startsWith("primary:download")) {
            Timber.e("Device's download folder detected : %s", treeUri.toString())
            return Pair(ProcessFolderResult.KO_DOWNLOAD_FOLDER, treeUri.toString())
        }
    }

    // Check if selected folder is separate from Hentoid's other primary location
    val otherLocationUriStr: String =
        if (location == StorageLocation.PRIMARY_1) Preferences.getStorageUri(StorageLocation.PRIMARY_2)
        else Preferences.getStorageUri(StorageLocation.PRIMARY_1)

    if (otherLocationUriStr.isNotEmpty()) {
        val treeFullPath = getFullPathFromUri(context, treeUri)
        val otherLocationFullPath =
            getFullPathFromUri(context, Uri.parse(otherLocationUriStr))
        if (treeFullPath.startsWith(otherLocationFullPath)) {
            Timber.e(
                "Selected folder is inside the other primary location : %s",
                treeUri.toString()
            )
            return Pair(ProcessFolderResult.KO_OTHER_PRIMARY, treeUri.toString())
        }
        if (otherLocationFullPath.startsWith(treeFullPath)) {
            Timber.e(
                "Selected folder contains the other primary location : %s",
                treeUri.toString()
            )
            return Pair(ProcessFolderResult.KO_OTHER_PRIMARY, treeUri.toString())
        }
    }

    // Check if selected folder is separate from Hentoid's external location
    val extLocationStr = Preferences.getStorageUri(StorageLocation.EXTERNAL)
    if (extLocationStr.isNotEmpty()) {
        val treeFullPath = getFullPathFromUri(context, treeUri)
        val extFullPath = getFullPathFromUri(context, Uri.parse(extLocationStr))
        if (treeFullPath.startsWith(extFullPath)) {
            Timber.e("Selected folder is inside the external location : %s", treeUri.toString())
            return Pair(ProcessFolderResult.KO_PRIMARY_EXTERNAL, treeUri.toString())
        }
        if (extFullPath.startsWith(treeFullPath)) {
            Timber.e("Selected folder contains the external location : %s", treeUri.toString())
            return Pair(ProcessFolderResult.KO_PRIMARY_EXTERNAL, treeUri.toString())
        }
    }

    // Retrieve or create the Hentoid folder
    val hentoidFolder = getOrCreateHentoidFolder(context, docFile)
    if (null == hentoidFolder) {
        Timber.e("Could not create Primary folder in folder %s", docFile.uri.toString())
        return Pair(ProcessFolderResult.KO_CREATE_FAIL, treeUri.toString())
    }

    // Set the folder as the app's downloads folder
    val result = createNoMedia(context, hentoidFolder)
    if (result < 0) {
        Timber.e(
            "Could not set the selected root folder (error = %d) %s",
            result,
            hentoidFolder.uri.toString()
        )
        return Pair(
            ProcessFolderResult.KO_INVALID_FOLDER,
            hentoidFolder.uri.toString()
        )
    }

    // Scan the folder for an existing library; start the import
    return if (hasBooks(context, hentoidFolder)) {
        if (!askScanExisting) {
            runPrimaryImport(context, location, hentoidFolder.uri.toString(), options)
            Pair(ProcessFolderResult.OK_LIBRARY_DETECTED, hentoidFolder.uri.toString())
        } else Pair(
            ProcessFolderResult.OK_LIBRARY_DETECTED_ASK,
            hentoidFolder.uri.toString()
        )
    } else {
        // Create a new library or import an Hentoid folder without books
        // => Don't run the import worker and settle things here

        // In case that Location was previously populated, drop all books
        if (Preferences.getStorageUri(location).isNotEmpty()) {
            val dao: CollectionDAO = ObjectBoxDAO()
            try {
                ContentHelper.detachAllPrimaryContent(dao, location)
            } finally {
                dao.cleanup()
            }
        }
        Preferences.setStorageUri(location, hentoidFolder.uri.toString())
        Pair(ProcessFolderResult.OK_EMPTY_FOLDER, hentoidFolder.uri.toString())
    }
}

/**
 * Scan the given tree URI for 3rd party books, archives or Hentoid books
 *
 * @param context Context to be used
 * @param treeUri Tree URI of the folder where to find 3rd party books, archives or Hentoid books
 * @return Pair containing :
 * - Left : Standardized result - see ImportHelper.Result
 * - Right : URI of the detected or created primary folder
 */
fun setAndScanExternalFolder(
    context: Context,
    treeUri: Uri
): Pair<ProcessFolderResult, String> {
    // Persist I/O permissions; keep existing ones if present
    persistLocationCredentials(
        context,
        treeUri,
        listOf(StorageLocation.PRIMARY_1, StorageLocation.PRIMARY_2)
    )

    // Check if the folder exists
    val docFile = DocumentFile.fromTreeUri(context, treeUri)
    if (null == docFile || !docFile.exists()) {
        Timber.e("Could not find the selected file %s", treeUri.toString())
        return Pair(ProcessFolderResult.KO_INVALID_FOLDER, treeUri.toString())
    }

    // Check if selected folder is separate from one of Hentoid's primary locations
    var primaryUri1 = Preferences.getStorageUri(StorageLocation.PRIMARY_1)
    var primaryUri2 = Preferences.getStorageUri(StorageLocation.PRIMARY_2)
    if (primaryUri1.isNotEmpty()) primaryUri1 =
        getFullPathFromUri(context, Uri.parse(primaryUri1))
    if (primaryUri2.isNotEmpty()) primaryUri2 =
        getFullPathFromUri(context, Uri.parse(primaryUri2))
    val selectedFullPath = getFullPathFromUri(context, treeUri)
    if (primaryUri1.isNotEmpty() && selectedFullPath.startsWith(primaryUri1)
        || primaryUri2.isNotEmpty() && selectedFullPath.startsWith(primaryUri2)
    ) {
        Timber.w(
            "Trying to set the external library inside a primary library location %s",
            treeUri.toString()
        )
        return Pair(ProcessFolderResult.KO_PRIMARY_EXTERNAL, treeUri.toString())
    }
    if (primaryUri1.isNotEmpty() && primaryUri1.startsWith(selectedFullPath)
        || primaryUri2.isNotEmpty() && primaryUri2.startsWith(selectedFullPath)
    ) {
        Timber.w(
            "Trying to set the external library over a primary library location %s",
            treeUri.toString()
        )
        return Pair(ProcessFolderResult.KO_PRIMARY_EXTERNAL, treeUri.toString())
    }

    // Set the folder as the app's external library folder
    val folderUri = docFile.uri.toString()
    Preferences.setExternalLibraryUri(folderUri)

    // Start the import
    return if (runExternalImport(context)) Pair(ProcessFolderResult.OK_LIBRARY_DETECTED, folderUri)
    else Pair(ProcessFolderResult.KO_ALREADY_RUNNING, folderUri)
}

/**
 * Persist I/O credentials for the given location, keeping the given existing credentials
 *
 * @param context  Context to use
 * @param treeUri  Uri to add credentials for
 * @param location Locations to keep credentials for
 */
fun persistLocationCredentials(
    context: Context,
    treeUri: Uri,
    location: List<StorageLocation>
) {
    val uri = location
        .mapNotNull { l -> Preferences.getStorageUri(l) }
        .filterNot { obj -> obj.isEmpty() }
        .map { uri -> Uri.parse(uri) }
    persistNewUriPermission(context, treeUri, uri)
}

/**
 * Show the dialog to ask the user if he wants to import existing books
 *
 * @param context        Context to be used
 * @param location       Location we're working on
 * @param rootUri        Uri of the selected folder
 * @param cancelCallback Callback to run when the dialog is canceled
 */
fun showExistingLibraryDialog(
    context: Context,
    location: StorageLocation,
    rootUri: String,
    cancelCallback: Runnable?
) {
    MaterialAlertDialogBuilder(
        context,
        context.getIdForCurrentTheme(R.style.Theme_Light_Dialog)
    )
        .setIcon(R.drawable.ic_warning)
        .setCancelable(false)
        .setTitle(R.string.app_name)
        .setMessage(R.string.contents_detected)
        .setPositiveButton(
            R.string.yes
        ) { dialog1, _ ->
            dialog1.dismiss()
            runPrimaryImport(context, location, rootUri, null)
        }
        .setNegativeButton(
            R.string.no
        ) { dialog2, _ ->
            dialog2.dismiss()
            cancelCallback?.run()
        }
        .create()
        .show()
}

/**
 * Detect whether the given folder contains books or not
 * by counting the elements inside each site's download folder (but not its subfolders)
 *
 *
 * NB : this method works approximately because it doesn't try to count JSON files
 * However, findFilesRecursively -the method used by ImportService- is too slow on certain phones
 * and might cause freezes -> we stick to that approximate method for ImportActivity
 *
 * @param context Context to be used
 * @param folder  Folder to examine
 * @return True if the current Hentoid folder contains at least one book; false if not
 */
private fun hasBooks(context: Context, folder: DocumentFile): Boolean {
    try {
        FileExplorer(context, folder.uri).use { explorer ->
            val folders = explorer.listFolders(context, folder)

            // Filter out download subfolders among listed subfolders
            for (subfolder in folders) {
                val subfolderName = subfolder.name
                if (subfolderName != null) {
                    for (s in Site.entries)
                        if (subfolderName.equals(s.folder, ignoreCase = true)) {
                            // Search subfolders within identified download folders
                            // NB : for performance issues, we assume the mere presence of a subfolder inside a download folder means there's an existing book
                            if (explorer.hasFolders(subfolder)) return true
                            break
                        }
                }
            }
        }
    } catch (e: IOException) {
        Timber.w(e)
    }
    return false
}

/**
 * Detect or create the Hentoid app folder inside the given base folder
 *
 * @param context    Context to be used
 * @param baseFolder Root folder to search for or create the Hentoid folder
 * @return DocumentFile representing the found or newly created Hentoid folder
 */
private fun getOrCreateHentoidFolder(
    context: Context,
    baseFolder: DocumentFile
): DocumentFile? {
    val targetFolder = getExistingHentoidDirFrom(context, baseFolder)
    return targetFolder ?: baseFolder.createDirectory(DEFAULT_PRIMARY_FOLDER)
}

/**
 * Try and detect if the Hentoid primary folder is, or is inside the given folder
 *
 * @param context Context to use
 * @param root    Folder to search the Hentoid folder in
 * @return Detected Hentoid folder; null if nothing detected
 */
fun getExistingHentoidDirFrom(context: Context, root: DocumentFile): DocumentFile? {
    if (!root.exists() || !root.isDirectory || null == root.name) return null

    // Selected folder _is_ the Hentoid folder
    if (isHentoidFolderName(root.name!!)) return root

    // If not, look for it in its children
    val hentoidDirs = listFoldersFilter(context, root, hentoidFolderNames)
    return if (hentoidDirs.isNotEmpty()) hentoidDirs[0] else null
}

/**
 * Run the import of the Hentoid primary library
 *
 * @param context Context to use
 * @param options Import options to use
 */
private fun runPrimaryImport(
    context: Context,
    location: StorageLocation,
    targetRoot: String,
    options: ImportOptions?
) {
    ImportNotificationChannel.init(context)
    val builder = PrimaryImportData.Builder()
    builder.setLocation(location)
    builder.setTargetRoot(targetRoot)
    if (options != null) {
        builder.setRefreshRename(options.rename)
        builder.setRefreshRemovePlaceholders(options.removePlaceholders)
        builder.setRenumberPages(options.renumberPages)
        builder.setRefreshCleanNoJson(options.cleanNoJson)
        builder.setRefreshCleanNoImages(options.cleanNoImages)
        builder.setImportGroups(options.importGroups)
    }
    val workManager = WorkManager.getInstance(context)
    workManager.enqueueUniqueWork(
        R.id.import_service.toString(), ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequest.Builder(PrimaryImportWorker::class.java).setInputData(builder.data)
            .addTag(WORK_CLOSEABLE).build()
    )
}

/**
 * Run the import of the Hentoid external library
 *
 * @param context Context to use
 */
private fun runExternalImport(
    context: Context
): Boolean {
    if (ExternalImportWorker.isRunning(context)) return false
    ImportNotificationChannel.init(context)
    val workManager = WorkManager.getInstance(context)
    workManager.enqueueUniqueWork(
        R.id.external_import_service.toString(), ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequest.Builder(ExternalImportWorker::class.java).addTag(WORK_CLOSEABLE).build()
    )
    return true
}

/**
 * Create a Content from the given folder
 *
 * @param context      Context to use
 * @param bookFolder   Folder to analyze
 * @param explorer     FileExplorer to use
 * @param parentNames  Names of parent folders, for formatting purposes; last of the list is the immediate parent of bookFolder
 * @param targetStatus Target status of the Content to create
 * @param dao          CollectionDAO to use
 * @param imageFiles   List of images to match files with; null if they have to be recreated from the files
 * @param jsonFile     JSON file to use, if one has been detected upstream; null if it has to be detected
 * @return Content created from the folder information and files
 */
fun scanBookFolder(
    context: Context,
    bookFolder: DocumentFile,
    explorer: FileExplorer,
    parentNames: List<String>,
    targetStatus: StatusContent,
    dao: CollectionDAO,
    imageFiles: List<DocumentFile>?,
    jsonFile: DocumentFile?
): Content {
    Timber.d(">>>> scan book folder %s", bookFolder.uri)
    var result: Content? = null
    if (jsonFile != null) {
        try {
            val content = JsonHelper.jsonToObject(
                context, jsonFile,
                JsonContent::class.java
            )
            result = content.toEntity(dao)
            result.jsonUri = jsonFile.uri.toString()
        } catch (e: IOException) {
            Timber.w(e)
        } catch (e: JsonDataException) {
            Timber.w(e)
        }
    }
    if (null == result) {
        var title = cleanTitle(bookFolder.name)
        // Tachiyomi downloads - include parent folder name as title
        if (title.lowercase(Locale.getDefault())
                .startsWith("chapter") && parentNames.isNotEmpty()
        ) {
            // Single chapter
            title =
                if ("chapter".equals(title, ignoreCase = true))
                    cleanTitle(parentNames[parentNames.size - 1]) else  // Multiple chapters
                    cleanTitle(parentNames[parentNames.size - 1]) + " " + title
        }
        result = Content().setTitle(title)
        var site: Site? = Site.NONE
        if (parentNames.isNotEmpty()) {
            for (parent in parentNames)
                for (s in Site.entries)
                    if (parent.equals(s.folder, ignoreCase = true)
                    ) {
                        site = s
                        break
                    }
        }
        result.setSite(site)
        result.setDownloadDate(bookFolder.lastModified())
        result.addAttributes(parentNamesAsTags(parentNames))
    }
    if (targetStatus == StatusContent.EXTERNAL) result!!.addAttributes(newExternalAttribute())
    result!!.setStatus(targetStatus).setStorageUri(bookFolder.uri.toString())
    if (0L == result.downloadDate) result.setDownloadDate(Instant.now().toEpochMilli())
    result.lastEditDate = Instant.now().toEpochMilli()
    val images: MutableList<ImageFile> = ArrayList()
    scanFolderImages(context, bookFolder, explorer, targetStatus, false, images, imageFiles)

    // Detect cover
    val coverExists = images.any { obj: ImageFile -> obj.isCover }
    if (!coverExists) createCover(images)

    // If streamed, keep everything and update cover URI
    if (result.downloadMode == Content.DownloadMode.STREAM) {
        val coverFile = images.firstOrNull { obj: ImageFile -> obj.isCover }
        if (coverFile != null) result.getCover().setFileUri(coverFile.fileUri)
            .setSize(coverFile.size)
    } else { // Set all detected images
        result.setImageFiles(images)
    }
    if (0 == result.qtyPages) {
        val countUnreadable = images.filterNot { obj: ImageFile -> obj.isReadable }.count()
        result.setQtyPages(images.size - countUnreadable) // Minus unreadable pages (cover thumb)
    }
    result.computeSize()
    return result
}

private fun cleanTitle(s: String?): String {
    var result = StringHelper.protect(s)
    result = result.replace("_", " ")
    // Remove expressions between []'s
    result = result.replace("\\[[^(\\[\\])]*\\]".toRegex(), "")
    return result.trim { it <= ' ' }
}

/**
 * Create a Content from the given parent folder and chapter subfolders, merging all "chapters" into one content
 *
 * @param context        Context to use
 * @param parent         Parent folder to take into account for title and download date
 * @param chapterFolders Folders containing chapters to scan for images
 * @param explorer       FileExplorer to use
 * @param parentNames    Names of parent folders, for formatting purposes; last of the list is the immediate parent of parent
 * @param dao            CollectionDAO to use
 * @param jsonFile       JSON file to use, if one has been detected upstream; null if it needs to be detected
 * @return Content created from the folder information, subfolders and files
 */
fun scanChapterFolders(
    context: Context,
    parent: DocumentFile,
    chapterFolders: List<DocumentFile>,
    explorer: FileExplorer,
    parentNames: List<String>,
    dao: CollectionDAO,
    jsonFile: DocumentFile?
): Content {
    Timber.d(">>>> scan chapter folder %s", parent.uri)
    var result: Content? = null
    if (jsonFile != null) {
        try {
            val content = JsonHelper.jsonToObject(
                context, jsonFile,
                JsonContent::class.java
            )
            result = content.toEntity(dao)
            result.jsonUri = jsonFile.uri.toString()
        } catch (e: IOException) {
            Timber.w(e)
        } catch (e: JsonDataException) {
            Timber.w(e)
        }
    }
    if (null == result) {
        result =
            Content().setSite(Site.NONE)
                .setTitle(if (null == parent.name) "" else parent.name)
                .setUrl("")
        result.setDownloadDate(parent.lastModified())
        result.addAttributes(parentNamesAsTags(parentNames))
    }
    result!!.addAttributes(newExternalAttribute())
    result.setStatus(StatusContent.EXTERNAL).setStorageUri(parent.uri.toString())
    if (0L == result.downloadDate) result.setDownloadDate(Instant.now().toEpochMilli())
    result.lastEditDate = Instant.now().toEpochMilli()
    val images: MutableList<ImageFile> = ArrayList()
    // Scan pages across all subfolders
    for (chapterFolder in chapterFolders) scanFolderImages(
        context,
        chapterFolder,
        explorer,
        StatusContent.EXTERNAL,
        true,
        images,
        null
    )
    val coverExists = images.any { obj: ImageFile -> obj.isCover }
    if (!coverExists) createCover(images)
    result.setImageFiles(images)
    if (0 == result.qtyPages) {
        val countUnreadable = images.filterNot { obj: ImageFile -> obj.isReadable }.count()
        result.setQtyPages(images.size - countUnreadable) // Minus unreadable pages (cover thumb)
    }
    result.computeSize()
    return result
}

/**
 * Populate on enrich the given image list according to the contents of the given folder
 *
 * @param context                Context to use
 * @param bookFolder             Folder to scan image files from
 * @param explorer               FileExplorer to use
 * @param targetStatus           Target status of the detected images
 * @param addFolderNametoImgName True if the parent folder name has to be added to detected images name
 * @param images                 Image list to populate or enrich
 * @param imgs             Image file list, if already listed upstream; null if it needs to be listed
 */
private fun scanFolderImages(
    context: Context,
    bookFolder: DocumentFile,
    explorer: FileExplorer,
    targetStatus: StatusContent,
    addFolderNametoImgName: Boolean,
    images: MutableList<ImageFile>,
    imgs: List<DocumentFile>?
) {
    var imageFiles = imgs
    val order = images.maxOfOrNull { i -> i.order } ?: 0
    val folderName = if (null == bookFolder.name) "" else bookFolder.name!!
    if (null == imageFiles) imageFiles =
        explorer.listFiles(context, bookFolder, imageNamesFilter)
    var namePrefix = ""
    if (addFolderNametoImgName) namePrefix = "$folderName-"
    images.addAll(
        ContentHelper.createImageListFromFiles(
            imageFiles, targetStatus, order,
            namePrefix
        )
    )
}

/**
 * Create a cover and add it to the given image list
 *
 * @param images Image list to generate the cover from (and add it to)
 */
fun createCover(images: MutableList<ImageFile>) {
    if (images.isNotEmpty()) {
        // Create a new cover entry from the 1st element
        images.add(0, ImageFile(images[0]).setIsCover(true))
    }
}

/**
 * Return a list with the attribute flagging a book as external
 *
 * @return List with the attribute flagging a book as external
 */
private fun newExternalAttribute(): List<Attribute> {
    return listOf(
        Attribute(
            AttributeType.TAG,
            EXTERNAL_LIB_TAG,
            EXTERNAL_LIB_TAG,
            Site.NONE
        )
    )
}

/**
 * Remove the attribute flagging the given book as external, if it exists
 *
 * @param content Content to remove the "external" attribute flag, if it has been set
 */
fun removeExternalAttributes(content: Content) {
    content.putAttributes(content.attributes.filterNot { a ->
        a.name.equals(EXTERNAL_LIB_TAG, ignoreCase = true)
    }.toList())
    if (content.status == StatusContent.EXTERNAL) content.setStatus(StatusContent.DOWNLOADED)
}

/**
 * Convert the given list of parent folder names into a list of Attribute of type TAG
 *
 * @param parentNames List of parent folder names
 * @return Representation of parent folder names as Attributes of type TAG
 */
private fun parentNamesAsTags(parentNames: List<String>): AttributeMap {
    val result = AttributeMap()
    // Don't include the very first one, it's the name of the root folder of the library
    if (parentNames.size > 1) {
        for (i in 1 until parentNames.size) result.add(
            Attribute(
                AttributeType.TAG,
                parentNames[i], parentNames[i], Site.NONE
            )
        )
    }
    return result
}

/**
 * Create Content from every archive inside the given subfolders
 *
 * @param context     Context to use
 * @param subFolders  Subfolders to scan for archives
 * @param explorer    FileExplorer to use
 * @param parentNames Names of parent folders, for formatting purposes; last of the list is the immediate parent of the scanned folders
 * @param dao         CollectionDAO to use
 * @param chaptered   True to create one single book containing the 1st archive of each subfolder as chapters; false to create one book per archive
 * @return List of Content created from every archive inside the given subfolders
 */
fun scanForArchives(
    context: Context,
    parent: DocumentFile,
    subFolders: List<DocumentFile>,
    explorer: FileExplorer,
    parentNames: List<String>,
    dao: CollectionDAO,
    chaptered: Boolean = false
): List<Content> {
    val result: MutableList<Content> = ArrayList()
    for (subfolder in subFolders) {
        val files = explorer.listFiles(context, subfolder, null)
        val archives: MutableList<DocumentFile> = ArrayList()
        val jsons: MutableList<DocumentFile> = ArrayList()

        // Look for the interesting stuff
        for (file in files) {
            val fileName = file.name ?: ""
            if (getArchiveNamesFilter().accept(fileName)) archives.add(file)
            else if (JsonHelper.getJsonNamesFilter().accept(fileName)) jsons.add(file)
        }
        for (archive in archives) {
            val json = getFileWithName(jsons, archive.name)
            val c = scanArchive(
                context,
                subfolder,
                archive,
                parentNames,
                StatusContent.EXTERNAL,
                dao,
                json
            )
            if (0 == c.first) {
                result.add(c.second!!)
                if (chaptered) break // Just read the 1st archive of any subfolder
            }
        }
    }
    if (chaptered) { // Return one single book with all results as chapters
        val content =
            Content().setSite(Site.NONE)
                .setTitle(parentNames.lastOrNull() ?: "")
                .setUrl("")
        content.setDownloadDate(
            subFolders.lastOrNull()?.lastModified() ?: Instant.now().toEpochMilli()
        )
        content.addAttributes(parentNamesAsTags(parentNames))

        content.addAttributes(newExternalAttribute())
        content.setStatus(StatusContent.EXTERNAL).setStorageUri(parent.uri.toString())
        if (0L == content.downloadDate) content.setDownloadDate(Instant.now().toEpochMilli())
        content.lastEditDate = Instant.now().toEpochMilli()

        val chapterStr = context.getString(R.string.gallery_chapter_prefix)
        var chapterOffset = 0
        val chapters: MutableList<Chapter> = ArrayList()
        val images: MutableList<ImageFile> = ArrayList()
        result.forEachIndexed { cidx, c ->
            val chapter = Chapter(cidx + 1, c.archiveLocationUri, chapterStr + " " + (cidx + 1))
            chapter.setContent(content)
            chapter.setImageFiles(c.imageList.filter { i -> i.isReadable })
            chapter.imageFiles?.forEachIndexed { iidx, img ->
                img.setOrder(chapterOffset + iidx + 1)
                img.computeName(5)
                img.setChapter(chapter)
            }
            chapters.add(chapter)
            chapter.imageFiles?.let {
                images.addAll(it)
                chapterOffset += it.size
            }
        }
        val coverExists = images.any { i -> i.isCover }
        if (!coverExists) createCover(images)
        content.setImageFiles(images)
        if (0 == content.qtyPages) {
            val countUnreadable = images.filterNot { obj: ImageFile -> obj.isReadable }.count()
            content.setQtyPages(images.size - countUnreadable) // Minus unreadable pages (cover thumb)
        }
        content.setChapters(chapters)
        content.computeSize()
        return listOf(content)
    } else return result
}

/**
 * Create a content from the given archive
 * NB : any returned Content with the IGNORED status shouldn't be taken into account by the caller
 *
 * @param context      Context to use
 * @param parentFolder Parent folder where the archive is located
 * @param archive      Archive file to scan
 * @param parentNames  Names of parent folders, for formatting purposes; last of the list is the immediate parent of parentFolder
 * @param targetStatus Target status of the Content to create
 * @param dao          CollectionDAO to use
 * @param jsonFile     JSON file to use, if one has been detected upstream; null if it has to be detected
 * @return Pair containing
 *  Key : Return code
 *      0 = success
 *      1 = failure; file just contains other archives
 *      2 = failure; file doesn't contain supported images or is corrupted
 *  Value : Content created from the given archive, ur null if return code > 0
 */
fun scanArchive(
    context: Context,
    parentFolder: DocumentFile,
    archive: DocumentFile,
    parentNames: List<String>,
    targetStatus: StatusContent,
    dao: CollectionDAO,
    jsonFile: DocumentFile?
): Pair<Int, Content?> {
    var result: Content? = null
    if (jsonFile != null) {
        try {
            val content = JsonHelper.jsonToObject(
                context, jsonFile,
                JsonContent::class.java
            )
            result = content.toEntity(dao)
            result.jsonUri = jsonFile.uri.toString()
        } catch (e: IOException) {
            Timber.w(e)
        } catch (e: JsonDataException) {
            Timber.w(e)
        }
    }

    var entries = emptyList<ArchiveEntry>()
    try {
        entries = context.getArchiveEntries(archive)
    } catch (e: Exception) {
        Timber.w(e)
    }

    val archiveEntries = entries.filter { (path) -> isSupportedArchive(path) }
    val imageEntries = entries.filter { (path) -> isSupportedImage(path) }
        .filter { (_, size): ArchiveEntry -> size > 0 }

    if (imageEntries.isEmpty()) {
        // If it just contains other archives, raise an error
        if (archiveEntries.isNotEmpty()) return Pair(1, null)
        // If it contains no supported images, raise an error
        return Pair(2, null)
    }

    val images = ContentHelper.createImageListFromArchiveEntries(
        archive.uri,
        imageEntries,
        targetStatus,
        0,
        ""
    )
    val coverExists = images.any { obj: ImageFile -> obj.isCover }
    if (!coverExists) createCover(images)

    // Create content envelope
    if (null == result) {
        result = Content().setSite(Site.NONE).setTitle(
            if (null == archive.name) ""
            else getFileNameWithoutExtension(archive.name!!)
        ).setUrl("")
        result.setDownloadDate(archive.lastModified())
        result.addAttributes(parentNamesAsTags(parentNames))
        result.addAttributes(newExternalAttribute())
    }
    result!!.setStatus(targetStatus)
        .setStorageUri(archive.uri.toString()) // Here storage URI is a file URI, not a folder
    if (0L == result.downloadDate) result.setDownloadDate(Instant.now().toEpochMilli())
    result.lastEditDate = Instant.now().toEpochMilli()
    result.archiveLocationUri = parentFolder.uri.toString()
    result.setImageFiles(images)
    if (0 == result.qtyPages) {
        val countUnreadable = images.filterNot { obj: ImageFile -> obj.isReadable }.count()
        result.setQtyPages(images.size - countUnreadable) // Minus unreadable pages (cover thumb)
    }
    result.computeSize()
    // e.g. when the ZIP table doesn't contain any size entry
    if (result.size <= 0) result.forceSize(archive.length())
    return Pair(0, result)
}

/**
 * Add the given list of bookmarks to the DB, handling duplicates
 * Bookmarks that have the same URL as existing ones won't be imported
 *
 * @param dao       CollectionDAO to use
 * @param bookmarks List of bookmarks to add to the existing bookmarks
 * @return Quantity of new integrated bookmarks
 */
fun importBookmarks(dao: CollectionDAO, bookmarks: List<SiteBookmark>): Int {
    // Don't import bookmarks that have the same URL as existing ones
    val existingBookmarkUrls: Set<SiteBookmark> = HashSet(dao.selectAllBookmarks())
    val bookmarksToImport = HashSet(bookmarks).filterNot { o: SiteBookmark ->
        existingBookmarkUrls.contains(
            o
        )
    }.toList()
    dao.insertBookmarks(bookmarksToImport)
    return bookmarksToImport.size
}

/**
 * Add the given list of renaming rules to the DB, handling duplicates
 * Rules that have the same attribute type, source and target string as existing ones won't be imported
 *
 * @param dao   CollectionDAO to use
 * @param rules List of rules to add to the existing rules
 */
fun importRenamingRules(dao: CollectionDAO, rules: List<RenamingRule>) {
    val existingRules = HashSet(dao.selectRenamingRules(AttributeType.UNDEFINED, null))
    val rulesToImport = HashSet(rules).filterNot { o: RenamingRule ->
        existingRules.contains(o)
    }.toList()
    dao.insertRenamingRules(rulesToImport)
}

/**
 * Return the first file with the given name (without extension) among the given list of files
 *
 * @param files List of files to search into
 * @param name  File name to detect
 * @return First file with the given name among the given list, or null if none matches the given name
 */
fun getFileWithName(files: List<DocumentFile>, name: String?): DocumentFile? {
    if (null == name) return null
    val targetBareName = getFileNameWithoutExtension(name)
    val file = files.firstOrNull { f ->
        f.name != null && getFileNameWithoutExtension(f.name!!)
            .equals(targetBareName, ignoreCase = true)
    }
    return file
}

/**
 * Build a [NameFilter] only accepting Content json files
 *
 * @return [NameFilter] only accepting Content json files
 */
fun getContentJsonNamesFilter(): NameFilter {
    return hentoidContentJson
}