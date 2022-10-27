package me.devsaki.hentoid.widget

import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.paging.PagedList
import io.reactivex.Single
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle.Companion.parseSearchUri
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.util.*
import java.util.*

class ContentSearchManager(val dao: CollectionDAO) {

    private val values = ContentSearchBundle()


    fun toBundle(): Bundle {
        val result = Bundle()
        saveToBundle(result)
        return result
    }

    fun saveToBundle(b: Bundle) {
        b.putAll(values.bundle)
    }

    fun loadFromBundle(b: Bundle) {
        values.bundle.putAll(b)
    }


    fun setFilterBookFavourites(value: Boolean) {
        values.filterBookFavourites = value
    }

    fun setFilterBookCompleted(value: Boolean) {
        values.filterBookCompleted = value
    }

    fun isFilterBookCompleted(): Boolean {
        return values.filterBookCompleted
    }

    fun setFilterBookNotCompleted(value: Boolean) {
        values.filterBookNotCompleted = value
    }

    fun isFilterBookNotCompleted(): Boolean {
        return values.filterBookNotCompleted
    }

    fun setFilterRating(value: Int) {
        values.filterRating = value
    }

    fun setFilterPageFavourites(value: Boolean) {
        values.filterPageFavourites = value
    }

    fun setLoadAll(value: Boolean) {
        values.loadAll = value
    }

    fun setQuery(value: String) {
        values.query = value
    }

    fun setContentSortField(value: Int) {
        values.sortField = value
    }

    fun setContentSortDesc(value: Boolean) {
        values.sortDesc = value
    }

    fun setLocation(value: Int) {
        values.location = value
    }

    fun setContentType(value: Int) {
        values.contentType = value
    }

    fun setGroup(value: Group?) {
        if (value != null) values.groupId = value.id else values.groupId = -1
    }

    fun setTags(tags: List<Attribute>?) {
        if (tags != null) {
            values.attributes = SearchActivityBundle.buildSearchUri(tags).toString()
        } else clearSelectedSearchTags()
    }

    fun clearSelectedSearchTags() {
        values.attributes = SearchActivityBundle.buildSearchUri(Collections.emptyList()).toString()
    }

    fun clearFilters() {
        clearSelectedSearchTags()
        setQuery("")
        setFilterBookFavourites(false)
        setFilterBookCompleted(false)
        setFilterBookNotCompleted(false)
        setFilterPageFavourites(false)
        setFilterRating(-1)
        setLocation(0)
        setContentType(0)
    }

    fun getLibrary(): LiveData<PagedList<Content>> {
        val tags = parseSearchUri(Uri.parse(values.attributes)).attributes
        return when {
            // Universal search
            values.query.isNotEmpty() -> dao.searchBooksUniversal(
                values
            )
            // Advanced search
            tags.isNotEmpty() || values.location > 0 || values.contentType > 0 -> dao.searchBooks(
                values,
                tags
            )
            // Default search (display recent)
            else -> dao.selectRecentBooks(
                values
            )
        }
    }

    fun searchLibraryForId(): Single<List<Long>> {
        val tags = parseSearchUri(Uri.parse(values.attributes)).attributes
        return when {
            // Universal search
            values.query.isNotEmpty() -> dao.searchBookIdsUniversal(
                values
            )
            // Advanced search
            tags.isNotEmpty() || values.location > 0 || values.contentType > 0 -> dao.searchBookIds(
                values,
                tags
            )
            // Default search (display recent)
            else -> dao.selectRecentBookIds(
                values
            )
        }
    }


    // INNER CLASS

    class ContentSearchBundle(val bundle: Bundle = Bundle()) {

        var loadAll by bundle.boolean(default = false)

        var filterPageFavourites by bundle.boolean(default = false)

        var filterBookFavourites by bundle.boolean(default = false)

        var filterBookCompleted by bundle.boolean(default = false)

        var filterBookNotCompleted by bundle.boolean(default = false)

        var filterRating by bundle.int(default = -1)

        var query by bundle.string(default = "")

        var sortField by bundle.int(default = Preferences.getContentSortField())

        var sortDesc by bundle.boolean(default = Preferences.isContentSortDesc())

        var attributes by bundle.string(default = "") // Stored using a search URI for convenience

        var location by bundle.int(default = 0)

        var contentType by bundle.int(default = 0)

        var groupId by bundle.long(default = -1)


        fun isFilterActive(): Boolean {
            val tags = parseSearchUri(Uri.parse(attributes)).attributes
            return query.isNotEmpty()
                    || tags.isNotEmpty()
                    || location > 0
                    || contentType > 0
                    || filterBookFavourites
                    || filterBookCompleted
                    || filterBookNotCompleted
                    || filterRating > -1
                    || filterPageFavourites
        }
    }
}