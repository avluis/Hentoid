package me.devsaki.hentoid.util.download

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.util.Pair
import me.devsaki.hentoid.core.Consumer
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.StringHelper
import me.devsaki.hentoid.util.download.DownloadSpeedLimiter.take
import me.devsaki.hentoid.util.exception.DownloadInterruptedException
import me.devsaki.hentoid.util.exception.NetworkingException
import me.devsaki.hentoid.util.exception.UnsupportedContentException
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.file.FileHelper.MemoryUsageFigures
import me.devsaki.hentoid.util.image.ImageHelper.getMimeTypeFromPictureBinary
import me.devsaki.hentoid.util.network.HttpHelper
import org.jsoup.nodes.Document
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.text.Charsets.UTF_8

object DownloadHelper {

    // NB : Actual size of read bytes may be smaller
    private const val DL_IO_BUFFER_SIZE_B = 50 * 1024

    /**
     * Download the given resource to the given disk location
     *
     * @param site              Site to use params for
     * @param rawUrl            URL to download from
     * @param resourceId        ID of the corresponding resource (for logging purposes only)
     * @param requestHeaders    HTTP request headers to use
     * @param targetFolderUri   Uri of the folder where to save the downloaded resource
     * @param targetFileName    Name of the file to save the downloaded resource
     * @param interruptDownload Used to interrupt the download whenever the value switches to true. If that happens, the file will be deleted.
     * @param forceMimeType     Forced mime-type of the downloaded resource (null for auto-set)
     * @param failFast          True for a shorter read timeout; false for a regular, patient download
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
    fun downloadToFile(
        context: Context,
        site: Site,
        rawUrl: String,
        resourceId: Int,
        requestHeaders: List<Pair<String, String>>?,
        targetFolderUri: Uri,
        targetFileName: String,
        interruptDownload: AtomicBoolean,
        forceMimeType: String? = null,
        failFast: Boolean = true,
        notifyProgress: Consumer<Float>? = null
    ): Pair<Uri, String> {
        Helper.assertNonUiThread()
        val url = HttpHelper.fixUrl(rawUrl, site.url)
        if (interruptDownload.get()) throw DownloadInterruptedException("Download interrupted")
        Timber.d("DOWNLOADING %d %s", resourceId, url)
        val response = if (failFast) HttpHelper.getOnlineResourceFast(
            url,
            requestHeaders,
            site.useMobileAgent(),
            site.useHentoidAgent(),
            site.useWebviewAgent()
        ) else HttpHelper.getOnlineResourceDownloader(
            url,
            requestHeaders,
            site.useMobileAgent(),
            site.useHentoidAgent(),
            site.useWebviewAgent()
        )
        Timber.d("DOWNLOADING %d - RESPONSE %s", resourceId, response.code)
        if (response.code >= 300) throw NetworkingException(
            response.code,
            "Network error " + response.code,
            null
        )
        val body = response.body
            ?: throw IOException("Could not read response : empty body for $url")
        var size = body.contentLength()
        if (size < 1) size = 1
        val sizeStr = FileHelper.formatHumanReadableSize(size, context.resources)
        Timber.d(
            "WRITING DOWNLOAD %d TO %s/%s (size %s)",
            resourceId,
            targetFolderUri.path,
            targetFileName,
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
                            val contentType = response.header(HttpHelper.HEADER_CONTENT_TYPE) ?: ""
                            mimeType = getMimeTypeFromStream(buffer, len, contentType, url, sizeStr)
                        }
                        // Create target file and output stream
                        targetFileUri =
                            createFile(context, targetFolderUri, targetFileName, mimeType)
                        out = FileHelper.getOutputStream(context, targetFileUri!!)
                    }
                    if (len > 0 && out != null) {
                        out!!.write(buffer, 0, len)
                        if (notifyProgress != null && 0 == iteration % notificationResolution)
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
                            FileHelper.fileSizeFromUri(context, targetFileUri!!)
                        Timber.d(
                            "DOWNLOAD %d [%s] WRITTEN TO %s (%s)",
                            resourceId,
                            mimeType,
                            targetFileUri!!.path,
                            FileHelper.formatHumanReadableSize(
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
        if (targetFileUri != null) FileHelper.removeFile(context, targetFileUri!!)
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
        if (result.isEmpty() || result.endsWith("/*")) {
            if (contentType.contains("text/")) {
                val message = buffer.copyOfRange(0, bufLength).toString(UTF_8).trim()
                throw UnsupportedContentException("Message received from $url : $message")
            }
            throw UnsupportedContentException("Invalid mime-type received from $url (size=$size; content-type=$contentType)")
        }
        return result
    }

    @Throws(IOException::class)
    private fun createFile(
        context: Context,
        targetFolderUri: Uri,
        targetFileName: String,
        mimeType: String
    ): Uri? {
        var targetFileNameFinal =
            targetFileName + "." + FileHelper.getExtensionFromMimeType(mimeType)
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
            val targetFolder =
                FileHelper.getDocumentFromTreeUriString(context, targetFolderUri.toString())
            if (targetFolder != null) {
                val file = FileHelper.findOrCreateDocumentFile(
                    context,
                    targetFolder,
                    mimeType,
                    targetFileNameFinal
                )
                file?.uri
                    ?: throw IOException("Could not create file $targetFileNameFinal : creation failed")
            } else {
                throw IOException("Could not create file $targetFileNameFinal : $targetFolderUri does not exist")
            }
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
                val canonicalDigitsStr = StringHelper.keepDigits(canonicalUrl)
                val canonicalDigits =
                    if (canonicalDigitsStr.isEmpty()) 0 else canonicalDigitsStr.toInt()
                val ogDigitsStr = StringHelper.keepDigits(ogUrl)
                val ogDigits = if (ogDigitsStr.isEmpty()) 0 else ogDigitsStr.toInt()
                if (canonicalDigits > ogDigits) canonicalUrl else ogUrl
            } else {
                canonicalUrl.ifEmpty { ogUrl }
            }
        return finalUrl
    }

    fun selectDownloadLocation(context: Context): StorageLocation {
        val uriStr1 = Preferences.getStorageUri(StorageLocation.PRIMARY_1).trim { it <= ' ' }
        val uriStr2 = Preferences.getStorageUri(StorageLocation.PRIMARY_2).trim { it <= ' ' }

        // Obvious cases
        if (uriStr1.isEmpty() && uriStr2.isEmpty()) return StorageLocation.NONE
        if (uriStr1.isNotEmpty() && uriStr2.isEmpty()) return StorageLocation.PRIMARY_1
        if (uriStr1.isEmpty()) return StorageLocation.PRIMARY_2

        // Broken cases
        val root1 = FileHelper.getDocumentFromTreeUriString(context, uriStr1)
        val root2 = FileHelper.getDocumentFromTreeUriString(context, uriStr2)
        if (null == root1 && null == root2) return StorageLocation.NONE
        if (root1 != null && null == root2) return StorageLocation.PRIMARY_1
        if (null == root1) return StorageLocation.PRIMARY_2

        // Apply download strategy
        val memUsage1 = MemoryUsageFigures(context, root1)
        val memUsage2 = MemoryUsageFigures(context, root2!!)
        val strategy = Preferences.getStorageDownloadStrategy()
        return if (Preferences.Constant.STORAGE_FILL_FALLOVER == strategy) {
            if (100 - memUsage1.freeUsageRatio100 > Preferences.getStorageSwitchThresholdPc()) StorageLocation.PRIMARY_2 else StorageLocation.PRIMARY_1
        } else {
            if (memUsage1.getfreeUsageBytes() > memUsage2.getfreeUsageBytes()) StorageLocation.PRIMARY_1 else StorageLocation.PRIMARY_2
        }
    }
}