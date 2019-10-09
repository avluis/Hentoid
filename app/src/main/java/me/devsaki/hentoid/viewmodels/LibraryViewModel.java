package me.devsaki.hentoid.viewmodels;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.PagedList;

import java.util.List;

import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.listener.PagedResultListener;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.widget.ContentSearchManager;
import timber.log.Timber;


public class LibraryViewModel extends AndroidViewModel implements PagedResultListener<Content> {

    // Settings
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = this::onSharedPreferenceChanged;
    private int booksPerPage = Preferences.getContentPageQuantity();
    private int currentPage = 1;
    private int maxPage = 999; //TODO

    // Technical
    private final ContentSearchManager searchManager = new ContentSearchManager(new ObjectBoxDAO(HentoidApp.getAppContext()));
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    // Collection data
    private final MutableLiveData<ObjectBoxDAO.ContentQueryResult> library = new MutableLiveData<>();        // Current content
    private LiveData<PagedList<Content>> libraryPaged = searchManager.getLibrary(booksPerPage);


    public LibraryViewModel(@NonNull Application application) {
        super(application);
        Preferences.registerPrefsChangedListener(prefsListener);
    }

    public void onSaveState(Bundle outState) {
        searchManager.saveToBundle(outState);
    }

    public void onRestoreState(@Nullable Bundle savedState) {
        if (savedState == null) return;
        searchManager.loadFromBundle(savedState);
    }

    @NonNull
    public LiveData<ObjectBoxDAO.ContentQueryResult> getLibrary() {
        return library;
    }

    @NonNull
    public LiveData<PagedList<Content>> getLibraryPaged() {
        return libraryPaged;
    }


    private void performSearch(int page) {
        performSearch(page, false);
    }

    private void performSearch(int page, boolean forceLoad) {
        if (!forceLoad && (page == currentPage || page < 1 || page > maxPage)) return;

        currentPage = page;
        searchManager.setCurrentPage(currentPage);
        libraryPaged = searchManager.getLibrary(booksPerPage);
    }

    @Override
    public void onPagedResultReady(List<Content> results, long totalSelectedContent, long totalContent) {
        Timber.i(">>Results ready : %s items (%s/%s) - page %s", results.size(), totalSelectedContent, totalContent, currentPage);
        ObjectBoxDAO.ContentQueryResult result = new ObjectBoxDAO.ContentQueryResult(results, totalSelectedContent, totalContent, currentPage);
        library.setValue(result);
    }

    @Override
    public void onPagedResultFailed(Content result, String message) {
        ToastUtil.toast("Book list loading failed");
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        searchManager.dispose();
        Preferences.unregisterPrefsChangedListener(prefsListener);
        compositeDisposable.clear();
    }

    private void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        Timber.i("Prefs change detected : %s", key);
        switch (key) {
            case Preferences.Key.PREF_QUANTITY_PER_PAGE_LISTS:
                booksPerPage = Preferences.getContentPageQuantity();
                break;
            default:
                // Other changes aren't handled here
        }
    }

    public void previousPage() {
        Timber.i(">>previousPage");
        performSearch(currentPage - 1);
    }

    public void nextPage() {
        Timber.i(">>nextPage");
        performSearch(currentPage + 1);
    }

    public void loadPage(int page) {
        Timber.i(">>loadPage");
        if (page != currentPage) performSearch(page);
    }
}
