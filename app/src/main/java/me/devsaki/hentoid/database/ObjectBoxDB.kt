package me.devsaki.hentoid.database

import android.util.SparseIntArray
import io.objectbox.BoxStore
import io.objectbox.Property
import io.objectbox.android.Admin
import io.objectbox.query.Query
import io.objectbox.query.QueryBuilder
import io.objectbox.query.QueryCondition
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle.Companion.buildSearchUri
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle.Companion.parseSearchUri
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.core.SEED_CONTENT
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeLocation
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Attribute_
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Chapter_
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.Content_
import me.devsaki.hentoid.database.domains.DownloadMode
import me.devsaki.hentoid.database.domains.ErrorRecord
import me.devsaki.hentoid.database.domains.ErrorRecord_
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.database.domains.GroupItem
import me.devsaki.hentoid.database.domains.GroupItem_
import me.devsaki.hentoid.database.domains.Group_
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.database.domains.ImageFile_
import me.devsaki.hentoid.database.domains.MyObjectBox
import me.devsaki.hentoid.database.domains.QueueRecord
import me.devsaki.hentoid.database.domains.QueueRecord_
import me.devsaki.hentoid.database.domains.RenamingRule
import me.devsaki.hentoid.database.domains.RenamingRule_
import me.devsaki.hentoid.database.domains.SearchRecord
import me.devsaki.hentoid.database.domains.ShuffleRecord
import me.devsaki.hentoid.database.domains.ShuffleRecord_
import me.devsaki.hentoid.database.domains.SiteBookmark
import me.devsaki.hentoid.database.domains.SiteBookmark_
import me.devsaki.hentoid.database.domains.SiteHistory
import me.devsaki.hentoid.database.domains.SiteHistory_
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.util.Location
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.RandomSeed.getSeed
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.Type
import me.devsaki.hentoid.util.file.getSupportedExtensions
import me.devsaki.hentoid.util.getLibraryStatuses
import me.devsaki.hentoid.util.getQueueStatuses
import me.devsaki.hentoid.util.getQueueTabStatuses
import me.devsaki.hentoid.widget.ContentSearchManager.ContentSearchBundle
import timber.log.Timber
import java.time.Instant
import java.util.EnumMap
import java.util.Locale
import java.util.Random
import kotlin.math.abs

object ObjectBoxDB {
    val store: BoxStore by lazy { initStore() }

    // Status displayed in the library view (all books of the library; both internal and external)
    val libraryStatus: IntArray = getLibraryStatuses()
    val queueStatus: IntArray = getQueueStatuses()
    private val libraryQueueStatus: IntArray = libraryStatus + queueStatus

    private const val DAY_IN_MILLIS = 1000L * 60 * 60 * 24

    // Cached queries
    private val contentFromAttributesSearchQ: Query<Content>
    private val contentFromSourceSearchQ: Query<Content>

    init {
        // Pre-cache intensive search queries
        contentFromAttributesSearchQ = buildContentFromAttributesSearchQ()
        contentFromSourceSearchQ = buildContentFromSourceSearchQ()
    }

    private fun initStore(): BoxStore {
        val context = HentoidApp.getInstance()
        val mStore = MyObjectBox.builder().androidContext(context)
            .maxSizeInKByte(Settings.maxDbSizeKb).build()
        if (BuildConfig.DEBUG && BuildConfig.INCLUDE_OBJECTBOX_BROWSER) {
            val started = Admin(mStore).start(context)
            Timber.i("ObjectBrowser started: %s", started)
        }
        return mStore
    }

    fun cleanup() {
        store.closeThreadResources()
    }

    fun tearDown() {
        store.closeThreadResources()
        store.close()
        store.deleteAllFiles()
    }

    fun getDbSizeBytes(): Long {
        return store.dbSize
    }

    private fun buildContentFromAttributesSearchQ(): Query<Content> {
        val contentFromAttributesQueryBuilder = store.boxFor(
            Content::class.java
        ).query()
        contentFromAttributesQueryBuilder.`in`(Content_.status, libraryStatus)
        contentFromAttributesQueryBuilder.link(Content_.attributes).equal(Attribute_.type, 0)
            .equal(Attribute_.name, "", QueryBuilder.StringOrder.CASE_INSENSITIVE)
        return contentFromAttributesQueryBuilder.build()
    }

    private fun buildContentFromSourceSearchQ(): Query<Content> {
        val contentFromSourceQueryBuilder = store.boxFor(Content::class.java).query()
        contentFromSourceQueryBuilder.`in`(Content_.status, libraryStatus)
        contentFromSourceQueryBuilder.equal(Content_.site, 1)
        return contentFromSourceQueryBuilder.build()
    }

    fun insertContentAndAttributes(content: Content): Pair<Long, Set<Attribute>> {
        val attributes = content.attributes
        val attrBox = store.boxFor(Attribute::class.java)
        val newAttrs: MutableSet<Attribute> = HashSet()
        try {
            // Master data management managed manually
            // Ensure all known attributes are replaced by their ID before being inserted
            // Watch https://github.com/objectbox/objectbox-java/issues/1023 for a lighter solution based on @Unique annotation
            var dbAttr: Attribute?
            var inputAttr: Attribute
            attrBox.query().equal(Attribute_.type, 0)
                .equal(Attribute_.name, "", QueryBuilder.StringOrder.CASE_INSENSITIVE).build()
                .use { attrQuery ->
                    for (i in attributes.indices) {
                        inputAttr = attributes[i]
                        dbAttr = attrQuery.setParameter(Attribute_.name, inputAttr.name)
                            .setParameter(Attribute_.type, inputAttr.type.code.toLong())
                            .findFirst() // Don't use the safe variant as it relies on attrQuery
                        // Existing attribute -> set the existing attribute
                        dbAttr?.let { attr ->
                            attributes[i] = attr
                            attr.addLocationsFrom(inputAttr) // Enrich with new locations
                            attrBox.put(attr) // Store it back
                        } ?: run { // New attribute -> normalize name
                            inputAttr.name =
                                inputAttr.name.lowercase(Locale.getDefault()).trim()
                            if (inputAttr.type == AttributeType.ARTIST || inputAttr.type == AttributeType.CIRCLE)
                                newAttrs.add(inputAttr)
                            // New attr will be stored by insertContentCore
                        }
                    }
                }
        } catch (e: Exception) {
            Timber.e(e)
        }
        val result = insertContentCore(content)
        return Pair<Long, Set<Attribute>>(result, newAttrs)
    }

    // Faster alternative to insertContent when Content fields only need to be updated
    fun insertContentCore(content: Content): Long {
        return store.boxFor(Content::class.java).put(content)
    }

    fun updateContentStatus(updateFrom: StatusContent, updateTo: StatusContent) {
        val contentList = selectContentByStatus(updateFrom)
        for (c in contentList) c.status = updateTo
        store.boxFor(Content::class.java).put(contentList)
    }

    fun updateContentProcessedFlag(contentId: Long, flag: Boolean) {
        store.boxFor(Content::class.java)[contentId]?.let { c ->
            c.isBeingProcessed = flag
            store.boxFor(Content::class.java).put(c)
        }
    }

    fun updateContentsProcessedFlag(contentIds: LongArray, flag: Boolean) {
        val contentStore = store.boxFor(Content::class.java)
        val data = contentStore[contentIds]
        data.forEach { it.isBeingProcessed = flag }
        contentStore.put(data)
    }

    fun selectContentByStatus(status: StatusContent): List<Content> {
        return selectContentByStatusCodes(intArrayOf(status.code))
    }

    private fun selectContentByStatusCodes(statusCodes: IntArray): List<Content> {
        return store.boxFor(Content::class.java).query()
            .`in`(Content_.status, statusCodes).safeFind()
    }

    fun selectAllInternalBooksQ(
        rootPath: String,
        favsOnly: Boolean,
        includePlaceholders: Boolean
    ): Query<Content> {
        // All statuses except SAVED, DOWNLOADING, PAUSED and ERROR that imply the book is in the download queue
        // and EXTERNAL because we only want to manage internal books here
        var storedContentStatus = intArrayOf(
            StatusContent.DOWNLOADED.code,
            StatusContent.MIGRATED.code,
            StatusContent.IGNORED.code,
            StatusContent.UNHANDLED_ERROR.code,
            StatusContent.CANCELED.code
        )
        if (includePlaceholders) storedContentStatus += StatusContent.PLACEHOLDER.code
        val query =
            store.boxFor(Content::class.java).query().`in`(Content_.status, storedContentStatus)
                .startsWith(
                    Content_.storageUri,
                    rootPath,
                    QueryBuilder.StringOrder.CASE_INSENSITIVE
                )
        if (favsOnly) query.equal(Content_.favourite, true)
        return query.build()
    }

    fun selectAllInternalBooksQ(rootPath: String, includePlaceholders: Boolean): Query<Content> {
        // All statuses except SAVED, DOWNLOADING, PAUSED and ERROR that imply the book is in the download queue
        // and EXTERNAL because we only want to manage internal books here
        var storedContentStatus: IntArray = intArrayOf(
            StatusContent.DOWNLOADED.code,
            StatusContent.MIGRATED.code,
            StatusContent.IGNORED.code,
            StatusContent.UNHANDLED_ERROR.code,
            StatusContent.CANCELED.code
        )
        if (includePlaceholders) storedContentStatus += StatusContent.PLACEHOLDER.code
        val query = store.boxFor(Content::class.java)
            .query().`in`(Content_.status, storedContentStatus)
            .startsWith(Content_.storageUri, rootPath, QueryBuilder.StringOrder.CASE_INSENSITIVE)
        return query.build()
    }

    fun selectAllExternalBooksQ(): Query<Content> {
        return store.boxFor(Content::class.java).query()
            .equal(Content_.status, StatusContent.EXTERNAL.code.toLong()).build()
    }

    fun selectAllErrorJsonBooksQ(): Query<Content> {
        return store.boxFor(Content::class.java).query()
            .equal(Content_.status, StatusContent.ERROR.code.toLong()).notNull(Content_.jsonUri)
            .notEqual(Content_.jsonUri, "", QueryBuilder.StringOrder.CASE_INSENSITIVE).build()
    }

