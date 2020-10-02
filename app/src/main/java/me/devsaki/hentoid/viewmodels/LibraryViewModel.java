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

import java.io.IOException;
import java.io.OutputStream;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;

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
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ZipUtil;
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
    private LiveData<Integer> totalContent;
    private final MediatorLiveData<PagedList<Content>> libraryPaged = new MediatorLiveData<>();
    // Groups data
    private MutableLiveData<Group> group = new MutableLiveData<>();
    private LiveData<List<Group>> currentGroupsSource;
    private MediatorLiveData<List<Group>> groups = new MediatorLiveData<>();

    // Updated whenever a new search is performed
    private MutableLiveData<Boolean> newSearch = new MutableLiveData<>();


    public LibraryViewModel(@NonNull Application application, @NonNull CollectionDAO collectionDAO) {
        super(application);
        dao = collectionDAO;
        searchManager = new ContentSearchManager(dao);
        totalContent = dao.countAllBooks();
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
    private void performSearch() {
        if (currentSource != null) libraryPaged.removeSource(currentSource);

        searchManager.setContentSortField(Preferences.getContentSortField());
        searchManager.setContentSortDesc(Preferences.isContentSortDesc());

        currentSource = searchManager.getLibrary();

        libraryPaged.addSource(currentSource, libraryPaged::setValue);
    }

    /**
     * Perform a new universal search using the given query
     *
     * @param query Query to use for the universal search
     */
    public void searchUniversal(@NonNull String query) {
        searchManager.clearSelectedSearchTags(); // If user searches in main toolbar, universal search takes over advanced search
        searchManager.setQuery(query);
        newSearch.setValue(true);
        performSearch();
    }

    /**
     * Perform a new search using the given query and metadata
     *
     * @param query    Query to use for the search
     * @param metadata Metadata to use for the search
     */
    public void search(@NonNull String query, @NonNull List<Attribute> metadata) {
        searchManager.setQuery(query);
        searchManager.setTags(metadata);
        newSearch.setValue(true);
        performSearch();
    }

    /**
     * Toggle the favourite filter
     */
    public void toggleFavouriteFilter() {
        searchManager.setFilterFavourites(!searchManager.isFilterFavourites());
        newSearch.setValue(true);
        performSearch();
    }

    /**
     * Set the mode (endless or paged)
     */
    public void setPagingMethod(boolean isEndless) {
        searchManager.setLoadAll(!isEndless);
        newSearch.setValue(true);
        performSearch();
    }

    /**
     * Update the order of the list
     */
    public void updateOrder() {
        newSearch.setValue(true);
        performSearch();
    }

    public void setGroup(Group group) {
        searchManager.setGroup(group);
        this.group.postValue(group);
        newSearch.setValue(true);
        // Don't search now as the UI will inevitably search as well upon switching to books view
//        performSearch();
    }

    public void setGrouping(Grouping grouping) {
        if (currentGroupsSource != null) groups.removeSource(currentGroupsSource);
        currentGroupsSource = dao.selectGroups(grouping.getId(), 0);
        groups.addSource(currentGroupsSource, groups::setValue);
    }


    // =========================
    // ========= CONTENT ACTIONS
    // =========================

    /**
     * Toggle the "favourite" state of the given content
     *
     * @param content Content whose favourite state to toggle
     */
    public void toggleContentFavourite(@NonNull final Content content) {
        if (content.isBeingDeleted()) return;

        compositeDisposable.add(
                Single.fromCallable(() -> doToggleContentFavourite(content.getId()))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                v -> {
                                },
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
            if (!theContent.getJsonUri().isEmpty())
                ContentHelper.updateContentJson(getApplication(), theContent);
            else ContentHelper.createContentJson(getApplication(), theContent);

            // Persist in it DB
            dao.insertContent(theContent);

            return theContent;
        }

        throw new InvalidParameterException("Invalid ContentId : " + contentId);
    }

    /**
     * Add the given content to the download queue
     *
     * @param content Content to be added to the download queue
     */
    public void addContentToQueue(@NonNull final Content content, StatusContent targetImageStatus) {
        dao.addContentToQueue(content, targetImageStatus);
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

        // Queue first content then groups, to be sure to delete empty groups only
        List<Object> items = new ArrayList<>();
        items.addAll(contents);
        items.addAll(groups);

        compositeDisposable.add(
                Observable.fromIterable(items)
                        .observeOn(Schedulers.io())
                        .map(this::doDeleteItem)
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
            String destName = ContentHelper.formatBookFolderName(content) + ".zip";
            OutputStream destFile = FileHelper.openNewDownloadOutputStream(getApplication(), destName, ZipUtil.ZIP_MIME_TYPE);
            Timber.d("Destination file: %s", destName);
            ZipUtil.zipFiles(getApplication(), files, destFile);
            return content;
        }
        return null;
    }

    public void setGroupCover(long groupId, ImageFile cover) {
        Group group = dao.selectGroup(groupId);
        if (group != null) group.picture.setAndPutTarget(cover);
    }

    public void saveContentPositions(List<Content> orderedContent) {
        Group group = getGroup().getValue();
        if (null == group) return;

        // Update the "has custom book order" group flag
        group.hasCustomBookOrder = true;

        int order = 0;
        for (Content c : orderedContent)
            for (GroupItem gi : group.items)
                if (gi.content.getTargetId() == c.getId()) {
                    gi.order = order++;
                    dao.insertGroupItem(gi);
                    break;
                }
        dao.insertGroup(group);
    }

    public void newGroup(@NonNull final Grouping grouping, @NonNull final String groupName) {
        dao.insertGroup(new Group(grouping, groupName, -1));
    }

    public void saveGroupPositions(List<Group> orderedGroups) {
        int order = 0;
        for (Group g : orderedGroups) {
            g.order = order++;
            dao.insertGroup(g);
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
        if (!group.items.isEmpty()) throw new GroupNotRemovedException(group, "Group is not empty");

        try {
            // Check if given content still exists in DB
            Group theGroup = dao.selectGroup(group.id);

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

    public void moveBooks(long[] bookIds, String newGroupName) {
        Group newGroup = new Group(Grouping.CUSTOM, newGroupName.trim(), -1);
        newGroup.id = dao.insertGroup(newGroup);
        moveBooks(bookIds, newGroup);
    }

    public void moveBooks(long[] bookIds, Group group) {
        List<Content> contents = dao.selectContent(bookIds);
        for (Content c : contents) moveBook(c, group);
    }

    private void moveBook(@NonNull final Content content, @NonNull final Group group) {
        // Get all groupItems for custom grouping
        List<GroupItem> groupItems = dao.selectGroupItems(content.getId(), Grouping.CUSTOM);
        // Delete them all
        if (!groupItems.isEmpty())
            dao.deleteGroupItems(Stream.of(groupItems).map(gi -> gi.id).toList());

        // Create the new links
        GroupItem newGroupItem = new GroupItem(content, group, -1);
        dao.insertGroupItem(newGroupItem);
    }
}
