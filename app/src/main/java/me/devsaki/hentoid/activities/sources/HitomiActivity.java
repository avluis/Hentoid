package me.devsaki.hentoid.activities.sources;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.network.HttpHelper;
import okhttp3.Response;
import timber.log.Timber;

/**
 * Created by Shiro on 1/20/2016.
 * Implements Hitomi.la source
 */
public class HitomiActivity extends BaseWebActivity {

    private static final String DOMAIN_FILTER = "hitomi.la";
    private static final String[] GALLERY_FILTER = {"//hitomi.la/[A-Za-z0-9\\-]+/[^/]+-[0-9]{2,}.html$"};
    private static final String[] blockedContent = {"hitomi-horizontal.js", "hitomi-vertical.js", "invoke.js", "ion.sound"};
    private static final String[] jsWhitelist = {"galleries/[A-Za-z0-9\\-]+.js$", "jquery", "filesaver", "common", "date", "download", "gallery", "jquery", "cookie", "jszip", "limitlists", "moment-with-locales", "moveimage", "pagination", "search", "searchlib", "yall", "reader", "decode_webp", "bootstrap"};
    private static final String[] blockedJsContents = {"exoloader", "popunder"};

    private static List<Pattern> whitelistUrlPattern = new ArrayList<>();
    private static List<String> jsBlacklistCache = new ArrayList<>();

    static {
        for (String s : jsWhitelist) whitelistUrlPattern.add(Pattern.compile(s));
    }

    Site getStartSite() {
        return Site.HITOMI;
    }

    @Override
    protected CustomWebViewClient getWebClient() {
        addContentBlockFilter(blockedContent);
        CustomWebViewClient client = new CustomWebViewClient(GALLERY_FILTER, this);
        client.restrictTo(DOMAIN_FILTER);
        return client;
    }

    /**
     * Specific implementation to get rid of Hitomi's ad js files
     * that have random names
     */
    @Override
    protected boolean isUrlForbidden(@NonNull String url) {
        // 1- Process usual blacklist and cached dynamic blacklist
        if (super.isUrlForbidden(url)) return true;
        if (jsBlacklistCache.contains(url)) return true;

        // 2- Accept non-JS files
        if (!url.toLowerCase().endsWith(".js")) return false;

        // 3- Accept JS files defined in the whitelist
        for (Pattern p : whitelistUrlPattern) {
            Matcher matcher = p.matcher(url.toLowerCase());
            if (matcher.find()) return false;
        }

        // 4- For the others (gray list), block them if they _contain_ keywords
        Timber.d(">> examining grey file %s", url);
        try {
            Response response = HttpHelper.getOnlineResource(url, null, getStartSite().canKnowHentoidAgent());
            if (null == response.body()) throw new IOException("Empty body");

            String jsBody = response.body().string().toLowerCase();
            for (String s : blockedJsContents)
                if (jsBody.contains(s)) {
                    jsBlacklistCache.add(url);
                    return true;
                }
        } catch (IOException e) {
            Timber.e(e);
        }

        // Accept non-blocked grey JS files
        return false;
    }
}
