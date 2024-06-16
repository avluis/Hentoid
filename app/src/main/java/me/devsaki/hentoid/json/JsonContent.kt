package me.devsaki.hentoid.json

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ErrorRecord
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.util.createCover

@JsonClass(generateAdapter = true)
data class JsonContent(
    val url: String?,
    val title: String,
    val coverImageUrl: String,
    val qtyPages: Int,
    val uploadDate: Long,
    val downloadDate: Long,
    val downloadCompletionDate: Long,
    val status: StatusContent,
    val site: Site,
    val favourite: Boolean,
    val rating: Int,
    val completed: Boolean,
    val reads: Long,
    val lastReadDate: Long,
    val lastReadPageIndex: Int,
    val downloadMode: Int,
    val manuallyMerged: Boolean,
    val bookPreferences: Map<String, String>,
    var attributes: Map<AttributeType, List<JsonAttribute>>?,
    val imageFiles: List<JsonImageFile>,
    val chapters: List<JsonChapter>,
    val errorRecords: List<JsonErrorRecord>,
    val groups: List<JsonGroupItem>,
    // Specific data for queued items
    val isFrozen: Boolean
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
        c.isFavourite,
        c.rating,
        c.isCompleted,
        c.reads,
        c.lastReadDate,
        c.lastReadPageIndex,
        c.downloadMode,
        c.isManuallyMerged,
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
        val result = Content()

        result.setSite(site)
        result.setUrl(url)
        result.setTitle(cleanup(title))
        result.setCoverImageUrl(coverImageUrl)
        result.setQtyPages(qtyPages)
        result.setUploadDate(uploadDate)
        result.setDownloadDate(downloadDate)
        if (downloadCompletionDate > 0) result.setDownloadCompletionDate(downloadCompletionDate)
        else result.setDownloadCompletionDate(downloadDate) // When the field is absent from the JSON

        result.setStatus(status)
        result.setFavourite(favourite)
        result.rating = rating
        result.setCompleted(completed)
        result.setReads(reads)
        result.setLastReadDate(lastReadDate)
        result.lastReadPageIndex = lastReadPageIndex
        result.bookPreferences = bookPreferences
        result.setDownloadMode(downloadMode)
        result.isManuallyMerged = manuallyMerged
        result.isFrozen = isFrozen


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
        for (chp in chapters) chps.add(chp.toEntity())
        result.setChapters(chps)


        // IMAGES
        val imgs = imageFiles.map { it.toEntity(chps) }.toMutableList()
        // Fix empty covers
        val cover = imgs.firstOrNull { it.isCover }
        if (null == cover || cover.url.isEmpty()) createCover(imgs)

        result.setImageFiles(imgs)

        // Fix books with incorrect QtyPages that may exist in old JSONs
        if (qtyPages <= 0) result.setQtyPages(imageFiles.size)


        // ERROR RECORDS
        val errs: MutableList<ErrorRecord> = ArrayList()
        for (err in errorRecords) errs.add(err.toEntity())
        result.setErrorLog(errs)


        // GROUPS
        if (dao != null) {
            for (gi in groups) {
                val group = dao.selectGroupByName(gi.groupingId, gi.groupName)
                if (group != null) // Group already exists
                    result.groupItems.add(gi.toEntity(result, group))
                else if (gi.groupingId == Grouping.CUSTOM.id) { // Create group from scratch
                    val newGroup = Group(Grouping.CUSTOM, gi.groupName, -1)
                    newGroup.id = dao.insertGroup(newGroup)
                    result.groupItems.add(gi.toEntity(result, newGroup))
                }
            }
        }

        result.populateUniqueSiteId()
        result.computeSize()
        result.computeReadProgress()
        return result
    }
}