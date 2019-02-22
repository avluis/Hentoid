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
import me.devsaki.hentoid.listener.ContentListener;
import me.devsaki.hentoid.listener.ResultListener;

public class ObjectBoxCollectionAccessor implements CollectionAccessor {

    private final ObjectBoxDB db;
    private CompositeDisposable compositeDisposable = new CompositeDisposable();

    private final int MODE_SEARCH_CONTENT_MODULAR = 0;
    private final int MODE_COUNT_CONTENT_MODULAR = 1;
    private final int MODE_SEARCH_CONTENT_UNIVERSAL = 2;
    private final int MODE_COUNT_CONTENT_UNIVERSAL = 3;

    private final int MODE_SEARCH_ATTRIBUTE_TEXT = 0;
    private final int MODE_SEARCH_ATTRIBUTE_AVAILABLE = 1;
    private final int MODE_SEARCH_ATTRIBUTE_COMBINED = 2;

    static class ContentQueryResult {
        List<Content> pagedContents;
        long totalContent;
        long totalSelectedContent;
    }


    public ObjectBoxCollectionAccessor(Context ctx) {
        db = ObjectBoxDB.getInstance(ctx);
    }


    @Override
    public void getRecentBooks(Site site, Language language, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener) {
        compositeDisposable.add(
                Single.just(
                        contentSearch(MODE_SEARCH_CONTENT_MODULAR, "", Collections.emptyList(), page, booksPerPage, orderStyle, favouritesOnly)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(contentQueryResult -> {
                            listener.onContentReady(contentQueryResult.pagedContents, contentQueryResult.totalSelectedContent, contentQueryResult.totalContent);
                        })
        );
    }

    @Override
    public void getPages(Content content, ContentListener listener) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void searchBooks(String query, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener) {
        compositeDisposable.add(
                Single.just(
                        contentSearch(MODE_SEARCH_CONTENT_MODULAR, query, metadata, page, booksPerPage, orderStyle, favouritesOnly)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(contentQueryResult -> {
                            listener.onContentReady(contentQueryResult.pagedContents, contentQueryResult.totalSelectedContent, contentQueryResult.totalContent);
                        })
        );
    }

    @Override
    public void countBooks(String query, List<Attribute> metadata, boolean favouritesOnly, ContentListener listener) {
        compositeDisposable.add(
                Single.just(
                        contentSearch(MODE_SEARCH_CONTENT_MODULAR, query, metadata, 1, 1, 1, favouritesOnly)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(contentQueryResult -> {
                            listener.onContentReady(contentQueryResult.pagedContents, contentQueryResult.totalSelectedContent, contentQueryResult.totalContent);
                        })
        );
    }

    @Override
    public void searchBooksUniversal(String query, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener) {
        compositeDisposable.add(
                Single.just(
                        contentSearch(MODE_SEARCH_CONTENT_UNIVERSAL, query, Collections.emptyList(), page, booksPerPage, orderStyle, favouritesOnly)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(contentQueryResult -> {
                            listener.onContentReady(contentQueryResult.pagedContents, contentQueryResult.totalSelectedContent, contentQueryResult.totalContent);
                        })
        );
    }

    @Override
    public void countBooksUniversal(String query, boolean favouritesOnly, ContentListener listener) {
        compositeDisposable.add(
                Single.just(
                        contentSearch(MODE_COUNT_CONTENT_UNIVERSAL, query, Collections.emptyList(), 1, 1, 1, favouritesOnly)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(contentQueryResult -> {
                            listener.onContentReady(contentQueryResult.pagedContents, contentQueryResult.totalSelectedContent, contentQueryResult.totalContent);
                        })
        );
    }

    @Override
    public void getAttributeMasterData(AttributeType type, String filter, ResultListener<List<Attribute>> listener) {
        List<AttributeType> attrTypes = new ArrayList<>();
        attrTypes.add(type);

        compositeDisposable.add(
                Single.just(
                        attributeSearch(MODE_SEARCH_ATTRIBUTE_TEXT, attrTypes, filter, Collections.emptyList(), false)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(result -> {
                            listener.onResultReady(result, result.size());
                        })
        );
    }

    @Override
    public void getAttributeMasterData(List<AttributeType> types, String filter, ResultListener<List<Attribute>> listener) {
        compositeDisposable.add(
                Single.just(
                        attributeSearch(MODE_SEARCH_ATTRIBUTE_TEXT, types, filter, Collections.emptyList(), false)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(result -> {
                            listener.onResultReady(result, result.size());
                        })
        );
    }

    @Override
    public boolean supportsAvailabilityFilter() {
        return true;
    }

    @Override
    public void getAttributeMasterData(List<AttributeType> types, String filter, List<Attribute> attrs, boolean filterFavourites, ResultListener<List<Attribute>> listener) {
        compositeDisposable.add(
                Single.just(
                        attributeSearch(MODE_SEARCH_ATTRIBUTE_COMBINED, types, filter, attrs, filterFavourites)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(result -> {
                            listener.onResultReady(result, result.size());
                        })
        );
    }

    @Override
    public void getAvailableAttributes(List<AttributeType> types, List<Attribute> attrs, boolean filterFavourites, ResultListener<List<Attribute>> listener) {
        compositeDisposable.add(
                Single.just(
                        attributeSearch(MODE_SEARCH_ATTRIBUTE_AVAILABLE, types, "", attrs, filterFavourites)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(result -> {
                            listener.onResultReady(result, result.size());
                        })
        );
    }

    @Override
    public void countAttributesPerType(List<Attribute> filter, ResultListener<SparseIntArray> listener) {
        compositeDisposable.add(
                Single.just(
                        count(filter)
                )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(result -> {
                            listener.onResultReady(result, result.size());
                        })
        );
    }

    @Override
    public void dispose() {
        compositeDisposable.clear();
    }

    private ContentQueryResult contentSearch(int mode, String filter, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, boolean favouritesOnly) {

        ContentQueryResult result = new ContentQueryResult();

        if (MODE_SEARCH_CONTENT_MODULAR == mode) {
            result.pagedContents = db.selectContentByQuery(filter, page, booksPerPage, metadata, favouritesOnly, orderStyle);
        } else if (MODE_SEARCH_CONTENT_UNIVERSAL == mode) {
            result.pagedContents = db.selectContentByUniqueQuery(filter, page, booksPerPage, favouritesOnly, orderStyle);
        } else {
            result.pagedContents = Collections.emptyList();
        }
        // Fetch total query count (i.e. total number of books corresponding to the given filter, in all pages)
        if (MODE_SEARCH_CONTENT_MODULAR == mode || MODE_COUNT_CONTENT_MODULAR == mode) {
            result.totalSelectedContent = db.countContentByQuery(filter, metadata, favouritesOnly);
        } else if (MODE_SEARCH_CONTENT_UNIVERSAL == mode || MODE_COUNT_CONTENT_UNIVERSAL == mode) {
            result.totalSelectedContent = db.countContentByUniqueQuery(filter, favouritesOnly);
        } else {
            result.totalSelectedContent = 0;
        }
        // Fetch total book count (i.e. total number of books in all the collection, regardless of filter)
        result.totalContent = db.countAllContent();

        return result;
    }

    private List<Attribute> attributeSearch(int mode, List<AttributeType> attrTypes, String filter, List<Attribute> attrs, boolean filterFavourites) {
        List<Attribute> result = new ArrayList<>();

        if (attrTypes != null && !attrTypes.isEmpty()) {

            if (MODE_SEARCH_ATTRIBUTE_TEXT == mode) {
                for (AttributeType type : attrTypes) {
                    if (AttributeType.SOURCE == type) // Specific case
                    {
                        result.addAll(db.selectAvailableSources());
                    } else {
                        result.addAll(db.selectAvailableAttributes(type, attrs, filter, filterFavourites));
                    }
                }
            } else if (MODE_SEARCH_ATTRIBUTE_AVAILABLE == mode || MODE_SEARCH_ATTRIBUTE_COMBINED == mode) {
                if (attrTypes.get(0).equals(AttributeType.SOURCE)) {
                    result = db.selectAvailableSources(attrs);
                } else {
                    result = new ArrayList<>();
                    for (AttributeType type : attrTypes)
                        result.addAll(db.selectAvailableAttributes(type, attrs, filter, filterFavourites)); // No favourites button in SearchActivity
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
