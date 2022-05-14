package me.devsaki.hentoid.viewmodels;

import static me.devsaki.hentoid.util.GroupHelper.moveContentToCustomGroup;

import android.app.Application;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.paging.PagedList;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.greenrobot.eventbus.EventBus;
import org.threeten.bp.Instant;

import java.io.IOException;
import java.io.OutputStream;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.core.Consts;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.database.domains.GroupItem;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.util.ArchiveHelper;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.GroupHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.RandomSeedSingleton;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.download.ContentQueueManager;
import me.devsaki.hentoid.util.exception.ContentNotProcessedException;
import me.devsaki.hentoid.util.exception.EmptyResultException;
import me.devsaki.hentoid.util.network.HttpHelper;
import me.devsaki.hentoid.widget.ContentSearchManager;
import me.devsaki.hentoid.widget.GroupSearchManager;
import me.devsaki.hentoid.workers.DeleteWorker;
import me.devsaki.hentoid.workers.PurgeWorker;
import me.devsaki.hentoid.workers.data.DeleteData;
import timber.log.Timber;


public class LibraryViewModel extends AndroidViewModel {

    // Collection DAO
    private final CollectionDAO dao;
    // Content search manager
    private final ContentSearchManager contentSearchManager;
    // Groups search manager
    private final GroupSearchManager groupSearchManager;
    // Cleanup for all RxJava calls
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    // Cleanup for all work observers
    private final List<Pair<UUID, Observer<WorkInfo>>> workObservers = new ArrayList<>();

    // Content data
    private LiveData<PagedList<Content>> currentSource;
    private final LiveData<Integer> totalContent;
    private final MediatorLiveData<PagedList<Content>> libraryPaged = new MediatorLiveData<>();
    private final MutableLiveData<Bundle> contentSearchBundle = new MutableLiveData<>();
    // Groups data
    private final MutableLiveData<Group> group = new MutableLiveData<>();
    private LiveData<List<Group>> currentGroupsSource;
    private final MediatorLiveData<List<Group>> groups = new MediatorLiveData<>();
    private LiveData<List<Group>> currentGroupsTotalSource;
    private final MediatorLiveData<Integer> currentGroupTotal = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> isCustomGroupingAvailable = new MutableLiveData<>();     // True if there's at least one existing custom group; false instead
    private final MutableLiveData<Bundle> groupSearchBundle = new MutableLiveData<>();

    // Updated whenever a new COntentsearch is performed
    private final MediatorLiveData<Boolean> newContentSearch = new MediatorLiveData<>();


    public LibraryViewModel(@NonNull Application application, @NonNull CollectionDAO collectionDAO) {
        super(application);
        dao = collectionDAO;
        contentSearchManager = new ContentSearchManager(dao);
        groupSearchManager = new GroupSearchManager(dao);
        totalContent = dao.countAllBooks();
        refreshCustomGroupingAvailable();
    }

    public void onSaveState(Bundle outState) {
        contentSearchManager.saveToBundle(outState);
        groupSearchManager.saveToBundle(outState);
    }

    public void onRestoreState(@Nullable Bundle savedState) {
        if (savedState == null) return;
        contentSearchManager.loadFromBundle(savedState);
        groupSearchManager.loadFromBundle(savedState);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        dao.cleanup();
        compositeDisposable.clear();
        if (workObservers.isEmpty()) {
            WorkManager workManager = WorkManager.getInstance(getApplication());
            for (Pair<UUID, Observer<WorkInfo>> info : workObservers)
                workManager.getWorkInfoByIdLiveData(info.getLeft()).removeObserver(info.getRight());
        }
    }

    @NonNull
    public LiveData<List<Group>> getGroups() {
        return groups;
    }

    @NonNull
    public LiveData<PagedList<Content>> getLibraryPaged() {
        return libraryPaged;
    }

    @NonNull
    public LiveData<Integer> getTotalContent() {
        return totalContent;
    }

    @NonNull
    public LiveData<Boolean> getNewContentSearch() {
        return newContentSearch;
    }

    @NonNull
    public LiveData<Group> getGroup() {
        return group;
    }

    @NonNull
    public LiveData<Integer> getTotalGroup() {
        return currentGroupTotal;
    }

    @NonNull
    public LiveData<Boolean> isCustomGroupingAvailable() {
        return isCustomGroupingAvailable;
    }

    @NonNull
    public LiveData<Bundle> getContentSearchManagerBundle() {
        return contentSearchBundle;
    }

    @NonNull
    public LiveData<Bundle> getGroupSearchManagerBundle() {
        return groupSearchBundle;
    }

    // =========================
    // ========= LIBRARY ACTIONS
    // =========================

