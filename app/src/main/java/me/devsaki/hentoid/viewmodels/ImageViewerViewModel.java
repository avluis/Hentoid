package me.devsaki.hentoid.viewmodels;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.annimon.stream.Stream;
import com.annimon.stream.function.BooleanConsumer;
import com.annimon.stream.function.Consumer;

import org.apache.commons.lang3.tuple.ImmutablePair;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.widget.ContentSearchManager;
import timber.log.Timber;


public class ImageViewerViewModel extends AndroidViewModel {

    private static final String KEY_IS_SHUFFLED = "is_shuffled";

    // Collection DAO
    private final CollectionDAO collectionDao;

    // Settings
    private boolean isShuffled = false;                                              // True if images have to be shuffled; false if presented in the book order
    private boolean showFavourites = false;                                          // True if viewer only shows favourite images; false if shows all pages
    private BooleanConsumer onShuffledChangeListener;

    // Collection data
    private final MutableLiveData<Content> content = new MutableLiveData<>();        // Current content
    private List<Long> contentIds = Collections.emptyList();                         // Content Ids of the whole collection ordered according to current filter
    private int currentContentIndex = -1;                                            // Index of current content within the above list
    private long loadedBookId = -1;                                                  // ID of currently loaded book

    // Pictures data
    private LiveData<List<ImageFile>> currentImageSource;
    private final MediatorLiveData<List<ImageFile>> images = new MediatorLiveData<>();    // Currently displayed set of images
    private final MutableLiveData<Integer> startingIndex = new MutableLiveData<>();     // 0-based index of the current image

    // Technical
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private Disposable searchDisposable = Disposables.empty();

    public ImageViewerViewModel(@NonNull Application application, @NonNull CollectionDAO collectionDAO) {
        super(application);
        collectionDao = collectionDAO;
    }


    @NonNull
    public LiveData<List<ImageFile>> getImages() {
        return images;
    }

    @NonNull
    public LiveData<Integer> getStartingIndex() {
        return startingIndex;
    }

    @NonNull
    public LiveData<Content> getContent() {
        return content;
    }

    public void setOnShuffledChangeListener(BooleanConsumer listener) {
        this.onShuffledChangeListener = listener;
    }

    public void onSaveState(Bundle outState) {
        outState.putBoolean(KEY_IS_SHUFFLED, isShuffled);
    }

    public void onRestoreState(@Nullable Bundle savedState) {
        if (savedState == null) return;
        isShuffled = savedState.getBoolean(KEY_IS_SHUFFLED, false);
    }

    public void loadFromContent(long contentId) {
        if (contentId > 0) {
            Content loadedContent = collectionDao.selectContent(contentId);
            if (loadedContent != null)
                processContent(loadedContent);
        }
    }

    public void loadFromSearchParams(long contentId, @NonNull Bundle bundle) {
        // Technical
        ContentSearchManager searchManager = new ContentSearchManager(collectionDao);
        searchManager.loadFromBundle(bundle);

        searchDisposable.dispose();
        searchDisposable = searchManager.searchLibraryForId().subscribe(
                list -> {
                    contentIds = list;
                    loadFromContent(contentId);
                },
                throwable -> {
                    Timber.w(throwable);
                    ToastUtil.toast("Book list loading failed");
                }
        );
    }

    public void setStartingIndex(int index) {
        startingIndex.setValue(index);
    }

    private void setImages(@NonNull Content theContent, @NonNull List<ImageFile> imgs) {
        compositeDisposable.add(
                Single.fromCallable(() -> doSetImages(theContent, imgs))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .map(p -> initViewer(p.left, p.right))
                        .observeOn(Schedulers.io())
                        .subscribe(
                                // Cache JSON and record 1 more view for the new content
                                c -> postLoadProcessing(getApplication().getApplicationContext(), c),
                                Timber::e
                        )
        );
    }

    private ImmutablePair<Content, List<ImageFile>> doSetImages(@NonNull Content theContent, @NonNull List<ImageFile> imgs) {
        boolean missingUris = Stream.of(imgs).filter(img -> img.getFileUri().isEmpty()).count() > 0;
        List<ImageFile> imageFiles = new ArrayList<>(imgs);

        // Reattach actual files to the book's pictures if they are empty or have no URI's
        if (missingUris || imgs.isEmpty()) {
            List<DocumentFile> pictureFiles = ContentHelper.getPictureFilesFromContent(getApplication(), theContent);
            if (!pictureFiles.isEmpty()) {
                if (imgs.isEmpty()) {
                    imageFiles = ContentHelper.createImageListFromFiles(pictureFiles);
                    theContent.setImageFiles(imageFiles);
                    collectionDao.insertContent(theContent);
                } else
                    ContentHelper.matchFilesToImageList(pictureFiles, imageFiles);
            }
        }

        return new ImmutablePair<>(theContent, imageFiles);
    }

