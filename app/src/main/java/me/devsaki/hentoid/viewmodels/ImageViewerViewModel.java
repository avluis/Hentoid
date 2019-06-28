package me.devsaki.hentoid.viewmodels;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.database.ObjectBoxCollectionAccessor;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.listener.ContentListener;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.widget.ContentSearchManager;


public class ImageViewerViewModel extends AndroidViewModel implements ContentListener {

    // Settings
    private boolean shuffleImages = false;      // True if images have to be shuffled; false if presented in the book order

    private ContentSearchManager searchManager = null;
    private CompositeDisposable compositeDisposable = new CompositeDisposable();

    // Pictures data
    private final MutableLiveData<List<String>> images = new MutableLiveData<>();   // Currently displayed set of images
    private List<String> initialImagesList;         // Initial URL list in the right order, to fallback when shuffling is disabled
    private int imageIndex;                         // 0-based position, as in "programmatic index"

    // Collection data
    private long maxPages;                       // Maximum available pages
    private long contentId;                     // Database ID of currently displayed book


    public ImageViewerViewModel(@NonNull Application application) {
        super(application);
        images.setValue(Collections.emptyList());
    }

    @NonNull
    public LiveData<List<String>> getImages() {
        return images;
    }

    public String getImage(int position) {
        List<String> imgs = images.getValue();
        if (imgs != null && position < imgs.size() && position > -1) return imgs.get(position);
        else return "";
    }

    public void setImages(List<String> imgs) {
        initialImagesList = new ArrayList<>(imgs);
        if (shuffleImages) Collections.shuffle(imgs);
        images.postValue(imgs);
    }

    public void setContentId(long contentId) {
        this.contentId = contentId;
    }

    public void setSearchParams(@Nonnull Bundle bundle) {
        Context ctx = getApplication().getApplicationContext();
        searchManager = new ContentSearchManager(new ObjectBoxCollectionAccessor(ctx));
        searchManager.loadFromBundle(bundle, ctx);
        int contentIndex = bundle.getInt("contentIndex", -1);
        if (contentIndex > -1) searchManager.setCurrentPage(contentIndex);
        searchManager.searchLibrary(1, this);
    }


    public void setImageIndex(int position) {
        this.imageIndex = position;
    }

    public int getImageIndex() {
        return imageIndex;
    }

    public void setShuffleImages(boolean shuffleImages) {
        this.shuffleImages = shuffleImages;
        if (shuffleImages) {
            List<String> imgs = new ArrayList<>(initialImagesList);
            Collections.shuffle(imgs);
            images.setValue(imgs);
        } else images.setValue(initialImagesList);
    }

    public int getInitialPosition() {
        ObjectBoxDB db = ObjectBoxDB.getInstance(getApplication().getApplicationContext());
        if (contentId > 0) {
            Content content = db.selectContentById(contentId);
            if (content != null) return content.getLastReadPageIndex();
        }
        return 0;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        searchManager.dispose();
        compositeDisposable.clear();
    }

    public void saveCurrentPosition() {
        ObjectBoxDB db = ObjectBoxDB.getInstance(getApplication().getApplicationContext());
        if (contentId > 0) {
            Content content = db.selectContentById(contentId);
            if (content != null) {
                content.setLastReadPageIndex(imageIndex);
                db.insertContent(content);
            }
        }
    }

    @Nullable
    public Content getCurrentContent() {
        ObjectBoxDB db = ObjectBoxDB.getInstance(getApplication().getApplicationContext());
        if (contentId > 0) {
            return db.selectContentById(contentId);
        } else return null;
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
        // Record last read position before leaving current content
        saveCurrentPosition();

        maxPages = totalContent;
        Content content = results.get(0);
        contentId = content.getId();

        // Load new content
        File[] pictures = FileHelper.getPictureFilesFromContent(getApplication().getApplicationContext(), content);
        if (pictures != null && pictures.length > 0) {
            List<String> imagesLocations = new ArrayList<>();
            for (File f : pictures) imagesLocations.add(f.getAbsolutePath());
            setImages(imagesLocations);

            // Record 1 more view for the new content
            compositeDisposable.add(
                    Completable.fromRunnable(() -> FileHelper.updateContentReads(getApplication().getApplicationContext(), content.getId(), pictures[0].getParentFile()))
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe()
            );
        }
    }


    @Override
    public void onContentFailed(Content content, String message) {
        ToastUtil.toast("Book list loading failed");
    }
}
