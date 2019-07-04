package me.devsaki.hentoid.viewmodels;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.annimon.stream.Stream;
import com.annimon.stream.function.BooleanConsumer;
import com.annimon.stream.function.Consumer;

import java.io.File;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.ObjectBoxCollectionAccessor;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.listener.ContentListener;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.widget.ContentSearchManager;
import timber.log.Timber;

import static com.annimon.stream.Collectors.toList;


public class ImageViewerViewModel extends AndroidViewModel implements ContentListener {

    // Settings
    /** True if images have to be shuffled; false if presented in the book order */
    private boolean isShuffled;
    private BooleanConsumer onShuffledChangeListener;

    // Pictures data
    private final MutableLiveData<List<ImageFile>> images = new MutableLiveData<>();    // Currently displayed set of images
    private final MutableLiveData<Integer> startingIndex = new MutableLiveData<>();     // 0-based index of the current image

    // Collection data
    private final MutableLiveData<Content> content = new MutableLiveData<>();        // Current content
    private long maxPages;                                                           // Maximum available pages

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

    public void loadFromContent(long contentId) {
        if (contentId > 0) {
            ObjectBoxDB db = ObjectBoxDB.getInstance(getApplication().getApplicationContext());
            Content content = db.selectContentById(contentId);
            if (content != null) processContent(content);
        }
    }

    public void loadFromSearchParams(@Nonnull Bundle bundle) {
        Context ctx = getApplication().getApplicationContext();
        searchManager = new ContentSearchManager(new ObjectBoxCollectionAccessor(ctx));
        searchManager.loadFromBundle(bundle, ctx);
        int contentIndex = bundle.getInt("contentIndex", -1);
        if (contentIndex > -1) searchManager.setCurrentPage(contentIndex);
        searchManager.searchLibrary(1, this);
    }

    public void setImages(List<ImageFile> imgs) {
        List<ImageFile> list = new ArrayList<>(imgs);
        if (isShuffled)
            Collections.shuffle(list);
        for (int i = 0; i < list.size(); i++)
            list.get(i).setDisplayOrder(i);
        images.setValue(list);
    }

    public void setStartingIndex(int index) {
        startingIndex.setValue(index);
    }

    public void onShuffleClick() {
        isShuffled = !isShuffled;
        onShuffledChangeListener.accept(isShuffled);

        List<ImageFile> imgs = getImages().getValue();
        if (imgs != null) {
            if (isShuffled) {
                Collections.shuffle(imgs);
            } else {
                // Sort images according to their Order
                imgs = Stream.of(imgs).sortBy(ImageFile::getOrder).collect(toList());
            }
            for (int i = 0; i < imgs.size(); i++)
                imgs.get(i).setDisplayOrder(i);
            images.setValue(imgs);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        searchManager.dispose();
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
    private static ImageFile togglePageFavourite(Context context, long imageId) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);

        ImageFile img = db.selectImageFile(imageId);

        if (img != null) {
            img.setFavourite(!img.isFavourite());

            // Persist it in DB
            db.insertImageFile(img);

            // Persist in it JSON
            Content content = img.content.getTarget();
            File dir = FileHelper.getContentDownloadDir(content);
            try {
                JsonHelper.saveJson(content.preJSONExport(), dir);
            } catch (IOException e) {
                Timber.e(e, "Error while writing to %s", dir.getAbsolutePath());
            }
            return img;
        } else
            throw new InvalidParameterException(String.format("Invalid image ID %s", imageId));
    }

    public void loadNextContent() {
        if (searchManager.getCurrentPage() < maxPages) // Need to load next content page
        {
            searchManager.increaseCurrentPage();
            searchManager.searchLibrary(1, this);
        }
    }

    public void loadPreviousContent() {
        if (searchManager.getCurrentPage() > 1) // Need to load previous content page
        {
            searchManager.decreaseCurrentPage();
            searchManager.searchLibrary(1, this);
        }
    }

    @Override
    public void onContentReady(List<Content> results, long totalSelectedContent, long totalContent) {
        maxPages = totalContent;
        processContent(results.get(0));
    }

    private void processContent(Content theContent) {
        theContent.setFirst(0 == theContent.getQueryOrder());
        theContent.setLast(maxPages - 1 == theContent.getQueryOrder());
        content.setValue(theContent);

        // Load new content
        File[] pictures = FileHelper.getPictureFilesFromContent(theContent);
        if (pictures != null && pictures.length > 0 && theContent.getImageFiles() != null) {
            List<ImageFile> imageFiles = new ArrayList<>(theContent.getImageFiles());
            matchFilesToImageList(pictures, imageFiles);
            setImages(imageFiles);

            if (Preferences.isViewerResumeLastLeft()) {
                setStartingIndex(theContent.getLastReadPageIndex());
            } else {
                setStartingIndex(0);
            }

            // Record 1 more view for the new content
            compositeDisposable.add(
                    Completable.fromRunnable(() -> FileHelper.updateContentReads(getApplication().getApplicationContext(), theContent.getId(), pictures[0].getParentFile()))
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe()
            );
        } else {
            ToastUtil.toast(R.string.no_images);
        }
    }

    private static void matchFilesToImageList(File[] files, List<ImageFile> images) {
        int i = 0;
        while (i < images.size()) {
            boolean matchFound = false;
            for (File f : files) {
                // Image and file name match => store absolute path
                if (images.get(i).getName().equals(FileHelper.getFileNameWithoutExtension(f.getName()))) {
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

    @Override
    public void onContentFailed(Content content, String message) {
        ToastUtil.toast("Book list loading failed");
    }
}
