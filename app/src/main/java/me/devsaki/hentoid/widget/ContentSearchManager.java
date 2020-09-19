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
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.util.Preferences;

public class ContentSearchManager {

    // Save state constants
    private static final String KEY_SELECTED_TAGS = "selected_tags";
    private static final String KEY_GROUP = "group";
    private static final String KEY_FILTER_FAVOURITES = "filter_favs";
    private static final String KEY_QUERY = "query";
    private static final String KEY_SORT_FIELD = "sort_field";
    private static final String KEY_SORT_DESC = "sort_desc";
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
    // Group
    private long groupId;
    // Sort field and direction
    private int contentSortField = Preferences.getContentSortField();
    private boolean contentSortDesc = Preferences.isContentSortDesc();


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

    public void setContentSortField(int contentSortField) {
        this.contentSortField = contentSortField;
    }

    public void setContentSortDesc(boolean contentSortDesc) {
        this.contentSortDesc = contentSortDesc;
    }

    public void setGroup(Group group) {
        if (group != null)
            groupId = group.id;
        else
            groupId = -1;
    }

    public List<Attribute> getTags() {
        return tags;
    }

    public void clearSelectedSearchTags() {
        if (tags != null) tags.clear();
    }


    public void saveToBundle(@Nonnull Bundle outState) {
        outState.putBoolean(KEY_FILTER_FAVOURITES, filterFavourites);
        outState.putString(KEY_QUERY, query);
        outState.putInt(KEY_SORT_FIELD, contentSortField);
        outState.putBoolean(KEY_SORT_DESC, contentSortDesc);
        outState.putInt(KEY_CURRENT_PAGE, currentPage);
        String searchUri = SearchActivityBundle.Builder.buildSearchUri(tags).toString();
        outState.putString(KEY_SELECTED_TAGS, searchUri);
        outState.putLong(KEY_GROUP, groupId);
    }

    public void loadFromBundle(@Nonnull Bundle state) {
        filterFavourites = state.getBoolean(KEY_FILTER_FAVOURITES, false);
        query = state.getString(KEY_QUERY, "");
        contentSortField = state.getInt(KEY_SORT_FIELD, Preferences.getContentSortField());
        contentSortDesc = state.getBoolean(KEY_SORT_DESC, Preferences.isContentSortDesc());
        currentPage = state.getInt(KEY_CURRENT_PAGE);

        String searchUri = state.getString(KEY_SELECTED_TAGS);
        tags = SearchActivityBundle.Parser.parseSearchUri(Uri.parse(searchUri));
        groupId = state.getLong(KEY_GROUP);
    }

    public LiveData<PagedList<Content>> getLibrary() {
        if (!getQuery().isEmpty())
            return collectionDAO.searchBooksUniversal(getQuery(), groupId, contentSortField, contentSortDesc, filterFavourites, loadAll); // Universal search
        else if (!tags.isEmpty())
            return collectionDAO.searchBooks("", groupId, tags, contentSortField, contentSortDesc, filterFavourites, loadAll); // Advanced search
        else
            return collectionDAO.getRecentBooks(groupId, contentSortField, contentSortDesc, filterFavourites, loadAll); // Default search (display recent)
    }

    public Single<List<Long>> searchLibraryForId() {
        if (!getQuery().isEmpty())
            return collectionDAO.searchBookIdsUniversal(getQuery(), groupId, contentSortField, contentSortDesc, filterFavourites); // Universal search
        else if (!tags.isEmpty())
            return collectionDAO.searchBookIds("", groupId, tags, contentSortField, contentSortDesc, filterFavourites); // Advanced search
        else
            return collectionDAO.getRecentBookIds(groupId, contentSortField, contentSortDesc, filterFavourites); // Default search (display recent)
    }
}
