package me.devsaki.hentoid.util.download

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import me.devsaki.hentoid.core.Consumer
import me.devsaki.hentoid.core.READER_CACHE
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.events.DownloadEvent
import me.devsaki.hentoid.parsers.ContentParserFactory
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.assertNonUiThread
import me.devsaki.hentoid.util.download.DownloadSpeedLimiter.take
import me.devsaki.hentoid.util.exception.DownloadInterruptedException
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.exception.LimitReachedException
import me.devsaki.hentoid.util.exception.NetworkingException
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.exception.UnsupportedContentException
import me.devsaki.hentoid.util.file.MemoryUsageFigures
import me.devsaki.hentoid.util.file.StorageCache
import me.devsaki.hentoid.util.file.createFile
import me.devsaki.hentoid.util.file.fileSizeFromUri
import me.devsaki.hentoid.util.file.formatHumanReadableSize
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.getMimeTypeFromStream
import me.devsaki.hentoid.util.file.getOutputStream
import me.devsaki.hentoid.util.file.isUriPermissionPersisted
import me.devsaki.hentoid.util.file.removeFile
import me.devsaki.hentoid.util.formatCacheKey
import me.devsaki.hentoid.util.keepDigits
import me.devsaki.hentoid.util.network.HEADER_CONTENT_TYPE
import me.devsaki.hentoid.util.network.HEADER_COOKIE_KEY
import me.devsaki.hentoid.util.network.HEADER_REFERER_KEY
import me.devsaki.hentoid.util.network.fixUrl
import me.devsaki.hentoid.util.network.getCookies
import me.devsaki.hentoid.util.network.getOnlineResourceDownloader
import me.devsaki.hentoid.util.network.getOnlineResourceFast
import me.devsaki.hentoid.util.network.peekCookies
import org.greenrobot.eventbus.EventBus
import org.jsoup.nodes.Document
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean


// NB : Actual size of read bytes may be smaller
private const val DL_IO_BUFFER_SIZE_B = 50 * 1024

/**
 * Download the picture at the given index to the given folder
 *
 * @param pageIndex    Index of the picture to download
 * @param stopDownload Switch to interrupt the download
 * @return Optional triple with
 * - The page index
 * - The Uri of the downloaded file
 *
 * The return value is empty if the download fails
 */
fun downloadPic(
    context: Context,
    content: Content,
    img: ImageFile,
    resourceId: Int,
    targetFolderUri: Uri? = null,
    onProgress: ((Float, Int) -> Unit)? = null,
    stopDownload: AtomicBoolean? = null
): Pair<Int, String>? {
    assertNonUiThread()

    try {
        val targetFile: File

        // Prepare request headers
        val headers: MutableList<Pair<String, String>> = ArrayList()
        headers.add(
            Pair(HEADER_REFERER_KEY, content.readerUrl)
        ) // Useful for Hitomi and Toonily
        val result: Uri?
        if (img.needsPageParsing) {
            val pageUrl = fixUrl(img.pageUrl, content.site.url)
            // Get cookies from the app jar
            var cookieStr = getCookies(pageUrl)
            // If nothing found, peek from the site
            if (cookieStr.isEmpty()) cookieStr = peekCookies(pageUrl)
            if (cookieStr.isNotEmpty()) headers.add(
                Pair(HEADER_COOKIE_KEY, cookieStr)
            )
            result = downloadPictureFromPage(
                context,
                content,
                img,
                resourceId,
                headers,
                targetFolderUri,
                onProgress,
                stopDownload
            )
        } else {
            val imgUrl = fixUrl(img.url, content.site.url)
            // Get cookies from the app jar
            var cookieStr = getCookies(imgUrl)
            // If nothing found, peek from the site
            if (cookieStr.isEmpty()) cookieStr = peekCookies(content.galleryUrl)
            if (cookieStr.isNotEmpty()) headers.add(
                Pair(HEADER_COOKIE_KEY, cookieStr)
            )
            result = downloadToFile(
                context,
                content.site,
                imgUrl,
                headers,
                if (null == targetFolderUri) { _, _ ->
                    StorageCache.createFile(
                        READER_CACHE,
                        formatCacheKey(img)
                    )
                }
                else { ctx, mimeType ->
                    createFile(
                        ctx,
                        targetFolderUri,
                        formatCacheKey(img),
                        mimeType
                    )
                },
                stopDownload,
                resourceId = resourceId
            ) { onProgress?.invoke(it, resourceId) }
        }

        val targetFileUri = result ?: throw ParseException("Resource is not available")
        targetFile = File(targetFileUri.path!!)

        return Pair(resourceId, Uri.fromFile(targetFile).toString())
    } catch (_: DownloadInterruptedException) {
        Timber.d("Download interrupted for pic %d", resourceId)
    } catch (e: Exception) {
        Timber.w(e)
    }
    return null
}

