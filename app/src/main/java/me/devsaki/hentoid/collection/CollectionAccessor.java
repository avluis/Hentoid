package me.devsaki.hentoid.collection;

import android.util.SparseIntArray;

import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Language;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.PagedResultListener;
import me.devsaki.hentoid.listener.ResultListener;

public interface CollectionAccessor {

    // BOOKS

    void getRecentBooksPaged(Site site, Language language, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, PagedResultListener<Content> listener);

    void getRecentBookIdsPaged(Site site, Language language, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, PagedResultListener<Long> listener);

    void getPages(Content content, PagedResultListener<Content> listener);

    void searchBooksPaged(String query, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, PagedResultListener<Content> listener);

    void searchBookIdsPaged(String query, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, PagedResultListener<Long> listener);

    void countBooks(String query, List<Attribute> metadata, boolean favouritesOnly, PagedResultListener<Content> listener);

    void searchBooksUniversalPaged(String query, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, PagedResultListener<Content> listener);

    void searchBookIdsUniversalPaged(String query, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, PagedResultListener<Long> listener);

    void countBooksUniversal(String query, boolean favouritesOnly, PagedResultListener<Content> listener);

    // ATTRIBUTES

    void getAttributeMasterData(List<AttributeType> types, String filter, int sortOrder, ResultListener<List<Attribute>> listener);

    void getAttributeMasterDataPaged(List<AttributeType> types, String filter, int page, int booksPerPage, int orderStyle, ResultListener<List<Attribute>> listener);

    boolean supportsAvailabilityFilter();

    boolean supportsAttributesPaging();

    void getAttributeMasterData(List<AttributeType> types, String filter, List<Attribute> attrs, boolean filterFavourites, int sortOrder, ResultListener<List<Attribute>> listener);

    void getAttributeMasterDataPaged(List<AttributeType> types, String filter, List<Attribute> attrs, boolean filterFavourites, int page, int booksPerPage, int orderStyle, ResultListener<List<Attribute>> listener);

    void getAvailableAttributes(List<AttributeType> types, List<Attribute> attrs, boolean filterFavourites, ResultListener<List<Attribute>> listener);

    void countAttributesPerType(List<Attribute> filter, ResultListener<SparseIntArray> listener);

    void dispose();
}
