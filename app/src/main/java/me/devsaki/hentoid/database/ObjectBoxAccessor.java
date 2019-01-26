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

public class ObjectBoxAccessor implements CollectionAccessor {

    private final ObjectBoxDB db;

    public ObjectBoxAccessor(Context ctx)
    {
        db = ObjectBoxDB.getInstance(ctx);
    }


    @Override
    public void getRecentBooks(Site site, Language language, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener) {
        List<Content> result = db.selectContentByQuery("", page, booksPerPage, Collections.emptyList(), favouritesOnly, orderStyle);
        long totalSelectedContent = db.countContentByQuery("", Collections.emptyList(), favouritesOnly);
        long totalContent = db.countAllContent();
        listener.onContentReady(result, totalSelectedContent, totalContent);
    }

    @Override
    public void getPages(Content content, ContentListener listener) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void searchBooks(String query, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener) {

    }

    @Override
    public void countBooks(String query, List<Attribute> metadata, boolean favouritesOnly, ContentListener listener) {

    }

    @Override
    public void searchBooksUniversal(String query, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener) {

    }

    @Override
    public void countBooksUniversal(String query, boolean favouritesOnly, ContentListener listener) {

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
}
