package me.devsaki.hentoid.database

import android.net.Uri
import android.util.SparseIntArray
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.paging.DataSource
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import io.objectbox.android.ObjectBoxDataSource
import io.objectbox.android.ObjectBoxLiveData
import io.objectbox.query.Query
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle.Companion.buildSearchUri
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle.Companion.parseSearchUri
import me.devsaki.hentoid.core.Consumer
import me.devsaki.hentoid.database.ObjectBoxPredeterminedDataSource.PredeterminedDataSourceFactory
import me.devsaki.hentoid.database.ObjectBoxRandomDataSource.RandomDataSourceFactory
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
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.QueuePosition
import me.devsaki.hentoid.util.Type
import me.devsaki.hentoid.widget.ContentSearchManager.Companion.searchContentIds
import me.devsaki.hentoid.widget.ContentSearchManager.ContentSearchBundle
import me.devsaki.hentoid.widget.ContentSearchManager.ContentSearchBundle.Companion.fromSearchCriteria
import timber.log.Timber
import kotlin.math.ceil
import kotlin.math.min

class ObjectBoxDAO : CollectionDAO {
    override fun cleanup() {
        ObjectBoxDB.cleanup()
    }

    override fun cleanupOrphanAttributes() {
        ObjectBoxDB.cleanupOrphanAttributes()
    }

    override fun getDbSizeBytes(): Long {
        return ObjectBoxDB.getDbSizeBytes()
    }

    override fun selectStoredFavContentIds(bookFavs: Boolean, groupFavs: Boolean): Set<Long> {
        return ObjectBoxDB.selectStoredContentFavIds(bookFavs, groupFavs)
    }

    override fun countContentWithUnhashedCovers(): Long {
        return ObjectBoxDB.selectNonHashedContentQ().safeCount()
    }

    override fun selectContentWithUnhashedCovers(): List<Content> {
        return ObjectBoxDB.selectNonHashedContentQ().safeFind()
    }

    override fun streamStoredContent(
        includeQueued: Boolean,
        orderField: Int,
        orderDesc: Boolean,
        consumer: Consumer<Content>
    ) {
        ObjectBoxDB.selectStoredContentQ(includeQueued, orderField, orderDesc).build()
            .use { query ->
                query.forEach { c -> consumer(c) }
            }
    }

    override fun selectRecentBookIds(searchBundle: ContentSearchBundle): List<Long> {
        return contentIdSearch(false, searchBundle, emptySet())
    }

    override fun searchBookIds(
        searchBundle: ContentSearchBundle,
        metadata: Set<Attribute>
    ): List<Long> {
        return contentIdSearch(false, searchBundle, metadata)
    }

    override fun searchBookIdsUniversal(searchBundle: ContentSearchBundle): List<Long> {
        return contentIdSearch(true, searchBundle, emptySet())
    }

    override fun insertAttribute(attr: Attribute): Long {
        return ObjectBoxDB.insertAttribute(attr)
    }

    override fun selectAttribute(id: Long): Attribute? {
        return ObjectBoxDB.selectAttribute(id)
    }

    override fun selectAttributeMasterDataPaged(
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
    ): AttributeQueryResult {
        return pagedAttributeSearch(
            types,
            filter,
            groupId,
            getDynamicGroupContent(groupId),
            attrs,
            location,
            contentType,
            includeFreeAttrs,
            orderStyle,
            page,
            booksPerPage
        )
    }

    override fun countAttributesPerType(
        groupId: Long,
        filter: Set<Attribute>?,
        location: Location,
        contentType: Type
    ): SparseIntArray {
        return countAttributes(
            groupId,
            getDynamicGroupContent(groupId),
            filter,
            location,
            contentType
        )
    }

    override fun selectChapters(contentId: Long): List<Chapter> {
        return ObjectBoxDB.selectChapters(contentId)
    }

    override fun selectChapter(chapterId: Long): Chapter? {
        return ObjectBoxDB.selectChapter(chapterId)
    }

    override fun selectErrorContentLive(): LiveData<List<Content>> {
        return ObjectBoxLiveData(ObjectBoxDB.selectErrorContentQ())
    }

    override fun selectErrorContentLive(query: String?, source: Site?): LiveData<List<Content>> {
        val bundle = ContentSearchBundle()
        bundle.query = query ?: ""
        val sourceAttr: MutableSet<Attribute> = HashSet()
        if (source != null) sourceAttr.add(Attribute(source))
        bundle.attributes = buildSearchUri(sourceAttr, "", 0, 0).toString()
        bundle.sortField = Preferences.Constant.ORDER_FIELD_DOWNLOAD_PROCESSING_DATE
        return ObjectBoxLiveData(
            ObjectBoxDB.selectContentUniversalQ(
                bundle,
                LongArray(0),
                intArrayOf(StatusContent.ERROR.code)
            )
        )
    }

    override fun selectErrorContent(): List<Content> {
        return ObjectBoxDB.selectErrorContentQ().safeFind()
    }

    override fun countAllBooksLive(): LiveData<Int> {
        // This is not optimal because it fetches all the content and returns its size only
        // That's because ObjectBox v2.4.0 does not allow watching Query.count or Query.findLazy using LiveData, but only Query.find
        // See https://github.com/objectbox/objectbox-java/issues/776
        val livedata = ObjectBoxLiveData(ObjectBoxDB.selectVisibleContentQ())
        val result = MediatorLiveData<Int>()
        result.addSource(livedata) { v -> result.setValue(v.size) }
        return result
    }

