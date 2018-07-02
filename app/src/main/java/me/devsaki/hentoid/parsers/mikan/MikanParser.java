package me.devsaki.hentoid.parsers.mikan;

import android.os.AsyncTask;

import com.google.gson.Gson;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Language;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ContentListener;
import me.devsaki.hentoid.util.JsonHelper;
import timber.log.Timber;

public class MikanParser {

    private static final String USAGE_RECENT_BOOKS = "recentBooks";
    private static final String USAGE_BOOK_PAGES = "bookPages";

    public static final int SORT_MOST_RECENT_FIRST = 0;
    public static final int SORT_MOST_POPULAR_FIRST = 1;

    private static final String MIKAN_BASE_URL = "http://initiate.host/";


    private static String getMikanCodeForSite(Site s) {
        switch (s) {
            case HITOMI:
                return "hitomi.la";
            default:
                return null;
        }
    }

    private static boolean isSiteUnsupported(Site s)
    {
        return (s != Site.HITOMI);
    }


    public static void getRecentBooks(Site site, int nbItems, Language language, int page, int sort, ContentListener listener) {
        launchRequest(buildRecentBooksRequest(site, nbItems, language, page, sort), USAGE_RECENT_BOOKS, null, listener);
    }

    public static void getPages(Content content, ContentListener listener) {
        launchRequest(buildBookPagesRequest(content), USAGE_BOOK_PAGES, content, listener);
    }

    private static String buildRecentBooksRequest(Site site, int nbItems, Language language, int page, int sort) {
        if (isSiteUnsupported(site)) {
            throw new UnsupportedOperationException("Site "+site.getDescription()+"not supported yet by Mikan search");
        }

        StringBuilder queryUrl = new StringBuilder(MIKAN_BASE_URL).append(getMikanCodeForSite(site));
        queryUrl.append("?items=").append(nbItems);
        queryUrl.append("&language=").append(language.getCode());
        queryUrl.append("&page=").append(page);
        queryUrl.append("&short=").append(SORT_MOST_POPULAR_FIRST == sort); // sort ??

        return queryUrl.toString();
    }

    private static String buildBookPagesRequest(Content content) {
        if (isSiteUnsupported(content.getSite())) {
            throw new UnsupportedOperationException("Site "+content.getSite().getDescription()+" not supported yet by Mikan search");
        }

        StringBuilder queryUrl = new StringBuilder(MIKAN_BASE_URL).append(getMikanCodeForSite(content.getSite()));
        queryUrl.append("/").append(content.getUniqueSiteId());
        queryUrl.append("/pages");

        return queryUrl.toString();
    }

    private static synchronized void launchRequest(String url, String usage, Content content, ContentListener listener) {
        new SearchTask(listener, content, usage).execute(url);
    }

    private static class SearchTask extends AsyncTask<String, String, JSONObject> {

        private final ContentListener listener;
        private final String usage;
        private final Content content;

        SearchTask(ContentListener listener, Content content, String usage) {
            this.listener = listener;
            this.usage = usage;
            this.content = content;
        }

        @Override
        protected JSONObject doInBackground(String... params) {
            JSONObject json = null;
            try {
                json = JsonHelper.jsonReader(params[0]);
            } catch (IOException e)  {
                Timber.w("JSON retrieval failed at URL %s", params[0]);
            }

            if (null == json)
            {
                Timber.w("No content available for URL %s", params[0]);
                listener.onContentFailed(true);
            }

            return json;
        }

        @Override
        protected void onPostExecute(JSONObject json) {
            MikanResponse response;
            switch (usage)
            {
                case MikanParser.USAGE_RECENT_BOOKS:
                    response = new Gson().fromJson(json.toString(), MikanResponse.class);
                    int maxItems = response.maxpage * response.result.size(); // Roughly : number of pages * number of books per page
                    listener.onContentReady(true, response.toContentList(), maxItems);
                    break;
                case MikanParser.USAGE_BOOK_PAGES:
                    response = new Gson().fromJson(json.toString(), MikanResponse.class);

                    if (this.content != null) {
                        List<Content> list = new ArrayList<>();
                        list.add(this.content);
                        this.content.setImageFiles(response.toImageFileList());
                        this.content.setQtyPages(response.pages.size());

                        listener.onContentReady(true, list, 1);
                    } else {
                        listener.onContentFailed(true);
                    }
                    break;
            }
        }
    }
}
