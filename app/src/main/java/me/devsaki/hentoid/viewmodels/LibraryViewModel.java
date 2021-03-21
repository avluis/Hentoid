package me.devsaki.hentoid.viewmodels;

import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.PagedList;

import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;

import org.apache.commons.lang3.tuple.ImmutablePair;

import java.io.IOException;
import java.io.OutputStream;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.database.domains.GroupItem;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.ArchiveHelper;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.GroupHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.download.ContentQueueManager;
import me.devsaki.hentoid.util.exception.ContentNotRemovedException;
import me.devsaki.hentoid.util.exception.GroupNotRemovedException;
import me.devsaki.hentoid.widget.ContentSearchManager;
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
        isCustomGroupingAvailable.postValue(dao.countGroupsFor(Grouping.CUSTOM) > 0);
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
     * Toggle the books favourite filter
     */
    public void toggleContentFavouriteFilter() {
        searchManager.setFilterBookFavourites(!searchManager.isFilterBookFavourites());
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

    public void redownloadContent(@NonNull final List<Content> contentList, boolean reparseContent, boolean reparseImages, @NonNull final Runnable onSuccess) {
        // Flag the content as "being deleted" (triggers blink animation)
        for (Content c : contentList) flagContentDelete(c, true);

        StatusContent targetImageStatus = reparseImages ? StatusContent.ERROR : null;

        compositeDisposable.add(
                Observable.fromIterable(contentList)
                        .observeOn(Schedulers.io())
                        .map(c -> (reparseContent) ? ContentHelper.reparseFromScratch(c) : c)
                        .map(c -> {
                            if (reparseImages) ContentHelper.purgeFiles(getApplication(), c);
                            return c;
                        })
                        .doOnNext(c -> dao.addContentToQueue(c, targetImageStatus))
                        .doOnComplete(() -> {
                            // TODO is there stuff to do on the IO thread ?
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                v -> {
                                    if (Preferences.isQueueAutostart())
                                        ContentQueueManager.getInstance().resumeQueue(getApplication());
                                    onSuccess.run();
                                },
                                Timber::e
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
     * @param onError  Callback to run when an error occurs
     */
    public void deleteItems(@NonNull final List<Content> contents, @NonNull final List<Group> groups, Consumer<Object> onProgress, Runnable onSuccess, Consumer<Throwable> onError) {
        // Flag the content as "being deleted" (triggers blink animation)
        for (Content c : contents) flagContentDelete(c, true);
        // TODO do the same blinking effect for groups ?

        // First chain contents, then groups (to be sure to delete empty groups only)
        List<Object> items = new ArrayList<>();
        items.addAll(contents);
        items.addAll(groups);

        compositeDisposable.add(
                Observable.fromIterable(items)
                        .observeOn(Schedulers.io())
                        .map(this::doDeleteItem)
                        .doOnComplete(() -> {
                            if (!groups.isEmpty()) {
                                isCustomGroupingAvailable.postValue(dao.countGroupsFor(Grouping.CUSTOM) > 0);
                                GroupHelper.updateGroupsJson(getApplication(), dao);
                            }
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                onProgress::accept,
                                onError::accept,
                                onSuccess::run
                        )
        );
    }

    private Object doDeleteItem(@NonNull final Object item) throws Exception {
        if (item instanceof Content) return doDeleteContent((Content) item);
        else if (item instanceof Group) return doDeleteGroup((Group) item);
        else return null;
    }

    /**
     * Delete the given content
     *
     * @param content Content to be deleted
     * @return Content that has been deleted
     * @throws ContentNotRemovedException When any issue occurs during removal
     */
    private Content doDeleteContent(@NonNull final Content content) throws ContentNotRemovedException {
        Helper.assertNonUiThread();
        try {
            // Check if given content still exists in DB
            Content theContent = dao.selectContent(content.getId());

            if (theContent != null) {
                ContentHelper.removeContent(getApplication(), dao, theContent);
                Timber.d("Removed item: %s from db and file system.", theContent.getTitle());
                return theContent;
            }
            throw new ContentNotRemovedException(content, "Error when trying to delete : invalid ContentId " + content.getId());
        } catch (ContentNotRemovedException cnre) {
            Timber.e(cnre, "Error when trying to delete %s", content.getId());
            throw cnre;
        } catch (Exception e) {
            Timber.e(e, "Error when trying to delete %s", content.getId());
            throw new ContentNotRemovedException(content, "Error when trying to delete " + content.getId() + " : " + e.getMessage(), e);
        }
    }

    public void archiveContents(@NonNull final List<Content> contentList, Consumer<Content> onProgress, Runnable onSuccess, Consumer<Throwable> onError) {
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

    public void saveContentPositions(@NonNull final List<Content> orderedContent, @NonNull final Runnable onSuccess) {
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

    public void newGroup(@NonNull final Grouping grouping, @NonNull final String newGroupName, @NonNull final Runnable onNameExists) {
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
                                isCustomGroupingAvailable.postValue(dao.countGroupsFor(Grouping.CUSTOM) > 0);
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

    public void renameGroup(@NonNull final Group group, @NonNull final String newGroupName, @NonNull final Runnable onNameExists) {
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
                                isCustomGroupingAvailable.postValue(dao.countGroupsFor(Grouping.CUSTOM) > 0);
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
     * Delete the given group
     * WARNING : If the group contains GroupItems, it will be ignored
     * This method is aimed to be used to delete empty groups when using Custom grouping
     *
     * @param group Group to be deleted
     * @return Group that has been deleted
     * @throws GroupNotRemovedException When any issue occurs during removal
     */
    private Group doDeleteGroup(@NonNull final Group group) throws GroupNotRemovedException {
        Helper.assertNonUiThread();

        try {
            // Check if given content still exists in DB
            Group theGroup = dao.selectGroup(group.id);
            if (!theGroup.items.isEmpty())
                throw new GroupNotRemovedException(group, "Group is not empty");

            if (theGroup != null) {
                dao.deleteGroup(theGroup.id);
                Timber.d("Removed group: %s from db.", theGroup.name);
                return theGroup;
            }
            throw new GroupNotRemovedException(group, "Error when trying to delete : invalid group ID " + group.id);
        } catch (GroupNotRemovedException gnre) {
            Timber.e(gnre, "Error when trying to delete %s", group.id);
            throw gnre;
        } catch (Exception e) {
            Timber.e(e, "Error when trying to delete %s", group.id);
            throw new GroupNotRemovedException(group, "Error when trying to delete " + group.id + " : " + e.getMessage(), e);
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

    public void moveBooksToNew(long[] bookIds, String newGroupName, @NonNull final Runnable onSuccess) {
        Group newGroup = new Group(Grouping.CUSTOM, newGroupName.trim(), -1);
        newGroup.id = dao.insertGroup(newGroup);
        moveBooks(bookIds, newGroup, onSuccess);
    }

    public void moveBooks(long[] bookIds, @Nullable final Group group, @NonNull final Runnable onSuccess) {
        compositeDisposable.add(
                Observable.fromIterable(Helper.getListFromPrimitiveArray(bookIds))
                        .observeOn(Schedulers.io())
                        .map(dao::selectContent)
                        .map(c -> doMoveBook(c, group))
                        .doOnNext(c -> ContentHelper.updateContentJson(getApplication(), c))
                        .doOnComplete(() -> {
                            isCustomGroupingAvailable.postValue(dao.countGroupsFor(Grouping.CUSTOM) > 0);
                            GroupHelper.updateGroupsJson(getApplication(), dao);
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                v -> onSuccess.run(),
                                Timber::e
                        )
        );
    }

    private Content doMoveBook(@NonNull final Content content, @Nullable final Group group) {
        Helper.assertNonUiThread();
        // Get all groupItems of the given content for custom grouping
        List<GroupItem> groupItems = dao.selectGroupItems(content.getId(), Grouping.CUSTOM);

        if (!groupItems.isEmpty()) {
            // Update the cover of the old groups if they used a picture from the book that is being moved
            for (GroupItem gi : groupItems) {
                Group g = gi.group.getTarget();
                if (g != null && !g.picture.isNull()) {
                    ImageFile groupCover = g.picture.getTarget();
                    if (groupCover.getContent().getTargetId() == content.getId()) {
                        updateGroupCover(g, content.getId());
                    }
                }
            }

            // Delete them all
            dao.deleteGroupItems(Stream.of(groupItems).map(gi -> gi.id).toList());
        }

        // Create the new links from the given content to the target group
        if (group != null) {
            GroupItem newGroupItem = new GroupItem(content, group, -1);
            // Use this syntax because content will be persisted on JSON right after that
            content.groupItems.add(newGroupItem);
            // Commit new link to the DB
            content.groupItems.applyChangesToDb();

            // Add a picture to the target group if it didn't have one
            if (group.picture.isNull())
                group.picture.setAndPutTarget(content.getCover());
        }

        return content;
    }

    private void updateGroupCover(@NonNull final Group g, long contentIdToRemove) {
        List<Content> groupsContents = g.getContents();

        // Empty group cover if there's just one content inside
        if (1 == groupsContents.size() && groupsContents.get(0).getId() == contentIdToRemove) {
            g.picture.setAndPutTarget(null);
            return;
        }

        // Choose 1st valid content cover
        for (Content c : groupsContents)
            if (c.getId() != contentIdToRemove) {
                ImageFile cover = c.getCover();
                if (cover.getId() > -1) {
                    g.picture.setAndPutTarget(cover);
                    return;
                }
            }
    }
}
