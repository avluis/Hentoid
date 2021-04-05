package me.devsaki.hentoid.viewmodels;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.annimon.stream.IntStream;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;

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
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.core.Consts;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.util.ArchiveHelper;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.RandomSeedSingleton;
import me.devsaki.hentoid.util.ToastHelper;
import me.devsaki.hentoid.util.exception.ContentNotRemovedException;
import me.devsaki.hentoid.widget.ContentSearchManager;
import timber.log.Timber;


public class ImageViewerViewModel extends AndroidViewModel {

    // Collection DAO
    private final CollectionDAO collectionDao;
    private final ContentSearchManager searchManager;

    // Collection data
    private final MutableLiveData<Content> content = new MutableLiveData<>();        // Current content
    private List<Long> contentIds = Collections.emptyList();                         // Content Ids of the whole collection ordered according to current filter
    private int currentContentIndex = -1;                                            // Index of current content within the above list
    private long loadedContentId = -1;                                               // ID of currently loaded book

    // Pictures data
    private LiveData<List<ImageFile>> currentImageSource;
    private final MediatorLiveData<List<ImageFile>> databaseImages = new MediatorLiveData<>();  // Set of image of current content
    private final MutableLiveData<List<ImageFile>> viewerImages = new MutableLiveData<>();     // Currently displayed set of images (reprocessed from databaseImages)
    private final MutableLiveData<Integer> startingIndex = new MutableLiveData<>();     // 0-based index of the current image
    private final MutableLiveData<Boolean> shuffled = new MutableLiveData<>();          // Shuffle state of the current book
    private final MutableLiveData<Boolean> showFavouritesOnly = new MutableLiveData<>();// True if viewer only shows favourite images; false if shows all pages
    private int thumbIndex;                                                             // Index of the thumbnail among loaded pages

    // Write cache for read indicator (no need to update DB and JSON at every page turn)
    private final Set<Integer> readPageNumbers = new HashSet<>();

    // TODO doc
    private final Map<Integer, String> imageLocations = new HashMap<>();

    // Technical
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private Disposable searchDisposable = Disposables.empty();
    private Disposable unarchiveDisposable = Disposables.empty();
    private Disposable imageLoadDisposable = Disposables.empty();
    private Disposable leaveDisposable = Disposables.empty();
    private Disposable emptyCacheDisposable = Disposables.empty();
    private boolean isArchiveExtracting = false;


    public ImageViewerViewModel(@NonNull Application application, @NonNull CollectionDAO collectionDAO) {
        super(application);
        collectionDao = collectionDAO;
        searchManager = new ContentSearchManager(collectionDao);

        showFavouritesOnly.postValue(false);
        shuffled.postValue(false);
    }