    /**
     * Perform a new library search
     */
    private void doSearchContent() {
        // Update search properties set directly through Preferences
        Timber.v(">> doSearchContent");
        contentSearchManager.setContentSortField(Preferences.getContentSortField());
        contentSearchManager.setContentSortDesc(Preferences.isContentSortDesc());
        if (Preferences.getGroupingDisplay().equals(Grouping.FLAT))
            contentSearchManager.setGroup(null);

        if (currentSource != null) libraryPaged.removeSource(currentSource);
        currentSource = contentSearchManager.getLibrary();
        libraryPaged.addSource(currentSource, libraryPaged::setValue);
        contentSearchBundle.postValue(contentSearchManager.toBundle());
    }

    /**
     * Perform a new content universal search using the given query
     *
     * @param query Query to use for the universal search
     */
    public void searchContentUniversal(@NonNull String query) {
        contentSearchManager.clearSelectedSearchTags(); // If user searches in main toolbar, universal search takes over advanced search
        contentSearchManager.setQuery(query);
        newContentSearch.setValue(true);
        doSearchContent();
    }

    /**
     * Perform a new content search using the given query and metadata
     *
     * @param query    Query to use for the search
     * @param metadata Metadata to use for the search
     */
    public void searchContent(@NonNull String query, @NonNull List<Attribute> metadata) {
        contentSearchManager.setQuery(query);
        contentSearchManager.setTags(metadata);
        newContentSearch.setValue(true);
        doSearchContent();
    }

    public void clearContent() {
        if (currentSource != null) {
            libraryPaged.removeSource(currentSource);
            currentSource = dao.selectNoContent();
            libraryPaged.addSource(currentSource, libraryPaged::setValue);
        }
    }

    public void searchGroup() {
        doSearchGroup();
    }

    private void doSearchGroup() {
        Timber.v(">> doSearchGroup");
        // Update search properties set directly through Preferences
        groupSearchManager.setSortField(Preferences.getGroupSortField());
        groupSearchManager.setSortDesc(Preferences.isGroupSortDesc());
        groupSearchManager.setGrouping(Preferences.getGroupingDisplay());
        groupSearchManager.setArtistGroupVisibility(Preferences.getArtistGroupVisibility());

        if (currentGroupsSource != null) groups.removeSource(currentGroupsSource);
        currentGroupsSource = groupSearchManager.getGroups();
        groups.addSource(currentGroupsSource, groups::setValue);

        if (currentGroupsTotalSource != null)
            currentGroupTotal.removeSource(currentGroupsTotalSource);
        currentGroupsTotalSource = groupSearchManager.getAllGroups();
        currentGroupTotal.addSource(currentGroupsTotalSource, list -> currentGroupTotal.postValue(list.size()));

        groupSearchBundle.postValue(groupSearchManager.toBundle());
        refreshCustomGroupingAvailable();
    }

    public void refreshCustomGroupingAvailable() {
        isCustomGroupingAvailable.postValue(dao.countGroupsFor(Grouping.CUSTOM) > 0);
    }


    /**
     * Toggle the completed filter
     */
    public void toggleCompletedFilter() {
        contentSearchManager.setFilterBookCompleted(!contentSearchManager.isFilterBookCompleted());
        newContentSearch.setValue(true);
        doSearchContent();
    }

    /**
     * Toggle the completed filter
     */
    public void toggleNotCompletedFilter() {
        contentSearchManager.setFilterBookNotCompleted(!contentSearchManager.isFilterBookNotCompleted());
        newContentSearch.setValue(true);
        doSearchContent();
    }

    /**
     * Toggle the books favourite filter
     */
    public void setContentFavouriteFilter(boolean value) {
        contentSearchManager.setFilterBookFavourites(value);
        newContentSearch.setValue(true);
        doSearchContent();
    }

    /**
     * Toggle the groups favourite filter
     */
    public void setGroupFavouriteFilter(boolean value) {
        groupSearchManager.setFilterFavourites(value);
        doSearchGroup();
    }

    /**
     * Toggle the books rating filter
     */
    public void setContentRatingFilter(int value) {
        contentSearchManager.setFilterBookRating(value);
        newContentSearch.setValue(true);
        doSearchContent();
    }


    public void setGroupQuery(String value) {
        groupSearchManager.setQuery(value);
        doSearchGroup();
    }

    public void setGrouping(int groupingId) {
        int currentGrouping = Preferences.getGroupingDisplay().getId();
        if (groupingId != currentGrouping) {
            Preferences.setGroupingDisplay(groupingId);
            if (groupingId == Grouping.FLAT.getId()) doSearchContent();
            doSearchGroup();
        }
    }

    public void clearGroupFilters() {
        groupSearchManager.clearFilters();
        doSearchGroup();
    }

    public void clearContentFilters() {
        contentSearchManager.clearFilters();
        doSearchContent();
    }

    /**
     * Set Content paging mode (endless or paged)
     */
    public void setContentPagingMethod(boolean isEndless) {
        contentSearchManager.setLoadAll(!isEndless);
        newContentSearch.setValue(true);
        doSearchContent();
    }

