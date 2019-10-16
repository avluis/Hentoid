package me.devsaki.hentoid.viewmodels;

import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.paging.PagedList;

import java.util.List;

import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.widget.ContentSearchManager;


public class LibraryViewModel extends AndroidViewModel {

    // Technical
    private final ObjectBoxDAO collectionDao = new ObjectBoxDAO(getApplication().getApplicationContext());
    private final ContentSearchManager searchManager = new ContentSearchManager(collectionDao);
    private final CompositeDisposable compositeDisposable = new CompositeDisposable(); // TODO remove if useless

    // Collection data
    private LiveData<PagedList<Content>> currentSource;
    private LiveData<Integer> totalContent = collectionDao.countAllBooks();
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

    public Bundle getSearchManagerBundle() {
        Bundle bundle = new Bundle();
        searchManager.saveToBundle(bundle);
        return bundle;
    }


    public void performSearch() {
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

    public void toggleContentFavourite(@NonNull final Content content) {
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
}
