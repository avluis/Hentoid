package me.devsaki.hentoid.widget;

import android.content.Context;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.collection.CollectionAccessor;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.enums.Language;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ContentListener;
import me.devsaki.hentoid.util.Preferences;

public class ContentSearchManager {

    // Save state constants
    private static final String KEY_SELECTED_TAGS = "selected_tags";
    private static final String KEY_FILTER_FAVOURITES = "filter_favs";
    private static final String KEY_QUERY = "query";
    private static final String KEY_SORT_ORDER = "sort_order";

    private final CollectionAccessor accessor;

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

    public void saveToBundle(@Nonnull Bundle outState) {
        outState.putBoolean(KEY_FILTER_FAVOURITES, filterFavourites);
        outState.putString(KEY_QUERY, query);
        outState.putInt(KEY_SORT_ORDER, contentSortOrder);
        long[] selectedTagIds = new long[tags.size()];
        int index = 0;
        for (Attribute a : tags) {
            selectedTagIds[index++] = a.getId();
        }
        outState.putLongArray(KEY_SELECTED_TAGS, selectedTagIds);
    }

    public void loadFromBundle(@Nonnull Bundle state, Context ctx) {
        filterFavourites = state.getBoolean(KEY_FILTER_FAVOURITES, false);
        query = state.getString(KEY_QUERY, "");
        contentSortOrder = state.getInt(KEY_SORT_ORDER, Preferences.getContentSortOrder());

        long[] selectedTagIds = state.getLongArray(KEY_SELECTED_TAGS);
        ObjectBoxDB db = ObjectBoxDB.getInstance(ctx);
        if (selectedTagIds != null) {
            for (long i : selectedTagIds) {
                Attribute a = db.selectAttributeById(i);
                if (a != null) {
                    tags.add(a);
                }
            }
        }
    }

    public void searchLibrary(int currentPage, int booksPerPage, ContentListener listener) {
        if (!getQuery().isEmpty())
            accessor.searchBooksUniversal(getQuery(), currentPage, booksPerPage, contentSortOrder, filterFavourites, listener); // Universal search
        else if (!tags.isEmpty())
            accessor.searchBooks("", tags, currentPage, booksPerPage, contentSortOrder, filterFavourites, listener); // Advanced search
        else
            accessor.getRecentBooks(Site.HITOMI, Language.ANY, currentPage, booksPerPage, contentSortOrder, filterFavourites, listener); // Default search (display recent)
        // TODO : do something about these ridiculous default 1st arguments
    }

    public void dispose() {
        accessor.dispose();
    }
}
