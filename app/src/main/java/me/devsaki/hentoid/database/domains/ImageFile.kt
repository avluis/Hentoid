package me.devsaki.hentoid.database.domains

import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Transient
import io.objectbox.annotation.Uid
import io.objectbox.relation.ToOne
import me.devsaki.hentoid.adapters.ImagePagerAdapter
import me.devsaki.hentoid.core.EXT_THUMB_FILE_PREFIX
import me.devsaki.hentoid.core.THUMB_FILE_NAME
import me.devsaki.hentoid.database.isReachable
import me.devsaki.hentoid.database.safeReach
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.util.file.getSupportedExtensions
import me.devsaki.hentoid.util.hash64
import me.devsaki.hentoid.util.isInLibrary
import net.greypanther.natsort.CaseInsensitiveSimpleNaturalComparator
import java.io.File
import java.util.Locale
import java.util.Objects
import kotlin.math.floor
import kotlin.math.log10

@Entity
data class ImageFile(
    @Id
    var id: Long = 0,
    @Uid(4786164809804019689L)
    var dbOrder: Int = -1,
    @Uid(8847017078500757224L)
    var dbUrl: String = "",
    @Uid(4756936261641767706L)
    var dbPageUrl: String = "",
    var name: String = "",
    var fileUri: String = "",
    var read: Boolean = false,
    @Index // Added to speed up the "favourite pages" book
    var favourite: Boolean = false,
    @Uid(946562145146984364L)
    var dbIsCover: Boolean = false,
    @Convert(converter = StatusContent.Converter::class, dbType = Integer::class)
    var status: StatusContent = StatusContent.UNHANDLED_ERROR,
    var size: Long = 0,
    var imageHash: Long = 0,
    var isTransformed: Boolean = false,
    // Useful only during cleanup operations; no need to get it into the JSON
    @Index
    var isFlaggedForDeletion: Boolean = false
) {
    lateinit var content: ToOne<Content>
    lateinit var chapter: ToOne<Chapter>

    // Temporary attributes during SAVED state only; no need to expose them for JSON persistence
    var downloadParams = ""


    // WARNING : Update copy constructor when adding attributes

    // == Runtime attributes; no need to expose them nor to persist them
    // cached value of uniqueHash
    @Transient
    var uniqueHash: Long = 0

    // Display order of the image in the image viewer (read-time only; 0-indexed)
    @Transient
    var displayOrder = 0

    // Uri to use to display images in the viewer (read-time only)
    @Transient
    var displayUri = ""

    // Uri to use to display images in the viewer (read-time only)
    @Transient
    var imageType = ImagePagerAdapter.ImageType.IMG_TYPE_UNSET

    // Backup URL for that picture (download-time only)
    @Transient
    var backupUrl = ""

    // Has the image been read from a backup URL ? (download-time only)
    @Transient
    var isBackup = false

    // Force refresh (read-time only)
    @Transient
    var isForceRefresh: Boolean = false


    constructor(img: ImageFile, populateContent: Boolean = true, populateChapter: Boolean = true) : this(
        img.id,
        img.order,
        img.url,
        img.pageUrl,
        img.name,
        img.fileUri,
        img.read,
        img.favourite,
        img.isCover,
        img.status,
        img.size,
        img.imageHash,
        img.isTransformed
    ) {
        this.downloadParams = img.downloadParams
        this.uniqueHash = img.uniqueHash
        this.displayOrder = img.displayOrder
        this.displayUri = img.displayUri
        this.imageType = img.imageType
        this.backupUrl = img.backupUrl
        this.isBackup = img.isBackup
        this.isForceRefresh = img.isForceRefresh

        if (populateContent) {
            if (img.content.isReachable(img)) {
                content.setTarget(img.content.target)
            } else {
                content.setTargetId(img.content.targetId)
            }
        }
        if (populateChapter) {
            if (img.chapter.isReachable(img)) {
                chapter.setTarget(img.chapter.target)
            } else {
                chapter.setTargetId(img.chapter.targetId)
            }
        }
    }

    companion object {
        fun fromImageUrl(
            order: Int,
            url: String,
            status: StatusContent,
            maxPages: Int
        ): ImageFile {
            val result = ImageFile(dbOrder = order, status = status, dbUrl = url)
            initName(result, maxPages, null)
            return result
        }

        fun fromImageUrl(
            order: Int,
            url: String,
            status: StatusContent,
            name: String
        ): ImageFile {
            val result = ImageFile(dbOrder = order, status = status, dbUrl = url)
            initName(result, -1, name)
            return result
        }

        fun fromPageUrl(
            order: Int,
            url: String,
            status: StatusContent,
            maxPages: Int
        ): ImageFile {
            val result = ImageFile(dbOrder = order, status = status, dbPageUrl = url)
            initName(result, maxPages, null)
            return result
        }

        fun fromPageUrl(
            order: Int,
            url: String,
            status: StatusContent,
            name: String
        ): ImageFile {
            val result = ImageFile(dbOrder = order, status = status, dbPageUrl = url)
            initName(result, -1, name)
            return result
        }

        fun newCover(url: String, status: StatusContent): ImageFile {
            val result = ImageFile(
                dbOrder = 0,
                dbUrl = url,
                status = status,
                name = THUMB_FILE_NAME,
                dbIsCover = true,
                read = true,
            )
            return result
        }

        private fun initName(
            imgFile: ImageFile,
            maxPages: Int,
            name: String?
        ) {
            if (name.isNullOrEmpty()) {
                imgFile.computeName(maxPages)
            } else {
                imgFile.name = name
            }
        }
    }

    var isCover: Boolean
        get() = dbIsCover
        set(value) {
            dbIsCover = value
            if (value) read = true
            uniqueHash = 0
        }

    var url: String
        get() = dbUrl
        set(value) {
            dbUrl = value
            uniqueHash = 0
        }

    var pageUrl: String
        get() = dbPageUrl
        set(value) {
            dbPageUrl = value
            uniqueHash = 0
        }


    var order: Int
        get() = dbOrder
        set(value) {
            dbOrder = value
            uniqueHash = 0
        }

    var contentId: Long
        get() = content.targetId
        set(value) {
            content.targetId = value
        }

    val linkedChapter: Chapter?
        get() = chapter.safeReach(this)

    val linkedContent: Content?
        get() = content.safeReach(this)

    var chapterId: Long
        get() = chapter.targetId
        set(value) {
            chapter.targetId = value
            uniqueHash = 0
        }

    fun setChapter(chapter: Chapter?) {
        this.chapter.target = chapter
        uniqueHash = 0
    }

    fun computeName(maxPages: Int): ImageFile {
        return computeNameDigits((floor(log10(maxPages.toDouble()))).toInt() + 1)
    }

    private fun computeNameDigits(nbMaxDigits: Int): ImageFile {
        name = String.format(Locale.ENGLISH, "%0${nbMaxDigits}d", order)
        return this
    }

    val isReadable: Boolean
        get() {
            return !name.startsWith(THUMB_FILE_NAME) && !name.startsWith(EXT_THUMB_FILE_PREFIX)
        }

    val usableUri: String
        get() {
            if (displayUri.isNotBlank()) return displayUri
            if (!isArchived && !isPdf && isInLibrary(status) && fileUri.isNotBlank()) return fileUri
            if (url.isNotBlank() && url.startsWith("http")) return url
            return if (isCover) linkedContent?.coverImageUrl ?: "" else ""
        }

    val isArchived: Boolean
        get() = isContainedInFile(fileUri, getSupportedExtensions())

    val isPdf: Boolean
        get() = isContainedInFile(fileUri, setOf("pdf"))

    private fun isContainedInFile(uri: String, exts: Set<String>): Boolean {
        val lowerUri = uri.lowercase(Locale.getDefault())
        for (ext in exts) {
            if (lowerUri.contains("." + ext + File.separator)) return true
        }
        return false
    }

    val isOnline: Boolean
        get() {
            return usableUri.startsWith("http")
        }

    // Defensive programming, as certain crashes report null values there
    @Suppress("SENSELESS_COMPARISON")
    val needsPageParsing: Boolean
        get() {
            if (null == pageUrl) return true
            return pageUrl.isNotEmpty() && (null == url || url.isEmpty())
        }

    // Hashcode (and by consequence equals) has to take into account fields that get visually updated on the app UI
    // If not done, FastAdapter's PagedItemListImpl cache won't detect changes to the object
    // and items won't be visually updated on screen
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val imageFile = other as ImageFile
        if (imageFile.isForceRefresh || isForceRefresh) return false

        return id == imageFile.id && url == imageFile.url && pageUrl == imageFile.pageUrl && fileUri == imageFile.fileUri && displayUri == imageFile.displayUri &&  imageType == imageFile.imageType && order == imageFile.order && isCover == imageFile.isCover && favourite == imageFile.favourite && chapter.targetId == imageFile.chapter.targetId
    }

    override fun hashCode(): Int {
        // Must be an int32, so we're bound to use Objects.hash
        return Objects.hash(
            id,
            pageUrl,
            url,
            fileUri,
            displayUri,
            imageType,
            order,
            isCover,
            favourite,
            chapter.targetId,
            isForceRefresh
        )
    }

    fun uniqueHash(): Long {
        if (0L == uniqueHash) uniqueHash =
            hash64((id.toString() + "." + pageUrl + "." + url + "." + order + "." + isCover + "." + chapter.targetId).toByteArray())
        return uniqueHash
    }

    /**
     * Comparator to be used to sort ImageFiles according to the Uri of their files
     */
    class UriImageFileComparator : Comparator<ImageFile> {
        override fun compare(o1: ImageFile, o2: ImageFile): Int {
            return CaseInsensitiveSimpleNaturalComparator.getInstance<CharSequence>()
                .compare(o1.fileUri, o2.fileUri)
        }
    }
}