    override fun countBooks(
        groupId: Long,
        metadata: Set<Attribute>?,
        location: Location,
        contentType: Type
    ): LiveData<Int> {
        // This is not optimal because it fetches all the content and returns its size only
        // That's because ObjectBox v2.4.0 does not allow watching Query.count or Query.findLazy using LiveData, but only Query.find
        // See https://github.com/objectbox/objectbox-java/issues/776
        val bundle = ContentSearchBundle()
        bundle.groupId = groupId
        bundle.location = location.value
        bundle.contentType = contentType.value
        bundle.sortField = Preferences.Constant.ORDER_FIELD_NONE
        val livedata = ObjectBoxLiveData(
            ObjectBoxDB.selectContentSearchContentQ(
                bundle,
                getDynamicGroupContent(groupId),
                metadata,
                ObjectBoxDB.libraryStatus
            )
        )
        val result = MediatorLiveData<Int>()
        result.addSource(livedata) { v -> result.setValue(v.size) }
        return result
    }

    override fun selectRecentBooks(searchBundle: ContentSearchBundle): LiveData<PagedList<Content>> {
        return getPagedContent(false, searchBundle, emptySet())
    }

    override fun searchBooks(
        searchBundle: ContentSearchBundle,
        metadata: Set<Attribute>
    ): LiveData<PagedList<Content>> {
        return getPagedContent(false, searchBundle, metadata)
    }

    override fun searchBooksUniversal(searchBundle: ContentSearchBundle): LiveData<PagedList<Content>> {
        return getPagedContent(true, searchBundle, emptySet())
    }

    override fun selectNoContent(): LiveData<PagedList<Content>> {
        return LivePagedListBuilder(
            ObjectBoxDataSource.Factory(ObjectBoxDB.selectNoContentQ()),
            1
        ).build()
    }


    private fun getPagedContent(
        isUniversal: Boolean,
        searchBundle: ContentSearchBundle,
        metadata: Set<Attribute>
    ): LiveData<PagedList<Content>> {
        val isCustomOrder = searchBundle.sortField == Preferences.Constant.ORDER_FIELD_CUSTOM
        val contentRetrieval: Pair<Long, DataSource.Factory<Int, Content>> =
            if (isCustomOrder) getPagedContentByList(
                isUniversal,
                searchBundle,
                metadata
            ) else getPagedContentByQuery(isUniversal, searchBundle, metadata)
        val nbPages = Preferences.getContentPageQuantity()
        var initialLoad = nbPages * 3
        if (searchBundle.loadAll) {
            // Trump Android's algorithm by setting a number of pages higher that the actual number of results
            // to avoid having a truncated result set (see issue #501)
            initialLoad = ceil(contentRetrieval.first * 1.0 / nbPages).toInt() * nbPages
        }
        val cfg = PagedList.Config.Builder().setEnablePlaceholders(!searchBundle.loadAll)
            .setInitialLoadSizeHint(initialLoad).setPageSize(nbPages).build()
        return LivePagedListBuilder(contentRetrieval.second, cfg).build()
    }

    private fun getPagedContentByQuery(
        isUniversal: Boolean,
        searchBundle: ContentSearchBundle,
        metadata: Set<Attribute>
    ): Pair<Long, DataSource.Factory<Int, Content>> {
        val isRandom = searchBundle.sortField == Preferences.Constant.ORDER_FIELD_RANDOM
        val query: Query<Content> = if (isUniversal) {
            ObjectBoxDB.selectContentUniversalQ(
                searchBundle,
                getDynamicGroupContent(searchBundle.groupId)
            )
        } else {
            ObjectBoxDB.selectContentSearchContentQ(
                searchBundle,
                getDynamicGroupContent(searchBundle.groupId),
                metadata,
                ObjectBoxDB.libraryStatus
            )
        }
        return if (isRandom) {
            val shuffledIds = ObjectBoxDB.getShuffledIds()
            Pair(query.count(), RandomDataSourceFactory(query, shuffledIds))
        } else Pair(query.count(), ObjectBoxDataSource.Factory(query))
    }

    private fun getPagedContentByList(
        isUniversal: Boolean,
        searchBundle: ContentSearchBundle,
        metadata: Set<Attribute>
    ): Pair<Long, DataSource.Factory<Int, Content>> {
        val ids: LongArray = if (isUniversal) {
            ObjectBoxDB.selectContentUniversalByGroupItem(
                searchBundle,
                getDynamicGroupContent(searchBundle.groupId)
            )
        } else {
            ObjectBoxDB.selectContentSearchContentByGroupItem(
                searchBundle,
                getDynamicGroupContent(searchBundle.groupId),
                metadata
            )
        }
        return Pair(
            ids.size.toLong(), PredeterminedDataSourceFactory(
                { id -> ObjectBoxDB.selectContentById(id) }, ids
            )
        )
    }

    override fun selectContent(id: Long): Content? {
        return ObjectBoxDB.selectContentById(id)
    }

    override fun selectContent(id: LongArray): List<Content> {
        return ObjectBoxDB.selectContentById(id.toList())
    }

