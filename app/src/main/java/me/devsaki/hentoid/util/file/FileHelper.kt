package me.devsaki.hentoid.util.file

import android.annotation.TargetApi
import android.app.usage.StorageStatsManager
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.system.Os
import android.text.TextUtils
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.copy
import me.devsaki.hentoid.util.formatEpochToDate
import me.devsaki.hentoid.util.hash64
import me.devsaki.hentoid.util.toast
import me.devsaki.hentoid.util.toastLong
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.math.BigInteger
import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.Instant
import java.util.Locale
import kotlin.math.min

val decimalFormat = NumberFormat.getInstance(Locale.getDefault()) as DecimalFormat
val decimalSeparator = decimalFormat.decimalFormatSymbols.decimalSeparator

const val AUTHORITY = BuildConfig.APPLICATION_ID + ".provider.FileProvider"

private const val PRIMARY_VOLUME_NAME = "primary" // DocumentsContract.PRIMARY_VOLUME_NAME
private const val NOMEDIA_FILE_NAME = ".nomedia"
private const val TEST_FILE_NAME = "delete.me"
const val DEFAULT_MIME_TYPE = "application/octet-steam"

// https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/os/FileUtils.java;l=972?q=isValidFatFilenameChar
private const val ILLEGAL_FILENAME_CHARS = "[\"*/:<>\\?\\\\|]"

const val URI_ELEMENTS_SEPARATOR = "%3A"

const val FILE_IO_BUFFER_SIZE = 32 * 1024


/**
 * Build a DocumentFile representing a file from the given Uri string
 *
 * @param context Context to use for the conversion
 * @param uriStr  Uri string to use
 * @return DocumentFile built from the given Uri string; null if the DocumentFile couldn't be built
 */
fun getFileFromSingleUriString(context: Context, uriStr: String): DocumentFile? {
    if (uriStr.isEmpty()) return null
    val result = DocumentFile.fromSingleUri(context, Uri.parse(uriStr))
    return if (null == result || !result.exists()) null
    else result
}

/**
 * Build a DocumentFile from the given Uri string
 *
 * @param context    Context to use for the conversion
 * @param treeUriStr Uri string to use
 * @return DocumentFile built from the given Uri string; null if the DocumentFile couldn't be built
 */
fun getDocumentFromTreeUriString(context: Context, treeUriStr: String): DocumentFile? {
    if (treeUriStr.isEmpty()) return null
    val result = DocumentFile.fromTreeUri(context, Uri.parse(treeUriStr))
    return if (null == result || !result.exists()) null
    else result
}

fun getFullPathFromUri(context: Context, uri: Uri): String {
    return if (ContentResolver.SCHEME_FILE == uri.scheme) {
        uri.path ?: ""
    } else {
        getFullPathFromTreeUri(context, uri)
    }
}

/**
 * Get the full, human-readable access path from the given Uri
 *
 *
 * Credits go to https://stackoverflow.com/questions/34927748/android-5-0-documentfile-from-tree-uri/36162691#36162691
 *
 * @param context Context to use for the conversion
 * @param uri     Uri to get the full path from
 * @return Full, human-readable access path from the given Uri
 */
private fun getFullPathFromTreeUri(context: Context, uri: Uri): String {
    if (uri.toString().isEmpty()) return ""

    var volumePath = getVolumePath(context, getVolumeIdFromUri(uri)) ?: "UnknownVolume"
    if (volumePath.endsWith(File.separator))
        volumePath = volumePath.substring(0, volumePath.length - 1)

    var documentPath = getDocumentPathFromUri(uri) ?: ""
    if (documentPath.endsWith(File.separator))
        documentPath = documentPath.substring(0, documentPath.length - 1)

    return if (documentPath.isNotEmpty()) {
        if (documentPath.startsWith(File.separator)) volumePath + documentPath
        else volumePath + File.separator + documentPath
    } else volumePath
}

/**
 * Get the human-readable access path for the given volume ID
 *
 * @param context  Context to use
 * @param volumeId Volume ID to get the path from
 * @return Human-readable access path of the given volume ID
 */
@Suppress("UNCHECKED_CAST")
private fun getVolumePath(context: Context, volumeId: String): String? {
    try {
        // StorageVolume exists since API19, has an uiid since API21 but is only visible since API24
        val mStorageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val storageVolumeClazz = Class.forName("android.os.storage.StorageVolume")
        val getVolumeList = mStorageManager.javaClass.getMethod("getVolumeList")
        val getUuid = storageVolumeClazz.getMethod("getUuid")
        val isPrimary = storageVolumeClazz.getMethod("isPrimary")

        var result: List<StorageVolume>
        val resTmp = getVolumeList.invoke(mStorageManager)
        result = if (null == resTmp) emptyList()
        else listOf(*resTmp as Array<StorageVolume>)

        // getRecentStorageVolumes (API30+) can detect USB storage on certain devices where getVolumeList can't
        if (Build.VERSION.SDK_INT >= 30) {
            val getRecentVolumeList = mStorageManager.javaClass.getMethod("getRecentStorageVolumes")
            val rvlResult: List<StorageVolume> =
                getRecentVolumeList.invoke(mStorageManager) as ArrayList<StorageVolume>

            result = if ((result.size > rvlResult.size)) result else rvlResult
        }

        for (volume in result) {
            val uuid = (getUuid.invoke(volume) as String?) ?: ""
            val primary = isPrimary.invoke(volume) as Boolean

            if (volumeIdMatch(uuid, primary, volumeId)) return getVolumePath(volume)
        }
        // not found.
        return null
    } catch (e: Exception) {
        Timber.w(e)
        return null
    }
}

/**
 * Returns the human-readable access path of the root of the given storage volume
 *
 * @param storageVolume android.os.storage.StorageVolume to return the path from
 * @return Human-readable access path of the root of the given storage volume; empty string if not found
 */
