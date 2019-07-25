package me.devsaki.hentoid.widget;

import android.net.Uri;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.activities.bundles.SearchActivityBundle;
import me.devsaki.hentoid.collection.CollectionAccessor;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Language;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.PagedResultListener;
import me.devsaki.hentoid.util.Preferences;

public class ContentSearchManager {

    // Save state constants
    private static final String KEY_SELECTED_TAGS = "selected_tags";
    private static final String KEY_FILTER_FAVOURITES = "filter_favs";
    private static final String KEY_QUERY = "query";
    private static final String KEY_SORT_ORDER = "sort_order";
    private static final String KEY_CURRENT_PAGE = "current_page";

    private final CollectionAccessor accessor;

    // Current page of collection view (NB : In EndlessFragment, a "page" is a group of loaded books. Last page is reached when scrolling reaches the very end of the book list)
    private int currentPage = 1;
    // Favourite filter active
    private boolean filterFavourites = false;
    // Full-text query
    private String query = "";
    // Current search tags
    private List<Attribute> tags = new ArrayList<>();

    private int contentSortOrder = Preferences.getContentSortOrder();


    public ContentSearchManager(CollectionAccessor accessor) {
        this.accessor = accessor;
    }

    public void setFilterFavourites(boolean filterFavourites) {
        this.filterFavourites = filterFavourites;
    }

    public boolean isFilterFavourites() {
        return filterFavourites;
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

    public int getContentSortOrder() {
        return contentSortOrder;
    }

    public void setContentSortOrder(int contentSortOrder) {
        this.contentSortOrder = contentSortOrder;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    public void increaseCurrentPage() {
        currentPage++;
    }

    public void decreaseCurrentPage() {
        currentPage--;
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

    public void searchLibraryForContent(int booksPerPage, PagedResultListener<Content> listener) {
        if (!getQuery().isEmpty())
            accessor.searchBooksUniversalPaged(getQuery(), currentPage, booksPerPage, contentSortOrder, filterFavourites, listener); // Universal search
        else if (!tags.isEmpty())
            accessor.searchBooksPaged("", tags, currentPage, booksPerPage, contentSortOrder, filterFavourites, listener); // Advanced search
        else
            accessor.getRecentBooksPaged(Site.HITOMI, Language.ANY, currentPage, booksPerPage, contentSortOrder, filterFavourites, listener); // Default search (display recent)
        // TODO : do something about these ridiculous default 1st arguments
    }

    public void searchLibraryForId(int booksPerPage, PagedResultListener<Long> listener) {
        if (!getQuery().isEmpty())
            accessor.searchBookIdsUniversalPaged(getQuery(), currentPage, booksPerPage, contentSortOrder, filterFavourites, listener); // Universal search
        else if (!tags.isEmpty())
            accessor.searchBookIdsPaged("", tags, currentPage, booksPerPage, contentSortOrder, filterFavourites, listener); // Advanced search
        else
            accessor.getRecentBookIdsPaged(Site.HITOMI, Language.ANY, currentPage, booksPerPage, contentSortOrder, filterFavourites, listener); // Default search (display recent)
        // TODO : do something about these ridiculous default 1st arguments
    }

    public void dispose() {
        accessor.dispose();
    }
}