    // Find any book that has the given content URL _or_ a chapter with the given content URL _or_ has a cover starting with the given cover URL
    override fun selectContentByUrlOrCover(
        site: Site,
        contentUrl: String,
        coverUrl: String?
    ): Content? {
        val coverUrlStart =
            if (coverUrl != null) Content.getNeutralCoverUrlRoot(coverUrl, site) else ""
        return ObjectBoxDB.selectContentByUrlOrCover(site, contentUrl, coverUrlStart)
    }

    // Find any book that has the given content URL _and_ has a cover starting with the given cover URL
    override fun selectContentsByUrl(
        site: Site,
        contentUrl: String
    ): Set<Content> {
        return ObjectBoxDB.selectContentByUrl(site, contentUrl)
    }

    // Find any book that has the given quality of pages _and_ size
    override fun selectContentsByQtyPageAndSize(qtyPage: Int, size: Long): Set<Content> {
        return ObjectBoxDB.selectContentsByQtyPageAndSize(qtyPage, size)
    }

    override fun selectAllSourceUrls(site: Site): Set<String> {
        return ObjectBoxDB.selectAllContentUrls(site.code)
    }

    override fun selectAllMergedUrls(site: Site): Set<String> {
        return ObjectBoxDB.selectAllMergedContentUrls(site)
    }

    override fun searchTitlesWith(word: String, contentStatusCodes: IntArray): List<Content> {
        return ObjectBoxDB.selectContentWithTitle(word, contentStatusCodes)
    }

    override fun selectContentByStorageUri(folderUri: String, onlyFlagged: Boolean): Content? {
        // Select only the "document" part of the URI, as the "tree" part can vary
        val docPart = folderUri.substring(folderUri.indexOf("/document/"))
        return ObjectBoxDB.selectContentEndWithStorageUri(docPart, onlyFlagged)
    }

    override fun insertContent(content: Content): Long {
        val result = ObjectBoxDB.insertContentAndAttributes(content)
        // Attach new attributes to existing groups, if any
        for (a in result.second) {
            val g = selectGroupByName(Grouping.ARTIST.id, a.name)
            if (g != null) insertGroupItem(GroupItem(result.first, g, -1))
        }
        return result.first
    }

    override fun insertContentCore(content: Content): Long {
        return ObjectBoxDB.insertContentCore(content)
    }

    override fun updateContentStatus(updateFrom: StatusContent, updateTo: StatusContent) {
        ObjectBoxDB.updateContentStatus(updateFrom, updateTo)
    }

    override fun updateContentProcessedFlag(contentId: Long, flag: Boolean) {
        ObjectBoxDB.updateContentProcessedFlag(contentId, flag)
    }

    override fun deleteContent(content: Content) {
        ObjectBoxDB.deleteContentById(content.id)
    }

    override fun selectErrorRecordByContentId(contentId: Long): List<ErrorRecord> {
        return ObjectBoxDB.selectErrorRecordByContentId(contentId)
    }

    override fun insertErrorRecord(record: ErrorRecord) {
        ObjectBoxDB.insertErrorRecord(record)
    }

    override fun deleteErrorRecords(contentId: Long) {
        ObjectBoxDB.deleteErrorRecords(contentId)
    }

    override fun insertChapters(chapters: List<Chapter>) {
        ObjectBoxDB.insertChapters(chapters)
    }

    override fun deleteChapters(content: Content) {
        ObjectBoxDB.deleteChaptersByContentId(content.id)
    }

    override fun deleteChapter(chapter: Chapter) {
        ObjectBoxDB.deleteChapter(chapter.id)
    }

    override fun clearDownloadParams(contentId: Long) {
        val c = ObjectBoxDB.selectContentById(contentId) ?: return
        c.setDownloadParams("")
        ObjectBoxDB.insertContentCore(c)
        val imgs = c.imageFiles ?: return
        for (img in imgs) img.setDownloadParams("")
        ObjectBoxDB.insertImageFiles(imgs)
    }

    override fun shuffleContent() {
        ObjectBoxDB.shuffleContentIds()
    }

    override fun countAllInternalBooks(rootPath: String, favsOnly: Boolean): Long {
        return ObjectBoxDB.selectAllInternalBooksQ(rootPath, favsOnly, true).safeCount()
    }

    override fun countAllQueueBooks(): Long {
        // Count doesn't work here because selectAllQueueBooksQ uses a filter
        return ObjectBoxDB.selectAllQueueBooksQ().safeFindIds().size.toLong()
    }

    override fun countAllQueueBooksLive(): LiveData<Int> {
        // This is not optimal because it fetches all the content and returns its size only
        // That's because ObjectBox v2.4.0 does not allow watching Query.count or Query.findLazy using LiveData, but only Query.find
        // See https://github.com/objectbox/objectbox-java/issues/776
        val livedata = ObjectBoxLiveData(ObjectBoxDB.selectAllQueueBooksQ())
        val result = MediatorLiveData<Int>()
        result.addSource(livedata) { v -> result.setValue(v.size) }
        return result
    }

    override fun streamAllInternalBooks(
        rootPath: String,
        favsOnly: Boolean,
        consumer: Consumer<Content>
    ) {
        ObjectBoxDB.selectAllInternalBooksQ(rootPath, favsOnly, true).use { query ->
            query.forEach { t -> consumer(t) }
        }
    }

