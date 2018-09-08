package me.devsaki.hentoid.database;

import android.content.Context;
import android.os.AsyncTask;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.collection.BaseCollectionAccessor;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Language;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.AttributeListener;
import me.devsaki.hentoid.listener.ContentListener;
import timber.log.Timber;

public class DatabaseAccessor extends BaseCollectionAccessor {

    private static final String contentSynch = "";
    private static final String attrSynch = "";
    private final HentoidDB db;


    static class ContentQueryResult
    {
        public List<Content> pagedContents;
        public int totalContent;
        public int totalSelectedContent;
    }


    public DatabaseAccessor(Context ctx)
    {
        db = HentoidDB.getInstance(ctx);
    }

    @Override
    public void getRecentBooks(Site site, Language language, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener) {
        synchronized (contentSynch) {
            new ContentFetchTask(db, "", new ArrayList<>(), page, booksPerPage, orderStyle, favouritesOnly, listener, USAGE_SEARCH, null).execute();
        }
    }

    @Override
    public void getPages(Content content, ContentListener listener) {
        // Not implemented
    }

    @Override
    public void searchBooks(String query, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener) {
        synchronized (contentSynch) {
            new ContentFetchTask(db, query, metadata, page, booksPerPage, orderStyle, favouritesOnly, listener, USAGE_SEARCH, null).execute();
        }
    }

    @Override
    public void getAttributeMasterData(AttributeType attr, String filter, AttributeListener listener) {
        synchronized (attrSynch) {
            new AttributesFetchTask(db, listener, attr, filter).execute();
        }
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
        private final String usage;
        private final Content content;

        ContentFetchTask(HentoidDB db, String query, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener, String usage, Content content) {
            this.db = db;
            this.titleQuery = query;
            this.metadata = metadata;
            this.currentPage = page;
            this.booksPerPage = booksPerPage;
            this.orderStyle = orderStyle;
            this.favouritesOnly = favouritesOnly;
            this.listener = listener;
            this.usage = usage;
            this.content = content;
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
                Timber.w("Empty response");
                listener.onContentFailed();
                return;
            }
            listener.onContentReady(response.pagedContents, response.totalSelectedContent, response.totalContent);
        }
    }

    private static class AttributesFetchTask extends AsyncTask<String, String, List<Attribute>> {

        private final HentoidDB db;
        private final AttributeListener listener;
        private final AttributeType attrType;
        private final String filter;

        AttributesFetchTask(HentoidDB db, AttributeListener listener, AttributeType attrType, String filter) {
            this.db = db;
            this.listener = listener;
            this.attrType = attrType;
            this.filter = filter;
        }

        @Override
        protected List<Attribute> doInBackground(String... params) {
            if (AttributeType.SOURCE == attrType) // Specific case
            {
                return db.selectAvailableSources();
            } else {
                return db.selectAllAttributesByType(attrType, filter);
            }
        }

        @Override
        protected void onPostExecute(List<Attribute> response) {
            if (null == response) {
                Timber.w("Empty response");
                listener.onAttributesFailed();
                return;
            }
            listener.onAttributesReady(response, response.size());
        }
    }
}