    private Content initViewer(@NonNull Content theContent, @NonNull List<ImageFile> imageFiles) {

        if (imageFiles.isEmpty()) { // No pictures found
            // TODO : do something more UX-friendly here; the user is alone with that black screen...
            ToastUtil.toast(R.string.no_images);
        }

        sortAndSetImages(imageFiles, isShuffled);

        if (theContent.getId() != loadedBookId) { // To be done once per book only
            if (Preferences.isViewerResumeLastLeft())
                setStartingIndex(theContent.getLastReadPageIndex());
            else
                setStartingIndex(0);
        }

        loadedBookId = theContent.getId();
        return theContent;
    }

    public void onShuffleClick() {
        isShuffled = !isShuffled;
        onShuffledChangeListener.accept(isShuffled);

        List<ImageFile> imgs = getImages().getValue();
        if (imgs != null) sortAndSetImages(imgs, isShuffled);
    }

    private void sortAndSetImages(@NonNull List<ImageFile> imgs, boolean shuffle) {
        if (shuffle) {
            Collections.shuffle(imgs);
            // Don't keep the cover
            imgs = Stream.of(imgs).filter(img -> !img.isCover()).toList();
        } else {
            // Sort images according to their Order; don't keep the cover
            imgs = Stream.of(imgs).sortBy(ImageFile::getOrder).filter(img -> !img.isCover()).toList();
        }

        if (showFavourites)
            imgs = Stream.of(imgs).filter(ImageFile::isFavourite).toList();

        for (int i = 0; i < imgs.size(); i++) imgs.get(i).setDisplayOrder(i);

        images.setValue(imgs);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        searchDisposable.dispose();
        compositeDisposable.clear();
    }