    fun selectAllQueueBooksQ(): Query<Content> {
        // Strong check to make sure selected books are _actually_ part of the queue (i.e. attached to a QueueRecord)
        // NB : Can't use QueryCondition here because there's no way to query the existence of a relation (see https://github.com/objectbox/objectbox-java/issues/1110)
        return store.boxFor(Content::class.java).query()
            .`in`(Content_.status, getQueueStatuses()).filter { c: Content ->
                StatusContent.ERROR == c.status || !c.queueRecords.isEmpty()
            }.build()
    }

    fun selectAllQueueBooksQ(rootPath: String): Query<Content> {
        // Strong check to make sure selected books are _actually_ part of the queue (i.e. attached to a QueueRecord)
        // NB : Can't use QueryCondition here because there's no way to query the existence of a relation (see https://github.com/objectbox/objectbox-java/issues/1110)
        return store.boxFor(Content::class.java).query()
            .`in`(Content_.status, getQueueStatuses())
            .startsWith(Content_.storageUri, rootPath, QueryBuilder.StringOrder.CASE_INSENSITIVE)
            .filter { c: Content ->
                StatusContent.ERROR == c.status || !c.queueRecords.isEmpty()
            }.build()
    }

    fun selectQueueUrls(site: Site): HashSet<String> {
        store.boxFor(Content::class.java).query()
            .`in`(Content_.status, getQueueStatuses())
            .equal(Content_.site, site.code.toLong())
            .filter { c: Content ->
                StatusContent.ERROR == c.status || !c.queueRecords.isEmpty()
            }
            .build().use { qb ->
                return qb.property(Content_.dbUrl).findStrings().toHashSet()
            }
    }

    fun selectAllFlaggedBooksQ(): Query<Content> {
        return store.boxFor(Content::class.java).query()
            .equal(Content_.isFlaggedForDeletion, true).build()
    }

    fun selectAllProcessedBooksQ(): Query<Content> {
        return store.boxFor(Content::class.java).query()
            .equal(Content_.isBeingProcessed, true)
            .build()
    }

    fun flagContentsForDeletion(contentList: List<Content>, flag: Boolean) {
        for (c in contentList) c.isFlaggedForDeletion = flag
        store.boxFor(Content::class.java).put(contentList)
    }

    fun markContentsAsBeingProcessed(contentList: List<Content>, flag: Boolean) {
        for (c in contentList) c.isBeingProcessed = flag
        store.boxFor(Content::class.java).put(contentList)
    }

    fun deleteContentById(contentId: Long) {
        deleteContentById(longArrayOf(contentId))
    }

    /**
     * Remove the given content and all related objects from the DB
     * NB : ObjectBox v2.3.1 does not support cascade delete, so everything has to be done manually
     *
     * @param contentId IDs of the contents to be removed from the DB
     */
    fun deleteContentById(contentId: LongArray) {
        val errorBox = store.boxFor(ErrorRecord::class.java)
        val imageFileBox = store.boxFor(ImageFile::class.java)
        val chapterBox = store.boxFor(Chapter::class.java)
        val contentBox = store.boxFor(Content::class.java)
        val groupItemBox = store.boxFor(GroupItem::class.java)
        val groupBox = store.boxFor(Group::class.java)
        for (id in contentId) {
            contentBox[id]?.let { c ->
                store.runInTx {
                    c.imageFiles.apply {
                        imageFileBox.remove(this)
                        clear() // Clear links to all imageFiles
                    }
                    c.chapters.apply {
                        chapterBox.remove(this)
                        clear() // Clear links to all chapters
                    }
                    c.errorLog.apply {
                        errorBox.remove(this)
                        clear() // Clear links to all errorRecords
                    }

                    // Clear links to all attributes
                    // NB : Properly removing all attributes here is too costly, especially on large collections
                    // It's done by calling cleanupOrphanAttributes
                    c.attributes.clear()

                    // Delete corresponding groupItem
                    val groupItems = groupItemBox.query().equal(GroupItem_.contentId, id).safeFind()
                    for (groupItem in groupItems) {
                        // If we're not in the Custom grouping and it's the only item of its group, delete the group
                        val g = groupItem.linkedGroup
                        if (g != null && g.grouping != Grouping.CUSTOM && g.getItems().size < 2)
                            groupBox.remove(g)
                        // Delete the item
                        groupItemBox.remove(groupItem)
                    }
                    contentBox.remove(c) // Remove the content itself
                }
            }
        }
    }

    /**
     * Cleanup all Attributes that don't have any backlink among content
     */
    fun cleanupOrphanAttributes() {
        val attributeBox = store.boxFor(Attribute::class.java)
        val locationBox = store.boxFor(AttributeLocation::class.java)
        // Get the attributes to clean
        val attrsToClean = attributeBox.query().filter { it.contents.isEmpty() }.safeFind()

        // Clean the attributes
        for (attr in attrsToClean) {
            Timber.v(">> Found empty attr : %s", attr.name)
            locationBox.remove(attr.locations)
            attr.locations.clear() // Clear location links
            attributeBox.remove(attr) // Delete the attribute itself
        }
    }

    fun selectQueueContents(): List<Content> {
        val result: MutableList<Content> = ArrayList()
        val queueRecords = selectQueueRecordsQ().safeFind()
        for (q in queueRecords) {
            q.linkedContent?.let { result.add(it) }
        }
        return result
    }

    fun selectQueueRecordsQ(): Query<QueueRecord> {
        return selectQueueRecordsQ(null, null)
    }

    fun selectQueueRecordsQ(query: String?, source: Site?): Query<QueueRecord> {
        val qb = store.boxFor(QueueRecord::class.java).query()
        if (!query.isNullOrEmpty() || source != null && source != Site.NONE) {
            val bundle = ContentSearchBundle()
            bundle.query = query ?: ""
            if (source != null && source != Site.NONE) {
                val sourceAttr: MutableSet<Attribute> = HashSet()
                sourceAttr.add(Attribute(source))
                bundle.attributes = buildSearchUri(sourceAttr, "", 0, 0).toString()
            }
            bundle.sortField = Settings.Value.ORDER_FIELD_NONE
            val contentIds = selectContentHybridSearchId(
                bundle,
                LongArray(0),
                getQueueTabStatuses()
            )
            qb.`in`(QueueRecord_.contentId, contentIds)
        }
        return qb.order(QueueRecord_.rank).build()
    }

    fun isContentInQueue(c: Content): Boolean {
        return store.boxFor(QueueRecord::class.java)
            .query().equal(QueueRecord_.contentId, c.id)
            .safeCount() > 0
    }

    fun selectMaxQueueOrder(): Long {
        store.boxFor(QueueRecord::class.java).query().build().use { qrc ->
            return qrc.property(QueueRecord_.rank).max()
        }
    }

    fun insertQueue(contentId: Long, order: Int) {
        store.boxFor(QueueRecord::class.java).put(QueueRecord(contentId, order))
    }

    fun updateQueue(queue: List<QueueRecord>) {
        val queueRecordBox = store.boxFor(QueueRecord::class.java)
        queueRecordBox.put(queue)
    }

    fun deleteQueueRecords(content: Content) {
        deleteQueueRecords(content.id)
    }

    fun deleteQueueRecords(queueIndex: Int) {
        store.boxFor(QueueRecord::class.java)
            .remove(selectQueueRecordsQ().safeFind()[queueIndex].id)
    }

    fun deleteQueueRecords() {
        store.boxFor(QueueRecord::class.java).removeAll()
    }

    private fun deleteQueueRecords(contentId: Long) {
        val queueRecordBox = store.boxFor(QueueRecord::class.java)
        val record =
            queueRecordBox.query().equal(QueueRecord_.contentId, contentId).safeFindFirst()
        if (record != null) queueRecordBox.remove(record)
    }

    fun deleteQueueRecords(ids: LongArray) {
        val queueRecordBox = store.boxFor(QueueRecord::class.java)
        queueRecordBox.remove(*ids)
    }

    fun selectVisibleContentQ(): Query<Content> {
        val bundle = ContentSearchBundle()
        bundle.sortField = Settings.Value.ORDER_FIELD_NONE
        return selectContentSearchContentQ(
            bundle,
            LongArray(0),
            emptySet(),
            libraryStatus
        )
    }

    fun selectContentById(id: Long): Content? {
        return store.boxFor(Content::class.java)[id]
    }

    fun selectContentById(ids: List<Long>): List<Content> {
        return store.boxFor(Content::class.java)[ids]
    }

    private fun selectContentIdsByChapterUrl(url: String): LongArray {
        if (url.isEmpty()) return LongArray(0)
        store.boxFor(Chapter::class.java).query(
            Chapter_.url.notEqual("", QueryBuilder.StringOrder.CASE_INSENSITIVE)
                .and(Chapter_.url.endsWith(url, QueryBuilder.StringOrder.CASE_INSENSITIVE))
        ).build().use { cq ->
            return cq.property(Chapter_.contentId).findLongs()
        }
    }

    // Find any book that has the given content URL _or_ a chapter with the given content URL _or_ has a cover starting with the given cover URL
    fun selectContentByUrlOrCover(
        site: Site,
        contentUrl: String,
        coverUrlStart: String
    ): Content? {
        val contentUrlCondition =
            Content_.dbUrl.notEqual("", QueryBuilder.StringOrder.CASE_INSENSITIVE)
                .and(
                    Content_.dbUrl.equal(
                        contentUrl,
                        QueryBuilder.StringOrder.CASE_INSENSITIVE
                    )
                )
                .and(Content_.site.equal(site.code))
        val chapterUrlCondition: QueryCondition<Content> =
            Content_.id.oneOf(selectContentIdsByChapterUrl(contentUrl))
        val urlCondition = contentUrlCondition.or(chapterUrlCondition)
        return if (coverUrlStart.isNotEmpty()) {
            val coverCondition =
                Content_.coverImageUrl.notEqual("", QueryBuilder.StringOrder.CASE_INSENSITIVE)
                    .and(
                        Content_.coverImageUrl.startsWith(
                            coverUrlStart,
                            QueryBuilder.StringOrder.CASE_INSENSITIVE
                        )
                    )
                    .and(Content_.site.equal(site.code))

            store.boxFor(Content::class.java).query(urlCondition.or(coverCondition))
                .order(Content_.id).safeFindFirst()
        } else
            store.boxFor(Content::class.java).query(urlCondition)
                .order(Content_.id).safeFindFirst()
    }

