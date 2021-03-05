package me.devsaki.hentoid.activities.sources;

import android.webkit.WebResourceResponse;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.content.ContentParser;
import me.devsaki.hentoid.parsers.content.LusciousContent;

public class LusciousActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "luscious.net";
    public static final String[] GALLERY_FILTER = {"operationName=AlbumGet", "luscious.net/[A-Za-z0-9\\-]+/[A-Za-z0-9\\-_]+_[0-9]+/$"};

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
            ContentParser contentParser = new LusciousContent();
            compositeDisposable.add(Single.fromCallable(() -> contentParser.toContent(urlStr))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            content -> super.processContent(content, urlStr, quickDownload)
                    )
            );
            return null;
        }
    }
}