// Access to getPathFile is limited to API<30
private fun getVolumePath(storageVolume: Any): String {
    var path = ""
    var absolutePath = ""
    var canonicalPath = ""
    try {
        val pathFile: File?
        val storageVolumeClazz = Class.forName("android.os.storage.StorageVolume")
        if (Build.VERSION.SDK_INT < 30) {
            val getPathFile = storageVolumeClazz.getMethod("getPathFile") // Removed in API30
            pathFile = getPathFile.invoke(storageVolume) as File
        } else {
            val getDirectory = storageVolumeClazz.getMethod("getDirectory")
            pathFile = getDirectory.invoke(storageVolume) as File?
        }

        if (pathFile != null) {
            path = pathFile.path
            absolutePath = pathFile.absolutePath
            canonicalPath = pathFile.canonicalPath
        }
    } catch (e: Exception) {
        Timber.w(e)
    }
    if (path.isEmpty() && absolutePath.isEmpty()) return canonicalPath
    if (path.isEmpty()) return absolutePath
    return path
}

/**
 * Get the volume ID of the given Uri
 *
 * @param uri Uri to get the volume ID for
 * @return Volume ID of the given Uri
 */
private fun getVolumeIdFromUri(uri: Uri): String {
    val docId = try {
        DocumentsContract.getDocumentId(uri)
    } catch (e: IllegalArgumentException) {
        DocumentsContract.getTreeDocumentId(uri)
    }

    val split = docId.split(":")
    return if (split.isNotEmpty()) split[0]
    else ""
}

/**
 * Get the human-readable document path of the given Uri
 *
 * @param uri Uri to get the path for
 * @return Human-readable document path of the given Uri
 */
private fun getDocumentPathFromUri(uri: Uri): String? {
    val docId = try {
        DocumentsContract.getDocumentId(uri)
    } catch (e: IllegalArgumentException) {
        DocumentsContract.getTreeDocumentId(uri)
    }

    val split = docId.split(":")
    return if (split.size >= 2) split[1]
    else docId
}

/**
 * Ensure file creation from stream.
 *
 * @param stream - OutputStream
 * @return true if all OK.
 */
fun syncStream(stream: FileOutputStream): Boolean {
    try {
        stream.fd.sync()
        return true
    } catch (e: IOException) {
        Timber.e(e, "IO Error")
    }

    return false
}

/**
 * Create an OutputStream opened the given file
 * NB1 : File length will be truncated to the length of the written data
 * NB2 : Code initially from org.apache.commons.io.FileUtils
 *
 * @param file File to open the OutputStream on
 * @return New OutputStream opened on the given file
 */
@Throws(IOException::class)
fun getOutputStream(file: File): OutputStream {
    if (file.exists()) {
        if (!file.isFile) throw IOException(file.path + " is not a File")
        if (!file.canWrite()) throw IOException(file.path + " can't be written to")
    } else {
        file.parentFile?.let { dir ->
            if ((!dir.mkdirs() && !dir.isDirectory())) {
                throw IOException("Cannot create directory '$dir'.")
            }
        }
    }
    return FileOutputStream(file, false)
}

/**
 * Create an OutputStream for the given file
 * NB : File length will be truncated to the length of the written data
 *
 * @param context Context to use
 * @param target  File to open the OutputStream on
 * @return New OutputStream opened on the given file
 * @throws IOException In case something horrible happens during I/O
 */
@Throws(IOException::class)
fun getOutputStream(context: Context, target: DocumentFile): OutputStream? {
    return context.contentResolver.openOutputStream(
        target.uri,
        "rwt"
    ) // Always truncate file to whatever data needs to be written
}

/**
 * Create an OutputStream for the file at the given Uri
 * NB : File length will be truncated to the length of the written data
 *
 * @param context Context to use
 * @param fileUri Uri of the file to open the OutputStream on
 * @return New OutputStream opened on the given file
 * @throws IOException In case something horrible happens during I/O
 */
@Throws(IOException::class)
fun getOutputStream(context: Context, fileUri: Uri): OutputStream? {
    if (ContentResolver.SCHEME_FILE == fileUri.scheme) {
        val path = fileUri.path
        if (null != path) return getOutputStream(File(path))
    } else {
        val doc = getFileFromSingleUriString(context, fileUri.toString())
        if (doc != null) return getOutputStream(context, doc)
    }
    throw IOException("Couldn't find document for Uri : $fileUri")
}

/**
 * Create an InputStream opened the given file
 *
 * @param context Context to use
 * @param target  File to open the InputStream on
 * @return New InputStream opened on the given file
 * @throws IOException In case something horrible happens during I/O
 */
@Throws(IOException::class)
fun getInputStream(context: Context, target: DocumentFile): InputStream {
    return getInputStream(context, target.uri)
}

/**
 * Create an InputStream opened the file at the given Uri
 *
 * @param context Context to use
 * @param fileUri Uri to open the InputStream on
 * @return New InputStream opened on the given file
 * @throws IOException In case something horrible happens during I/O
 */
@Throws(IOException::class, IllegalArgumentException::class)
fun getInputStream(context: Context, fileUri: Uri): InputStream {
    return context.contentResolver.openInputStream(fileUri)
        ?: throw IOException("Input stream not found for $fileUri")
}

/**
 * Delete the given file
 *
 * @param file The file to be deleted.
 * @return true if successfully deleted or if the file does not exist.
 */
fun removeFile(file: File): Boolean {
    return !file.exists() || deleteQuietly(file)
}

/**
 * Delete the file represented by the given Uri
 *
 * @param context Context to be used
 * @param fileUri Uri to the file to delete
 */
fun removeFile(context: Context, fileUri: Uri) {
    if (ContentResolver.SCHEME_FILE == fileUri.scheme) {
        val path = fileUri.path
        if (null != path) removeFile(File(path))
    } else {
        val doc = getFileFromSingleUriString(context, fileUri.toString())
        doc?.delete()
    }
}

/**
 * Return the DocumentFile with the given display name located in the given folder
 * If it doesn't exist, create a new one and return it
 *
 * @param context     Context to use
 * @param folder      Containing folder
 * @param mimeType    Mime-type to use if the document has to be created
 * @param displayName Display name of the document
 * @return Usable DocumentFile; null if creation failed
 */
