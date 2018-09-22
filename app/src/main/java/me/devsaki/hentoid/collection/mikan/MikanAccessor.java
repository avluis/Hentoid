package me.devsaki.hentoid.collection.mikan;

import android.content.Context;

import com.annimon.stream.Stream;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.reactivex.disposables.Disposable;
import me.devsaki.hentoid.collection.BaseCollectionAccessor;
import me.devsaki.hentoid.collection.LibraryMatcher;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Language;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.AttributeListener;
import me.devsaki.hentoid.listener.ContentListener;
import me.devsaki.hentoid.retrofit.MikanServer;
import me.devsaki.hentoid.util.AttributeCache;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.IllegalTags;
import me.devsaki.hentoid.util.Preferences;
import retrofit2.Response;
import timber.log.Timber;

import static com.annimon.stream.Collectors.toList;
import static io.reactivex.android.schedulers.AndroidSchedulers.mainThread;

public class MikanAccessor extends BaseCollectionAccessor {

    private final LibraryMatcher libraryMatcher;
    private Disposable disposable;

    // == CONSTRUCTOR

    public MikanAccessor(Context context) {
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

    private static boolean isSiteUnsupported(Site s) {
        return (s != Site.HITOMI);
    }

    private static List<Attribute> filter(List<Attribute> attributes, String filter) {
        if (filter == null) {
            return attributes;
        } else {
            return Stream.of(attributes)
                    .filter(value -> value.getName().contains(filter))
                    .collect(toList());
        }
    }

    private static Date extractExpiry(Response response) {
        String expiryDateStr = response.headers().get("x-expire");
        if (expiryDateStr != null) {
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            try {
                return dateFormat.parse(expiryDateStr);
            } catch (ParseException e) {
                Timber.i(e);
            }
        }

        return new Date();
    }

    private static String getEndpointPath(AttributeType attr) {
        switch (attr) {
            case ARTIST:
                return "artists";
            case CHARACTER:
                return "characters";
            case TAG:
                return "tags";
            case LANGUAGE:
                return "languages";
            case CIRCLE:
                return "groups";
            case SERIE:
                return "series";
            default:
                throw new UnsupportedOperationException("Master data endpoint for " + attr.name() + "does not exist");
        }
    }


    // === ACCESSORS

    public void getRecentBooks(Site site, Language language, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener) {
        boolean showMostRecentFirst = Preferences.Constant.PREF_ORDER_CONTENT_LAST_UL_DATE_FIRST == orderStyle;

        if (isSiteUnsupported(site)) {
            throw new UnsupportedOperationException("Site " + site.getDescription() + "not supported yet by Mikan search");
        }

        Map<String, String> params = new HashMap<>();
        if (Language.ANY != language) params.put("language", language.getCode() + "");
        params.put("page", page + "");
        params.put("sort", showMostRecentFirst + "");

        disposable = MikanServer.API.getRecent(getMikanCodeForSite(site), params)
                .observeOn(mainThread())
                .subscribe((result) -> onContentSuccess(result, listener), (throwable) -> listener.onContentFailed(null, "Recent books failed to load - " + throwable.getMessage()));
    }

    public void getPages(Content content, ContentListener listener) {
        if (isSiteUnsupported(content.getSite())) {
            throw new UnsupportedOperationException("Site " + content.getSite().getDescription() + " not supported yet by Mikan search");
        }

        disposable = MikanServer.API.getPages(getMikanCodeForSite(content.getSite()), content.getUniqueSiteId())
                .observeOn(mainThread())
                .subscribe((result) -> onPagesSuccess(result, content, listener), (throwable) -> listener.onContentFailed(content, "Pages failed to load - " + throwable.getMessage()));
    }

    public void searchBooks(String query, List<Attribute> metadata, int page, int booksPerPage, int orderStyle, boolean favouritesOnly, ContentListener listener) {
        // NB : Mikan does not support booksPerPage and orderStyle params
        List<Attribute> sites = Helper.extractAttributeByType(metadata, AttributeType.SOURCE);

        if (sites.size() > 1) {
            throw new UnsupportedOperationException("Searching through multiple sites not supported yet by Mikan search");
        }
        Site site = (1 == sites.size()) ? Site.searchByCode(sites.get(0).getId()) : Site.HITOMI;
        if (null == site) {
            throw new UnsupportedOperationException("Unrecognized site ID " + sites.get(0).getId());
        }
        if (isSiteUnsupported(site)) {
            throw new UnsupportedOperationException("Site " + site.getDescription() + " not supported yet by Mikan search");
        }

        String suffix = (query != null && query.length() > 0) ? "/search/" + query : "";

        Map<String, String> params = new HashMap<>();
        params.put("page", page + "");

        List<Attribute> attributes = Helper.extractAttributeByType(metadata, AttributeType.ARTIST);
        if (attributes.size() > 0) params.put("artist", Helper.buildListAsString(attributes));

        attributes = Helper.extractAttributeByType(metadata, AttributeType.CIRCLE);
        if (attributes.size() > 0) params.put("group", Helper.buildListAsString(attributes));

        attributes = Helper.extractAttributeByType(metadata, AttributeType.CHARACTER);
        if (attributes.size() > 0) params.put("character", Helper.buildListAsString(attributes));

        attributes = Helper.extractAttributeByType(metadata, AttributeType.TAG);
        if (attributes.size() > 0) params.put("tag", Helper.buildListAsString(attributes));

        attributes = Helper.extractAttributeByType(metadata, AttributeType.LANGUAGE);
        if (attributes.size() > 0) params.put("language", Helper.buildListAsString(attributes));


        disposable = MikanServer.API.search(getMikanCodeForSite(site), suffix, params)
                .observeOn(mainThread())
                .subscribe((result) -> onContentSuccess(result, listener), (throwable) -> listener.onContentFailed(null, "Search failed to load - " + throwable.getMessage()));
    }

    public void getAttributeMasterData(AttributeType attr, String filter, AttributeListener listener) {

        // Try and get response from cache
        List<Attribute> attributes = AttributeCache.getFromCache(attr.name());

        // If not cached (or cache expired), get it from network
        if (null == attributes) {
            String endpoint = getEndpointPath(attr);
            disposable = MikanServer.API.getMasterData(endpoint)
                    .observeOn(mainThread())
                    .subscribe((result) -> {
                        onMasterDataSuccess(result, attr.name(), filter, listener); // TODO handle caching in computing thread
                    }, (throwable) -> listener.onAttributesFailed("Attributes failed to load - " + throwable.getMessage() ));
        } else {
            List<Attribute> result = filter(attributes, filter);
            listener.onAttributesReady(result, result.size());
        }
    }

    @Override
    public void dispose() {
        if (disposable != null) disposable.dispose();
    }


    // === CALLBACKS

    private void onContentSuccess(MikanContentResponse response, ContentListener listener) {
        if (null == response) {
            listener.onContentFailed(null, "Content failed to load - Empty response");
            return;
        }

        int maxItems = response.maxpage * response.result.size(); // Roughly : number of pages * number of books per page
        listener.onContentReady(response.toContentList(libraryMatcher), maxItems, maxItems);
    }

    private void onPagesSuccess(MikanContentResponse response, Content content, ContentListener listener) {
        if (null == response) {
            listener.onContentFailed(content, "Pages failed to load - Empty response");
            return;
        }

        if (null == content) listener.onContentFailed(null, "Pages failed to load - Unexpected empty content");
        else {
            List<Content> list = new ArrayList<Content>() {{
                add(content);
            }};
            content.setImageFiles(response.toImageFileList()).setQtyPages(response.pages.size());
            listener.onContentReady(list, 1, 1);
        }
    }

    private void onMasterDataSuccess(Response<MikanAttributeResponse> response, String attrName, String filter, AttributeListener listener) {
        MikanAttributeResponse result = response.body();
        if (null == result) {
            listener.onAttributesFailed("Attributes failed to load - Empty response");
            return;
        }

        List<Attribute> attributes = result.toAttributeList();

        // Filter illegal tags
        if (AttributeType.TAG.name().equals(attrName)) {
            attributes = Stream.of(attributes)
                    .filter(value -> !IllegalTags.isIllegal(value.getName()))
                    .collect(toList());
        }

        // Cache results
        AttributeCache.setCache(attrName, attributes, extractExpiry(response)); // TODO run that in a computing thread

//        Timber.d("Mikan response [%s] : %s", attrResponse.request, json.toString());

        List<Attribute> finalResult = filter(attributes, filter);
        listener.onAttributesReady(finalResult, finalResult.size());
    }

}
