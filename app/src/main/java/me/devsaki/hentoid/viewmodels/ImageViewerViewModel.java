package me.devsaki.hentoid.viewmodels;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Content;


public class ImageViewerViewModel extends AndroidViewModel {

    // Settings
    private boolean shuffleImages = false;

    // Per book data
    private final MutableLiveData<List<String>> images = new MutableLiveData<>();

    private List<String> initialImagesList;   // Initial URL list in the right order, to fallback when shuffling is disabled
    private long contentId;
    private int currentPosition;            // 0-based position, as in "programmatic index"


    public ImageViewerViewModel(@NonNull Application application) {
        super(application);
        images.setValue(Collections.emptyList());
    }

    @NonNull
    public LiveData<List<String>> getImages() {
        return images;
    }

    public void setImages(List<String> imgs) {
        initialImagesList = new ArrayList<>(imgs);
        if (shuffleImages) Collections.shuffle(imgs);
        images.setValue(imgs);
    }

    public void setContentId(long contentId) {
        this.contentId = contentId;
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
}
