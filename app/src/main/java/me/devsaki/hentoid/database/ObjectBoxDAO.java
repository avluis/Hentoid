package me.devsaki.hentoid.database;

import android.content.Context;
import android.util.SparseIntArray;

import androidx.annotation.IntDef;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.objectbox.android.ObjectBoxDataSource;
import io.objectbox.query.Query;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Language;
import me.devsaki.hentoid.listener.PagedResultListener;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;

public class ObjectBoxDAO implements CollectionDAO {

    private final ObjectBoxDB db;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();


    @IntDef({Mode.SEARCH_CONTENT_MODULAR, Mode.COUNT_CONTENT_MODULAR, Mode.SEARCH_CONTENT_UNIVERSAL, Mode.COUNT_CONTENT_UNIVERSAL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Mode {
        int SEARCH_CONTENT_MODULAR = 0;
        int COUNT_CONTENT_MODULAR = 1;
        int SEARCH_CONTENT_UNIVERSAL = 2;
        int COUNT_CONTENT_UNIVERSAL = 3;
    }

    static class ContentIdQueryResult {
        long[] contentIds;
        long totalContent;
        long totalSelectedContent;
    }

    public static class ContentQueryResult {
        List<Content> pagedContents;
        long totalContent;
        long totalSelectedContent;
        int currentPage;

        ContentQueryResult() {
        }
    }

    static class AttributeQueryResult {
        final List<Attribute> pagedAttributes = new ArrayList<>();
        long totalSelectedAttributes;

        public void addAll(List<Attribute> list) {
            pagedAttributes.addAll(list);
        }
    }


    public ObjectBoxDAO(Context ctx) {
        db = ObjectBoxDB.getInstance(ctx);
    }

    @Override
    public void dispose() {
        compositeDisposable.clear();
    }


    @Override
    public void getRecentBooksPaged(Language language, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, PagedResultListener<Content> listener) {
        compositeDisposable.add(
                Single.fromCallable(
                        () -> pagedContentSearch(Mode.SEARCH_CONTENT_MODULAR, "", Collections.emptyList(), page, booksPerPage, orderStyle, favouritesOnly)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(contentQueryResult -> listener.onPagedResultReady(contentQueryResult.pagedContents, contentQueryResult.totalSelectedContent, contentQueryResult.totalContent))
        );
    }

    @Override
    public void getRecentBookIds(Language language, int orderStyle, boolean favouritesOnly, PagedResultListener<Long> listener) {
        compositeDisposable.add(
                Single.fromCallable(
                        () -> contentIdSearch(Mode.SEARCH_CONTENT_MODULAR, "", Collections.emptyList(), orderStyle, favouritesOnly)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(contentIdQueryResult -> listener.onPagedResultReady(
                                Helper.getListFromPrimitiveArray(contentIdQueryResult.contentIds), contentIdQueryResult.totalSelectedContent, contentIdQueryResult.totalContent))
        );
    }

    @Override
    public void searchBooksPaged(String query, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, PagedResultListener<Content> listener) {
        compositeDisposable.add(
                Single.fromCallable(
                        () -> pagedContentSearch(Mode.SEARCH_CONTENT_MODULAR, query, metadata, page, booksPerPage, orderStyle, favouritesOnly)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(contentQueryResult -> listener.onPagedResultReady(contentQueryResult.pagedContents, contentQueryResult.totalSelectedContent, contentQueryResult.totalContent))
        );
    }

    @Override
    public void searchBookIds(String query, List<Attribute> metadata, int orderStyle, boolean favouritesOnly, PagedResultListener<Long> listener) {
        compositeDisposable.add(
                Single.fromCallable(
                        () -> contentIdSearch(Mode.SEARCH_CONTENT_MODULAR, query, metadata, orderStyle, favouritesOnly)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(contentIdQueryResult -> listener.onPagedResultReady(
                                Helper.getListFromPrimitiveArray(contentIdQueryResult.contentIds), contentIdQueryResult.totalSelectedContent, contentIdQueryResult.totalContent))
        );
    }

    @Override
    public void countBooks(String query, List<Attribute> metadata, boolean favouritesOnly, PagedResultListener<Content> listener) {
        compositeDisposable.add(
                Single.fromCallable(
                        () -> pagedContentSearch(Mode.SEARCH_CONTENT_MODULAR, query, metadata, 1, 1, 1, favouritesOnly)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(contentQueryResult -> listener.onPagedResultReady(contentQueryResult.pagedContents, contentQueryResult.totalSelectedContent, contentQueryResult.totalContent))
        );
    }

    @Override
    public void searchBooksUniversalPaged(String query, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, PagedResultListener<Content> listener) {
        compositeDisposable.add(
                Single.fromCallable(
                        () -> pagedContentSearch(Mode.SEARCH_CONTENT_UNIVERSAL, query, Collections.emptyList(), page, booksPerPage, orderStyle, favouritesOnly)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(contentQueryResult -> listener.onPagedResultReady(contentQueryResult.pagedContents, contentQueryResult.totalSelectedContent, contentQueryResult.totalContent))
        );
    }

    @Override
    public void searchBookIdsUniversal(String query, int orderStyle, boolean favouritesOnly, PagedResultListener<Long> listener) {
        compositeDisposable.add(
                Single.fromCallable(
                        () -> contentIdSearch(Mode.SEARCH_CONTENT_UNIVERSAL, query, Collections.emptyList(), orderStyle, favouritesOnly)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(contentIdQueryResult -> listener.onPagedResultReady(
                                Helper.getListFromPrimitiveArray(contentIdQueryResult.contentIds), contentIdQueryResult.totalSelectedContent, contentIdQueryResult.totalContent))
        );
    }

    @Override
    public void countBooksUniversal(String query, boolean favouritesOnly, PagedResultListener<Content> listener) {
        compositeDisposable.add(
                Single.fromCallable(
                        () -> pagedContentSearch(Mode.COUNT_CONTENT_UNIVERSAL, query, Collections.emptyList(), 1, 1, 1, favouritesOnly)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(contentQueryResult -> listener.onPagedResultReady(contentQueryResult.pagedContents, contentQueryResult.totalSelectedContent, contentQueryResult.totalContent))
        );
    }

    @Override
    public void getAttributeMasterDataPaged(List<AttributeType> types, String filter, List<Attribute> attrs, boolean filterFavourites, int page, int booksPerPage, int orderStyle, ResultListener<List<Attribute>> listener) {
        compositeDisposable.add(
                Single.fromCallable(
                        () -> pagedAttributeSearch(types, filter, attrs, filterFavourites, orderStyle, page, booksPerPage)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(result -> listener.onResultReady(result.pagedAttributes, result.totalSelectedAttributes))
        );
    }

    @Override
    public void countAttributesPerType(List<Attribute> filter, ResultListener<SparseIntArray> listener) {
        compositeDisposable.add(
                Single.fromCallable(
                        () -> count(filter)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(result -> listener.onResultReady(result, result.size()))
        );
    }


    public LiveData<PagedList<Content>> searchBooksUniversal(String query, int orderStyle, boolean favouritesOnly) {
        return getPagedContent(Mode.SEARCH_CONTENT_UNIVERSAL, query, Collections.emptyList(), orderStyle, favouritesOnly);
    }

    public LiveData<PagedList<Content>> searchBooks(String query, List<Attribute> metadata, int orderStyle, boolean favouritesOnly) {
        return getPagedContent(Mode.SEARCH_CONTENT_MODULAR, query, metadata, orderStyle, favouritesOnly);
    }

    public LiveData<PagedList<Content>> getRecentBooks(int orderStyle, boolean favouritesOnly) {
        return getPagedContent(Mode.SEARCH_CONTENT_MODULAR, "", Collections.emptyList(), orderStyle, favouritesOnly);
    }

    private LiveData<PagedList<Content>> getPagedContent(int mode, String filter, List<Attribute> metadata, int orderStyle, boolean favouritesOnly) {
        boolean isRandom = (orderStyle == Preferences.Constant.ORDER_CONTENT_RANDOM);

        if (Mode.SEARCH_CONTENT_MODULAR == mode) {
            Query<Content> query = db.selectContentSearchQ(filter, metadata, favouritesOnly, orderStyle);
            return new LivePagedListBuilder<>(
                    isRandom ? new ObjectBoxRandomDataSource.Factory<>(query) : new ObjectBoxDataSource.Factory<>(query),
                    20
            ).build();
        } else { // Mode.SEARCH_CONTENT_UNIVERSAL
            // Due to objectBox limitations (see https://github.com/objectbox/objectbox-java/issues/497 and https://github.com/objectbox/objectbox-java/issues/201)
            // querying Content and attributes have to be done separately
            Query<Content> query1 = db.queryContentUniversalAttributes(filter, favouritesOnly);
            LiveData<PagedList<Content>> livedata1 = new LivePagedListBuilder<>(
                    isRandom ? new ObjectBoxRandomDataSource.Factory<>(query1) : new ObjectBoxDataSource.Factory<>(query1),
                    20
            ).build();

            Query<Content> query2 = db.queryContentUniversalContent2(filter, favouritesOnly, orderStyle);
            LiveData<PagedList<Content>> livedata2 = new LivePagedListBuilder<>(
                    isRandom ? new ObjectBoxRandomDataSource.Factory<>(query2) : new ObjectBoxDataSource.Factory<>(query2),
                    20
            ).build();

            MediatorLiveData<PagedList<Content>> result = new MediatorLiveData<>();
            result.addSource(livedata1, result::setValue);
            result.addSource(livedata2, result::setValue);

            return result;
        }
    }
/*
    private LiveData<PagedList<Content>> getPagedContent(int mode, String filter, List<Attribute> metadata, int booksPerPage, int orderStyle, boolean favouritesOnly) {
        if (orderStyle != Preferences.Constant.ORDER_CONTENT_RANDOM)
            return getOrderedPagedContent(mode, filter, metadata, booksPerPage, orderStyle, favouritesOnly);
        else
            return getRandomPagedContent(mode, filter, metadata, booksPerPage, favouritesOnly);
    }

    private LiveData<PagedList<Content>> getOrderedPagedContent(@Mode int mode, String filter, List<Attribute> metadata, int booksPerPage, int orderStyle, boolean favouritesOnly) {
        if (Mode.SEARCH_CONTENT_MODULAR == mode) {
            Query<Content> query = db.selectContentSearchQ(filter, metadata, favouritesOnly, orderStyle);
            return new LivePagedListBuilder<>(
                    new ObjectBoxDataSource.Factory<>(query),
                    booksPerPage
            ).build();
        } else { // Mode.SEARCH_CONTENT_UNIVERSAL
            // Due to objectBox limitations (see https://github.com/objectbox/objectbox-java/issues/497 and https://github.com/objectbox/objectbox-java/issues/201)
            // querying Content and attributes have to be done separately
            Query<Content> query1 = db.queryContentUniversalAttributes(filter, favouritesOnly);
            LiveData<PagedList<Content>> livedata1 = new LivePagedListBuilder<>(
                    new ObjectBoxDataSource.Factory<>(query1),
                    booksPerPage
            ).build();

            Query<Content> query2 = db.queryContentUniversalContent2(filter, favouritesOnly, orderStyle);
            LiveData<PagedList<Content>> livedata2 = new LivePagedListBuilder<>(
                    new ObjectBoxDataSource.Factory<>(query2),
                    booksPerPage
            ).build();

            MediatorLiveData<PagedList<Content>> result = new MediatorLiveData<>();
            result.addSource(livedata1, result::setValue);
            result.addSource(livedata2, result::setValue);

            return result;
        }
    }

    private LiveData<PagedList<Content>> getRandomPagedContent(@Mode int mode, String filter, List<Attribute> metadata, int booksPerPage, boolean favouritesOnly) {
        // TODO
        return getOrderedPagedContent(mode, filter, metadata, booksPerPage, Preferences.Constant.ORDER_CONTENT_LAST_DL_DATE_FIRST, favouritesOnly);
    }
*/

    private ContentQueryResult pagedContentSearch(@Mode int mode, String filter, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, boolean favouritesOnly) {

// StringBuilder sb = new StringBuilder();
// for (Attribute a : metadata) sb.append(a.getName()).append(";");
// timber.log.Timber.i("pagedContentSearch mode=" + mode +",filter=" + filter +",meta=" + sb.toString() + ",p=" + page +",bpp=" +  booksPerPage +",os=" +  orderStyle +",fav=" + favouritesOnly);

        ContentQueryResult result = new ContentQueryResult();

        if (Mode.SEARCH_CONTENT_MODULAR == mode) {
            result.pagedContents = db.selectContentSearch(filter, page, booksPerPage, metadata, favouritesOnly, orderStyle);
        } else if (Mode.SEARCH_CONTENT_UNIVERSAL == mode) {
            result.pagedContents = db.selectContentUniversal(filter, page, booksPerPage, favouritesOnly, orderStyle);
        } else {
            result.pagedContents = Collections.emptyList();
        }
        // Fetch total query count (i.e. total number of books corresponding to the given filter, in all pages)
        if (Mode.SEARCH_CONTENT_MODULAR == mode || Mode.COUNT_CONTENT_MODULAR == mode) {
            result.totalSelectedContent = db.countContentSearch(filter, metadata, favouritesOnly);
        } else if (Mode.SEARCH_CONTENT_UNIVERSAL == mode || Mode.COUNT_CONTENT_UNIVERSAL == mode) {
            result.totalSelectedContent = db.countContentUniversal(filter, favouritesOnly);
        } else {
            result.totalSelectedContent = 0;
        }
        // Fetch total book count (i.e. total number of books in all the collection, regardless of filter)
        result.totalContent = db.countAllContent();

// sb = new StringBuilder();
//  for (Content c : result.pagedContents) sb.append(c.getId()).append(";");
//  timber.log.Timber.i("pagedContentSearch result [%s] : %s", result.totalSelectedContent, sb.toString());

        return result;
    }

    private ContentIdQueryResult contentIdSearch(@Mode int mode, String filter, List<Attribute> metadata, int orderStyle, boolean favouritesOnly) {

        ContentIdQueryResult result = new ContentIdQueryResult();

        if (Mode.SEARCH_CONTENT_MODULAR == mode) {
            result.contentIds = db.selectContentSearchId(filter, metadata, favouritesOnly, orderStyle);
        } else if (Mode.SEARCH_CONTENT_UNIVERSAL == mode) {
            result.contentIds = db.selectContentUniversalId(filter, favouritesOnly, orderStyle);
        } else {
            result.contentIds = new long[0];
        }
        // Fetch total query count (i.e. total number of books corresponding to the given filter, in all pages)
        if (Mode.SEARCH_CONTENT_MODULAR == mode || Mode.COUNT_CONTENT_MODULAR == mode) {
            result.totalSelectedContent = db.countContentSearch(filter, metadata, favouritesOnly);
        } else if (Mode.SEARCH_CONTENT_UNIVERSAL == mode || Mode.COUNT_CONTENT_UNIVERSAL == mode) {
            result.totalSelectedContent = db.countContentUniversal(filter, favouritesOnly);
        } else {
            result.totalSelectedContent = 0;
        }
        // Fetch total book count (i.e. total number of books in all the collection, regardless of filter)
        result.totalContent = db.countAllContent();

        return result;
    }

    private AttributeQueryResult pagedAttributeSearch(List<AttributeType> attrTypes, String filter, List<Attribute> attrs, boolean filterFavourites, int sortOrder, int pageNum, int itemPerPage) {
        AttributeQueryResult result = new AttributeQueryResult();

        if (attrTypes != null && !attrTypes.isEmpty()) {

            if (attrTypes.get(0).equals(AttributeType.SOURCE)) {
                result.addAll(db.selectAvailableSources(attrs));
                result.totalSelectedAttributes = result.pagedAttributes.size();
            } else {
                result.totalSelectedAttributes = 0;
                for (AttributeType type : attrTypes) {
                    // TODO fix sorting when concatenating both lists
                    result.addAll(db.selectAvailableAttributes(type, attrs, filter, filterFavourites, sortOrder, pageNum, itemPerPage)); // No favourites button in SearchActivity
                    result.totalSelectedAttributes += db.countAvailableAttributes(type, attrs, filter, filterFavourites);
                }
            }
        }

        return result;
    }

    private SparseIntArray count(List<Attribute> filter) {
        SparseIntArray result;

        if (null == filter || filter.isEmpty()) {
            result = db.countAvailableAttributesPerType();
            result.put(AttributeType.SOURCE.getCode(), db.selectAvailableSources().size());
        } else {
            result = db.countAvailableAttributesPerType(filter);
            result.put(AttributeType.SOURCE.getCode(), db.selectAvailableSources(filter).size());
        }

        return result;
    }
}
