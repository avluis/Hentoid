package me.devsaki.hentoid.database;

import android.content.Context;
import android.os.AsyncTask;
import android.util.SparseIntArray;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.collection.CollectionAccessor;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Language;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ContentListener;
import me.devsaki.hentoid.listener.ResultListener;

public class DatabaseCollectionAccessor implements CollectionAccessor {

    private static final String contentSynch = "";
    private static final String attrSynch = "";
    private final HentoidDB db;


    static class ContentQueryResult
    {
        List<Content> pagedContents;
        int totalContent;
        int totalSelectedContent;
    }


    public DatabaseCollectionAccessor(Context ctx)
    {
        db = HentoidDB.getInstance(ctx);
    }

    @Override
    public void getRecentBooks(Site site, Language language, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener) {
        synchronized (contentSynch) {
            new ContentFetchTask(db, "", new ArrayList<>(), page, booksPerPage, orderStyle, favouritesOnly, listener).execute();
        }
    }

    @Override
    public void getPages(Content content, ContentListener listener) {
        // Not implemented
    }

    @Override
    public void searchBooks(String query, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener) {
        synchronized (contentSynch) {
            new ContentFetchTask(db, query, metadata, page, booksPerPage, orderStyle, favouritesOnly, listener).execute();
        }
    }

    @Override
    public void countBooks(String query, List<Attribute> metadata, boolean favouritesOnly, ContentListener listener) {
        synchronized (contentSynch) {
            new ContentFetchTask(db, query, metadata, favouritesOnly, listener).execute();
        }
    }

    @Override
    public void searchBooksUniversal(String query, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener) {
        synchronized (contentSynch) {
            new ContentFetchTask(db, query, page, booksPerPage, orderStyle, favouritesOnly, listener).execute();
        }
    }

    @Override
    public void countBooksUniversal(String query, boolean favouritesOnly, ContentListener listener) {
        synchronized (contentSynch) {
            new ContentFetchTask(db, query, favouritesOnly, listener).execute();
        }
    }

    @Override
    public void getAttributeMasterData(AttributeType type, String filter, ResultListener<List<Attribute>> listener) {
        synchronized (attrSynch) {
            List<AttributeType> attrs = new ArrayList<>();
            attrs.add(type);
            new AttributesFetchTask(db, listener, attrs, filter).execute();
        }
    }

    @Override
    public void getAttributeMasterData(List<AttributeType> types, String filter, ResultListener<List<Attribute>> listener) {
        synchronized (attrSynch) {
            new AttributesFetchTask(db, listener, types, filter).execute();
        }
    }

    @Override
    public boolean supportsAvailabilityFilter() {
        return true;
    }

    @Override
    public void getAttributeMasterData(List<AttributeType> types, String filter, List<Attribute> attrs, boolean filterFavourites, ResultListener<List<Attribute>> listener) {
        synchronized (attrSynch) {
            new AttributesFetchTask(db, listener, types, filter, attrs, filterFavourites).execute();
        }
    }

    @Override
    public void countAttributesPerType(List<Attribute> filter, ResultListener<SparseIntArray> listener) {
        synchronized (attrSynch) {
            new CountFetchTask(db, listener, filter).execute();
        }
    }

    @Override
    public void getAvailableAttributes(List<AttributeType> types, List<Attribute> attrs, boolean filterFavourites, ResultListener<List<Attribute>> listener) {
        synchronized (attrSynch) {
            new AttributesFetchTask(db, listener, types, attrs, filterFavourites).execute();
        }
    }


    @Override
    public void dispose() {
        // Nothing special
    }

    // === ASYNC TASKS

    private static class ContentFetchTask extends AsyncTask<String, String, ContentQueryResult> {

        private final HentoidDB db;
        private final ContentListener listener;
        private final String titleQuery;
        private final List<Attribute> metadata;
        private final int currentPage;
        private final int booksPerPage;
        private final int orderStyle;
        private final boolean favouritesOnly;

        private final int mode;

        private final int MODE_SEARCH_MODULAR = 0;
        private final int MODE_COUNT_MODULAR = 1;
        private final int MODE_SEARCH_UNIVERSAL = 2;
        private final int MODE_COUNT_UNIVERSAL = 3;


        ContentFetchTask(HentoidDB db, String query, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener) {
            this.db = db;
            this.titleQuery = query;
            this.metadata = null;
            this.currentPage = page;
            this.booksPerPage = booksPerPage;
            this.orderStyle = orderStyle;
            this.favouritesOnly = favouritesOnly;
            this.listener = listener;
            mode = MODE_SEARCH_UNIVERSAL;
        }

        ContentFetchTask(HentoidDB db, String query, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener) {
            this.db = db;
            this.titleQuery = query;
            this.metadata = metadata;
            this.currentPage = page;
            this.booksPerPage = booksPerPage;
            this.orderStyle = orderStyle;
            this.favouritesOnly = favouritesOnly;
            this.listener = listener;
            mode = MODE_SEARCH_MODULAR;
        }

        ContentFetchTask(HentoidDB db, String query, boolean favouritesOnly, ContentListener listener) {
            this.db = db;
            this.titleQuery = query;
            this.metadata = null;
            this.currentPage = 1;
            this.booksPerPage = 1;
            this.orderStyle = 1;
            this.favouritesOnly = favouritesOnly;
            this.listener = listener;
            mode = MODE_COUNT_UNIVERSAL;
        }

        ContentFetchTask(HentoidDB db, String query, List<Attribute> metadata, boolean favouritesOnly, ContentListener listener) {
            this.db = db;
            this.titleQuery = query;
            this.metadata = metadata;
            this.currentPage = 1;
            this.booksPerPage = 1;
            this.orderStyle = 1;
            this.favouritesOnly = favouritesOnly;
            this.listener = listener;
            mode = MODE_COUNT_MODULAR;
        }

