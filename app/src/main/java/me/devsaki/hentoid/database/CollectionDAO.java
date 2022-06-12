package me.devsaki.hentoid.database;

import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.paging.PagedList;

import com.annimon.stream.function.Consumer;

import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import io.reactivex.Single;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ErrorRecord;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.database.domains.GroupItem;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.database.domains.SearchRecord;
import me.devsaki.hentoid.database.domains.SiteBookmark;
import me.devsaki.hentoid.database.domains.SiteHistory;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.widget.ContentSearchManager;

public interface CollectionDAO {

    // CONTENT

    // Low-level operations

    LiveData<PagedList<Content>> selectNoContent();

    @Nullable
    Content selectContent(long id);

    List<Content> selectContent(long[] id);

    @Nullable
    Content selectContentByStorageUri(@NonNull final String folderUri, boolean onlyFlagged);

    @Nullable
    Content selectContentBySourceAndUrl(@NonNull Site site, @NonNull String contentUrl, @NonNull String coverUrl);

    Set<String> selectAllSourceUrls(@NonNull Site site);

    List<Content> searchTitlesWith(@NonNull final String word, int[] contentStatusCodes);

    long insertContent(@NonNull final Content content);

    long insertContentCore(@NonNull final Content content);

    void updateContentStatus(@NonNull final StatusContent updateFrom, @NonNull final StatusContent updateTo);

    void deleteContent(@NonNull final Content content);

    List<ErrorRecord> selectErrorRecordByContentId(long contentId);

    void insertErrorRecord(@NonNull final ErrorRecord record);

    void deleteErrorRecords(long contentId);

    void clearDownloadParams(long contentId);

    void shuffleContent();


    // MASS OPERATIONS

    // Internal library (i.e. managed in the Hentoid folder)

    long countAllInternalBooks(boolean favsOnly);

    void streamAllInternalBooks(boolean favsOnly, Consumer<Content> consumer);

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


    // GROUPS

    List<Group> selectGroups(long[] groupIds);

    LiveData<List<Group>> selectGroupsLive(int grouping, @Nullable String query, int orderField, boolean orderDesc, int artistGroupVisibility, boolean groupFavouritesOnly, int filterRating);

    List<Group> selectGroups(int grouping, int subType);

    List<Group> selectGroups(int grouping);

    @Nullable
    Group selectGroup(long groupId);

    @Nullable
    Group selectGroupByName(int grouping, @NonNull final String name);

    long countGroupsFor(Grouping grouping);

    long insertGroup(Group group);

    void deleteGroup(long groupId);

    void deleteAllGroups(Grouping grouping);

    void flagAllGroups(Grouping grouping);

    void deleteAllFlaggedGroups();

    long insertGroupItem(GroupItem item);

    List<GroupItem> selectGroupItems(long contentId, Grouping grouping);

    void deleteGroupItems(List<Long> groupItemIds);


    // High-level queries (internal and external locations)

    List<Content> selectStoredContent(boolean nonFavouriteOnly, boolean includeQueued, int orderField, boolean orderDesc);

    List<Long> selectStoredContentIds(boolean nonFavouritesOnly, boolean includeQueued, int orderField, boolean orderDesc);

    long countStoredContent(boolean nonFavouriteOnly, boolean includeQueued);

    List<Content> selectContentWithUnhashedCovers();

    long countContentWithUnhashedCovers();


    void streamStoredContent(boolean nonFavouritesOnly, boolean includeQueued, int orderField, boolean orderDesc, Consumer<Content> consumer);


    Single<List<Long>> selectRecentBookIds(ContentSearchManager.ContentSearchBundle searchBundle);

    Single<List<Long>> searchBookIds(ContentSearchManager.ContentSearchBundle searchBundle, List<Attribute> metadata);

    Single<List<Long>> searchBookIdsUniversal(ContentSearchManager.ContentSearchBundle searchBundle);


    LiveData<PagedList<Content>> selectRecentBooks(ContentSearchManager.ContentSearchBundle searchBundle);