    override fun deleteAllExternalBooks() {
        ObjectBoxDB.deleteContentById(ObjectBoxDB.selectAllExternalBooksQ().safeFindIds())
    }

    override fun selectGroups(groupIds: LongArray): List<Group> {
        return ObjectBoxDB.selectGroups(groupIds) ?: emptyList()
    }

    override fun selectGroups(grouping: Int): List<Group> {
        return ObjectBoxDB.selectGroupsQ(
            grouping, null, 0, false, -1,
            groupFavouritesOnly = false,
            groupNonFavouritesOnly = false,
            filterRating = -1
        ).safeFind()
    }

    override fun selectGroups(grouping: Int, subType: Int): List<Group> {
        return ObjectBoxDB.selectGroupsQ(
            grouping,
            null,
            0,
            false,
            subType,
            groupFavouritesOnly = false,
            groupNonFavouritesOnly = false,
            filterRating = -1
        ).safeFind()
    }

    override fun selectEditedGroups(grouping: Int): List<Group> {
        return ObjectBoxDB.selectEditedGroups(grouping)
    }

    override fun selectGroupsLive(
        grouping: Int,
        query: String?,
        orderField: Int,
        orderDesc: Boolean,
        artistGroupVisibility: Int,
        groupFavouritesOnly: Boolean,
        groupNonFavouritesOnly: Boolean,
        filterRating: Int
    ): LiveData<List<Group>> {
        // Artist / group visibility filter is only relevant when the selected grouping is "By Artist"
        val subType = if (grouping == Grouping.ARTIST.id) artistGroupVisibility else -1
        val livedata: LiveData<List<Group>> = ObjectBoxLiveData(
            ObjectBoxDB.selectGroupsQ(
                grouping,
                query,
                orderField,
                orderDesc,
                subType,
                groupFavouritesOnly,
                groupNonFavouritesOnly,
                filterRating
            )
        )
        var workingData = livedata


        // === SPECIFIC DATA

        // Download date grouping : groups are empty as they are dynamically populated
        //   -> Manually add items inside each of them
        //   -> Manually set a cover for each of them
        if (grouping == Grouping.DL_DATE.id) {
            val livedata2 = MediatorLiveData<List<Group>>()
            livedata2.addSource(livedata) { groups ->
                val enrichedWithItems =
                    groups.map { g ->
                        enrichGroupWithItemsByDlDate(
                            g,
                            g.propertyMin,
                            g.propertyMax
                        )
                    }.toList()
                livedata2.setValue(enrichedWithItems)
            }
            workingData = livedata2
        }

        // Dynamic grouping : groups are empty as they are dynamically populated
        //   -> Manually add items inside each of them
        //   -> Manually set a cover for each of them
        if (grouping == Grouping.DYNAMIC.id) {
            val livedata2 = MediatorLiveData<List<Group>>()
            livedata2.addSource(livedata) { groups ->
                val enrichedWithItems =
                    groups.map { g -> enrichGroupWithItemsByQuery(g) }
                livedata2.setValue(enrichedWithItems)
            }
            workingData = livedata2
        }

        // Custom "ungrouped" special group is dynamically populated
        // -> Manually add items
        if (grouping == Grouping.CUSTOM.id) {
            val livedata2 = MediatorLiveData<List<Group>>()
            livedata2.addSource(livedata) { groups ->
                val enrichedWithItems = groups.map { g ->
                    enrichCustomGroups(g)
                }
                livedata2.setValue(enrichedWithItems)
            }
            workingData = livedata2
        }


        // === ORDERING

        // Order by number of children (ObjectBox can't do that natively)
        if (Preferences.Constant.ORDER_FIELD_CHILDREN == orderField) {
            val result = MediatorLiveData<List<Group>>()
            result.addSource(workingData) { groups ->
                val sortOrder = if (orderDesc) -1 else 1
                val orderedByNbChildren = groups.sortedBy { g -> g.getItems().size * sortOrder }
                result.setValue(orderedByNbChildren)
            }
            return result
        }

        // Order by latest download date of children (ObjectBox can't do that natively)
        if (Preferences.Constant.ORDER_FIELD_DOWNLOAD_PROCESSING_DATE == orderField) {
            val result = MediatorLiveData<List<Group>>()
            result.addSource(workingData) { groups ->
                val sortOrder = if (orderDesc) -1 else 1
                val orderedByDlDate = groups.sortedBy { g ->
                    getLatestDlDate(g) * sortOrder
                }
                result.setValue(orderedByDlDate)
            }
            return result
        }
        return workingData
    }

    private fun enrichGroupWithItemsByDlDate(g: Group, minDays: Int, maxDays: Int): Group {
        val items = selectGroupItemsByDlDate(g, minDays, maxDays)
        g.setItems(items)
        if (items.isNotEmpty()) g.coverContent.target = items[0].content.target
        return g
    }

    private fun enrichGroupWithItemsByQuery(g: Group): Group {
        val items = selectGroupItemsByQuery(g)
        g.setItems(items)
        if (items.isNotEmpty()) {
            val c = selectContent(items[0].contentId)
            g.coverContent.target = c
        }
        return g
    }

