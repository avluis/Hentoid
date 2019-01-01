package me.devsaki.hentoid.collection;

import android.util.SparseIntArray;

import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Language;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ContentListener;
import me.devsaki.hentoid.listener.ResultListener;

public interface CollectionAccessor {

    void getRecentBooks(Site site, Language language, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener);

    void getPages(Content content, ContentListener listener);

    void searchBooks(String query, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener);

    void countBooks(String query, List<Attribute> metadata, boolean favouritesOnly, ContentListener listener);

    void searchBooksUniversal(String query,  int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener);

    void countBooksUniversal(String query, boolean favouritesOnly, ContentListener listener);

    void getAttributeMasterData(AttributeType type, String filter, ResultListener<List<Attribute>> listener);

    void getAttributeMasterData(List<AttributeType> types, String filter, ResultListener<List<Attribute>> listener);

    boolean supportsAvailabilityFilter();

    void getAttributeMasterData(List<AttributeType> types, String filter, List<Attribute> attrs, boolean filterFavourites, ResultListener<List<Attribute>> listener);

    void getAvailableAttributes(List<AttributeType> types, List<Attribute> attrs, boolean filterFavourites, ResultListener<List<Attribute>> listener);

    void countAttributesPerType(List<Attribute> filter, ResultListener<SparseIntArray> listener);

    void dispose();
}
