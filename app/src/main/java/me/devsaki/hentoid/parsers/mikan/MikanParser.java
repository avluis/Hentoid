package me.devsaki.hentoid.parsers.mikan;

import android.os.AsyncTask;

import com.google.gson.Gson;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Language;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.AttributeListener;
import me.devsaki.hentoid.listener.ContentListener;
import me.devsaki.hentoid.util.AttributeCache;
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

    public static void getAttributeMasterData(AttributeType attr, AttributeListener listener) {
        launchRequest(buildGetAttrRequest(attr), attr.name(), listener);
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

    private static String buildGetAttrRequest(AttributeType attr) {
        String result = MIKAN_BASE_URL + getMikanCodeForSite(Site.HITOMI) + // Forced HITOMI until the endpoint moves to root URL
                "/info/";

        switch(attr) {
            case ARTIST:result+="artists"; break;
            case CHARACTER:result+="characters"; break;
            case TAG:result+="tags"; break;
            case LANGUAGE:result+="languages"; break;
            case CIRCLE:result+="groups"; break;
        }

        return result;
    }

    private static synchronized void launchRequest(String url, String usage, Content content, ContentListener listener) {
        new ContentFetchTask(listener, content, usage).execute(url);
    }

    private static synchronized void launchRequest(String url, String usage, AttributeListener listener) {
        new AttributesFetchTask(listener, usage).execute(url);
    }


    private static class ContentFetchTask extends AsyncTask<String, String, MikanContentResponse> {

        private final ContentListener listener;
        private final String usage;
        private final Content content;

        ContentFetchTask(ContentListener listener, Content content, String usage) {
            this.listener = listener;
            this.usage = usage;
            this.content = content;
        }

        @Override
        protected MikanContentResponse doInBackground(String... params) {
            JSONObject json = null;
            String url = params[0];
            Timber.d("Querying Mikan at URL %s", url);
            try {
                JsonHelper.JSONResponse response = JsonHelper.jsonReader(url);
                if (response != null) json = response.object;
            } catch (IOException e)  {
                Timber.w("JSON retrieval failed at URL %s", url);
                return null;
            }

            if (null == json)
            {
                Timber.w("No content available for URL %s", url);
                return null;
            }

            MikanContentResponse response = new Gson().fromJson(json.toString(), MikanContentResponse.class);

            Timber.d("Mikan response [%s] : %s", response.request, json.toString());

            return response;
        }

        @Override
        protected void onPostExecute(MikanContentResponse response) {
            if (null == response) {
                Timber.w("Empty response");
                listener.onContentFailed();
                return;
            }

            switch (usage)
            {
                case MikanParser.USAGE_RECENT_BOOKS:
                case MikanParser.USAGE_SEARCH:
                    int maxItems = response.maxpage * response.result.size(); // Roughly : number of pages * number of books per page
                    listener.onContentReady(response.toContentList(), maxItems);
                    break;
                case MikanParser.USAGE_BOOK_PAGES:
                    if (null == content) listener.onContentFailed();
                    else {
                        List<Content> list = new ArrayList<Content>() {{ add(content); }};
                        content.setImageFiles(response.toImageFileList()).setQtyPages(response.pages.size());
                        listener.onContentReady(list, 1);
                    }
                    break;
            }
        }
    }

    private static class AttributesFetchTask extends AsyncTask<String, String, List<Attribute>> {

        private final AttributeListener listener;
        private final String usage;

        AttributesFetchTask(AttributeListener listener, String usage) {
            this.listener = listener;
            this.usage = usage;
        }

        @Override
        protected List<Attribute> doInBackground(String... params) {

            // Try and get response from cache
            List<Attribute> cachedAttrs = AttributeCache.getFromCache(usage);
            if (cachedAttrs != null) return cachedAttrs;

            // If not cached (or cache expired), get it from network
            JSONObject json = null;
            JsonHelper.JSONResponse response;
            String url = params[0];
            Timber.d("Querying Mikan at URL %s", url);
            try {
                response = JsonHelper.jsonReader(url);
                if (response != null) json = response.object;
            } catch (IOException e)  {
                Timber.w("JSON retrieval failed at URL %s", url);
                return null;
            }

            if (null == json)
            {
                Timber.w("No content available for URL %s", url);
                return null;
            }

            MikanAttributeResponse attrResponse = new Gson().fromJson(json.toString(), MikanAttributeResponse.class);
            List<Attribute> attributes = attrResponse.toAttributeList();

            AttributeCache.setCache(usage, attributes, response.expiryDate);

            Timber.d("Mikan response [%s] : %s", attrResponse.request, json.toString());

            return attributes;
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
