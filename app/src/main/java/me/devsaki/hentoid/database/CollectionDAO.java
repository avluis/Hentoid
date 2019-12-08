package me.devsaki.hentoid.database;

import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.paging.PagedList;

import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.listener.PagedResultListener;
import me.devsaki.hentoid.listener.ResultListener;

public interface CollectionDAO {

    // CONTENT

    // Low-level operations

    Content selectContent(long id);

    void insertContent(@NonNull final Content content);

    void deleteContent(@NonNull final Content content);


    // High-level queries

    void getRecentBookIds(int orderStyle, boolean favouritesOnly, PagedResultListener<Long> listener);

    void searchBookIds(String query, List<Attribute> metadata, int orderStyle, boolean favouritesOnly, PagedResultListener<Long> listener);

    void searchBookIdsUniversal(String query, int orderStyle, boolean favouritesOnly, PagedResultListener<Long> listener);


    LiveData<PagedList<Content>> searchBooksUniversal(String query, int orderStyle, boolean favouritesOnly);

    LiveData<PagedList<Content>> searchBooks(String query, List<Attribute> metadata, int orderStyle, boolean favouritesOnly);

    LiveData<PagedList<Content>> getRecentBooks(int orderStyle, boolean favouritesOnly);

    LiveData<Integer> countBooks(String query, List<Attribute> metadata, boolean favouritesOnly);


    // Other stuff

    void addContentToQueue(@NonNull final Content content);


    // ATTRIBUTES

    void getAttributeMasterDataPaged(List<AttributeType> types, String filter, List<Attribute> attrs, boolean filterFavourites, int page, int booksPerPage, int orderStyle, ResultListener<List<Attribute>> listener);

    void countAttributesPerType(List<Attribute> filter, ResultListener<SparseIntArray> listener);

    void dispose();
}
