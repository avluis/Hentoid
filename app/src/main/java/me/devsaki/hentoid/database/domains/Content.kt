package me.devsaki.hentoid.database.domains

import android.text.TextUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Transient
import io.objectbox.annotation.Uid
import io.objectbox.converter.PropertyConverter
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne
import me.devsaki.hentoid.activities.sources.ASMHentaiActivity
import me.devsaki.hentoid.activities.sources.AllPornComicActivity
import me.devsaki.hentoid.activities.sources.BaseWebActivity
import me.devsaki.hentoid.activities.sources.DeviantArtActivity
import me.devsaki.hentoid.activities.sources.DoujinsActivity
import me.devsaki.hentoid.activities.sources.EHentaiActivity
import me.devsaki.hentoid.activities.sources.EdoujinActivity
import me.devsaki.hentoid.activities.sources.ExHentaiActivity
import me.devsaki.hentoid.activities.sources.HdPornComicsActivity
import me.devsaki.hentoid.activities.sources.Hentai2ReadActivity
import me.devsaki.hentoid.activities.sources.HentaifoxActivity
import me.devsaki.hentoid.activities.sources.HiperdexActivity
import me.devsaki.hentoid.activities.sources.HitomiActivity
import me.devsaki.hentoid.activities.sources.ImhentaiActivity
import me.devsaki.hentoid.activities.sources.KemonoActivity
import me.devsaki.hentoid.activities.sources.LusciousActivity
import me.devsaki.hentoid.activities.sources.MangagoActivity
import me.devsaki.hentoid.activities.sources.Manhwa18Activity
import me.devsaki.hentoid.activities.sources.ManhwaActivity
import me.devsaki.hentoid.activities.sources.MrmActivity
import me.devsaki.hentoid.activities.sources.MultpornActivity
import me.devsaki.hentoid.activities.sources.MusesActivity
import me.devsaki.hentoid.activities.sources.NhentaiActivity
import me.devsaki.hentoid.activities.sources.NovelcrowActivity
import me.devsaki.hentoid.activities.sources.PixivActivity
import me.devsaki.hentoid.activities.sources.PorncomixActivity
import me.devsaki.hentoid.activities.sources.PururinActivity
import me.devsaki.hentoid.activities.sources.SimplyActivity
import me.devsaki.hentoid.activities.sources.TmoActivity
import me.devsaki.hentoid.activities.sources.ToonilyActivity
import me.devsaki.hentoid.activities.sources.TsuminoActivity
import me.devsaki.hentoid.database.domains.ImageFile.Companion.fromImageUrl
import me.devsaki.hentoid.database.reach
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.Site.SiteConverter
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.util.MAP_STRINGS
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.file.getExtension
import me.devsaki.hentoid.util.file.isSupportedArchive
import me.devsaki.hentoid.util.formatAuthor
import me.devsaki.hentoid.util.hash64
import me.devsaki.hentoid.util.isNumeric
import me.devsaki.hentoid.util.jsonToObject
import me.devsaki.hentoid.util.network.UriParts
import me.devsaki.hentoid.util.network.getHttpProtocol
import me.devsaki.hentoid.util.serializeToJson
import timber.log.Timber
import java.io.IOException
import java.util.Objects

enum class DownloadMode(val value: Int) {
    DOWNLOAD(Settings.Value.DL_ACTION_DL_PAGES), // Download images
    STREAM(Settings.Value.DL_ACTION_STREAM), // Saves the book for on-demande viewing
    ASK(Settings.Value.DL_ACTION_ASK); // Saves the book for on-demande viewing)

    companion object {
        fun fromValue(v: Int): DownloadMode {
            return entries.firstOrNull { it.value == v } ?: DOWNLOAD
        }
    }
}

