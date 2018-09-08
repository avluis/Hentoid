package me.devsaki.hentoid.collection;

import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Language;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.AttributeListener;
import me.devsaki.hentoid.listener.ContentListener;

public abstract class BaseCollectionAccessor implements CollectionAccessor {

    protected static final String USAGE_RECENT_BOOKS = "recentBooks";
    protected static final String USAGE_BOOK_PAGES = "bookPages";
    protected static final String USAGE_SEARCH = "search";

    @Override
    public abstract void getRecentBooks(Site site, Language language, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener);

    @Override
    public abstract void getPages(Content content, ContentListener listener);

    @Override
    public void searchBooks(String query, List<Attribute> metadata, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener)
    {
        searchBooks(query, metadata, 1, booksPerPage, orderStyle, favouritesOnly, listener);
    }

    @Override
    public abstract void searchBooks(String query, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener);

    @Override
    public void getAttributeMasterData(AttributeType attr, AttributeListener listener) {
        getAttributeMasterData(attr, "", listener);
    }

    @Override
    public abstract void getAttributeMasterData(AttributeType attr, String filter, AttributeListener listener);
}
