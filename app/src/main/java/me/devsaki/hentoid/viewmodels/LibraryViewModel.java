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

import java.io.File;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ZipUtil;
import me.devsaki.hentoid.util.exception.ContentNotRemovedException;
import me.devsaki.hentoid.widget.ContentSearchManager;
import timber.log.Timber;


public class LibraryViewModel extends AndroidViewModel {

    // Technical
    private final ObjectBoxDAO collectionDao = new ObjectBoxDAO(getApplication().getApplicationContext());
    private final ContentSearchManager searchManager = new ContentSearchManager(collectionDao);
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    private static final String AUTHORIZED_CHARS = "[^a-zA-Z0-9.-]";

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

    public void toggleContentFavourite(@NonNull final Content content) {
        if (content.isBeingDeleted()) return;

        // Flag the content as "being favourited" (triggers blink animation)
        content.setIsBeingFavourited(true);
        collectionDao.insertContent(content);

        compositeDisposable.add(
                Single.fromCallable(() -> toggleFavourite(content.getId()))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                v -> {
                                },
                                Timber::e
                        )
        );
    }

    private Content toggleFavourite(long contentId) {

        // Check if given content still exists in DB
        Content theContent = collectionDao.selectContent(contentId);

        if (theContent != null) {
            theContent.setFavourite(!theContent.isFavourite());
            theContent.setIsBeingFavourited(false);

            // Persist in it JSON
            if (!theContent.getJsonUri().isEmpty())
                ContentHelper.updateJson(getApplication(), theContent);
            else ContentHelper.createJson(theContent);

            // Persist in it DB
            collectionDao.insertContent(theContent);

            return theContent;
        }

        throw new InvalidParameterException("ContentId " + contentId + " does not refer to a valid content");
    }

    public void addContentToQueue(@NonNull final Content content) {
        collectionDao.addContentToQueue(content);
    }

    public void flagContentDelete(@NonNull final Content content, boolean flag) {
        content.setIsBeingDeleted(flag);
        collectionDao.insertContent(content);
    }

    public void deleteItems(@NonNull final List<Content> contents, Runnable onComplete, Consumer<Throwable> onError) {
        // Flag the content as "being deleted" (triggers blink animation)
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
                                onComplete::run
                        )
        );
    }

    private Content deleteContent(@NonNull final Content content) throws ContentNotRemovedException {
        try {
            // Check if given content still exists in DB
            Content theContent = collectionDao.selectContent(content.getId());

            if (theContent != null) {
                ContentHelper.removeContent(theContent);
                collectionDao.deleteContent(theContent);
                Timber.d("Removed item: %s from db and file system.", theContent.getTitle());
                return theContent;
            }
            throw new ContentNotRemovedException(content, "ContentId " + content.getId() + " does not refer to a valid content");
        } catch (Exception e) {
            Timber.e(e, "Error when trying to delete %s", content.getId());
            throw new ContentNotRemovedException(content, "Error when trying to delete " + content.getId() + " : " + e.getMessage(), e);
        }
    }

    public void archiveContent(@NonNull final Content content, Consumer<File> onSuccess) {
        Timber.d("Building file list for: %s", content.getTitle());
        // Build list of files

        File dir = ContentHelper.getContentDownloadDir(content);

        File[] files = dir.listFiles();
        if (files != null && files.length > 0) {
            Arrays.sort(files);
            ArrayList<File> fileList = new ArrayList<>();
            for (File file : files) {
                String filename = file.getName();
                if (filename.endsWith(".json") || filename.contains("thumb")) {
                    break;
                }
                fileList.add(file);
            }

            // Create folder to share from
            File sharedDir = new File(getApplication().getExternalCacheDir() + "/shared");
            if (FileHelper.createDirectory(sharedDir)) {
                Timber.d("Shared folder created.");
            }

            // Clean directory (in case of previous job)
            if (FileHelper.cleanDirectory(sharedDir)) {
                Timber.d("Shared folder cleaned up.");
            }

            // Build destination file
            File dest = new File(getApplication().getExternalCacheDir() + "/shared",
                    content.getTitle().replaceAll(AUTHORIZED_CHARS, "_") + ".zip");
            Timber.d("Destination file: %s", dest);

            compositeDisposable.add(
                    Single.fromCallable(() -> ZipUtil.zipFiles(fileList, dest))
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(onSuccess::accept,
                                    Timber::e)
            );
        }
    }
}
