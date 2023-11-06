package me.devsaki.hentoid.database

import android.content.Context
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

/**
 * DAO specialized in one-shot queries (migration & maintenance)
 */
class MaintenanceDAO(ctx: Context) {
    private val db: ObjectBoxDB = ObjectBoxDB.getInstance(ctx)


    fun cleanup() {
        db.cleanup()
    }

    fun selectContentWithOldPururinHost(): List<Content> {
        return db.store.boxFor(Content::class.java).query()
            .equal(Content_.site, Site.PURURIN.code.toLong()).contains(
                Content_.coverImageUrl,
                "://api.pururin.io/images/",
                QueryBuilder.StringOrder.CASE_INSENSITIVE
            ).safeFind()
    }

    fun selectContentWithOldTsuminoCovers(): List<Content> {
        return db.store.boxFor(Content::class.java).query()
            .equal(Content_.site, Site.TSUMINO.code.toLong()).contains(
                Content_.coverImageUrl,
                "://www.tsumino.com/Image/Thumb/",
                QueryBuilder.StringOrder.CASE_INSENSITIVE
            ).safeFind()
    }

    fun selectContentWithOldHitomiCovers(): List<Content> {
        return db.store.boxFor(Content::class.java).query()
            .equal(Content_.site, Site.HITOMI.code.toLong()).contains(
                Content_.coverImageUrl, "/smallbigtn/", QueryBuilder.StringOrder.CASE_INSENSITIVE
            ).safeFind()
    }

    fun selectDownloadedM18Books(): List<Content> {
        return db.store.boxFor(Content::class.java).query()
            .equal(Content_.site, Site.MANHWA18.code.toLong())
            .`in`(Content_.status, ObjectBoxDB.libraryStatus).safeFind()
    }

    fun selecChaptersEmptyName(): List<Chapter> {
        return db.store.boxFor(Chapter::class.java).query()
            .equal(Chapter_.name, "", QueryBuilder.StringOrder.CASE_INSENSITIVE).safeFind()
    }

    fun selectDownloadedContentWithNoSize(): List<Content> {
        return db.store.boxFor(Content::class.java).query()
            .`in`(Content_.status, ObjectBoxDB.libraryStatus).isNull(Content_.size).safeFind()
    }

    fun selectDownloadedContentWithNoReadProgress(): List<Content> {
        return db.store.boxFor(Content::class.java).query()
            .`in`(Content_.status, ObjectBoxDB.libraryStatus).isNull(Content_.readProgress)
            .safeFind()
    }

    fun selectGroupsWithNoCoverContent(): List<Group> {
        return db.store.boxFor(Group::class.java).query().isNull(Group_.coverContentId).or()
            .equal(Group_.coverContentId, 0).safeFind()
    }

    fun selectContentWithNullCompleteField(): List<Content> {
        return db.store.boxFor(Content::class.java).query().isNull(Content_.completed).safeFind()
    }

    fun selectContentWithNullDlModeField(): List<Content> {
        return db.store.boxFor(Content::class.java).query().isNull(Content_.downloadMode).safeFind()
    }

    fun selectContentWithNullMergeField(): List<Content> {
        return db.store.boxFor(Content::class.java).query().isNull(Content_.manuallyMerged)
            .safeFind()
    }

    fun selectContentWithNullDlCompletionDateField(): List<Content> {
        return db.store.boxFor(Content::class.java).query().isNull(Content_.downloadCompletionDate)
            .safeFind()
    }

    fun selectContentWithInvalidUploadDate(): List<Content> {
        return db.store.boxFor(Content::class.java).query().greater(Content_.uploadDate, 0)
            .less(Content_.uploadDate, 10000000000L).safeFind()
    }

    fun selectChapterWithNullUploadDate(): List<Chapter> {
        return db.store.boxFor(Chapter::class.java).query().isNull(Chapter_.uploadDate).safeFind()
    }

    fun selectOrphanQueueRecordIds(): LongArray {
        val qrCondition = QueueRecord_.contentId.lessOrEqual(0).or(QueueRecord_.contentId.isNull)
        return db.store.boxFor(QueueRecord::class.java).query(qrCondition).safeFindIds()
    }

    // Proxies to the update functions of the base DB

    fun insertContentCore(c: Content) {
        db.insertContentCore(c)
    }

    fun insertGroup(g: Group) {
        db.insertGroup(g)
    }

    fun updateImageFileUrl(img: ImageFile) {
        db.updateImageFileUrl(img)
    }

    fun insertImageFiles(imgs: List<ImageFile>) {
        db.insertImageFiles(imgs)
    }

    fun insertChapters(chps: List<Chapter>) {
        db.insertChapters(chps)
    }

    fun deleteQueueRecords(ids: LongArray) {
        db.deleteQueueRecords(ids)
    }
}