    // Find all books that have the given content URL
    fun selectContentByUrl(site: Site, contentUrl: String): Set<Content> {
        val contentUrlCondition =
            Content_.dbUrl.notEqual("", QueryBuilder.StringOrder.CASE_INSENSITIVE)
                .and(
                    Content_.dbUrl.equal(
                        contentUrl,
                        QueryBuilder.StringOrder.CASE_INSENSITIVE
                    )
                )
                .and(Content_.site.equal(site.code))

        return store.boxFor(Content::class.java).query(contentUrlCondition)
            .order(Content_.id).safeFind().toSet()
    }

    fun selectContentsByQtyPageAndSize(qtyPage: Int, size: Long): Set<Content> {
        val contentUrlCondition =
            Content_.size.equal(size)
                .and(Content_.qtyPages.equal(qtyPage))

        return store.boxFor(Content::class.java).query(contentUrlCondition)
            .order(Content_.id).safeFind().toSet()
    }

    fun selectAllContentUrls(siteCode: Int): Set<String> {
        store.boxFor(Content::class.java).query().equal(Content_.site, siteCode.toLong())
            .`in`(Content_.status, libraryStatus).notNull(Content_.dbUrl)
            .notEqual(Content_.dbUrl, "", QueryBuilder.StringOrder.CASE_INSENSITIVE).build()
            .use { allContentQ ->
                return allContentQ.property(Content_.dbUrl).findStrings().toHashSet()
            }
    }

    fun selectAllMergedContentUrls(site: Site): Set<String> {
        store.boxFor(Chapter::class.java).query()
            .startsWith(Chapter_.url, site.url, QueryBuilder.StringOrder.CASE_INSENSITIVE)
            .build()
            .use { allChapterQ ->
                return allChapterQ.property(Chapter_.url).findStrings().toHashSet()
            }
    }

    fun selectContentEndWithStorageUri(folderUriEnd: String, onlyFlagged: Boolean): Content? {
        val queryBuilder = store.boxFor(
            Content::class.java
        ).query()
            .endsWith(
                Content_.storageUri,
                folderUriEnd,
                QueryBuilder.StringOrder.CASE_INSENSITIVE
            )
        if (onlyFlagged) queryBuilder.equal(Content_.isFlaggedForDeletion, true)
        return queryBuilder.build().safeFindFirst()
    }

    private fun getIdsFromAttributes(attrs: Set<Attribute>): LongArray {
        if (attrs.isEmpty()) return LongArray(0)
        val firstAttr = attrs.firstOrNull()
        return if (firstAttr != null && firstAttr.isExcluded) {
            val filteredBooks = selectFilteredContent(attrs)

            // Find all content positively matching the given attributes
            // TODO... but the attrs are already negative ^^"
            val query = store.boxFor(Content::class.java).query()
            query.`in`(Content_.status, libraryStatus)
            query.`in`(Content_.id, filteredBooks)
            val content = query.safeFind()

            // Extract sites from them
            val contentPerSite = content.groupBy { c -> c.site }
            val filteredSiteCodes: MutableSet<Long> = HashSet()
            contentPerSite.forEach {
                filteredSiteCodes.add(it.key.code.toLong())
            }
            val result: List<Long> = ArrayList(filteredSiteCodes)
            result.toLongArray()
        } else {
            val result = LongArray(attrs.size)
            if (attrs.isNotEmpty()) {
                var index = 0
                for (a in attrs) result[index++] = a.id
            }
            result
        }
    }

    private fun applySortOrder(
        query: QueryBuilder<Content>,
        orderField: Int,
        orderDesc: Boolean
    ) {
        // Random ordering is tricky (see https://github.com/objectbox/objectbox-java/issues/17)
        // => Implemented post-query build
        if (orderField == Settings.Value.ORDER_FIELD_RANDOM) return
        // Custom ordering depends on another "table"
        // => Implemented post-query build
        if (orderField == Settings.Value.ORDER_FIELD_CUSTOM) {
            /*
            query.sort(new Content.GroupItemOrderComparator(groupId)); // doesn't work with PagedList because it uses Query.find(final long offset, final long limit)
            query.backlink(GroupItem_.content).order(GroupItem_.order); // doesn't work yet (see https://github.com/objectbox/objectbox-java/issues/141)
             */
            return
        }
        val field = getPropertyFromField(orderField) ?: return
        if (orderDesc) query.orderDesc(field) else query.order(field)

        // Specifics sub-sorting fields when ordering by reads
        if (orderField == Settings.Value.ORDER_FIELD_READS) {
            if (orderDesc) query.orderDesc(Content_.lastReadDate) else query.order(Content_.lastReadDate)
                .orderDesc(Content_.downloadDate)
        }
    }

    private fun getPropertyFromField(prefsFieldCode: Int): Property<Content>? {
        return when (prefsFieldCode) {
            Settings.Value.ORDER_FIELD_TITLE -> Content_.title
            Settings.Value.ORDER_FIELD_ARTIST -> Content_.dbAuthor
            Settings.Value.ORDER_FIELD_NB_PAGES -> Content_.qtyPages
            Settings.Value.ORDER_FIELD_DOWNLOAD_PROCESSING_DATE -> Content_.downloadDate
            Settings.Value.ORDER_FIELD_DOWNLOAD_COMPLETION_DATE -> Content_.downloadCompletionDate
            Settings.Value.ORDER_FIELD_UPLOAD_DATE -> Content_.uploadDate
            Settings.Value.ORDER_FIELD_READ_DATE -> Content_.lastReadDate
            Settings.Value.ORDER_FIELD_READS -> Content_.reads
            Settings.Value.ORDER_FIELD_SIZE -> Content_.size
            Settings.Value.ORDER_FIELD_READ_PROGRESS -> Content_.readProgress
            else -> null
        }
    }

    fun selectNoContentQ(): Query<Content> {
        return store.boxFor(Content::class.java).query().equal(Content_.id, -1).build()
    }

    fun selectContentSearchContentQ(
        searchBundle: ContentSearchBundle,
        dynamicGroupContentIds: LongArray,
        metadata: Set<Attribute>?,
        statuses: IntArray
    ): Query<Content> {
        if (Settings.Value.ORDER_FIELD_CUSTOM == searchBundle.sortField) return store.boxFor(
            Content::class.java
        ).query().build()
        val metadataMap = AttributeMap()
        metadataMap.addAll(metadata)
        val hasTitleFilter = searchBundle.query.isNotEmpty()
        val sources = metadataMap[AttributeType.SOURCE]
        val hasSiteFilter =
            metadataMap.containsKey(AttributeType.SOURCE) && !sources.isNullOrEmpty()
        val hasTagFilter = metadataMap.keys.size > if (hasSiteFilter) 1 else 0
        var qc = initContentQC(searchBundle, dynamicGroupContentIds, statuses)
        if (hasSiteFilter) qc = qc.and(Content_.site.oneOf(getIdsFromAttributes(sources!!)))
        if (hasTitleFilter) qc = qc.and(
            Content_.title.contains(
                searchBundle.query,
                QueryBuilder.StringOrder.CASE_INSENSITIVE
            )
        )
        if (hasTagFilter) {
            for ((attrType, attrs) in metadataMap) {
                if (attrType != AttributeType.SOURCE) { // Not a "real" attribute in database
                    if (attrs.isNotEmpty()) {
                        qc = qc.and(Content_.id.oneOf(selectFilteredContent(attrs)))
                    }
                }
            }
        }
        qc = applyContentLocationFilter(
            qc,
            Location.entries.first { it.value == searchBundle.location })
        qc = applyContentTypeFilter(
            qc,
            Type.entries.first { it.value == searchBundle.contentType })
        val query = store.boxFor(Content::class.java).query(qc)
        if (searchBundle.filterPageFavourites) filterWithPageFavs(query)
        applySortOrder(query, searchBundle.sortField, searchBundle.sortDesc)
        return query.build()
    }

    fun selectContentSearchContentByGroupItem(
        searchBundle: ContentSearchBundle,
        dynamicGroupContentIds: LongArray,
        metadata: Set<Attribute>?
    ): LongArray {
        if (searchBundle.sortField != Settings.Value.ORDER_FIELD_CUSTOM) return longArrayOf()
        val metadataMap = AttributeMap()
        metadataMap.addAll(metadata)
        val hasTitleFilter = searchBundle.query.isNotEmpty()
        val sources = metadataMap[AttributeType.SOURCE]
        val hasSiteFilter =
            metadataMap.containsKey(AttributeType.SOURCE) && !sources.isNullOrEmpty()
        val hasTagFilter = metadataMap.keys.size > if (hasSiteFilter) 1 else 0

        // Pre-filter and order on GroupItem
        val query = store.boxFor(
            GroupItem::class.java
        ).query()
        if (searchBundle.groupId > 0) {
            if (dynamicGroupContentIds.isEmpty()) query.equal(
                GroupItem_.groupId,
                searchBundle.groupId
            ) else query.`in`(GroupItem_.contentId, dynamicGroupContentIds)
        }
        if (searchBundle.sortDesc) query.orderDesc(GroupItem_.order) else query.order(GroupItem_.order)

        // Get linked Content
        val contentQuery = query.link(GroupItem_.content)
        if (hasSiteFilter) contentQuery.`in`(Content_.site, getIdsFromAttributes(sources!!))
        if (searchBundle.filterBookFavourites) contentQuery.equal(
            Content_.favourite,
            true
        ) else if (searchBundle.filterBookNonFavourites) contentQuery.equal(
            Content_.favourite,
            false
        )
        if (searchBundle.filterBookCompleted) contentQuery.equal(
            Content_.completed,
            true
        ) else if (searchBundle.filterBookNotCompleted) contentQuery.equal(
            Content_.completed,
            false
        )
        if (searchBundle.filterRating > -1) contentQuery.equal(
            Content_.rating,
            searchBundle.filterRating.toLong()
        )
        if (hasTitleFilter) contentQuery.contains(
            Content_.title,
            searchBundle.query,
            QueryBuilder.StringOrder.CASE_INSENSITIVE
        )
        if (hasTagFilter) {
            for ((attrType, attrs) in metadataMap) {
                if (attrType != AttributeType.SOURCE) { // Not a "real" attribute in database
                    if (attrs.isNotEmpty()) {
                        contentQuery.`in`(Content_.id, selectFilteredContent(attrs))
                    }
                }
            }
        }
        return query.safeFind().map { gi -> gi.contentId }.toLongArray()
    }

