package me.devsaki.hentoid.database;

import android.util.SparseIntArray;

import androidx.lifecycle.LiveData;
import androidx.paging.PagedList;

import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Language;
import me.devsaki.hentoid.listener.PagedResultListener;
import me.devsaki.hentoid.listener.ResultListener;

public interface CollectionDAO {

    // BOOKS

    void getRecentBooksPaged(Language language, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, PagedResultListener<Content> listener);

    void getRecentBookIds(Language language, int orderStyle, boolean favouritesOnly, PagedResultListener<Long> listener);

    void searchBooksPaged(String query, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, PagedResultListener<Content> listener);

    void searchBookIds(String query, List<Attribute> metadata, int orderStyle, boolean favouritesOnly, PagedResultListener<Long> listener);

    void countBooks(String query, List<Attribute> metadata, boolean favouritesOnly, PagedResultListener<Content> listener);

    void searchBooksUniversalPaged(String query, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, PagedResultListener<Content> listener);

    void searchBookIdsUniversal(String query, int orderStyle, boolean favouritesOnly, PagedResultListener<Long> listener);

    void countBooksUniversal(String query, boolean favouritesOnly, PagedResultListener<Content> listener);



    LiveData<PagedList<Content>> searchBooksUniversal(String query, int orderStyle, boolean favouritesOnly);

    LiveData<PagedList<Content>> searchBooks(String query, List<Attribute> metadata, int orderStyle, boolean favouritesOnly);

    LiveData<PagedList<Content>> getRecentBooks(int orderStyle, boolean favouritesOnly);



    // ATTRIBUTES

    void getAttributeMasterDataPaged(List<AttributeType> types, String filter, List<Attribute> attrs, boolean filterFavourites, int page, int booksPerPage, int orderStyle, ResultListener<List<Attribute>> listener);

    void countAttributesPerType(List<Attribute> filter, ResultListener<SparseIntArray> listener);

    void dispose();
}
