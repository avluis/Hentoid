package me.devsaki.hentoid.database;

import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.paging.PagedList;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Single;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.database.domains.SiteHistory;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;

public interface CollectionDAO {

    // CONTENT

    // Low-level operations

    Content selectContent(long id);

    Content selectContentBySourceAndUrl(@NonNull Site site, @NonNull String url);

    void insertContent(@NonNull final Content content);

    void deleteContent(@NonNull final Content content);


    // High-level queries

    Single<List<Long>> getRecentBookIds(int orderStyle, boolean favouritesOnly);

    Single<List<Long>> searchBookIds(String query, List<Attribute> metadata, int orderStyle, boolean favouritesOnly);

    Single<List<Long>> searchBookIdsUniversal(String query, int orderStyle, boolean favouritesOnly);


    LiveData<PagedList<Content>> searchBooksUniversal(String query, int orderStyle, boolean favouritesOnly, boolean loadAll);

    LiveData<PagedList<Content>> searchBooks(String query, List<Attribute> metadata, int orderStyle, boolean favouritesOnly, boolean loadAll);

    LiveData<PagedList<Content>> getRecentBooks(int orderStyle, boolean favouritesOnly, boolean loadAll);

    LiveData<Integer> countBooks(String query, List<Attribute> metadata, boolean favouritesOnly);

    LiveData<Integer> countAllBooks();


    // IMAGEFILES

    void insertImageFile(@NonNull ImageFile img);

    ImageFile selectImageFile(long id);

    LiveData<List<ImageFile>> getDownloadedImagesFromContent(long id);


    // QUEUE

    List<QueueRecord> selectQueue();

    void updateQueue(long contentId, int newOrder);

    void deleteQueue(@NonNull Content content);

    LiveData<PagedList<QueueRecord>> getQueueContent();

    void addContentToQueue(@NonNull final Content content, StatusContent targetImageStatus);


    // ATTRIBUTES

    Single<AttributeQueryResult> getAttributeMasterDataPaged(List<AttributeType> types, String filter, List<Attribute> attrs, boolean filterFavourites, int page, int booksPerPage, int orderStyle);

    Single<SparseIntArray> countAttributesPerType(List<Attribute> filter);


    // SITE HISTORY

    SiteHistory getHistory(@NonNull Site s);

    void insertSiteHistory(@NonNull Site site, @NonNull String url);


    // RESULTS STRUCTURES

    class AttributeQueryResult {
        public List<Attribute> attributes = new ArrayList<>();
        public long totalSelectedAttributes;

        AttributeQueryResult() {}
        public AttributeQueryResult(List<Attribute> attributes, long totalSelectedAttributes) {
            this.attributes = new ArrayList<>(attributes);
            this.totalSelectedAttributes = totalSelectedAttributes;
        }
    }
}