/**
 * Download the picture represented by the given ImageFile to the given storage location
 *
 * @param content           Corresponding Content
 * @param img               ImageFile of the page to download
 * @param resourceId        Internal ID for the page to download, for remapping purposes (usually, the page index)
 * @param requestHeaders    HTTP request headers to use
 * @param interruptDownload Used to interrupt the download whenever the value switches to true. If that happens, the file will be deleted.
 * @return Pair containing
 * - Left : Downloaded file
 * - Right : Detected mime-type of the downloaded resource
 * @throws UnsupportedContentException, IOException, LimitReachedException, EmptyResultException, DownloadInterruptedException in case something horrible happens
 */
@Throws(
    UnsupportedContentException::class,
    IOException::class,
    LimitReachedException::class,
    EmptyResultException::class,
    DownloadInterruptedException::class
)
private fun downloadPictureFromPage(
    context: Context,
    content: Content,
    img: ImageFile,
    resourceId: Int,
    requestHeaders: List<Pair<String, String>>,
    targetFolderUri: Uri? = null,
    onProgress: ((Float, Int) -> Unit)? = null,
    interruptDownload: AtomicBoolean? = null
): Uri? {
    val site = content.site
    val pageUrl = fixUrl(img.pageUrl, site.url)
    val parser = ContentParserFactory.getImageListParser(content.site)
    val pages: Pair<String, String?>
    try {
        pages = parser.parseImagePage(pageUrl, requestHeaders)
    } finally {
        parser.clear()
    }
    img.url = pages.first
    // Download the picture
    try {
        return downloadToFile(
            context,
            content.site,
            img.url,
            requestHeaders,
            if (null == targetFolderUri) { _, _ ->
                StorageCache.createFile(
                    READER_CACHE,
                    formatCacheKey(img)
                )
            }
            else { ctx, mimeType ->
                createFile(
                    ctx,
                    targetFolderUri,
                    formatCacheKey(img),
                    mimeType
                )
            },
            interruptDownload,
            resourceId = resourceId
        ) { onProgress?.invoke(it, resourceId) }
    } catch (e: IOException) {
        if (pages.second != null) Timber.d("First download failed; trying backup URL") else throw e
    }
    // Trying with backup URL
    img.url = pages.second ?: ""
    return downloadToFile(
        context,
        content.site,
        img.url,
        requestHeaders,
        if (null == targetFolderUri) { _, _ ->
            StorageCache.createFile(
                READER_CACHE,
                formatCacheKey(img)
            )
        }
        else { ctx, mimeType -> createFile(ctx, targetFolderUri, formatCacheKey(img), mimeType) },
        interruptDownload,
        resourceId = resourceId
    ) { onProgress?.invoke(it, resourceId) }
}

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
): Uri? {
    return downloadToFile(
        context, site, rawUrl, requestHeaders,
        fileCreator = { _, _ -> StorageCache.createFile(READER_CACHE, cacheKey) },
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
): Uri? {
    return downloadToFile(
        context, site, rawUrl, requestHeaders,
        fileCreator = { ctx, mimeType ->
            createFile(ctx, targetFolderUri, targetFileName, mimeType)
        },
        interruptDownload, forceMimeType, failFast, resourceId, notifyProgress
    )
}

/**
 * Download the given resource to the given storage location
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
    interruptDownload: AtomicBoolean? = null,
    forceMimeType: String? = null,
    failFast: Boolean = true,
    resourceId: Int,
    notifyProgress: Consumer<Float>? = null
): Uri? {
    assertNonUiThread()
    val url = fixUrl(rawUrl, site.url)
    if (interruptDownload?.get() == true) throw DownloadInterruptedException("Download interrupted")
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
                if (interruptDownload?.get() == true) break
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
                        targetFileUri.path,
                        sizeStr
                    )
                    out = getOutputStream(context, targetFileUri)
                }
                if (len > 0 && out != null) {
                    out!!.write(buffer, 0, len)
                    if (notifyProgress != null && 0 == iteration % notificationResolution && size > 0)
                        notifyProgress.invoke(processed * 100f / size)
                    take(len.toLong())
                }
            }
            // End of download
            if (interruptDownload?.get() != true) {
                notifyProgress?.invoke(100f)
                out?.flush()
                if (targetFileUri != null) {
                    val targetFileSize =
                        fileSizeFromUri(context, targetFileUri)
                    Timber.d(
                        "DOWNLOAD %d [%s] WRITTEN TO %s (%s)",
                        resourceId,
                        mimeType,
                        targetFileUri.path,
                        formatHumanReadableSize(
                            targetFileSize,
                            context.resources
                        )
                    )
                }
                return targetFileUri
            }
        }
    }
    // Remove the remaining file chunk if download has been interrupted
    if (targetFileUri != null) removeFile(context, targetFileUri)
    throw DownloadInterruptedException("Download interrupted")
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
    // Folder already set (e.g. resume paused download, redownload from library)
    if (content.storageUri.isNotEmpty()) {
        // Reset storage URI if unreachable (will be re-created later in the method)
        val rootFolder = getDocumentFromTreeUriString(context, content.storageUri)
        if (null == rootFolder) content.clearStorageDoc()
        else {
            if (!testDownloadFolder(context, content.storageUri)) return null
            // Will come out null if invalid
            dir = getDocumentFromTreeUriString(context, content.storageUri)
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