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
import me.devsaki.hentoid.collection.CollectionAccessor;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Language;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.PagedResultListener;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.util.Helper;

public class ObjectBoxCollectionAccessor implements CollectionAccessor {

    private final ObjectBoxDB db;
    private CompositeDisposable compositeDisposable = new CompositeDisposable();

    private static final int MODE_SEARCH_CONTENT_MODULAR = 0;
    private static final int MODE_COUNT_CONTENT_MODULAR = 1;
    private static final int MODE_SEARCH_CONTENT_UNIVERSAL = 2;
    private static final int MODE_COUNT_CONTENT_UNIVERSAL = 3;

    private static final int MODE_SEARCH_ATTRIBUTE_TEXT = 0;
    private static final int MODE_SEARCH_ATTRIBUTE_AVAILABLE = 1;
    private static final int MODE_SEARCH_ATTRIBUTE_COMBINED = 2;

    static class ContentIdQueryResult {
        long[] pagedContentIds;
        long totalContent;
        long totalSelectedContent;
    }

    static class ContentQueryResult {
        List<Content> pagedContents;
        long totalContent;
        long totalSelectedContent;
    }

    static class AttributeQueryResult {
        List<Attribute> pagedAttributes = new ArrayList<>();
        long totalSelectedAttributes;

        public void addAll(List<Attribute> list) {
            pagedAttributes.addAll(list);
        }
    }


    public ObjectBoxCollectionAccessor(Context ctx) {
        db = ObjectBoxDB.getInstance(ctx);
    }