    private fun enrichCustomGroups(g: Group): Group {
        if (g.grouping == Grouping.CUSTOM) {
            val newItems: MutableList<GroupItem>
            if (g.isUngroupedGroup) { // Populate Ungrouped custom group
                newItems = ObjectBoxDB.selectUngroupedContentIds().map { id ->
                    GroupItem(id, g, -1)
                }.toMutableList()
            } else { // Reselect items; only take items from the library to avoid counting those who've been sent back to the Queue
                val groupContent =
                    ObjectBoxDB.selectContentIdsByGroup(g.id) // Specific query to get there fast
                newItems = ArrayList()
                for (i in groupContent.indices) {
                    newItems.add(GroupItem(groupContent[i], g, i))
                }
            }
            g.setItems(newItems)
            // Reset cover content if it isn't among remaining books
            if (newItems.isNotEmpty()) {
                val newContents = newItems.map { obj: GroupItem -> obj.contentId }
                if (!newContents.contains(g.coverContent.targetId)) {
                    val c = selectContent(newItems[0].contentId)
                    g.coverContent.target = c
                }
            }
        }
        return g
    }

    private fun getLatestDlDate(g: Group): Long {
        // Manually select all content as g.getContents won't work (unresolved items)
        val contents = ObjectBoxDB.selectContentById(g.contentIds)
        return contents.maxOfOrNull { c -> c.downloadDate } ?: 0
    }

    override fun selectGroup(groupId: Long): Group? {
        return ObjectBoxDB.selectGroup(groupId)
    }

    override fun selectGroupByName(grouping: Int, name: String): Group? {
        return ObjectBoxDB.selectGroupByName(grouping, name)
    }

    // Does NOT check name unicity
    override fun insertGroup(group: Group): Long {
        // Auto-number max order when not provided
        if (-1 == group.order) group.order = ObjectBoxDB.getMaxGroupOrderFor(group.grouping) + 1
        return ObjectBoxDB.insertGroup(group)
    }

    override fun countGroupsFor(grouping: Grouping): Long {
        return ObjectBoxDB.countGroupsFor(grouping)
    }

    override fun deleteGroup(groupId: Long) {
        ObjectBoxDB.deleteGroup(groupId)
    }

    override fun deleteAllGroups(grouping: Grouping) {
        ObjectBoxDB.deleteGroupItemsByGrouping(grouping.id)
        ObjectBoxDB.selectGroupsByGroupingQ(grouping.id).safeRemove()
    }

    override fun flagAllGroups(grouping: Grouping) {
        ObjectBoxDB.flagGroupsForDeletion(
            ObjectBoxDB.selectGroupsByGroupingQ(grouping.id).safeFind()
        )
    }

    override fun deleteAllFlaggedGroups() {
        ObjectBoxDB.selectFlaggedGroupsQ().use { flaggedGroups ->
            // Delete related GroupItems first
            val groups = flaggedGroups.find()
            for (g in groups) ObjectBoxDB.deleteGroupItemsByGroup(g.id)

            // Actually delete the Group
            flaggedGroups.remove()
        }
    }

    override fun deleteOrphanArtistGroups() {
        ObjectBoxDB.deleteOrphanArtistGroups()
    }

    override fun insertGroupItem(item: GroupItem): Long {
        // Auto-number max order when not provided
        if (-1 == item.order) item.order = ObjectBoxDB.getMaxGroupItemOrderFor(item.groupId) + 1

        // If target group doesn't have a cover, get the corresponding Content's
        val groupCoverContent = item.group.target.coverContent
        if (!groupCoverContent.isResolvedAndNotNull) {
            val c: Content? = if (!item.content.isResolvedAndNotNull) {
                selectContent(item.contentId)
            } else {
                item.content.target
            }
            groupCoverContent.setAndPutTarget(c)
        }
        return ObjectBoxDB.insertGroupItem(item)
    }

    override fun selectGroupItems(contentId: Long, grouping: Grouping): List<GroupItem> {
        return ObjectBoxDB.selectGroupItems(contentId, grouping.id)
    }

    private fun selectGroupItemsByDlDate(
        group: Group,
        minDays: Int,
        maxDays: Int
    ): List<GroupItem> {
        val contentResult = ObjectBoxDB.selectContentByDlDate(minDays, maxDays)
        return contentResult.map { c -> GroupItem(c, group, -1) }
    }

    private fun selectGroupItemsByQuery(group: Group): List<GroupItem> {
        val criteria = parseSearchUri(Uri.parse(group.searchUri))
        val bundle = fromSearchCriteria(criteria)
        val contentResult = searchContentIds(bundle, this)
        return contentResult.map { c -> GroupItem(c, group, -1) }
    }

    override fun deleteGroupItems(groupItemIds: List<Long>) {
        // Check if one of the GroupItems to delete is linked to the content that contains the group's cover picture
        val groupItems = ObjectBoxDB.selectGroupItems(groupItemIds.toLongArray())
        for (gi in groupItems) {
            val groupCoverContent = gi.group.target.coverContent
            // If so, remove the cover picture
            if (groupCoverContent.isResolvedAndNotNull && groupCoverContent.targetId == gi.content.targetId)
                gi.group.target.coverContent.setAndPutTarget(null)
        }
        ObjectBoxDB.deleteGroupItems(groupItemIds.toLongArray())
    }

    override fun flagAllInternalBooks(rootPath: String, includePlaceholders: Boolean) {
        ObjectBoxDB.flagContentsForDeletion(
            ObjectBoxDB.selectAllInternalBooksQ(
                rootPath,
                false,
                includePlaceholders
            ).safeFind(), true
        )
    }

