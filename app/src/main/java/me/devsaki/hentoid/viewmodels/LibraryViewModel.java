package me.devsaki.hentoid.viewmodels;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

import javax.annotation.Nonnull;

import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.database.ObjectBoxCollectionAccessor;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.listener.PagedResultListener;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.widget.ContentSearchManager;
import timber.log.Timber;


public class LibraryViewModel extends AndroidViewModel implements PagedResultListener<Content> {

    // Settings
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = this::onSharedPreferenceChanged;
    private int booksPerPage = Preferences.getContentPageQuantity();
    private int currentPage = 1;

    // Collection data
    private final MutableLiveData<ObjectBoxCollectionAccessor.ContentQueryResult> library = new MutableLiveData<>();        // Current content

    // Technical
    private ContentSearchManager searchManager = null;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();


    public LibraryViewModel(@NonNull Application application) {
        super(application);
        Preferences.registerPrefsChangedListener(listener);
        library.setValue(null); // Default content; tells everyone nothing has been loaded yet
    }

    @NonNull
    public LiveData<ObjectBoxCollectionAccessor.ContentQueryResult> getLibrary() {
        return library;
    }

    public void loadFromSearchParams(@Nonnull Bundle bundle) {
        Context ctx = getApplication().getApplicationContext();
        searchManager = new ContentSearchManager(new ObjectBoxCollectionAccessor(ctx));
        searchManager.loadFromBundle(bundle);
        performSearch(1);
    }

    private void performSearch(int page) {
        currentPage = page;
        if (page > 1) searchManager.setCurrentPage(page);
        searchManager.searchLibraryForContent(booksPerPage, this);
    }

    @Override
    public void onPagedResultReady(List<Content> results, long totalSelectedContent, long totalContent) {
        ObjectBoxCollectionAccessor.ContentQueryResult result = new ObjectBoxCollectionAccessor.ContentQueryResult(results, totalSelectedContent, totalContent);
        library.setValue(result);
    }

    @Override
    public void onPagedResultFailed(Content result, String message) {
        ToastUtil.toast("Book list loading failed");
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (searchManager != null) searchManager.dispose();
        Preferences.unregisterPrefsChangedListener(listener);
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

    public void loadMore() {
        performSearch(++currentPage);
    }
}
