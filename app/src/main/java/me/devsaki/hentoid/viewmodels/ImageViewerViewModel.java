package me.devsaki.hentoid.viewmodels;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.Pair;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.annimon.stream.Collectors;
import com.annimon.stream.IntStream;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.core.Consts;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.parsers.ContentParserFactory;
import me.devsaki.hentoid.parsers.images.ImageListParser;
import me.devsaki.hentoid.util.ArchiveHelper;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.ImageHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.RandomSeedSingleton;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.ToastHelper;
import me.devsaki.hentoid.util.download.ContentQueueManager;
import me.devsaki.hentoid.util.download.DownloadHelper;
import me.devsaki.hentoid.util.exception.DownloadInterruptedException;
import me.devsaki.hentoid.util.exception.EmptyResultException;
import me.devsaki.hentoid.util.exception.LimitReachedException;
import me.devsaki.hentoid.util.exception.UnsupportedContentException;
import me.devsaki.hentoid.util.network.HttpHelper;
import me.devsaki.hentoid.widget.ContentSearchManager;
import timber.log.Timber;

/**
 * ViewModel for the image viewer
 * <p>
 * Upon loading a new Content, LiveData updates are done in the following sequence :
 * 1. Content
 * 2. Images
 * 3. Starting index
 */
public class ImageViewerViewModel extends AndroidViewModel {

    // Number of concurrent image downloads
    private static final int CONCURRENT_DOWNLOADS = 3;
    private static final int EXTRACT_RANGE = 35;

    private static Pattern VANILLA_CHAPTERNAME_PATTERN = null;

    // Collection DAO
    private final CollectionDAO dao;
    private final ContentSearchManager searchManager;

    // Collection data
    private final MutableLiveData<Content> content = new MutableLiveData<>();        // Current content
    private List<Long> contentIds = Collections.emptyList();                         // Content Ids of the whole collection ordered according to current filter
    private int currentContentIndex = -1;                                            // Index of current content within the above list
    private long loadedContentId = -1;                                               // ID of currently loaded book

    // Pictures data
    private LiveData<List<ImageFile>> currentImageSource;
    private final MediatorLiveData<List<ImageFile>> databaseImages = new MediatorLiveData<>();  // Set of image of current content
    private final List<ImageFile> viewerImagesInternal = Collections.synchronizedList(new ArrayList<>());
    private final MutableLiveData<List<ImageFile>> viewerImages = new MutableLiveData<>();     // Currently displayed set of images (reprocessed from databaseImages)
    private final MutableLiveData<Integer> startingIndex = new MutableLiveData<>();     // 0-based index of the current image
    private final MutableLiveData<Boolean> shuffled = new MutableLiveData<>();          // Shuffle state of the current book
    private final MutableLiveData<Boolean> showFavouritesOnly = new MutableLiveData<>();// True if viewer only shows favourite images; false if shows all pages
    private int thumbIndex;                                                             // Index of the thumbnail among loaded pages

    // Write cache for read indicator (no need to update DB and JSON at every page turn)
    private final Set<Integer> readPageNumbers = new HashSet<>();

    // Cache for image locations according to their order
    private final Map<Integer, String> imageLocationCache = new HashMap<>();
    // Switch to interrupt extracting when leaving the activity
    private final AtomicBoolean interruptArchiveExtract = new AtomicBoolean(false);
    // Page indexes that are being downloaded
    private final Set<Integer> indexProcessInProgress = Collections.synchronizedSet(new HashSet<>());
    // FIFO switches to interrupt downloads when browsing the book
    private final Queue<AtomicBoolean> downloadsQueue = new ConcurrentLinkedQueue<>();

    // Technical
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private final CompositeDisposable imageDownloadDisposables = new CompositeDisposable();
    private final CompositeDisposable notificationDisposables = new CompositeDisposable();
    private Disposable searchDisposable = Disposables.empty();
    private Disposable archiveExtractDisposable = Disposables.empty();
    private Disposable imageLoadDisposable = Disposables.empty();
    private Disposable leaveDisposable = Disposables.empty();
    private Disposable emptyCacheDisposable = Disposables.empty();


    public ImageViewerViewModel(@NonNull Application application, @NonNull CollectionDAO collectionDAO) {
        super(application);
        dao = collectionDAO;
        searchManager = new ContentSearchManager(dao);

        showFavouritesOnly.postValue(false);
        shuffled.postValue(false);
    }

    @Override
    protected void onCleared() {
        dao.cleanup();
        compositeDisposable.clear();
        searchDisposable.dispose();
        super.onCleared();
    }

    @NonNull
    public LiveData<Content> getContent() {
        return content;
    }

    @NonNull
    public LiveData<List<ImageFile>> getViewerImages() {
        return viewerImages;
    }

    @NonNull
    public LiveData<Integer> getStartingIndex() {
        return startingIndex;
    }

    @NonNull
    public LiveData<Boolean> getShuffled() {
        return shuffled;
    }

    @NonNull
    public LiveData<Boolean> getShowFavouritesOnly() {
        return showFavouritesOnly;
    }

    // Artificial observer bound to the activity's lifecycle to ensure DB images are pushed to the ViewModel
    public void observeDbImages(AppCompatActivity activity) {
        databaseImages.observe(activity, v -> {
        });
    }

    /**
     * Load the given Content at the given page number
     *
     * @param contentId  ID of the Content to load
     * @param pageNumber Page number to start with
     */
    public void loadContentFromId(long contentId, int pageNumber) {
        if (contentId > 0) {
            Content loadedContent = dao.selectContent(contentId);
            if (loadedContent != null)
                loadContent(loadedContent, pageNumber);
        }
    }

    /**
     * Load the given Content at the given page number + preload all content IDs corresponding to the given search params
     *
     * @param contentId  ID of the Content to load
     * @param pageNumber Page number to start with
     * @param bundle     ContentSearchBundle with the current filters and search criteria
     */
    public void loadContentFromSearchParams(long contentId, int pageNumber, @NonNull Bundle bundle) {
        searchManager.loadFromBundle(bundle);
        loadContentFromSearchParams(contentId, pageNumber);
    }

    /**
     * Load the given Content at the given page number using the current state of SearchManager
     *
     * @param contentId  ID of the Content to load
     * @param pageNumber Page number to start with
     */
    private void loadContentFromSearchParams(long contentId, int pageNumber) {
        searchDisposable.dispose();
        searchDisposable = searchManager.searchLibraryForId().subscribe(
                list -> {
                    contentIds = list;
                    loadContentFromId(contentId, pageNumber);
                },
                throwable -> {
                    Timber.w(throwable);
                    ToastHelper.toast(R.string.book_list_loading_failed);
                }
        );
    }

    /**
     * Set the given index as the picture viewer's starting index
     *
     * @param index Page index to set
     */
    public void setViewerStartingIndex(int index) {
        startingIndex.postValue(index);
    }

