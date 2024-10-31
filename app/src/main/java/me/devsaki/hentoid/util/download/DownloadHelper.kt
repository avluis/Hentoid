package me.devsaki.hentoid.util.download

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import me.devsaki.hentoid.core.Consumer
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.events.DownloadEvent
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.assertNonUiThread
import me.devsaki.hentoid.util.download.DownloadSpeedLimiter.take
import me.devsaki.hentoid.util.exception.DownloadInterruptedException
import me.devsaki.hentoid.util.exception.NetworkingException
import me.devsaki.hentoid.util.exception.UnsupportedContentException
import me.devsaki.hentoid.util.file.DiskCache
import me.devsaki.hentoid.util.file.MemoryUsageFigures
import me.devsaki.hentoid.util.file.fileSizeFromUri
import me.devsaki.hentoid.util.file.findOrCreateDocumentFile
import me.devsaki.hentoid.util.file.formatHumanReadableSize
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.getDocumentFromTreeUri
import me.devsaki.hentoid.util.file.getExtensionFromMimeType
import me.devsaki.hentoid.util.file.getOutputStream
import me.devsaki.hentoid.util.file.isUriPermissionPersisted
import me.devsaki.hentoid.util.file.removeFile
import me.devsaki.hentoid.util.image.getMimeTypeFromPictureBinary
import me.devsaki.hentoid.util.image.isMimeTypeSupported
import me.devsaki.hentoid.util.keepDigits
import me.devsaki.hentoid.util.network.HEADER_CONTENT_TYPE
import me.devsaki.hentoid.util.network.fixUrl
import me.devsaki.hentoid.util.network.getOnlineResourceDownloader
import me.devsaki.hentoid.util.network.getOnlineResourceFast
import org.greenrobot.eventbus.EventBus
import org.jsoup.nodes.Document
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.text.Charsets.UTF_8


// NB : Actual size of read bytes may be smaller
private const val DL_IO_BUFFER_SIZE_B = 50 * 1024

@Throws(
    IOException::class,
    UnsupportedContentException::class,
    DownloadInterruptedException::class,
    IllegalStateException::class
)
fun downloadToFileCached(
    context: Context,
    site: Site,
    rawUrl: String,
    requestHeaders: List<Pair<String, String>>,
    interruptDownload: AtomicBoolean,
    cacheKey: String,
    forceMimeType: String? = null,
    failFast: Boolean = true,
    resourceId: Int,
    notifyProgress: Consumer<Float>? = null
): Pair<Uri?, String> {
    return downloadToFile(
        context, site, rawUrl, requestHeaders,
        fileCreator = { _, _ -> DiskCache.createFile(cacheKey) },
        interruptDownload, forceMimeType, failFast, resourceId, notifyProgress
    )
}

@Throws(
    IOException::class,
    UnsupportedContentException::class,
    DownloadInterruptedException::class,
    IllegalStateException::class
)
fun downloadToFile(
    context: Context,
    site: Site,
    rawUrl: String,
    requestHeaders: List<Pair<String, String>>,
    targetFolderUri: Uri,
    targetFileName: String,
    interruptDownload: AtomicBoolean,
    forceMimeType: String? = null,
    failFast: Boolean = true,
    resourceId: Int,
    notifyProgress: Consumer<Float>? = null
): Pair<Uri?, String> {
    return downloadToFile(
        context, site, rawUrl, requestHeaders,
        fileCreator = { ctx, mimeType ->
            createFile(ctx, targetFolderUri, targetFileName, mimeType)
        },
        interruptDownload, forceMimeType, failFast, resourceId, notifyProgress
    )
}

/**
 * Download the given resource to the given disk location
 *
 * @param site              Site to use params for
 * @param rawUrl            URL to download from
 * @param requestHeaders    HTTP request headers to use
 * @param fileCreator       Method to use to create the file where to download
 * @param interruptDownload Used to interrupt the download whenever the value switches to true. If that happens, the file will be deleted.
 * @param forceMimeType     Forced mime-type of the downloaded resource (null for auto-set)
 * @param failFast          True for a shorter read timeout; false for a regular, patient download
 * @param resourceId        ID of the corresponding resource (for logging purposes only)
 * @param notifyProgress    Consumer called with the download progress %
 * @return Pair containing
 * - Left : Uri of downloaded file
 * - Right : Detected mime-type of the downloaded resource
 */
