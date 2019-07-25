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

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.ObjectBoxCollectionAccessor;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.listener.PagedResultListener;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.widget.ContentSearchManager;
import timber.log.Timber;

import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static com.annimon.stream.Collectors.toList;


public class ImageViewerViewModel extends AndroidViewModel implements PagedResultListener<Long> {

    private static final String KEY_IS_SHUFFLED = "is_shuffled";

    // Settings
    private boolean isShuffled = false;                                              // True if images have to be shuffled; false if presented in the book order
    private BooleanConsumer onShuffledChangeListener;

    // Collection data
    private final MutableLiveData<Content> content = new MutableLiveData<>();        // Current content
    private List<Long> contentIds = Collections.emptyList();                         // Content Ids of the whole collection ordered according to current filter
    private int currentContentIndex = -1;                                            // Index of current content within the above list
    private long loadedContentId = -1;                                               // Content ID that has been initially loaded

    // Pictures data
    private final MutableLiveData<List<ImageFile>> images = new MutableLiveData<>();    // Currently displayed set of images
    private final MutableLiveData<Integer> startingIndex = new MutableLiveData<>();     // 0-based index of the current image

    // Technical
    private ContentSearchManager searchManager = null;
    private CompositeDisposable compositeDisposable = new CompositeDisposable();