    private fun selectContentUniversalAttributesQ(
        searchBundle: ContentSearchBundle,
        dynamicGroupContentIds: LongArray,
        statuses: IntArray
    ): Query<Content> {
        val qc = initContentQC(searchBundle, dynamicGroupContentIds, statuses)
        val query = store.boxFor(Content::class.java).query(qc)
        if (searchBundle.filterPageFavourites) filterWithPageFavs(query)
        query.link(Content_.attributes).contains(
            Attribute_.name,
            searchBundle.query,
            QueryBuilder.StringOrder.CASE_INSENSITIVE
        )
        return query.build()
    }

    private fun selectContentUniversalContentQ(
        searchBundle: ContentSearchBundle,
        additionalIds: LongArray,
        dynamicGroupContentIds: LongArray,
        statuses: IntArray
    ): Query<Content> {
        if (Settings.Value.ORDER_FIELD_CUSTOM == searchBundle.sortField)
            return store.boxFor(Content::class.java).query().build()
        var qc = initContentQC(searchBundle, dynamicGroupContentIds, statuses)
        qc = qc.and(
            Content_.title.contains(
                searchBundle.query,
                QueryBuilder.StringOrder.CASE_INSENSITIVE
            )
                .or(
                    Content_.uniqueSiteId.equal(
                        searchBundle.query,
                        QueryBuilder.StringOrder.CASE_INSENSITIVE
                    )
                )
                .or(Content_.id.oneOf(additionalIds))
        )
        val qb = store.boxFor(Content::class.java).query(qc)
        if (searchBundle.filterPageFavourites) filterWithPageFavs(qb)
        applySortOrder(qb, searchBundle.sortField, searchBundle.sortDesc)
        return qb.build()
    }

    private fun initContentQC(
        searchBundle: ContentSearchBundle,
        dynamicGroupContentIds: LongArray,
        statuses: IntArray
    ): QueryCondition<Content> {
        var qc: QueryCondition<Content> = Content_.status.oneOf(statuses)
        if (searchBundle.filterBookFavourites) qc =
            qc.and(Content_.favourite.equal(true)) else if (searchBundle.filterBookNonFavourites) qc =
            qc.and(Content_.favourite.equal(false))
        if (searchBundle.filterBookCompleted) qc =
            qc.and(Content_.completed.equal(true)) else if (searchBundle.filterBookNotCompleted) qc =
            qc.and(Content_.completed.equal(false))
        if (searchBundle.filterRating > -1) qc =
            qc.and(Content_.rating.equal(searchBundle.filterRating))
        if (searchBundle.groupId > 0) qc =
            applyContentGroupFilter(qc, searchBundle.groupId, dynamicGroupContentIds)
        return qc
    }

    private fun selectContentUniversalContentByGroupItem(
        searchBundle: ContentSearchBundle,
        dynamicGroupContentIds: LongArray,
        additionalIds: LongArray
    ): LongArray {
        if (searchBundle.sortField != Settings.Value.ORDER_FIELD_CUSTOM) return longArrayOf()

        // Pre-filter and order on GroupItem
        val query = store.boxFor(
            GroupItem::class.java
        ).query()
        if (searchBundle.groupId > 0) query.equal(GroupItem_.groupId, searchBundle.groupId)
        if (searchBundle.sortDesc) query.orderDesc(GroupItem_.order) else query.order(GroupItem_.order)

        // Get linked content
        val qb = query.link(GroupItem_.content)
        qb.`in`(Content_.status, libraryStatus)
        if (searchBundle.filterBookFavourites) qb.equal(
            Content_.favourite,
            true
        ) else if (searchBundle.filterBookNonFavourites) qb.equal(Content_.favourite, false)
        if (searchBundle.filterBookCompleted) qb.equal(
            Content_.completed,
            true
        ) else if (searchBundle.filterBookNotCompleted) qb.equal(Content_.completed, false)
        if (searchBundle.filterRating > -1) qb.equal(
            Content_.rating,
            searchBundle.filterRating.toLong()
        )
        qb.contains(
            Content_.title,
            searchBundle.query,
            QueryBuilder.StringOrder.CASE_INSENSITIVE
        )
        qb.or().equal(
            Content_.uniqueSiteId,
            searchBundle.query,
            QueryBuilder.StringOrder.CASE_INSENSITIVE
        )
        //        query.or().link(Content_.attributes).contains(Attribute_.name, queryStr, QueryBuilder.StringOrder.CASE_INSENSITIVE); // Use of or() here is not possible yet with ObjectBox v2.3.1
        qb.or().`in`(Content_.id, additionalIds)
        // TODO use applyContentGroupFilter instead; requires using QueryCondition instead of QueryBuilder
        if (searchBundle.groupId > 0) {
            if (dynamicGroupContentIds.isEmpty()) // Classic group
                qb.`in`(Content_.id, selectFilteredContent(searchBundle.groupId)) else qb.`in`(
                Content_.id,
                dynamicGroupContentIds
            ) // Dynamic group
        }
        return query.safeFind().map { gi -> gi.contentId }.toLongArray()
    }

    fun selectContentUniversalQ(
        searchBundle: ContentSearchBundle,
        dynamicGroupContentIds: LongArray
    ): Query<Content> {
        return selectContentUniversalQ(searchBundle, dynamicGroupContentIds, libraryStatus)
    }

    fun selectContentUniversalQ(
        searchBundle: ContentSearchBundle,
        dynamicGroupContentIds: LongArray,
        status: IntArray
    ): Query<Content> {
        // Due to objectBox limitations (see https://github.com/objectbox/objectbox-java/issues/497)
        // querying Content and attributes have to be done separately
        val ids = selectContentUniversalAttributesQ(
            searchBundle,
            dynamicGroupContentIds,
            status
        ).safeFindIds()
        return selectContentUniversalContentQ(searchBundle, ids, dynamicGroupContentIds, status)
    }

    fun selectContentUniversalByGroupItem(
        searchBundle: ContentSearchBundle,
        dynamicGroupContentIds: LongArray
    ): LongArray {
        // Due to objectBox limitations (see https://github.com/objectbox/objectbox-java/issues/497)
        // querying Content and attributes have to be done separately
        val ids = selectContentUniversalAttributesQ(
            searchBundle,
            dynamicGroupContentIds,
            libraryStatus
        ).safeFindIds()
        return selectContentUniversalContentByGroupItem(
            searchBundle,
            dynamicGroupContentIds,
            ids
        )
    }

    fun getShuffledIds(): List<Long> {
        store.boxFor(ShuffleRecord::class.java).query().build().use { srq ->
            return srq.property(ShuffleRecord_.contentId).findLongs().toList()
        }
    }

    fun shuffleContentIds() {
        // Clear previous shuffled list
        val shuffleStore = store.boxFor(ShuffleRecord::class.java)
        shuffleStore.removeAll()
        // Populate with a new list
        val allBooksIds = selectStoredContentQ(
            false,
            -1,
            false
        ).safeFindIds().toMutableList()
        allBooksIds.shuffle(Random(getSeed(SEED_CONTENT)))
        shuffleStore.put(allBooksIds.map { ShuffleRecord(contentId = it) })
    }

    private fun shuffleRandomSortId(query: Query<Content>): LongArray {
        val queryIds = query.findIds().toMutableSet()
        val shuffleIds = getShuffledIds()
        val shuffledSet = LinkedHashSet<Long>(shuffleIds.size)
        shuffledSet.addAll(shuffleIds)

        // Keep common IDs
        shuffledSet.retainAll(queryIds)

        // Isolate new IDs that have never been shuffled and append them at the end
        if (shuffledSet.size < queryIds.size) {
            queryIds.removeAll(shuffledSet)
            shuffledSet.addAll(queryIds)
        }
        return shuffledSet.toLongArray()
    }

    // TODO use searchBundle to pass metadata instead of a separate argument (see selectContentHybridSearchId)
    fun selectContentSearchId(
        searchBundle: ContentSearchBundle,
        dynamicGroupContentIds: LongArray,
        metadata: Set<Attribute>?,
        statuses: IntArray
    ): LongArray {
        var result: LongArray
        selectContentSearchContentQ(
            searchBundle,
            dynamicGroupContentIds,
            metadata,
            statuses
        ).use { query ->
            result = if (searchBundle.sortField != Settings.Value.ORDER_FIELD_RANDOM) {
                query.findIds()
            } else {
                shuffleRandomSortId(query)
            }
        }
        return result
    }

    fun selectContentUniversalId(
        searchBundle: ContentSearchBundle,
        dynamicGroupContentIds: LongArray,
        statuses: IntArray
    ): LongArray {
        var result: LongArray
        // Due to objectBox limitations (see https://github.com/objectbox/objectbox-java/issues/497)
        // querying Content and attributes have to be done separately
        val ids = selectContentUniversalAttributesQ(
            searchBundle,
            dynamicGroupContentIds,
            statuses
        ).safeFindIds()
        selectContentUniversalContentQ(
            searchBundle,
            ids,
            dynamicGroupContentIds,
            statuses
        ).use { query ->
            result = if (searchBundle.sortField != Settings.Value.ORDER_FIELD_RANDOM) {
                query.findIds()
            } else {
                shuffleRandomSortId(query)
            }
        }
        return result
    }

