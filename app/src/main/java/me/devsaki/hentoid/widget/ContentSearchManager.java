package me.devsaki.hentoid.widget;

import android.net.Uri;
import android.os.Bundle;

import androidx.lifecycle.LiveData;
import androidx.paging.PagedList;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import io.reactivex.Single;
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.Preferences;

public class ContentSearchManager {

    // Save state constants
    private static final String KEY_SELECTED_TAGS = "selected_tags";
    private static final String KEY_FILTER_FAVOURITES = "filter_favs";
    private static final String KEY_QUERY = "query";
    private static final String KEY_SORT_ORDER = "sort_order";
    private static final String KEY_CURRENT_PAGE = "current_page";

    private final CollectionDAO collectionDAO;

    // Current page of collection view (NB : In EndlessFragment, a "page" is a group of loaded books. Last page is reached when scrolling reaches the very end of the book list)
    private int currentPage = 1;
    // Favourite filter active
    private boolean filterFavourites = false;
    // Full-text query
    private String query = "";
    // Current search tags
    private List<Attribute> tags = new ArrayList<>();
    // Current search tags
    private boolean loadAll = false;

    private int contentSortOrder = Preferences.getContentSortOrder();


    public ContentSearchManager(CollectionDAO collectionDAO) {
        this.collectionDAO = collectionDAO;
    }

    public void setFilterFavourites(boolean filterFavourites) {
        this.filterFavourites = filterFavourites;
    }

    public boolean isFilterFavourites() {
        return filterFavourites;
    }

    public void setLoadAll(boolean loadAll) {
        this.loadAll = loadAll;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getQuery() {
        return (query != null) ? query : "";
    }

    public void setTags(List<Attribute> tags) {
        if (tags != null) this.tags = tags;
        else this.tags.clear();
    }

    public List<Attribute> getTags() {
        return tags;
    }

    public void clearSelectedSearchTags() {
        if (tags != null) tags.clear();
    }

    public void setContentSortOrder(int contentSortOrder) {
        this.contentSortOrder = contentSortOrder;
    }

    public void saveToBundle(@Nonnull Bundle outState) {
        outState.putBoolean(KEY_FILTER_FAVOURITES, filterFavourites);
        outState.putString(KEY_QUERY, query);
        outState.putInt(KEY_SORT_ORDER, contentSortOrder);
        outState.putInt(KEY_CURRENT_PAGE, currentPage);
        String searchUri = SearchActivityBundle.Builder.buildSearchUri(tags).toString();
        outState.putString(KEY_SELECTED_TAGS, searchUri);
    }

    public void loadFromBundle(@Nonnull Bundle state) {
        filterFavourites = state.getBoolean(KEY_FILTER_FAVOURITES, false);
        query = state.getString(KEY_QUERY, "");
        contentSortOrder = state.getInt(KEY_SORT_ORDER, Preferences.getContentSortOrder());
        currentPage = state.getInt(KEY_CURRENT_PAGE);

        String searchUri = state.getString(KEY_SELECTED_TAGS);
        tags = SearchActivityBundle.Parser.parseSearchUri(Uri.parse(searchUri));
    }

    public LiveData<PagedList<Content>> getLibrary() {
        if (!getQuery().isEmpty())
            return collectionDAO.searchBooksUniversal(getQuery(), contentSortOrder, filterFavourites, loadAll); // Universal search
        else if (!tags.isEmpty())
            return collectionDAO.searchBooks("", tags, contentSortOrder, filterFavourites, loadAll); // Advanced search
        else
            return collectionDAO.getRecentBooks(contentSortOrder, filterFavourites, loadAll); // Default search (display recent)
    }

    public Single<List<Long>> searchLibraryForId() {
        if (!getQuery().isEmpty())
            return collectionDAO.searchBookIdsUniversal(getQuery(), contentSortOrder, filterFavourites); // Universal search
        else if (!tags.isEmpty())
            return collectionDAO.searchBookIds("", tags, contentSortOrder, filterFavourites); // Advanced search
        else
            return collectionDAO.getRecentBookIds(contentSortOrder, filterFavourites); // Default search (display recent)
    }
}