fun findOrCreateDocumentFile(
    context: Context,
    folder: DocumentFile,
    mimeType: String?,
    displayName: String
): DocumentFile? {
    // Look for it first
    val file = findFile(context, folder, displayName)
    if (null == file) { // Create it
        val localMime = if (mimeType.isNullOrEmpty()) DEFAULT_MIME_TYPE else mimeType
        return folder.createFile(localMime, displayName)
    } else return file
}

/**
 * Try to create the .nomedia file inside the given folder
 *
 * @param context Context to use
 * @param folder  Folder to create the file into
 * @return 0 if the given folder is valid and has been set; -1 if the given folder is invalid; -2 if write credentials are insufficient
 */
fun createNoMedia(context: Context, folder: DocumentFile): Int {
    // Validate folder
    if (!folder.exists() && !folder.isDirectory) return -1

    // Make sure the nomedia file is created
    var nomedia = findFile(context, folder, NOMEDIA_FILE_NAME)
    if (null == nomedia) {
        nomedia = folder.createFile(DEFAULT_MIME_TYPE, NOMEDIA_FILE_NAME)
        if (null == nomedia || !nomedia.exists()) return -3
    }

    // Remove and add back a test file to test if the user has the I/O rights to the selected folder
    val testFile = findOrCreateDocumentFile(context, folder, DEFAULT_MIME_TYPE, TEST_FILE_NAME)
        ?: return -3
    if (!testFile.delete()) return -2

    return 0
}


/**
 * Find the folder inside the given parent folder (non recursive) that has the given name
 *
 * @param context       Context to use
 * @param parent        Parent folder of the folder to find
 * @param subfolderName Name of the folder to find
 * @return Folder inside the given parent folder (non recursive) that has the given name; null if not found
 */
fun findFolder(context: Context, parent: DocumentFile, subfolderName: String): DocumentFile? {
    return findDocumentFile(context, parent, subfolderName, listFolders = true, listFiles = false)
}

/**
 * Find the file inside the given parent folder (non recursive) that has the given name
 *
 * @param context  Context to use
 * @param parent   Parent folder of the file to find
 * @param fileName Name of the file to find
 * @return File inside the given parent folder (non recursive) that has the given name; null if not found
 */
fun findFile(context: Context, parent: DocumentFile, fileName: String): DocumentFile? {
    return findDocumentFile(context, parent, fileName, listFolders = false, listFiles = true)
}

/**
 * List all subfolders inside the given parent folder (non recursive)
 *
 * @param context Context to use
 * @param parent  Parent folder to list subfolders from
 * @return Subfolders of the given parent folder
 */
// see https://stackoverflow.com/questions/5084896/using-contentproviderclient-vs-contentresolver-to-access-content-provider
fun listFolders(context: Context, parent: DocumentFile): List<DocumentFile> {
    return listFoldersFilter(context, parent, null)
}

/**
 * List all subfolders inside the given parent folder (non recursive) that match the given name filter
 *
 * @param context Context to use
 * @param parent  Parent folder to list subfolders from
 * @param filter  Name filter to use to filter the folders to list
 * @return Subfolders of the given parent folder matching the given name filter
 */
fun listFoldersFilter(
    context: Context,
    parent: DocumentFile,
    filter: NameFilter?
): List<DocumentFile> {
    var result = emptyList<DocumentFile>()
    try {
        FileExplorer(context, parent).use { fe ->
            result = fe.listDocumentFiles(
                context, parent, filter,
                listFolders = true,
                listFiles = false,
                stopFirst = false
            )
        }
    } catch (e: IOException) {
        Timber.w(e)
    }
    return result
}

/**
 * List all files (non-folders) inside the given parent folder (non recursive) that match the given name filter
 *
 * @param context Context to use
 * @param parent  Parent folder to list files from
 * @param filter  Name filter to use to filter the files to list
 * @return Files of the given parent folder matching the given name filter
 */
fun listFiles(context: Context, parent: DocumentFile, filter: NameFilter?): List<DocumentFile> {
    var result = emptyList<DocumentFile>()
    try {
        FileExplorer(context, parent).use { fe ->
            result = fe.listDocumentFiles(
                context, parent, filter,
                listFolders = false,
                listFiles = true,
                stopFirst = false
            )
        }
    } catch (e: IOException) {
        Timber.w(e)
    }
    return result
}

/**
 * List the first element inside the given parent folder (non recursive) that matches the given criteria
 *
 * @param context     Context to use
 * @param parent      Parent folder to search into
 * @param nameFilter  Name filter to use to filter the element to find
 * @param listFolders True if the element to find can be a folder
 * @param listFiles   True if the element to find can be a file (i.e. non folder)
 * @return First element of the given parent folder matching the given criteria
 */
private fun findDocumentFile(
    context: Context,
    parent: DocumentFile,
    nameFilter: String,
    listFolders: Boolean,
    listFiles: Boolean
): DocumentFile? {
    var result: List<DocumentFile?> = emptyList<DocumentFile>()
    try {
        FileExplorer(context, parent).use { fe ->
            result = fe.listDocumentFiles(
                context,
                parent,
                fe.createNameFilterEquals(nameFilter),
                listFolders,
                listFiles,
                true
            )
        }
    } catch (e: IOException) {
        Timber.w(e)
    }
    return if (result.isEmpty()) null else result[0]
}

/**
 * Open the given file using the device's app(s) of choice
 *
 * @param context Context to use
 * @param aFile   File to be opened
 */
fun openFile(context: Context, aFile: File) {
    val file = File(aFile.absolutePath)
    val dataUri = FileProvider.getUriForFile(context, AUTHORITY, file)
    tryOpenFile(context, dataUri, aFile.name, aFile.isDirectory)
}

/**
 * Open the given file using the device's app(s) of choice
 *
 * @param context Context to use
 * @param aFile   File to be opened
 */
fun openFile(context: Context, aFile: DocumentFile) {
    val fileName = if ((null == aFile.name)) "" else aFile.name!!
    tryOpenFile(context, aFile.uri, fileName, aFile.isDirectory)
}

