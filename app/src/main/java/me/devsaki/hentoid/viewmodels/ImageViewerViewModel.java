package me.devsaki.hentoid.viewmodels;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.annimon.stream.Stream;
import com.annimon.stream.function.BooleanConsumer;
import com.annimon.stream.function.Consumer;

import java.io.File;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.widget.ContentSearchManager;
import timber.log.Timber;

import static android.os.Build.VERSION_CODES.LOLLIPOP;


public class ImageViewerViewModel extends AndroidViewModel {

    private static final String KEY_IS_SHUFFLED = "is_shuffled";

    // Collection DAO
    private final CollectionDAO collectionDao = new ObjectBoxDAO(getApplication().getApplicationContext());

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


    public ImageViewerViewModel(@NonNull Application application) {
        super(application);
        content.setValue(null); // Default content; tells everyone nothing has been loaded yet
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

    public void loadFromSearchParams(long contentId, @Nonnull Bundle bundle) {
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

    private void setImages(@NonNull Content content, @NonNull List<ImageFile> imgs) {
        // Load new content
        File[] pictureFiles = ContentHelper.getPictureFilesFromContent(content); // TODO this is called too often when viewing a queued book -> optimize !
        if (pictureFiles != null && pictureFiles.length > 0) {
            List<ImageFile> imageFiles;
            if (imgs.isEmpty()) {
                imageFiles = filesToImageList(pictureFiles);
                content.setImageFiles(imageFiles);
                collectionDao.insertContent(content);
            } else {
                imageFiles = new ArrayList<>(imgs);
                matchFilesToImageList(pictureFiles, imageFiles);
            }
            sortAndSetImages(imageFiles, isShuffled);

            if (content.getId() != loadedBookId) { // To be done once per book only
                if (Preferences.isViewerResumeLastLeft())
                    setStartingIndex(content.getLastReadPageIndex());
                else
                    setStartingIndex(0);
            }

            loadedBookId = content.getId();

            // Cache JSON and record 1 more view for the new content
            compositeDisposable.add(
                    Single.fromCallable(() -> postLoadProcessing(content))
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    v -> {
                                    },
                                    Timber::e
                            )
            );
        } else {
            ToastUtil.toast(R.string.no_images);
        }
    }

    public void onShuffleClick() {
        isShuffled = !isShuffled;
        onShuffledChangeListener.accept(isShuffled);

        List<ImageFile> imgs = getImages().getValue();
        if (imgs != null) sortAndSetImages(imgs, isShuffled);
    }

    private void sortAndSetImages(@Nonnull List<ImageFile> imgs, boolean shuffle) {
        if (shuffle) {
            Collections.shuffle(imgs);
        } else {
            // Sort images according to their Order
            imgs = Stream.of(imgs).sortBy(ImageFile::getOrder).toList();
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

        int indexToSet = index;
        // Reset the memorized page index if it represents the last page
        if (index == theImages.size() - 1) indexToSet = 0;

        theContent.setLastReadPageIndex(indexToSet);
        if (highestImageIndexReached + 1 >= readThresholdPosition)
            ContentHelper.updateContentReads(getApplication(), collectionDao, theContent);
        else collectionDao.insertContent(theContent);
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
                Single.fromCallable(() -> togglePageFavourite(getApplication().getApplicationContext(), file.getId()))
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
    @WorkerThread
    private ImageFile togglePageFavourite(Context context, long imageId) {
        ImageFile img = collectionDao.selectImageFile(imageId);

        if (img != null) {
            img.setFavourite(!img.isFavourite());

            // Persist it in DB
            collectionDao.insertImageFile(img);

            // Persist in it JSON
            Content theContent = img.content.getTarget();
            if (!theContent.getJsonUri().isEmpty()) ContentHelper.updateJson(context, theContent);
            else ContentHelper.createJson(theContent);

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
                                    // Switch to the next book
                                    contentIds.remove(currentContentIndex);
                                    if (currentContentIndex >= contentIds.size() && currentContentIndex > 0)
                                        currentContentIndex--;
                                    loadFromContent(contentIds.get(currentContentIndex));
                                },
                                e -> {
                                    Timber.e(e);
                                    // Restore image source listener on error
                                    images.addSource(currentImageSource, imgs -> setImages(targetContent, imgs));
                                }
                        )
        );
    }

    @WorkerThread
    private void doDeleteBook(@NonNull Content targetContent) {
        collectionDao.deleteQueue(targetContent);
        ContentHelper.removeContent(targetContent, collectionDao);
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

    @WorkerThread
    private void doDeletePage(int pageIndex) {
        List<ImageFile> imageFiles = images.getValue();
        if (imageFiles != null && imageFiles.size() > pageIndex)
            ContentHelper.removePage(imageFiles.get(pageIndex), collectionDao, getApplication());
    }

    public void loadNextContent() {
        if (currentContentIndex < contentIds.size() - 1) currentContentIndex++;
        loadFromContent(contentIds.get(currentContentIndex));
    }

    public void loadPreviousContent() {
        if (currentContentIndex > 0) currentContentIndex--;
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

    private static void matchFilesToImageList(File[] files, @Nonnull List<ImageFile> images) {
        int i = 0;
        while (i < images.size()) {
            boolean matchFound = false;
            for (File f : files) {
                // Image and file name match => store absolute path
                if (fileNamesMatch(images.get(i).getName(), f.getName())) {
                    matchFound = true;
                    images.get(i).setAbsolutePath(f.getAbsolutePath());
                    break;
                }
            }
            // Image is not among detected files => remove it
            if (!matchFound) {
                images.remove(i);
            } else i++;
        }
    }

    // Match when the names are exactly the same, or when their value is
    private static boolean fileNamesMatch(@NonNull String name1, @NonNull String name2) {
        name1 = FileHelper.getFileNameWithoutExtension(name1);
        name2 = FileHelper.getFileNameWithoutExtension(name2);
        if (name1.equalsIgnoreCase(name2)) return true;

        try {
            return (Integer.parseInt(name1) == Integer.parseInt(name2));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private List<ImageFile> filesToImageList(@NonNull File[] files) {
        List<ImageFile> result = new ArrayList<>();
        int order = 1;
        // Sort files by name alpha
        List<File> fileList = Stream.of(files).sortBy(File::getName).toList();
        for (File f : fileList) {
            ImageFile img = new ImageFile();
            String name = FileHelper.getFileNameWithoutExtension(f.getName());
            img.setName(name).setOrder(order++).setUrl("").setStatus(StatusContent.DOWNLOADED).setAbsolutePath(f.getAbsolutePath());
            result.add(img);
        }
        return result;
    }

    @WorkerThread
    private Content postLoadProcessing(@Nonnull Content content) {
        cacheJson(content);
        return content;
    }

    // Cache JSON URI in the database to speed up favouriting
    // NB : Lollipop only because it must have _full_ support for SAF
    @WorkerThread
    private void cacheJson(@Nonnull Content content) {
        if (content.getJsonUri().isEmpty() && Build.VERSION.SDK_INT >= LOLLIPOP) {
            File bookFolder = ContentHelper.getContentDownloadDir(content);
            DocumentFile file = FileHelper.getDocumentFile(new File(bookFolder, Consts.JSON_FILE_NAME_V2), false);
            if (file != null) {
                // Cache the URI of the JSON to the database
                content.setJsonUri(file.getUri().toString());
                collectionDao.insertContent(content);
            } else {
                Timber.e("File not detected : %s", content.getStorageFolder());
            }
        }
    }
}
