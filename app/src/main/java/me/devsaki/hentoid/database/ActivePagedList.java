package me.devsaki.hentoid.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.paging.PagedList;

import com.annimon.stream.function.Consumer;

import timber.log.Timber;


public class ActivePagedList<T> extends PagedList.BoundaryCallback<T> {

    private LiveData<PagedList<T>> pagedList;
    private Consumer<T> onItemAtFrontLoaded = null;
    private Consumer<T> onItemAtEndLoaded = null;

    public LiveData<PagedList<T>> getPagedList() {
        return pagedList;
    }

    void setPagedList(LiveData<PagedList<T>> pageList) {
        this.pagedList = pageList;
    }

    @Nullable
    public Consumer<T> getOnItemAtFrontLoaded() {
        return onItemAtFrontLoaded;
    }

    public void setOnItemAtFrontLoaded(Consumer<T> onItemAtFrontLoaded) {
        this.onItemAtFrontLoaded = onItemAtFrontLoaded;
    }

    @Nullable
    public Consumer<T> getOnItemAtEndLoaded() {
        return onItemAtEndLoaded;
    }

    public void setOnItemAtEndLoaded(Consumer<T> onItemAtEndLoaded) {
        this.onItemAtEndLoaded = onItemAtEndLoaded;
    }

    @Override
    public void onItemAtFrontLoaded(@NonNull T itemAtFront) {
        Timber.d(">> item loaded at front : %s", itemAtFront.toString());
        if (onItemAtFrontLoaded != null) onItemAtFrontLoaded.accept(itemAtFront);
    }

    @Override
    public void onItemAtEndLoaded(@NonNull T itemAtEnd) {
        Timber.d(">> item loaded at end : %s", itemAtEnd.toString());
        if (onItemAtEndLoaded != null) onItemAtEndLoaded.accept(itemAtEnd);
    }
}
