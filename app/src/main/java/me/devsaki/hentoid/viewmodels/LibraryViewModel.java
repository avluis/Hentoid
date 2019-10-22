package me.devsaki.hentoid.viewmodels;

import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.PagedList;

import com.annimon.stream.function.Consumer;

import java.util.List;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.exception.ContentNotRemovedException;
import me.devsaki.hentoid.widget.ContentSearchManager;
import timber.log.Timber;


public class LibraryViewModel extends AndroidViewModel {

    // Technical
    private final ObjectBoxDAO collectionDao = new ObjectBoxDAO(getApplication().getApplicationContext());
    private final ContentSearchManager searchManager = new ContentSearchManager(collectionDao);
    private final CompositeDisposable compositeDisposable = new CompositeDisposable(); // TODO remove if useless

    // Collection data
    private LiveData<PagedList<Content>> currentSource;
    private LiveData<Integer> totalContent = collectionDao.countAllBooks();
    private MutableLiveData<Boolean> newSearch = new MutableLiveData<>();
    private final MediatorLiveData<PagedList<Content>> libraryPaged = new MediatorLiveData<>();


    public LibraryViewModel(@NonNull Application application) {
        super(application);
        performSearch();
    }

    public void onSaveState(Bundle outState) {
        searchManager.saveToBundle(outState);
    }

    public void onRestoreState(@Nullable Bundle savedState) {
        if (savedState == null) return;
        searchManager.loadFromBundle(savedState);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        searchManager.dispose();
        compositeDisposable.clear();
    }

    @NonNull
    public LiveData<PagedList<Content>> getLibraryPaged() {
        return libraryPaged;
    }

    @NonNull
    public LiveData<Integer> getTotalContent() {
        return totalContent;
    }

    @NonNull
    public LiveData<Boolean> getNewSearch() {
        return newSearch;
    }

    public Bundle getSearchManagerBundle() {
        Bundle bundle = new Bundle();
        searchManager.saveToBundle(bundle);
        return bundle;
    }


    public void performSearch() {
        newSearch.setValue(true);
        libraryPaged.removeSource(currentSource);
        searchManager.setContentSortOrder(Preferences.getContentSortOrder());
        currentSource = searchManager.getLibrary();
        libraryPaged.addSource(currentSource, libraryPaged::setValue);
    }

    public void searchUniversal(@NonNull String query) {
        searchManager.clearSelectedSearchTags(); // If user searches in main toolbar, universal search takes over advanced search
        searchManager.setQuery(query);
        performSearch();
    }

    public void search(@NonNull String query, @NonNull List<Attribute> metadata) {
        searchManager.setQuery(query);
        searchManager.setTags(metadata);
        performSearch();
    }

    public void toggleFavouriteFilter() {
        searchManager.setFilterFavourites(!searchManager.isFilterFavourites());
        performSearch();
    }

    public void toggleContentFavourite(@NonNull final Content content) { // TODO file update in another thread
        if (!content.isBeingDeleted()) {
            content.setFavourite(!content.isFavourite());

            // Persist in it DB
            collectionDao.insertContent(content);

            // Persist in it JSON
            if (!content.getJsonUri().isEmpty())
                ContentHelper.updateJson(getApplication(), content);
            else ContentHelper.createJson(content);
        }
    }

    public void addContentToQueue(@NonNull final Content content) {
        collectionDao.addContentToQueue(content);
    }

    public void flagContentDelete(@NonNull final Content content, boolean flag) {
        content.setIsBeingDeleted(flag);
        collectionDao.insertContent(content);
    }

    public void deleteItems(@NonNull final List<Content> contents, Runnable callback, Consumer<Throwable> onError) {
        for (Content c : contents) flagContentDelete(c, true);

        compositeDisposable.add(
                Observable.fromIterable(contents)
                        .subscribeOn(Schedulers.io())
                        .flatMap(s -> Observable.fromCallable(() -> deleteContent(s)))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                v -> {
                                },
                                onError::accept,
                                callback::run
                        )
        );
    }

    private Content deleteContent(final Content content) throws ContentNotRemovedException {
        try {
            // Check if given content still exists in DB
            ObjectBoxDB db = ObjectBoxDB.getInstance(HentoidApp.getAppContext());
            Content theContent = db.selectContentById(content.getId());

            if (theContent != null) {
                ContentHelper.removeContent(content);
                db.deleteContent(content);
                Timber.d("Removed item: %s from db and file system.", content.getTitle());
                return content;
            }
            throw new ContentNotRemovedException(content, "ContentId " + content.getId() + " does not refer to a valid content");
        } catch (Exception e) {
            Timber.e(e, "Error when trying to delete %s", content.getId());
            throw new ContentNotRemovedException(content, "Error when trying to delete " + content.getId() + " : " + e.getMessage());
        }
    }
}