    public ImageViewerViewModel(@NonNull Application application) {
        super(application);
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
            ObjectBoxDB db = ObjectBoxDB.getInstance(getApplication().getApplicationContext());
            Content loadedContent = db.selectContentById(contentId);
            if (loadedContent != null)
                processContent(loadedContent);
        }
    }

    public void loadFromSearchParams(long contentId, @Nonnull Bundle bundle) {
        loadedContentId = contentId;
        Context ctx = getApplication().getApplicationContext();
        searchManager = new ContentSearchManager(new ObjectBoxCollectionAccessor(ctx));
        searchManager.loadFromBundle(bundle);
        int contentIndex = bundle.getInt("contentIndex", -1);
        if (contentIndex > -1) searchManager.setCurrentPage(contentIndex);
        searchManager.searchLibraryForId(-1, this);
    }

    @Override
    public void onPagedResultReady(List<Long> results, long totalSelectedContent, long totalContent) {
        contentIds = results;
        loadFromContent(loadedContentId);
    }

    @Override
    public void onPagedResultFailed(Long contentId, String message) {
        ToastUtil.toast("Book list loading failed");
    }

    public void setStartingIndex(int index) {
        startingIndex.setValue(index);
    }

    public void setImages(List<ImageFile> imgs) {
        List<ImageFile> list = new ArrayList<>(imgs);
        sortAndSetImages(list, isShuffled);
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
            imgs = Stream.of(imgs).sortBy(ImageFile::getOrder).collect(toList());
        }
        for (int i = 0; i < imgs.size(); i++) imgs.get(i).setDisplayOrder(i);
        images.setValue(imgs);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (searchManager != null) searchManager.dispose();
        compositeDisposable.clear();
    }

    public void savePosition(int index) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(getApplication().getApplicationContext());
        Content theContent = content.getValue();
        if (theContent != null) {
            theContent.setLastReadPageIndex(index);
            db.insertContent(theContent);
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
    }

    /**
     * Toggles favourite flag in DB and in the content JSON
     *
     * @param context Context to be used for this operation
     * @param imageId ID of the image whose flag to toggle
     * @return ImageFile with the new state
     */
    @WorkerThread
    private static ImageFile togglePageFavourite(Context context, long imageId) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);

        ImageFile img = db.selectImageFile(imageId);

        if (img != null) {
            img.setFavourite(!img.isFavourite());

            // Persist it in DB
            db.insertImageFile(img);

            // Persist in it JSON
            Content content = img.content.getTarget();
            if (!content.getJsonUri().isEmpty()) FileHelper.updateJson(context, content);
            else FileHelper.createJson(content);

            return img;
        } else
            throw new InvalidParameterException(String.format("Invalid image ID %s", imageId));
    }

    public void loadNextContent() {
        if (currentContentIndex < contentIds.size() - 1) currentContentIndex++;
        loadFromContent(contentIds.get(currentContentIndex));
    }

    public void loadPreviousContent() {
        if (currentContentIndex > 0) currentContentIndex--;
        loadFromContent(contentIds.get(currentContentIndex));
    }

    private void processContent(Content theContent) {
        currentContentIndex = contentIds.indexOf(theContent.getId());
        theContent.setFirst(0 == currentContentIndex);
        theContent.setLast(currentContentIndex == contentIds.size() - 1);

        // Load new content
        File[] pictureFiles = FileHelper.getPictureFilesFromContent(theContent);
        if (pictureFiles != null && pictureFiles.length > 0) {
            List<ImageFile> imageFiles;
            if (null == theContent.getImageFiles() || theContent.getImageFiles().isEmpty()) {
                imageFiles = new ArrayList<>();
                saveFilesToImageList(pictureFiles, imageFiles, theContent);
            } else {
                imageFiles = new ArrayList<>(theContent.getImageFiles());
                matchFilesToImageList(pictureFiles, imageFiles);
            }
            setImages(imageFiles);

            if (Preferences.isViewerResumeLastLeft()) {
                setStartingIndex(theContent.getLastReadPageIndex());
            } else {
                setStartingIndex(0);
            }

            // Cache JSON and record 1 more view for the new content
            compositeDisposable.add(
                    Single.fromCallable(() -> postLoadProcessing(getApplication().getApplicationContext(), theContent))
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    content::setValue,
                                    Timber::e
                            )
            );
        } else {
            ToastUtil.toast(R.string.no_images);
        }
    }

    private static void matchFilesToImageList(File[] files, @Nonnull List<ImageFile> images) {
        int i = 0;
        while (i < images.size()) {
            boolean matchFound = false;
            for (File f : files) {
                // Image and file name match => store absolute path
                if (FileHelper.getFileNameWithoutExtension(images.get(i).getName()).equals(FileHelper.getFileNameWithoutExtension(f.getName()))) {
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

    private static void saveFilesToImageList(File[] files, @Nonnull List<ImageFile> images, @Nonnull Content content) {
        int order = 0;
        // Sort files by name alpha
        List<File> fileList = Stream.of(files).sortBy(File::getName).collect(toList());
        for (File f : fileList) {
            order++;
            ImageFile img = new ImageFile();
            String name = FileHelper.getFileNameWithoutExtension(f.getName());
            img.setName(name).setOrder(order).setUrl("").setStatus(StatusContent.DOWNLOADED).setAbsolutePath(f.getAbsolutePath());
            images.add(img);
        }
        content.addImageFiles(images);
        ObjectBoxDB.getInstance(HentoidApp.getAppContext()).insertContent(content);
    }

    @WorkerThread
    @Nullable
    private static Content postLoadProcessing(@Nonnull Context context, @Nonnull Content content) {
        cacheJson(context, content);
        return FileHelper.updateContentReads(context, content.getId());
    }

    // Cache JSON URI in the database to speed up favouriting
    // NB : Lollipop only because it must have _full_ support for SAF
    @WorkerThread
    private static void cacheJson(@Nonnull Context context, @Nonnull Content content) {
        if (content.getJsonUri().isEmpty() && Build.VERSION.SDK_INT >= LOLLIPOP) {
            File bookFolder = FileHelper.getContentDownloadDir(content);
            DocumentFile file = FileHelper.getDocumentFile(new File(bookFolder, Consts.JSON_FILE_NAME_V2), false);
            if (file != null) {
                // Cache the URI of the JSON to the database
                ObjectBoxDB db = ObjectBoxDB.getInstance(context);
                content.setJsonUri(file.getUri().toString());
                db.insertContent(content);
            } else {
                Timber.e("File not detected : %s", content.getStorageFolder());
            }
        }
    }
}
