package me.devsaki.hentoid.viewmodels;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.support.annotation.NonNull;

import java.util.Collections;
import java.util.List;


public class ImageViewerViewModel extends ViewModel {

    private final MutableLiveData<List<String>> images = new MutableLiveData<>();

    private int initialPosition;

    public ImageViewerViewModel() {
        images.setValue(Collections.emptyList());
    }

    @NonNull
    public LiveData<List<String>> getImages() {
        return images;
    }

    public void setImages(List<String> imgs) {
        images.setValue(imgs);
    }

    public int getInitialPosition() {
        return initialPosition;
    }

    public void setInitialPosition(int initialPosition) {
        this.initialPosition = initialPosition;
    }
}