    private fun selectContentHybridSearchId(
        searchBundle: ContentSearchBundle,
        dynamicGroupContentIds: LongArray,
        statuses: IntArray
    ): LongArray {
        // Start with full text if query exists, as it has better chances of narrowing down results
        val query = searchBundle.query
        val idsFullText = if (query.isNotEmpty()) selectContentUniversalId(
            searchBundle,
            dynamicGroupContentIds,
            statuses
        ) else LongArray(0)
        val metadata: Set<Attribute> = parseSearchUri(searchBundle.attributes).attributes
        val idsAttrs = if (metadata.isNotEmpty()) selectContentSearchId(
            searchBundle,
            dynamicGroupContentIds,
            metadata,
            statuses
        ) else LongArray(0)
        // Intersect if needed
        return if (idsFullText.isNotEmpty() && idsAttrs.isNotEmpty()) {
            val fullTextSet = idsFullText.toMutableSet()
            val attrsSet = idsAttrs.toSet()
            fullTextSet.retainAll(attrsSet)
            fullTextSet.toLongArray()
        } else {
            if (idsFullText.isNotEmpty()) idsFullText else idsAttrs
        }
    }

    private fun selectFilteredContent(groupId: Long): LongArray {
        if (groupId < 1) return LongArray(0)
        val qb = store.boxFor(Content::class.java).query()
        qb.link(Content_.groupItems).equal(GroupItem_.groupId, groupId)
        return qb.safeFindIds()
    }

    private fun selectFilteredContent(attrs: Set<Attribute>): LongArray {
        return selectFilteredContent(
            -1,
            LongArray(0),
            attrs,
            Location.ANY,
            Type.ANY
        )
    }

    private fun selectFilteredContent(
        groupId: Long,
        dynamicGroupContentIds: LongArray,
        attributesFilter: Set<Attribute>?,
        location: Location,
        contentType: Type
    ): LongArray {
        val attrs = attributesFilter ?: emptySet()
        if (attrs.isEmpty() && groupId < 1 && Location.ANY == location && Type.ANY == contentType)
            return LongArray(0)

        // Handle simple case where no attributes have been selected
        if (attrs.isEmpty()) {
            var qc: QueryCondition<Content> = Content_.status.oneOf(libraryStatus)
            if (groupId > 0) qc = applyContentGroupFilter(qc, groupId, dynamicGroupContentIds)
            qc = applyContentLocationFilter(qc, location)
            qc = applyContentTypeFilter(qc, contentType)
            return store.boxFor(Content::class.java).query(qc).safeFindIds()
        }

        // Pre-build queries to reuse them efficiently within the loops

        // Content from attribute
        val contentFromAttributesQuery: Query<Content>
        var useCachedAttrQuery = false
        if (groupId < 1 && Location.ANY == location && Type.ANY == contentType) { // Standard cached query
            contentFromAttributesQuery = contentFromAttributesSearchQ
            useCachedAttrQuery = true
        } else { // On-demand query
            var qc: QueryCondition<Content> = Content_.status.oneOf(libraryStatus)
            qc = applyContentLocationFilter(qc, location)
            qc = applyContentTypeFilter(qc, contentType)
            if (groupId > 0) qc = applyContentGroupFilter(qc, groupId, dynamicGroupContentIds)
            val contentFromAttributesQueryBuilder = store.boxFor(
                Content::class.java
            ).query(qc)
            contentFromAttributesQueryBuilder.link(Content_.attributes)
                .equal(Attribute_.type, 0)
                .equal(Attribute_.name, "", QueryBuilder.StringOrder.CASE_INSENSITIVE)
            contentFromAttributesQuery = contentFromAttributesQueryBuilder.build()
        }

        // Content from source (distinct query as source is not an actual Attribute of the data model)
        val contentFromSourceQuery: Query<Content>
        var useCachedSourceQuery = false
        if (groupId < 1 && Location.ANY == location && Type.ANY == contentType) { // Standard cached query
            contentFromSourceQuery = contentFromSourceSearchQ
            useCachedSourceQuery = true
        } else { // On-demand query
            var qc: QueryCondition<Content> = Content_.status.oneOf(libraryStatus)
            qc = qc.and(Content_.site.equal(1))
            qc = applyContentLocationFilter(qc, location)
            qc = applyContentTypeFilter(qc, contentType)
            if (groupId > 0) qc = applyContentGroupFilter(qc, groupId, dynamicGroupContentIds)
            contentFromSourceQuery = store.boxFor(Content::class.java).query(qc).build()
        }

        // Prepare first iteration for exclusion mode
        // If first tag is to be excluded, start with the whole database and _remove_ IDs (inverse logic)
        var idsFull = emptyList<Long>().toMutableList()
        val firstAttr = attrs.firstOrNull()
        if (firstAttr != null && firstAttr.isExcluded) {
            val contentFromAttributesQueryBuilder1 = store.boxFor(Content::class.java).query()
            contentFromAttributesQueryBuilder1.`in`(Content_.status, libraryStatus)
            idsFull = contentFromAttributesQueryBuilder1.safeFindIds().toMutableList()
        }

        // Cumulative query loop
        // Each iteration restricts the results of the next because advanced search uses an AND logic
        var results = emptyList<Long>().toMutableList()
        var ids: LongArray
        try {
            for (attr in attrs) {
                ids = if (attr.type == AttributeType.SOURCE) {
                    contentFromSourceQuery.setParameter(Content_.site, attr.id).findIds()
                } else {
                    contentFromAttributesQuery.setParameter(
                        Attribute_.type,
                        attr.type.code.toLong()
                    ).setParameter(Attribute_.name, attr.name).findIds()
                }
                if (results.isEmpty()) { // First iteration
                    results = ids.toMutableList()

                    // If first tag is to be excluded, start trimming results
                    if (attr.isExcluded) {
                        idsFull.removeAll(results)
                        results = idsFull
                    }
                } else {
                    // Filter results with newly found IDs (only common IDs should stay)
                    val idsAsSet = ids.toSet()
                    // Remove ids that fit the attribute from results
                    // Careful with retainAll performance when using List instead of Set
                    if (attr.isExcluded) results.removeAll(idsAsSet) else results.retainAll(
                        idsAsSet
                    )
                }
            }
        } finally {
            if (!useCachedAttrQuery) contentFromAttributesQuery.close()
            if (!useCachedSourceQuery) contentFromSourceQuery.close()
        }
        return results.toLongArray()
    }

    private fun filterWithPageFavs(builder: QueryBuilder<Content>) {
        builder.link(Content_.imageFiles).equal(ImageFile_.favourite, true)
    }

    fun selectAvailableSources(): List<Attribute> {
        return selectAvailableSources(
            -1,
            LongArray(0),
            null,
            Location.ANY,
            Type.ANY,
            false
        )
    }

    fun selectAvailableSources(
        groupId: Long,
        dynamicGroupContentIds: LongArray,
        filter: Set<Attribute>?,
        location: Location,
        contentType: Type,
        includeFreeAttrs: Boolean
    ): List<Attribute> {
        var qc: QueryCondition<Content> = Content_.status.oneOf(libraryStatus)
        if (!filter.isNullOrEmpty()) {
            val metadataMap = AttributeMap()
            metadataMap.addAll(filter)
            val params = metadataMap[AttributeType.SOURCE]
            if (!params.isNullOrEmpty()) qc =
                qc.and(Content_.site.oneOf(getIdsFromAttributes(params)))
            for ((attrType, attrs) in metadataMap) {
                if (attrType != AttributeType.SOURCE) { // Not a "real" attribute in database
                    if (attrs.isNotEmpty() && !includeFreeAttrs) qc =
                        qc.and(Content_.id.oneOf(selectFilteredContent(attrs)))
                }
            }
        }
        if (groupId > 0) qc = applyContentGroupFilter(qc, groupId, dynamicGroupContentIds)
        qc = applyContentLocationFilter(qc, location)
        qc = applyContentTypeFilter(qc, contentType)
        val query = store.boxFor(Content::class.java).query(qc)
        val content = query.safeFind()

        // SELECT field, COUNT(*) GROUP BY (field) is not implemented in ObjectBox v2.3.1
        // (see https://github.com/objectbox/objectbox-java/issues/422)
        // => Group by and count have to be done manually (thanks God Stream exists !)
        // Group and count by source
        val map = content.groupBy { c -> c.site }
        val result: MutableList<Attribute> = ArrayList()
        map.forEach {
            val size = it.value.size
            val attr = Attribute(it.key)
            attr.externalId = it.key.code
            attr.count = size
            result.add(attr)
        }
        // Order by count desc
        return result.sortedBy { a -> -a.count }
    }

    fun selectErrorContentQ(): Query<Content> {
        return store.boxFor(Content::class.java).query()
            .equal(Content_.status, StatusContent.ERROR.code.toLong())
            .orderDesc(Content_.downloadDate).build()
    }

    fun selectContentByDlDate(minDays: Int, maxDays: Int): List<Content> {
        var qc: QueryCondition<Content> = Content_.status.oneOf(libraryStatus)
        qc = applyContentDownloadDateFilter(qc, minDays, maxDays)
        return store.boxFor(Content::class.java).query(qc).safeFind()
    }

    private fun applyContentGroupFilter(
        qc: QueryCondition<Content>,
        groupId: Long,
        dynamicGroupContentIds: LongArray
    ): QueryCondition<Content> {
        return if (dynamicGroupContentIds.isEmpty()) {
            val group = store.boxFor(
                Group::class.java
            )[groupId]
            if (group != null && group.grouping == Grouping.DL_DATE) // According to days since download date
                applyContentDownloadDateFilter(
                    qc,
                    group.propertyMin,
                    group.propertyMax
                ) else if (group != null && group.isUngroupedGroup) // Ungrouped = Books with no CUSTOM group attached
                qc.and(Content_.id.notOneOf(selectCustomGroupedContent())) else  // Direct link to group
                qc.and(Content_.id.oneOf(selectFilteredContent(groupId)))
        } else { // Dynamic group
            qc.and(Content_.id.oneOf(dynamicGroupContentIds))
        }
    }

    private fun applyContentDownloadDateFilter(
        qc: QueryCondition<Content>,
        minDays: Int,
        maxDays: Int
    ): QueryCondition<Content> {
        val today = Instant.now().toEpochMilli()
        val minDownloadDate = today - maxDays * DAY_IN_MILLIS
        val maxDownloadDate = today - minDays * DAY_IN_MILLIS
        return qc.and(Content_.downloadDate.between(minDownloadDate, maxDownloadDate))
    }

    fun insertAttribute(attr: Attribute): Long {
        return store.boxFor(Attribute::class.java).put(attr)
    }

