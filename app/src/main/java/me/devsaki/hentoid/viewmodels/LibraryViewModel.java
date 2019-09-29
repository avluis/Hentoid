package me.devsaki.hentoid.viewmodels;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.annimon.stream.Stream;
import com.annimon.stream.function.BooleanConsumer;
import com.annimon.stream.function.Consumer;

import java.io.File;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.ObjectBoxCollectionAccessor;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.listener.PagedResultListener;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.widget.ContentSearchManager;
import timber.log.Timber;

import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static com.annimon.stream.Collectors.toList;


public class LibraryViewModel extends AndroidViewModel implements PagedResultListener<Content> {

    // Settings


    // Collection data
    private final MutableLiveData<List<Content>> library = new MutableLiveData<>();        // Current content

    // Technical
    private ContentSearchManager searchManager = null;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();


    public LibraryViewModel(@NonNull Application application) {
        super(application);
        library.setValue(null); // Default content; tells everyone nothing has been loaded yet
    }

    @NonNull
    public LiveData<List<Content>> getLibrary() {
        return library;
    }

    public void loadFromSearchParams(@Nonnull Bundle bundle) {
        Context ctx = getApplication().getApplicationContext();
        searchManager = new ContentSearchManager(new ObjectBoxCollectionAccessor(ctx));
        searchManager.loadFromBundle(bundle);
        int contentIndex = bundle.getInt("contentIndex", -1);
        if (contentIndex > -1) searchManager.setCurrentPage(contentIndex);
        searchManager.searchLibraryForContent(-1, this);
    }

    @Override
    public void onPagedResultReady(List<Content> results, long totalSelectedContent, long totalContent) {
        library.setValue(results);
        // TODO set subtotals
    }

    @Override
    public void onPagedResultFailed(Content result, String message) {
        ToastUtil.toast("Book list loading failed");
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (searchManager != null) searchManager.dispose();
        compositeDisposable.clear();
    }
}
