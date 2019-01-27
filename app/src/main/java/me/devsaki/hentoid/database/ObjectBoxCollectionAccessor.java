package me.devsaki.hentoid.database;

import android.content.Context;
import android.util.SparseIntArray;

import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.collection.CollectionAccessor;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Language;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ContentListener;
import me.devsaki.hentoid.listener.ResultListener;

public class ObjectBoxCollectionAccessor implements CollectionAccessor {

    private final ObjectBoxDB db;

    private final int MODE_SEARCH_MODULAR = 0;
    private final int MODE_COUNT_MODULAR = 1;
    private final int MODE_SEARCH_UNIVERSAL = 2;
    private final int MODE_COUNT_UNIVERSAL = 3;


    public ObjectBoxCollectionAccessor(Context ctx)
    {
        db = ObjectBoxDB.getInstance(ctx);
    }


    @Override
    public void getRecentBooks(Site site, Language language, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener) {
        contentSearch(MODE_SEARCH_MODULAR, "", Collections.emptyList(), page, booksPerPage, orderStyle, favouritesOnly, listener);
    }

    @Override
    public void getPages(Content content, ContentListener listener) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void searchBooks(String query, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener) {
        contentSearch(MODE_SEARCH_MODULAR, query, metadata, page, booksPerPage, orderStyle, favouritesOnly, listener);
    }

    @Override
    public void countBooks(String query, List<Attribute> metadata, boolean favouritesOnly, ContentListener listener) {
        contentSearch(MODE_COUNT_MODULAR, query, metadata, 1, 1, 1, favouritesOnly, listener);
    }

    @Override
    public void searchBooksUniversal(String query, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener) {
        contentSearch(MODE_SEARCH_UNIVERSAL, query, Collections.emptyList(), page, booksPerPage, orderStyle, favouritesOnly, listener);
    }

    @Override
    public void countBooksUniversal(String query, boolean favouritesOnly, ContentListener listener) {
        contentSearch(MODE_COUNT_UNIVERSAL, query, Collections.emptyList(), 1, 1, 1, favouritesOnly, listener);
    }

    @Override
    public void getAttributeMasterData(AttributeType type, String filter, ResultListener<List<Attribute>> listener) {

    }

    @Override
    public void getAttributeMasterData(List<AttributeType> types, String filter, ResultListener<List<Attribute>> listener) {

    }

    @Override
    public boolean supportsAvailabilityFilter() {
        return true;
    }

    @Override
    public void getAttributeMasterData(List<AttributeType> types, String filter, List<Attribute> attrs, boolean filterFavourites, ResultListener<List<Attribute>> listener) {

    }

    @Override
    public void getAvailableAttributes(List<AttributeType> types, List<Attribute> attrs, boolean filterFavourites, ResultListener<List<Attribute>> listener) {

    }

    @Override
    public void countAttributesPerType(List<Attribute> filter, ResultListener<SparseIntArray> listener) {

    }

    @Override
    public void dispose() {
        // Nothing special
    }

    private void contentSearch(int mode, String query, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener) {

        List<Content> result = Collections.emptyList();
        long totalSelectedContent = 0;

        if (MODE_SEARCH_MODULAR == mode) {
            result = db.selectContentByQuery(query, page, booksPerPage, metadata, favouritesOnly, orderStyle);
        } else if (MODE_SEARCH_UNIVERSAL == mode) {
            result = db.selectContentByUniqueQuery(query, page, booksPerPage, favouritesOnly, orderStyle);
        }
        // Fetch total query count (i.e. total number of books corresponding to the given filter, in all pages)
        if (MODE_SEARCH_MODULAR == mode || MODE_COUNT_MODULAR == mode) {
            totalSelectedContent = db.countContentByQuery(query, metadata, favouritesOnly);
        } else if (MODE_SEARCH_UNIVERSAL == mode || MODE_COUNT_UNIVERSAL == mode) {
            totalSelectedContent = db.countContentByUniqueQuery(query, favouritesOnly);
        }
        // Fetch total book count (i.e. total number of books in all the collection, regardless of filter)
        long totalContent = db.countAllContent();

        listener.onContentReady(result, totalSelectedContent, totalContent);
    }

    private void modularContentCount(String query, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener) {
        List<Content> result = db.selectContentByQuery(query, page, booksPerPage, metadata, favouritesOnly, orderStyle);
        long totalSelectedContent = db.countContentByQuery(query, metadata, favouritesOnly);
        long totalContent = db.countAllContent();
        listener.onContentReady(result, totalSelectedContent, totalContent);
    }
}