@Throws(
    IOException::class,
    UnsupportedContentException::class,
    DownloadInterruptedException::class,
    IllegalStateException::class
)
private fun downloadToFile(
    context: Context,
    site: Site,
    rawUrl: String,
    requestHeaders: List<Pair<String, String>>,
    fileCreator: (Context, String) -> Uri,
    interruptDownload: AtomicBoolean,
    forceMimeType: String? = null,
    failFast: Boolean = true,
    resourceId: Int,
    notifyProgress: Consumer<Float>? = null
): Pair<Uri?, String> {
    assertNonUiThread()
    val url = fixUrl(rawUrl, site.url)
    if (interruptDownload.get()) throw DownloadInterruptedException("Download interrupted")
    Timber.d("DOWNLOADING %d %s", resourceId, url)
    val response = if (failFast) getOnlineResourceFast(
        url,
        requestHeaders,
        site.useMobileAgent,
        site.useHentoidAgent,
        site.useWebviewAgent
    ) else getOnlineResourceDownloader(
        url,
        requestHeaders,
        site.useMobileAgent,
        site.useHentoidAgent,
        site.useWebviewAgent
    )
    Timber.d("DOWNLOADING %d - RESPONSE %s", resourceId, response.code)
    if (response.code >= 300) throw NetworkingException(
        response.code,
        "Network error " + response.code,
        null
    )
    val body = response.body
        ?: throw IOException("Could not read response : empty body for $url")
    val size = body.contentLength()
    val sizeStr =
        if (size < 1) "unknown" else formatHumanReadableSize(size, context.resources)
    Timber.d(
        "STARTING DOWNLOAD FOR %d (size %s)",
        resourceId,
        sizeStr
    )
    var mimeType = forceMimeType ?: ""
    val buffer = ByteArray(DL_IO_BUFFER_SIZE_B)
    val notificationResolution = 250 * 1024 / DL_IO_BUFFER_SIZE_B // Notify every 250 KB
    var len: Int
    var processed: Long = 0
    var iteration = 0
    var out: OutputStream? = null
    var targetFileUri: Uri? = null
    body.use { bdy ->
        bdy.byteStream().use { stream ->
            while (stream.read(buffer).also { len = it } > -1) {
                if (interruptDownload.get()) break
                processed += len.toLong()

                // First iteration
                if (0 == iteration++) {
                    // Read mime-type on the fly if not forced
                    if (mimeType.isEmpty()) {
                        val contentType = response.header(HEADER_CONTENT_TYPE) ?: ""
                        mimeType = getMimeTypeFromStream(buffer, len, contentType, url, sizeStr)
                    }
                    // Create target file and output stream
                    targetFileUri = fileCreator(context, mimeType)
                    Timber.d(
                        "WRITING DOWNLOAD %d TO %s (size %s)",
                        resourceId,
                        targetFileUri!!.path,
                        sizeStr
                    )
                    out = getOutputStream(context, targetFileUri!!)
                }
                if (len > 0 && out != null) {
                    out!!.write(buffer, 0, len)
                    if (notifyProgress != null && 0 == iteration % notificationResolution && size > 0)
                        notifyProgress.invoke(processed * 100f / size)
                    take(len.toLong())
                }
            }
            // End of download
            if (!interruptDownload.get()) {
                notifyProgress?.invoke(100f)
                out?.flush()
                if (targetFileUri != null) {
                    val targetFileSize =
                        fileSizeFromUri(context, targetFileUri!!)
                    Timber.d(
                        "DOWNLOAD %d [%s] WRITTEN TO %s (%s)",
                        resourceId,
                        mimeType,
                        targetFileUri!!.path,
                        formatHumanReadableSize(
                            targetFileSize,
                            context.resources
                        )
                    )
                }
                return Pair(targetFileUri, mimeType)
            }
        }
    }
    // Remove the remaining file chunk if download has been interrupted
    if (targetFileUri != null) removeFile(context, targetFileUri!!)
    throw DownloadInterruptedException("Download interrupted")
}