    fun selectAttribute(id: Long): Attribute? {
        return store.boxFor(Attribute::class.java)[id]
    }

    private fun queryAvailableAttributesQ(
        type: AttributeType,
        filter: String?,
        filteredContent: LongArray,
        includeFreeAttrs: Boolean
    ): Query<Attribute> {
        val query = store.boxFor(
            Attribute::class.java
        ).query()
        query.equal(Attribute_.type, type.code.toLong())
        if (filter != null && filter.trim().isNotEmpty()) query.contains(
            Attribute_.name,
            filter.trim { it <= ' ' },
            QueryBuilder.StringOrder.CASE_INSENSITIVE
        )
        if (!includeFreeAttrs) {
            if (filteredContent.isNotEmpty()) query.link(Attribute_.contents)
                .`in`(Content_.id, filteredContent)
                .`in`(Content_.status, libraryStatus) else query.link(Attribute_.contents)
                .`in`(Content_.status, libraryStatus)
        }
        return query.build()
    }

    fun countAvailableAttributes(
        type: AttributeType,
        groupId: Long,
        dynamicGroupContentIds: LongArray,
        attributeFilter: Set<Attribute>?,
        location: Location,
        contentType: Type,
        includeFreeAttrs: Boolean,
        filter: String?
    ): Long {
        val filteredContent = if (includeFreeAttrs) LongArray(0)
        else selectFilteredContent(
            groupId,
            dynamicGroupContentIds,
            attributeFilter,
            location,
            contentType
        )
        return queryAvailableAttributesQ(
            type,
            filter,
            filteredContent,
            includeFreeAttrs
        ).safeCount()
    }

    // In our case, limit() argument has to be human-readable -> no issue concerning its type staying in the int range
    fun selectAvailableAttributes(
        type: AttributeType,
        groupId: Long,
        dynamicGroupContentIds: LongArray,
        attributeFilter: Set<Attribute>?,
        location: Location,
        contentType: Type,
        includeFreeAttrs: Boolean,
        filter: String?,
        sortOrder: Int,
        page: Int,
        itemsPerPage: Int,
        searchAttrCount: Boolean
    ): List<Attribute> {
        val filteredContent = if (includeFreeAttrs) LongArray(0) else selectFilteredContent(
            groupId,
            dynamicGroupContentIds,
            attributeFilter,
            location,
            contentType
        )
        if (filteredContent.isEmpty() && !attributeFilter.isNullOrEmpty() && !includeFreeAttrs) return emptyList()
        val result =
            queryAvailableAttributesQ(
                type,
                filter,
                filteredContent,
                includeFreeAttrs
            ).safeFind()

        // Compute attribute count for sorting
        if (searchAttrCount) {
            result.forEach { a ->
                // Only count the relevant Contents
                a.count = countAttributeContents(a.id, libraryStatus, filteredContent).toInt()
            }
        }

        // Apply sort order
        var s = result.asSequence()
        s = if (Settings.Value.SEARCH_ORDER_ATTRIBUTES_ALPHABETIC == sortOrder) {
            s.sortedBy { -it.count }.sortedBy { it.name }
        } else {
            s.sortedBy { it.name }.sortedBy { -it.count }
        }
        // Apply paging on sorted items
        if (itemsPerPage > 0) {
            val start = (page - 1) * itemsPerPage
            s = s.take(page * itemsPerPage).drop(start)
        }
        return s.toList()
    }

    private fun countAttributeContents(
        attrId: Long,
        status: IntArray,
        filteredContentIds: LongArray
    ): Long {
        var qc: QueryCondition<Content> = Content_.status.oneOf(status)
        if (filteredContentIds.isNotEmpty()) qc = qc.and(Content_.id.oneOf(filteredContentIds))
        val contentFromAttributesQueryBuilder = store.boxFor(Content::class.java).query(qc)
        contentFromAttributesQueryBuilder.link(Content_.attributes)
            .equal(Attribute_.dbId, attrId)
        return contentFromAttributesQueryBuilder.build().count()
    }

    fun countAvailableAttributesPerType(): SparseIntArray {
        return countAvailableAttributesPerType(
            -1,
            LongArray(0),
            null,
            Location.ANY,
            Type.ANY
        )
    }

    fun countAvailableAttributesPerType(
        groupId: Long,
        dynamicGroupContentIds: LongArray,
        attributeFilter: Set<Attribute>?,
        location: Location,
        contentType: Type
    ): SparseIntArray {
        // Get Content filtered by current selection
        val filteredContent = selectFilteredContent(
            groupId,
            dynamicGroupContentIds,
            attributeFilter,
            location,
            contentType
        )
        // Get available attributes of the resulting content list
        val query = store.boxFor(Attribute::class.java).query()
        if (filteredContent.isNotEmpty()) query.link(Attribute_.contents)
            .`in`(Content_.id, filteredContent)
            .`in`(Content_.status, libraryStatus) else query.link(Attribute_.contents)
            .`in`(Content_.status, libraryStatus)
        val attributes = query.safeFind()
        val result = SparseIntArray()
        // SELECT field, COUNT(*) GROUP BY (field) is not implemented in ObjectBox v2.3.1
        // (see https://github.com/objectbox/objectbox-java/issues/422)
        // => Group by and count have to be done manually (thanks God Stream exists !)
        // Group and count by type
        val map = attributes.groupBy { a -> a.type }
        map.forEach {
            if (filteredContent.isEmpty() && attributeFilter != null) {
                result.append(it.key.code, 0)
            } else {
                result.append(it.key.code, it.value.size)
            }
        }
        return result
    }

    fun selectContentWithTitle(word: String, contentStatusCodes: IntArray): List<Content> {
        val query = store.boxFor(Content::class.java).query()
        query.contains(Content_.title, word, QueryBuilder.StringOrder.CASE_INSENSITIVE)
        query.`in`(Content_.status, contentStatusCodes)
        return query.safeFind()
    }

    private fun applyContentLocationFilter(
        qc: QueryCondition<Content>,
        location: Location
    ): QueryCondition<Content> {
        return when (location) {
            Location.PRIMARY -> qc.and(Content_.status.notEqual(StatusContent.EXTERNAL.code))
            Location.PRIMARY_1 -> {
                var root = Settings.getStorageUri(StorageLocation.PRIMARY_1)
                if (root.isEmpty()) root = "FAIL" // Auto-fails condition
                qc.and(
                    Content_.storageUri.startsWith(
                        root,
                        QueryBuilder.StringOrder.CASE_INSENSITIVE
                    )
                )
            }

            Location.PRIMARY_2 -> {
                var root = Settings.getStorageUri(StorageLocation.PRIMARY_2)
                if (root.isEmpty()) root = "FAIL" // Auto-fails condition
                qc.and(
                    Content_.storageUri.startsWith(
                        root,
                        QueryBuilder.StringOrder.CASE_INSENSITIVE
                    )
                )
            }

            Location.EXTERNAL -> qc.and(Content_.status.equal(StatusContent.EXTERNAL.code))
            else -> qc
        }
    }

    private fun applyContentTypeFilter(
        inQc: QueryCondition<Content>,
        contentType: Type
    ): QueryCondition<Content> {
        var qc = inQc
        return when (contentType) {
            Type.STREAMED -> qc.and(
                Content_.downloadMode.equal(DownloadMode.STREAM.value)
            )

            Type.ARCHIVE -> {
                qc = qc.and(Content_.status.equal(StatusContent.EXTERNAL.code))
                var combinedCondition: QueryCondition<Content>? = null
                for (ext in getSupportedExtensions()) {
                    combinedCondition =
                        if (null == combinedCondition) Content_.storageUri.endsWith(
                            ext,
                            QueryBuilder.StringOrder.CASE_INSENSITIVE
                        ) else combinedCondition.or(
                            Content_.storageUri.endsWith(
                                ext,
                                QueryBuilder.StringOrder.CASE_INSENSITIVE
                            )
                        )
                }
                if (combinedCondition != null) qc.and(combinedCondition) else qc
            }

            Type.PDF -> {
                qc = qc.and(Content_.status.equal(StatusContent.EXTERNAL.code))
                val pdfCondition = Content_.storageUri.endsWith(
                    "pdf",
                    QueryBuilder.StringOrder.CASE_INSENSITIVE
                )
                qc.and(pdfCondition)
            }

            Type.PLACEHOLDER -> qc.and(Content_.status.equal(StatusContent.PLACEHOLDER.code))
            Type.FOLDER -> {
                // TODO : Should also not be an archive, but that would require Content_.storageUri.doesNotEndWith (see ObjectBox issue #1129)
                qc = qc.and(Content_.downloadMode.equal(DownloadMode.DOWNLOAD.value))
                qc.and(Content_.status.notEqual(StatusContent.PLACEHOLDER.code))
            }

            Type.ANY -> qc
        }
    }

    fun updateImageFileStatusParamsMimeTypeUriSize(image: ImageFile) {
        val imgBox = store.boxFor(ImageFile::class.java)
        val img = imgBox[image.id]
        if (img != null) {
            img.status = image.status
            img.downloadParams = image.downloadParams
            img.mimeType = image.mimeType
            img.fileUri = image.fileUri
            img.size = image.size
            imgBox.put(img)
        }
    }

    fun updateImageContentStatus(
        contentId: Long,
        updateFrom: StatusContent?,
        updateTo: StatusContent
    ) {
        val query = store.boxFor(ImageFile::class.java).query()
        if (updateFrom != null) query.equal(ImageFile_.status, updateFrom.code.toLong())
        val imgs = query.equal(ImageFile_.contentId, contentId).safeFind()
        if (imgs.isEmpty()) return
        for (img in imgs) img.status = updateTo
        store.boxFor(ImageFile::class.java).put(imgs)
    }

    fun updateImageFileUrl(image: ImageFile) {
        val imgBox = store.boxFor(ImageFile::class.java)
        val img = imgBox[image.id]
        if (img != null) {
            img.url = image.url
            imgBox.put(img)
        }
    }

