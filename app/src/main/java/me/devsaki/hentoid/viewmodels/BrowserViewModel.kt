package me.devsaki.hentoid.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.domains.SiteBookmark
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.InnerNameNumberBookmarkComparator
import me.devsaki.hentoid.util.updateBookmarksJson

class BrowserViewModel(
    application: Application,
    private val dao: CollectionDAO
) : AndroidViewModel(application) {

    private val browserSite = MutableLiveData<Site>()
    private val bookmarksSite = MutableLiveData<Site>()
    private val pageTitle = MutableLiveData<String>()
    private val pageUrl = MutableLiveData<String>()
    private val bookmarks = MutableLiveData<List<SiteBookmark>>()
    private val bookmarkedSites = MutableLiveData<List<Site>>()

    override fun onCleared() {
        super.onCleared()
        dao.cleanup()
    }

    fun getBrowserSite(): LiveData<Site> {
        return browserSite
    }

    fun setBrowserSite(data: Site) {
        browserSite.value = data
    }

    fun getBookmarksSite(): LiveData<Site> {
        return bookmarksSite
    }

    fun setBookmarksSite(data: Site) {
        bookmarksSite.postValue(data)
    }

    fun pageTitle(): LiveData<String> {
        return pageTitle
    }

    fun setPageTitle(data: String) {
        pageTitle.postValue(data)
    }

    fun pageUrl(): LiveData<String> {
        return pageUrl
    }

    fun setPageUrl(data: String) {
        pageUrl.postValue(data)
    }

    fun bookmarks(): LiveData<List<SiteBookmark>> {
        return bookmarks
    }

    fun bookmarkedSites(): LiveData<List<Site>> {
        return bookmarkedSites
    }


    init {
        reloadBookmarks()
    }


    private fun getProperSite(): Site {
        return bookmarksSite.value ?: browserSite.value ?: Site.NONE
    }

    fun reloadBookmarks(sortAsc: Boolean? = null) {
        val theSite = getProperSite()
        try {
            // Fetch custom bookmarks
            var bookmarks = dao.selectBookmarks(theSite)

            // Apply sort if needed
            if (sortAsc != null) {
                bookmarks = bookmarks.sortedWith(InnerNameNumberBookmarkComparator())
                if (!sortAsc) bookmarks = bookmarks.reversed()

                // Renumber and save new order
                bookmarks.forEachIndexed { i, b -> b.order = i }
                dao.insertBookmarks(bookmarks)
            }
            this.bookmarks.postValue(bookmarks)

            val bookmarkedSites = dao.selectAllBookmarks().groupBy { it.site }.keys
            this.bookmarkedSites.postValue(Site.entries.filter { bookmarkedSites.contains(it) && it.isVisible })
        } finally {
            dao.cleanup()
        }
    }

    fun addBookmark(title: String) {
        browserSite.value?.let { s ->
            try {
                dao.insertBookmark(
                    SiteBookmark(
                        site = s,
                        title = title,
                        url = pageUrl.value ?: ""
                    )
                )
            } finally {
                dao.cleanup()
            }
            reloadBookmarks()
        }
    }

    fun updateBookmark(b: SiteBookmark) {
        try {
            dao.insertBookmark(b)
        } finally {
            dao.cleanup()
        }
        reloadBookmarks()
    }

    fun moveBookmark(oldPosition: Int, newPosition: Int) {
        val bookmarks = bookmarks.value?.toMutableList() ?: return
        try {
            if (oldPosition < 0 || oldPosition >= bookmarks.size) return

            // Add a bogus item on Position 0 to simulate the "Homepage" UI item
            bookmarks.add(0, SiteBookmark(site = Site.NONE))

            // Move the item
            val fromValue = bookmarks[oldPosition]
            val delta = if (oldPosition < newPosition) 1 else -1
            var i = oldPosition
            while (i != newPosition) {
                bookmarks[i] = bookmarks[i + delta]
                i += delta
            }
            bookmarks[newPosition] = fromValue

            // Remove the bogus element before saving
            bookmarks.removeIf { b -> b.site == Site.NONE }

            // Renumber everything
            bookmarks.forEachIndexed { idx, b -> b.order = idx + 1 }

            // Update DB
            dao.insertBookmarks(bookmarks)
        } finally {
            dao.cleanup()
        }
    }

    fun setBookmarkAsHome(id: Long) {
        val theSite = getProperSite()
        try {
            val bookmarks = dao.selectBookmarks(theSite)
            for (b in bookmarks) {
                if (b.id == id) b.isHomepage = !b.isHomepage
                else b.isHomepage = false
            }
            dao.insertBookmarks(bookmarks)
        } finally {
            dao.cleanup()
        }
        reloadBookmarks()
    }

    fun deleteBookmark(id: Long) {
        deleteBookmarks(listOf(id))
    }

    fun deleteBookmarks(ids: List<Long>) {
        try {
            dao.deleteBookmarks(ids)
        } finally {
            dao.cleanup()
        }
        reloadBookmarks()
    }

    fun updateBookmarksJson() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    updateBookmarksJson(HentoidApp.getInstance(), dao)
                } finally {
                    dao.cleanup()
                }
            }
        }
    }
}