@Throws(UnsupportedContentException::class)
private fun getMimeTypeFromStream(
    buffer: ByteArray,
    bufLength: Int,
    contentType: String,
    url: String,
    size: String,
): String {
    val result = getMimeTypeFromPictureBinary(buffer)
    if (!isMimeTypeSupported(result)) {
        if (contentType.contains("text/")) {
            val message = buffer.copyOfRange(0, bufLength).toString(UTF_8).trim()
            throw UnsupportedContentException("Message received from $url : $message")
        }
        throw UnsupportedContentException("Invalid mime-type received from $url (size=$size; content-type=$contentType; img mime-type=$result)")
    }
    return result
}

@Throws(IOException::class)
fun createFile(
    context: Context,
    targetFolderUri: Uri,
    targetFileName: String,
    mimeType: String
): Uri {
    var targetFileNameFinal =
        targetFileName + "." + getExtensionFromMimeType(mimeType)
    // Keep the extension if the target file name is provided with one
    val dotOffset = targetFileName.lastIndexOf('.')
    if (dotOffset > -1) {
        val extLength = targetFileName.length - targetFileName.lastIndexOf('.') - 1
        if (extLength < 5) targetFileNameFinal = targetFileName
    }
    return if (ContentResolver.SCHEME_FILE == targetFolderUri.scheme) {
        val path = targetFolderUri.path
        if (path != null) {
            val targetFolder = File(path)
            if (targetFolder.exists()) {
                val targetFile = File(targetFolder, targetFileNameFinal)
                if (!targetFile.exists() && !targetFile.createNewFile()) {
                    throw IOException("Could not create file " + targetFile.path + " in " + path)
                }
                Uri.fromFile(targetFile)
            } else {
                throw IOException("Could not create file $targetFileNameFinal : $path does not exist")
            }
        } else {
            throw IOException("Could not create file $targetFileNameFinal : $targetFolderUri has no path")
        }
    } else {
        getDocumentFromTreeUri(context, targetFolderUri)?.let { targetFolder ->
            val file = findOrCreateDocumentFile(
                context,
                targetFolder,
                mimeType,
                targetFileNameFinal
            )
            file?.uri
                ?: throw IOException("Could not create file $targetFileNameFinal : creation failed")
        }
            ?: throw IOException("Could not create file $targetFileNameFinal : $targetFolderUri does not exist")
    }
}

/**
 * Extract the given HTML document's canonical URL using link and OpenGraph metadata when available
 * NB : Uses the URL with the highest number when both exist and are not the same
 *
 * @param doc HTML document to parse
 * @return Canonical URL of the given document; empty string if nothing found
 */
fun getCanonicalUrl(doc: Document): String {
    // Get the canonical URL
    var canonicalUrl = ""
    val canonicalElt = doc.select("head link[rel=canonical]").first()
    if (canonicalElt != null) canonicalUrl = canonicalElt.attr("href").trim { it <= ' ' }

    // Get the OpenGraph URL
    var ogUrl = ""
    val ogUrlElt = doc.select("head meta[property=og:url]").first()
    if (ogUrlElt != null) ogUrl = ogUrlElt.attr("content").trim { it <= ' ' }
    val finalUrl: String =
        if (canonicalUrl.isNotEmpty() && ogUrl.isNotEmpty() && ogUrl != canonicalUrl) {
            val canonicalDigitsStr = keepDigits(canonicalUrl)
            val canonicalDigits =
                if (canonicalDigitsStr.isEmpty()) 0 else canonicalDigitsStr.toInt()
            val ogDigitsStr = keepDigits(ogUrl)
            val ogDigits = if (ogDigitsStr.isEmpty()) 0 else ogDigitsStr.toInt()
            if (canonicalDigits > ogDigits) canonicalUrl else ogUrl
        } else {
            canonicalUrl.ifEmpty { ogUrl }
        }
    return finalUrl
}

