package me.devsaki.hentoid.database

import android.util.SparseIntArray
import androidx.lifecycle.LiveData
import androidx.paging.PagedList
import me.devsaki.hentoid.core.Consumer
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ErrorRecord
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.database.domains.GroupItem
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.database.domains.QueueRecord
import me.devsaki.hentoid.database.domains.RenamingRule
import me.devsaki.hentoid.database.domains.SearchRecord
import me.devsaki.hentoid.database.domains.SiteBookmark
import me.devsaki.hentoid.database.domains.SiteHistory
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.util.AttributeQueryResult
import me.devsaki.hentoid.util.Location
import me.devsaki.hentoid.util.QueuePosition
import me.devsaki.hentoid.util.Type
import me.devsaki.hentoid.widget.ContentSearchManager.ContentSearchBundle

interface CollectionDAO {

    // CONTENT
    // Low-level operations
    fun selectNoContent(): LiveData<PagedList<Content>>

    fun selectContent(id: Long): Content?

    fun selectContent(id: LongArray): List<Content>

    fun selectContentByStorageUri(folderUri: String, onlyFlagged: Boolean): Content?

    fun selectContentByStorageRootUri(rootUri: String): List<Content>

    fun selectContentByUrlOrCover(site: Site, contentUrl: String, coverUrl: String?): Content?

    fun selectContentsByUrl(site: Site, contentUrl: String): Set<Content>

    fun selectContentsByQtyPageAndSize(qtyPage: Int, size: Long): Set<Content>

    fun selectAllSourceUrls(site: Site): Set<String>

    fun selectAllMergedUrls(site: Site): Set<String>

    fun searchTitlesWith(word: String, contentStatusCodes: IntArray): List<Content>

    fun insertContent(content: Content): Long

    fun insertContentCore(content: Content): Long

    fun updateContentStatus(updateFrom: StatusContent, updateTo: StatusContent)

    fun updateContentProcessedFlag(contentId: Long, flag: Boolean)

    fun updateContentsProcessedFlagById(contentIds: List<Long>, flag: Boolean)

    fun updateContentsProcessedFlag(contents: List<Content>, flag: Boolean)

    fun deleteContent(content: Content)

    fun selectErrorRecordByContentId(contentId: Long): List<ErrorRecord>

    fun insertErrorRecord(record: ErrorRecord)

    fun deleteErrorRecords(contentId: Long)

    fun clearDownloadParams(contentId: Long)

    fun shuffleContent()


    // MASS OPERATIONS
    // Primary library ("internal books")
    fun countAllInternalBooks(rootPath: String, favsOnly: Boolean): Long

    fun streamAllInternalBooks(rootPath: String, favsOnly: Boolean, consumer: Consumer<Content>)

    fun flagAllInternalBooks(rootPath: String, includePlaceholders: Boolean)

    fun deleteAllInternalContents(rootPath: String, resetRemainingImagesStatus: Boolean)

    // Queued books
    fun flagAllErrorBooksWithJson()

    fun countAllQueueBooks(): Long

    fun countAllQueueBooksLive(): LiveData<Int>

    fun deleteAllQueuedBooks()


    // Flagging
    fun deleteAllFlaggedContents(resetRemainingImagesStatus: Boolean, pathRoot: String?)


    // External library
    fun deleteAllExternalBooks()

    fun flagAllExternalContents()


    // GROUPS
    fun selectGroups(groupIds: LongArray): List<Group>

    fun selectGroupsLive(
        grouping: Int,
        query: String?,
        orderField: Int,
        orderDesc: Boolean,
        artistGroupVisibility: Int,
        groupFavouritesOnly: Boolean,
        groupNonFavouritesOnly: Boolean,
        filterRating: Int
    ): LiveData<List<Group>>

    fun selectGroups(grouping: Int): List<Group>

    fun selectGroups(grouping: Int, subType: Int): List<Group>

    fun selectEditedGroups(grouping: Int): List<Group>

    fun selectGroup(groupId: Long): Group?

    fun selectGroupByName(grouping: Int, name: String): Group?

    fun countGroupsFor(grouping: Grouping): Long

    fun insertGroup(group: Group): Long

    fun deleteGroup(groupId: Long)

    fun deleteAllGroups(grouping: Grouping)

    fun flagAllGroups(grouping: Grouping)

    fun deleteAllFlaggedGroups()

    fun deleteEmptyArtistGroups()

    fun insertGroupItem(item: GroupItem): Long

    fun selectGroupItems(contentId: Long, grouping: Grouping): List<GroupItem>

    fun deleteGroupItems(groupItemIds: List<Long>)


    // High-level queries (internal and external locations)
    fun selectStoredFavContentIds(bookFavs: Boolean, groupFavs: Boolean): Set<Long>

    fun selectContentWithUnhashedCovers(): List<Content>

    fun countContentWithUnhashedCovers(): Long

    fun streamStoredContent(
        includeQueued: Boolean,
        orderField: Int,
        orderDesc: Boolean,
        consumer: Consumer<Content>
    )


    fun selectRecentBookIds(searchBundle: ContentSearchBundle): List<Long>

    fun searchBookIds(searchBundle: ContentSearchBundle, metadata: Set<Attribute>): List<Long>

    fun searchBookIdsUniversal(searchBundle: ContentSearchBundle): List<Long>


    fun selectRecentBooks(searchBundle: ContentSearchBundle): LiveData<PagedList<Content>>

    fun searchBooks(
        searchBundle: ContentSearchBundle,
        metadata: Set<Attribute>
    ): LiveData<PagedList<Content>>

