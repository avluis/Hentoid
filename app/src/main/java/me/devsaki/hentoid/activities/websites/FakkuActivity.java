package me.devsaki.hentoid.activities.websites;

import android.webkit.CookieManager;

import java.util.HashMap;
import java.util.Map;

import io.reactivex.android.schedulers.AndroidSchedulers;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.listener.ResultListener;
import me.devsaki.hentoid.retrofit.FakkuServer;
import me.devsaki.hentoid.util.JsonHelper;
import timber.log.Timber;

public class FakkuActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "fakku.net";
    private static final String GALLERY_FILTER = "fakku.net/hentai/";

    Site getStartSite() {
        return Site.FAKKU2;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new FakkuViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

    private class FakkuViewClient extends CustomWebViewClient {

        FakkuViewClient(String filteredUrl, ResultListener<Content> listener) {
            super(filteredUrl, listener);
        }

        @Override
        protected void onGalleryFound(String url) {
            String cookie = CookieManager.getInstance().getCookie(url);
            String[] galleryUrlParts = url.split("/");
            compositeDisposable.add(FakkuServer.API.getGalleryMetadata(galleryUrlParts[galleryUrlParts.length - 1], cookie)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            metadata -> {
                                Content content = metadata.toContent();
                                if (content != null) {
                                    // Save cookies for future calls during download
                                    Map<String, String> params = new HashMap<>();
                                    params.put("cookie", cookie);
                                    content.setDownloadParams(JsonHelper.serializeToJson(params));
                                }

                                listener.onResultReady(content, 1);
                            }, throwable -> {
                                Timber.e(throwable, "Error parsing content.");
                                listener.onResultFailed("");
                            })
            );
        }
    }
}