fun selectDownloadLocation(context: Context): StorageLocation {
    val uriStr1 = Settings.getStorageUri(StorageLocation.PRIMARY_1).trim()
    val uriStr2 = Settings.getStorageUri(StorageLocation.PRIMARY_2).trim()

    // Obvious cases
    if (uriStr1.isEmpty() && uriStr2.isEmpty()) return StorageLocation.NONE
    if (uriStr1.isNotEmpty() && uriStr2.isEmpty()) return StorageLocation.PRIMARY_1
    if (uriStr1.isEmpty()) return StorageLocation.PRIMARY_2

    // Broken cases
    val root1 = getDocumentFromTreeUriString(context, uriStr1)
    val root2 = getDocumentFromTreeUriString(context, uriStr2)
    if (null == root1 && null == root2) return StorageLocation.NONE
    if (root1 != null && null == root2) return StorageLocation.PRIMARY_1
    if (null == root1) return StorageLocation.PRIMARY_2

    // Apply download strategy
    val memUsage1 = MemoryUsageFigures(context, root1)
    val memUsage2 = MemoryUsageFigures(context, root2!!)
    val strategy = Settings.storageDownloadStrategy
    return if (Settings.Value.STORAGE_FILL_FALLOVER == strategy) {
        if (100 - memUsage1.freeUsageRatio100 > Settings.storageSwitchThresholdPc) StorageLocation.PRIMARY_2 else StorageLocation.PRIMARY_1
    } else {
        if (memUsage1.getfreeUsageBytes() > memUsage2.getfreeUsageBytes()) StorageLocation.PRIMARY_1 else StorageLocation.PRIMARY_2
    }
}

fun getDownloadLocation(context: Context, content: Content): Pair<DocumentFile?, StorageLocation>? {
    // Check for download folder existence, available free space and credentials
    var dir: DocumentFile? = null
    var location: StorageLocation = StorageLocation.NONE
    // Folder already set (e.g. resume paused download)
    if (content.storageUri.isNotEmpty()) {
        // Reset storage URI if unreachable (will be re-created later in the method)
        val rootFolder = getDocumentFromTreeUriString(context, content.storageUri)
        if (null == rootFolder) content.clearStorageDoc()
        else {
            if (!testDownloadFolder(context, content.storageUri)) return null
            dir = getDocumentFromTreeUriString(
                context,
                content.storageUri
            ) // Will come out null if invalid
        }
    }
    // Auto-select location according to storage management strategy
    if (content.storageUri.isEmpty()) {
        location = selectDownloadLocation(context)
        if (!testDownloadFolder(context, Settings.getStorageUri(location))) return null
    }
    return Pair(dir, location)
}

private fun testDownloadFolder(
    context: Context,
    uriString: String
): Boolean {
    if (uriString.isEmpty()) {
        // May happen if user has skipped it during the intro
        Timber.i("No download folder set")
        EventBus.getDefault()
            .post(DownloadEvent.fromPauseMotive(DownloadEvent.Motive.NO_DOWNLOAD_FOLDER))
        return false
    }
    val rootFolder = getDocumentFromTreeUriString(context, uriString)
    if (null == rootFolder) {
        // May happen if the folder has been moved or deleted after it has been selected
        Timber.i("Download folder has not been found. Please select it again.")
        EventBus.getDefault()
            .post(DownloadEvent.fromPauseMotive(DownloadEvent.Motive.DOWNLOAD_FOLDER_NOT_FOUND))
        return false
    }
    if (!isUriPermissionPersisted(context.contentResolver, rootFolder.uri)) {
        // May happen if user has manually removed credentials using his OS
        Timber.i("Insufficient credentials on download folder. Please select it again.")
        EventBus.getDefault()
            .post(DownloadEvent.fromPauseMotive(DownloadEvent.Motive.DOWNLOAD_FOLDER_NO_CREDENTIALS))
        return false
    }
    val spaceLeftBytes = MemoryUsageFigures(context, rootFolder).getfreeUsageBytes()
    if (spaceLeftBytes < 2L * 1024 * 1024) {
        Timber.i("Device very low on storage space (<2 MB). Queue paused.")
        EventBus.getDefault().post(
            DownloadEvent.fromPauseMotive(
                DownloadEvent.Motive.NO_STORAGE,
                spaceLeftBytes
            )
        )
        return false
    }
    return true
}