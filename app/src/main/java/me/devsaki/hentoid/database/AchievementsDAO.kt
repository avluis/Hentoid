package me.devsaki.hentoid.database

import android.content.Context
import io.objectbox.query.QueryBuilder
import io.objectbox.query.QueryCondition
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.Attribute_
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.Content_
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.database.domains.ImageFile_
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import kotlin.math.max

/**
 * DAO specialized in achievements management
 */
class AchievementsDAO(ctx: Context) {
    private val db: ObjectBoxDB = ObjectBoxDB.getInstance(ctx)


    fun cleanup() {
        db.cleanup()
    }

    fun selectEligibleContentIds(): Set<Long> {
        val query: QueryBuilder<Content> = db.selectStoredContentQ(false, -1, false)
        // Can't remove edited books based on getLastEditDate as this field is updated when reimporting and transforming books
        return query.safeFindIds().toSet()
    }

    fun selectUngroupedContentIds(): Set<Long> {
        return db.selectUngroupedContentIds()
    }

    fun selectTotalReadPages(): Long {
        val condition: QueryCondition<ImageFile> = ImageFile_.read.equal(true)
        return db.store.boxFor(ImageFile::class.java).query(condition).safeCount()
    }

    fun selectLargestArtist(eligibleContent: Set<Long>, max: Int): Long {
        // Select all ARTIST attributes
        val condition: QueryCondition<Attribute> = Attribute_.type.equal(AttributeType.ARTIST.code)
        val artists: List<Attribute> =
            db.store.boxFor(Attribute::class.java).query(condition).safeFind()

        var largest: Long = 0
        for (a in artists) {
            val linkedContents: MutableSet<Long> = a.contents.map { c -> c.id }.toHashSet()
            // Limit to stored books
            linkedContents.retainAll(eligibleContent)
            largest = max(largest, linkedContents.size.toLong())
            if (largest >= max) break
        }
        return largest
    }

    fun countWithTagsOr(eligibleContent: Set<Long>, vararg tagNames: String): Long {
        var condition: QueryCondition<Attribute> = Attribute_.name.equal(
            tagNames[0], QueryBuilder.StringOrder.CASE_INSENSITIVE
        )
        for (i in 1..<tagNames.size) condition = condition.or(
            Attribute_.name.equal(
                tagNames[i],
                QueryBuilder.StringOrder.CASE_INSENSITIVE
            )
        )
        val tags = db.store.boxFor(Attribute::class.java).query(condition).safeFind()
        val linkedContents = tags.flatMap { t -> t.contents }.map { c -> c.id }.toMutableSet()

        // Limit to stored books
        linkedContents.retainAll(eligibleContent)
        return linkedContents.size.toLong()
    }

    fun countWithTagsAnd(eligibleContent: Set<Long>, vararg tagNames: String): Long {
        // Populate with first set
        var condition =
            Attribute_.name.equal(tagNames[0], QueryBuilder.StringOrder.CASE_INSENSITIVE)
        var tags = db.store.boxFor(Attribute::class.java).query(condition).safeFind()
        val linkedContents = tags.flatMap { t -> t.contents }.map { c -> c.id }.toMutableSet()

        // Retain all subsequent sets
        for (i in 1..<tagNames.size) {
            condition =
                Attribute_.name.equal(tagNames[i], QueryBuilder.StringOrder.CASE_INSENSITIVE)
            tags = db.store.boxFor(Attribute::class.java).query(condition).safeFind()
            for (a in tags) linkedContents.retainAll(a.contents.map { c -> c.id }.toSet())
        }

        // Limit to stored books
        linkedContents.retainAll(eligibleContent)
        return linkedContents.size.toLong()
    }

    fun countWithSitesOr(eligibleContent: Set<Long>, sites: List<Site>): Long {
        var condition: QueryCondition<Content?> = Content_.site.equal(sites[0].code)
        for (i in 1..<sites.size) condition = condition.or(Content_.site.equal(sites[i].code))
        val contents = db.store.boxFor(Content::class.java).query(condition).safeFind()
        val linkedContents = contents.map { obj -> obj.id }.toHashSet()

        // Limit to stored books
        linkedContents.retainAll(eligibleContent)
        return linkedContents.size.toLong()
    }

    fun selectNewestRead(): Long {
        val c = db.store.boxFor(Content::class.java)
            .query().orderDesc(Content_.lastReadDate).safeFindFirst()
        return c?.lastReadDate ?: 0
    }

    fun selectNewestDownload(): Long {
        val c = db.store.boxFor(Content::class.java).query()
            .orderDesc(Content_.downloadDate).safeFindFirst()
        return c?.downloadDate ?: 0
    }

    fun countQueuedBooks(): Long {
        val query: QueryBuilder<Content> = db.store.boxFor(Content::class.java).query()
        query.`in`(Content_.status, ObjectBoxDB.queueStatus)
        return query.safeCount()
    }
}