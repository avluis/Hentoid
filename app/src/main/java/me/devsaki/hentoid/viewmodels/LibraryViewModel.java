package me.devsaki.hentoid.viewmodels;

import static me.devsaki.hentoid.util.GroupHelper.moveContentToCustomGroup;

import android.annotation.TargetApi;
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
import androidx.paging.PagedList;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.greenrobot.eventbus.EventBus;
import org.threeten.bp.Instant;

import java.io.IOException;
import java.io.OutputStream;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import io.objectbox.relation.ToOne;
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
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.util.ArchiveHelper;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.GroupHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.RandomSeedSingleton;
import me.devsaki.hentoid.util.download.ContentQueueManager;
import me.devsaki.hentoid.util.exception.ContentNotProcessedException;
import me.devsaki.hentoid.util.exception.EmptyResultException;
import me.devsaki.hentoid.util.network.HttpHelper;
import me.devsaki.hentoid.widget.ContentSearchManager;
import me.devsaki.hentoid.workers.DeleteWorker;
import me.devsaki.hentoid.workers.PurgeWorker;
import me.devsaki.hentoid.workers.data.DeleteData;
import timber.log.Timber;


public class LibraryViewModel extends AndroidViewModel {

    // Collection DAO
    private final CollectionDAO dao;
    // Library search manager
    private final ContentSearchManager searchManager;
    // Cleanup for all RxJava calls
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    // Collection data
    private LiveData<PagedList<Content>> currentSource;
    private final LiveData<Integer> totalContent;
    private LiveData<Integer> currentGroupCountSource;
    private final MediatorLiveData<Integer> totalGroups = new MediatorLiveData<>();
    private final MediatorLiveData<PagedList<Content>> libraryPaged = new MediatorLiveData<>();
    // Groups data
    private final MutableLiveData<Group> group = new MutableLiveData<>();
    private LiveData<List<Group>> currentGroupsSource;
    private final MediatorLiveData<List<Group>> groups = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> isCustomGroupingAvailable = new MutableLiveData<>();     // True if there's at least one existing custom group; false instead

    // Updated whenever a new search is performed
    private final MediatorLiveData<Boolean> newSearch = new MediatorLiveData<>();


    public LibraryViewModel(@NonNull Application application, @NonNull CollectionDAO collectionDAO) {
        super(application);
        dao = collectionDAO;
        searchManager = new ContentSearchManager(dao);
        totalContent = dao.countAllBooks();
        refreshCustomGroupingAvailable();
    }

    public void onSaveState(Bundle outState) {
        searchManager.saveToBundle(outState);
    }

