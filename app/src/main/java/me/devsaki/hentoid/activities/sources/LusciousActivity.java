package me.devsaki.hentoid.activities.sources;

import android.webkit.WebResourceResponse;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.content.ContentParser;
import me.devsaki.hentoid.parsers.content.LusciousContent;
import me.devsaki.hentoid.util.Debouncer;
import me.devsaki.hentoid.util.network.HttpHelper;

public class LusciousActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "luscious.net";
    public static final String[] GALLERY_FILTER = {
            "operationName=AlbumGet", // Fetch using GraphQL call (NB : only works for desktop; mobile version calls GraphQL using `fetch`, which transmits query data in the POST body)
            "luscious.net/[\\w\\-]+/[\\w\\-]+_[0-9]+/$", // Actual gallery page URL (NB : only works for the first viewed gallery, or when manually reloading a page)
            "[\\w]+.luscious.net/[\\w\\-]+/[0-9]+/[\\w\\-\\.]+$" // Image URL containing album ID
    };
    //private static final String[] DIRTY_ELEMENTS = {".ad_banner"}; <-- doesn't work; added dynamically on an element tagged with a neutral-looking class

    public static final Pattern IMAGE_URL_PATTERN = Pattern.compile(GALLERY_FILTER[2]);


    Site getStartSite() {
        return Site.LUSCIOUS;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new LusciousWebClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        client.addUrlWhitelist(DOMAIN_FILTER);
        //client.addDirtyElements(DIRTY_ELEMENTS);
        return client;
    }

    private class LusciousWebClient extends CustomWebViewClient {

        private final Debouncer<Boolean> detectDebouncer;
        private final Map<String, Integer> bookIdsCount = new HashMap<>();

        LusciousWebClient(Site site, String[] filter, CustomWebActivity activity) {
            super(site, filter, activity);
            detectDebouncer = new Debouncer<>(getApplication(), 2000, this::clearLoadingPics);
        }

        private void clearLoadingPics(Boolean b) {
            bookIdsCount.clear();
        }

        /**
         * Specific implementation to get rid of ad js files that have random names
         */
        @Override
        protected boolean isUrlBlacklisted(@NonNull String url) {
            // 1- Process usual blacklist
            if (super.isUrlBlacklisted(url)) return true;

            // 2- Accept non-JS files
            if (!HttpHelper.getExtensionFromUri(url).equals("js")) return false;

            // 3- Accept JS files defined in the whitelist; block others
            return !super.isUrlWhitelisted(url);
        }

        // Call the API without using BaseWebActivity.parseResponse
        @Override
        protected WebResourceResponse parseResponse(@NonNull String urlStr, @Nullable Map<String, String> requestHeaders, boolean analyzeForDownload, boolean quickDownload) {
            String detectedBookId = "";

            // If a picture is being loaded, count the number of pictures loaded for a given book ID
            // If their count within 2 seconds exceeds 2, then we're loading a gallery = we're on that specific book gallery page !
            if (IMAGE_URL_PATTERN.matcher(urlStr).find()) {
                String[] parts = urlStr.split("/");
                String bookId = parts[4];

                Integer countObj = bookIdsCount.get(bookId);
                if (countObj != null) {
                    int count = countObj + 1;
                    // We just need one hit, not one hit per image
                    if (3 == count) detectedBookId = bookId;
                    bookIdsCount.put(bookId, count);
                } else {
                    bookIdsCount.put(bookId, 1);
                }
                detectDebouncer.submit(true);
            }

            activity.onGalleryPageStarted();

            String theUrl = (detectedBookId.isEmpty()) ? urlStr : detectedBookId;
            ContentParser contentParser = new LusciousContent();
            compositeDisposable.add(Single.fromCallable(() -> contentParser.toContent(theUrl))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            content -> super.processContent(content, content.getGalleryUrl(), quickDownload)
                    )
            );
            return null;
        }
    }
}