    public void searchContent() {
        newContentSearch.setValue(true);
        doSearchContent();
    }

    public void setGroup(Group group, boolean forceRefresh) {
        Group currentGroup = this.group.getValue();
        if (!forceRefresh && Objects.equals(group, currentGroup)) return;

        // Reset content sorting to TITLE when reaching the Ungrouped group with CUSTOM sorting (can't work)
        if (!group.grouping.canReorderBooks() || (group.grouping.equals(Grouping.CUSTOM) && 1 == group.getSubtype()))
            Preferences.setContentSortField(Preferences.Constant.ORDER_FIELD_TITLE);

        this.group.postValue(group);
        contentSearchManager.setGroup(group);

        newContentSearch.setValue(true);
        // Don't search now as the UI will inevitably search as well upon switching to books view
        // TODO only useful when browsing custom groups ?
        doSearchContent();
    }

    // =========================
    // ========= CONTENT ACTIONS
    // =========================


    public void toggleContentCompleted(@NonNull final List<Content> content, @NonNull final Runnable onSuccess) {
        compositeDisposable.add(
                Observable.fromIterable(content)
                        .observeOn(Schedulers.io())
                        .map(c -> {
                            doToggleContentCompleted(c.getId());
                            return c;
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                v -> onSuccess.run(),
                                Timber::e
                        )
        );
    }

    /**
     * Toggle the "completed" state of the given content
     *
     * @param contentId ID of the content whose completed state to toggle
     */
    private void doToggleContentCompleted(long contentId) {
        Helper.assertNonUiThread();

        // Check if given content still exists in DB
        Content theContent = dao.selectContent(contentId);

        if (theContent != null) {
            if (theContent.isBeingDeleted()) return;
            theContent.setCompleted(!theContent.isCompleted());
            ContentHelper.persistJson(getApplication(), theContent);
            dao.insertContent(theContent);
            return;
        }

        throw new InvalidParameterException("Invalid ContentId : " + contentId);
    }

    /**
     * Toggle the "favourite" state of the given content
     *
     * @param content Content whose favourite state to toggle
     */
    public void toggleContentFavourite(@NonNull final Content content, @NonNull final Runnable onSuccess) {
        if (content.isBeingDeleted()) return;

        compositeDisposable.add(
                Single.fromCallable(() -> doToggleContentFavourite(content.getId()))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                v -> onSuccess.run(),
                                Timber::e
                        )
        );
    }

    /**
     * Toggle the "favourite" state of the given content
     *
     * @param contentId ID of the content whose favourite state to toggle
     * @return Resulting content
     */
    private Content doToggleContentFavourite(long contentId) {
        Helper.assertNonUiThread();

        // Check if given content still exists in DB
        Content theContent = dao.selectContent(contentId);

        if (theContent != null) {
            theContent.setFavourite(!theContent.isFavourite());
            ContentHelper.persistJson(getApplication(), theContent);
            dao.insertContent(theContent);
            return theContent;
        }

        throw new InvalidParameterException("Invalid ContentId : " + contentId);
    }