    @Override
    protected void onCleared() {
        collectionDao.cleanup();
        searchDisposable.dispose();
        compositeDisposable.clear();
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

    public void loadFromContent(long contentId) {
        if (contentId > 0) {
            Content loadedContent = collectionDao.selectContent(contentId);
            if (loadedContent != null)
                processContent(loadedContent);
        }
    }

    public void loadFromSearchParams(long contentId, @NonNull Bundle bundle) {
        searchManager.loadFromBundle(bundle);
        applySearchParams(contentId);
    }

    private void applySearchParams(long contentId) {
        searchDisposable.dispose();
        searchDisposable = searchManager.searchLibraryForId().subscribe(
                list -> {
                    contentIds = list;
                    loadFromContent(contentId);
                },
                throwable -> {
                    Timber.w(throwable);
                    ToastHelper.toast("Book list loading failed");
                }
        );
    }

    public void setReaderStartingIndex(int index) {
        startingIndex.setValue(index);
    }

    private void setImages(@NonNull Content theContent, @NonNull List<ImageFile> newImages) {
        Observable<ImageFile> observable;

        databaseImages.postValue(newImages);

        // Don't reload from disk / archive again if the image list hasn't changed
        // e.g. page favourited
        if (imageLocations.isEmpty() || newImages.size() != imageLocations.size()) {
            if (theContent.isArchive())
                observable = Observable.create(emitter -> processArchiveImages(theContent, newImages, emitter));
            else
                observable = Observable.create(emitter -> processDiskImages(theContent, newImages, emitter));

            AtomicInteger nbProcessed = new AtomicInteger();
            imageLoadDisposable = observable
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .doOnComplete(
                            // Called this way to properly run on io thread
                            () -> postLoadProcessing(getApplication().getApplicationContext(), theContent)
                    )
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            imageFile -> {
                                nbProcessed.getAndIncrement();
                                EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, 0, nbProcessed.get(), 0, newImages.size()));
                            },
                            t -> {
                                Timber.e(t);
                                EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, 0, nbProcessed.get(), 0, newImages.size()));
                            },
                            () -> {
                                EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, 0, nbProcessed.get(), 0, newImages.size()));
                                for (ImageFile img : newImages)
                                    imageLocations.put(img.getOrder(), img.getFileUri());
                                initViewer(theContent, newImages);
                                imageLoadDisposable.dispose();
                            }
                    );
        } else {
            // Copy location properties of the new list on the current list
            for (int i = 0; i < newImages.size(); i++) {
                ImageFile newImg = newImages.get(i);
                String location = imageLocations.get(newImg.getOrder());
                newImg.setFileUri(location);
            }

            initViewer(theContent, newImages);
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
                    collectionDao.insertContent(theContent);
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

    public void emptyCacheFolder() {
        emptyCacheDisposable =
                Completable.fromRunnable(this::doEmptyCacheFolder)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> emptyCacheDisposable.dispose(),
                                Timber::e
                        );
    }

    private void doEmptyCacheFolder() {
        Helper.assertNonUiThread();

        File cachePicFolder = getOrCreatePictureCacheFolder();
        if (cachePicFolder != null) {
            File[] files = cachePicFolder.listFiles();
            if (files != null)
                for (File f : files)
                    if (!f.delete()) Timber.w("Unable to delete file %s", f.getAbsolutePath());
        }
    }

    private synchronized void processArchiveImages(
            @NonNull Content theContent,
            @NonNull List<ImageFile> newImages,
            @NonNull final ObservableEmitter<ImageFile> emitter) throws IOException {
        if (!theContent.isArchive())
            throw new IllegalArgumentException("Content must be an archive");

        if (isArchiveExtracting) return;

        List<ImageFile> newImageFiles = new ArrayList<>(newImages);
        List<ImageFile> currentImages = databaseImages.getValue();
        if ((!newImages.isEmpty() && loadedContentId != newImages.get(0).getContent().getTargetId()) || null == currentImages) { // Load a new book
            // TODO create a smarter cache that works with a window of a given size
            File cachePicFolder = getOrCreatePictureCacheFolder();
            if (cachePicFolder != null) {
                // Empty the cache folder where previous cached images might be
                File[] files = cachePicFolder.listFiles();
                if (files != null)
                    for (File f : files)
                        if (!f.delete()) Timber.w("Unable to delete file %s", f.getAbsolutePath());

                // Extract the images if they are contained within an archive
                // Unzip the archive in the app's cache folder
                DocumentFile archiveFile = FileHelper.getFileFromSingleUriString(getApplication(), theContent.getStorageUri());
                // TODO replace that with a proper on-demand loading
                if (archiveFile != null) {
                    isArchiveExtracting = true;
                    unarchiveDisposable = ArchiveHelper.extractArchiveEntriesRx(
                            getApplication(),
                            archiveFile,
                            Stream.of(newImageFiles).filter(i -> i.getFileUri().startsWith(theContent.getStorageUri())).map(i -> i.getFileUri().replace(theContent.getStorageUri() + File.separator, "")).toList(),
                            cachePicFolder,
                            null)
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
                String location = imageLocations.get(newImg.getOrder());
                newImg.setFileUri(location);
            }

            // Replace initial images with updated images
            newImages.clear();
            newImages.addAll(newImageFiles);

            emitter.onComplete();
        }
    }

    // TODO doc
    private ImageFile mapUriToImageFile(@NonNull final List<ImageFile> imageFiles, @NonNull final Uri uri) {
        String path = uri.getPath();
        if (null == path) return new ImageFile();

        // Feed the Uri's of unzipped files back into the corresponding images for viewing
        for (ImageFile img : imageFiles) {
            if (FileHelper.getFileNameWithoutExtension(img.getFileUri()).equalsIgnoreCase(ArchiveHelper.extractCacheFileName(path))) {
                return img.setFileUri(uri.toString());
            }
        }
        return new ImageFile();
    }

    private void initViewer(@NonNull Content theContent, @NonNull List<ImageFile> imageFiles) {
        Boolean shuffledVal = getShuffled().getValue();
        sortAndSetViewerImages(imageFiles, (null == shuffledVal) ? false : shuffledVal);

        if (theContent.getId() != loadedContentId) { // To be done once per book only
            int collectionStartingIndex = 0;

            // Auto-restart at last read position if asked to
            if (Preferences.isViewerResumeLastLeft() && theContent.getLastReadPageIndex() > -1)
                collectionStartingIndex = theContent.getLastReadPageIndex();

            // Correct offset with the thumb index
            thumbIndex = -1;
            for (int i = 0; i < imageFiles.size(); i++)
                if (!imageFiles.get(i).isReadable()) {
                    thumbIndex = i;
                    break;
                }

            if (thumbIndex == collectionStartingIndex) collectionStartingIndex += 1;


            setReaderStartingIndex(collectionStartingIndex - thumbIndex - 1);

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
            if (collectionStartingIndex < imageFiles.size())
                markPageAsRead(imageFiles.get(collectionStartingIndex).getOrder());
        }

        loadedContentId = theContent.getId();
    }

    public void toggleShuffle() {
        Boolean shuffledVal = getShuffled().getValue();
        boolean isShuffled = (null == shuffledVal) ? false : shuffledVal;
        isShuffled = !isShuffled;
        if (isShuffled) RandomSeedSingleton.getInstance().renewSeed(Consts.SEED_PAGES);
        shuffled.postValue(isShuffled);

        List<ImageFile> imgs = databaseImages.getValue();
        if (imgs != null) sortAndSetViewerImages(imgs, isShuffled);
    }

    private void sortAndSetViewerImages(@NonNull List<ImageFile> imgs, boolean shuffle) {
        if (shuffle) {
            Collections.shuffle(imgs, new Random(RandomSeedSingleton.getInstance().getSeed(Consts.SEED_PAGES)));
            // Don't keep the cover thumb
            imgs = Stream.of(imgs).filter(ImageFile::isReadable).toList();
        } else {
            // Sort images according to their Order; don't keep the cover thumb
            imgs = Stream.of(imgs).sortBy(ImageFile::getOrder).filter(ImageFile::isReadable).toList();
        }

        Boolean showFavouritesOnlyVal = getShowFavouritesOnly().getValue();
        if (showFavouritesOnlyVal != null && showFavouritesOnlyVal)
            imgs = Stream.of(imgs).filter(ImageFile::isFavourite).toList();

        for (int i = 0; i < imgs.size(); i++) imgs.get(i).setDisplayOrder(i);

        viewerImages.postValue(imgs);
    }

    public void onLeaveBook(int readerIndex) {
        if (Preferences.Constant.VIEWER_DELETE_ASK_BOOK == Preferences.getViewerDeleteAskMode())
            Preferences.setViewerDeleteAskMode(Preferences.Constant.VIEWER_DELETE_ASK_AGAIN);

        // Don't do anything if the Content hasn't even been loaded
        if (-1 == loadedContentId) return;

        List<ImageFile> theImages = databaseImages.getValue();
        Content theContent = collectionDao.selectContent(loadedContentId);
        if (null == theImages || null == theContent) return;

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
                readThresholdPosition = theImages.size();
                break;
            default:
                readThresholdPosition = 1;
        }
        int collectionIndex = readerIndex + thumbIndex + 1;
        boolean updateReads = (readPageNumbers.size() >= readThresholdPosition || theContent.getReads() > 0);

        // Reset the memorized page index if it represents the last page
        int indexToSet = (collectionIndex >= theImages.size()) ? 0 : collectionIndex;

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
            applySearchParams(loadedContentId);
        }
    }

    public void toggleImageFavourite(int viewerIndex, @NonNull Consumer<Boolean> successCallback) {
        List<ImageFile> list = viewerImages.getValue();
        if (list != null) {
            ImageFile file = list.get(viewerIndex);
            boolean newState = !file.isFavourite();
            toggleImageFavourite(Stream.of(file).toList(), () -> successCallback.accept(newState));
        }
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
        Content theContent = collectionDao.selectContent(images.get(0).getContent().getTargetId());
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
        collectionDao.insertImageFiles(dbImages);

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
        collectionDao.insertContent(content);

        // Persist new values in JSON
        Context context = getApplication().getApplicationContext();
        if (!content.getJsonUri().isEmpty())
            ContentHelper.updateContentJson(context, content);
        else ContentHelper.createContentJson(context, content);
    }

    public void deleteBook(Consumer<Throwable> onError) {
        Content targetContent = collectionDao.selectContent(loadedContentId);
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
                                            loadFromContent(contentIds.get(currentContentIndex));
                                    } else { // Close the viewer if the list is empty (single book)
                                        content.postValue(null);
                                    }
                                },
                                e -> {
                                    onError.accept(e);
                                    // Restore image source listener on error
                                    databaseImages.addSource(currentImageSource, imgs -> setImages(targetContent, imgs));
                                }
                        )
        );
    }

    private void doDeleteBook(@NonNull Content targetContent) throws ContentNotRemovedException {
        Helper.assertNonUiThread();
        ContentHelper.removeQueuedContent(getApplication(), collectionDao, targetContent, true);
    }

    public void deletePage(int pageViewerIndex, Consumer<Throwable> onError) {
        List<ImageFile> imageFiles = viewerImages.getValue();
        if (imageFiles != null && imageFiles.size() > pageViewerIndex && pageViewerIndex > -1)
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
        ContentHelper.removePages(pages, collectionDao, getApplication());
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
        ContentHelper.setContentCover(page, collectionDao, getApplication());
    }

    public void loadNextContent(int readerIndex) {
        if (currentContentIndex < contentIds.size() - 1) {
            currentContentIndex++;
            if (!contentIds.isEmpty()) {
                onLeaveBook(readerIndex);
                loadFromContent(contentIds.get(currentContentIndex));
            }
        }
    }

    public void loadPreviousContent(int readerIndex) {
        if (currentContentIndex > 0) {
            currentContentIndex--;
            if (!contentIds.isEmpty()) {
                onLeaveBook(readerIndex);
                loadFromContent(contentIds.get(currentContentIndex));
            }
        }
    }

    private void processContent(@NonNull Content theContent) {
        currentContentIndex = contentIds.indexOf(theContent.getId());
        if (-1 == currentContentIndex) currentContentIndex = 0;

        theContent.setFirst(0 == currentContentIndex);
        theContent.setLast(currentContentIndex >= contentIds.size() - 1);
        if (contentIds.size() > currentContentIndex && loadedContentId != contentIds.get(currentContentIndex))
            imageLocations.clear();
        content.postValue(theContent);

        // Observe the content's images
        // NB : It has to be dynamic to be updated when viewing a book from the queue screen
        if (currentImageSource != null) databaseImages.removeSource(currentImageSource);
        currentImageSource = collectionDao.selectDownloadedImagesFromContent(theContent.getId());
        databaseImages.addSource(currentImageSource, imgs -> setImages(theContent, imgs));
    }

    private void postLoadProcessing(@NonNull Context context, @NonNull Content content) {
        // Cache images in the Json file
        cacheJson(context, content);
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

    private Content doUpdateContentPreferences(@NonNull final Context context, long contentId, @NonNull final Map<String, String> newPrefs) {
        Helper.assertNonUiThread();

        Content theContent = collectionDao.selectContent(contentId);
        if (null == theContent) return null;

        theContent.setBookPreferences(newPrefs);
        // Persist in DB
        collectionDao.insertContent(theContent);

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
        collectionDao.insertContent(content);
    }

    @Nullable
    private File getOrCreatePictureCacheFolder() {
        File cacheDir = getApplication().getCacheDir();
        File pictureCacheDir = new File(cacheDir.getAbsolutePath() + File.separator + Consts.PICTURE_CACHE_FOLDER);
        if (pictureCacheDir.exists()) return pictureCacheDir;
        else if (pictureCacheDir.mkdir()) return pictureCacheDir;
        else return null;
    }

    public void markPageAsRead(int pageNumber) {
        readPageNumbers.add(pageNumber);
    }
}
