package me.devsaki.hentoid.viewmodels;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

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
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
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
import me.devsaki.hentoid.util.exception.ContentNotProcessedException;
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

    private static final Pattern VANILLA_CHAPTERNAME_PATTERN = Pattern.compile("Chapter [0-9]+");

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
    // Switch to interrupt unarchiving when leaving the activity
    private final AtomicBoolean interruptArchiveLoad = new AtomicBoolean(false);
    // Page indexes that are being downloaded
    private final Set<Integer> downloadsInProgress = Collections.synchronizedSet(new HashSet<>());
    // FIFO switches to interrupt downloads when browsing the book
    private final Queue<AtomicBoolean> downloadsQueue = new ConcurrentLinkedQueue<>();

    // Technical
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private final CompositeDisposable imageDownloadDisposable = new CompositeDisposable();
    private final CompositeDisposable notificationDisposables = new CompositeDisposable();
    private Disposable searchDisposable = Disposables.empty();
    private Disposable unarchiveDisposable = Disposables.empty();
    private Disposable imageLoadDisposable = Disposables.empty();
    private Disposable leaveDisposable = Disposables.empty();
    private Disposable emptyCacheDisposable = Disposables.empty();
    private boolean isArchiveExtracting = false;


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

    public void loadFromContent(long contentId, int pageNumber) {
        if (contentId > 0) {
            Content loadedContent = dao.selectContent(contentId);
            if (loadedContent != null)
                processContent(loadedContent, pageNumber);
        }
    }

    public void loadFromSearchParams(long contentId, int pageNumber, @NonNull Bundle bundle) {
        searchManager.loadFromBundle(bundle);
        applySearchParams(contentId, pageNumber);
    }

    private void applySearchParams(long contentId, int pageNumber) {
        searchDisposable.dispose();
        searchDisposable = searchManager.searchLibraryForId().subscribe(
                list -> {
                    contentIds = list;
                    loadFromContent(contentId, pageNumber);
                },
                throwable -> {
                    Timber.w(throwable);
                    ToastHelper.toast("Book list loading failed");
                }
        );
    }

    public void setReaderStartingIndex(int index) {
        startingIndex.postValue(index);
    }

    private void setImages(@NonNull Content theContent, int pageNumber, @NonNull List<ImageFile> newImages) {
        Observable<ImageFile> observable;

        databaseImages.postValue(newImages);

        // Don't reload from disk / archive again if the image list hasn't changed
        // e.g. page favourited
        if (imageLocationCache.isEmpty() || newImages.size() != imageLocationCache.size()) {
            if (theContent.isArchive())
                observable = Observable.create(emitter -> processArchiveImages(theContent, newImages, interruptArchiveLoad, emitter));
            else
                observable = Observable.create(emitter -> processDiskImages(theContent, newImages, emitter));

            AtomicInteger nbProcessed = new AtomicInteger();
            imageLoadDisposable = observable
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .doOnComplete(
                            // Called this way to properly run on io thread
                            () -> cacheJson(getApplication().getApplicationContext(), theContent)
                    )
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            imageFile -> {
                                nbProcessed.getAndIncrement();
                                EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, R.id.viewer_load, 0, nbProcessed.get(), 0, newImages.size()));
                            },
                            t -> {
                                Timber.e(t);
                                EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, R.id.viewer_load, 0, nbProcessed.get(), 0, newImages.size()));
                            },
                            () -> {
                                EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, R.id.viewer_load, 0, nbProcessed.get(), 0, newImages.size()));
                                for (ImageFile img : newImages)
                                    imageLocationCache.put(img.getOrder(), img.getFileUri());
                                initViewer(theContent, -1, newImages);
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

            initViewer(theContent, pageNumber, newImages);
        }
    }

    private void processDiskImages(
            @NonNull Content theContent,
            @NonNull List<ImageFile> newImages,
            @NonNull final ObservableEmitter<ImageFile> emitter) {
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

        emitter.onComplete();
    }

    public void onActivityLeave() {
        // Dispose the composite disposables for good
        imageDownloadDisposable.dispose();
        notificationDisposables.dispose();
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

    private synchronized void processArchiveImages(
            @NonNull Content theContent,
            @NonNull List<ImageFile> newImages,
            @NonNull AtomicBoolean interrupt,
            @NonNull final ObservableEmitter<ImageFile> emitter) throws IOException {
        if (!theContent.isArchive())
            throw new IllegalArgumentException("Content must be an archive");

        if (isArchiveExtracting) return;

        List<ImageFile> newImageFiles = new ArrayList<>(newImages);
        List<ImageFile> currentImages = databaseImages.getValue();
        if ((!newImages.isEmpty() && loadedContentId != newImages.get(0).getContent().getTargetId()) || null == currentImages) { // Load a new book
            // TODO create a smarter cache that works with a window of a given size
            File cachePicFolder = FileHelper.getOrCreateCacheFolder(getApplication(), Consts.PICTURE_CACHE_FOLDER);
            if (cachePicFolder != null) {
                // Empty the cache folder where previous cached images might be
                File[] files = cachePicFolder.listFiles();
                if (files != null)
                    for (File f : files)
                        if (!f.delete()) Timber.w("Unable to delete file %s", f.getAbsolutePath());

                // Extract the images if they are contained within an archive
                // Unzip the archive in the app's cache folder
                DocumentFile archiveFile = FileHelper.getFileFromSingleUriString(getApplication(), theContent.getStorageUri());
                // TODO replace that with a proper on-demand loading - see #706
                if (archiveFile != null) {
                    interrupt.set(false);
                    isArchiveExtracting = true;
                    unarchiveDisposable = ArchiveHelper.extractArchiveEntriesRx(
                            getApplication(),
                            archiveFile,
                            Stream.of(newImageFiles).filter(i -> i.getFileUri().startsWith(theContent.getStorageUri())).map(i -> i.getFileUri().replace(theContent.getStorageUri() + File.separator, "")).toList(),
                            cachePicFolder,
                            null,
                            interrupt)
                            .subscribeOn(Schedulers.io())
                            .observeOn(Schedulers.computation())
                            .subscribe(
                                    uri -> emitter.onNext(mapUriToImageFile(newImages, uri)),
                                    t -> {
                                        Timber.e(t);
                                        isArchiveExtracting = false;
                                    },
                                    () -> {
                                        isArchiveExtracting = false;
                                        emitter.onComplete();
                                        unarchiveDisposable.dispose();
                                    }
                            );
                }
            }
        } else { // Refresh current book with new data
            for (int i = 0; i < newImageFiles.size(); i++) {
                ImageFile newImg = newImageFiles.get(i);
                String location = imageLocationCache.get(newImg.getOrder());
                newImg.setFileUri(location);
            }

            // Replace initial images with updated images
            newImages.clear();
            newImages.addAll(newImageFiles);

            emitter.onComplete();
        }
    }

    /**
     * Map the given file Uri to its corresponding ImageFile in the given list, using their display name
     *
     * @param imageFiles List of ImageFiles to map the given Uri to
     * @param uri        File Uri to map to one of the elements of the given list
     * @return Matched ImageFile with the valued Uri if found; empty ImageFile if not found
     */
    private ImageFile mapUriToImageFile(@NonNull final List<ImageFile> imageFiles, @NonNull final Uri uri) {
        String path = uri.getPath();
        if (null == path) return new ImageFile();

        // Feed the Uri's of unzipped files back into the corresponding images for viewing
        for (ImageFile img : imageFiles) {
            if (FileHelper.getFileNameWithoutExtension(img.getFileUri()).equalsIgnoreCase(ArchiveHelper.extractFileNameFromCacheName(path))) {
                return img.setFileUri(uri.toString());
            }
        }
        return new ImageFile();
    }

    private void initViewer(@NonNull Content theContent, int pageNumber, @NonNull List<ImageFile> imageFiles) {
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

            setReaderStartingIndex(startingIndex - thumbIndex - 1);

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

    public void toggleShuffle() {
        Boolean shuffledVal = getShuffled().getValue();
        boolean isShuffled = null != shuffledVal && shuffledVal;
        isShuffled = !isShuffled;
        if (isShuffled) RandomSeedSingleton.getInstance().renewSeed(Consts.SEED_PAGES);
        shuffled.postValue(isShuffled);

        List<ImageFile> imgs = databaseImages.getValue();
        if (imgs != null) sortAndSetViewerImages(imgs, isShuffled);
    }

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

        synchronized (viewerImagesInternal) {
            viewerImagesInternal.clear();
            viewerImagesInternal.addAll(imgs);
        }
        viewerImages.postValue(new ArrayList<>(viewerImagesInternal));
    }

    public void onLeaveBook(int readerIndex) {
        if (Preferences.Constant.VIEWER_DELETE_ASK_BOOK == Preferences.getViewerDeleteAskMode())
            Preferences.setViewerDeleteAskMode(Preferences.Constant.VIEWER_DELETE_ASK_AGAIN);

        // Stop any ongoing picture loading
        unarchiveDisposable.dispose();
        imageLoadDisposable.dispose();
        // Clear the composite disposables so that they can be reused
        imageDownloadDisposable.clear();
        notificationDisposables.clear();
        downloadsInProgress.clear();
        isArchiveExtracting = false;
        interruptArchiveLoad.set(true);

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
        int collectionIndex = readerIndex + thumbIndex + 1;
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

    public void filterFavouriteImages(boolean targetState) {
        if (loadedContentId > -1) {
            showFavouritesOnly.postValue(targetState);
            if (searchManager != null) searchManager.setFilterPageFavourites(targetState);
            applySearchParams(loadedContentId, -1);
        }
    }

    public void toggleImageFavourite(int viewerIndex, @NonNull Consumer<Boolean> successCallback) {
        ImageFile file = viewerImagesInternal.get(viewerIndex);
        boolean newState = !file.isFavourite();
        toggleImageFavourite(Stream.of(file).toList(), () -> successCallback.accept(newState));
    }

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
     * Toggles content favourite flag in DB and in the content JSON
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

    public void deleteBook(Consumer<Throwable> onError) {
        Content targetContent = dao.selectContent(loadedContentId);
        if (null == targetContent) return;

        // Unplug image source listener (avoid displaying pages as they are being deleted; it messes up with DB transactions)
        if (currentImageSource != null) databaseImages.removeSource(currentImageSource);

        compositeDisposable.add(
                Completable.fromAction(() -> doDeleteBook(targetContent))
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
                                            loadFromContent(contentIds.get(currentContentIndex), -1);
                                    } else { // Close the viewer if the list is empty (single book)
                                        content.postValue(null);
                                    }
                                },
                                e -> {
                                    onError.accept(e);
                                    // Restore image source listener on error
                                    databaseImages.addSource(currentImageSource, imgs -> setImages(targetContent, -1, imgs));
                                }
                        )
        );
    }

    private void doDeleteBook(@NonNull Content targetContent) throws ContentNotProcessedException {
        Helper.assertNonUiThread();
        ContentHelper.removeQueuedContent(getApplication(), dao, targetContent);
    }

    public void deletePage(int pageViewerIndex, Consumer<Throwable> onError) {
        List<ImageFile> imageFiles = viewerImagesInternal;
        if (imageFiles.size() > pageViewerIndex && pageViewerIndex > -1)
            deletePages(Stream.of(imageFiles.get(pageViewerIndex)).toList(), onError);
    }

    public void deletePages(List<ImageFile> pages, Consumer<Throwable> onError) {
        compositeDisposable.add(
                Completable.fromRunnable(() -> doDeletePages(pages))
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

    private void doDeletePages(@NonNull List<ImageFile> pages) {
        Helper.assertNonUiThread();
        ContentHelper.removePages(pages, dao, getApplication());
    }

    public void setCover(ImageFile page) {
        compositeDisposable.add(
                Completable.fromRunnable(() -> doSetCover(page))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> { // Update is done through LiveData
                                },
                                Timber::e
                        )
        );
    }

    private void doSetCover(@NonNull ImageFile page) {
        Helper.assertNonUiThread();
        ContentHelper.setContentCover(page, dao, getApplication());
    }

    public void loadNextContent(int readerIndex) {
        if (currentContentIndex < contentIds.size() - 1) {
            currentContentIndex++;
            if (!contentIds.isEmpty()) {
                onLeaveBook(readerIndex);
                loadFromContent(contentIds.get(currentContentIndex), -1);
            }
        }
    }

    public void loadPreviousContent(int readerIndex) {
        if (currentContentIndex > 0) {
            currentContentIndex--;
            if (!contentIds.isEmpty()) {
                onLeaveBook(readerIndex);
                loadFromContent(contentIds.get(currentContentIndex), -1);
            }
        }
    }

    private void processContent(@NonNull Content theContent, int pageNumber) {
        Preferences.setViewerCurrentContent(theContent.getId());
        currentContentIndex = contentIds.indexOf(theContent.getId());
        if (-1 == currentContentIndex) currentContentIndex = 0;

        theContent.setFirst(0 == currentContentIndex);
        theContent.setLast(currentContentIndex >= contentIds.size() - 1);
        if (contentIds.size() > currentContentIndex && loadedContentId != contentIds.get(currentContentIndex))
            imageLocationCache.clear();
        content.postValue(theContent);
        processImages(theContent, pageNumber);
    }

    private void processImages(@NonNull Content theContent, int pageNumber) {
        // Observe the content's images
        // NB : It has to be dynamic to be updated when viewing a book from the queue screen
        if (currentImageSource != null) databaseImages.removeSource(currentImageSource);
        currentImageSource = dao.selectDownloadedImagesFromContentLive(theContent.getId());
        databaseImages.addSource(currentImageSource, imgs -> setImages(theContent, pageNumber, imgs));
    }

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

    // Cache JSON URI in the database to speed up favouriting
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

    public void markPageAsRead(int pageNumber) {
        readPageNumbers.add(pageNumber);
    }

    public void repostImages() {
        viewerImages.postValue(viewerImages.getValue());
    }

    public synchronized void setCurrentPage(int pageIndex, int direction) {
        if (viewerImagesInternal.size() <= pageIndex) return;

        List<Integer> indexesToLoad = new ArrayList<>();
        int increment = (direction > 0) ? 1 : -1;
        if (isPictureDownloadable(pageIndex, viewerImagesInternal))
            indexesToLoad.add(pageIndex);
        if (isPictureDownloadable(pageIndex + increment, viewerImagesInternal))
            indexesToLoad.add(pageIndex + increment);
        if (isPictureDownloadable(pageIndex + 2 * increment, viewerImagesInternal))
            indexesToLoad.add(pageIndex + 2 * increment);

        if (indexesToLoad.isEmpty()) return;

        File cachePicFolder = FileHelper.getOrCreateCacheFolder(getApplication(), Consts.PICTURE_CACHE_FOLDER);
        if (cachePicFolder != null) {
            for (int index : indexesToLoad) {
                if (downloadsInProgress.contains(index)) continue;
                downloadsInProgress.add(index);

                // Adjust the current queue
                while (downloadsQueue.size() >= CONCURRENT_DOWNLOADS) {
                    AtomicBoolean stopDownload = downloadsQueue.poll();
                    if (stopDownload != null) stopDownload.set(true);
                    Timber.d("Aborting a download");
                }
                // Schedule a new download
                AtomicBoolean stopDownload = new AtomicBoolean(false);
                downloadsQueue.add(stopDownload);

                imageDownloadDisposable.add(
                        Single.fromCallable(() -> downloadPic(index, cachePicFolder, stopDownload))
                                .subscribeOn(Schedulers.io())
                                .observeOn(Schedulers.computation())
                                .subscribe(
                                        resultOpt -> {
                                            if (resultOpt.isEmpty()) { // Nothing to download
                                                Timber.d("NO IMAGE FOUND AT INDEX %d", index);
                                                downloadsInProgress.remove(index);
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
    }

    /**
     * Indicate if the given page index is a downloadable picture in the given list
     *
     * @param pageIndex Index to test
     * @param images    List of pictures to test against
     * @return True if the given index is a downloadable picture; false if not
     */
    private boolean isPictureDownloadable(int pageIndex, @NonNull List<ImageFile> images) {
        return pageIndex > -1
                && images.size() > pageIndex
                && images.get(pageIndex).getStatus().equals(StatusContent.ONLINE)
                && images.get(pageIndex).getFileUri().isEmpty();
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

    // TODO doc
    private ImmutablePair<File, String> downloadPictureFromPage(@NonNull Content content,
                                                                @NonNull ImageFile img,
                                                                int pageIndex,
                                                                List<Pair<String, String>> requestHeaders,
                                                                @NonNull File targetFolder,
                                                                @NonNull String targetFileName,
                                                                @NonNull final AtomicBoolean stopDownload) throws
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
                    stopDownload,
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
                stopDownload,
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
                            if (c.isEmpty()) throw new EmptyResultException();
                            dao.addContentToQueue(
                                    c.get(), StatusContent.SAVED, ContentHelper.QueuePosition.TOP,
                                    ContentQueueManager.getInstance().isQueueActive());
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
            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.FAILURE, R.id.page_download, pageIndex, 0, 100, 100));
        } else if (progress < 100) {
            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, R.id.page_download, pageIndex, progress, 0, 100));
        } else {
            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, R.id.page_download, pageIndex, progress, 0, 100));
        }
    }

    // TODO doc
    public void removeChapters(@NonNull Consumer<Throwable> onError) {
        Content theContent = content.getValue();
        if (null == theContent) return;

        compositeDisposable.add(
                Completable.fromRunnable(() -> doRemoveChapters(theContent))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> processImages(theContent, -1), // Force reload images
                                e -> {
                                    Timber.e(e);
                                    onError.accept(e);
                                }
                        )
        );
    }

    // TODO doc
    private void doRemoveChapters(@NonNull Content theContent) {
        Helper.assertNonUiThread();

        dao.deleteChapters(theContent);
    }

    // TODO doc
    public void createRemoveChapter(@NonNull ImageFile img, @NonNull Consumer<Throwable> onError) {
        Content theContent = content.getValue();
        if (null == theContent) return;

        compositeDisposable.add(
                Completable.fromRunnable(() -> doCreateRemoveChapter(theContent.getId(), img))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> processImages(theContent, -1), // Force reload images
                                e -> {
                                    Timber.e(e);
                                    onError.accept(e);
                                }
                        )
        );
    }

    // TODO doc
    private void doCreateRemoveChapter(long contentId, @NonNull ImageFile selectedPage) {
        Helper.assertNonUiThread();

        Content content = dao.selectContent(contentId); // Work on a fresh content
        if (null == content) throw new IllegalArgumentException("No content found");

        Chapter previousChapter = selectedPage.getLinkedChapter();
        // Creation of the very first chapter of the book -> unchaptered pages are considered as "chapter 1"
        if (null == previousChapter) {
            previousChapter = new Chapter(1, "", "Chapter 1");
            previousChapter.setImageFiles(viewerImagesInternal);
            // Link images the other way around so that what follows works properly
            for (ImageFile img : viewerImagesInternal) img.setChapter(previousChapter);
            previousChapter.setContent(content);
        }

        List<ImageFile> chapterImages = previousChapter.getImageFiles();
        if (null == chapterImages || chapterImages.isEmpty())
            throw new IllegalArgumentException("No images found for selection");

        if (selectedPage.getOrder() < 2)
            throw new IllegalArgumentException("Can't create or remove chapter on first page");

        // If we tap the 1st page of an existing chapter, it means we're removing it
        Optional<ImageFile> firstChapterPic = Stream.of(chapterImages).sortBy(ImageFile::getOrder).findFirst();
        boolean isRemoving = (firstChapterPic.get().getOrder().intValue() == selectedPage.getOrder().intValue());

        if (isRemoving) doRemoveChapter(content, previousChapter, chapterImages);
        else doCreateChapter(content, selectedPage, previousChapter, chapterImages);

        // Rearrange all chapters

        // Work on a clean image set directly from the DAO
        // (we don't want to depend on LiveData being on time here)
        List<ImageFile> viewerImages = dao.selectDownloadedImagesFromContent(content.getId());
        // Rely on the order of pictures to get chapter in the right order
        List<Chapter> allChapters = Stream.of(viewerImages)
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
                c.setName("Chapter " + order);
            // Update order
            c.setOrder(order);
            c.setContent(content);
            order++;
            updatedChapters.add(c);
        }

        // Save chapters
        dao.insertChapters(updatedChapters);
    }

    // TODO doc
    private void doCreateChapter(
            @NonNull Content content,
            @NonNull ImageFile selectedPage,
            @NonNull Chapter previousChapter,
            @NonNull List<ImageFile> chapterImages
    ) {
        int newChapterOrder = previousChapter.getOrder() + 1;
        Chapter newChapter = new Chapter(newChapterOrder, "", "Chapter " + newChapterOrder);
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

    // TODO doc
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

    public void moveChapter(int oldPosition, int newPosition, Consumer<Throwable> onError) {
        Content theContent = content.getValue();
        if (null == theContent) return;

        compositeDisposable.add(
                Completable.fromRunnable(() -> doMoveChapter(theContent.getId(), oldPosition, newPosition))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> { // Force reload the whole Content
                                    Content updatedContent = dao.selectContent(theContent.getId());
                                    if (updatedContent != null) processContent(updatedContent, -1);
                                },
                                e -> {
                                    Timber.e(e);
                                    onError.accept(e);
                                }
                        )
        );
    }

    private void doMoveChapter(long contentId, int oldPosition, int newPosition) {
        Helper.assertNonUiThread();
        List<Chapter> chapters = dao.selectChapters(contentId);
        if (null == chapters || chapters.isEmpty())
            throw new IllegalArgumentException("No chapters found");

        if (oldPosition < 0 || oldPosition >= chapters.size()) return;

        // Move the item
        Chapter fromValue = chapters.get(oldPosition);
        int delta = oldPosition < newPosition ? 1 : -1;
        for (int i = oldPosition; i != newPosition; i += delta) {
            chapters.set(i, chapters.get(i + delta));
        }
        chapters.set(newPosition, fromValue);

        // Renumber all chapters and update the DB
        int index = 1;
        for (Chapter c : chapters) c.setOrder(index++);
        dao.insertChapters(chapters);

        // Renumber all images and update the DB
        List<ImageFile> images = Stream.of(chapters).map(Chapter::getImageFiles).withoutNulls().flatMap(Stream::of).toList();
        if (images.isEmpty())
            throw new IllegalArgumentException("No imagesfound");

        index = 1;
        int nbMaxDigits = images.get(images.size() - 1).getName().length(); // Keep existing formatting
        Map<String, ImageFile> fileNames = new HashMap<>();
        for (ImageFile img : images) {
            img.setOrder(index++);
            img.computeName(nbMaxDigits);
            fileNames.put(img.getFileUri(), img);
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