    public void onLeaveBook(int index, int highestImageIndexReached) {
        List<ImageFile> theImages = images.getValue();
        Content theContent = content.getValue();
        if (null == theImages || null == theContent) return;

        int readThresholdPref = Preferences.getViewerReadThreshold();
        int readThresholdPosition;
        switch (readThresholdPref) {
            case Preferences.Constant.PREF_VIEWER_READ_THRESHOLD_2:
                readThresholdPosition = 2;
                break;
            case Preferences.Constant.PREF_VIEWER_READ_THRESHOLD_5:
                readThresholdPosition = 5;
                break;
            case Preferences.Constant.PREF_VIEWER_READ_THRESHOLD_ALL:
                readThresholdPosition = theImages.size() - 1;
                break;
            default:
                readThresholdPosition = 1;
        }
        boolean updateReads = (highestImageIndexReached + 1 >= readThresholdPosition);

        // Reset the memorized page index if it represents the last page
        int indexToSet = (index == theImages.size() - 1) ? 0 : index;

        compositeDisposable.add(
                Completable.fromRunnable(() -> doLeaveBook(theContent.getId(), indexToSet, updateReads))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> { // No feedback needed
                                },
                                Timber::e
                        )
        );
    }

    private void doLeaveBook(final long contentId, int indexToSet, boolean updateReads) {
        Helper.assertNonUiThread();

        // Get a fresh version of current content in case it has been updated since the initial load
        // (that can be the case when viewing a book that is being downloaded)
        Content savedContent = collectionDao.selectContent(contentId);
        if (null == savedContent) return;

        savedContent.setLastReadPageIndex(indexToSet);
        if (updateReads)
            ContentHelper.updateContentReads(getApplication(), collectionDao, savedContent);
        else collectionDao.insertContent(savedContent);
    }

    public void toggleShowFavouritePages(Consumer<Boolean> callback) {
        Content c = content.getValue();
        if (c != null) {
            showFavourites = !showFavourites;
            processContent(c);
            callback.accept(showFavourites);
        }
    }

    public void togglePageFavourite(ImageFile file, Consumer<ImageFile> callback) {
        compositeDisposable.add(
                Single.fromCallable(() -> doTogglePageFavourite(getApplication().getApplicationContext(), file.getId()))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                result -> onToggleFavouriteSuccess(result, callback),
                                Timber::e
                        )
        );
    }

    private void onToggleFavouriteSuccess(ImageFile result, Consumer<ImageFile> callback) {
        List<ImageFile> imgs = getImages().getValue();
        if (imgs != null) {
            for (ImageFile img : imgs)
                if (img.getId() == result.getId()) {
                    img.setFavourite(result.isFavourite()); // Update new state in memory
                    result.setDisplayOrder(img.getDisplayOrder()); // Set the display order of the item to
                    callback.accept(result); // Inform the view
                }
        }
        compositeDisposable.clear();
    }

    /**
     * Toggles favourite flag in DB and in the content JSON
     *
     * @param context Context to be used for this operation
     * @param imageId ID of the image whose flag to toggle
     * @return ImageFile with the new state
     */
    private ImageFile doTogglePageFavourite(Context context, long imageId) {
        Helper.assertNonUiThread();
        ImageFile img = collectionDao.selectImageFile(imageId);

        if (img != null) {
            img.setFavourite(!img.isFavourite());

            // Persist in DB
            collectionDao.insertImageFile(img);

            // Persist in JSON
            Content theContent = img.content.getTarget();
            if (!theContent.getJsonUri().isEmpty()) ContentHelper.updateJson(context, theContent);
            else ContentHelper.createJson(context, theContent);

            return img;
        } else
            throw new InvalidParameterException(String.format("Invalid image ID %s", imageId));
    }

    public void deleteBook() {
        Content targetContent = collectionDao.selectContent(loadedBookId);
        if (null == targetContent) return;

        // Unplug image source listener (avoid displaying pages as they are being deleted)
        if (currentImageSource != null) images.removeSource(currentImageSource);

        compositeDisposable.add(
                Completable.fromRunnable(() -> doDeleteBook(targetContent))
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
                                        content.setValue(null);
                                    }
                                },
                                e -> {
                                    Timber.e(e);
                                    // Restore image source listener on error
                                    images.addSource(currentImageSource, imgs -> setImages(targetContent, imgs));
                                }
                        )
        );
    }

    private void doDeleteBook(@NonNull Content targetContent) {
        Helper.assertNonUiThread();
        collectionDao.deleteQueue(targetContent);
        ContentHelper.removeContent(getApplication(), targetContent, collectionDao);
    }

    public void deletePage(int pageIndex) {
        compositeDisposable.add(
                Completable.fromRunnable(() -> doDeletePage(pageIndex))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> { // Update is done through LiveData
                                },
                                Timber::e
                        )
        );
    }

    private void doDeletePage(int pageIndex) {
        Helper.assertNonUiThread();
        List<ImageFile> imageFiles = images.getValue();
        if (imageFiles != null && imageFiles.size() > pageIndex)
            ContentHelper.removePage(imageFiles.get(pageIndex), collectionDao, getApplication());
    }

    public void loadNextContent() {
        if (currentContentIndex < contentIds.size() - 1) currentContentIndex++;
        if (!contentIds.isEmpty())
            loadFromContent(contentIds.get(currentContentIndex));
    }

    public void loadPreviousContent() {
        if (currentContentIndex > 0) currentContentIndex--;
        if (!contentIds.isEmpty())
            loadFromContent(contentIds.get(currentContentIndex));
    }

    private void processContent(@NonNull Content theContent) {
        currentContentIndex = contentIds.indexOf(theContent.getId());
        if (-1 == currentContentIndex) currentContentIndex = 0;

        theContent.setFirst(0 == currentContentIndex);
        theContent.setLast(currentContentIndex >= contentIds.size() - 1);
        content.setValue(theContent);

        // Observe the content's images
        // NB : It has to be dynamic to be updated when viewing a book from the queue screen
        if (currentImageSource != null) images.removeSource(currentImageSource);
        currentImageSource = collectionDao.getDownloadedImagesFromContent(theContent.getId());
        images.addSource(currentImageSource, imgs -> setImages(theContent, imgs));
    }

    private void postLoadProcessing(@NonNull Context context, @NonNull Content content) {
        cacheJson(context, content);
    }

    public void updateBookPreferences(@NonNull final Map<String, String> newPrefs) {
        Content theContent = content.getValue();
        if (null == theContent) return;
        theContent.setBookPreferences(newPrefs);

        compositeDisposable.add(
                Completable.fromRunnable(() -> doUpdateContent(getApplication().getApplicationContext(), theContent))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> { // Update is done through LiveData
                                },
                                Timber::e
                        )
        );
    }

    private void doUpdateContent(@NonNull final Context context, @NonNull final Content c) {
        Helper.assertNonUiThread();

        // Persist in DB
        collectionDao.insertContent(c);
        content.postValue(c);

        // Persist in JSON
        if (!c.getJsonUri().isEmpty()) ContentHelper.updateJson(context, c);
        else ContentHelper.createJson(context, c);
    }

    // Cache JSON URI in the database to speed up favouriting
    private void cacheJson(@NonNull Context context, @NonNull Content content) {
        Helper.assertNonUiThread();
        if (!content.getJsonUri().isEmpty()) return;

        DocumentFile folder = DocumentFile.fromTreeUri(context, Uri.parse(content.getStorageUri()));
        if (null == folder || !folder.exists()) return;

        DocumentFile foundFile = FileHelper.findFile(getApplication(), folder, Consts.JSON_FILE_NAME_V2);
        if (null == foundFile) {
            Timber.e("JSON file not detected in %s", content.getStorageUri());
            return;
        }

        // Cache the URI of the JSON to the database
        content.setJsonUri(foundFile.getUri().toString());
        collectionDao.insertContent(content);
    }
}
