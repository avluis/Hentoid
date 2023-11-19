package me.devsaki.hentoid.activities.sources;

import android.webkit.WebResourceResponse;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.Map;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.json.sources.LusciousQuery;
import me.devsaki.hentoid.parsers.content.ContentParser;
import me.devsaki.hentoid.parsers.content.LusciousContent;
import me.devsaki.hentoid.util.JsonHelper;
import timber.log.Timber;

public class LusciousActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "luscious.net";
    public static final String[] GALLERY_FILTER = {
            "operationName=AlbumGet", // Fetch using GraphQL call
            "luscious.net/[\\w\\-]+/[\\w\\-]+_[0-9]+/$" // Actual gallery page URL (NB : only works for the first viewed gallery, or when manually reloading a page)
    };
    //private static final String[] REMOVABLE_ELEMENTS = {".ad_banner"}; <-- doesn't work; added dynamically on an element tagged with a neutral-looking class


    Site getStartSite() {
        return Site.LUSCIOUS;
    }

    @Override
    protected CustomWebViewClient createWebClient() {
        LusciousWebClient client = new LusciousWebClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        client.adBlocker.addToJsUrlWhitelist(DOMAIN_FILTER);
        client.setJsStartupScripts("luscious_adblock.js");

        // Init fetch handler here for convenience
        fetchHandler = client::onFetchCall;

        return client;
    }

    private static class LusciousWebClient extends CustomWebViewClient {

        LusciousWebClient(Site site, String[] filter, CustomWebActivity activity) {
            super(site, filter, activity);
        }

        public void onFetchCall(String url, String body) {
            if (!isGalleryPage(url)) return;
            try {
                LusciousQuery query = JsonHelper.jsonToObject(body, LusciousQuery.class);
                String id = query.getIdVariable();
                if (!id.isEmpty()) parseResponse(id, null, true, false);
            } catch (IOException e) {
                Timber.e(e);
            }
        }

        // Call the API without using BaseWebActivity.parseResponse
        @Override
        protected WebResourceResponse parseResponse(@NonNull String urlStr, @Nullable Map<String, String> requestHeaders, boolean analyzeForDownload, boolean quickDownload) {
            if (activity != null) activity.onGalleryPageStarted();

            ContentParser contentParser = new LusciousContent();
            compositeDisposable.add(Single.fromCallable(() -> contentParser.toContent(urlStr))
                    .subscribeOn(Schedulers.io())
                    .map(content -> super.processContent(content, content.getGalleryUrl(), quickDownload))
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            content2 -> resConsumer.onContentReady(content2, quickDownload),
                            Timber::e
                    )
            );
            return null;
        }
    }
}