    // Returns a list of processed images grouped by status, with count and filesize (in bytes)
    fun countProcessedImagesById(contentId: Long): Map<StatusContent, Pair<Int, Long>> {
        val imgQuery = store.boxFor(ImageFile::class.java).query()
        imgQuery.equal(ImageFile_.contentId, contentId)
        val images = imgQuery.safeFind()
        val result: MutableMap<StatusContent, Pair<Int, Long>> =
            EnumMap(StatusContent::class.java)
        // SELECT field, COUNT(*) GROUP BY (field) is not implemented in ObjectBox v2.3.1
        // (see https://github.com/objectbox/objectbox-java/issues/422)
        // => Group by and count have to be done manually (thanks God Stream exists !)
        // Group and count by type
        val map = images.groupBy { i -> i.status }
        map.forEach {
            var sizeBytes: Long = 0
            val count: Int = it.value.size
            for (img in it.value) sizeBytes += img.size
            result[it.key] = Pair(count, sizeBytes)
        }
        return result
    }

    fun selectAllFavouritePagesQ(): Query<ImageFile> {
        return store.boxFor(ImageFile::class.java).query()
            .equal(ImageFile_.favourite, true)
            .build()
    }

    fun selectPrimaryMemoryUsagePerSource(rootPath: String?): Map<Site, Pair<Int, Long>> {
        return selectMemoryUsagePerSource(
            intArrayOf(
                StatusContent.DOWNLOADED.code,
                StatusContent.MIGRATED.code
            ), rootPath
        )
    }

    fun selectExternalMemoryUsagePerSource(): Map<Site, Pair<Int, Long>> {
        return selectMemoryUsagePerSource(intArrayOf(StatusContent.EXTERNAL.code), "")
    }

    private fun selectMemoryUsagePerSource(
        statusCodes: IntArray,
        rootPath: String?
    ): Map<Site, Pair<Int, Long>> {
        // Get all downloaded images regardless of the book's status
        val query = store.boxFor(
            Content::class.java
        ).query()
        query.`in`(Content_.status, statusCodes)
        if (!rootPath.isNullOrEmpty()) query.startsWith(
            Content_.storageUri,
            rootPath,
            QueryBuilder.StringOrder.CASE_INSENSITIVE
        )
        val books = query.safeFind()
        val result: MutableMap<Site, Pair<Int, Long>> = EnumMap(Site::class.java)

        // SELECT field, COUNT(*) GROUP BY (field) is not implemented in ObjectBox v2.3.1
        // (see https://github.com/objectbox/objectbox-java/issues/422)
        // => Group by and count have to be done manually (thanks God Stream exists !)
        // Group and count by type
        val map = books.groupBy { c -> c.site }
        map.forEach {
            var size: Long = 0
            val count: Int = it.value.size
            for (c in it.value) size += c.size
            result[it.key] = Pair(count, size)
        }
        return result
    }

    fun insertErrorRecord(record: ErrorRecord) {
        store.boxFor(ErrorRecord::class.java).put(record)
    }

    fun selectErrorRecordByContentId(contentId: Long): List<ErrorRecord> {
        return store.boxFor(ErrorRecord::class.java).query()
            .equal(ErrorRecord_.contentId, contentId)
            .safeFind()
    }

    fun deleteErrorRecords(contentId: Long) {
        val records = selectErrorRecordByContentId(contentId)
        store.boxFor(ErrorRecord::class.java).remove(records)
    }

    fun insertImageFile(img: ImageFile) {
        if (img.id > 0) store.boxFor(ImageFile::class.java).put(img)
    }

    private fun deleteImageFiles(contentId: Long) {
        store.boxFor(ImageFile::class.java).query()
            .equal(ImageFile_.contentId, contentId)
            .safeRemove()
    }

    fun deleteImageFiles(images: List<ImageFile>?) {
        store.boxFor(ImageFile::class.java).remove(images)
    }

    fun insertImageFiles(imgs: List<ImageFile>) {
        store.boxFor(ImageFile::class.java).put(imgs)
    }

    fun replaceImageFiles(contentId: Long, newList: List<ImageFile>) {
        store.runInTx {
            deleteImageFiles(contentId)
            newList.forEach { it.contentId = contentId }
            insertImageFiles(newList)
        }
    }

    fun selectImageFile(id: Long): ImageFile? {
        return if (id > 0) store.boxFor(ImageFile::class.java)[id] else null
    }

    fun selectImageFiles(id: LongArray): List<ImageFile> {
        return store.boxFor(ImageFile::class.java)[id]
    }

    fun selectDownloadedImagesFromContentQ(id: Long): Query<ImageFile> {
        val builder = store.boxFor(ImageFile::class.java).query()
        builder.equal(ImageFile_.contentId, id)
        builder.`in`(
            ImageFile_.status,
            intArrayOf(
                StatusContent.DOWNLOADED.code,
                StatusContent.EXTERNAL.code,
                StatusContent.ONLINE.code,
                StatusContent.PLACEHOLDER.code
            )
        )
        builder.order(ImageFile_.dbOrder)
        return builder.build()
    }

    fun insertSiteHistory(site: Site, url: String) {
        val siteHistory = selectHistory(site)
        if (siteHistory != null) {
            siteHistory.url = url
            store.boxFor(SiteHistory::class.java).put(siteHistory)
        } else {
            store.boxFor(SiteHistory::class.java).put(SiteHistory(site = site, url = url))
        }
    }

    fun selectHistory(s: Site): SiteHistory? {
        return store.boxFor(SiteHistory::class.java).query()
            .equal(SiteHistory_.site, s.code.toLong())
            .safeFindFirst()
    }

    // BOOKMARKS

    // BOOKMARKS
    fun selectBookmarksQ(s: Site?): Query<SiteBookmark> {
        val qb = store.boxFor(SiteBookmark::class.java).query()
        if (s != null) qb.equal(SiteBookmark_.site, s.code.toLong())
        return qb.order(SiteBookmark_.order).build()
    }

    fun selectHomepage(s: Site): SiteBookmark? {
        val qb = store.boxFor(SiteBookmark::class.java).query()
        qb.equal(SiteBookmark_.site, s.code.toLong())
        qb.equal(SiteBookmark_.isHomepage, true)
        return qb.safeFindFirst()
    }

    private fun selectAllBooksmarkUrls(): Array<String> {
        store.boxFor(SiteBookmark::class.java).query().build().use { sbq ->
            return sbq.property(SiteBookmark_.url).findStrings()
        }
    }

    fun insertBookmark(bookmark: SiteBookmark): Long {
        return store.boxFor(SiteBookmark::class.java).put(bookmark)
    }

    fun insertBookmarks(bookmarks: List<SiteBookmark>) {
        store.boxFor(SiteBookmark::class.java).put(bookmarks)
    }

    fun deleteBookmark(bookmarkId: Long) {
        store.boxFor(SiteBookmark::class.java).remove(bookmarkId)
    }

    fun getMaxBookmarkOrderFor(site: Site): Int {
        store.boxFor(SiteBookmark::class.java).query()
            .equal(SiteBookmark_.site, site.code.toLong()).build().use { sbq ->
                return sbq.property(SiteBookmark_.order).max().toInt()
            }
    }

    // Select all duplicate bookmarks that end with a "/"
    fun selectAllDuplicateBookmarksQ(): Query<SiteBookmark> {
        val urls = selectAllBooksmarkUrls()
        for (i in urls.indices) urls[i] = urls[i] + "/"
        val query = store.boxFor(SiteBookmark::class.java).query()
        query.`in`(SiteBookmark_.url, urls, QueryBuilder.StringOrder.CASE_INSENSITIVE)
        return query.build()
    }

    // SEARCH RECORDS
    fun selectSearchRecordsQ(): Query<SearchRecord> {
        return store.boxFor(SearchRecord::class.java).query().build()
    }

    fun deleteSearchRecord(id: Long) {
        store.boxFor(SearchRecord::class.java).remove(id)
    }

    fun insertSearchRecords(records: List<SearchRecord>) {
        store.boxFor(SearchRecord::class.java).put(records)
    }

    // RENAMING RULES
    fun selectRenamingRule(id: Long): RenamingRule? {
        return store.boxFor(RenamingRule::class.java)[id]
    }

    fun selectRenamingRulesQ(type: AttributeType, nameFilter: String): Query<RenamingRule> {
        var qc: QueryCondition<RenamingRule>? = null
        var nameQc: QueryCondition<RenamingRule>? = null
        if (nameFilter.isNotEmpty()) nameQc =
            RenamingRule_.sourceName.contains(
                nameFilter,
                QueryBuilder.StringOrder.CASE_INSENSITIVE
            )
                .or(
                    RenamingRule_.sourceName.contains(
                        nameFilter,
                        QueryBuilder.StringOrder.CASE_INSENSITIVE
                    )
                )
        if (type != AttributeType.UNDEFINED) {
            qc = RenamingRule_.attributeType.equal(type.code)
            if (nameQc != null) qc = qc.and(nameQc)
        }
        if (null == qc) qc = nameQc
        val qb = if (null == qc) store.boxFor(RenamingRule::class.java).query()
        else store.boxFor(RenamingRule::class.java).query(qc)
        val sortField =
            if (Settings.Value.ORDER_FIELD_SOURCE_NAME == Settings.ruleSortField) RenamingRule_.sourceName else RenamingRule_.targetName
        if (Settings.isRuleSortDesc) {
            qb.orderDesc(sortField)
        } else {
            qb.order(sortField)
        }
        return qb.build()
    }

    fun insertRenamingRule(rule: RenamingRule): Long {
        return store.boxFor(RenamingRule::class.java).put(rule)
    }

    fun insertRenamingRules(rules: List<RenamingRule>) {
        store.boxFor(RenamingRule::class.java).put(rules)
    }

    fun deleteRenamingRules(ids: LongArray) {
        store.boxFor(RenamingRule::class.java).remove(*ids)
    }


    // GROUPS
    fun insertGroup(group: Group): Long {
        return store.boxFor(Group::class.java).put(group)
    }

    fun insertGroupItem(item: GroupItem): Long {
        return store.boxFor(GroupItem::class.java).put(item)
    }

    fun selectGroupItems(groupItemIds: LongArray): List<GroupItem> {
        return store.boxFor(GroupItem::class.java)[groupItemIds]
    }

