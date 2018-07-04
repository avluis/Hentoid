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
import me.devsaki.hentoid.util.UrlBuilder;
import timber.log.Timber;

public class MikanParser {

    private static final String USAGE_RECENT_BOOKS = "recentBooks";
    private static final String USAGE_BOOK_PAGES = "bookPages";
    private static final String USAGE_SEARCH = "search";

    private static final String MIKAN_BASE_URL = "https://api.initiate.host/v1/";


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


    public static void getRecentBooks(Site site, Language language, int page, boolean showMostRecentFirst, ContentListener listener) {
        launchRequest(buildRecentBooksRequest(site, language, page, showMostRecentFirst), USAGE_RECENT_BOOKS, null, listener);
    }

    public static void getPages(Content content, ContentListener listener) {
        launchRequest(buildBookPagesRequest(content), USAGE_BOOK_PAGES, content, listener);
    }

    public static void searchBooks(Site site, String query, ContentListener listener) {
        launchRequest(buildSimpleSearchRequest(site, query), USAGE_SEARCH, null, listener);
    }

    private static String buildRecentBooksRequest(Site site, Language language, int page, boolean showMostRecentFirst) {
        if (isSiteUnsupported(site)) {
            throw new UnsupportedOperationException("Site "+site.getDescription()+"not supported yet by Mikan search");
        }

        UrlBuilder url = new UrlBuilder(MIKAN_BASE_URL+getMikanCodeForSite(site));
        if (Language.ANY != language) url.addParam("language",language.getCode());
        url.addParam("page",page);
        url.addParam("sort",showMostRecentFirst);

        return url.toString();
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

    private static String buildSimpleSearchRequest(Site site, String query) {
        if (isSiteUnsupported(site)) {
            throw new UnsupportedOperationException("Site "+site.getDescription()+" not supported yet by Mikan search");
        }

        StringBuilder queryUrl = new StringBuilder(MIKAN_BASE_URL).append(getMikanCodeForSite(site));
        queryUrl.append("/search/").append(query);

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
            String url = params[0];
            Timber.d("Querying Mikan at URL %s", url);
            try {
                json = JsonHelper.jsonReader(url);
            } catch (IOException e)  {
                Timber.w("JSON retrieval failed at URL %s", url);
            }

            if (null == json)
            {
                Timber.w("No content available for URL %s", url);
                listener.onContentFailed(true);
                return null;
            }

            return json;
        }

        @Override
        protected void onPostExecute(JSONObject json) {
            if (null == json) return;
            MikanResponse response = new Gson().fromJson(json.toString(), MikanResponse.class);
            switch (usage)
            {
                case MikanParser.USAGE_RECENT_BOOKS:
                case MikanParser.USAGE_SEARCH:
                    int maxItems = response.maxpage * response.result.size(); // Roughly : number of pages * number of books per page
                    listener.onContentReady(true, response.toContentList(), maxItems);
                    break;
                case MikanParser.USAGE_BOOK_PAGES:
                    if (null == content) listener.onContentFailed(true);
                    else {
                        List<Content> list = new ArrayList<Content>() {{ add(content); }};
                        content.setImageFiles(response.toImageFileList()).setQtyPages(response.pages.size());
                        listener.onContentReady(true, list, 1);
                    }
                    break;
            }
            Timber.d("Mikan response [%s] : %s", response.request, json.toString());
        }
    }
}
