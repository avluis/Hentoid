package me.devsaki.hentoid.database;

import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.paging.PagedList;

import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.database.domains.SiteHistory;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.listener.PagedResultListener;
import me.devsaki.hentoid.listener.ResultListener;

public interface CollectionDAO {

    // CONTENT

    // Low-level operations

    Content selectContent(long id);

    Content selectContentBySourceAndUrl(@NonNull Site site, @NonNull String url);

    void insertContent(@NonNull final Content content);

    void deleteContent(@NonNull final Content content);


    // High-level queries

    void getRecentBookIds(int orderStyle, boolean favouritesOnly, PagedResultListener<Long> listener);

    void searchBookIds(String query, List<Attribute> metadata, int orderStyle, boolean favouritesOnly, PagedResultListener<Long> listener);

    void searchBookIdsUniversal(String query, int orderStyle, boolean favouritesOnly, PagedResultListener<Long> listener);


    ActivePagedList<Content> searchBooksUniversal(String query, int orderStyle, boolean favouritesOnly);

    ActivePagedList<Content> searchBooks(String query, List<Attribute> metadata, int orderStyle, boolean favouritesOnly);

    ActivePagedList<Content> getRecentBooks(int orderStyle, boolean favouritesOnly);

    LiveData<Integer> countBooks(String query, List<Attribute> metadata, boolean favouritesOnly);

    LiveData<Integer> countAllBooks();


    // IMAGEFILES

    void insertImageFile(@NonNull ImageFile img);

    ImageFile selectImageFile(long id);


    // QUEUE

    List<QueueRecord> selectQueue();

    void updateQueue(long contentId, int newOrder);

    void deleteQueue(@NonNull Content content);

    LiveData<PagedList<QueueRecord>> getQueueContent();

    void addContentToQueue(@NonNull final Content content, StatusContent targetImageStatus);


    // ATTRIBUTES

    void getAttributeMasterDataPaged(List<AttributeType> types, String filter, List<Attribute> attrs, boolean filterFavourites, int page, int booksPerPage, int orderStyle, ResultListener<List<Attribute>> listener);

    void countAttributesPerType(List<Attribute> filter, ResultListener<SparseIntArray> listener);

    void dispose();


    // SITE HISTORY

    SiteHistory getHistory(@NonNull Site s);

    void insertSiteHistory(@NonNull Site site, @NonNull String url);
}