/**
 * Open the given Uri using the device's app(s) of choice
 *
 * @param context Context to use
 * @param uri     Uri of the resource to be opened
 */
fun openUri(context: Context, uri: Uri) {
    tryOpenFile(context, uri, uri.lastPathSegment ?: "", false)
}

/**
 * Attempt to open the file or folder at the given Uri using the device's app(s) of choice
 *
 * @param context     Context to use
 * @param uri         Uri of the file or folder to be opened
 * @param fileName    Display name of the file or folder to be opened
 * @param isDirectory true if the given Uri represents a folder; false if it represents a file
 */
private fun tryOpenFile(context: Context, uri: Uri, fileName: String, isDirectory: Boolean) {
    try {
        if (isDirectory) {
            try {
                openFileWithIntent(context, uri, DocumentsContract.Document.MIME_TYPE_DIR)
            } catch (e1: ActivityNotFoundException) {
                try {
                    openFileWithIntent(context, uri, "resource/folder")
                } catch (e2: ActivityNotFoundException) {
                    context.toast(R.string.select_file_manager)
                    openFileWithIntent(context, uri, "*/*")
                    // TODO if it also crashes after this call, tell the user to get DocumentsUI.apk ? (see #670)
                }
            }
        } else openFileWithIntent(
            context,
            uri,
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(getExtension(fileName))
        )
    } catch (e: ActivityNotFoundException) {
        Timber.e(e, "No activity found to open %s", uri.toString())
        context.toastLong(R.string.error_open, Toast.LENGTH_LONG)
    }
}

/**
 * Opens the given Uri using the device's app(s) of choice
 *
 * @param context  Context to use
 * @param uri      Uri of the file or folder to be opened
 * @param mimeType Mime-type to use (determines the apps the device will suggest for opening the resource)
 */
private fun openFileWithIntent(context: Context, uri: Uri, mimeType: String?) {
    val myIntent = Intent(Intent.ACTION_VIEW)
    myIntent.setDataAndTypeAndNormalize(uri, mimeType)
    myIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(myIntent)
}

/**
 * Returns the extension of the given filename, without the "."
 *
 * @param fileName Filename
 * @return Extension of the given filename, without the "."
 */
fun getExtension(fileName: String): String {
    return if (fileName.contains(".")) fileName.substring(fileName.lastIndexOf('.') + 1)
        .lowercase() else ""
}

/**
 * Returns the filename of the given file path, without the extension
 *
 * @param filePath File path
 * @return Name of the given file, without the extension
 */
fun getFileNameWithoutExtension(filePath: String): String {
    val folderSeparatorIndex = filePath.lastIndexOf(File.separator)
    val fileName = if (-1 == folderSeparatorIndex) filePath
    else filePath.substring(folderSeparatorIndex + 1)

    val dotIndex = fileName.lastIndexOf('.')
    return if (-1 == dotIndex) fileName
    else fileName.substring(0, dotIndex)
}

/**
 * Save the given binary data in the given file, truncating the file length to the given data
 *
 * @param context    Context to use
 * @param uri        Uri of the file to write to
 * @param binaryData Data to write
 * @throws IOException In case something horrible happens during I/O
 */
@Throws(IOException::class)
fun saveBinary(context: Context, uri: Uri, binaryData: ByteArray?) {
    val buffer = ByteArray(FILE_IO_BUFFER_SIZE)
    var count: Int

    ByteArrayInputStream(binaryData).use { input ->
        val out = getOutputStream(context, uri)
        BufferedOutputStream(out).use { output ->
            while ((input.read(buffer).also { count = it }) != -1) {
                output.write(buffer, 0, count)
            }
            output.flush()
        }
    }
}

/**
 * Get the relevant file extension (without the ".") from the given mime-type
 *
 * @param mimeType Mime-type to get a file extension from
 * @return Most relevant file extension (without the ".") corresponding to the given mime-type; null if none has been found
 */
fun getExtensionFromMimeType(mimeType: String): String? {
    if (mimeType.isEmpty()) return null

    val result = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
    // Exceptions that MimeTypeMap does not support
    if (null == result) {
        if (mimeType == "image/apng" || mimeType == "image/vnd.mozilla.apng") return "png"
    }
    return result
}

/**
 * Get the most relevant mime-type for the given file extension
 *
 * @param extension File extension to get the mime-type for (without the ".")
 * @return Most relevant mime-type for the given file extension; generic mime-type if none found
 */
private fun getMimeTypeFromExtension(extension: String): String {
    if (extension.isEmpty()) return DEFAULT_MIME_TYPE
    val result = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    return result ?: DEFAULT_MIME_TYPE
}

/**
 * Get the most relevant mime-type for the given file name
 *
 * @param fileName File name to get the mime-type for
 * @return Most relevant mime-type for the given file name; generic mime-type if none found
 */
fun getMimeTypeFromFileName(fileName: String): String {
    return getMimeTypeFromExtension(getExtension(fileName))
}

/**
 * Share the given file using the device's app(s) of choice
 *
 * @param context Context to use
 * @param fileUri Uri of the file to share
 * @param title   Title of the user dialog
 */
fun shareFile(context: Context, fileUri: Uri, title: String) {
    shareFile(context, fileUri, title, "text/*")
}

/**
 * Share the given file using the device's app(s) of choice
 *
 * @param context Context to use
 * @param fileUri Uri of the file to share
 * @param title   Title of the user dialog
 * @param type    The type to use for the sharing intent
 */
fun shareFile(context: Context, fileUri: Uri, title: String, type: String) {
    val sharingIntent = Intent(Intent.ACTION_SEND)
    sharingIntent.setType(type)
    if (title.isNotEmpty()) sharingIntent.putExtra(Intent.EXTRA_SUBJECT, title)
    if (fileUri.toString().startsWith("file")) {
        val legitUri = FileProvider.getUriForFile(context, AUTHORITY, File(fileUri.toString()))
        sharingIntent.putExtra(Intent.EXTRA_STREAM, legitUri)
    } else {
        sharingIntent.putExtra(Intent.EXTRA_STREAM, fileUri)
    }
    context.startActivity(Intent.createChooser(sharingIntent, context.getString(R.string.send_to)))
}

