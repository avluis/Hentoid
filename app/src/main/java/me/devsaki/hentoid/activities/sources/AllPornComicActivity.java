package me.devsaki.hentoid.activities.sources;

import android.os.Looper;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.network.HttpHelper;
import okhttp3.Response;
import okhttp3.ResponseBody;
import timber.log.Timber;

public class AllPornComicActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "allporncomic.com";
    private static final String[] GALLERY_FILTER = {"allporncomic.com/porncomic/[%\\w\\-]+/$"};
    private static final String[] JS_WHITELIST = {DOMAIN_FILTER + "/cdn", DOMAIN_FILTER + "/wp"};
    private static final String[] JS_CONTENT_BLACKLIST = {"var exoloader;", "popunder"};
    private static final String[] DIRTY_ELEMENTS = {"iframe"};

    private static final List<String> jsBlacklistCache = new ArrayList<>();


    Site getStartSite() {
        return Site.ALLPORNCOMIC;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        CustomWebViewClient client = new AllPornComicWebViewClient(getStartSite(), GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        client.addUrlWhitelist(JS_WHITELIST);
        client.addDirtyElements(DIRTY_ELEMENTS);
        return client;
    }

    private static class AllPornComicWebViewClient extends CustomWebViewClient {

        AllPornComicWebViewClient(Site site, String[] filter, CustomWebActivity activity) {
            super(site, filter, activity);
        }

        /**
         * Specific implementation to get rid of ad js files that have random names
         */
        @Override
        protected boolean isUrlBlacklisted(@NonNull String url) {
            // 1- Process usual blacklist and cached dynamic blacklist
            if (super.isUrlBlacklisted(url)) return true;
            if (jsBlacklistCache.contains(url)) return true;

            // 2- Accept non-JS files
            String extension = HttpHelper.getExtensionFromUri(url);
            if (!extension.equals("js") && !extension.isEmpty()) return false; // obvious JS and hidden JS

            // 3- Accept JS files defined in the whitelist
            if (super.isUrlWhitelisted(url)) return false;

            // 4- For the others (gray list), block them if they _contain_ keywords
            if (Looper.getMainLooper().getThread() != Thread.currentThread()) { // No call on UI thread
                Timber.d(">> examining grey file %s", url);
                try {
                    Response response = HttpHelper.getOnlineResource(url, null, site.useMobileAgent(), site.useHentoidAgent(), site.useWebviewAgent());
                    ResponseBody body = response.body();
                    if (null == body) throw new IOException("Empty body");

                    String jsBody = body.string().toLowerCase();
                    for (String s : JS_CONTENT_BLACKLIST)
                        if (jsBody.contains(s)) {
                            Timber.d(">> grey file %s BLOCKED", url);
                            jsBlacklistCache.add(url);
                            return true;
                        }
                } catch (IOException e) {
                    Timber.e(e);
                }
                Timber.d(">> grey file %s ALLOWED", url);
            }

            // Accept non-blocked (=grey) JS files
            return false;
        }
    }
}
