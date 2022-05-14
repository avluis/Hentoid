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

    fun setFilterBookRating(value: Int) {
        values.filterBookRating = value
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

    fun setGroup(value: Group?) {
        if (value != null) values.groupId = value.id else values.groupId = -1
    }

    fun setTags(tags: List<Attribute>?) {
        if (tags != null) {
            values.searchUri = SearchActivityBundle.buildSearchUri(tags).toString()
        } else clearSelectedSearchTags()
    }

    fun clearSelectedSearchTags() {
        values.searchUri = SearchActivityBundle.buildSearchUri(Collections.emptyList()).toString()
    }

    fun clearFilters() {
        clearSelectedSearchTags()
        setQuery("")
        setFilterBookFavourites(false)
        setFilterBookCompleted(false)
        setFilterBookNotCompleted(false)
        setFilterPageFavourites(false)
        setFilterBookRating(0)
    }

    fun getLibrary(): LiveData<PagedList<Content>> {
        val tags = parseSearchUri(Uri.parse(values.searchUri))
        return when {
            values.query.isNotEmpty() -> dao.searchBooksUniversal(
                values
            ) // Universal search
            tags.isNotEmpty() -> dao.searchBooks(
                values,
                tags
            ) // Advanced search
            else -> dao.selectRecentBooks(
                values
            )
        } // Default search (display recent)
    }

    fun searchLibraryForId(): Single<List<Long>> {
        val tags = parseSearchUri(Uri.parse(values.searchUri))
        return when {
            values.query.isNotEmpty() -> dao.searchBookIdsUniversal(
                values
            ) // Universal search
            tags.isNotEmpty() -> dao.searchBookIds(
                values,
                tags
            ) // Advanced search
            else -> dao.selectRecentBookIds(
                values
            )
        } // Default search (display recent)
    }


    // INNER CLASS

    class ContentSearchBundle(val bundle: Bundle = Bundle()) {

        var loadAll by bundle.boolean(default = false)

        var filterPageFavourites by bundle.boolean(default = false)

        var filterBookFavourites by bundle.boolean(default = false)

        var filterBookCompleted by bundle.boolean(default = false)

        var filterBookNotCompleted by bundle.boolean(default = false)

        var filterBookRating by bundle.int(default = 0)

        var query by bundle.string(default = "")

        var sortField by bundle.int(default = Preferences.getContentSortField())

        var sortDesc by bundle.boolean(default = Preferences.isContentSortDesc())

        var searchUri by bundle.string(default = "")

        var groupId by bundle.long(default = -1)


        fun isFilterActive(): Boolean {
            val tags = parseSearchUri(Uri.parse(searchUri))
            return query.isNotEmpty()
                    || tags.isNotEmpty()
                    || filterBookFavourites
                    || filterBookCompleted
                    || filterBookNotCompleted
                    || filterBookRating > 0
                    || filterPageFavourites
        }
    }
}