    fun searchBooksUniversal(searchBundle: ContentSearchBundle): LiveData<PagedList<Content>>


    fun selectErrorContentLive(): LiveData<List<Content>>

    fun selectErrorContentLive(query: String?, source: Site?): LiveData<List<Content>>

    fun selectErrorContent(): List<Content>


    fun countBooks(
        groupId: Long,
        metadata: Set<Attribute>?,
        location: Location,
        contentType: Type
    ): LiveData<Int>

    fun countAllBooksLive(): LiveData<Int>


    // IMAGEFILES
    fun insertImageFile(img: ImageFile)

    fun insertImageFiles(imgs: List<ImageFile>)

    fun replaceImageList(contentId: Long, newList: List<ImageFile>)

    fun updateImageContentStatus(
        contentId: Long,
        updateFrom: StatusContent?,
        updateTo: StatusContent
    )

    fun updateImageFileStatusParamsMimeTypeUriSize(image: ImageFile)

    fun deleteImageFiles(imgs: List<ImageFile>)

    fun selectImageFile(id: Long): ImageFile?

    fun selectImageFiles(ids: LongArray): List<ImageFile>

    fun selectChapterImageFiles(ids: LongArray): List<ImageFile>

    fun flagImagesForDeletion(ids : LongArray, value : Boolean)

    fun selectDownloadedImagesFromContentLive(id: Long): LiveData<List<ImageFile>>

    fun selectDownloadedImagesFromContent(id: Long): List<ImageFile>

    fun countProcessedImagesById(contentId: Long): Map<StatusContent, Pair<Int, Long>>

    fun selectAllFavouritePagesLive(): LiveData<List<ImageFile>>

    fun countAllFavouritePagesLive(): LiveData<Int>

    fun selectPrimaryMemoryUsagePerSource(): Map<Site, Pair<Int, Long>>

    fun selectPrimaryMemoryUsagePerSource(rootPath: String): Map<Site, Pair<Int, Long>>

    fun selectExternalMemoryUsagePerSource(): Map<Site, Pair<Int, Long>>


    // QUEUE
    fun selectQueue(): List<QueueRecord>

    fun selectQueueLive(): LiveData<List<QueueRecord>>

    fun selectQueueLive(query: String?, source: Site?): LiveData<List<QueueRecord>>

    fun selectQueueUrls(site: Site): Set<String>

    fun addContentToQueue(
        content: Content,
        sourceImageStatus: StatusContent?,
        targetImageStatus: StatusContent?,
        position: QueuePosition,
        replacedContentId: Long,
        replacementTitle: String?,
        isQueueActive: Boolean
    )

    fun updateQueue(queue: List<QueueRecord>)

    fun deleteQueueRecordsCore()

    fun deleteQueue(content: Content)

    fun deleteQueue(index: Int)


    // ATTRIBUTES
    fun insertAttribute(attr: Attribute): Long

    fun selectAttribute(id: Long): Attribute?

    fun selectAttributeMasterDataPaged(
        types: List<AttributeType>,
        filter: String?,
        groupId: Long,
        attrs: Set<Attribute>?,
        location: Location,
        contentType: Type,
        includeFreeAttrs: Boolean,
        page: Int,
        booksPerPage: Int,
        orderStyle: Int
    ): AttributeQueryResult

    fun countAttributesPerType(
        groupId: Long,
        filter: Set<Attribute>?,
        location: Location,
        contentType: Type
    ): SparseIntArray


    // CHAPTERS
    fun selectChapters(contentId: Long): List<Chapter>

    fun selectChapters(chapterIds: List<Long>): List<Chapter>

    fun selectChapter(chapterId: Long): Chapter?

    fun insertChapters(chapters: List<Chapter>)

    fun deleteChapters(content: Content)

    fun deleteChapter(chapter: Chapter)


    // SITE HISTORY
    fun selectHistory(s: Site): SiteHistory

    fun insertSiteHistory(site: Site, url: String)


    // BOOKMARKS
    fun countAllBookmarks(): Long

    fun selectAllBookmarks(): List<SiteBookmark>

    fun selectBookmarks(s: Site): List<SiteBookmark>

    fun selectHomepage(s: Site): SiteBookmark?

    fun insertBookmark(bookmark: SiteBookmark): Long

    fun insertBookmarks(bookmarks: List<SiteBookmark>)

    fun deleteBookmark(bookmarkId: Long)

    fun deleteAllBookmarks()


    // SEARCH HISTORY
    fun selectSearchRecordsLive(): LiveData<List<SearchRecord>>

    fun insertSearchRecord(record: SearchRecord, limit: Int)

    fun deleteAllSearchRecords()


    // RENAMING RULES
    fun selectRenamingRule(id: Long): RenamingRule?

    fun selectRenamingRulesLive(
        type: AttributeType,
        nameFilter: String?
    ): LiveData<List<RenamingRule>>

    fun selectRenamingRules(type: AttributeType, nameFilter: String?): List<RenamingRule>

    fun insertRenamingRule(rule: RenamingRule): Long

    fun insertRenamingRules(rules: List<RenamingRule>)

    fun deleteRenamingRules(ids: List<Long>)


    // RESOURCES
    fun cleanup()

    fun cleanupOrphanAttributes()

    fun getDbSizeBytes(): Long


    // ONE-TIME USE QUERIES (MIGRATION & CLEANUP)
    fun selectContentIdsWithUpdatableJson(): LongArray
}