    /**
     * Process the given raw ImageFile entries to populate the viewer
     *
     * @param theContent Content to use
     * @param pageNumber Page number to start with
     * @param newImages  Images to process
     */
    private void loadImages(@NonNull Content theContent, int pageNumber, @NonNull List<ImageFile> newImages) {
        databaseImages.postValue(newImages);

        // Don't reload from disk / archive again if the image list hasn't changed
        // e.g. page favourited
        if (!theContent.isArchive() && (imageLocationCache.isEmpty() || newImages.size() != imageLocationCache.size())) {
            imageLoadDisposable = Completable.fromRunnable(() -> processStorageImages(theContent, newImages))
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .doOnComplete(
                            // Called this way to properly run on I/O thread
                            () -> cacheJson(getApplication().getApplicationContext(), theContent)
                    )
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            () -> {
                                for (ImageFile img : newImages)
                                    imageLocationCache.put(img.getOrder(), img.getFileUri());
                                processImages(theContent, -1, newImages);
                                imageLoadDisposable.dispose();
                            }
                    );
        } else {
            // Copy location properties of the new list on the current list
            for (int i = 0; i < newImages.size(); i++) {
                ImageFile newImg = newImages.get(i);
                String location = imageLocationCache.get(newImg.getOrder());
                newImg.setFileUri(location);
            }

            processImages(theContent, pageNumber, newImages);
        }
    }

    /**
     * Process the given raw ImageFile entries to populate the viewer, loading the images directly from the device's storage
     *
     * @param theContent Content to use
     * @param newImages  Images to process
     */
    private void processStorageImages(
            @NonNull Content theContent,
            @NonNull List<ImageFile> newImages) {
        if (theContent.isArchive())
            throw new IllegalArgumentException("Content must not be an archive");
        boolean missingUris = Stream.of(newImages).filter(img -> img.getFileUri().isEmpty()).count() > 0;
        List<ImageFile> newImageFiles = new ArrayList<>(newImages);

        // Reattach actual files to the book's pictures if they are empty or have no URI's
        if (missingUris || newImages.isEmpty()) {
            List<DocumentFile> pictureFiles = ContentHelper.getPictureFilesFromContent(getApplication(), theContent);
            if (!pictureFiles.isEmpty()) {
                if (newImages.isEmpty()) {
                    newImageFiles = ContentHelper.createImageListFromFiles(pictureFiles);
                    theContent.setImageFiles(newImageFiles);
                    dao.insertContent(theContent);
                } else {
                    // Match files for viewer display; no need to persist that
                    ContentHelper.matchFilesToImageList(pictureFiles, newImageFiles);
                }
            }
        }

        // Replace initial images with updated images
        newImages.clear();
        newImages.addAll(newImageFiles);
    }

    /**
     * Callback to run when the activity is on the verge of being destroyed
     */
    public void onActivityLeave() {
        // Dispose the composite disposables for good
        imageDownloadDisposables.dispose();
        notificationDisposables.dispose();
        archiveExtractDisposable.dispose();
        // Empty cache
        emptyCacheDisposable =
                Completable.fromRunnable(() -> FileHelper.emptyCacheFolder(getApplication(), Consts.PICTURE_CACHE_FOLDER))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> emptyCacheDisposable.dispose(),
                                Timber::e
                        );
    }

    /**
     * Map the given file Uri to its corresponding ImageFile in the given list, using their display name
     *
     * @param contentId  ID of the current Content
     * @param imageFiles List of ImageFiles to map the given Uri to
     * @param uri        File Uri to map to one of the elements of the given list
     * @return Matched ImageFile with the valued Uri if found; empty ImageFile if not found
     */
    private Optional<Pair<Integer, ImageFile>> mapUriToImageFile(long contentId, @NonNull final List<ImageFile> imageFiles, @NonNull final Uri uri) {
        String path = uri.getPath();
        if (null == path) return Optional.empty();

        // Feed the Uri's of unzipped files back into the corresponding images for viewing
        int index = 0;
        for (ImageFile img : imageFiles) {
            if (FileHelper.getFileNameWithoutExtension(uri.getPath()).endsWith(contentId + "." + index)) {
                return Optional.of(new Pair<>(index, img));
            }
            index++;
        }
        return Optional.empty();
    }

    /**
     * Initialize the picture viewer using the given parameters
     *
     * @param theContent Content to use
     * @param pageNumber Page number to start with
     * @param imageFiles Pictures to process
     */
    private void processImages(@NonNull Content theContent, int pageNumber, @NonNull List<ImageFile> imageFiles) {
        Boolean shuffledVal = getShuffled().getValue();
        sortAndSetViewerImages(imageFiles, null != shuffledVal && shuffledVal);

        if (theContent.getId() != loadedContentId) { // To be done once per book only
            int startingIndex = 0;

            // Auto-restart at last read position if asked to
            if (Preferences.isViewerResumeLastLeft() && theContent.getLastReadPageIndex() > -1)
                startingIndex = theContent.getLastReadPageIndex();

            // Start at the given page number, if any
            if (pageNumber > -1) {
                int index = 0;
                for (ImageFile img : imageFiles) {
                    if (img.getOrder() == pageNumber) {
                        startingIndex = index + 1;
                        break;
                    }
                    index++;
                }
            }

            // Correct offset with the thumb index
            thumbIndex = -1;
            for (int i = 0; i < imageFiles.size(); i++)
                if (!imageFiles.get(i).isReadable()) {
                    thumbIndex = i;
                    break;
                }

            if (thumbIndex == startingIndex) startingIndex += 1;
            else if (thumbIndex > startingIndex) thumbIndex = 0; // Ignore if it doesn't intervene

            setViewerStartingIndex(startingIndex - thumbIndex - 1);

            // Init the read pages write cache
            readPageNumbers.clear();
            Collection<Integer> readPages = Stream.of(imageFiles).filter(ImageFile::isRead).filter(ImageFile::isReadable).map(ImageFile::getOrder).toList();

            // Fix pre-v1.13 books where ImageFile.read has no value
            if (readPages.isEmpty() && theContent.getLastReadPageIndex() > 0 && theContent.getLastReadPageIndex() < imageFiles.size()) {
                int lastReadPageNumber = imageFiles.get(theContent.getLastReadPageIndex()).getOrder();
                readPageNumbers.addAll(IntStream.rangeClosed(1, lastReadPageNumber).boxed().toList());
            } else {
                readPageNumbers.addAll(readPages);
            }

            // Mark initial page as read
            if (startingIndex < imageFiles.size())
                markPageAsRead(imageFiles.get(startingIndex).getOrder());
        }

        loadedContentId = theContent.getId();
    }

    /**
     * Toggle the shuffle mode
     */
    public void toggleShuffle() {
        Boolean shuffledVal = getShuffled().getValue();
        boolean isShuffled = null != shuffledVal && shuffledVal;
        isShuffled = !isShuffled;
        if (isShuffled) RandomSeedSingleton.getInstance().renewSeed(Consts.SEED_PAGES);
        shuffled.postValue(isShuffled);

        List<ImageFile> imgs = databaseImages.getValue();
        if (imgs != null) sortAndSetViewerImages(imgs, isShuffled);
    }

    /**
     * Sort and set the given images for the viewer
     *
     * @param imgs    Images to process
     * @param shuffle Trye if shuffle mode is on; false if not
     */
    private void sortAndSetViewerImages(@NonNull List<ImageFile> imgs, boolean shuffle) {
        Stream<ImageFile> imgStream;
        if (shuffle) {
            Collections.shuffle(imgs, new Random(RandomSeedSingleton.getInstance().getSeed(Consts.SEED_PAGES)));
            imgStream = Stream.of(imgs);
        } else {
            // Sort images according to their Order; don't keep the cover thumb
            imgStream = Stream.of(imgs).sortBy(ImageFile::getOrder);
        }
        // Don't keep the cover thumb
        imgStream = imgStream.filter(ImageFile::isReadable);

        Boolean showFavouritesOnlyVal = getShowFavouritesOnly().getValue();
        if (showFavouritesOnlyVal != null && showFavouritesOnlyVal)
            imgStream = imgStream.filter(ImageFile::isFavourite);

        imgs = imgStream.toList();

        for (int i = 0; i < imgs.size(); i++) imgs.get(i).setDisplayOrder(i);

        // Only update if there's any noticeable difference on images...
        boolean hasDiff = (imgs.size() != viewerImagesInternal.size());
        if (!hasDiff) {
            for (int i = 0; i < imgs.size(); i++) {
                hasDiff = !Objects.equals(imgs.get(i), viewerImagesInternal.get(i));
                if (hasDiff) break;
            }
        }
        // ...or chapters
        if (!hasDiff) {
            List<Chapter> oldChapters = Stream.of(viewerImagesInternal).map(ImageFile::getLinkedChapter).toList();
            List<Chapter> newChapters = Stream.of(imgs).map(ImageFile::getLinkedChapter).toList();

            hasDiff = (oldChapters.size() != newChapters.size());

            if (!hasDiff) {
                for (int i = 0; i < oldChapters.size(); i++) {
                    hasDiff = !Objects.equals(oldChapters.get(i), newChapters.get(i));
                    if (hasDiff) break;
                }
            }
        }

        if (hasDiff) {
            synchronized (viewerImagesInternal) {
                viewerImagesInternal.clear();
                viewerImagesInternal.addAll(imgs);
            }
            viewerImages.postValue(new ArrayList<>(viewerImagesInternal));
        }
    }

    /**
     * Callback to run whenever a book is left (e.g. using previous/next or leaving activity)
     *
     * @param viewerIndex Viewer index of the active page when the user left the book
     */
    public void onLeaveBook(int viewerIndex) {
        if (Preferences.Constant.VIEWER_DELETE_ASK_BOOK == Preferences.getViewerDeleteAskMode())
            Preferences.setViewerDeleteAskMode(Preferences.Constant.VIEWER_DELETE_ASK_AGAIN);

        indexProcessInProgress.clear();
        interruptArchiveExtract.set(true);

        // Stop any ongoing picture loading
        imageLoadDisposable.dispose();
        // Clear the composite disposables so that they can be reused
        imageDownloadDisposables.clear();
        notificationDisposables.clear();

        // Don't do anything if the Content hasn't even been loaded
        if (-1 == loadedContentId) return;

        List<ImageFile> theImages = databaseImages.getValue();
        Content theContent = dao.selectContent(loadedContentId);
        if (null == theImages || null == theContent) return;

        int nbReadablePages = (int) Stream.of(theImages).filter(ImageFile::isReadable).count();
        int readThresholdPref = Preferences.getViewerReadThreshold();
        int readThresholdPosition;
        switch (readThresholdPref) {
            case Preferences.Constant.VIEWER_READ_THRESHOLD_2:
                readThresholdPosition = 2;
                break;
            case Preferences.Constant.VIEWER_READ_THRESHOLD_5:
                readThresholdPosition = 5;
                break;
            case Preferences.Constant.VIEWER_READ_THRESHOLD_ALL:
                readThresholdPosition = nbReadablePages;
                break;
            default:
                readThresholdPosition = 1;
        }
        int collectionIndex = viewerIndex + thumbIndex + 1;
        boolean updateReads = (readPageNumbers.size() >= readThresholdPosition || theContent.getReads() > 0);

        // Reset the memorized page index if it represents the last page
        int indexToSet = (collectionIndex >= nbReadablePages) ? 0 : collectionIndex;

        leaveDisposable =
                Completable.fromRunnable(() -> doLeaveBook(theContent.getId(), indexToSet, updateReads))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> leaveDisposable.dispose(),
                                Timber::e
                        );
    }

    /**
     * Perform the I/O operations to persist book information upon leaving
     *
     * @param contentId   ID of the Content to save
     * @param indexToSet  DB page index to set as the last read page
     * @param updateReads True if number of reads have to be updated; false if not
     */
    private void doLeaveBook(final long contentId, int indexToSet, boolean updateReads) {
        Helper.assertNonUiThread();

        // Use a brand new DAO for that (the viewmodel's DAO may be in the process of being cleaned up)
        CollectionDAO dao = new ObjectBoxDAO(getApplication());
        try {
            // Get a fresh version of current content in case it has been updated since the initial load
            // (that can be the case when viewing a book that is being downloaded)
            Content savedContent = dao.selectContent(contentId);
            if (null == savedContent) return;

            List<ImageFile> theImages = savedContent.getImageFiles();
            if (null == theImages) return;

            // Update image read status with the cached read statuses
            long previousReadPagesCount = Stream.of(theImages).filter(ImageFile::isRead).filter(ImageFile::isReadable).count();
            if (readPageNumbers.size() > previousReadPagesCount) {
                for (ImageFile img : theImages)
                    if (readPageNumbers.contains(img.getOrder())) img.setRead(true);
                savedContent.computeReadProgress();
            }

            if (indexToSet != savedContent.getLastReadPageIndex() || updateReads || readPageNumbers.size() > previousReadPagesCount)
                ContentHelper.updateContentReadStats(getApplication(), dao, savedContent, theImages, indexToSet, updateReads);
        } finally {
            dao.cleanup();
        }
    }

    /**
     * Set the filter for favourite pages to the target state
     *
     * @param targetState Target state of the favourite pages filter
     */
    public void filterFavouriteImages(boolean targetState) {
        if (loadedContentId > -1) {
            showFavouritesOnly.postValue(targetState);
            if (searchManager != null) searchManager.setFilterPageFavourites(targetState);
            loadContentFromSearchParams(loadedContentId, -1);
        }
    }

    /**
     * Toggle the favourite status of the page at the given viewer index
     *
     * @param viewerIndex     Viewer index of the page whose status to toggle
     * @param successCallback Callback to be called on success
     */
    public void toggleImageFavourite(int viewerIndex, @NonNull Consumer<Boolean> successCallback) {
        ImageFile file = viewerImagesInternal.get(viewerIndex);
        boolean newState = !file.isFavourite();
        toggleImageFavourite(Stream.of(file).toList(), () -> successCallback.accept(newState));
    }

    /**
     * Toggle the favourite status of the given pages
     *
     * @param images          Pages whose status to toggle
     * @param successCallback Callback to be called on success
     */
    public void toggleImageFavourite(List<ImageFile> images, @NonNull Runnable successCallback) {
        compositeDisposable.add(
                Completable.fromRunnable(() -> doToggleImageFavourite(images))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                successCallback::run,
                                Timber::e
                        )
        );
    }

    /**
     * Toggles page favourite flag in DB and in the content JSON
     *
     * @param images images whose flag to toggle
     */
    private void doToggleImageFavourite(@NonNull final List<ImageFile> images) {
        Helper.assertNonUiThread();

        if (images.isEmpty()) return;
        Content theContent = dao.selectContent(images.get(0).getContent().getTargetId());
        if (null == theContent) return;

        // We can't work on the given objects as they are tied to the UI (part of ImageFileItem)
        List<ImageFile> dbImages = theContent.getImageFiles();
        if (null == dbImages) return;

        for (ImageFile img : images)
            for (ImageFile dbImg : dbImages)
                if (img.getId() == dbImg.getId()) {
                    dbImg.setFavourite(!dbImg.isFavourite());
                    break;
                }

        // Persist in DB
        dao.insertImageFiles(dbImages);

        // Persist new values in JSON
        theContent.setImageFiles(dbImages);
        Context context = getApplication().getApplicationContext();
        if (!theContent.getJsonUri().isEmpty())
            ContentHelper.updateContentJson(context, theContent);
        else ContentHelper.createContentJson(context, theContent);
    }

    /**
     * Toggle the favourite flag of the given Content
     *
     * @param successCallback Callback to be called on success
     */
    public void toggleContentFavourite(@NonNull Consumer<Boolean> successCallback) {
        Content c = getContent().getValue();
        if (null == c) return;
        boolean newState = !c.isFavourite();

        compositeDisposable.add(
                Completable.fromRunnable(() -> doToggleContentFavourite(c))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> successCallback.accept(newState),
                                Timber::e
                        )
        );
    }

    /**
     * Toggle the favourite flag of the given Content in DB and in the content JSON
     *
     * @param content content whose flag to toggle
     */
    private void doToggleContentFavourite(@NonNull final Content content) {
        Helper.assertNonUiThread();

        content.setFavourite(!content.isFavourite());

        // Persist in DB
        dao.insertContent(content);

        // Persist new values in JSON
        Context context = getApplication().getApplicationContext();
        if (!content.getJsonUri().isEmpty())
            ContentHelper.updateContentJson(context, content);
        else ContentHelper.createContentJson(context, content);
    }

    /**
     * Delete the current Content
     *
     * @param onError Callback to call in case an error occurs
     */
    public void deleteContent(Consumer<Throwable> onError) {
        Content targetContent = dao.selectContent(loadedContentId);
        if (null == targetContent) return;

        // Unplug image source listener (avoid displaying pages as they are being deleted; it messes up with DB transactions)
        if (currentImageSource != null) databaseImages.removeSource(currentImageSource);

        compositeDisposable.add(
                Completable.fromAction(() -> ContentHelper.removeQueuedContent(getApplication(), dao, targetContent, true))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> {
                                    currentImageSource = null;
                                    // Switch to the next book if the list is populated (multi-book)
                                    if (!contentIds.isEmpty()) {
                                        contentIds.remove(currentContentIndex);
                                        if (currentContentIndex >= contentIds.size() && currentContentIndex > 0)
                                            currentContentIndex--;
                                        if (contentIds.size() > currentContentIndex)
                                            loadContentFromId(contentIds.get(currentContentIndex), -1);
                                    } else { // Close the viewer if the list is empty (single book)
                                        content.postValue(null);
                                    }
                                },
                                e -> {
                                    onError.accept(e);
                                    // Restore image source listener on error
                                    databaseImages.addSource(currentImageSource, imgs -> loadImages(targetContent, -1, imgs));
                                }
                        )
        );
    }

    /**
     * Delete the page at the given viewer index
     *
     * @param pageViewerIndex Viewer index of the page to delete
     * @param onError         Callback to run in case of error
     */
    public void deletePage(int pageViewerIndex, Consumer<Throwable> onError) {
        List<ImageFile> imageFiles = viewerImagesInternal;
        if (imageFiles.size() > pageViewerIndex && pageViewerIndex > -1)
            deletePages(Stream.of(imageFiles.get(pageViewerIndex)).toList(), onError);
    }

    /**
     * Delete the given pages
     *
     * @param pages   Pages to delete
     * @param onError Callback to run in case of error
     */
    public void deletePages(List<ImageFile> pages, Consumer<Throwable> onError) {
        compositeDisposable.add(
                Completable.fromRunnable(() -> ContentHelper.removePages(pages, dao, getApplication()))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> { // Update is done through LiveData
                                },
                                e -> {
                                    Timber.e(e);
                                    onError.accept(e);
                                }
                        )
        );
    }

    /**
     * Set the given image as the current Content's cover
     *
     * @param page Page to set as the current Content's cover
     */
    public void setCover(ImageFile page) {
        compositeDisposable.add(
                Completable.fromRunnable(() -> ContentHelper.setContentCover(page, dao, getApplication()))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> { // Update is done through LiveData
                                },
                                Timber::e
                        )
        );
    }

    /**
     * Load the next Content according to the current filter & search criteria
     *
     * @param viewerIndex Page viewer index the current Content has been left on
     */
    public void loadNextContent(int viewerIndex) {
        if (currentContentIndex < contentIds.size() - 1) {
            currentContentIndex++;
            if (!contentIds.isEmpty()) {
                onLeaveBook(viewerIndex);
                loadContentFromId(contentIds.get(currentContentIndex), -1);
            }
        }
    }

    /**
     * Load the previous Content according to the current filter & search criteria
     *
     * @param viewerIndex Page viewer index the current Content has been left on
     */
    public void loadPreviousContent(int viewerIndex) {
        if (currentContentIndex > 0) {
            currentContentIndex--;
            if (!contentIds.isEmpty()) {
                onLeaveBook(viewerIndex);
                loadContentFromId(contentIds.get(currentContentIndex), -1);
            }
        }
    }

    /**
     * Load the given content at the given page number
     *
     * @param theContent Content to load
     * @param pageNumber Page number to start with
     */
    private void loadContent(@NonNull Content theContent, int pageNumber) {
        Preferences.setViewerCurrentContent(theContent.getId());
        currentContentIndex = contentIds.indexOf(theContent.getId());
        if (-1 == currentContentIndex) currentContentIndex = 0;

        theContent.setFirst(0 == currentContentIndex);
        theContent.setLast(currentContentIndex >= contentIds.size() - 1);
        if (contentIds.size() > currentContentIndex && loadedContentId != contentIds.get(currentContentIndex))
            imageLocationCache.clear();
        content.postValue(theContent);
        loadDatabaseImages(theContent, pageNumber);
    }

    /**
     * Load the given Content's pictures from the database and process them, initializing the viewer to start at the given page number
     *
     * @param theContent Content to load the pictures for
     * @param pageNumber Page number to start with
     */
    private void loadDatabaseImages(@NonNull Content theContent, int pageNumber) {
        // Observe the content's images
        // NB : It has to be dynamic to be updated when viewing a book from the queue screen
        if (currentImageSource != null) databaseImages.removeSource(currentImageSource);
        currentImageSource = dao.selectDownloadedImagesFromContentLive(theContent.getId());
        databaseImages.addSource(currentImageSource, imgs -> loadImages(theContent, pageNumber, imgs));
    }

    /**
     * Update local preferences for the current Content
     *
     * @param newPrefs Preferences to replace the current Content's local preferences
     */
    public void updateContentPreferences(@NonNull final Map<String, String> newPrefs) {
        compositeDisposable.add(
                Single.fromCallable(() -> doUpdateContentPreferences(getApplication().getApplicationContext(), loadedContentId, newPrefs))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                content::postValue,
                                Timber::e
                        )
        );
    }

    /**
     * Update local preferences for the given Content
     *
     * @param context   Context to use
     * @param contentId ID of the Content whose local preferences will be replaced
     * @param newPrefs  Preferences to replace the current Content's local preferences
     * @return Updated Content
     */
    private Content doUpdateContentPreferences(@NonNull final Context context, long contentId,
                                               @NonNull final Map<String, String> newPrefs) {
        Helper.assertNonUiThread();

        Content theContent = dao.selectContent(contentId);
        if (null == theContent) return null;

        theContent.setBookPreferences(newPrefs);
        // Persist in DB
        dao.insertContent(theContent);

        // Persist in JSON
        if (!theContent.getJsonUri().isEmpty())
            ContentHelper.updateContentJson(context, theContent);
        else ContentHelper.createContentJson(context, theContent);

        return theContent;
    }

    /**
     * Cache the JSON URI of the given Content in the database to speed up favouriting
     *
     * @param context Context to use
     * @param content Content to cache the JSON URI for
     */
    private void cacheJson(@NonNull Context context, @NonNull Content content) {
        Helper.assertNonUiThread();
        if (!content.getJsonUri().isEmpty() || content.isArchive()) return;

        DocumentFile folder = FileHelper.getFolderFromTreeUriString(context, content.getStorageUri());
        if (null == folder) return;

        DocumentFile foundFile = FileHelper.findFile(getApplication(), folder, Consts.JSON_FILE_NAME_V2);
        if (null == foundFile) {
            Timber.e("JSON file not detected in %s", content.getStorageUri());
            return;
        }

        // Cache the URI of the JSON to the database
        content.setJsonUri(foundFile.getUri().toString());
        dao.insertContent(content);
    }

    /**
     * Mark the given page number as read
     *
     * @param pageNumber Page number to mark as read
     */
    public void markPageAsRead(int pageNumber) {
        readPageNumbers.add(pageNumber);
    }

    /**
     * Force all images to be reposted
     */
    public void repostImages() {
        viewerImages.postValue(viewerImages.getValue());
    }

    /**
     * Handler to call when changing page
     *
     * @param viewerIndex Viewer index of the page that has just been displayed
     * @param direction   Direction the viewer is going to (1 : forward; -1 : backward; 0 : no movement)
     */
    public synchronized void onPageChange(int viewerIndex, int direction) {
        if (viewerImagesInternal.size() <= viewerIndex) return;
        Content theContent = getContent().getValue();
        if (null == theContent) return;

        boolean isArchive = theContent.isArchive();
        Set<Integer> picturesLeftToProcess = Stream.range(0, viewerImagesInternal.size()).filter(i -> isPictureNeedsProcessing(i, viewerImagesInternal, isArchive)).collect(Collectors.toSet());
        if (null == picturesLeftToProcess) return;

        // Identify pages to be loaded
        List<Integer> indexesToLoad = new ArrayList<>();
        int increment = (direction >= 0) ? 1 : -1;
        int quantity = isArchive ? EXTRACT_RANGE : CONCURRENT_DOWNLOADS;
        // pageIndex at 1/3rd of the range to extract/download -> determine its bound
        int initialIndex = (int) Math.floor(Helper.coerceIn(viewerIndex - (quantity * increment / 3f), 0, viewerImagesInternal.size() - 1));
        for (int i = 0; i < quantity; i++)
            if (picturesLeftToProcess.contains(initialIndex + (increment * i)))
                indexesToLoad.add(initialIndex + (increment * i));

        // Only run extraction when there's at least 1/3rd of the extract range to fetch
        // (prevents calling extraction for one single picture at every page turn)
        boolean greenlight = true;
        if (isArchive) {
            greenlight = indexesToLoad.size() >= EXTRACT_RANGE / 3f;
            if (!greenlight) {
                int from = (increment > 0) ? initialIndex : 0;
                int to = (increment > 0) ? viewerImagesInternal.size() : initialIndex + 1;
                long leftToProcessDirection = Stream.range(from, to).filter(picturesLeftToProcess::contains).count();
                greenlight = indexesToLoad.size() == leftToProcessDirection;
            }
        }
        if (indexesToLoad.isEmpty() || !greenlight) return;

        DocumentFile archiveFile = isArchive ? FileHelper.getFileFromSingleUriString(getApplication(), theContent.getStorageUri()) : null;
        if (isArchive && null == archiveFile) return;

        File cachePicFolder = FileHelper.getOrCreateCacheFolder(getApplication(), Consts.PICTURE_CACHE_FOLDER);
        if (null == cachePicFolder) return;

        Timber.d("Processing %d files starting around %d from index %s", indexesToLoad.size(), viewerIndex, initialIndex);

        if (isArchive) extractPics(indexesToLoad, archiveFile, cachePicFolder);
        else downloadPics(indexesToLoad, cachePicFolder);
    }

    /**
     * Indicate if the picture at the given page index in the given list needs processing
     * (i.e. downloading o extracting)
     *
     * @param pageIndex Index to test
     * @param images    List of pictures to test against
     * @param isArchive True if the current content is an archive
     * @return True if the picture at the given index needs processing; false if not
     */
    private boolean isPictureNeedsProcessing(int pageIndex, @NonNull List<ImageFile> images, boolean isArchive) {
        if (pageIndex < 0 || images.size() <= pageIndex) return false;
        ImageFile img = images.get(pageIndex);
        return (img.getStatus().equals(StatusContent.ONLINE) && img.getFileUri().isEmpty()) // Image has to be downloaded
                || (isArchive && (img.getFileUri().isEmpty() || img.getUrl().equals(img.getFileUri()))); // Image has to be extracted from an archive
    }

    /**
     * Download the pictures at the given indexes to the given folder
     *
     * @param indexesToLoad DB indexes of the pictures to download
     * @param targetFolder  Target folder to download the pictures to
     */
    private void downloadPics(
            @NonNull List<Integer> indexesToLoad,
            @NonNull File targetFolder
    ) {
        for (int index : indexesToLoad) {
            if (indexProcessInProgress.contains(index)) continue;
            indexProcessInProgress.add(index);

            // Adjust the current queue
            while (downloadsQueue.size() >= CONCURRENT_DOWNLOADS) {
                AtomicBoolean stopDownload = downloadsQueue.poll();
                if (stopDownload != null) stopDownload.set(true);
                Timber.d("Aborting a download");
            }
            // Schedule a new download
            AtomicBoolean stopDownload = new AtomicBoolean(false);
            downloadsQueue.add(stopDownload);

            Single<Optional<ImmutableTriple<Integer, String, String>>> single = Single.fromCallable(() -> downloadPic(index, targetFolder, stopDownload));

            imageDownloadDisposables.add(
                    single.subscribeOn(Schedulers.io())
                            .observeOn(Schedulers.computation())
                            .subscribe(
                                    resultOpt -> {
                                        if (resultOpt.isEmpty()) { // Nothing to download
                                            Timber.d("NO IMAGE FOUND AT INDEX %d", index);
                                            indexProcessInProgress.remove(index);
                                            notifyDownloadProgress(-1, index);
                                            return;
                                        }

                                        int downloadedPageIndex = resultOpt.get().left;

                                        synchronized (viewerImagesInternal) {
                                            if (viewerImagesInternal.size() <= downloadedPageIndex)
                                                return;

                                            // Instanciate a new ImageFile not to modify the one used by the UI
                                            ImageFile downloadedPic = new ImageFile(viewerImagesInternal.get(downloadedPageIndex));
                                            downloadedPic.setFileUri(resultOpt.get().middle);
                                            downloadedPic.setMimeType(resultOpt.get().right);

                                            viewerImagesInternal.remove(downloadedPageIndex);
                                            viewerImagesInternal.add(downloadedPageIndex, downloadedPic);
                                            Timber.d("REPLACING INDEX %d - ORDER %d -> %s", downloadedPageIndex, downloadedPic.getOrder(), downloadedPic.getFileUri());

                                            // Instanciate a new list to trigger an actual Adapter UI refresh
                                            viewerImages.postValue(new ArrayList<>(viewerImagesInternal));
                                            imageLocationCache.put(downloadedPic.getOrder(), downloadedPic.getFileUri());
                                        }
                                    },
                                    Timber::w
                            )
            );
        }
    }

    /**
     * Extract the picture files at the given indexes from the given archive to the given folder
     *
     * @param indexesToLoad DB indexes of the pictures to download
     * @param archiveFile   Archive file to extract from
     * @param targetFolder  Folder to extract the files to
     */
    private void extractPics(
            @NonNull List<Integer> indexesToLoad,
            @NonNull DocumentFile archiveFile,
            @NonNull File targetFolder
    ) {
        compositeDisposable.add(
                Completable.fromRunnable(() -> doExtractPics(indexesToLoad, archiveFile, targetFolder))
                        .subscribeOn(Schedulers.computation())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe()
        );
    }

    private void doExtractPics(
            @NonNull List<Integer> indexesToLoad,
            @NonNull DocumentFile archiveFile,
            @NonNull File targetFolder
    ) {
        // Interrupt current extracting process, if any
        if (!indexProcessInProgress.isEmpty()) {
            interruptArchiveExtract.set(true);
            // Wait until extraction has actually stopped
            int remainingIterations = 15; // Timeout
            do {
                Helper.pause(500);
            } while (!indexProcessInProgress.isEmpty() && remainingIterations-- > 0);
            if (!indexProcessInProgress.isEmpty()) return;
        }
        indexProcessInProgress.addAll(indexesToLoad);

        Content theContent = getContent().getValue();
        if (null == theContent) return;

        List<Pair<String, String>> extractInstructions = new ArrayList<>();
        for (Integer index : indexesToLoad) {
            if (index < 0 || index >= viewerImagesInternal.size()) continue;
            ImageFile img = viewerImagesInternal.get(index);
            if (!img.getFileUri().isEmpty()) continue;

            extractInstructions.add(new Pair<>(img.getUrl().replace(theContent.getStorageUri() + File.separator, ""), theContent.getId() + "." + index));
        }

        Timber.d("Extracting %d files starting from index %s", extractInstructions.size(), indexesToLoad.get(0));

        Observable<Uri> observable = Observable.create(emitter ->
                ArchiveHelper.extractArchiveEntries(
                        getApplication(),
                        archiveFile.getUri(),
                        targetFolder,
                        extractInstructions,
                        interruptArchiveExtract,
                        emitter)
        );

        AtomicInteger nbProcessed = new AtomicInteger();
        archiveExtractDisposable = observable
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .doOnComplete(
                        // Called this way to properly run on io thread
                        () -> cacheJson(getApplication().getApplicationContext(), theContent)
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        uri -> {
                            nbProcessed.getAndIncrement();
                            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, R.id.viewer_load, 0, nbProcessed.get(), 0, indexesToLoad.size()));
                            Optional<Pair<Integer, ImageFile>> img = mapUriToImageFile(theContent.getId(), viewerImagesInternal, uri);
                            if (img.isPresent()) {
                                indexProcessInProgress.remove(img.get().first);

                                // Instanciate a new ImageFile not to modify the one used by the UI
                                ImageFile extractedPic = new ImageFile(img.get().second);
                                extractedPic.setFileUri(uri.toString());
                                extractedPic.setMimeType(ImageHelper.getMimeTypeFromUri(getApplication().getApplicationContext(), uri));

                                synchronized (viewerImagesInternal) {
                                    viewerImagesInternal.remove(img.get().first.intValue());
                                    viewerImagesInternal.add(img.get().first, extractedPic);
                                    Timber.v("Extracting : replacing index %d - order %d -> %s (%s)", img.get().first, extractedPic.getOrder(), extractedPic.getFileUri(), extractedPic.getMimeType());

                                    // Instanciate a new list to trigger an actual Adapter UI refresh every 4 iterations
                                    if (0 == nbProcessed.get() % 4 || nbProcessed.get() == extractInstructions.size())
                                        viewerImages.postValue(new ArrayList<>(viewerImagesInternal));
                                }
                                imageLocationCache.put(extractedPic.getOrder(), extractedPic.getFileUri());
                            }
                        },
                        t -> {
                            Timber.e(t);
                            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, R.id.viewer_load, 0, nbProcessed.get(), 0, indexesToLoad.size()));
                            indexProcessInProgress.clear();
                            interruptArchiveExtract.set(false);
                            archiveExtractDisposable.dispose();
                        },
                        () -> {
                            Timber.d("Extracted %d files successfuly", extractInstructions.size());
                            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, R.id.viewer_load, 0, nbProcessed.get(), 0, indexesToLoad.size()));
                            indexProcessInProgress.clear();
                            interruptArchiveExtract.set(false);
                            archiveExtractDisposable.dispose();
                        }
                );
    }

    /**
     * Download the picture at the given index to the given folder
     *
     * @param pageIndex    Index of the picture to download
     * @param targetFolder Folder to download to
     * @param stopDownload Switch to interrupt the download
     * @return Optional triple with
     * - The page index
     * - The Uri of the downloaded file
     * - The Mime-type of the downloaded file
     * <p>
     * The return value is empty if the download fails
     */
    private Optional<ImmutableTriple<Integer, String, String>> downloadPic(
            int pageIndex,
            @NonNull File targetFolder,
            @NonNull final AtomicBoolean stopDownload) {
        Helper.assertNonUiThread();
        Content content = getContent().getValue();
        if (null == content || viewerImagesInternal.size() <= pageIndex)
            return Optional.empty();

        ImageFile img = viewerImagesInternal.get(pageIndex);
        // Already downloaded
        if (!img.getFileUri().isEmpty())
            return Optional.of(new ImmutableTriple<>(pageIndex, img.getFileUri(), img.getMimeType()));

        // Initiate download
        try {
            final String targetFileName = content.getId() + "." + pageIndex;
            File[] existing = targetFolder.listFiles((dir, name) -> name.equalsIgnoreCase(targetFileName));
            String mimeType = ImageHelper.MIME_IMAGE_GENERIC;
            if (existing != null) {
                File targetFile;
                // No cached image -> fetch online
                if (0 == existing.length) {
                    // Prepare request headers
                    List<Pair<String, String>> headers = new ArrayList<>();
                    headers.add(new Pair<>(HttpHelper.HEADER_REFERER_KEY, content.getReaderUrl())); // Useful for Hitomi and Toonily

                    ImmutablePair<File, String> result;
                    if (img.needsPageParsing()) {
                        // Get cookies from the app jar
                        String cookieStr = HttpHelper.getCookies(img.getPageUrl());
                        // If nothing found, peek from the site
                        if (cookieStr.isEmpty())
                            cookieStr = HttpHelper.peekCookies(img.getPageUrl());
                        if (!cookieStr.isEmpty())
                            headers.add(new Pair<>(HttpHelper.HEADER_COOKIE_KEY, cookieStr));
                        result = downloadPictureFromPage(content, img, pageIndex, headers, targetFolder, targetFileName, stopDownload);
                    } else {
                        // Get cookies from the app jar
                        String cookieStr = HttpHelper.getCookies(img.getUrl());
                        // If nothing found, peek from the site
                        if (cookieStr.isEmpty())
                            cookieStr = HttpHelper.peekCookies(content.getGalleryUrl());
                        if (!cookieStr.isEmpty())
                            headers.add(new Pair<>(HttpHelper.HEADER_COOKIE_KEY, cookieStr));
                        result = DownloadHelper.downloadToFile(
                                content.getSite(),
                                img.getUrl(),
                                pageIndex,
                                headers,
                                targetFolder,
                                targetFileName,
                                null,
                                stopDownload,
                                f -> notifyDownloadProgress(f, pageIndex)
                        );
                    }
                    targetFile = result.left;
                    mimeType = result.right;
                } else { // Image is already there
                    targetFile = existing[0];
                    Timber.d("PIC %d FOUND AT %s (%.2f KB)", pageIndex, targetFile.getAbsolutePath(), targetFile.length() / 1024.0);
                }

                return Optional.of(new ImmutableTriple<>(pageIndex, Uri.fromFile(targetFile).toString(), mimeType));
            }
        } catch (DownloadInterruptedException ie) {
            Timber.d("Download interrupted for pic %d", pageIndex);
        } catch (Exception e) {
            Timber.w(e);
        }
        return Optional.empty();
    }

    /**
     * Download the picture represented by the given ImageFile to the given disk location
     *
     * @param content           Corresponding Content
     * @param img               ImageFile of the page to download
     * @param pageIndex         Index of the page to download
     * @param requestHeaders    HTTP request headers to use
     * @param targetFolder      Folder where to save the downloaded resource
     * @param targetFileName    Name of the file to save the downloaded resource
     * @param interruptDownload Used to interrupt the download whenever the value switches to true. If that happens, the file will be deleted.
     * @return Pair containing
     * - Left : Downloaded file
     * - Right : Detected mime-type of the downloades resource
     * @throws UnsupportedContentException, IOException, LimitReachedException, EmptyResultException, DownloadInterruptedException in case something horrible happens
     */
    private ImmutablePair<File, String> downloadPictureFromPage(@NonNull Content content,
                                                                @NonNull ImageFile img,
                                                                int pageIndex,
                                                                List<Pair<String, String>> requestHeaders,
                                                                @NonNull File targetFolder,
                                                                @NonNull String targetFileName,
                                                                @NonNull final AtomicBoolean interruptDownload) throws
            UnsupportedContentException, IOException, LimitReachedException, EmptyResultException, DownloadInterruptedException {
        Site site = content.getSite();
        String pageUrl = HttpHelper.fixUrl(img.getPageUrl(), site.getUrl());
        ImageListParser parser = ContentParserFactory.getInstance().getImageListParser(content.getSite());
        ImmutablePair<String, Optional<String>> pages = parser.parseImagePage(pageUrl, requestHeaders);
        img.setUrl(pages.left);
        // Download the picture
        try {
            return DownloadHelper.downloadToFile(
                    content.getSite(),
                    img.getUrl(),
                    pageIndex,
                    requestHeaders,
                    targetFolder,
                    targetFileName,
                    null,
                    interruptDownload,
                    f -> notifyDownloadProgress(f, pageIndex)
            );
        } catch (IOException e) {
            if (pages.right.isPresent()) Timber.d("First download failed; trying backup URL");
            else throw e;
        }
        // Trying with backup URL
        img.setUrl(pages.right.get());
        return DownloadHelper.downloadToFile(
                content.getSite(),
                img.getUrl(),
                pageIndex,
                requestHeaders,
                targetFolder,
                targetFileName,
                null,
                interruptDownload,
                f -> notifyDownloadProgress(f, pageIndex)
        );
    }

    /**
     * Send the current book to the queue to be reparsed from scratch
     *
     * @param onError Consumer to call in case reparsing fails
     */
    public void reparseBook(Consumer<Throwable> onError) {
        Content theContent = content.getValue();
        if (null == theContent) return;

        compositeDisposable.add(
                Observable.fromIterable(Stream.of(theContent).toList())
                        .observeOn(Schedulers.io())
                        .map(ContentHelper::reparseFromScratch)
                        .doOnNext(c -> {
                            if (c.right.isEmpty()) throw new EmptyResultException();
                            dao.addContentToQueue(
                                    c.right.get(), StatusContent.SAVED, ContentHelper.QueuePosition.TOP, -1,
                                    ContentQueueManager.getInstance().isQueueActive(getApplication()));
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnComplete(() -> {
                            if (Preferences.isQueueAutostart())
                                ContentQueueManager.getInstance().resumeQueue(getApplication());
                        })
                        .subscribe(
                                v -> { // Nothing; feedback is done through LiveData
                                },
                                onError::accept
                        )
        );
    }

    /**
     * Notify the download progress of the given page
     *
     * @param progressPc % progress to display
     * @param pageIndex  Index of downloaded page
     */
    private void notifyDownloadProgress(float progressPc, int pageIndex) {
        notificationDisposables.add(Completable.fromRunnable(() -> doNotifyDownloadProgress(progressPc, pageIndex))
                .subscribeOn(Schedulers.computation())
                .subscribe()
        );
    }

    /**
     * Notify the download progress of the given page
     *
     * @param progressPc % progress to display
     * @param pageIndex  Index of downloaded page
     */
    private void doNotifyDownloadProgress(float progressPc, int pageIndex) {
        int progress = (int) Math.floor(progressPc);
        if (progress < 0) { // Error
            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.FAILURE, R.id.viewer_page_download, pageIndex, 0, 100, 100));
        } else if (progress < 100) {
            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, R.id.viewer_page_download, pageIndex, progress, 0, 100));
        } else {
            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, R.id.viewer_page_download, pageIndex, progress, 0, 100));
        }
    }

    /**
     * Strip all chapters from the current Content
     * NB : All images are kept; only chapters are removed
     *
     * @param onError Callback in case processing fails
     */
    public void stripChapters(@NonNull Consumer<Throwable> onError) {
        Content theContent = content.getValue();
        if (null == theContent) return;

        compositeDisposable.add(
                Completable.fromRunnable(() -> dao.deleteChapters(theContent))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> loadDatabaseImages(theContent, -1), // Force reload images
                                e -> {
                                    Timber.e(e);
                                    onError.accept(e);
                                }
                        )
        );
    }

    /**
     * Create or remove a chapter at the given position
     * - If the given position is the first page of a chapter -> remove this chapter
     * - If not, create a new chapter at this position
     *
     * @param selectedPage Position to remove or create a chapter at
     * @param onError      Callback in case processing fails
     */
    public void createRemoveChapter(@NonNull ImageFile selectedPage, @NonNull Consumer<Throwable> onError) {
        Content theContent = content.getValue();
        if (null == theContent) return;

        compositeDisposable.add(
                Completable.fromRunnable(() -> doCreateRemoveChapter(theContent.getId(), selectedPage.getId()))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> loadDatabaseImages(theContent, -1), // Force reload images
                                e -> {
                                    Timber.e(e);
                                    onError.accept(e);
                                }
                        )
        );
    }

    /**
     * Create or remove a chapter at the given position
     * * - If the given position is the first page of a chapter -> remove this chapter
     * * - If not, create a new chapter at this position
     *
     * @param contentId      ID of the corresponding content
     * @param selectedPageId ID of the page to remove or create a chapter at
     */
    private void doCreateRemoveChapter(long contentId, long selectedPageId) {
        Helper.assertNonUiThread();
        String chapterStr = getApplication().getString(R.string.gallery_chapter_prefix);
        if (null == VANILLA_CHAPTERNAME_PATTERN)
            VANILLA_CHAPTERNAME_PATTERN = Pattern.compile(chapterStr + " [0-9]+");

        Content theContent = dao.selectContent(contentId); // Work on a fresh content
        if (null == theContent) throw new IllegalArgumentException("No content found");

        ImageFile selectedPage = dao.selectImageFile(selectedPageId);
        Chapter currentChapter = selectedPage.getLinkedChapter();
        // Creation of the very first chapter of the book -> unchaptered pages are considered as "chapter 1"
        if (null == currentChapter) {
            currentChapter = new Chapter(1, "", chapterStr + " 1");
            List<ImageFile> workingList = theContent.getImageFiles();
            if (workingList != null) {
                currentChapter.setImageFiles(workingList);
                // Link images the other way around so that what follows works properly
                for (ImageFile img : workingList) img.setChapter(currentChapter);
            }
            currentChapter.setContent(theContent);
        }

        List<ImageFile> chapterImages = currentChapter.getImageFiles();
        if (null == chapterImages || chapterImages.isEmpty())
            throw new IllegalArgumentException("No images found for selection");

        if (selectedPage.getOrder() < 2)
            throw new IllegalArgumentException("Can't create or remove chapter on first page");

        // If we tap the 1st page of an existing chapter, it means we're removing it
        Optional<ImageFile> firstChapterPic = Stream.of(chapterImages).sortBy(ImageFile::getOrder).findFirst();
        boolean isRemoving = (firstChapterPic.get().getOrder().intValue() == selectedPage.getOrder().intValue());

        if (isRemoving) doRemoveChapter(theContent, currentChapter, chapterImages);
        else doCreateChapter(theContent, selectedPage, currentChapter, chapterImages);

        // Rearrange all chapters

        // Work on a clean image set directly from the DAO
        // (we don't want to depend on LiveData being on time here)
        List<ImageFile> theViewerImages = dao.selectDownloadedImagesFromContent(theContent.getId());
        // Rely on the order of pictures to get chapter in the right order
        List<Chapter> allChapters = Stream.of(theViewerImages)
                .map(ImageFile::getLinkedChapter)
                .distinct()
                .withoutNulls()
                .filter(c -> c.getOrder() > -1)
                .toList();

        // Renumber all chapters to reflect changes
        int order = 1;
        List<Chapter> updatedChapters = new ArrayList<>();
        for (Chapter c : allChapters) {
            // Update names with the default "Chapter x" naming
            if (VANILLA_CHAPTERNAME_PATTERN.matcher(c.getName()).matches())
                c.setName(chapterStr + " " + order);
            // Update order
            c.setOrder(order++);
            c.setContent(theContent);
            updatedChapters.add(c);
        }

        // Save chapters
        dao.insertChapters(updatedChapters);
    }

    /**
     * Create a chapter at the given position, which will become the 1st page of the new chapter
     *
     * @param content        Corresponding Content
     * @param selectedPage   Position to create a new chapter at
     * @param currentChapter Current chapter at the given position
     * @param chapterImages  Images of the current chapter at the given position
     */
    private void doCreateChapter(
            @NonNull Content content,
            @NonNull ImageFile selectedPage,
            @NonNull Chapter currentChapter,
            @NonNull List<ImageFile> chapterImages
    ) {
        int newChapterOrder = currentChapter.getOrder() + 1;
        Chapter newChapter = new Chapter(newChapterOrder, "", getApplication().getString(R.string.gallery_chapter_prefix) + " " + newChapterOrder);
        newChapter.setContent(content);

        // Sort by order
        chapterImages = Stream.of(chapterImages).sortBy(ImageFile::getOrder).toList();

        // Split pages
        int firstPageOrder = selectedPage.getOrder();
        int lastPageOrder = chapterImages.get(chapterImages.size() - 1).getOrder();
        for (ImageFile img : chapterImages)
            if (img.getOrder() >= firstPageOrder && img.getOrder() <= lastPageOrder) {
                Chapter oldChapter = img.getLinkedChapter();
                if (oldChapter != null) oldChapter.removeImageFile(img);
                img.setChapter(newChapter);
                newChapter.addImageFile(img);
            }

        // Save images
        dao.insertImageFiles(chapterImages);
    }

    /**
     * Remove the given chapter
     * All pages from this chapter will be affected to the preceding chapter
     *
     * @param content       Corresponding Content
     * @param toRemove      Chapter to remove
     * @param chapterImages Images of the chapter to remove
     */
    private void doRemoveChapter(
            @NonNull Content content,
            @NonNull Chapter toRemove,
            @NonNull List<ImageFile> chapterImages
    ) {
        List<Chapter> contentChapters = content.getChapters();
        if (null == contentChapters) return;

        contentChapters = Stream.of(contentChapters).sortBy(Chapter::getOrder).toList();
        int removeOrder = toRemove.getOrder();

        // Identify preceding chapter
        Chapter precedingChapter = null;
        for (Chapter c : contentChapters) {
            if (c.getOrder() == removeOrder) break;
            precedingChapter = c;
        }

        // Pages of selected chapter will join the preceding chapter
        for (ImageFile img : chapterImages) img.setChapter(precedingChapter);
        dao.insertImageFiles(chapterImages);
        dao.deleteChapter(toRemove);
    }

    /**
     * Move the chapter of the current Content from oldIndex to newIndex and renumber pages & files accordingly
     *
     * @param oldIndex Old index (0-based) of the chapter to move
     * @param newIndex New index (0-based) of the chapter to move
     * @param onError  Callback in case processing fails
     */
    public void moveChapter(int oldIndex, int newIndex, Consumer<Throwable> onError) {
        Content theContent = content.getValue();
        if (null == theContent) return;

        compositeDisposable.add(
                Completable.fromRunnable(() -> doMoveChapter(theContent.getId(), oldIndex, newIndex))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> { // Force reload the whole Content
                                    Content updatedContent = dao.selectContent(theContent.getId());
                                    if (updatedContent != null) loadContent(updatedContent, -1);
                                },
                                e -> {
                                    Timber.e(e);
                                    onError.accept(e);
                                }
                        )
        );
    }

    /**
     * Move the chapter of the given Content from oldPosition to newPosition and renumber pages & files accordingly
     *
     * @param contentId ID of the Content to work on
     * @param oldIndex  Old index (0-based) of the chapter to move
     * @param newIndex  New index (0-based) of the chapter to move
     */
    private void doMoveChapter(long contentId, int oldIndex, int newIndex) {
        Helper.assertNonUiThread();
        String chapterStr = getApplication().getString(R.string.gallery_chapter_prefix);
        if (null == VANILLA_CHAPTERNAME_PATTERN)
            VANILLA_CHAPTERNAME_PATTERN = Pattern.compile(chapterStr + " [0-9]+");

        List<Chapter> chapters = dao.selectChapters(contentId);
        if (null == chapters || chapters.isEmpty())
            throw new IllegalArgumentException("No chapters found");

        if (oldIndex < 0 || oldIndex >= chapters.size()) return;

        // Move the item
        Chapter fromValue = chapters.get(oldIndex);
        int delta = oldIndex < newIndex ? 1 : -1;
        for (int i = oldIndex; i != newIndex; i += delta) {
            chapters.set(i, chapters.get(i + delta));
        }
        chapters.set(newIndex, fromValue);

        // Renumber all chapters and update the DB
        int index = 1;
        for (Chapter c : chapters) {
            // Update names with the default "Chapter x" naming
            if (VANILLA_CHAPTERNAME_PATTERN.matcher(c.getName()).matches())
                c.setName(chapterStr + " " + index);
            // Update order
            c.setOrder(index++);
        }
        dao.insertChapters(chapters);

        // Renumber all readable images and update the DB
        List<ImageFile> images = Stream.of(chapters).map(Chapter::getImageFiles).withoutNulls().flatMap(Stream::of).toList();
        if (images.isEmpty())
            throw new IllegalArgumentException("No images found");

        index = 1;
        int nbMaxDigits = images.get(images.size() - 1).getName().length(); // Keep existing formatting
        Map<String, ImageFile> fileNames = new HashMap<>();
        for (ImageFile img : images) {
            if (img.isReadable()) {
                img.setOrder(index++);
                img.computeName(nbMaxDigits);
                fileNames.put(img.getFileUri(), img);
            }
        }

        // = Rename all files that need to be renamed

        // Compute all renaming tasks
        List<ImmutableTriple<ImageFile, DocumentFile, String>> firstPass = new ArrayList<>();
        DocumentFile parentFolder = FileHelper.getFolderFromTreeUriString(getApplication(), chapters.get(0).getContent().getTarget().getStorageUri());
        if (parentFolder != null) {
            List<DocumentFile> contentFiles = FileHelper.listFiles(getApplication(), parentFolder, null);
            for (DocumentFile doc : contentFiles) {
                ImageFile img = fileNames.get(doc.getUri().toString());
                if (img != null) {
                    String docName = doc.getName();
                    if (docName != null) {
                        String rawName = FileHelper.getFileNameWithoutExtension(docName);
                        if (!rawName.equals(img.getName())) {
                            Timber.d("Adding to 1st pass : %s != %s", rawName, img.getName());
                            String extension = FileHelper.getExtension(docName);
                            firstPass.add(new ImmutableTriple<>(img, doc, img.getName() + "." + extension));
                        }
                    }
                }
            }
        }

        // Moving pages inside the same folder invariably result in having to give files a name that is already taken during processing
        // => need to do two passes to make sure every file eventually gets its right name

        // Keep an snapshot of all filenames to keep track of which files belong to the 2nd pass
        List<String> existingNames = Stream.of(firstPass).map(i -> i.getMiddle().getName()).withoutNulls().toList();
        List<ImmutableTriple<ImageFile, DocumentFile, String>> secondPass = new ArrayList<>();

        // Run 1st pass
        int nbImages = firstPass.size();
        int nbProcessedPics = 1;
        for (ImmutableTriple<ImageFile, DocumentFile, String> renamingTask : firstPass) {
            DocumentFile doc = renamingTask.middle;
            String existingName = StringHelper.protect(doc.getName());
            String newName = renamingTask.getRight();
            if (existingNames.contains(newName)) secondPass.add(renamingTask);
            doc.renameTo(newName);
            renamingTask.getLeft().setFileUri(doc.getUri().toString());
            existingNames.remove(existingName);
            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, R.id.generic_progress, 0, nbProcessedPics++, 0, nbImages));
        }

        // Run 2nd pass
        nbImages = secondPass.size();
        nbProcessedPics = 1;
        for (ImmutableTriple<ImageFile, DocumentFile, String> renamingTask : secondPass) {
            DocumentFile doc = renamingTask.middle;
            String newName = renamingTask.getRight();
            doc.renameTo(newName);
            renamingTask.getLeft().setFileUri(doc.getUri().toString());
            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, R.id.generic_progress, 0, nbProcessedPics++, 0, nbImages));
        }

        dao.insertImageFiles(images);

        EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, R.id.generic_progress, 0, nbImages, 0, nbImages));

        // Reset locations cache as image order has changed
        imageLocationCache.clear();
    }
}