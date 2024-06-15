package me.devsaki.hentoid.json

import com.squareup.moshi.JsonClass
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.database.domains.RenamingRule
import me.devsaki.hentoid.database.domains.SiteBookmark
import me.devsaki.hentoid.enums.Grouping

@JsonClass(generateAdapter = true)
class JsonContentCollection {
    var library: MutableList<JsonContent> = ArrayList()
    var queue: List<JsonContent> = ArrayList()
    var groupings: MutableList<JsonCustomGrouping> = ArrayList()
    var bookmarks: List<JsonBookmark> = ArrayList()
    var renamingRules: List<JsonRenamingRule> = ArrayList()


    fun addToLibrary(content: Content) {
        library.add(JsonContent(content, false))
    }

    fun getEntityQueue(): List<Content> {
        return queue.map { it.toEntity() }
    }

    fun replaceQueue(queue: List<Content>) {
        this.queue = queue.map { JsonContent(it, false) }
    }

    fun getEntityGroups(grouping: Grouping): List<Group> {
        return groupings
            .filter { it.groupingId == grouping.id }
            .flatMap { it.groups }
            .map { it.toEntity(grouping) }
    }

    fun replaceGroups(grouping: Grouping, groups: List<Group>) {
        // Clear previous entries of the same grouping
        groupings = groupings.filterNot { it.groupingId == grouping.id }.toMutableList()
        if (groups.isNotEmpty()) groupings.add(JsonCustomGrouping(grouping, groups))
    }

    fun getEntityBookmarks(): List<SiteBookmark> {
        return bookmarks.map { it.toEntity() }
    }

    fun replaceBookmarks(bookmarks: List<SiteBookmark>) {
        this.bookmarks = bookmarks.map { JsonBookmark(it) }
    }

    fun getEntityRenamingRules(): List<RenamingRule> {
        return renamingRules.map { it.toEntity() }
    }

    fun replaceRenamingRules(data: List<RenamingRule>) {
        this.renamingRules = data.map { JsonRenamingRule(it) }
    }

    fun isEmpty(): Boolean {
        return library.isEmpty() && queue.isEmpty() && groupings.isEmpty() && bookmarks.isEmpty() && renamingRules.isEmpty()
    }
}