/**
 * Return the position of the given sequence in the given data array
 *
 * @param data       Data where to find the sequence
 * @param initialPos Initial position to start from
 * @param sequence   Sequence to look for
 * @param limit      Limit not to cross (in bytes counted from the initial position); 0 for unlimited
 * @return Position of the sequence in the data array; -1 if not found within the given initial position and limit
 */
fun findSequencePosition(data: ByteArray, initialPos: Int, sequence: ByteArray, limit: Int): Int {
    var iSequence = 0

    if (initialPos < 0 || initialPos > data.size) return -1

    val remainingBytes = if ((limit > 0)) min((data.size - initialPos).toDouble(), limit.toDouble())
        .toInt() else data.size

    for (i in initialPos until remainingBytes) {
        if (sequence[iSequence] == data[i]) iSequence++
        else if (iSequence > 0) iSequence = 0

        if (sequence.size == iSequence) return i - sequence.size
    }

    // Target sequence not found
    return -1
}

/**
 * Copy the given file to the target location, giving the copy the given name
 * If a file with the same target name already exists, it is overwritten
 *
 * @param context         Context to use
 * @param sourceFileUri   Uri of the source file to copy
 * @param targetFolderUri Uri of the folder where to copy the source file
 * @param mimeType        Mime-type of the source file
 * @param newName         Filename to give of the copy
 * @return Uri of the copied file, if successful; null if failed
 * @throws IOException If something terrible happens
 */
@Throws(IOException::class)
fun copyFile(
    context: Context,
    sourceFileUri: Uri,
    targetFolderUri: Uri,
    mimeType: String,
    newName: String
): Uri? {
    if (!fileExists(context, sourceFileUri)) return null

    val targetFileUri: Uri
    if (ContentResolver.SCHEME_FILE == targetFolderUri.scheme) {
        val targetFolder = legacyFileFromUri(targetFolderUri)
        if (null == targetFolder || !targetFolder.exists()) return null
        val targetFile = File(targetFolder, newName)
        if (!targetFile.exists() && !targetFile.createNewFile()) return null
        targetFileUri = Uri.fromFile(targetFile)
    } else {
        val targetFolder = DocumentFile.fromTreeUri(context, targetFolderUri)
        if (null == targetFolder || !targetFolder.exists()) return null
        val targetFile = findOrCreateDocumentFile(context, targetFolder, mimeType, newName)
        if (null == targetFile || !targetFile.exists()) return null
        targetFileUri = targetFile.uri
    }

    getOutputStream(context, targetFileUri)?.use { output ->
        getInputStream(context, sourceFileUri)
            .use { input -> copy(input, output) }
    }
    return targetFileUri
}

/**
 * Get the device's Downloads folder
 *
 * @return Device's Downloads folder
 */
fun getDownloadsFolder(): File {
    return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
}

/**
 * Return an opened OutputStream in a brand new file created in the device's Downloads folder
 *
 * @param context  Context to use
 * @param fileName Name of the file to create
 * @param mimeType Mime-type of the file to create
 * @return Opened OutputStream in a brand new file created in the device's Downloads folder
 * @throws IOException If something horrible happens during I/O
 */
// TODO document what happens when a file with the same name already exists there before the call
@Throws(IOException::class)
fun openNewDownloadOutputStream(
    context: Context,
    fileName: String,
    mimeType: String
): OutputStream? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        openNewDownloadOutputStreamQ(context, fileName, mimeType)
    } else {
        openNewDownloadOutputStreamLegacy(fileName)
    }
}

/**
 * Legacy (non-SAF, pre-Android 10) version of openNewDownloadOutputStream
 * Return an opened OutputStream in a brand new file created in the device's Downloads folder
 *
 * @param fileName Name of the file to create
 * @return Opened OutputStream in a brand new file created in the device's Downloads folder
 * @throws IOException If something horrible happens during I/O
 */
@Throws(IOException::class)
private fun openNewDownloadOutputStreamLegacy(fileName: String): OutputStream {
    val downloadsFolder =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IOException("Downloads folder not found")

    val target = File(downloadsFolder, fileName)
    if (!target.exists() && !target.createNewFile()) throw IOException("Could not create new file in downloads folder")

    return getOutputStream(target)
}

/**
 * Android 10 version of openNewDownloadOutputStream
 * https://gitlab.com/commonsguy/download-wrangler/blob/master/app/src/main/java/com/commonsware/android/download/DownloadRepository.kt
 * Return an opened OutputStream in a brand new file created in the device's Downloads folder
 *
 * @param context  Context to use
 * @param fileName Name of the file to create
 * @param mimeType Mime-type of the file to create
 * @return Opened OutputStream in a brand new file created in the device's Downloads folder
 * @throws IOException If something horrible happens during I/O
 */
@TargetApi(29)
@Throws(IOException::class)
private fun openNewDownloadOutputStreamQ(
    context: Context,
    fileName: String,
    mimeType: String
): OutputStream? {
    val values = ContentValues()
    // Make filename unique to avoid failures on certain devices when creating a file with the same name multiple times
    val fileExt = getExtension(fileName)
    val fileNoExt =
        getFileNameWithoutExtension(fileName) + "_" + formatEpochToDate(
            Instant.now().toEpochMilli(), "yyyyMMdd-hhmm"
        )
    values.put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileNoExt.$fileExt")
    values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType)

    val resolver = context.contentResolver
    val targetFileUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: throw IOException("Target URI could not be formed")

    return resolver.openOutputStream(targetFileUri)
}

/**
 * Format the given file size using human-readable units, two decimals precision
 * e.g. if the size represents more than 1M Bytes, the result is formatted as megabytes
 *
 * @param bytes Size to format, in bytes
 * @return Given file size using human-readable units, two decimals precision
 */
fun formatHumanReadableSize(bytes: Long, res: Resources): String {
    return byteCountToDisplayRoundedSize(bytes, 2, res)
}

