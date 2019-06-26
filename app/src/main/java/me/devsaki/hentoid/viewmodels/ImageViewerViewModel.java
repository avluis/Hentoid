package me.devsaki.hentoid.viewmodels;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.ObjectBoxCollectionAccessor;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.widget.ContentSearchManager;


public class ImageViewerViewModel extends AndroidViewModel {

    // Settings
    private boolean shuffleImages = false;      // True if images have to be shuffled; false if presented in the book order

    // Per book data
    private final MutableLiveData<List<String>> images = new MutableLiveData<>();   // Currently displayed set of images

    private ContentSearchManager searchManager = null;

    private List<String> initialImagesList;     // Initial URL list in the right order, to fallback when shuffling is disabled
    private long contentId;                     // Database ID of currently displayed book
    private int currentPosition;                // 0-based position, as in "programmatic index"


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
        images.setValue(imgs);
    }

    public void setContentId(long contentId) {
        this.contentId = contentId;
    }

    public void setSearchParams(@Nonnull Bundle bundle) {
        Context ctx = getApplication().getApplicationContext();
        searchManager = new ContentSearchManager(new ObjectBoxCollectionAccessor(ctx));
        searchManager.loadFromBundle(bundle, ctx);
    }


    public void setCurrentPosition(int position) {
        this.currentPosition = position;
    }

    public int getCurrentPosition() {
        return currentPosition;
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
    }

    public void saveCurrentPosition() {
        ObjectBoxDB db = ObjectBoxDB.getInstance(getApplication().getApplicationContext());
        if (contentId > 0) {
            Content content = db.selectContentById(contentId);
            if (content != null) {
                content.setLastReadPageIndex(currentPosition);
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

    public void loadNextContent()
    {
        loadContent(+1);
    }

    public void loadPreviousContent()
    {
        loadContent(-1);
    }

    private void loadContent(int delta)
    {

    }
}