        @Override
        protected ContentQueryResult doInBackground(String... params) {
            ContentQueryResult result = new ContentQueryResult();

            // Fetch the given page of results (query results count is always <= booksPerPage)
            if (MODE_SEARCH_MODULAR == mode) {
                result.pagedContents = db.selectContentByQuery(titleQuery, currentPage, booksPerPage, metadata, favouritesOnly, orderStyle);
            } else if (MODE_SEARCH_UNIVERSAL == mode) {
                result.pagedContents = db.selectContentByUniqueQuery(titleQuery, currentPage, booksPerPage, favouritesOnly, orderStyle);
            }
            // Fetch total query count (i.e. total number of books corresponding to the given filter, in all pages)
            if (MODE_SEARCH_MODULAR == mode || MODE_COUNT_MODULAR == mode) {
                result.totalSelectedContent = db.countContentByQuery(titleQuery, metadata, favouritesOnly);
            } else if (MODE_SEARCH_UNIVERSAL == mode || MODE_COUNT_UNIVERSAL == mode) {
                result.totalSelectedContent = db.countContentByUniqueQuery(titleQuery, favouritesOnly);
            }
            // Fetch total book count (i.e. total number of books in all the collection, regardless of filter)
            result.totalContent = db.countAllContent();

            return result;
        }

        @Override
        protected void onPostExecute(ContentQueryResult response) {
            if (null == response) {
                listener.onContentFailed(null, "Content failed to load - Empty response");
                return;
            }
            listener.onContentReady(response.pagedContents, response.totalSelectedContent, response.totalContent);
        }
    }

    private static class AttributesFetchTask extends AsyncTask<String, String, List<Attribute>> {

        private final HentoidDB db;
        private final ResultListener<List<Attribute>> listener;
        private final List<AttributeType> attrTypes;
        private final List<Attribute> attrs;
        private final String filter;
        private final boolean filterFavourites;

        private final int mode;

        private final int MODE_SEARCH_TEXT = 0;
        private final int MODE_SEARCH_AVAILABLE = 1;
        private final int MODE_SEARCH_COMBINED = 2;


        AttributesFetchTask(HentoidDB db, ResultListener<List<Attribute>> listener, List<AttributeType> attrTypes, String filter) {
            this.db = db;
            this.listener = listener;
            this.attrTypes = attrTypes;
            this.attrs = null;
            this.filter = filter;
            this.filterFavourites = false;
            mode = MODE_SEARCH_TEXT;
        }

        AttributesFetchTask(HentoidDB db, ResultListener<List<Attribute>> listener, List<AttributeType> attrTypes, List<Attribute> attrs, boolean filterFavourites) {
            this.db = db;
            this.listener = listener;
            this.attrTypes = attrTypes;
            this.attrs = attrs;
            this.filter = null;
            this.filterFavourites = filterFavourites;
            mode = MODE_SEARCH_AVAILABLE;
        }

        AttributesFetchTask(HentoidDB db, ResultListener<List<Attribute>> listener, List<AttributeType> attrTypes, String filter, List<Attribute> attrs, boolean filterFavourites) {
            this.db = db;
            this.listener = listener;
            this.attrTypes = attrTypes;
            this.attrs = attrs;
            this.filter = filter;
            this.filterFavourites = filterFavourites;
            mode = MODE_SEARCH_COMBINED;
        }

        @Override
        protected List<Attribute> doInBackground(String... params) {
            List<Attribute> result = new ArrayList<>();

            if (null == attrTypes || attrTypes.isEmpty()) return null;

            if (MODE_SEARCH_TEXT == mode) {
                for (AttributeType type : attrTypes) {
                    if (AttributeType.SOURCE == type) // Specific case
                    {
                        result.addAll(db.selectAvailableSources());
                    } else {
                        result.addAll(db.selectAllAttributesByType(type, filter));
                    }
                }
            } else if (MODE_SEARCH_AVAILABLE == mode || MODE_SEARCH_COMBINED == mode) {
                if (attrTypes.get(0).equals(AttributeType.SOURCE)) {
                    result = db.selectAvailableSources(attrs);
                } else {
                    result = new ArrayList<>();
                    for (AttributeType type : attrTypes)
                        result.addAll(db.selectAvailableAttributes(type, attrs, filter, filterFavourites)); // No favourites button in SearchActivity
                }
            }

            return result;
        }

        @Override
        protected void onPostExecute(List<Attribute> response) {
            if (null == response) {
                listener.onResultFailed("Attributes failed to load - Empty response");
                return;
            }
            listener.onResultReady(response, response.size());
        }
    }

    private static class CountFetchTask extends AsyncTask<String, String, SparseIntArray> {

        private final HentoidDB db;
        private final ResultListener<SparseIntArray> listener;
        private final List<Attribute> filter;

        CountFetchTask(HentoidDB db, ResultListener<SparseIntArray> listener, List<Attribute> filter) {
            this.db = db;
            this.listener = listener;
            this.filter = filter;
        }

        @Override
        protected SparseIntArray doInBackground(String... params) {
            SparseIntArray result;

            if (null == filter || filter.isEmpty()) {
                result = db.countAttributesPerType();
                result.put(AttributeType.SOURCE.getCode(), db.selectAvailableSources().size());
            } else {
                result = db.countAttributesPerType(filter);
                result.put(AttributeType.SOURCE.getCode(), db.selectAvailableSources(filter).size());
            }

            return result;
        }

        @Override
        protected void onPostExecute(SparseIntArray response) {
            if (null == response) {
                listener.onResultFailed("Result failed to load - Empty response");
                return;
            }
            listener.onResultReady(response, response.size());
        }
    }
}