/**
 * Format the given file size using human-readable units, no decimals
 * e.g. if the size represents more than 1M Bytes, the result is formatted as megabytes
 *
 * @param bytes Size to format, in bytes
 * @return Given file size using human-readable units, no decimals
 */
fun formatHumanReadableSizeInt(bytes: Long, res: Resources): String {
    return byteCountToDisplayRoundedSize(bytes, 0, res)
}

/**
 * Get memory usage figures for the volume containing the given folder
 *
 * @param context Context to use
 * @param f       Folder to get the figures from
 */
class MemoryUsageFigures(context: Context, f: DocumentFile) {
    private var freeMemBytes: Long = 0

    /**
     * Get total storage capacity in bytes
     */
    var totalSpaceBytes: Long = 0
        private set

    init {
        init26(context, f)
        if (0L == totalSpaceBytes) init21(context, f)
        if (0L == totalSpaceBytes) initLegacy(context, f)
    }

    // Old way of measuring memory (inaccurate on certain devices)
    private fun initLegacy(context: Context, f: DocumentFile) {
        val fullPath = getFullPathFromUri(context, f.uri) // Oh so dirty !!
        if (fullPath.isNotEmpty()) {
            val file = File(fullPath)
            this.freeMemBytes = file.freeSpace // should actually have been getUsableSpace
            this.totalSpaceBytes = file.totalSpace
        }
    }

    // Init for API 21 to 25
    private fun init21(context: Context, f: DocumentFile) {
        val fullPath = getFullPathFromUri(context, f.uri) // Oh so dirty !!
        if (fullPath.isNotEmpty()) {
            val stat = StatFs(fullPath)

            val blockSize = stat.blockSizeLong
            totalSpaceBytes = stat.blockCountLong * blockSize
            freeMemBytes = stat.availableBlocksLong * blockSize
        }
    }

    // Init for API 26+
    // Inspired by https://github.com/Cheticamp/Storage_Volumes/
    private fun init26(context: Context, f: DocumentFile) {
        val volumeId = getVolumeIdFromUri(f.uri)
        val mgr = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager

        val volumes = mgr.storageVolumes
        var targetVolume: StorageVolume? = null
        var primaryVolume: StorageVolume? = null
        // No need to test anything, there's just one single volume
        if (1 == volumes.size) targetVolume = volumes[0]
        else { // Look for a match among listed volumes
            for (v in volumes) {
                if (v.isPrimary) primaryVolume = v

                if (volumeIdMatch(v, volumeId)) {
                    targetVolume = v
                    break
                }
            }
        }

        // If no volume matches, default to Primary
        // NB : necessary to avoid defaulting to the root on rooted phones
        // (rooted phone's root is a separate volume with specific memory usage figures)
        if (null == targetVolume) {
            targetVolume = primaryVolume
        }

        // Process target volume
        if (targetVolume != null) {
            if (targetVolume.isPrimary) {
                processPrimary(context)
            } else {
                processSecondary(targetVolume)
            }
        }
    }

    // Use StorageStatsManager on primary volume
    private fun processPrimary(context: Context) {
        val uuid = StorageManager.UUID_DEFAULT
        try {
            val storageStatsManager =
                context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            totalSpaceBytes = storageStatsManager.getTotalBytes(uuid)
            freeMemBytes = storageStatsManager.getFreeBytes(uuid)
        } catch (e: IOException) {
            Timber.w(e)
        }
    }

    // StorageStatsManager doesn't work for volumes other than the primary volume since
    // the "UUID" available for non-primary volumes is not acceptable to
    // StorageStatsManager. We must revert to statvfs(path) for non-primary volumes.
    private fun processSecondary(volume: StorageVolume) {
        try {
            val volumePath = getVolumePath(volume)
            if (volumePath.isNotEmpty()) {
                val stats = Os.statvfs(volumePath)
                val blockSize = stats.f_bsize
                totalSpaceBytes = stats.f_blocks * blockSize
                freeMemBytes = stats.f_bavail * blockSize
            }
        } catch (e: Exception) { // On some devices, Os.statvfs can throw other exceptions than ErrnoException
            Timber.w(e)
        }
    }

    val freeUsageRatio100: Double
        /**
         * Get free usage ratio (0 = all memory full; 100 = all memory free)
         */
        get() = freeMemBytes * 100.0 / totalSpaceBytes

    /**
     * Get free storage capacity in bytes
     */
    fun getfreeUsageBytes(): Long {
        return freeMemBytes
    }
}

/**
 * Indicate whether the given volume IDs match
 *
 * @param volume       Volume to compare against
 * @param treeVolumeId Volume ID extracted from an Uri
 * @return True if both IDs match
 */
private fun volumeIdMatch(volume: StorageVolume, treeVolumeId: String): Boolean {
    return volumeIdMatch(volume.uuid ?: "", volume.isPrimary, treeVolumeId)
}

/**
 * Indicate whether the given volume IDs match
 *
 * @param volumeUuid      Volume UUID to compare against
 * @param isVolumePrimary True if the volume to compare against is primary
 * @param treeVolumeId    Volume ID extracted from an Uri
 * @return True if the given volume matches the given volume ID
 */
private fun volumeIdMatch(
    volumeUuid: String,
    isVolumePrimary: Boolean,
    treeVolumeId: String
): Boolean {
    return if (volumeUuid == treeVolumeId.replace("/", "")) true
    else isVolumePrimary && treeVolumeId == PRIMARY_VOLUME_NAME
}

/**
 * Reset the app's persisted I/O permissions :
 * - persist I/O permissions for the given new Uri
 * - keep existing persisted I/O permissions for the given optional Uris
 *
 *
 * NB : if the optional Uris have no persisted permissions, this call won't create them
 *
 * @param context  Context to use
 * @param newUri   New Uri to add to the persisted I/O permission
 * @param keepUris List of Uri to keep in the persisted I/O permissions, if already set (can be empty)
 */
