package me.devsaki.hentoid.util

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import me.devsaki.hentoid.core.THUMB_FILE_NAME
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.ContentParserFactory.getImageListParser
import me.devsaki.hentoid.util.exception.EmptyResultException
import me.devsaki.hentoid.util.exception.LimitReachedException
import me.devsaki.hentoid.util.file.ArchiveEntry
import me.devsaki.hentoid.util.file.InnerNameNumberArchiveComparator
import me.devsaki.hentoid.util.file.InnerNameNumberFileComparator
import me.devsaki.hentoid.util.file.getFileNameWithoutExtension
import me.devsaki.hentoid.util.file.listFiles
import me.devsaki.hentoid.util.file.removeFile
import me.devsaki.hentoid.util.image.MIME_IMAGE_GENERIC
import me.devsaki.hentoid.util.image.getMimeTypeFromPictureBinary
import me.devsaki.hentoid.util.image.imageNamesFilter
import me.devsaki.hentoid.util.network.CloudflareHelper.CloudflareProtectedException
import me.devsaki.hentoid.util.network.HEADER_COOKIE_KEY
import me.devsaki.hentoid.util.network.HEADER_REFERER_KEY
import me.devsaki.hentoid.util.network.fetchBodyFast
import me.devsaki.hentoid.util.network.fixUrl
import me.devsaki.hentoid.util.network.getCookies
import timber.log.Timber
import java.io.File
import java.io.IOException
import kotlin.math.max


// TODO empty this cache at some point
private val fileNameMatchCache: MutableMap<String, String> = HashMap()


fun formatCacheKey(img: ImageFile): String {
    return "img" + img.id
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
fun createImageListFromFiles(files: List<DocumentFile>): List<ImageFile> {
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
    archiveFileUri: Uri,
    files: List<ArchiveEntry>,
    targetStatus: StatusContent,
    startingOrder: Int,
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
        result.add(img)
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
 * Remove the given pages from the storage and the DB
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

    // Remove the pages from storage
    for (image in images) removeFile(context, image.fileUri.toUri())

    // Lists all relevant content
    val contents = images.map { it.content.targetId }.distinct()

    // Update content JSON if it exists (i.e. if book is not queued)
    for (contentId in contents) {
        val content = dao.selectContent(contentId)
        if (content != null && content.jsonUri.isNotEmpty()) updateJson(context, content)
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
    if (contentDownloadParamsStr.isEmpty()) {
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
    if (contentDownloadParamsStr.length > 2) {
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
fun testDownloadPictureFromPage(
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
fun testDownloadPicture(
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