    @Override
    public void getRecentBooksPaged(Site site, Language language, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, PagedResultListener<Content> listener) {
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
    public void getRecentBookIdsPaged(Site site, Language language, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, PagedResultListener<Long> listener) {
        compositeDisposable.add(
                Single.fromCallable(
                        () -> pagedContentIdSearch(MODE_SEARCH_CONTENT_MODULAR, "", Collections.emptyList(), page, booksPerPage, orderStyle, favouritesOnly)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(contentIdQueryResult -> listener.onPagedResultReady(
                                Helper.getListFromPrimitiveArray(contentIdQueryResult.pagedContentIds), contentIdQueryResult.totalSelectedContent, contentIdQueryResult.totalContent))
        );
    }

    @Override
    public void getPages(Content content, PagedResultListener<Content> listener) {
        throw new UnsupportedOperationException("Not implemented");
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
    public void searchBookIdsPaged(String query, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, PagedResultListener<Long> listener) {
        compositeDisposable.add(
                Single.fromCallable(
                        () -> pagedContentIdSearch(MODE_SEARCH_CONTENT_MODULAR, query, metadata, page, booksPerPage, orderStyle, favouritesOnly)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(contentIdQueryResult -> listener.onPagedResultReady(
                                Helper.getListFromPrimitiveArray(contentIdQueryResult.pagedContentIds), contentIdQueryResult.totalSelectedContent, contentIdQueryResult.totalContent))
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
    public void searchBookIdsUniversalPaged(String query, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, PagedResultListener<Long> listener) {
        compositeDisposable.add(
                Single.fromCallable(
                        () -> pagedContentIdSearch(MODE_SEARCH_CONTENT_UNIVERSAL, query, Collections.emptyList(), page, booksPerPage, orderStyle, favouritesOnly)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(contentIdQueryResult -> listener.onPagedResultReady(
                                Helper.getListFromPrimitiveArray(contentIdQueryResult.pagedContentIds), contentIdQueryResult.totalSelectedContent, contentIdQueryResult.totalContent))
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
    public void getAttributeMasterData(List<AttributeType> types, String filter, int sortOrder, ResultListener<List<Attribute>> listener) {
        compositeDisposable.add(
                Single.fromCallable(
                        () -> attributeSearch(MODE_SEARCH_ATTRIBUTE_TEXT, types, filter, Collections.emptyList(), false, sortOrder)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(result -> listener.onResultReady(result.pagedAttributes, result.totalSelectedAttributes))
        );
    }

    @Override
    public void getAttributeMasterDataPaged(List<AttributeType> types, String filter, int page, int booksPerPage, int orderStyle, ResultListener<List<Attribute>> listener) {
        compositeDisposable.add(
                Single.fromCallable(
                        () -> pagedAttributeSearch(MODE_SEARCH_ATTRIBUTE_TEXT, types, filter, Collections.emptyList(), false, orderStyle, page, booksPerPage)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(result -> listener.onResultReady(result.pagedAttributes, result.totalSelectedAttributes))
        );
    }

    @Override
    public boolean supportsAvailabilityFilter() {
        return true;
    }

    @Override
    public boolean supportsAttributesPaging() {
        return true;
    }

    @Override
    public void getAttributeMasterData(List<AttributeType> types, String filter, List<Attribute> attrs, boolean filterFavourites, int sortOrder, ResultListener<List<Attribute>> listener) {
        compositeDisposable.add(
                Single.fromCallable(
                        () -> attributeSearch(MODE_SEARCH_ATTRIBUTE_COMBINED, types, filter, attrs, filterFavourites, sortOrder)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(result -> listener.onResultReady(result.pagedAttributes, result.totalSelectedAttributes))
        );
    }

    @Override
    public void getAttributeMasterDataPaged(List<AttributeType> types, String filter, List<Attribute> attrs, boolean filterFavourites, int page, int booksPerPage, int orderStyle, ResultListener<List<Attribute>> listener) {
        compositeDisposable.add(
                Single.fromCallable(
                        () -> pagedAttributeSearch(MODE_SEARCH_ATTRIBUTE_COMBINED, types, filter, attrs, filterFavourites, orderStyle, page, booksPerPage)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(result -> listener.onResultReady(result.pagedAttributes, result.totalSelectedAttributes))
        );
    }

    @Override
    public void getAvailableAttributes(List<AttributeType> types, List<Attribute> attrs, boolean filterFavourites, ResultListener<List<Attribute>> listener) {
        compositeDisposable.add(
                Single.fromCallable(
                        () -> attributeSearch(MODE_SEARCH_ATTRIBUTE_AVAILABLE, types, "", attrs, filterFavourites, 1)
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

    @Override
    public void dispose() {
        compositeDisposable.clear();
    }

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

    private ContentIdQueryResult pagedContentIdSearch(int mode, String filter, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, boolean favouritesOnly) {

        ContentIdQueryResult result = new ContentIdQueryResult();

        if (MODE_SEARCH_CONTENT_MODULAR == mode) {
            result.pagedContentIds = db.selectContentSearchId(filter, page, booksPerPage, metadata, favouritesOnly, orderStyle);
        } else if (MODE_SEARCH_CONTENT_UNIVERSAL == mode) {
            result.pagedContentIds = db.selectContentUniversalId(filter, page, booksPerPage, favouritesOnly, orderStyle);
        } else {
            result.pagedContentIds = new long[0];
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

    private AttributeQueryResult attributeSearch(int mode, List<AttributeType> attrTypes, String filter, List<Attribute> attrs, boolean filterFavourites, int sortOrder) {
        return pagedAttributeSearch(mode, attrTypes, filter, attrs, filterFavourites, sortOrder, 1, -1);
    }

    private AttributeQueryResult pagedAttributeSearch(int mode, List<AttributeType> attrTypes, String filter, List<Attribute> attrs, boolean filterFavourites, int sortOrder, int pageNum, int itemPerPage) {
        AttributeQueryResult result = new AttributeQueryResult();

        if (attrTypes != null && !attrTypes.isEmpty()) {

            if (MODE_SEARCH_ATTRIBUTE_TEXT == mode) {
                for (AttributeType type : attrTypes) {
                    if (AttributeType.SOURCE == type) // Specific case
                    {
                        result.addAll(db.selectAvailableSources());
                        result.totalSelectedAttributes = result.pagedAttributes.size();
                    } else {
                        result.addAll(db.selectAvailableAttributes(type, attrs, filter, filterFavourites, sortOrder, pageNum, itemPerPage));
                        result.totalSelectedAttributes = db.countAvailableAttributes(type, attrs, filter, filterFavourites);
                    }
                }
            } else if (MODE_SEARCH_ATTRIBUTE_AVAILABLE == mode || MODE_SEARCH_ATTRIBUTE_COMBINED == mode) {
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
