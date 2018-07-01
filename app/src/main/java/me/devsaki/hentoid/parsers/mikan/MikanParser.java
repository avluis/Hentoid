package me.devsaki.hentoid.parsers.mikan;

import android.os.AsyncTask;

import com.google.gson.Gson;

import org.json.JSONObject;

import java.io.IOException;

import me.devsaki.hentoid.enums.Language;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ContentListener;
import me.devsaki.hentoid.util.JsonHelper;
import timber.log.Timber;

public class MikanParser {

    private static final int USAGE_RECENT_BOOKS = 0;

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


    public static void getRecentBooks(Site site, int nbItems, Language language, int page, int sort, ContentListener listener) {
        AsyncTask.execute(() -> {
            try {
                launchRequest(buildRecentBooksRequest(site, nbItems, language, page, sort), USAGE_RECENT_BOOKS, listener);
            } catch (IOException e) {
                listener.onContentFailed(true);
            }
        });
    }

    private static void launchRequest(String url, int usage, ContentListener listener) throws IOException {
        JSONObject json = JsonHelper.jsonReader(url);

        if (null == json)
        {
            Timber.w("No content available for url %s", url);
            listener.onContentFailed(true);
            return;
        }

        switch (usage)
        {
            case USAGE_RECENT_BOOKS:
                MikanResponse response = new Gson().fromJson(json.toString(), MikanResponse.class);
                int maxItems = response.maxpage * response.result.size(); // Roughly : number of pages * number of books per page
                listener.onContentReady(true, response.toContentList(), maxItems);
                break;
        }
    }

    private static String buildRecentBooksRequest(Site site, int nbItems, Language language, int page, int sort) {
        if (site != Site.HITOMI) {
            throw new UnsupportedOperationException("Site not supported yet by Mikan search");
        }

        StringBuilder queryUrl = new StringBuilder(MIKAN_BASE_URL).append(getMikanCodeForSite(site));
        queryUrl.append("?items=").append(nbItems);
        queryUrl.append("&language=").append(language.getCode());
        queryUrl.append("&page=").append(page);
        queryUrl.append("&short=").append(SORT_MOST_POPULAR_FIRST == sort); // sort ??

        return queryUrl.toString();
    }
}
