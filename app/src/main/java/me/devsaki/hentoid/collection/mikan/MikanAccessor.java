package me.devsaki.hentoid.collection.mikan;

import android.content.Context;
import android.os.AsyncTask;

import com.google.gson.Gson;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.collection.BaseCollectionAccessor;
import me.devsaki.hentoid.collection.LibraryMatcher;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Language;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.AttributeListener;
import me.devsaki.hentoid.listener.ContentListener;
import me.devsaki.hentoid.util.AttributeCache;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.IllegalTags;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.UrlBuilder;
import timber.log.Timber;

public class MikanAccessor extends BaseCollectionAccessor {

    private static final String MIKAN_BASE_URL = "https://api.initiate.host/v1/";
    private static final Object contentSynch = new Object();
    private static final Object attrSynch = new Object();

    private final LibraryMatcher libraryMatcher;

    // == CONSTRUCTOR

    public MikanAccessor(Context context)
    {
        libraryMatcher = new LibraryMatcher(context);
    }


    // == UTILS

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

    private static void filterIllegalTags(List<Attribute> list)
    {
        int size = list.size();
        int i = 0;

        while (i < size)
        {
            if (IllegalTags.isIllegal(list.get(i).getName()))
            {
                list.remove(i);
                i--;
                size--;
            }
            i++;
        }
    }


    // === ACCESSORS

    public void getRecentBooks(Site site, Language language, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener) {
        launchRequest(buildRecentBooksRequest(site, language, page, Preferences.Constant.PREF_ORDER_CONTENT_LAST_UL_DATE_FIRST == orderStyle), USAGE_RECENT_BOOKS, null, listener, libraryMatcher);
    }

    public void getPages(Content content, ContentListener listener) {
        launchRequest(buildBookPagesRequest(content), USAGE_BOOK_PAGES, content, listener, libraryMatcher);
    }

    public void searchBooks(String query, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener) {
        // NB : Mikan does not support booksPerPage and orderStyle params
        launchRequest(buildSearchRequest(metadata, query, page), USAGE_SEARCH, null, listener, libraryMatcher);
    }

    public void getAttributeMasterData(AttributeType attr, String filter, AttributeListener listener) {
        launchRequest(buildGetAttrRequest(attr), attr.toString(), filter, listener);
    }


    // === REQUEST BUILDERS

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

    private static String buildSearchRequest(List<Attribute> metadata, String query, int page) {

        List<Attribute> sites = Helper.extractAttributeByType(metadata, AttributeType.SOURCE);

        if (sites.size() > 1) {
            throw new UnsupportedOperationException("Searching through multiple sites not supported yet by Mikan search");
        }
        Site site = (1 == sites.size()) ? Site.searchByCode(sites.get(0).getId()) : Site.HITOMI;
        if (null == site) {
            throw new UnsupportedOperationException("Unrecognized site ID " + sites.get(0).getId());
        }
        if (isSiteUnsupported(site)) {
            throw new UnsupportedOperationException("Site "+site.getDescription()+" not supported yet by Mikan search");
        }

        String titleQuery = (query != null && query.length() > 0)? "/search/" + query : "";
        UrlBuilder url = new UrlBuilder(MIKAN_BASE_URL + getMikanCodeForSite(site) + titleQuery);

        url.addParam("page",page);

        List<Attribute> params = Helper.extractAttributeByType(metadata, AttributeType.ARTIST);
        if (params.size() > 0) url.addParam("artist", Helper.buildListAsString(params));

        params = Helper.extractAttributeByType(metadata, AttributeType.CIRCLE);
        if (params.size() > 0) url.addParam("group", Helper.buildListAsString(params));

        params = Helper.extractAttributeByType(metadata, AttributeType.CHARACTER);
        if (params.size() > 0) url.addParam("character", Helper.buildListAsString(params));

        params = Helper.extractAttributeByType(metadata, AttributeType.TAG);
        if (params.size() > 0) url.addParam("tag", Helper.buildListAsString(params));

        params = Helper.extractAttributeByType(metadata, AttributeType.LANGUAGE);
        if (params.size() > 0) url.addParam("language", Helper.buildListAsString(params));

        return url.toString();
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
            case SERIE:result+="series"; break;
        }

        return result;
    }


    // === REQUEST LAUNCHERS

    private static void launchRequest(String url, String usage, Content content, ContentListener listener, LibraryMatcher matcher) {
        synchronized (contentSynch) {
            new ContentFetchTask(listener, content, usage, matcher).execute(url);
        }
    }

    private static void launchRequest(String url, String usage, String filter, AttributeListener listener) {
        synchronized (attrSynch) {
            new AttributesFetchTask(listener, usage, filter).execute(url);
        }
    }


    // === ASYNC TASKS

    private static class ContentFetchTask extends AsyncTask<String, String, MikanContentResponse> {

        private final ContentListener listener;
        private final String usage;
        private final Content content;
        private final LibraryMatcher matcher;

        ContentFetchTask(ContentListener listener, Content content, String usage, LibraryMatcher matcher) {
            this.listener = listener;
            this.usage = usage;
            this.content = content;
            this.matcher = matcher;
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
                Timber.w(e, "JSON retrieval failed at URL %s", url);
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
                case USAGE_RECENT_BOOKS:
                case USAGE_SEARCH:
                    int maxItems = response.maxpage * response.result.size(); // Roughly : number of pages * number of books per page
                    listener.onContentReady(response.toContentList(matcher), maxItems);
                    break;
                case USAGE_BOOK_PAGES:
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
        private final String filter;

        AttributesFetchTask(AttributeListener listener, String usage, String filter) {
            this.listener = listener;
            this.usage = usage;
            this.filter = filter;
        }

        @Override
        protected List<Attribute> doInBackground(String... params) {

            // Try and get response from cache
            List<Attribute> attributes = AttributeCache.getFromCache(usage);

            // If not cached (or cache expired), get it from network
            if (null == attributes) {

                JSONObject json = null;
                JsonHelper.JSONResponse response;
                String url = params[0];
                Timber.d("Querying Mikan at URL %s", url);
                try {
                    response = JsonHelper.jsonReader(url);
                    if (response != null) json = response.object;
                } catch (IOException e) {
                    Timber.w(e, "JSON retrieval failed at URL %s", url);
                    return null;
                }

                if (null == json) {
                    Timber.w("No content available for URL %s", url);
                    return null;
                }

                // Deserialize response
                MikanAttributeResponse attrResponse = new Gson().fromJson(json.toString(), MikanAttributeResponse.class);
                attributes = attrResponse.toAttributeList();

                // Filter illegal tags
                if (AttributeType.TAG.toString().equals(usage))
                {
                    filterIllegalTags(attributes);
                }

                // Cache results
                AttributeCache.setCache(usage, attributes, response.expiryDate);

                Timber.d("Mikan response [%s] : %s", attrResponse.request, json.toString());
            }

            List<Attribute> result = attributes;
            if (filter != null)
            {
                result = new ArrayList<>();
                for (Attribute a : attributes) if (a.getName().contains(filter)) result.add(a);
            }

            return result;
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
