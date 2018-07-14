package me.devsaki.hentoid.database;

import android.os.AsyncTask;

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

    @Override
    public void getRecentBooks(Site site, Language language, int page, boolean showMostRecentFirst, ContentListener listener) {

    }

    @Override
    public void getPages(Content content, ContentListener listener) {

    }

    @Override
    public void searchBooks(String query, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, ContentListener listener) {
        new ContentFetchTask(query, metadata, page, booksPerPage, orderStyle, listener, USAGE_SEARCH, null).execute();
    }

    @Override
    public void getAttributeMasterData(AttributeType attr, String filter, AttributeListener listener) {

    }

    // === ASYNC TASKS

    private static class ContentFetchTask extends AsyncTask<String, String, List<Content>> {

        private final ContentListener listener;
        private final String titleQuery;
        private final List<Attribute> metadata;
        private final int page;
        private final int booksPerPage;
        private final int orderStyle;
        private final String usage;
        private final Content content;

        ContentFetchTask(String query, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, ContentListener listener, String usage, Content content) {
            this.titleQuery = query;
            this.metadata = metadata;
            this.page = page;
            this.booksPerPage = booksPerPage;
            this.orderStyle = orderStyle;
            this.listener = listener;
            this.usage = usage;
            this.content = content;
        }

        @Override
        protected List<Content> doInBackground(String... params) {
            // TODO
            return null;
        }

        @Override
        protected void onPostExecute(List<Content> response) {
            if (null == response) {
                Timber.w("Empty response");
                listener.onContentFailed();
                return;
            }
            listener.onContentReady(response, response.size());
        }
    }

    private static class AttributesFetchTask extends AsyncTask<String, String, List<Attribute>> {

        private final AttributeListener listener;
        private final String usage;
        private final String filter;

        AttributesFetchTask(AttributeListener listener, String usage, String filter) {
            this.listener = listener;
            this.usage = usage;
            this.filter = filter;
        }

        @Override
        protected List<Attribute> doInBackground(String... params) {

            // TODO
/*
            List<Attribute> result = attributes;
            if (filter != null)
            {
                result = new ArrayList<>();
                for (Attribute a : attributes) if (a.getName().contains(filter)) result.add(a);
                attributes.clear();
            }

            return result;
*/

            return null;
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