    override fun flagAllExternalBooks() {
        ObjectBoxDB.flagContentsForDeletion(ObjectBoxDB.selectAllExternalBooksQ().safeFind(), true)
    }

    override fun deleteAllInternalBooks(rootPath: String, resetRemainingImagesStatus: Boolean) {
        ObjectBoxDB.deleteContentById(
            ObjectBoxDB.selectAllInternalBooksQ(rootPath, false).safeFindIds()
        )
        if (resetRemainingImagesStatus) resetRemainingImagesStatus(rootPath)
    }

    override fun deleteAllFlaggedBooks(resetRemainingImagesStatus: Boolean, pathRoot: String?) {
        ObjectBoxDB.deleteContentById(ObjectBoxDB.selectAllFlaggedBooksQ().safeFindIds())
        if (resetRemainingImagesStatus && pathRoot != null) resetRemainingImagesStatus(pathRoot)
    }

    // Switch status of all remaining images (i.e. from queued books) to SAVED, as we cannot guarantee the files are still there
    private fun resetRemainingImagesStatus(rootPath: String) {
        val remainingContentIds = ObjectBoxDB.selectAllQueueBooksQ(rootPath).safeFindIds()
        for (contentId in remainingContentIds) ObjectBoxDB.updateImageContentStatus(
            contentId,
            null,
            StatusContent.SAVED
        )
    }

    override fun flagAllErrorBooksWithJson() {
        ObjectBoxDB.flagContentsForDeletion(ObjectBoxDB.selectAllErrorJsonBooksQ().safeFind(), true)
    }

    override fun deleteAllQueuedBooks() {
        Timber.i("Cleaning up queue")
        ObjectBoxDB.deleteContentById(ObjectBoxDB.selectAllQueueBooksQ().safeFindIds())
        ObjectBoxDB.deleteQueueRecords()
    }

    override fun insertImageFile(img: ImageFile) {
        ObjectBoxDB.insertImageFile(img)
    }

    override fun insertImageFiles(imgs: List<ImageFile>) {
        ObjectBoxDB.insertImageFiles(imgs)
    }

    override fun replaceImageList(contentId: Long, newList: List<ImageFile>) {
        ObjectBoxDB.replaceImageFiles(contentId, newList)
    }

    override fun updateImageContentStatus(
        contentId: Long,
        updateFrom: StatusContent?,
        updateTo: StatusContent
    ) {
        ObjectBoxDB.updateImageContentStatus(contentId, updateFrom, updateTo)
    }

    override fun updateImageFileStatusParamsMimeTypeUriSize(image: ImageFile) {
        ObjectBoxDB.updateImageFileStatusParamsMimeTypeUriSize(image)
    }

    override fun deleteImageFiles(imgs: List<ImageFile>) {
        // Delete the page
        ObjectBoxDB.deleteImageFiles(imgs)

        // Lists all relevant content
        val contents = imgs.filter { i -> i.content != null }
            .map { i: ImageFile -> i.content.targetId }.distinct()

        // Update the content with its new size
        for (contentId in contents) {
            val content = ObjectBoxDB.selectContentById(contentId)
            if (content != null) {
                content.computeSize()
                ObjectBoxDB.insertContentCore(content)
            }
        }
    }

    override fun selectImageFile(id: Long): ImageFile? {
        return ObjectBoxDB.selectImageFile(id)
    }

    override fun selectImageFiles(ids: LongArray): List<ImageFile> {
        return ObjectBoxDB.selectImageFiles(ids)
    }

    override fun selectDownloadedImagesFromContentLive(id: Long): LiveData<List<ImageFile>> {
        return ObjectBoxLiveData(ObjectBoxDB.selectDownloadedImagesFromContentQ(id))
    }

    override fun selectDownloadedImagesFromContent(id: Long): List<ImageFile> {
        return ObjectBoxDB.selectDownloadedImagesFromContentQ(id).safeFind()
    }

    override fun countProcessedImagesById(contentId: Long): Map<StatusContent, Pair<Int, Long>> {
        return ObjectBoxDB.countProcessedImagesById(contentId)
    }

    override fun selectAllFavouritePagesLive(): LiveData<List<ImageFile>> {
        return ObjectBoxLiveData(ObjectBoxDB.selectAllFavouritePagesQ())
    }

    override fun countAllFavouritePagesLive(): LiveData<Int> {
        // This is not optimal because it fetches all the content and returns its size only
        // That's because ObjectBox v2.4.0 does not allow watching Query.count or Query.findLazy using LiveData, but only Query.find
        // See https://github.com/objectbox/objectbox-java/issues/776
        val livedata = ObjectBoxLiveData(ObjectBoxDB.selectAllFavouritePagesQ())
        val result = MediatorLiveData<Int>()
        result.addSource(livedata) { v -> result.setValue(v.size) }
        return result
    }

    override fun selectPrimaryMemoryUsagePerSource(): Map<Site, Pair<Int, Long>> {
        return ObjectBoxDB.selectPrimaryMemoryUsagePerSource("")
    }

    override fun selectPrimaryMemoryUsagePerSource(rootPath: String): Map<Site, Pair<Int, Long>> {
        return ObjectBoxDB.selectPrimaryMemoryUsagePerSource(rootPath)
    }