@Entity
data class Content(
    @Id
    var id: Long = 0,
    @Index
    @Uid(5800889076602216395L)
    var dbUrl: String = "",
    var uniqueSiteId: String = "", // Has to be queryable in DB, hence has to be a field
    var title: String = "",
    @Uid(2417271458982667075L)
    var dbAuthor: String = "",
    var coverImageUrl: String = "",
    var qtyPages: Int = 0,// Integer is actually unnecessary, but changing this to plain int requires a small DB model migration...
    var uploadDate: Long = 0,
    var downloadDate: Long = 0, // aka "Download date (processed)"
    var downloadCompletionDate: Long = 0, // aka "Download date (completed)"
    @Index
    @Convert(converter = StatusContent.Converter::class, dbType = Int::class)
    var status: StatusContent = StatusContent.UNHANDLED_ERROR,
    @Index
    @Convert(converter = SiteConverter::class, dbType = Long::class)
    var site: Site = Site.NONE,
    var storageUri: String = "",
    var favourite: Boolean = false,
    var rating: Int = 0,
    var completed: Boolean = false,
    var reads: Long = 0,
    var lastReadDate: Long = 0,
    var lastReadPageIndex: Int = 0,
    var manuallyMerged: Boolean = false,
    @Convert(converter = StringMapConverter::class, dbType = String::class)
    var bookPreferences: Map<String, String> = HashMap(),
    @Convert(converter = DownloadModeConverter::class, dbType = Int::class)
    var downloadMode: DownloadMode = DownloadMode.DOWNLOAD,
    var replacementTitle: String = "",
    // Aggregated data redundant with the sum of individual data contained in ImageFile
    // ObjectBox can't do the sum in a single Query, so here it is !
    var size: Long = 0,
    var readProgress: Float = 0f,
    // Temporary during SAVED state only
    var downloadParams: String = "",
    // Needs to be in the DB to keep the information when deletion takes a long time
    // and user navigates away; no need to save that into JSON
    var isBeingProcessed: Boolean = false,
    // Needs to be in the DB to optimize I/O
    // No need to save that into the JSON file itself, obviously
    var jsonUri: String = "",
    // Useful only during cleanup operations; no need to get it into the JSON
    @Index
    var isFlaggedForDeletion: Boolean = false,
    var lastEditDate: Long = 0
) {
    lateinit var attributes: ToMany<Attribute>

    @Backlink(to = "content")
    lateinit var imageFiles: ToMany<ImageFile>

    @Backlink(to = "content")
    lateinit var groupItems: ToMany<GroupItem>

    @Backlink(to = "content")
    lateinit var chapters: ToMany<Chapter>

    @Backlink(to = "content")
    lateinit var queueRecords: ToMany<QueueRecord>

    lateinit var contentToReplace: ToOne<Content>

    // Temporary during ERROR state only
    @Backlink(to = "content")
    lateinit var errorLog: ToMany<ErrorRecord>

    // Runtime attributes; no need to expose them for JSON persistence nor to persist them to DB
    @Transient
    var uniqueHash: Long = 0 // cached value of uniqueHash

    // number of downloaded pages; used to display the progress bar on the queue screen
    @Transient
    var progress: Long = 0

    // Number of downloaded bytes; used to display the size estimate on the queue screen
    @Transient
    var downloadedBytes: Long = 0

    @Transient
    var isFirst = false // True if current content is the first of its set in the DB query

    @Transient
    var isLast = false // True if current content is the last of its set in the DB query

    // Current number of download retries current content has gone through
    @Transient
    var numberDownloadRetries = 0

    // Read pages count fed by payload; only useful to update list display
    @Transient
    var dbReadPagesCount = -1

    @Transient
    var parentStorageUri: String? = null // Only used when importing

    @Transient
    private var storageDoc: DocumentFile? = null // Only used when importing

    // Only used when importing queued items (temp location to simplify JSON structure; definite storage in QueueRecord)
    @Transient
    var isFrozen = false

    @Transient
    var folderExists = true // Only used when loading the Content into the reader

    @Transient
    var isDynamic = false // Only used when loading the Content into the reader

    companion object {
        fun getWebActivityClass(site: Site): Class<out AppCompatActivity?> {
            return when (site) {
                Site.HITOMI -> HitomiActivity::class.java
                Site.NHENTAI -> NhentaiActivity::class.java
                Site.ASMHENTAI, Site.ASMHENTAI_COMICS -> ASMHentaiActivity::class.java
                Site.TSUMINO -> TsuminoActivity::class.java
                Site.PURURIN -> PururinActivity::class.java
                Site.EHENTAI -> EHentaiActivity::class.java
                Site.EXHENTAI -> ExHentaiActivity::class.java
                Site.MUSES -> MusesActivity::class.java
                Site.DOUJINS -> DoujinsActivity::class.java
                Site.LUSCIOUS -> LusciousActivity::class.java
                Site.PORNCOMIX -> PorncomixActivity::class.java
                Site.HENTAI2READ -> Hentai2ReadActivity::class.java
                Site.HENTAIFOX -> HentaifoxActivity::class.java
                Site.MRM -> MrmActivity::class.java
                Site.MANHWA -> ManhwaActivity::class.java
                Site.IMHENTAI -> ImhentaiActivity::class.java
                Site.TOONILY -> ToonilyActivity::class.java
                Site.ALLPORNCOMIC -> AllPornComicActivity::class.java
                Site.PIXIV -> PixivActivity::class.java
                Site.MANHWA18 -> Manhwa18Activity::class.java
                Site.MULTPORN -> MultpornActivity::class.java
                Site.SIMPLY -> SimplyActivity::class.java
                Site.HDPORNCOMICS -> HdPornComicsActivity::class.java
                Site.EDOUJIN -> EdoujinActivity::class.java
                Site.DEVIANTART -> DeviantArtActivity::class.java
                Site.MANGAGO -> MangagoActivity::class.java
                Site.HIPERDEX -> HiperdexActivity::class.java
                Site.NOVELCROW -> NovelcrowActivity::class.java
                Site.TMO -> TmoActivity::class.java
                Site.KEMONO -> KemonoActivity::class.java
                else -> BaseWebActivity::class.java
            }
        }

        fun getGalleryUrlFromId(site: Site, id: String, altCode: Int = 0): String {
            return when (site) {
                Site.HITOMI -> site.url + "/galleries/" + id + ".html"
                Site.NHENTAI, Site.ASMHENTAI, Site.ASMHENTAI_COMICS -> site.url + "/g/" + id + "/"
                Site.IMHENTAI, Site.HENTAIFOX -> site.url + "/gallery/" + id + "/"
                Site.HENTAICAFE -> site.url + "/hc.fyi/" + id
                Site.TSUMINO -> site.url + "/entry/" + id
                Site.NEXUS -> site.url + "/view/" + id
                Site.LUSCIOUS -> site.url.replace("manga", "albums") + id + "/"
                Site.HBROWSE -> site.url + id + "/c00001"
                Site.PIXIV -> if (1 == altCode) site.url + "users/" + id
                else site.url + "artworks/" + id

                Site.MULTPORN -> site.url + "node/" + id
                Site.HDPORNCOMICS -> site.url + "?p=" + id
                else -> site.url
            }
        }

        /**
         * Neutralizes the given cover URL to detect duplicate books
         *
         * @param url  Cover URL to neutralize
         * @param site Site the URL is taken from
         * @return Neutralized cover URL
         */
        fun getNeutralCoverUrlRoot(url: String, site: Site): String {
            if (url.isEmpty()) return url

            if (site == Site.MANHWA) {
                val parts = UriParts(url, true)
                // Remove the last part of the filename if it is formatted as "numberxnumber"
                var nameParts = parts.fileNameNoExt.split("-")
                val lastPartParts = nameParts[nameParts.size - 1].split("x")
                for (s in lastPartParts) if (!isNumeric(s)) return url

                nameParts = nameParts.subList(0, nameParts.size - 1)
                return parts.path + TextUtils.join("-", nameParts)
            } else {
                return url
            }
        }

        fun transformRawUrl(site: Site, url: String): String {
            when (site) {
                Site.TSUMINO -> return url.replace("/Read/Index", "")
                Site.PURURIN -> {
                    if (url.contains("/collection/")) return url
                    return url.replace(getHttpProtocol(url) + "://pururin.me/gallery", "")
                }

                Site.NHENTAI -> return url.replace(site.url, "").replace("/g/", "/")
                    .replaceFirst("/1/$".toRegex(), "/")

                Site.MUSES -> return url.replace(site.url, "")
                    .replace("https://comics.8muses.com", "")

                Site.MRM -> return url.replace(site.url, "").split("/")[0]

                Site.HITOMI -> return url.replace(site.url, "").replace("/reader", "")
                    .replace("/galleries", "")

                Site.MANHWA18, Site.IMHENTAI, Site.HENTAIFOX -> return url.replace(site.url, "")
                    .replace("/gallery", "").replace("/g/", "/")

                Site.PIXIV -> return url.replace(site.url, "").replace("^[a-z]{2}/".toRegex(), "")
                Site.ALLPORNCOMIC, Site.DOUJINS, Site.HENTAI2READ, Site.HBROWSE, Site.MANHWA, Site.MULTPORN, Site.TOONILY, Site.SIMPLY, Site.HDPORNCOMICS, Site.DEVIANTART -> return url.replace(
                    site.url,
                    ""
                )

                Site.EHENTAI, Site.EXHENTAI, Site.ASMHENTAI, Site.ASMHENTAI_COMICS, Site.ANCHIRA -> return url.replace(
                    site.url + "/g",
                    ""
                ).replace(site.url + "/api/v1/library", "")

                Site.EDOUJIN, Site.LUSCIOUS, Site.HIPERDEX -> return url.replace(
                    site.url.replace("/manga/", ""),
                    ""
                )

                Site.NOVELCROW -> return url.replace(
                    site.url.replace("/comic/", ""),
                    ""
                )

                Site.TMO -> return url.replace(
                    site.url.replace("/contents/", ""),
                    ""
                )

                Site.KEMONO -> return url.replace(site.url, "")

                Site.MANGAGO -> return url.replace(site.url + "read-manga/", "")
                Site.PORNCOMIX -> return url
                else -> return url
            }
        }
    }


    fun clearAttributes() {
        attributes.clear()
    }

    fun putAttributes(attributes: Collection<Attribute?>?) {
        // We do want to compare array references, not content
        if (attributes != null && attributes !== this.attributes) {
            this.attributes.clear()
            this.attributes.addAll(attributes)
        }
    }

    val attributeMap: AttributeMap
        get() {
            val result = AttributeMap()
            val list = attributes.reach(this)
            for (a in list) result.add(a)
            return result
        }

    fun putAttributes(attrs: AttributeMap) {
        attributes.clear()
        addAttributes(attrs)
    }

    fun addAttributes(attrs: AttributeMap): Content {
        for ((_, attrList) in attrs) {
            addAttributes(attrList)
        }
        return this
    }

    fun addAttributes(attrs: Collection<Attribute>): Content {
        attributes.addAll(attrs)
        return this
    }

    fun populateUniqueSiteId() {
        if (uniqueSiteId.isEmpty()) uniqueSiteId = computeUniqueSiteId()
    }


    private fun computeUniqueSiteId(): String {
        val paths: List<String>

        when (site) {
            Site.FAKKU -> return url.substring(url.lastIndexOf('/') + 1)
            Site.EHENTAI, Site.EXHENTAI, Site.PURURIN -> {
                if (url.contains("/collection/")) return ""
                paths = url.split("/")
                return if ((paths.size > 1)) paths[1] else paths[0]
            }

            Site.MRM, Site.HBROWSE -> return url.split("/")[0]

            Site.HITOMI -> {
                paths = url.split("/")
                val expression = if ((paths.size > 1)) paths[1] else paths[0]
                return expression.replace(".html", "")
            }

            Site.ASMHENTAI, Site.ASMHENTAI_COMICS, Site.NHENTAI, Site.PANDA, Site.TSUMINO
                -> return url.replace("/", "")

            Site.MUSES -> return url.replace("/comics/album/", "").replace("/", ".")
            Site.FAKKU2, Site.HENTAIFOX, Site.PORNCOMIX, Site.MANHWA, Site.TOONILY, Site.IMHENTAI, Site.ALLPORNCOMIC, Site.MULTPORN, Site.EDOUJIN, Site.SIMPLY, Site.DEVIANTART, Site.HIPERDEX, Site.NOVELCROW, Site.TMO -> {
                // Last part of the URL
                paths = url.split("/")
                return paths[paths.size - 1]
            }

            Site.KEMONO -> {
                // Service, user ID and content ID
                paths = url.split("/")
                val userIdx = paths.indexOf("user")
                val postIdx = paths.indexOf("post")
                val elements = ArrayList<String>()
                if (userIdx > -1) elements.add(paths[userIdx - 1]) // Service
                if (userIdx > -1) elements.add(paths[userIdx + 1]) // User id
                if (postIdx > -1) elements.add(paths[postIdx + 1]) // Post id

                return TextUtils.join("-", elements)
            }

            Site.DOUJINS -> {
                // ID is the last numeric part of the URL
                // e.g. lewd-title-ch-1-3-42116 -> 42116 is the ID
                val lastIndex = url.lastIndexOf('-')
                return url.substring(lastIndex + 1)
            }

            Site.LUSCIOUS -> {
                // ID is the last numeric part of the URL
                // e.g. /albums/lewd_title_ch_1_3_42116/ -> 42116 is the ID
                if (url.isNotEmpty()) {
                    val lastIndex = url.lastIndexOf('_')
                    return url.substring(lastIndex + 1, url.length - 1)
                } else return ""
            }

            Site.PIXIV ->                 // - If artworks, ID is the artwork ID
                // - If not, ID is the whole URL
                return if (url.contains("artworks")) url.substring(url.lastIndexOf('/') + 1)
                else url

            else -> return ""
        }
    }

    val galleryUrl: String
        get() {
            val galleryConst: String
            when (site) {
                Site.PURURIN, Site.IMHENTAI -> if (url.contains("/collection/")) return url
                else galleryConst = "/gallery"

                Site.HITOMI -> galleryConst = "/galleries"
                Site.ASMHENTAI, Site.ASMHENTAI_COMICS, Site.EHENTAI, Site.EXHENTAI, Site.NHENTAI, Site.ANCHIRA -> galleryConst =
                    "/g"

                Site.TSUMINO -> galleryConst = "/entry"
                Site.FAKKU2 -> galleryConst = "/hentai/"
                Site.EDOUJIN, Site.LUSCIOUS -> return site.url.replace("/manga/", "") + url
                Site.PORNCOMIX -> return url
                Site.HENTAIFOX -> {
                    var result = site.url + "/gallery$url".replace("//", "/")
                    if (result.endsWith("/1/")) result = result.substring(0, result.length - 3)
                    if (result.endsWith("/1")) result = result.substring(0, result.length - 2)
                    return result
                }

                Site.MANGAGO -> galleryConst = "read-manga/"
                else -> galleryConst = ""
            }
            return site.url + (galleryConst + url).replace("//", "/")
        }

    val readerUrl: String
        get() {
            when (site) {
                Site.HITOMI -> return site.url + "/reader" + url
                Site.TSUMINO -> return site.url + "/Read/Index" + url
                Site.ASMHENTAI -> return site.url + "/gallery" + url + "1/"
                Site.ASMHENTAI_COMICS -> return site.url + "/gallery" + url
                Site.PURURIN -> {
                    if (url.contains("/collection/")) return galleryUrl
                    return site.url + "/read/" + url.substring(1).replace("/", "/01/")
                }

                Site.FAKKU2 -> return "$galleryUrl/read/page/1"
                Site.MUSES -> return site.url.replace("album", "picture") + "/1"
                Site.LUSCIOUS -> return galleryUrl + "read/"
                Site.PORNCOMIX -> return if (galleryUrl.contains("/manga")) "$galleryUrl/p/1/"
                else "$galleryUrl#&gid=1&pid=1"

                Site.HENTAIFOX -> return (galleryUrl.replace(
                    "/gallery/",
                    "/g/"
                ) + "/1/").replace("//1/", "/1/")

                else -> return galleryUrl
            }
        }

    fun setRawUrl(value: String) {
        url = transformRawUrl(site, value)
    }

    var url: String
        get() = dbUrl
        set(value) {
            dbUrl = if (value.startsWith("http")) transformRawUrl(site, value)
            else value
            populateUniqueSiteId()
        }

    fun computeAuthor() {
        dbAuthor = formatAuthor(this)
    }

    var author: String
        get() {
            if (dbAuthor.isEmpty()) computeAuthor()
            return dbAuthor
        }
        set(value) {
            dbAuthor = value
        }

    val imageList: List<ImageFile>
        get() = imageFiles.reach(this)

    fun setImageFiles(imageFiles: List<ImageFile>?): Content {
        // We do want to compare array references, not content
        if (imageFiles != null && imageFiles !== this.imageFiles) {
            this.imageFiles.clear()
            this.imageFiles.addAll(imageFiles)
        }
        return this
    }

    val cover: ImageFile
        get() {
            val images = imageList
            if (images.isEmpty()) {
                val makeupCover = fromImageUrl(0, coverImageUrl, StatusContent.ONLINE, 1)
                makeupCover.imageHash = Long.MIN_VALUE // Makeup cover is unhashable
                return makeupCover
            }
            for (img in images) if (img.isCover) return img
            // If nothing found, get 1st page as cover
            return imageList.first()
        }

    val errorList: List<ErrorRecord>
        get() {
            return errorLog.reach(this)
        }

    fun setErrorLog(errorLog: List<ErrorRecord>?) {
        if (errorLog != null && errorLog != this.errorLog) {
            this.errorLog.clear()
            this.errorLog.addAll(errorLog)
        }
    }

    fun getPercent(): Double {
        return if (qtyPages > 0) progress * 1.0 / qtyPages
        else 0.0
    }

    fun computeProgress() {
        if (0L == progress) progress =
            imageList.count { it.status == StatusContent.DOWNLOADED || it.status == StatusContent.ERROR } * 1L
    }

    fun getBookSizeEstimate(): Double {
        if (downloadedBytes > 0) {
            computeProgress()
            if (progress > 3) return (downloadedBytes / getPercent()).toLong().toDouble()
        }
        return 0.0
    }

    fun computeDownloadedBytes() {
        if (0L == downloadedBytes) downloadedBytes = imageFiles.sumOf { it.size }
    }

    fun getNbDownloadedPages(): Int {
        return imageList.count { (it.status == StatusContent.DOWNLOADED || it.status == StatusContent.EXTERNAL || it.status == StatusContent.ONLINE) && it.isReadable }
    }

    private fun getDownloadedPagesSize(): Long {
        return imageList
            .filter { it.status == StatusContent.DOWNLOADED || it.status == StatusContent.EXTERNAL }
            .sumOf { it.size }
    }

    fun computeSize() {
        size = getDownloadedPagesSize()
    }

    fun setStorageDoc(storageDoc: DocumentFile): Content {
        this.storageUri = storageDoc.uri.toString()
        this.storageDoc = storageDoc
        return this
    }

    fun clearStorageDoc() {
        storageUri = ""
        storageDoc = null
    }

    fun getStorageDoc(): DocumentFile? {
        return storageDoc
    }

    fun increaseReads(): Content {
        reads++
        return this
    }

    // Warning : this assumes the URI contains the file name, which is not guaranteed (not in any spec)!
    val isArchive: Boolean
        get() = isSupportedArchive(storageUri)

    // Warning : this assumes the URI contains the file name, which is not guaranteed (not in any spec)!
    val isPdf: Boolean
        get() = getExtension(storageUri).equals("pdf", true)

    val groupItemList: List<GroupItem>
        get() = groupItems.reach(this)

    fun getGroupItems(grouping: Grouping): List<GroupItem> {
        return groupItemList
            .filterNot { null == it.linkedGroup }
            .filter { it.linkedGroup?.grouping == grouping }
    }

    private fun computeReadPagesCount(): Int {
        val countReadPages =
            imageFiles.filter(ImageFile::read).count(ImageFile::isReadable)
        return if (0 == countReadPages && lastReadPageIndex > 0) lastReadPageIndex // pre-v1.13 content
        else countReadPages // post v1.13 content
    }

    var readPagesCount: Int
        get() = if (dbReadPagesCount > -1) dbReadPagesCount else computeReadPagesCount()
        set(value) {
            dbReadPagesCount = value
        }

    fun computeReadProgress() {
        val denominator = imageList.count { it.isReadable }
        if (0 == denominator) {
            readProgress = 0f
            return
        }
        readProgress = computeReadPagesCount() * 1f / denominator
    }

    val chaptersList: List<Chapter>
        get() = chapters.reach(this)

    fun setChapters(chapters: List<Chapter?>?) {
        // We do want to compare array references, not content
        if (chapters != null && chapters !== this.chapters) {
            this.chapters.clear()
            this.chapters.addAll(chapters)
        }
    }

    fun clearChapters() {
        chapters.clear()
    }

    fun setContentIdToReplace(contentIdToReplace: Long) {
        contentToReplace.targetId = contentIdToReplace
    }

    fun increaseNumberDownloadRetries() {
        numberDownloadRetries++
    }

    class StringMapConverter : PropertyConverter<Map<String, String>, String> {
        override fun convertToEntityProperty(databaseValue: String?): Map<String, String> {
            if (null == databaseValue) return java.util.HashMap()

            try {
                return jsonToObject<Map<String, String>>(databaseValue, MAP_STRINGS)!!
            } catch (e: IOException) {
                Timber.w(e)
                return java.util.HashMap()
            }
        }

        override fun convertToDatabaseValue(entityProperty: Map<String, String>): String {
            return serializeToJson(entityProperty, MAP_STRINGS)
        }
    }

    class DownloadModeConverter : PropertyConverter<DownloadMode, Int> {
        override fun convertToEntityProperty(databaseValue: Int?): DownloadMode {
            if (databaseValue == null) return DownloadMode.DOWNLOAD
            return DownloadMode.fromValue(databaseValue)
        }

        override fun convertToDatabaseValue(entityProperty: DownloadMode): Int {
            return entityProperty.value
        }
    }

    // Hashcode (and by consequence equals) has to take into account fields that get visually updated on the app UI
    // If not done, FastAdapter's PagedItemListImpl cache won't detect changes to the object
    // and items won't be visually updated on screen
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val content = other as Content
        return favourite == content.favourite
                && rating == content.rating
                && completed == content.completed
                && downloadDate == content.downloadDate  // To differentiate external books that have no URL
                && size == content.size // To differentiate external books that have no URL
                && lastReadDate == content.lastReadDate
                && isBeingProcessed == content.isBeingProcessed
                && url == content.url
                && coverImageUrl == content.coverImageUrl
                && site == content.site
                && downloadMode == content.downloadMode
                && lastEditDate == content.lastEditDate
                && qtyPages == content.qtyPages
    }

    override fun hashCode(): Int {
        return Objects.hash(
            favourite,
            rating,
            completed,
            downloadDate,
            size,
            lastReadDate,
            isBeingProcessed,
            url,
            coverImageUrl,
            site,
            downloadMode,
            lastEditDate,
            qtyPages
        )
    }

    fun uniqueHash(): Long {
        if (0L == uniqueHash) uniqueHash = hash64("$id.$uniqueSiteId".toByteArray())
        return uniqueHash
    }
}