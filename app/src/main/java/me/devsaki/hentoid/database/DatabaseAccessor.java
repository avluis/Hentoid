package me.devsaki.hentoid.database;

import android.content.Context;
import android.os.AsyncTask;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.collection.CollectionAccessor;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Language;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.AttributeListener;
import me.devsaki.hentoid.listener.ContentListener;

public class DatabaseAccessor implements CollectionAccessor {

    private static final String contentSynch = "";
    private static final String attrSynch = "";
    private final HentoidDB db;


    static class ContentQueryResult
    {
        List<Content> pagedContents;
        int totalContent;
        int totalSelectedContent;
    }


    public DatabaseAccessor(Context ctx)
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
    public void getAttributeMasterData(AttributeType attr, String filter, AttributeListener listener) {
        synchronized (attrSynch) {
            List<AttributeType> attrs = new ArrayList<>();
            attrs.add(attr);
            new AttributesFetchTask(db, listener, attrs, filter).execute();
        }
    }

    @Override
    public void getAttributeMasterData(List<AttributeType> attr, String filter, AttributeListener listener) {
        synchronized (attrSynch) {
            new AttributesFetchTask(db, listener, attr, filter).execute();
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

        ContentFetchTask(HentoidDB db, String query, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener) {
            this.db = db;
            this.titleQuery = query;
            this.metadata = metadata;
            this.currentPage = page;
            this.booksPerPage = booksPerPage;
            this.orderStyle = orderStyle;
            this.favouritesOnly = favouritesOnly;
            this.listener = listener;
        }

        @Override
        protected ContentQueryResult doInBackground(String... params) {
            ContentQueryResult result = new ContentQueryResult();

            result.pagedContents = db.selectContentByQuery(titleQuery, currentPage, booksPerPage, metadata, favouritesOnly, orderStyle);
            // Fetch total query count (since query are paged, query results count is always <= booksPerPage)
            result.totalSelectedContent = db.countContentByQuery(titleQuery, metadata, favouritesOnly);
            // Fetch total book count (useful for displaying and comparing the total number of books)
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
        private final AttributeListener listener;
        private final List<AttributeType> attrTypes;
        private final String filter;

        AttributesFetchTask(HentoidDB db, AttributeListener listener, List<AttributeType> attrTypes, String filter) {
            this.db = db;
            this.listener = listener;
            this.attrTypes = attrTypes;
            this.filter = filter;
        }

        @Override
        protected List<Attribute> doInBackground(String... params) {
            List<Attribute> attrs = new ArrayList<>();

            for (AttributeType type : attrTypes) {
                if (AttributeType.SOURCE == type) // Specific case
                {
                    attrs.addAll(db.selectAvailableSources());
                } else {
                    attrs.addAll(db.selectAllAttributesByType(type, filter));
                }
            }

            return attrs;
        }

        @Override
        protected void onPostExecute(List<Attribute> response) {
            if (null == response) {
                listener.onAttributesFailed("Attributes failed to load - Empty response");
                return;
            }
            listener.onAttributesReady(response, response.size());
        }
    }
}
