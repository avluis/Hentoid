package me.devsaki.hentoid.database;

import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.paging.PagedList;

import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import io.reactivex.Single;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ErrorRecord;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.database.domains.GroupItem;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.database.domains.SiteHistory;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;

public interface CollectionDAO {

    // CONTENT

    // Low-level operations

    @Nullable
    Content selectContent(long id);
    List<Content> selectContent(long[] id);

    @Nullable
    Content selectContentByFolderUri(@NonNull final String folderUri, boolean onlyFlagged);

    @Nullable
    Content selectContentBySourceAndUrl(@NonNull Site site, @NonNull String url);

    long insertContent(@NonNull final Content content);

    void updateContentStatus(@NonNull final StatusContent updateFrom, @NonNull final StatusContent updateTo);

    void deleteContent(@NonNull final Content content);

    List<ErrorRecord> selectErrorRecordByContentId(long contentId);

    void insertErrorRecord(@NonNull final ErrorRecord record);

    void deleteErrorRecords(long contentId);


    // MASS OPERATIONS

    // Internal library (i.e. managed in the Hentoid folder)

    long countAllInternalBooks(boolean favsOnly);

    List<Content> selectAllInternalBooks(boolean favsOnly);

    void flagAllInternalBooks();

    void deleteAllInternalBooks(boolean resetRemainingImagesStatus);

    // Queued books

    void flagAllErrorBooksWithJson();

    long countAllQueueBooks();

    List<Content> selectAllQueueBooks();

    void deleteAllQueuedBooks();

    // Flagging

    void deleteAllFlaggedBooks(boolean resetRemainingImagesStatus);

    // External library

    long countAllExternalBooks();

    void deleteAllExternalBooks();

    // Groups

    LiveData<List<Group>> selectGroups(int grouping, @Nullable String query, int orderStyle);
    List<Group> selectGroups(int grouping);

    @Nullable
    Group selectGroup(long groupId);

    @Nullable
    Group selectGroupByFlag(int grouping, int flag);

    long countGroupsFor(Grouping grouping);

    long insertGroup(Group group);

    void deleteGroup(long groupId);

    long insertGroupItem(GroupItem item);

    List<GroupItem> selectGroupItems(long contentId, Grouping grouping);

    void deleteGroupItem(long groupItemId);

    void deleteGroupItems(List<Long> groupItemIds);



    // High-level queries (internal and external locations)

    Single<List<Long>> getStoredBookIds(boolean nonFavouriteOnly, boolean includeQueued);


    Single<List<Long>> getRecentBookIds(long groupId, int orderField, boolean orderDesc, boolean favouritesOnly);

    Single<List<Long>> searchBookIds(String query, long groupId, List<Attribute> metadata, int orderField, boolean orderDesc, boolean favouritesOnly);

    Single<List<Long>> searchBookIdsUniversal(String query, long groupId, int orderField, boolean orderDesc, boolean favouritesOnly);


    LiveData<PagedList<Content>> getRecentBooks(long groupId, int orderField, boolean orderDesc, boolean favouritesOnly, boolean loadAll);

    LiveData<PagedList<Content>> searchBooks(String query, long groupId, List<Attribute> metadata, int orderField, boolean orderDesc, boolean favouritesOnly, boolean loadAll);

    LiveData<PagedList<Content>> searchBooksUniversal(String query, long groupId, int orderField, boolean orderDesc, boolean favouritesOnly, boolean loadAll);


    LiveData<List<Content>> getErrorContent();


    LiveData<Integer> countBooks(String query, long groupId, List<Attribute> metadata, boolean favouritesOnly);

    LiveData<Integer> countAllBooks();


    // IMAGEFILES

    void insertImageFile(@NonNull ImageFile img);

    void replaceImageList(long contentId, @NonNull final List<ImageFile> newList);

    void updateImageContentStatus(long contentId, StatusContent updateFrom, @NonNull StatusContent updateTo);

    void updateImageFileStatusParamsMimeTypeUriSize(@NonNull ImageFile image);

    void deleteImageFiles(@NonNull List<ImageFile> imgs);

    ImageFile selectImageFile(long id);

    LiveData<List<ImageFile>> getDownloadedImagesFromContent(long id);

    Map<StatusContent, ImmutablePair<Integer, Long>> countProcessedImagesById(long contentId);

    Map<Site, ImmutablePair<Integer, Long>> getMemoryUsagePerSource();


    // QUEUE

    List<QueueRecord> selectQueue();

    LiveData<List<QueueRecord>> getQueueContent();

    void addContentToQueue(@NonNull final Content content, StatusContent targetImageStatus);

    void updateQueue(@NonNull List<QueueRecord> queue);

    void deleteQueue(@NonNull Content content);

    void deleteQueue(int index);


    // ATTRIBUTES

    Single<AttributeQueryResult> getAttributeMasterDataPaged(
            @NonNull List<AttributeType> types,
            String filter,
            List<Attribute> attrs,
            boolean filterFavourites,
            int page,
            int booksPerPage,
            int orderStyle);

    Single<SparseIntArray> countAttributesPerType(List<Attribute> filter);


    // SITE HISTORY

    SiteHistory getHistory(@NonNull Site s);

    void insertSiteHistory(@NonNull Site site, @NonNull String url);


    // RESOURCES

    void cleanup();

    long getDbSizeBytes();


    // ONE-TIME USE QUERIES (MIGRATION & CLEANUP)

    Single<List<Long>> getOldStoredBookIds();

    long countOldStoredContent();


    // RESULTS STRUCTURES
    @SuppressWarnings("squid:S1104")
            // This is a dumb struct class, nothing more
    class AttributeQueryResult {
        public List<Attribute> attributes = new ArrayList<>();
        public long totalSelectedAttributes = 0;
    }
}