    fun selectGroupItems(contentId: Long, groupingId: Int): List<GroupItem> {
        val qb = store.boxFor(GroupItem::class.java)
            .query().equal(GroupItem_.contentId, contentId)
        qb.link(GroupItem_.group).equal(Group_.grouping, groupingId.toLong())
        return qb.safeFind()
    }

    fun deleteGroupItems(groupItemIds: LongArray) {
        store.boxFor(GroupItem::class.java).remove(*groupItemIds)
    }

    fun countGroupsFor(grouping: Grouping): Long {
        return store.boxFor(Group::class.java).query()
            .equal(Group_.grouping, grouping.id.toLong())
            .safeCount()
    }

    fun getMaxGroupOrderFor(grouping: Grouping): Int {
        store.boxFor(Group::class.java).query().equal(Group_.grouping, grouping.id.toLong())
            .build().use { gq ->
                return gq.property(Group_.order).max().toInt()
            }
    }

    fun getMaxGroupItemOrderFor(groupid: Long): Int {
        store.boxFor(GroupItem::class.java).query().equal(GroupItem_.groupId, groupid).build()
            .use { giq ->
                return giq.property(GroupItem_.order).max().toInt()
            }
    }

    fun selectGroupsQ(
        grouping: Int,
        query: String?,
        orderField: Int,
        orderDesc: Boolean,
        subType: Int,
        groupFavouritesOnly: Boolean,
        groupNonFavouritesOnly: Boolean,
        filterRating: Int
    ): Query<Group> {
        val qb = store.boxFor(Group::class.java)
            .query().equal(Group_.grouping, grouping.toLong())
        if (query != null) qb.contains(
            Group_.name,
            query,
            QueryBuilder.StringOrder.CASE_INSENSITIVE
        )

        // Subtype filtering for artists groups
        if (subType > -1) {
            if (grouping == Grouping.ARTIST.id && subType != Preferences.Constant.ARTIST_GROUP_VISIBILITY_ARTISTS_GROUPS) {
                qb.equal(Group_.subtype, subType.toLong())
            }
            // Subtype filtering for custom groups
            if (grouping == Grouping.CUSTOM.id) {
                qb.equal(Group_.subtype, subType.toLong())
            }
        }
        if (groupFavouritesOnly) qb.equal(
            Group_.favourite,
            true
        ) else if (groupNonFavouritesOnly) qb.equal(Group_.favourite, false)
        if (filterRating > -1) qb.equal(Group_.rating, filterRating.toLong())
        var property = Group_.name
        if (Settings.Value.ORDER_FIELD_CUSTOM == orderField || grouping == Grouping.DL_DATE.id) property =
            Group_.order
        // Order by number of children / download date is done by the DAO
        if (orderDesc) qb.orderDesc(property) else qb.order(property)
        return qb.build()
    }

    fun selectEditedGroups(grouping: Int): List<Group> {
        val qcFavs: QueryCondition<Group> = Group_.favourite.equal(true)
        val qcRating: QueryCondition<Group> = Group_.rating.greater(0)
        val qc = Group_.grouping.equal(grouping).and(qcFavs.or(qcRating))
        return store.boxFor(Group::class.java).query(qc).safeFind()
    }

    fun selectGroup(groupId: Long): Group? {
        return store.boxFor(Group::class.java)[groupId]
    }

    fun selectGroups(groupIds: LongArray): List<Group>? {
        return store.boxFor(Group::class.java)[groupIds]
    }

    fun selectGroupByName(grouping: Int, name: String): Group? {
        return store.boxFor(Group::class.java).query().equal(Group_.grouping, grouping.toLong())
            .equal(Group_.name, name, QueryBuilder.StringOrder.CASE_INSENSITIVE)
            .safeFindFirst()
    }

    fun deleteGroup(groupId: Long) {
        store.boxFor(Group::class.java).remove(groupId)
    }

    fun deleteEmptyArtistGroups() {
        return store.boxFor(Group::class.java).query()
            .equal(Group_.grouping, 1)
            .relationCount(Group_.items, 0).safeRemove()
    }

    fun selectGroupsByGroupingQ(groupingId: Int): Query<Group> {
        return store.boxFor(Group::class.java).query()
            .equal(Group_.grouping, groupingId.toLong())
            .build()
    }

    fun selectFlaggedGroupsQ(): Query<Group> {
        return store.boxFor(Group::class.java).query()
            .equal(Group_.isFlaggedForDeletion, true)
            .build()
    }

    fun flagGroupsForDeletion(groupList: List<Group>) {
        for (g in groupList) g.isFlaggedForDeletion = true
        store.boxFor(Group::class.java).put(groupList)
    }

    fun deleteGroupItemsByGrouping(groupingId: Int) {
        val qb = store.boxFor(GroupItem::class.java).query()
        qb.link(GroupItem_.group).equal(Group_.grouping, groupingId.toLong())
        qb.safeRemove()
    }

    fun deleteGroupItemsByGroup(groupId: Long) {
        val qb = store.boxFor(GroupItem::class.java).query()
        qb.link(GroupItem_.group).equal(Group_.id, groupId)
        qb.safeRemove()
    }

    fun selectChapters(contentId: Long): List<Chapter> {
        return store.boxFor(Chapter::class.java).query().equal(Chapter_.contentId, contentId)
            .order(Chapter_.order).safeFind()
    }

    fun selectChapters(chapterIds: List<Long>): List<Chapter> {
        return store.boxFor(Chapter::class.java).get(chapterIds.toLongArray())
    }

    fun selectChapter(chapterId: Long): Chapter? {
        return store.boxFor(Chapter::class.java).get(chapterId)
    }

    fun insertChapters(chapters: List<Chapter>?) {
        store.boxFor(Chapter::class.java).put(chapters)
    }

    fun deleteChaptersByContentId(contentId: Long) {
        val qb = store.boxFor(Chapter::class.java).query()
        qb.equal(Chapter_.contentId, contentId)
        qb.safeRemove()
    }

    fun deleteChapter(chapterId: Long) {
        store.boxFor(Chapter::class.java).remove(chapterId)
    }

    fun selectStoredContentQ(
        includeQueued: Boolean,
        orderField: Int,
        orderDesc: Boolean
    ): QueryBuilder<Content> {
        val query = store.boxFor(Content::class.java).query()
        if (includeQueued) query.`in`(
            Content_.status,
            libraryQueueStatus
        ) else query.`in`(Content_.status, libraryStatus)
        if (orderField > -1) {
            val field = getPropertyFromField(orderField)
            if (null != field) {
                if (orderDesc) query.orderDesc(field) else query.order(field)
            }
        }
        return query
    }

    fun selectStoredContentFavIds(bookFavs: Boolean, groupFavs: Boolean): Set<Long> {
        return if (bookFavs && groupFavs) {
            val qcBIds = selectStoredContentFavBookBQ().safeFindIds().toMutableSet()
            val qcGIds: Set<Long> = selectStoredContentFavBookGQ().safeFindIds().toSet()
            qcBIds.addAll(qcGIds)
            qcBIds
        } else if (bookFavs) {
            selectStoredContentFavBookBQ().safeFindIds().toSet()
        } else if (groupFavs) {
            selectStoredContentFavBookGQ().safeFindIds().toSet()
        } else {
            HashSet()
        }
    }

    private fun selectStoredContentFavBookBQ(): Query<Content> {
        val qc: QueryCondition<Content> = Content_.status.oneOf(libraryStatus)
        val qcB: QueryCondition<Content> = Content_.favourite.equal(true)
        return store.boxFor(Content::class.java).query(qc.and(qcB)).build()
    }

    private fun selectStoredContentFavBookGQ(): Query<Content> {
        // Triple-linking =D
        val contentQb = store.boxFor(Content::class.java).query()
        val groupItemSubQb = contentQb.link(Content_.groupItems)
        groupItemSubQb.link(GroupItem_.group).equal(Group_.favourite, true)
        return contentQb.build()
    }

    fun selectNonHashedContentQ(): Query<Content> {
        val query = store.boxFor(Content::class.java).query().`in`(
            Content_.status,
            intArrayOf(
                StatusContent.DOWNLOADED.code,
                StatusContent.MIGRATED.code,
                StatusContent.EXTERNAL.code
            )
        ).notNull(Content_.storageUri)
            .notEqual(Content_.storageUri, "", QueryBuilder.StringOrder.CASE_INSENSITIVE)
        val imageQuery = query.backlink(ImageFile_.content)
        imageQuery.equal(ImageFile_.dbIsCover, true).isNull(ImageFile_.imageHash).or()
            .`in`(ImageFile_.imageHash, longArrayOf(0, -1))
            .notEqual(ImageFile_.status, StatusContent.ONLINE.code.toLong())
        return query.build()
    }

    private fun selectCustomGroupedContent(): LongArray {
        val customContentQB = store.boxFor(Content::class.java).query()
        customContentQB.link(Content_.groupItems).link(GroupItem_.group)
            .equal(Group_.grouping, Grouping.CUSTOM.id.toLong()) // Custom group
            .equal(Group_.subtype, 0) // Not the Ungrouped group (subtype 1)
        return customContentQB.safeFindIds()
    }

    fun selectUngroupedContentIds(): Set<Long> {
        // Select all eligible content
        val allContentQ =
            store.boxFor(Content::class.java).query().`in`(Content_.status, libraryStatus)
        val allContent = allContentQ.safeFindIds().toMutableSet()
        // Strip all content that have a Custom grouping
        allContent.removeAll(selectCustomGroupedContent().toSet())
        return allContent
    }

    fun selectContentIdsByGroup(groupId: Long): LongArray {
        val customContentQB = store.boxFor(Content::class.java)
            .query().`in`(Content_.status, libraryStatus)
        customContentQB.link(Content_.groupItems).link(GroupItem_.group)
            .equal(Group_.id, groupId)
        return customContentQB.safeFindIds()
    }

    fun selectContentIdsWithUpdatableJson(): LongArray {
        val contentCondition =
            Content_.jsonUri.endsWith(".json").and(Content_.downloadCompletionDate.greater(0))
        val allContentQ = store.boxFor(Content::class.java)
            .query(contentCondition).filter { c: Content ->
                abs((c.downloadCompletionDate - c.downloadDate).toDouble()) > 10
            }
        return allContentQ.safeFindIds()
    }
}