    /**
     * Set the rating to the given value for the given content IDs
     *
     * @param contentIds   Content IDs to set the rating for
     * @param targetRating Rating to set
     * @param onSuccess    Runnable to call if the operation succeeds
     */
    public void rateContents(@NonNull final List<Long> contentIds, int targetRating, @NonNull final Runnable onSuccess) {
        compositeDisposable.add(
                Observable.fromIterable(contentIds)
                        .observeOn(Schedulers.io())
                        .map(id -> doRateContent(id, targetRating))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                v -> onSuccess.run(),
                                Timber::w
                        )
        );
    }

    /**
     * Set the rating to the given value for the given content ID
     *
     * @param contentId    Content ID to set the rating for
     * @param targetRating Rating to set
     */
    private Content doRateContent(long contentId, int targetRating) {
        Helper.assertNonUiThread();

        // Check if given content still exists in DB
        Content theContent = dao.selectContent(contentId);

        if (theContent != null) {
            theContent.setRating(targetRating);
            ContentHelper.persistJson(getApplication(), theContent);
            dao.insertContent(theContent);
            return theContent;
        }

        throw new InvalidParameterException("Invalid ContentId : " + contentId);
    }

    public void redownloadContent(
            @NonNull final List<Content> contentList,
            boolean reparseContent,
            boolean reparseImages,
            int position,
            @NonNull final Consumer<Integer> onSuccess,
            @NonNull final Consumer<Throwable> onError) {
        // Flag the content as "being deleted" (triggers blink animation)
        for (Content c : contentList) flagContentDelete(c, true);

        StatusContent targetImageStatus = reparseImages ? StatusContent.ERROR : null;
        AtomicInteger errorCount = new AtomicInteger(0);

        compositeDisposable.add(
                Observable.fromIterable(contentList)
                        .observeOn(Schedulers.io())
                        .map(c -> (reparseContent) ? ContentHelper.reparseFromScratch(c).right : Optional.of(c))
                        .doOnNext(c -> {
                            if (c.isPresent()) {
                                Content content = c.get();
                                // Non-blocking performance bottleneck; run in a dedicated worker
                                // TODO if the purge is extremely long, that worker might still be working while downloads are happening on these same books
                                if (reparseImages) purgeItem(content, false);
                                dao.addContentToQueue(
                                        content, targetImageStatus, position, -1,
                                        ContentQueueManager.getInstance().isQueueActive(getApplication()));
                            } else {
                                errorCount.incrementAndGet();
                                onError.accept(new EmptyResultException(getApplication().getString(R.string.stream_canceled)));
                            }
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnComplete(() -> {
                            if (Preferences.isQueueAutostart())
                                ContentQueueManager.getInstance().resumeQueue(getApplication());
                            onSuccess.accept(contentList.size() - errorCount.get());
                        })
                        .subscribe(
                                v -> { // Nothing; feedback is done through LiveData
                                },
                                onError::accept
                        )
        );
    }

    public void downloadContent(
            @NonNull final List<Content> contentList,
            int position,
            @NonNull final Consumer<Integer> onSuccess,
            @NonNull final Consumer<Throwable> onError) {
        // Flag the content as "being deleted" (triggers blink animation)
        for (Content c : contentList) flagContentDelete(c, true);

        AtomicInteger nbErrors = new AtomicInteger(0);
        compositeDisposable.add(
                Observable.fromIterable(contentList)
                        .observeOn(Schedulers.io())
                        .map(c -> {
                            // Reparse content from scratch if images KO
                            if (!ContentHelper.isDownloadable(c)) {
                                Timber.d("Pages unreachable; reparsing content");
                                // Reparse content itself
                                Pair<Content, Optional<Content>> newContent = ContentHelper.reparseFromScratch(c);
                                if (newContent.getRight().isEmpty()) flagContentDelete(c, false);
                                return newContent.getRight();
                            }
                            return Optional.of(c);
                        })
                        .doOnNext(c -> {
                            if (c.isPresent()) {
                                c.get().setDownloadMode(Content.DownloadMode.DOWNLOAD);
                                dao.addContentToQueue(
                                        c.get(), StatusContent.SAVED, position, -1,
                                        ContentQueueManager.getInstance().isQueueActive(getApplication()));
                            } else {
                                nbErrors.incrementAndGet();
                                onError.accept(new EmptyResultException(getApplication().getString(R.string.download_canceled)));
                            }
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnComplete(() -> {
                            if (Preferences.isQueueAutostart())
                                ContentQueueManager.getInstance().resumeQueue(getApplication());
                            onSuccess.accept(contentList.size() - nbErrors.get());
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                v -> { // Nothing; feedback is done through LiveData
                                },
                                onError::accept
                        )
        );
    }

    public void streamContent(@NonNull final List<Content> contentList,
                              @NonNull final Consumer<Throwable> onError) {

        // Flag the content as "being deleted" (triggers blink animation)
        for (Content c : contentList) flagContentDelete(c, true);

        compositeDisposable.add(
                Observable.fromIterable(contentList)
                        .observeOn(Schedulers.io())
                        .map(c -> {
                            Timber.d("Checking pages availability");
                            // Reparse content from scratch if images KO
                            if (!ContentHelper.isDownloadable(c)) {
                                Timber.d("Pages unreachable; reparsing content");
                                // Reparse content itself
                                Pair<Content, Optional<Content>> newContent = ContentHelper.reparseFromScratch(c);
                                if (newContent.getRight().isEmpty()) {
                                    flagContentDelete(c, false);
                                    return newContent.getRight();
                                } else {
                                    Content reparsedContent = newContent.getRight().get();
                                    // Reparse pages
                                    List<ImageFile> newImages = ContentHelper.fetchImageURLs(reparsedContent, StatusContent.ONLINE);
                                    reparsedContent.setImageFiles(newImages);
                                    // Associate new pages' cover with current cover file (that won't be deleted)
                                    reparsedContent.getCover().setStatus(StatusContent.DOWNLOADED).setFileUri(c.getCover().getFileUri());
                                    // Save everything
                                    dao.replaceImageList(reparsedContent.getId(), newImages);
                                    return Optional.of(reparsedContent);
                                }
                            }
                            return Optional.of(c);
                        })
                        .doOnNext(c -> {
                            if (c.isPresent()) {
                                Content dbContent = dao.selectContent(c.get().getId());
                                if (null == dbContent) return;
                                // Non-blocking performance bottleneck; scheduled in a dedicated worker
                                purgeItem(c.get(), true);
                                dbContent.setDownloadMode(Content.DownloadMode.STREAM);
                                List<ImageFile> imgs = dbContent.getImageFiles();
                                if (imgs != null) {
                                    for (ImageFile img : imgs) {
                                        img.setFileUri("");
                                        img.setSize(0);
                                        img.setStatus(StatusContent.ONLINE);
                                    }
                                    dao.insertImageFiles(imgs);
                                }
                                dbContent.forceSize(0);
                                dbContent.setIsBeingDeleted(false);
                                dao.insertContent(dbContent);
                                ContentHelper.updateJson(getApplication(), dbContent);
                            } else {
                                onError.accept(new EmptyResultException(getApplication().getString(R.string.stream_canceled)));
                            }
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                v -> { // Nothing; feedback is done through LiveData
                                },
                                onError::accept
                        )
        );
    }

    /**
     * Set the "being deleted" flag of the given content
     *
     * @param content Content whose flag to set
     * @param flag    Value of the flag to be set
     */
    public void flagContentDelete(@NonNull final Content content, boolean flag) {
        content.setIsBeingDeleted(flag);
        dao.insertContent(content);
    }

    /**
     * Delete the given list of content
     *
     * @param contents List of content to be deleted
     */
    public void deleteItems(
            @NonNull final List<Content> contents,
            @NonNull final List<Group> groups,
            boolean deleteGroupsOnly,
            Runnable onSuccess) {
        DeleteData.Builder builder = new DeleteData.Builder();
        if (!contents.isEmpty())
            builder.setContentIds(Stream.of(contents).map(Content::getId).toList());
        if (!groups.isEmpty())
            builder.setGroupIds(Stream.of(groups).map(Group::getId).toList());
        builder.setDeleteGroupsOnly(deleteGroupsOnly);

        WorkManager workManager = WorkManager.getInstance(getApplication());
        WorkRequest request = new OneTimeWorkRequest.Builder(DeleteWorker.class).setInputData(builder.getData()).build();
        workManager.enqueue(request);

        Observer<WorkInfo> workInfoObserver = workInfo -> {
            if (workInfo.getState().isFinished()) {
                if (onSuccess != null) onSuccess.run();
                refreshCustomGroupingAvailable();
            }
        };

        workObservers.add(new ImmutablePair<>(request.getId(), workInfoObserver));
        workManager.getWorkInfoByIdLiveData(request.getId()).observeForever(workInfoObserver);
    }

    public void purgeItem(@NonNull final Content content, boolean keepCover) {
        DeleteData.Builder builder = new DeleteData.Builder();
        builder.setContentPurgeIds(Stream.of(content).map(Content::getId).toList());
        builder.setContentPurgeKeepCovers(keepCover);

        WorkManager workManager = WorkManager.getInstance(getApplication());
        workManager.enqueueUniqueWork(
                Integer.toString(R.id.delete_service_purge),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                new OneTimeWorkRequest.Builder(PurgeWorker.class).setInputData(builder.getData()).build()
        );
    }

    public void archiveContents(@NonNull final List<Content> contentList, Consumer<
            Content> onProgress, Runnable onSuccess, Consumer<Throwable> onError) {
        Timber.d("Building file list for %s books", contentList.size());

        compositeDisposable.add(
                Observable.fromIterable(contentList)
                        .observeOn(Schedulers.io())
                        .map(this::doArchiveContent)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                onProgress::accept,
                                onError::accept,
                                onSuccess::run
                        )
        );
    }

    /**
     * Archive the given Content into a ZIP file located into the device's 'Download' folder
     *
     * @param content Content to be archived
     */
    public Content doArchiveContent(@NonNull final Content content) throws IOException {
        Helper.assertNonUiThread();
        Timber.i(">> archive %s", content.getTitle());
        DocumentFile bookFolder = FileHelper.getFolderFromTreeUriString(getApplication(), content.getStorageUri());
        if (null == bookFolder) return null;

        List<DocumentFile> files = FileHelper.listFiles(getApplication(), bookFolder, null); // Everything (incl. JSON and thumb) gets into the archive
        if (!files.isEmpty()) {
            // Build destination file
            ImmutablePair<String, String> bookFolderName = ContentHelper.formatBookFolderName(content);
            // First try creating the file with the new naming...
            String destName = bookFolderName.left + ".zip";
            OutputStream destFile = null;
            try {
                try {
                    destFile = FileHelper.openNewDownloadOutputStream(getApplication(), destName, ArchiveHelper.ZIP_MIME_TYPE);
                } catch (IOException e) { // ...if it fails, try creating the file with the old sanitized naming
                    destName = bookFolderName.right + ".zip";
                    destFile = FileHelper.openNewDownloadOutputStream(getApplication(), destName, ArchiveHelper.ZIP_MIME_TYPE);
                }
                Timber.d("Destination file: %s", destName);
                ArchiveHelper.zipFiles(getApplication(), files, destFile);
            } finally {
                if (destFile != null) destFile.close();
            }
            return content;
        }
        return null;
    }

    public void setGroupCoverContent(long groupId, @NonNull Content coverContent) {
        Group localGroup = dao.selectGroup(groupId);
        if (localGroup != null) localGroup.coverContent.setAndPutTarget(coverContent);
    }

    public void saveContentPositions(@NonNull final List<Content> orderedContent,
                                     @NonNull final Runnable onSuccess) {
        compositeDisposable.add(
                Completable.fromRunnable(() -> doSaveContentPositions(orderedContent))
                        .subscribeOn(Schedulers.io())
                        .observeOn(Schedulers.io())
                        .doOnComplete(() -> GroupHelper.updateGroupsJson(getApplication(), dao))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                onSuccess::run,
                                Timber::e
                        )
        );
    }

    private void doSaveContentPositions(@NonNull final List<Content> orderedContent) {
        Group localGroup = getGroup().getValue();
        if (null == localGroup) return;

        // Update the "has custom book order" group flag
        localGroup.hasCustomBookOrder = true;

        int order = 0;
        for (Content c : orderedContent)
            for (GroupItem gi : localGroup.items)
                if (gi.content.getTargetId() == c.getId()) {
                    gi.order = order++;
                    dao.insertGroupItem(gi);
                    break;
                }
        dao.insertGroup(localGroup);
    }

    public void saveGroupPositions(@NonNull final List<Group> orderedGroups) {
        compositeDisposable.add(
                Completable.fromRunnable(() -> doSaveGroupPositions(orderedGroups))
                        .subscribeOn(Schedulers.io())
                        .observeOn(Schedulers.io())
                        .doOnComplete(() -> GroupHelper.updateGroupsJson(getApplication(), dao))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> { // Update is done through LiveData
                                },
                                Timber::e
                        )
        );
    }

    private void doSaveGroupPositions(@NonNull final List<Group> orderedGroups) {
        int order = 0;
        for (Group g : orderedGroups) {
            g.order = order++;
            dao.insertGroup(g);
        }
    }

    public List<Content> getGroupContents(@NonNull Group group) {
        return dao.selectContent(Helper.getPrimitiveArrayFromList(group.getContentIds()));
    }

    public void newGroup(@NonNull final Grouping grouping, @NonNull final String newGroupName,
                         @NonNull final Runnable onNameExists) {
        // Check if the group already exists
        List<Group> localGroups = getGroups().getValue();
        if (null == localGroups) return;

        List<Group> groupMatchingName = Stream.of(localGroups).filter(g -> g.name.equalsIgnoreCase(newGroupName)).toList();
        if (!groupMatchingName.isEmpty()) { // Existing group with the same name
            onNameExists.run();
        } else {
            compositeDisposable.add(
                    Completable.fromRunnable(() -> dao.insertGroup(new Group(grouping, newGroupName, -1)))
                            .subscribeOn(Schedulers.io())
                            .observeOn(Schedulers.io())
                            .doOnComplete(() -> {
                                refreshCustomGroupingAvailable();
                                GroupHelper.updateGroupsJson(getApplication(), dao);
                            })
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    () -> { // Update is done through LiveData
                                    },
                                    Timber::e
                            )
            );
        }
    }

    public void renameGroup(@NonNull final Group group, @NonNull final String newGroupName,
                            @NonNull final Consumer<Integer> onFail, @NonNull final Runnable onSuccess) {
        // Check if the group already exists
        List<Group> localGroups = getGroups().getValue();
        if (null == localGroups) return;

        List<Group> groupMatchingName = Stream.of(localGroups).filter(g -> g.name.equalsIgnoreCase(newGroupName)).toList();
        if (!groupMatchingName.isEmpty()) { // Existing group with the same name
            onFail.accept(R.string.group_name_exists);
        } else if (group.grouping.equals(Grouping.CUSTOM) && 1 == group.getSubtype()) { // "Ungrouped" group can't be renamed because it stops to work (TODO investgate that)
            onFail.accept(R.string.group_rename_forbidden);
        } else {
            group.name = newGroupName;
            compositeDisposable.add(
                    Completable.fromRunnable(() -> dao.insertGroup(group))
                            .subscribeOn(Schedulers.io())
                            .observeOn(Schedulers.io())
                            .doOnComplete(() -> {
                                refreshCustomGroupingAvailable();
                                GroupHelper.updateGroupsJson(getApplication(), dao);
                            })
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    onSuccess::run,
                                    Timber::e
                            )
            );
        }
    }

    /**
     * Toggle the "favourite" state of the given group
     *
     * @param group Group whose favourite state to toggle
     */
    public void toggleGroupFavourite(@NonNull final Group group) {
        if (group.isBeingDeleted()) return;

        compositeDisposable.add(
                Single.fromCallable(() -> doToggleGroupFavourite(group.id))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                v -> {
                                    // Updated through LiveData
                                },
                                Timber::e
                        )
        );
    }

    /**
     * Toggle the "favourite" state of the given group
     *
     * @param groupId ID of the group whose favourite state to toggle
     * @return Resulting group
     */
    private Group doToggleGroupFavourite(long groupId) {
        Helper.assertNonUiThread();

        // Check if given group still exists in DB
        Group theGroup = dao.selectGroup(groupId);

        if (theGroup != null) {
            theGroup.setFavourite(!theGroup.isFavourite());

            // Persist in it JSON
            GroupHelper.updateGroupsJson(getApplication(), dao);

            // Persist in it DB
            dao.insertGroup(theGroup);

            return theGroup;
        }

        throw new InvalidParameterException("Invalid GroupId : " + groupId);
    }

    public void moveContentsToNewCustomGroup(long[] contentIds, String newGroupName,
                                             @NonNull final Runnable onSuccess) {
        Group newGroup = new Group(Grouping.CUSTOM, newGroupName.trim(), -1);
        newGroup.id = dao.insertGroup(newGroup);
        moveContentsToCustomGroup(contentIds, newGroup, onSuccess);
    }

    public void moveContentsToCustomGroup(long[] contentIds, @Nullable final Group group,
                                          @NonNull final Runnable onSuccess) {
        compositeDisposable.add(
                Observable.fromIterable(Helper.getListFromPrimitiveArray(contentIds))
                        .observeOn(Schedulers.io())
                        .map(dao::selectContent)
                        .map(c -> moveContentToCustomGroup(c, group, dao))
                        .doOnNext(c -> ContentHelper.updateJson(getApplication(), c))
                        .doOnComplete(() -> {
                            refreshCustomGroupingAvailable();
                            GroupHelper.updateGroupsJson(getApplication(), dao);
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                v -> onSuccess.run(),
                                Timber::e
                        )
        );
    }

    public void shuffleContent() {
        RandomSeedSingleton.getInstance().renewSeed(Consts.SEED_CONTENT);
        dao.shuffleContent();
    }

    public void renameContent(@NonNull Content content, @NonNull String title) {
        compositeDisposable.add(
                Completable.fromRunnable(() -> doRenameContent(content, title))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> {
                                    // Updated through LiveData
                                },
                                Timber::e
                        )
        );
    }

    private void doRenameContent(@NonNull Content content, @NonNull String title) {
        Helper.assertNonUiThread();
        Content dbContent = dao.selectContent(content.getId()); // Instanciate a new Content from DB to avoid updating the UI reference
        if (dbContent != null) {
            dbContent.setTitle(title);
            ContentHelper.persistJson(getApplication(), dbContent);
            dao.insertContent(dbContent);
        }
    }

    public void mergeContents(
            @NonNull List<Content> contentList,
            @NonNull String newTitle,
            boolean deleteAfterMerging,
            @NonNull Runnable onSuccess) {
        if (contentList.isEmpty()) return;

        // Flag the content as "being deleted" (triggers blink animation)
        if (deleteAfterMerging) for (Content c : contentList) flagContentDelete(c, true);

        compositeDisposable.add(
                Single.fromCallable(() -> {
                            ContentHelper.mergeContents(getApplication(), contentList, newTitle, dao);
                            return true;
                        })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .map(b -> {
                                    if (deleteAfterMerging)
                                        deleteItems(contentList, Collections.emptyList(), false, null);
                                    return true;
                                }
                        )
                        .subscribe(
                                b -> onSuccess.run(),
                                t -> {
                                    Timber.e(t);
                                    if (deleteAfterMerging)
                                        for (Content c : contentList) flagContentDelete(c, false);
                                }
                        )
        );
    }

    public void splitContent(
            @NonNull Content content,
            @NonNull List<Chapter> chapters,
            @NonNull Runnable onSuccess) {
        compositeDisposable.add(
                Single.fromCallable(() -> {
                            boolean result = false;
                            try {
                                doSplitContent(content, chapters);
                                result = true;
                            } catch (ContentNotProcessedException e) {
                                Timber.e(e);
                            }
                            return result;
                        })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                b -> {
                                    if (b) onSuccess.run();
                                },
                                Timber::e
                        )
        );
    }

    private void doSplitContent(@NonNull Content content, @NonNull List<Chapter> chapters) throws Exception {
        Helper.assertNonUiThread();
        List<ImageFile> images = content.getImageFiles();
        if (chapters.isEmpty())
            throw new ContentNotProcessedException(content, "No chapters detected");
        if (null == images || images.isEmpty())
            throw new ContentNotProcessedException(content, "No images detected");

        int nbProcessedPics = 0;
        int nbImages = (int) Stream.of(chapters).flatMap(c -> Stream.of(c.getImageFiles())).filter(ImageFile::isReadable).count();
        for (Chapter chap : chapters) {
            Content splitContent = createContentFromChapter(content, chap);

            // Create a new folder for the split content
            DocumentFile targetFolder = ContentHelper.getOrCreateContentDownloadDir(getApplication(), splitContent);
            if (null == targetFolder || !targetFolder.exists())
                throw new ContentNotProcessedException(splitContent, "Could not create target directory");

            splitContent.setStorageUri(targetFolder.getUri().toString());

            // Copy the corresponding images to that folder
            List<ImageFile> splitContentImages = splitContent.getImageFiles();
            if (null == splitContentImages)
                throw new ContentNotProcessedException(splitContent, "No images detected in generated book");

            for (ImageFile img : splitContentImages) {
                if (img.getStatus().equals(StatusContent.DOWNLOADED)) {
                    String extension = HttpHelper.getExtensionFromUri(img.getFileUri());
                    Uri newUri = FileHelper.copyFile(
                            getApplication(),
                            Uri.parse(img.getFileUri()),
                            targetFolder.getUri(),
                            img.getMimeType(),
                            img.getName() + "." + extension);
                    if (newUri != null)
                        img.setFileUri(newUri.toString());
                    else
                        Timber.w("Could not move file %s", img.getFileUri());
                    EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, R.id.generic_progress, 0, nbProcessedPics++, 0, nbImages));
                }
            }

            // Save the JSON for the new book
            DocumentFile jsonFile = ContentHelper.createJson(getApplication(), splitContent);
            if (jsonFile != null) splitContent.setJsonUri(jsonFile.getUri().toString());

            // Save new content (incl. onn-custom group operations)
            ContentHelper.addContent(getApplication(), dao, splitContent);

            // Set custom group, if any
            List<GroupItem> customGroups = content.getGroupItems(Grouping.CUSTOM);
            if (!customGroups.isEmpty())
                GroupHelper.moveContentToCustomGroup(splitContent, customGroups.get(0).getGroup(), dao);
        }

        EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, R.id.generic_progress, 0, nbImages, 0, nbImages));
    }

    private Content createContentFromChapter(@NonNull Content content, @NonNull Chapter chapter) {
        Content splitContent = new Content();

        String url = StringHelper.protect(chapter.getUrl());
        if (url.isEmpty()) { // Default (e.g. manually created chapters)
            url = content.getUrl();
            splitContent.setSite(content.getSite());
        } else { // Detect site and cleanup full URL (e.g. previously merged books)
            Site site = Site.searchByUrl(url);
            if (site != null && !site.equals(Site.NONE)) {
                splitContent.setSite(site);
                url = url.replace(site.getUrl(), "");
            }
        }
        splitContent.setUrl(url);

        String id = chapter.getUniqueId();
        if (id.isEmpty()) id = content.getUniqueSiteId() + "_"; // Don't create a copy of content
        splitContent.setUniqueSiteId(id);
        splitContent.setDownloadMode(content.getDownloadMode());
        String newTitle = content.getTitle();
        if (!newTitle.contains(chapter.getName()))
            newTitle += " - " + chapter.getName(); // Avoid swelling the title after multiple merges and splits
        splitContent.setTitle(newTitle);
        splitContent.setUploadDate(content.getUploadDate());
        splitContent.setDownloadDate(Instant.now().toEpochMilli());
        splitContent.setStatus(content.getStatus());
        splitContent.setBookPreferences(content.getBookPreferences());

        List<ImageFile> images = chapter.getImageFiles();
        if (images != null) {
            images = Stream.of(chapter.getImageFiles()).sortBy(ImageFile::getOrder).toList();
            int position = 0;
            int nbMaxDigits = (int) (Math.floor(Math.log10(images.size()) + 1));
            for (ImageFile img : images) {
                img.setId(0); // Force working on a new picture
                img.setChapter(null);
                img.getContent().setTarget(null); // Clear content
                img.setIsCover(0 == position);
                img.setOrder(position++);
                img.computeName(nbMaxDigits);
            }

            splitContent.setImageFiles(images);
            splitContent.setChapters(null);
            splitContent.setQtyPages((int) Stream.of(images).filter(ImageFile::isReadable).count());
            splitContent.computeSize();

            String coverImageUrl = StringHelper.protect(images.get(0).getUrl());
            if (coverImageUrl.isEmpty()) coverImageUrl = content.getCoverImageUrl();
            splitContent.setCoverImageUrl(coverImageUrl);
        }

        List<Attribute> splitAttributes = Stream.of(content).flatMap(c -> Stream.of(c.getAttributes())).toList();
        splitContent.addAttributes(splitAttributes);

        return splitContent;
    }
}