    public void onRestoreState(@Nullable Bundle savedState) {
        if (savedState == null) return;
        searchManager.loadFromBundle(savedState);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        dao.cleanup();
        compositeDisposable.clear();
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
    public LiveData<Boolean> getNewSearch() {
        return newSearch;
    }

    @NonNull
    public LiveData<Group> getGroup() {
        return group;
    }

    @NonNull
    public LiveData<Integer> getTotalGroup() {
        return totalGroups;
    }

    @NonNull
    public LiveData<Boolean> isCustomGroupingAvailable() {
        return isCustomGroupingAvailable;
    }

    public Bundle getSearchManagerBundle() {
        Bundle bundle = new Bundle();
        searchManager.saveToBundle(bundle);
        return bundle;
    }

    // =========================
    // ========= LIBRARY ACTIONS
    // =========================

    /**
     * Perform a new library search
     */
    private void doSearchContent() {
        if (currentSource != null) libraryPaged.removeSource(currentSource);

        searchManager.setContentSortField(Preferences.getContentSortField());
        searchManager.setContentSortDesc(Preferences.isContentSortDesc());

        currentSource = searchManager.getLibrary();

        libraryPaged.addSource(currentSource, libraryPaged::setValue);
    }

    /**
     * Perform a new content universal search using the given query
     *
     * @param query Query to use for the universal search
     */
    public void searchContentUniversal(@NonNull String query) {
        searchManager.clearSelectedSearchTags(); // If user searches in main toolbar, universal search takes over advanced search
        searchManager.setQuery(query);
        newSearch.setValue(true);
        doSearchContent();
    }

    /**
     * Perform a new content search using the given query and metadata
     *
     * @param query    Query to use for the search
     * @param metadata Metadata to use for the search
     */
    public void searchContent(@NonNull String query, @NonNull List<Attribute> metadata) {
        searchManager.setQuery(query);
        searchManager.setTags(metadata);
        newSearch.setValue(true);
        doSearchContent();
    }

    public void clearContent() {
        if (currentSource != null) {
            libraryPaged.removeSource(currentSource);
            currentSource = dao.selectNoContent();
            libraryPaged.addSource(currentSource, libraryPaged::setValue);
        }
    }

    /**
     * Perform a new group search using the given query
     *
     * @param query Query to use for the search
     */
    public void searchGroup(Grouping grouping, @NonNull String query, int orderField, boolean orderDesc, int artistGroupVisibility, boolean groupFavouritesOnly) {
        if (currentGroupsSource != null) groups.removeSource(currentGroupsSource);
        currentGroupsSource = dao.selectGroups(grouping.getId(), query, orderField, orderDesc, artistGroupVisibility, groupFavouritesOnly);
        groups.addSource(currentGroupsSource, groups::setValue);
    }

    /**
     * Toggle the completed filter
     */
    public void toggleCompletedFilter() {
        searchManager.setFilterBookCompleted(!searchManager.isFilterBookCompleted());
        newSearch.setValue(true);
        doSearchContent();
    }

    /**
     * Toggle the completed filter
     */
    public void toggleNotCompletedFilter() {
        searchManager.setFilterBookNotCompleted(!searchManager.isFilterBookNotCompleted());
        newSearch.setValue(true);
        doSearchContent();
    }

    /**
     * Toggle the books favourite filter
     */
    public void setContentFavouriteFilter(boolean value) {
        searchManager.setFilterBookFavourites(value);
        newSearch.setValue(true);
        doSearchContent();
    }

    /**
     * Set the mode (endless or paged)
     */
    public void setPagingMethod(boolean isEndless) {
        searchManager.setLoadAll(!isEndless);
        newSearch.setValue(true);
        doSearchContent();
    }

    /**
     * Update the order of the content list
     */
    public void updateContentOrder() {
        newSearch.setValue(true);
        doSearchContent();
    }

    public void setGroup(Group group) {
        searchManager.setGroup(group);
        this.group.postValue(group);
        newSearch.setValue(true);
        // Don't search now as the UI will inevitably search as well upon switching to books view
        // TODO only useful when browsing custom groups ?
        doSearchContent();
    }

    public void setGrouping(Grouping grouping, int orderField, boolean orderDesc, int artistGroupVisibility, boolean groupFavouritesOnly) {
        if (grouping.equals(Grouping.FLAT)) {
            setGroup(null);
            return;
        }

        if (currentGroupsSource != null) groups.removeSource(currentGroupsSource);
        currentGroupsSource = dao.selectGroups(grouping.getId(), null, orderField, orderDesc, artistGroupVisibility, groupFavouritesOnly);
        groups.addSource(currentGroupsSource, groups::setValue);

        if (currentGroupCountSource != null) totalGroups.removeSource(currentGroupCountSource);
        currentGroupCountSource = dao.countLiveGroupsFor(grouping);
        totalGroups.addSource(currentGroupCountSource, totalGroups::setValue);
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

            // Persist in it JSON
            if (!theContent.getJsonUri().isEmpty()) // Having an active Content without JSON file shouldn't be possible after the API29 migration
                ContentHelper.updateContentJson(getApplication(), theContent);
            else ContentHelper.createContentJson(getApplication(), theContent);

            // Persist in it DB
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

            // Persist in it JSON
            if (!theContent.getJsonUri().isEmpty()) // Having an active Content without JSON file shouldn't be possible after the API29 migration
                ContentHelper.updateContentJson(getApplication(), theContent);
            else ContentHelper.createContentJson(getApplication(), theContent);

            // Persist in it DB
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
                        .map(c -> (reparseContent) ? ContentHelper.reparseFromScratch(c) : Optional.of(c))
                        .doOnNext(c -> {
                            if (c.isPresent()) {
                                Content content = c.get();
                                // Non-blocking performance bottleneck; run in a dedicated worker
                                // TODO if the purge is extremely long, that worker might still be working while downloads are happening on these same books
                                if (reparseImages) purgeItem(content);
                                dao.addContentToQueue(
                                        content, targetImageStatus, position,
                                        ContentQueueManager.getInstance().isQueueActive());
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
                                Optional<Content> newContent = ContentHelper.reparseFromScratch(c);
                                if (newContent.isEmpty()) flagContentDelete(c, false);
                                return newContent;
                            }
                            return Optional.of(c);
                        })
                        .doOnNext(c -> {
                            if (c.isPresent()) {
                                c.get().setDownloadMode(Content.DownloadMode.DOWNLOAD);
                                dao.addContentToQueue(
                                        c.get(), StatusContent.SAVED, position,
                                        ContentQueueManager.getInstance().isQueueActive());
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
                                Optional<Content> newContent = ContentHelper.reparseFromScratch(c);
                                if (newContent.isEmpty()) {
                                    flagContentDelete(c, false);
                                    return newContent;
                                } else {
                                    Content reparsedContent = newContent.get();
                                    // Reparse pages
                                    List<ImageFile> newImages = ContentHelper.fetchImageURLs(reparsedContent, StatusContent.ONLINE);
                                    dao.replaceImageList(reparsedContent.getId(), newImages);
                                    return Optional.of(reparsedContent.setImageFiles(newImages));
                                }
                            }
                            return Optional.of(c);
                        })
                        .doOnNext(c -> {
                            if (c.isPresent()) {
                                Content dbContent = dao.selectContent(c.get().getId());
                                if (null == dbContent) return;
                                // Non-blocking performance bottleneck; scheduled in a dedicated worker
                                purgeItem(c.get());
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
                                ContentHelper.updateContentJson(getApplication(), dbContent);
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
            boolean deleteGroupsOnly) {
        DeleteData.Builder builder = new DeleteData.Builder();
        if (!contents.isEmpty())
            builder.setContentIds(Stream.of(contents).map(Content::getId).toList());
        if (!groups.isEmpty())
            builder.setGroupIds(Stream.of(groups).map(Group::getId).toList());
        builder.setDeleteGroupsOnly(deleteGroupsOnly);

        WorkManager workManager = WorkManager.getInstance(getApplication());
        workManager.enqueue(new OneTimeWorkRequest.Builder(DeleteWorker.class).setInputData(builder.getData()).build());
    }

    public void purgeItem(@NonNull final Content content) {
        DeleteData.Builder builder = new DeleteData.Builder();
        builder.setContentPurgeIds(Stream.of(content).map(Content::getId).toList());

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

    public void setGroupCover(long groupId, ImageFile cover) {
        Group localGroup = dao.selectGroup(groupId);
        if (localGroup != null) localGroup.picture.setAndPutTarget(cover);
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
                            @NonNull final Runnable onNameExists) {
        // Check if the group already exists
        List<Group> localGroups = getGroups().getValue();
        if (null == localGroups) return;

        List<Group> groupMatchingName = Stream.of(localGroups).filter(g -> g.name.equalsIgnoreCase(newGroupName)).toList();
        if (!groupMatchingName.isEmpty()) { // Existing group with the same name
            onNameExists.run();
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
                                    () -> { // Update is done through LiveData
                                    },
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
                        .doOnNext(c -> ContentHelper.updateContentJson(getApplication(), c))
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

    public void refreshCustomGroupingAvailable() {
        isCustomGroupingAvailable.postValue(dao.countGroupsFor(Grouping.CUSTOM) > 0);
    }

    public void resetCompletedFilter() {
        if (searchManager.isFilterBookCompleted())
            toggleCompletedFilter();
        else if (searchManager.isFilterBookNotCompleted())
            toggleNotCompletedFilter();
    }

    public void shuffleContent() {
        RandomSeedSingleton.getInstance().renewSeed(Consts.SEED_CONTENT);
        dao.shuffleContent();
    }

    @TargetApi(24)
    public void mergeContents(
            @NonNull List<Content> contentList,
            @NonNull String newTitle,
            @NonNull Runnable onSuccess) {
        if (0 == contentList.size()) return;

        // Flag the content as "being deleted" (triggers blink animation)
        for (Content c : contentList) flagContentDelete(c, true);

        compositeDisposable.add(
                Single.fromCallable(() -> {
                    boolean result = false;
                    try {
                        doMergeContents(contentList, newTitle);
                        result = true;
                    } catch (ContentNotProcessedException e) {
                        Timber.e(e);
                        for (Content c : contentList) flagContentDelete(c, false);
                    }
                    return result;
                })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                b -> {
                                    if (b) onSuccess.run();
                                },
                                t -> {
                                    Timber.e(t);
                                    for (Content c : contentList) flagContentDelete(c, false);
                                }
                        )
        );
    }

    private void doMergeContents(@NonNull List<Content> contentList, @NonNull String newTitle) throws ContentNotProcessedException {
        Helper.assertNonUiThread();

        Content firstContent = contentList.get(0);

        // Initiate a new Content
        Content mergedContent = new Content();
        mergedContent.setSite(firstContent.getSite());
        mergedContent.setUrl(firstContent.getUrl());
        mergedContent.setUniqueSiteId(firstContent.getUniqueSiteId() + "_"); // Not to create a copy of firstContent
        mergedContent.setDownloadMode(firstContent.getDownloadMode());
        mergedContent.setTitle(newTitle);
        mergedContent.setCoverImageUrl(firstContent.getCoverImageUrl());
        mergedContent.setUploadDate(firstContent.getUploadDate());
        mergedContent.setDownloadDate(Instant.now().toEpochMilli());
        mergedContent.setStatus(firstContent.getStatus());
        mergedContent.setFavourite(firstContent.isFavourite());
        mergedContent.setBookPreferences(firstContent.getBookPreferences());

        // Merge attributes
        List<Attribute> mergedAttributes = Stream.of(contentList).flatMap(c -> Stream.of(c.getAttributes())).toList();
        mergedContent.addAttributes(mergedAttributes);

        // Create destination folder for new content
        DocumentFile targetFolder = ContentHelper.getOrCreateContentDownloadDir(getApplication(), mergedContent);
        if (null == targetFolder || !targetFolder.exists())
            throw new ContentNotProcessedException(mergedContent, "Could not create target directory");

        mergedContent.setStorageUri(targetFolder.getUri().toString());

        // Renumber all picture files and dispatch chapters
        long nbImages = Stream.of(contentList).flatMap(c -> Stream.of(c.getImageFiles())).filter(ImageFile::isReadable).count();
        int nbMaxDigits = (int) (Math.floor(Math.log10(nbImages)) + 1);

        List<ImageFile> mergedImages = new ArrayList<>();
        List<Chapter> mergedChapters = new ArrayList<>();

        // Set cover
        ImageFile firstCover = firstContent.getCover();
        ImageFile coverPic = ImageFile.newCover(firstCover.getUrl(), firstCover.getStatus());
        boolean isError = false;
        try {
            if (coverPic.getStatus().equals(StatusContent.DOWNLOADED)) {
                String extension = HttpHelper.getExtensionFromUri(firstCover.getFileUri());
                Uri newUri = FileHelper.moveFileAsync(
                        getApplication(),
                        Uri.parse(firstCover.getFileUri()),
                        targetFolder.getUri(),
                        firstCover.getMimeType(),
                        firstCover.getName() + "." + extension,
                        compositeDisposable);
                if (newUri != null)
                    coverPic.setFileUri(newUri.toString());
                else
                    Timber.w("Could not move file %s", firstCover.getFileUri());
            }
            mergedImages.add(coverPic);

            int chapterOrder = 0;
            int pictureOrder = 1;
            int nbProcessedPics = 1;
            for (Content c : contentList) {
                if (null == c.getImageFiles()) continue;
                Chapter contentChapter = new Chapter(chapterOrder++, c.getGalleryUrl(), c.getTitle());
                for (ImageFile img : c.getImageFiles()) {
                    if (!img.isReadable()) continue;
                    ImageFile newImg = new ImageFile(img);
                    newImg.setId(0); // Force working on a new picture
                    newImg.getContent().setTarget(null); // Clear content
                    newImg.setOrder(pictureOrder++);
                    newImg.setName(String.format(Locale.ENGLISH, "%0" + nbMaxDigits + "d", newImg.getOrder()));
                    ToOne<Chapter> chapLink = newImg.getChapter();
                    Chapter newChapter;
                    if (null == chapLink || !chapLink.isResolvedAndNotNull()) { // No chapter -> set content chapter
                        newChapter = contentChapter;
                    } else {
                        newChapter = Chapter.fromChapter(chapLink.getTarget()).setOrder(chapterOrder++);
                    }
                    if (!mergedChapters.contains(newChapter)) mergedChapters.add(newChapter);
                    newImg.setChapter(newChapter);
                    // TODO test merging books with existing chapters

                    // If exists, move the picture to the merged books's folder
                    if (newImg.getStatus().equals(StatusContent.DOWNLOADED)) {
                        String extension = HttpHelper.getExtensionFromUri(img.getFileUri());
                        Uri newUri = FileHelper.moveFileAsync(
                                getApplication(),
                                Uri.parse(img.getFileUri()),
                                targetFolder.getUri(),
                                newImg.getMimeType(),
                                newImg.getName() + "." + extension,
                                compositeDisposable);
                        if (newUri != null)
                            newImg.setFileUri(newUri.toString());
                        else
                            Timber.w("Could not move file %s", img.getFileUri());
                        EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, R.id.generic_progress, 0, nbProcessedPics++, 0, (int) nbImages));
                    }
                    mergedImages.add(newImg);
                }
            }
        } catch (IOException e) {
            Timber.w(e);
            isError = true;
        }

        if (!isError) {
            mergedContent.setImageFiles(mergedImages);
            mergedContent.setChapters(mergedChapters); // Chapters have to be attached to Content too
            mergedContent.setQtyPages(mergedImages.size() - 1);
            mergedContent.computeSize();

            DocumentFile jsonFile = ContentHelper.createContentJson(getApplication(), mergedContent);
            if (jsonFile != null) mergedContent.setJsonUri(jsonFile.getUri().toString());

            // Save new content (incl. group operations)
            ContentHelper.addContent(getApplication(), dao, mergedContent);
            // TODO test artist & time groups

            // Merge custom groups and update
            // Merged book can be a member of one custom group only
            Optional<Group> customGroup = Stream.of(contentList).flatMap(c -> Stream.of(c.groupItems)).map(GroupItem::getGroup).withoutNulls().distinct().filter(g -> g.grouping.equals(Grouping.CUSTOM)).findFirst();
            if (customGroup.isPresent())
                GroupHelper.moveContentToCustomGroup(mergedContent, customGroup.get(), dao);
            // TODO test custom groups

            // Remove old contents
            for (Content c : contentList) ContentHelper.removeContent(getApplication(), dao, c);
        }

        EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, R.id.generic_progress, 0, (int) nbImages, 0, (int) nbImages));
    }
}