    override fun selectExternalMemoryUsagePerSource(): Map<Site, Pair<Int, Long>> {
        return ObjectBoxDB.selectExternalMemoryUsagePerSource()
    }

    override fun addContentToQueue(
        content: Content,
        sourceImageStatus: StatusContent?,
        targetImageStatus: StatusContent?,
        position: QueuePosition,
        replacedContentId: Long,
        replacementTitle: String?,
        isQueueActive: Boolean
    ) {
        if (targetImageStatus != null) ObjectBoxDB.updateImageContentStatus(
            content.id,
            sourceImageStatus,
            targetImageStatus
        )
        content.setStatus(StatusContent.PAUSED)
        content.setIsBeingProcessed(false) // Remove any UI animation
        if (replacedContentId > -1) content.setContentIdToReplace(replacedContentId)
        if (replacementTitle != null) content.replacementTitle = replacementTitle
        insertContent(content)
        if (!ObjectBoxDB.isContentInQueue(content)) {
            val targetPosition: Int =
                if (position == QueuePosition.BOTTOM) {
                    ObjectBoxDB.selectMaxQueueOrder().toInt() + 1
                } else { // Top - don't put #1 if queue is active not to interrupt current download
                    if (isQueueActive) 2 else 1
                }
            insertQueueAndRenumber(content.id, targetPosition)
        }
    }

    private fun insertQueueAndRenumber(contentId: Long, order: Int) {
        val queue = ObjectBoxDB.selectQueueRecordsQ().safeFind().toMutableList()
        val newRecord = QueueRecord(contentId, order)

        // Put in the right place
        if (order > queue.size) queue.add(newRecord) else {
            val newOrder = min((queue.size + 1).toDouble(), order.toDouble()).toInt()
            queue.add(newOrder - 1, newRecord)
        }
        // Renumber everything and save
        var index = 1
        for (qr in queue) qr.rank = index++
        ObjectBoxDB.updateQueue(queue)
    }

    private fun getDynamicGroupContent(groupId: Long): LongArray {
        var result = emptyList<Long>()
        if (groupId > -1) {
            val g = selectGroup(groupId)
            if (g != null && g.grouping == Grouping.DYNAMIC) {
                result = selectGroupItemsByQuery(g).map { obj: GroupItem -> obj.contentId }
            }
        }
        return result.toLongArray()
    }

    private fun contentIdSearch(
        isUniversal: Boolean,
        searchBundle: ContentSearchBundle,
        metadata: Set<Attribute>
    ): List<Long> {
        return if (isUniversal) {
            ObjectBoxDB.selectContentUniversalId(
                searchBundle,
                getDynamicGroupContent(searchBundle.groupId),
                ObjectBoxDB.libraryStatus
            ).toList()
        } else {
            ObjectBoxDB.selectContentSearchId(
                searchBundle,
                getDynamicGroupContent(searchBundle.groupId),
                metadata,
                ObjectBoxDB.libraryStatus
            ).toList()
        }
    }

    private fun pagedAttributeSearch(
        attrTypes: List<AttributeType>,
        filter: String?,
        groupId: Long,
        dynamicGroupContentIds: LongArray,
        attrs: Set<Attribute>?,
        location: Location,
        contentType: Type,
        includeFreeAttrs: Boolean,
        sortOrder: Int,
        pageNum: Int,
        itemPerPage: Int
    ): AttributeQueryResult {
        val result: MutableList<Attribute> = ArrayList()
        var totalSelectedAttributes: Long = 0
        if (attrTypes.isNotEmpty()) {
            if (attrTypes[0] == AttributeType.SOURCE) {
                result.addAll(
                    ObjectBoxDB.selectAvailableSources(
                        groupId,
                        dynamicGroupContentIds,
                        attrs,
                        location,
                        contentType,
                        includeFreeAttrs
                    )
                )
                totalSelectedAttributes = result.size.toLong()
            } else {
                for (type in attrTypes) {
                    // TODO fix sorting when concatenating both lists
                    result.addAll(
                        ObjectBoxDB.selectAvailableAttributes(
                            type,
                            groupId,
                            dynamicGroupContentIds,
                            attrs,
                            location,
                            contentType,
                            includeFreeAttrs,
                            filter,
                            sortOrder,
                            pageNum,
                            itemPerPage
                        )
                    )
                    totalSelectedAttributes += ObjectBoxDB.countAvailableAttributes(
                        type,
                        groupId,
                        dynamicGroupContentIds,
                        attrs,
                        location,
                        contentType,
                        includeFreeAttrs,
                        filter
                    )
                }
            }
        }
        return AttributeQueryResult(result, totalSelectedAttributes)
    }

    private fun countAttributes(
        groupId: Long,
        dynamicGroupContentIds: LongArray,
        filter: Set<Attribute>?,
        location: Location,
        contentType: Type
    ): SparseIntArray {
        val result: SparseIntArray
        if (filter.isNullOrEmpty() && Location.ANY == location && Type.ANY == contentType && -1L == groupId) {
            result = ObjectBoxDB.countAvailableAttributesPerType()
            result.put(AttributeType.SOURCE.code, ObjectBoxDB.selectAvailableSources().size)
        } else {
            result = ObjectBoxDB.countAvailableAttributesPerType(
                groupId,
                dynamicGroupContentIds,
                filter,
                location,
                contentType
            )
            result.put(
                AttributeType.SOURCE.code,
                ObjectBoxDB.selectAvailableSources(
                    groupId,
                    dynamicGroupContentIds,
                    filter,
                    location,
                    contentType,
                    false
                ).size
            )
        }
        return result
    }

