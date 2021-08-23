package me.devsaki.hentoid.viewmodels;

import static me.devsaki.hentoid.util.GroupHelper.moveBook;

import android.app.Application;
import android.os.Bundle;
import android.util.Pair;

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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.database.domains.GroupItem;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ContentParserFactory;
import me.devsaki.hentoid.parsers.images.ImageListParser;
import me.devsaki.hentoid.util.ArchiveHelper;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.GroupHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.ImageHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.RandomSeedSingleton;
import me.devsaki.hentoid.util.download.ContentQueueManager;
import me.devsaki.hentoid.util.exception.EmptyResultException;
import me.devsaki.hentoid.util.exception.LimitReachedException;
import me.devsaki.hentoid.util.network.HttpHelper;
import me.devsaki.hentoid.widget.ContentSearchManager;
import me.devsaki.hentoid.workers.DeleteWorker;
import me.devsaki.hentoid.workers.data.DeleteData;
import okhttp3.Response;
import okhttp3.ResponseBody;
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
            @NonNull final Runnable onSuccess,
            @NonNull final Consumer<Throwable> onError) {
        // Flag the content as "being deleted" (triggers blink animation)
        for (Content c : contentList) flagContentDelete(c, true);

        StatusContent targetImageStatus = reparseImages ? StatusContent.ERROR : null;

        compositeDisposable.add(
                Observable.fromIterable(contentList)
                        .observeOn(Schedulers.io())
                        .map(c -> (reparseContent) ? ContentHelper.reparseFromScratch(c) : Optional.of(c))
                        .map(c -> {
                            if (c.isEmpty()) throw new EmptyResultException();
                            Content content = c.get();
                            // Non-blocking performance bottleneck; run in a dedicated worker
                            // TODO if the purge is extremely long, that worker might still be working while downloads are happening on these same books
                            if (reparseImages) purgeItem(content);
                            return content;
                        })
                        .doOnNext(c -> dao.addContentToQueue(
                                c, targetImageStatus, position,
                                ContentQueueManager.getInstance().isQueueActive()))
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnComplete(() -> {
                            if (Preferences.isQueueAutostart())
                                ContentQueueManager.getInstance().resumeQueue(getApplication());
                            onSuccess.run();
                        })
                        .subscribe(
                                v -> { // Nothing; feedback is done through LiveData
                                },
                                onError::accept
                        )
        );
    }

    // TODO feedback in case it fails
    public void downloadContent(
            @NonNull final List<Content> contentList,
            int position,
            @NonNull final Runnable onSuccess) {
        // Flag the content as "being deleted" (triggers blink animation)
        for (Content c : contentList) flagContentDelete(c, true);

        compositeDisposable.add(
                Observable.fromIterable(contentList)
                        .observeOn(Schedulers.io())
                        .map(c -> {
                            // Reparse content from scratch if images KO
                            if (!arePagesReachable(c)) {
                                Timber.d("Pages unreachable; reparsing content");
                                // Reparse content itself
                                Optional<Content> newContent = ContentHelper.reparseFromScratch(c);
                                if (newContent.isEmpty()) throw new EmptyResultException();
                                return newContent.get();
                            }
                            return c;
                        })
                        .map(c -> c.setDownloadMode(Content.DownloadMode.DOWNLOAD))
                        .doOnNext(c -> dao.addContentToQueue(
                                c, StatusContent.SAVED, position,
                                ContentQueueManager.getInstance().isQueueActive()))
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnComplete(() -> {
                            if (Preferences.isQueueAutostart())
                                ContentQueueManager.getInstance().resumeQueue(getApplication());
                            onSuccess.run();
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                v -> { // Nothing; feedback is done through LiveData
                                },
                                Timber::e
                        )
        );
    }

    // TODO feedback in case it fails
    public void streamContent(@NonNull final List<Content> contentList) {

        compositeDisposable.add(
                Observable.fromIterable(contentList)
                        .observeOn(Schedulers.io())
                        .map(c -> {
                            // Reparse content from scratch if images KO
                            if (!arePagesReachable(c)) {
                                Timber.d("Pages unreachable; reparsing content");
                                // Reparse content itself
                                Optional<Content> newContent = ContentHelper.reparseFromScratch(c);
                                if (newContent.isEmpty()) throw new EmptyResultException();
                                Content reparsedContent = newContent.get();
                                // Reparse pages
                                List<ImageFile> newImages = ContentHelper.fetchImageURLs(reparsedContent, StatusContent.ONLINE);
                                dao.replaceImageList(reparsedContent.getId(), newImages);
                                return reparsedContent.setImageFiles(newImages);
                            }
                            return c;
                        })
                        .map(c -> {
                            // Non-blocking performance bottleneck; scheduled in a dedicated worker
                            purgeItem(c);
                            return c;
                        })
                        .map(c -> {
                            Content dbContent = dao.selectContent(c.getId());
                            if (null == dbContent) return c;
                            dbContent.setStatus(StatusContent.ONLINE);
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
                            dao.insertContent(dbContent);
                            ContentHelper.updateContentJson(getApplication(), dbContent);
                            return dbContent;
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                v -> { // Nothing; feedback is done through LiveData
                                },
                                Timber::e
                        )
        );
    }

    private boolean arePagesReachable(@NonNull final Content content) {
        List<ImageFile> images = content.getImageFiles();
        if (null == images) return false;

        // Pick a random picture
        ImageFile img = images.get(new Random().nextInt(images.size()));

        // Peek it to see if downloads work
        List<Pair<String, String>> headers = new ArrayList<>();
        headers.add(new Pair<>(HttpHelper.HEADER_REFERER_KEY, content.getReaderUrl())); // Useful for Hitomi and Toonily

        try {
            if (img.needsPageParsing()) {
                // Get cookies from the app jar
                String cookieStr = HttpHelper.getCookies(img.getPageUrl());
                // If nothing found, peek from the site
                if (cookieStr.isEmpty())
                    cookieStr = HttpHelper.peekCookies(img.getPageUrl());
                if (!cookieStr.isEmpty())
                    headers.add(new Pair<>(HttpHelper.HEADER_COOKIE_KEY, cookieStr));
                return testDownloadPictureFromPage(content, img, headers);
            } else {
                // Get cookies from the app jar
                String cookieStr = HttpHelper.getCookies(img.getUrl());
                // If nothing found, peek from the site
                if (cookieStr.isEmpty())
                    cookieStr = HttpHelper.peekCookies(content.getGalleryUrl());
                if (!cookieStr.isEmpty())
                    headers.add(new Pair<>(HttpHelper.HEADER_COOKIE_KEY, cookieStr));
                return testDownloadPicture(content, img, headers);
            }
        } catch (IOException | LimitReachedException | EmptyResultException e) {
            Timber.w(e);
        }
        return false;
    }

    private boolean testDownloadPictureFromPage(@NonNull Content content,
                                                @NonNull ImageFile img,
                                                List<Pair<String, String>> requestHeaders) throws IOException, LimitReachedException, EmptyResultException {
        Site site = content.getSite();
        String pageUrl = HttpHelper.fixUrl(img.getPageUrl(), site.getUrl());
        ImageListParser parser = ContentParserFactory.getInstance().getImageListParser(content.getSite());
        ImmutablePair<String, Optional<String>> pages = parser.parseImagePage(pageUrl, requestHeaders);
        img.setUrl(pages.left);
        // Download the picture
        try {
            return testDownloadPicture(content, img, requestHeaders);
        } catch (IOException e) {
            if (pages.right.isPresent()) Timber.d("First download failed; trying backup URL");
            else throw e;
        }
        // Trying with backup URL
        img.setUrl(pages.right.get());
        return testDownloadPicture(content, img, requestHeaders);
    }

    // TODO doc
    private boolean testDownloadPicture(
            @NonNull Content content,
            @NonNull ImageFile img,
            List<Pair<String, String>> requestHeaders) throws IOException {

        Site site = content.getSite();
        // TODO do that with a much smaller timeout than the default 30s (!)
        Response response = HttpHelper.getOnlineResource(img.getUrl(), requestHeaders, site.useMobileAgent(), site.useHentoidAgent(), site.useWebviewAgent());
        if (response.code() >= 300) throw new IOException("Network error " + response.code());

        ResponseBody body = response.body();
        if (null == body)
            throw new IOException("Could not read response : empty body for " + img.getUrl());

        byte[] buffer = new byte[50];
        // Read mime-type on the fly
        try (InputStream in = body.byteStream()) {
            if (in.read(buffer) > -1) {
                String mimeType = ImageHelper.getMimeTypeFromPictureBinary(buffer);
                Timber.d("Testing online picture accessibility : found %s at %s", mimeType, img.getUrl());
                return (!mimeType.isEmpty() && !mimeType.equals(ImageHelper.MIME_IMAGE_GENERIC));
            }
        }
        return false;
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
        if (!groups.isEmpty()) builder.setGroupIds(Stream.of(groups).map(Group::getId).toList());
        builder.setDeleteGroupsOnly(deleteGroupsOnly);

        WorkManager workManager = WorkManager.getInstance(getApplication());
        workManager.enqueueUniqueWork(
                Integer.toString(R.id.delete_service),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                new OneTimeWorkRequest.Builder(DeleteWorker.class).setInputData(builder.getData()).build()
        );
    }

    public void purgeItem(@NonNull final Content content) {
        DeleteData.Builder builder = new DeleteData.Builder();
        builder.setContentPurgeIds(Stream.of(content).map(Content::getId).toList());

        WorkManager workManager = WorkManager.getInstance(getApplication());
        workManager.enqueueUniqueWork(
                Integer.toString(R.id.delete_service),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                new OneTimeWorkRequest.Builder(DeleteWorker.class).setInputData(builder.getData()).build()
        );
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
                        .map(c -> moveBook(c, group, dao))
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
}
