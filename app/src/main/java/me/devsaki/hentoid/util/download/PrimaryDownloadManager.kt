package me.devsaki.hentoid.util.download

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.core.DOWNLOAD_CACHE_FOLDER
import me.devsaki.hentoid.core.HentoidApp.Companion.getInstance
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DownloadMode
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.util.exception.ArchiveException
import me.devsaki.hentoid.util.file.ArchiveStreamer
import me.devsaki.hentoid.util.file.InnerNameNumberArchiveComparator
import me.devsaki.hentoid.util.file.MIME_TYPE_CBZ
import me.devsaki.hentoid.util.file.PdfManager
import me.devsaki.hentoid.util.file.copyFile
import me.devsaki.hentoid.util.file.createFile
import me.devsaki.hentoid.util.file.getArchiveEntries
import me.devsaki.hentoid.util.file.getDocumentFromTreeUri
import me.devsaki.hentoid.util.file.getOrCreateCacheFolder
import me.devsaki.hentoid.util.file.removeFile
import me.devsaki.hentoid.util.file.tryCleanDirectory
import me.devsaki.hentoid.util.formatFolderName
import me.devsaki.hentoid.util.getArchivePdfThumbFileName
import me.devsaki.hentoid.util.getContainingFolder
import me.devsaki.hentoid.util.getOrCreateContentDownloadDir
import me.devsaki.hentoid.util.getOrCreateSiteDownloadDir
import me.devsaki.hentoid.util.image.isSupportedImage
import me.devsaki.hentoid.util.network.UriParts
import me.devsaki.hentoid.util.pause
import me.devsaki.hentoid.util.persistJson
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class PrimaryDownloadManager {
    private var downloadMode: DownloadMode? = null

    /**
     * Parent folder of the target downloads' location
     *   DownloadMode.DOWNLOAD or STREAM : book folder inside site folder
     *   DownloadMode.DOWNLOAD_ARCHIVE, DownloadMode.DOWNLOAD_ARCHIVE_FILE : site folder
     */
    private var downloadFolder: DocumentFile? = null

    /**
     * Target archive for downloads
     *   DownloadMode.DOWNLOAD or STREAM : null
     *   DownloadMode.DOWNLOAD_ARCHIVE, DownloadMode.DOWNLOAD_ARCHIVE_FILE : archive file
     */
    private var downloadArchive: Uri? = null

    private var archiveStreamer: ArchiveStreamer? = null

    private val localMatch: MutableMap<String, String> = ConcurrentHashMap()


    /**
     * Create download folder or archive
     */
    suspend fun createTargetLocation(context: Context, content: Content): Boolean =
        withContext(Dispatchers.IO) {
            Timber.d("Primary download manager : Init ${content.downloadMode}")

            downloadMode = content.downloadMode
            val locationResult =
                getDownloadLocation(getInstance(), content) ?: return@withContext false

            // Location is known already (e.g. resume download or redownload)
            locationResult.first?.let {
                content.setStorageDoc(it)
                if (downloadMode == DownloadMode.DOWNLOAD_ARCHIVE || downloadMode == DownloadMode.DOWNLOAD_ARCHIVE_FILE) {
                    downloadArchive = it.uri
                    // Compute parent folder of the archive
                    downloadFolder = content.getContainingFolder(context)?.let { parent ->
                        getDocumentFromTreeUri(context, parent)
                    }
                    if (downloadMode == DownloadMode.DOWNLOAD_ARCHIVE)
                        archiveStreamer = ArchiveStreamer(context, it.uri, true)
                } else {
                    downloadFolder = it
                }

                return@withContext true
            }

            // Location has to be computed
            val location = locationResult.second
            downloadFolder =
                if (downloadMode == DownloadMode.DOWNLOAD_ARCHIVE || downloadMode == DownloadMode.DOWNLOAD_ARCHIVE_FILE) {
                    getOrCreateSiteDownloadDir(context, location, content.site)
                } else {
                    getOrCreateContentDownloadDir(context, content, location)
                }

            downloadFolder?.let { dlFolder ->
                if (downloadMode == DownloadMode.DOWNLOAD_ARCHIVE) {
                    val archiveName = formatFolderName(content).first + ".cbz"
                    createFile(context, dlFolder.uri, archiveName, MIME_TYPE_CBZ).let { uri ->
                        getDocumentFromTreeUri(context, uri)?.let { content.setStorageDoc(it) }
                        downloadArchive = uri
                        archiveStreamer = ArchiveStreamer(context, uri, false)
                    }
                } else {
                    content.setStorageDoc(dlFolder)
                }
            } ?: throw IOException("Couldn't create download folder")

            return@withContext true
        }

    /**
     * Identify download folder
     */
    suspend fun getDownloadFolder(context: Context): Uri = withContext(Dispatchers.IO) {
        return@withContext if (downloadMode == DownloadMode.DOWNLOAD_ARCHIVE) {
            Uri.fromFile(
                getOrCreateCacheFolder(context, DOWNLOAD_CACHE_FOLDER)
                    ?: throw IOException("Couldn't initialize cache folder $DOWNLOAD_CACHE_FOLDER")
            )
        } else {
            downloadFolder?.uri ?: throw IllegalArgumentException("Download folder not set")
        }
    }

    /**
     * Process downloaded file
     */
    fun processDownloadedFile(context: Context, isCoverThumb: Boolean, uri: Uri) {
        if (downloadMode == DownloadMode.DOWNLOAD_ARCHIVE) {
            if (isCoverThumb) {
                // Copy thumb to thumb location
                val coverUri = copyFile(
                    context,
                    uri,
                    context.filesDir,
                    getArchivePdfThumbFileName(downloadArchive!!)
                )
                localMatch[uri.toString()] = coverUri.toString()
            } else archiveStreamer?.addFile(context, uri)
        } else {
            downloadFolder?.let { dlFolder ->
                // Check if the downloaded file is indeed inside the download folder...
                val dlFolderPath = dlFolder.uri.path ?: return
                val filePath = uri.path ?: return

                if (filePath.startsWith(dlFolderPath, true)) return

                Timber.i("Moving downloaded file to the target download folder")
                // ...if it's not (e.g. Ugoira assembled inside temp folder), move it
                val finalUri = copyFile(
                    context,
                    uri,
                    dlFolder,
                    uri.lastPathSegment ?: "",
                    forceCreate = true
                ) ?: throw IOException("Couldn't copy result ugoira file")
                localMatch[uri.toString()] = finalUri.toString()

                removeFile(context, uri)
            }
        }
    }

    /**
     * Manually trigger image location refresh when downloading an archive
     * NB : Optional; is done automatically when calling completeDownload
     *
     * @return true if at least one value has been updated; false if nothing changed
     */
    fun refreshLocation(imageList: Collection<ImageFile>): Map<Long, String> {
        if (downloadMode == DownloadMode.DOWNLOAD_ARCHIVE) {
            archiveStreamer?.let { return refreshLocation(imageList, it) }
        }
        return emptyMap()
    }

    /**
     * Process post-download actions
     */
    @Throws(ArchiveException::class)
    suspend fun completeDownload(context: Context, content: Content) = withContext(Dispatchers.IO) {
        if (downloadMode == DownloadMode.DOWNLOAD_ARCHIVE) {
            // Wait until archive streaming has completed (poll every 500ms)
            while (archiveStreamer?.queueActive ?: false) pause(500)

            archiveStreamer?.let { streamer ->
                // Throws exception if archiving has failed
                if (streamer.queueFailed)
                    throw ArchiveException(streamer.queueFailMessage)

                var imgList = content.imageList
                val newLocations = refreshLocation(imgList, streamer)
                if (newLocations.isNotEmpty()) {
                    imgList = imgList.map { img ->
                        newLocations[img.id]?.let { img.fileUri = it }
                        img
                    }
                    content.setImageFiles(imgList)
                }
            }
        }

        if (downloadMode == DownloadMode.DOWNLOAD_ARCHIVE_FILE) {
            content.imageList.firstOrNull()?.let { archive ->
                var uri = archive.fileUri.toUri()
                val uriParts = UriParts(uri)
                getDocumentFromTreeUri(context, uri)?.let { doc ->
                    if (doc.renameTo(formatFolderName(content).first + "." + uriParts.extension)) {
                        uri = doc.uri
                        content.setStorageDoc(doc)
                    } else {
                        throw IOException("Couldn't rename archive")
                    }
                }

                val entries = if (uriParts.extension.equals("pdf", true)) {
                    PdfManager().getEntries(context, uri)
                } else // Archive
                    context.getArchiveEntries(uri)

                val imgs = entries
                    .filter { !it.isFolder && isSupportedImage(it.path) }
                    .sortedWith(InnerNameNumberArchiveComparator())
                    .mapIndexed { i, e ->
                        ImageFile(
                            dbOrder = i,
                            fileUri = uri.toString() + File.separator + e.path,
                            dbUrl = uri.toString() + File.separator + e.path,
                            size = e.size,
                            status = StatusContent.DOWNLOADED
                        )
                    }
                imgs.forEach { it.computeName(imgs.size) }
                content.setImageFiles(imgs)
                content.qtyPages = imgs.size
            }
        }

        // Create JSON
        persistJson(context, content)

        // Empty cache
        getOrCreateCacheFolder(context, DOWNLOAD_CACHE_FOLDER)?.let {
            if (!tryCleanDirectory(it)) Timber.d("Failed to clean download cache")
        }

        clear()
    }


    /**
     * Refresh image location according to what's been archived
     *
     * @return true if at least one value has been updated; false if nothing changed
     */
    private fun refreshLocation(
        imageList: Collection<ImageFile>,
        streamer: ArchiveStreamer
    ): Map<Long, String> {
        val result = HashMap<Long, String>()
        imageList.forEach { img ->
            localMatch[img.fileUri]?.let {
                if (img.fileUri != it) result[img.id] = it
            }
            streamer.mappedUris[img.fileUri]?.let {
                if (img.fileUri != it) result[img.id] = it
            }
        }
        return result
    }

    fun clear() {
        Timber.d("Primary download manager : Clearing")
        downloadMode = null
        archiveStreamer?.close()
        archiveStreamer = null
        downloadFolder = null
        localMatch.clear()
    }
}