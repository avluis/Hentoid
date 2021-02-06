package me.devsaki.hentoid.activities.sources;

import android.net.Uri;
import android.webkit.WebResourceResponse;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import io.reactivex.android.schedulers.AndroidSchedulers;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.json.sources.LusciousQueryParam;
import me.devsaki.hentoid.retrofit.sources.LusciousServer;
import me.devsaki.hentoid.util.JsonHelper;
import timber.log.Timber;

public class LusciousActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "luscious.net";
    private static final String[] GALLERY_FILTER = {"operationName=AlbumGet", "luscious.net/[A-Za-z0-9\\-]+/[A-Za-z0-9\\-_]+_[0-9]+/$"};

    Site getStartSite() {
        return Site.LUSCIOUS;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new LusciousWebClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

    private class LusciousWebClient extends CustomWebViewClient {

        LusciousWebClient(String[] filter, WebContentListener listener) {
            super(filter, listener);
        }

        // Call the API without using BaseWebActivity.parseResponse
        @Override
        protected WebResourceResponse parseResponse(@NonNull String urlStr, @Nullable Map<String, String> requestHeaders, boolean analyzeForDownload, boolean quickDownload) {
            String bookId;

            if (urlStr.contains(GALLERY_FILTER[0])) { // Triggered by a graphQL request
                String vars = Uri.parse(urlStr).getQueryParameter("variables");
                if (null == vars || vars.isEmpty()) {
                    Timber.w("No variable field found in %s", urlStr);
                    return null;
                }

                try {
                    bookId = JsonHelper.jsonToObject(vars, LusciousQueryParam.class).getId();
                } catch (Exception e) {
                    Timber.w(e);
                    return null;
                }
            } else { // Triggered by the loading of the page itself
                // ID is the last numeric part of the URL
                // e.g. /albums/lewd_title_ch_1_3_42116/ -> 42116 is the ID
                int lastIndex = urlStr.lastIndexOf('_');
                bookId = urlStr.substring(lastIndex + 1, urlStr.length() - 1);
            }

            Map<String, String> query = new HashMap<>();
            query.put("id", new Random().nextInt(10) + "");
            query.put("operationName", "AlbumGet");
            query.put("query", " query AlbumGet($id: ID!) { album { get(id: $id) { ... on Album { ...AlbumStandard } ... on MutationError { errors { code message } } } } } fragment AlbumStandard on Album { __typename id title labels description created modified like_status number_of_favorites rating status marked_for_deletion marked_for_processing number_of_pictures number_of_animated_pictures slug is_manga url download_url permissions cover { width height size url } created_by { id url name display_name user_title avatar { url size } } content { id title url } language { id title url } tags { id category text url count } genres { id title slug url } audiences { id title url url } last_viewed_picture { id position url } } "); // Yeah...
            query.put("variables", "{\"id\":\"" + bookId + "\"}");

            compositeDisposable.add(LusciousServer.API.getBookMetadata(query)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            metadata -> super.processContent(metadata.toContent(), urlStr, quickDownload),
                            throwable -> {
                                Timber.e(throwable, "Error parsing content.");
                                isHtmlLoaded = true;
                                listener.onResultFailed();
                            })
            );
            return null;
        }
    }
}
