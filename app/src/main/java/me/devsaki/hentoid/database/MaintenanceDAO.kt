package me.devsaki.hentoid.database

import io.objectbox.query.QueryBuilder
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Chapter_
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.Content_
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.database.domains.Group_
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.database.domains.QueueRecord
import me.devsaki.hentoid.database.domains.QueueRecord_
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.getQueueTabStatuses

/**
 * DAO specialized in one-shot queries (migration & maintenance)
 */
class MaintenanceDAO {
    fun cleanup() {
        ObjectBoxDB.cleanup()
    }

    fun selectContentWithOldPururinHost(): List<Content> {
        return ObjectBoxDB.store.boxFor(Content::class.java).query()
            .equal(Content_.site, Site.PURURIN.code.toLong()).contains(
                Content_.coverImageUrl,
                "://api.pururin.io/images/",
                QueryBuilder.StringOrder.CASE_INSENSITIVE
            ).safeFind()
    }

    fun selectContentWithOldTsuminoCovers(): List<Content> {
        return ObjectBoxDB.store.boxFor(Content::class.java).query()
            .equal(Content_.site, Site.TSUMINO.code.toLong()).contains(
                Content_.coverImageUrl,
                "://www.tsumino.com/Image/Thumb/",
                QueryBuilder.StringOrder.CASE_INSENSITIVE
            ).safeFind()
    }

    fun selectContentWithOldHitomiCovers(): List<Content> {
        return ObjectBoxDB.store.boxFor(Content::class.java).query()
            .equal(Content_.site, Site.HITOMI.code.toLong()).contains(
                Content_.coverImageUrl, "/smallbigtn/", QueryBuilder.StringOrder.CASE_INSENSITIVE
            ).safeFind()
    }

    fun selectDownloadedM18Books(): List<Content> {
        return ObjectBoxDB.store.boxFor(Content::class.java).query()
            .equal(Content_.site, Site.MANHWA18.code.toLong())
            .`in`(Content_.status, ObjectBoxDB.libraryStatus).safeFind()
    }

    fun selecChaptersEmptyName(): List<Chapter> {
        return ObjectBoxDB.store.boxFor(Chapter::class.java).query()
            .equal(Chapter_.name, "", QueryBuilder.StringOrder.CASE_INSENSITIVE).safeFind()
    }

    fun selectDownloadedContentWithNoSize(): List<Content> {
        return ObjectBoxDB.store.boxFor(Content::class.java).query()
            .`in`(Content_.status, ObjectBoxDB.libraryStatus).isNull(Content_.size).safeFind()
    }

    fun selectDownloadedContentWithNoReadProgress(): List<Content> {
        return ObjectBoxDB.store.boxFor(Content::class.java).query()
            .`in`(Content_.status, ObjectBoxDB.libraryStatus).isNull(Content_.readProgress)
            .safeFind()
    }

    fun selectGroupsWithNoCoverContent(): List<Group> {
        return ObjectBoxDB.store.boxFor(Group::class.java).query().isNull(Group_.coverContentId)
            .or()
            .equal(Group_.coverContentId, 0).safeFind()
    }

    fun selectContentWithNullCompleteField(): List<Content> {
        return ObjectBoxDB.store.boxFor(Content::class.java).query().isNull(Content_.completed)
            .safeFind()
    }

    fun selectContentWithNullDlModeField(): List<Content> {
        return ObjectBoxDB.store.boxFor(Content::class.java).query().isNull(Content_.downloadMode)
            .safeFind()
    }

    fun selectContentWithNullMergeField(): List<Content> {
        return ObjectBoxDB.store.boxFor(Content::class.java).query().isNull(Content_.manuallyMerged)
            .safeFind()
    }

    fun selectContentWithNullDlCompletionDateField(): List<Content> {
        return ObjectBoxDB.store.boxFor(Content::class.java).query()
            .isNull(Content_.downloadCompletionDate)
            .safeFind()
    }

    fun selectContentWithInvalidUploadDate(): List<Content> {
        return ObjectBoxDB.store.boxFor(Content::class.java).query().greater(Content_.uploadDate, 0)
            .less(Content_.uploadDate, 10000000000L).safeFind()
    }

    fun selectChapterWithNullUploadDate(): List<Chapter> {
        return ObjectBoxDB.store.boxFor(Chapter::class.java).query().isNull(Chapter_.uploadDate)
            .safeFind()
    }

    fun selectOrphanQueueRecordIds(): LongArray {
        val qrCondition = QueueRecord_.contentId.lessOrEqual(0).or(QueueRecord_.contentId.isNull)
        return ObjectBoxDB.store.boxFor(QueueRecord::class.java).query(qrCondition).safeFindIds()
    }

    // Select content that have a queue status but no corresponding QueueRecord
    fun selectOrphanQueueContent(): List<Content> {
        val qrCondition = Content_.status.oneOf(getQueueTabStatuses()).and(Content_.queueRecords.relationCount(0))
        return ObjectBoxDB.store.boxFor(Content::class.java).query(qrCondition).safeFind()
    }

    // Proxies to the update functions of the regular DB

    fun insertContentCore(c: Content) {
        ObjectBoxDB.insertContentCore(c)
    }

    fun insertGroup(g: Group) {
        ObjectBoxDB.insertGroup(g)
    }

    fun updateImageFileUrl(img: ImageFile) {
        ObjectBoxDB.updateImageFileUrl(img)
    }

    fun insertImageFiles(imgs: List<ImageFile>) {
        ObjectBoxDB.insertImageFiles(imgs)
    }

    fun insertChapters(chps: List<Chapter>) {
        ObjectBoxDB.insertChapters(chps)
    }

    fun deleteQueueRecords(ids: LongArray) {
        ObjectBoxDB.deleteQueueRecords(ids)
    }

    fun deleteOrphanArtistGroups() {
        ObjectBoxDB.deleteEmptyArtistGroups()
    }
}