fun persistNewUriPermission(context: Context, newUri: Uri, keepUris: List<Uri>?) {
    val contentResolver = context.contentResolver
    if (!isUriPermissionPersisted(contentResolver, newUri)) {
        Timber.d("Persisting Uri permission for %s", newUri)
        // Release previous access permissions, if different than the new one
        val keepList: MutableList<Uri> = keepUris?.toMutableList() ?: mutableListOf()
        keepList.add(newUri)
        revokePreviousPermissions(contentResolver, keepList)
        // Persist new access permission
        contentResolver.takePersistableUriPermission(
            newUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }
}

/**
 * Check if the given Uri has persisted I/O permissions
 *
 * @param resolver ContentResolver to use
 * @param uri      Uri to check
 * @return true if the given Uri has persisted I/O permissions
 */
fun isUriPermissionPersisted(resolver: ContentResolver, uri: Uri): Boolean {
    val treeUriId = DocumentsContract.getTreeDocumentId(uri)
    for (p in resolver.persistedUriPermissions) {
        if (DocumentsContract.getTreeDocumentId(p.uri) == treeUriId) {
            Timber.d("Uri permission already persisted for %s", uri)
            return true
        }
    }
    return false
}

/**
 * Revoke persisted Uri I/O permissions to the exception of given Uri's
 *
 * @param resolver   ContentResolver to use
 * @param exceptions Uri's whose permissions won't be revoked
 */
private fun revokePreviousPermissions(resolver: ContentResolver, exceptions: List<Uri>) {
    // Unfortunately, the content Uri of the selected resource is not exactly the same as the one stored by ContentResolver
    // -> solution is to compare their TreeDocumentId instead
    val exceptionIds = exceptions.map { documentUri: Uri? ->
        DocumentsContract.getTreeDocumentId(documentUri)
    }
    for (p in resolver.persistedUriPermissions) if (!exceptionIds.contains(
            DocumentsContract.getTreeDocumentId(
                p.uri
            )
        )
    ) resolver.releasePersistableUriPermission(
        p.uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    )

    if (resolver.persistedUriPermissions.size <= exceptionIds.size) {
        Timber.d("Permissions revoked successfully")
    } else {
        Timber.d("Failed to revoke permissions")
    }
}


/**
 * Return the content of the given file as an UTF-8 string
 * Leading BOMs are ignored
 *
 * @param context Context to be used
 * @param f       File to read from
 * @return Content of the given file as a string; empty string if an error occurred
 */
fun readFileAsString(context: Context, f: DocumentFile): String {
    try {
        getInputStream(context, f).let {
            return readStreamAsString(it)
        }
    } catch (e: IOException) {
        Timber.e(e, "Error while reading %s", f.uri.toString())
    } catch (e: IllegalArgumentException) {
        Timber.e(e, "Error while reading %s", f.uri.toString())
    }
    return ""
}

/**
 * Read the given InputStream as a continuous string, ignoring line breaks and BOMs
 * WARNING : Designed to be used on text files only
 *
 * @param input InputStream to read from
 * @return String built from the given InputStream
 * @throws IOException              In case something horrible happens
 * @throws IllegalArgumentException In case something horrible happens
 */
@Throws(IOException::class, IllegalArgumentException::class)
fun readStreamAsString(input: InputStream): String {
    return TextUtils.join(" ", readStreamAsStrings(input))
}

@Throws(IOException::class, IllegalArgumentException::class)
fun readStreamAsStrings(input: InputStream): List<String> {
    val result: MutableList<String> = ArrayList()
    var sCurrentLine: String?
    var isFirst = true
    BufferedReader(InputStreamReader(input)).use { br ->
        while ((br.readLine().also { sCurrentLine = it }) != null) {
            sCurrentLine?.let {
                if (isFirst) {
                    // Strip UTF-8 BOMs if any
                    if (it[0] == '\uFEFF') sCurrentLine = it.substring(1)
                    isFirst = false
                }
                result.add(it)
            } ?: break
        }
    }
    return result
}

/**
 * Indicate whether the file at the given Uri exists or not
 *
 * @param context Context to be used
 * @param fileUri Uri to the file whose existence is to check
 * @return True if the given Uri points to an existing file; false instead
 */
fun fileExists(context: Context, fileUri: Uri): Boolean {
    if (ContentResolver.SCHEME_FILE == fileUri.scheme) {
        val path = fileUri.path
        return if (path != null) File(path).exists()
        else false
    } else {
        val doc = getFileFromSingleUriString(context, fileUri.toString())
        return (doc != null)
    }
}

fun legacyFileFromUri(fileUri: Uri): File? {
    if (ContentResolver.SCHEME_FILE == fileUri.scheme) {
        val path = fileUri.path
        if (path != null) return File(path)
    }
    return null
}

/**
 * Return the size of the file at the given Uri, in bytes
 *
 * @param context Context to be used
 * @param fileUri Uri to the file whose size to retrieve
 * @return Size of the file at the given Uri; -1 if it cannot be found
 */
fun fileSizeFromUri(context: Context, fileUri: Uri): Long {
    if (ContentResolver.SCHEME_FILE == fileUri.scheme) {
        val path = fileUri.path
        if (path != null) return File(path).length()
    } else {
        val doc = getFileFromSingleUriString(context, fileUri.toString())
        if (doc != null) return doc.length()
    }
    return -1
}

/**
 * Get a valid Uri for the given File
 *
 * @param context Context to use
 * @param file    File to get the Uri for
 * @return Valid Uri
 */
fun getFileUriCompat(context: Context, file: File): Uri {
    return FileProvider.getUriForFile(context, AUTHORITY, file)
}

/**
 * Remove all illegal characters from the given string to make it a valid Android file name
 *
 * @param fileName String to clean up
 * @return Cleaned string
 */
fun cleanFileName(fileName: String): String {
    return fileName.replace(ILLEGAL_FILENAME_CHARS.toRegex(), "")
}

/**
 * Empty the given subfolder inside the cache folder
 *
 * @param context    Context to use
 * @param folderName Name of the subfolder to empty
 */
fun emptyCacheFolder(context: Context, folderName: String) {
    val cacheFolder = getOrCreateCacheFolder(context, folderName)
    if (cacheFolder != null) {
        val files = cacheFolder.listFiles()
        if (files != null) for (f in files) if (!f.delete()) Timber.w(
            "Unable to delete file %s",
            f.absolutePath
        )
    }
}

/**
 * Retrieve or create the subfolder with the given name inside the cache folder
 *
 * @param context    Context to use
 * @param folderName Name of the subfolder to retrieve or create; may contain subfolders separated by File.separator
 * @return Subfolder as a File, or null if it couldn't be found nor created
 */
fun getOrCreateCacheFolder(context: Context, folderName: String): File? {
    var root = context.cacheDir
    val subfolders = folderName.split(File.separator)
    for (subfolderName in subfolders) {
        val cacheFolder = File(root, subfolderName)
        root = if (cacheFolder.exists()) cacheFolder
        else if (cacheFolder.mkdir()) cacheFolder
        else return null
    }
    return root
}

fun getAssetAsString(mgr: AssetManager, assetName: String): String {
    val sb = StringBuilder()
    getAssetAsString(mgr, assetName, sb)
    return sb.toString()
}

fun getAssetAsString(mgr: AssetManager, assetName: String, sb: StringBuilder) {
    try {
        mgr.open(assetName).use { `is` ->
            BufferedReader(InputStreamReader(`is`)).use { br ->
                var sCurrentLine: String?
                while ((br.readLine().also { sCurrentLine = it }) != null) {
                    sb.append(sCurrentLine).append(System.lineSeparator())
                }
            }
        }
    } catch (e: Exception) {
        Timber.e(e)
    }
}

/**
 * Deletes a file, never throwing an exception. If file is a directory, delete it and all sub-directories.
 *
 *
 * The difference between File.delete() and this method are:
 *
 *  * A directory to be deleted does not have to be empty.
 *  * No exceptions are thrown when a file or directory cannot be deleted.
 *
 *
 *
 * Custom substitute for commons.io.FileUtils.deleteQuietly that works with devices that doesn't support File.toPath
 *
 * @param file file or directory to delete, can be `null`
 * @return `true` if the file or directory was deleted, otherwise
 * `false`
 */
private fun deleteQuietly(file: File?): Boolean {
    if (file == null) {
        return false
    }
    try {
        if (file.isDirectory) {
            tryCleanDirectory(file)
        }
    } catch (ignored: java.lang.Exception) {
    }

    return try {
        file.delete()
    } catch (ignored: java.lang.Exception) {
        false
    }
}

/**
 * Cleans a directory without deleting it.
 *
 *
 * Custom substitute for commons.io.FileUtils.cleanDirectory that supports devices without File.toPath
 *
 * @param directory directory to clean
 * @return true if directory has been successfully cleaned
 * @throws IOException in case cleaning is unsuccessful
 */
@Throws(IOException::class)
fun tryCleanDirectory(directory: File): Boolean {
    val files = directory.listFiles()
        ?: throw IOException("Failed to list content of $directory")

    var isSuccess = true

    for (file in files) {
        if (file.isDirectory && !tryCleanDirectory(file)) isSuccess = false
        if (!file.delete() && file.exists()) isSuccess = false
    }

    return isSuccess
}

/**
 * Returns a human-readable version of the file size, where the input represents a specific number of bytes.
 *
 *
 * If the size is over 1GB, the size is rounded by places.
 *
 *
 *
 * Similarly for the 1MB and 1KB boundaries.
 *
 *
 * @param size   the number of bytes
 * @param places rounded decimal places
 * @param locale decimal separator locale
 * @return a human-readable display value (includes units - EB, PB, TB, GB, MB, KB or bytes)
 * @see [IO-226 - should the rounding be changed?](https://issues.apache.org/jira/browse/IO-226)
 */
// Directly copied from https://github.com/apache/commons-io/pull/74
fun byteCountToDisplayRoundedSize(
    size: BigInteger,
    places: Int,
    res: Resources,
    locale: Locale?
): String {
    val sizeInLong = size.toLong()
    val formatPattern = "%." + places + "f"
    val displaySize =
        if (size.divide(ONE_GB_BI) > BigInteger.ZERO) {
            String.format(
                locale,
                formatPattern,
                sizeInLong / ONE_GB_BI.toDouble()
            ) + " " + res.getString(R.string.u_gigabyte)
        } else if (size.divide(ONE_MB_BI) > BigInteger.ZERO) {
            String.format(
                locale,
                formatPattern,
                sizeInLong / ONE_MB_BI.toDouble()
            ) + " " + res.getString(R.string.u_megabyte)
        } else if (size.divide(ONE_KB_BI) > BigInteger.ZERO) {
            String.format(
                locale,
                formatPattern,
                sizeInLong / ONE_KB_BI.toDouble()
            ) + " " + res.getString(R.string.u_kilobyte)
        } else {
            size.toString() + " " + res.getString(R.string.u_byte)
        }

    return displaySize.replaceFirst(
        ("[" +
                decimalSeparator +
                "]" +
                String(CharArray(places)).replace('\u0000', '0') +
                " ").toRegex(), " "
    )
}

/**
 * Returns a human-readable version of the file size, where the input represents a specific number of bytes.
 *
 * @param size   the number of bytes
 * @param places rounded decimal places
 * @return a human-readable display value (includes units - GB, MB, KB or bytes)
 * @see [IO-226 - should the rounding be changed?](https://issues.apache.org/jira/browse/IO-226)
 */
fun byteCountToDisplayRoundedSize(size: Long, places: Int, res: Resources): String {
    return byteCountToDisplayRoundedSize(
        BigInteger.valueOf(size),
        places,
        res,
        Locale.getDefault()
    )
}

fun DocumentFile.uniqueHash(): Long {
    return hash64((this.name + "." + this.length()).toByteArray())
}

fun interface NameFilter {
    /**
     * Tests whether or not the specified abstract display name should be included in a pathname list.
     *
     * @param displayName The abstract display name to be tested
     * @return `true` if and only if `displayName` should be included
     */
    fun accept(displayName: String): Boolean
}