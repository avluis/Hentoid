package me.devsaki.hentoid.database;

import android.content.Context;
import android.util.SparseIntArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

public class ObjectBoxDAO implements CollectionDAO {

    private final ObjectBoxDB db;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    private static final int MODE_SEARCH_CONTENT_MODULAR = 0;
    private static final int MODE_COUNT_CONTENT_MODULAR = 1;
    private static final int MODE_SEARCH_CONTENT_UNIVERSAL = 2;
    private static final int MODE_COUNT_CONTENT_UNIVERSAL = 3;

    static class ContentIdQueryResult {
        long[] contentIds;
        long totalContent;
        long totalSelectedContent;
    }

    public static class ContentQueryResult {
        public List<Content> pagedContents;
        public long totalContent;
        public long totalSelectedContent;
        public int currentPage;

        ContentQueryResult() {
        }

        public ContentQueryResult(List<Content> pagedContents, long totalContent, long totalSelectedContent, int currentPage) {
            this.pagedContents = pagedContents;
            this.totalContent = totalContent;
            this.totalSelectedContent = totalSelectedContent;
            this.currentPage = currentPage;
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
                        () -> pagedContentSearch(MODE_SEARCH_CONTENT_MODULAR, "", Collections.emptyList(), page, booksPerPage, orderStyle, favouritesOnly)
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
                        () -> contentIdSearch(MODE_SEARCH_CONTENT_MODULAR, "", Collections.emptyList(), orderStyle, favouritesOnly)
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
                        () -> pagedContentSearch(MODE_SEARCH_CONTENT_MODULAR, query, metadata, page, booksPerPage, orderStyle, favouritesOnly)
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
                        () -> contentIdSearch(MODE_SEARCH_CONTENT_MODULAR, query, metadata, orderStyle, favouritesOnly)
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
                        () -> pagedContentSearch(MODE_SEARCH_CONTENT_MODULAR, query, metadata, 1, 1, 1, favouritesOnly)
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
                        () -> pagedContentSearch(MODE_SEARCH_CONTENT_UNIVERSAL, query, Collections.emptyList(), page, booksPerPage, orderStyle, favouritesOnly)
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
                        () -> contentIdSearch(MODE_SEARCH_CONTENT_UNIVERSAL, query, Collections.emptyList(), orderStyle, favouritesOnly)
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
                        () -> pagedContentSearch(MODE_COUNT_CONTENT_UNIVERSAL, query, Collections.emptyList(), 1, 1, 1, favouritesOnly)
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

/*
    public DataObserver<ContentQueryResult> getPagedContent(int mode, String filter, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, boolean favouritesOnly) {
        // Main query
        Query<Content> query;
        if (MODE_SEARCH_CONTENT_MODULAR == mode) {
            query = db.selectContentSearchQ(filter, page, booksPerPage, metadata, favouritesOnly, orderStyle);
        } else if (MODE_SEARCH_CONTENT_UNIVERSAL == mode) {
            //result.pagedContents = db.selectContentUniversal(filter, page, booksPerPage, favouritesOnly, orderStyle);
        } else {
            //result.pagedContents = Collections.emptyList();
        }
        return query.subscribe().observer(data -> processResults(data, orderStyle, booksPerPage));
    }

    private List<Content> processResults(@NonNull List<Content> contents, int orderStyle, int booksPerPage)
    {
        if (orderStyle != Preferences.Constant.ORDER_CONTENT_RANDOM) {
            if (booksPerPage < 0) result = query.find();
            else result = query.find(start, booksPerPage);
        } else {
            result = shuffleRandomSort(query, start, booksPerPage);
        }
        return setQueryIndexes(result, page, booksPerPage);
    }

*/


    private ContentQueryResult pagedContentSearch(int mode, String filter, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, boolean favouritesOnly) {

// StringBuilder sb = new StringBuilder();
// for (Attribute a : metadata) sb.append(a.getName()).append(";");
// timber.log.Timber.i("pagedContentSearch mode=" + mode +",filter=" + filter +",meta=" + sb.toString() + ",p=" + page +",bpp=" +  booksPerPage +",os=" +  orderStyle +",fav=" + favouritesOnly);

        ContentQueryResult result = new ContentQueryResult();

        if (MODE_SEARCH_CONTENT_MODULAR == mode) {
            result.pagedContents = db.selectContentSearch(filter, page, booksPerPage, metadata, favouritesOnly, orderStyle);
        } else if (MODE_SEARCH_CONTENT_UNIVERSAL == mode) {
            result.pagedContents = db.selectContentUniversal(filter, page, booksPerPage, favouritesOnly, orderStyle);
        } else {
            result.pagedContents = Collections.emptyList();
        }
        // Fetch total query count (i.e. total number of books corresponding to the given filter, in all pages)
        if (MODE_SEARCH_CONTENT_MODULAR == mode || MODE_COUNT_CONTENT_MODULAR == mode) {
            result.totalSelectedContent = db.countContentSearch(filter, metadata, favouritesOnly);
        } else if (MODE_SEARCH_CONTENT_UNIVERSAL == mode || MODE_COUNT_CONTENT_UNIVERSAL == mode) {
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

    private ContentIdQueryResult contentIdSearch(int mode, String filter, List<Attribute> metadata, int orderStyle, boolean favouritesOnly) {

        ContentIdQueryResult result = new ContentIdQueryResult();

        if (MODE_SEARCH_CONTENT_MODULAR == mode) {
            result.contentIds = db.selectContentSearchId(filter, metadata, favouritesOnly, orderStyle);
        } else if (MODE_SEARCH_CONTENT_UNIVERSAL == mode) {
            result.contentIds = db.selectContentUniversalId(filter, favouritesOnly, orderStyle);
        } else {
            result.contentIds = new long[0];
        }
        // Fetch total query count (i.e. total number of books corresponding to the given filter, in all pages)
        if (MODE_SEARCH_CONTENT_MODULAR == mode || MODE_COUNT_CONTENT_MODULAR == mode) {
            result.totalSelectedContent = db.countContentSearch(filter, metadata, favouritesOnly);
        } else if (MODE_SEARCH_CONTENT_UNIVERSAL == mode || MODE_COUNT_CONTENT_UNIVERSAL == mode) {
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