    LiveData<PagedList<Content>> searchBooks(ContentSearchManager.ContentSearchBundle searchBundle, List<Attribute> metadata);

    LiveData<PagedList<Content>> searchBooksUniversal(ContentSearchManager.ContentSearchBundle searchBundle);


    LiveData<List<Content>> selectErrorContent();

    List<Content> selectErrorContentList();


    LiveData<Integer> countBooks(long groupId, List<Attribute> metadata);

    LiveData<Integer> countAllBooks();


    // IMAGEFILES

    void insertImageFile(@NonNull ImageFile img);

    void insertImageFiles(@NonNull List<ImageFile> imgs);

    void replaceImageList(long contentId, @NonNull final List<ImageFile> newList);

    void updateImageContentStatus(long contentId, StatusContent updateFrom, @NonNull StatusContent updateTo);

    void updateImageFileStatusParamsMimeTypeUriSize(@NonNull ImageFile image);

    void deleteImageFiles(@NonNull List<ImageFile> imgs);

    ImageFile selectImageFile(long id);

    LiveData<List<ImageFile>> selectDownloadedImagesFromContentLive(long id);

    List<ImageFile> selectDownloadedImagesFromContent(long id);

    Map<StatusContent, ImmutablePair<Integer, Long>> countProcessedImagesById(long contentId);

    Map<Site, ImmutablePair<Integer, Long>> selectPrimaryMemoryUsagePerSource();

    Map<Site, ImmutablePair<Integer, Long>> selectExternalMemoryUsagePerSource();


    // QUEUE

    List<QueueRecord> selectQueue();

    @Nullable
    QueueRecord selectQueue(long contentId);

    LiveData<List<QueueRecord>> selectQueueLive();

    LiveData<List<QueueRecord>> selectQueueLive(String query);

    LiveData<Integer> countQueueLive();

    void addContentToQueue(@NonNull final Content content, StatusContent targetImageStatus, @ContentHelper.QueuePosition int position, long replacedContentId, boolean isQueueActive);

    void updateQueue(@NonNull List<QueueRecord> queue);

    void deleteQueue(@NonNull Content content);

    void deleteQueue(int index);


    // ATTRIBUTES

    Single<AttributeQueryResult> selectAttributeMasterDataPaged(
            @NonNull List<AttributeType> types,
            String filter,
            List<Attribute> attrs,
            int page,
            int booksPerPage,
            int orderStyle);

    Single<SparseIntArray> countAttributesPerType(List<Attribute> filter);


    // CHAPTERS

    List<Chapter> selectChapters(long contentId);

    void insertChapters(@NonNull final List<Chapter> chapters);

    void deleteChapters(@NonNull final Content content);

    void deleteChapter(@NonNull final Chapter chapter);


    // SITE HISTORY

    SiteHistory selectHistory(@NonNull Site s);

    void insertSiteHistory(@NonNull Site site, @NonNull String url);


    // BOOKMARKS

    long countAllBookmarks();

    List<SiteBookmark> selectAllBookmarks();

    List<SiteBookmark> selectBookmarks(@NonNull Site s);

    @Nullable
    SiteBookmark selectHomepage(@NonNull Site s);

    long insertBookmark(@NonNull SiteBookmark bookmark);

    void insertBookmarks(@NonNull List<SiteBookmark> bookmarks);

    void deleteBookmark(long bookmarkId);

    void deleteAllBookmarks();


    // SEARCH HISTORY

    LiveData<List<SearchRecord>> selectSearchRecordsLive();

    void insertSearchRecord(@NonNull SearchRecord record, int limit);

    void deleteAllSearchRecords();


    // RESOURCES

    void cleanup();

    long getDbSizeBytes();


    // ONE-TIME USE QUERIES (MIGRATION & CLEANUP)

    Single<List<Long>> selectOldStoredBookIds();

    long countOldStoredContent();


    // RESULTS STRUCTURES

    // This is a dumb struct class, nothing more
    @SuppressWarnings("squid:S1104")
    class AttributeQueryResult {
        public List<Attribute> attributes = new ArrayList<>();
        public long totalSelectedAttributes = 0;
    }
}
