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
import me.devsaki.hentoid.database.domains.SiteHistory
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.InnerNameNumberBookmarkComparator
import me.devsaki.hentoid.util.updateBookmarksJson
import java.time.Instant

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
    private val siteHistory = MutableLiveData<List<SiteHistory>>()

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
        pageUrl.value = data
    }

    fun bookmarks(): LiveData<List<SiteBookmark>> {
        return bookmarks
    }

    fun bookmarkedSites(): LiveData<List<Site>> {
        return bookmarkedSites
    }

    fun siteHistory(): LiveData<List<SiteHistory>> {
        return siteHistory
    }


    private fun getProperSite(): Site {
        return bookmarksSite.value ?: browserSite.value ?: Site.NONE
    }

    fun loadBookmarks(sortAsc: Boolean? = null) {
        val theSite = getProperSite()
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
    }

    fun addBookmark(title: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                browserSite.value?.let { s ->
                    dao.insertBookmark(
                        SiteBookmark(
                            site = s,
                            title = title,
                            url = pageUrl.value ?: ""
                        )
                    )
                    loadBookmarks()
                    dao.cleanup()
                }
            }
        }
    }

    fun updateBookmark(b: SiteBookmark) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.insertBookmark(b)
                loadBookmarks()
                dao.cleanup()
            }
        }
    }

    fun moveBookmark(oldPosition: Int, newPosition: Int) {
        val bookmarks = bookmarks.value?.toMutableList() ?: return
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
        dao.cleanup()
    }

    fun setBookmarkAsHome(id: Long) {
        val theSite = getProperSite()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val bookmarks = dao.selectBookmarks(theSite)
                for (b in bookmarks) {
                    if (b.id == id) b.isHomepage = !b.isHomepage
                    else b.isHomepage = false
                }
                dao.insertBookmarks(bookmarks)
                loadBookmarks()
                dao.cleanup()
            }
        }
    }

    fun deleteBookmark(id: Long) {
        deleteBookmarks(listOf(id))
    }

    fun deleteBookmarks(ids: List<Long>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.deleteBookmarks(ids)
                loadBookmarks()
                dao.cleanup()
            }
        }
    }

    fun updateBookmarksJson() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                updateBookmarksJson(HentoidApp.getInstance(), dao)
                dao.cleanup()
            }
        }
    }

    fun saveCurrentUrl(site: Site, url: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.insertSiteHistory(site, url, Instant.now().toEpochMilli())
                dao.cleanup()
            }
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                siteHistory.postValue(dao.selectHistory())
                dao.cleanup()
            }
        }
    }
}