    override fun selectQueueLive(): LiveData<List<QueueRecord>> {
        return ObjectBoxLiveData(ObjectBoxDB.selectQueueRecordsQ())
    }

    override fun selectQueueLive(query: String?, source: Site?): LiveData<List<QueueRecord>> {
        return ObjectBoxLiveData(ObjectBoxDB.selectQueueRecordsQ(query, source))
    }

    override fun selectQueue(): List<QueueRecord> {
        return ObjectBoxDB.selectQueueRecordsQ().safeFind()
    }

    override fun updateQueue(queue: List<QueueRecord>) {
        ObjectBoxDB.updateQueue(queue)
    }

    override fun deleteQueue(content: Content) {
        ObjectBoxDB.deleteQueueRecords(content)
    }

    override fun deleteQueue(index: Int) {
        ObjectBoxDB.deleteQueueRecords(index)
    }

    override fun deleteQueueRecordsCore() {
        ObjectBoxDB.deleteQueueRecords()
    }

    override fun selectHistory(s: Site): SiteHistory {
        return ObjectBoxDB.selectHistory(s) ?: SiteHistory()
    }

    override fun insertSiteHistory(site: Site, url: String) {
        ObjectBoxDB.insertSiteHistory(site, url)
    }

    // BOOKMARKS

    // BOOKMARKS
    override fun countAllBookmarks(): Long {
        return ObjectBoxDB.selectBookmarksQ(null).safeCount()
    }

    override fun selectAllBookmarks(): List<SiteBookmark> {
        return ObjectBoxDB.selectBookmarksQ(null).safeFind()
    }

    override fun deleteAllBookmarks() {
        ObjectBoxDB.selectBookmarksQ(null).safeRemove()
    }

    override fun selectBookmarks(s: Site): List<SiteBookmark> {
        return ObjectBoxDB.selectBookmarksQ(s).safeFind()
    }

    override fun selectHomepage(s: Site): SiteBookmark? {
        return ObjectBoxDB.selectHomepage(s)
    }

    override fun insertBookmark(bookmark: SiteBookmark): Long {
        // Auto-number max order when not provided
        if (-1 == bookmark.order) bookmark.order =
            ObjectBoxDB.getMaxBookmarkOrderFor(bookmark.site) + 1
        return ObjectBoxDB.insertBookmark(bookmark)
    }

    override fun insertBookmarks(bookmarks: List<SiteBookmark>) {
        // Mass insert method; no need to renumber here
        ObjectBoxDB.insertBookmarks(bookmarks)
    }

    override fun deleteBookmark(bookmarkId: Long) {
        ObjectBoxDB.deleteBookmark(bookmarkId)
    }


    // SEARCH HISTORY

    // SEARCH HISTORY
    override fun selectSearchRecordsLive(): LiveData<List<SearchRecord>> {
        return ObjectBoxLiveData(ObjectBoxDB.selectSearchRecordsQ())
    }

    private fun selectSearchRecords(): List<SearchRecord> {
        return ObjectBoxDB.selectSearchRecordsQ().safeFind()
    }

    override fun insertSearchRecord(record: SearchRecord, limit: Int) {
        val records = selectSearchRecords().toMutableList()
        if (records.contains(record)) return
        while (records.size >= limit) {
            ObjectBoxDB.deleteSearchRecord(records[0].id)
            records.removeAt(0)
        }
        records.add(record)
        ObjectBoxDB.insertSearchRecords(records)
    }

    override fun deleteAllSearchRecords() {
        ObjectBoxDB.selectSearchRecordsQ().safeRemove()
    }


    // RENAMING RULES

    // RENAMING RULES
    override fun selectRenamingRule(id: Long): RenamingRule? {
        return ObjectBoxDB.selectRenamingRule(id)
    }

    override fun selectRenamingRulesLive(
        type: AttributeType,
        nameFilter: String?
    ): LiveData<List<RenamingRule>> {
        return ObjectBoxLiveData(
            ObjectBoxDB.selectRenamingRulesQ(
                type,
                nameFilter ?: ""
            )
        )
    }

    override fun selectRenamingRules(type: AttributeType, nameFilter: String?): List<RenamingRule> {
        return ObjectBoxDB.selectRenamingRulesQ(type, nameFilter ?: "").safeFind()
    }

    override fun insertRenamingRule(rule: RenamingRule): Long {
        return ObjectBoxDB.insertRenamingRule(rule)
    }

    override fun insertRenamingRules(rules: List<RenamingRule>) {
        ObjectBoxDB.insertRenamingRules(rules)
    }

    override fun deleteRenamingRules(ids: List<Long>) {
        ObjectBoxDB.deleteRenamingRules(ids.toLongArray())
    }


    // ONE-TIME USE QUERIES (MIGRATION & CLEANUP)
    override fun selectContentIdsWithUpdatableJson(): LongArray {
        return ObjectBoxDB.selectContentIdsWithUpdatableJson()
    }
}