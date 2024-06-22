package me.devsaki.hentoid.json

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DownloadMode
import me.devsaki.hentoid.database.domains.ErrorRecord
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.util.Preferences

@JsonClass(generateAdapter = true)
data class JsonContent(
    val url: String?,
    val title: String,
    val coverImageUrl: String,
    val qtyPages: Int,
    val uploadDate: Long,
    val downloadDate: Long,
    val downloadCompletionDate: Long?,
    val status: StatusContent,
    val site: Site,
    val favourite: Boolean?,
    val rating: Int?,
    val completed: Boolean?,
    val reads: Long?,
    val lastReadDate: Long?,
    val lastReadPageIndex: Int?,
    val downloadMode: Int?,
    val manuallyMerged: Boolean?,
    val bookPreferences: Map<String, String>?,
    var attributes: Map<AttributeType, List<JsonAttribute>>?,
    val imageFiles: List<JsonImageFile>,
    val chapters: List<JsonChapter>?,
    val errorRecords: List<JsonErrorRecord>?,
    val groups: List<JsonGroupItem>?,
    // Specific data for queued items
    val isFrozen: Boolean?
) {
    constructor(c: Content, keepImages: Boolean = true) : this(
        c.url,
        cleanup(c.title),
        c.coverImageUrl,
        c.qtyPages,
        c.uploadDate,
        c.downloadDate,
        c.downloadCompletionDate,
        c.status,
        c.site,
        c.favourite,
        c.rating,
        c.completed,
        c.reads,
        c.lastReadDate,
        c.lastReadPageIndex,
        c.downloadMode.value,
        c.manuallyMerged,
        c.bookPreferences,
        c.attributes.groupingBy { it.type }.fold(
            initialValueSelector = { _, _ -> mutableListOf<JsonAttribute>() },
            operation = { _, acc, a -> acc.add(JsonAttribute(a, c.site)); acc }
        ),
        if (keepImages) c.imageList.map { JsonImageFile(it) } else emptyList(),
        c.chaptersList.map { JsonChapter(it) },
        c.errorList.map { JsonErrorRecord(it) },
        c.groupItemList.filter {
            it.getGroup().run { grouping == Grouping.CUSTOM || hasCustomBookOrder }
        }.map { JsonGroupItem(it) },
        c.isFrozen
    )

    fun toEntity(dao: CollectionDAO? = null): Content {
        val result = Content(
            site = site,
            dbUrl = url ?: "",
            title = cleanup(title),
            coverImageUrl = coverImageUrl,
            qtyPages = qtyPages,
            uploadDate = uploadDate,
            downloadDate = downloadDate,
            downloadCompletionDate = if ((downloadCompletionDate ?: 0) > 0) downloadCompletionDate
                ?: 0 else downloadDate,
            status = status,
            favourite = favourite ?: false,
            rating = rating ?: 0,
            completed = completed ?: false,
            reads = reads ?: 0,
            lastReadDate = lastReadDate ?: 0,
            lastReadPageIndex = lastReadPageIndex ?: 0,
            bookPreferences = bookPreferences ?: HashMap(),
            downloadMode = DownloadMode.fromValue(
                downloadMode ?: Preferences.Constant.DL_ACTION_DL_PAGES
            ),
            manuallyMerged = manuallyMerged ?: false
        )
        result.isFrozen = isFrozen ?: false

        // ATTRIBUTES
        attributes?.let { attrs ->
            result.clearAttributes()
            for (jsonAttrList in attrs.values) {
                // Remove duplicates that may exist in old JSONs (cause weird single tags to appear in the DB)
                result.addAttributes(jsonAttrList.distinct().map { it.toEntity(site) })
            }
        }
        result.computeAuthor()

        // CHAPTERS
        val chps: MutableList<Chapter> = ArrayList()
        chapters?.forEach { chps.add(it.toEntity()) }
        result.setChapters(chps)

        // IMAGES
        val imgs = imageFiles.map { it.toEntity(chps) }.toMutableList()
        // If no cover, set the first page as cover
        val cover = imgs.firstOrNull { it.isCover }
        if ((null == cover || cover.url.isEmpty()) && imgs.size > 0) imgs[0].isCover = true

        result.setImageFiles(imgs)

        // Fix books with incorrect QtyPages that may exist in old JSONs
        if (qtyPages <= 0) result.qtyPages = imageFiles.size


        // ERROR RECORDS
        val errs: MutableList<ErrorRecord> = ArrayList()
        errorRecords?.forEach { errs.add(it.toEntity()) }
        result.setErrorLog(errs)


        // GROUPS
        if (dao != null) {
            groups?.forEach {
                val group = dao.selectGroupByName(it.groupingId, it.groupName)
                if (group != null) // Group already exists
                    result.groupItems.add(it.toEntity(result, group))
                else if (it.groupingId == Grouping.CUSTOM.id) { // Create group from scratch
                    val newGroup = Group(Grouping.CUSTOM, it.groupName, -1)
                    newGroup.id = dao.insertGroup(newGroup)
                    result.groupItems.add(it.toEntity(result, newGroup))
                }
            }
        }

        result.populateUniqueSiteId()
        result.computeSize()
        result.computeReadProgress()
        return result
    }
}