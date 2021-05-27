package me.devsaki.hentoid.util;

import android.os.Looper;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.network.HttpHelper;
import okhttp3.Response;
import okhttp3.ResponseBody;
import timber.log.Timber;

public class AdBlocker {
    private final Site site;

    // List of blocked URLs (ads or annoying images) -- will be replaced by a blank stream
    // Universal lists (applied to all sites)
    private static final Set<String> universalUrlBlacklist = new HashSet<>();
    // Local lists (applied to current site)
    private List<String> localUrlBlacklist;
    private final List<Pattern> jsWhitelistUrlPatternList = new ArrayList<>();
    private final List<String> jsContentBlacklist = new ArrayList<>();

    private final List<String> jsBlacklistCache = new ArrayList<>();


    static {
        String[] appUrlBlacklist = HentoidApp.getInstance().getResources().getStringArray(R.array.blocked_domains);
        universalUrlBlacklist.addAll(Arrays.asList(appUrlBlacklist));
    }


    public AdBlocker(Site s) {
        this.site = s;

        String[] appUrlWhitelist = HentoidApp.getInstance().getResources().getStringArray(R.array.allowed_domains);
        addUrlWhitelist(appUrlWhitelist);
    }

    /**
     * Indicates if the given URL is blacklisted by the current content filters
     *
     * @param url URL to be examinated
     * @return True if URL is blacklisted according to current filters; false if not
     */
    private boolean isUrlBlacklisted(@NonNull String url) {
        String comparisonUrl = url.toLowerCase();
        for (String s : universalUrlBlacklist) {
            if (comparisonUrl.contains(s)) return true;
        }
        if (localUrlBlacklist != null)
            for (String s : localUrlBlacklist) {
                if (comparisonUrl.contains(s)) return true;
            }
        return false;
    }

    /**
     * Add an element to current URL blacklist
     *
     * @param filter Filter to addAll to local blacklist
     */
    public void addToUrlBlacklist(String... filter) {
        if (null == localUrlBlacklist) localUrlBlacklist = new ArrayList<>();
        Collections.addAll(localUrlBlacklist, filter);
    }

    /**
     * Add an element to current URL whitelist
     *
     * @param filter Filter to addAll to local whitelist
     */
    public void addUrlWhitelist(String... filter) {
        for (String s : filter)
            addJsWhitelistUrlPattern("^.*" + s.replace(".", "\\.") + ".*$");
    }

    // TODO doc
    public void addJsWhitelistUrlPattern(String pattern) {
        jsWhitelistUrlPatternList.add(Pattern.compile(pattern));
    }

    // TODO doc
    public void addJsContentBlacklist(String sequence) {
        jsContentBlacklist.add(sequence);
    }

    // TODO doc
    public boolean isBlocked(@NonNull String url) {
        // 1- Process usual blacklist and cached dynamic blacklist
        if (isUrlBlacklisted(url)) return true;
        if (jsBlacklistCache.contains(url)) return true;

        // 2- Accept non-JS files
        String extension = HttpHelper.getExtensionFromUri(url);
        if (!extension.equals("js") && !extension.isEmpty())
            return false; // obvious JS and hidden JS

        // 3- Accept JS files defined in the whitelist
        for (Pattern p : jsWhitelistUrlPatternList) {
            Matcher matcher = p.matcher(url.toLowerCase());
            if (matcher.find()) return false;
        }

        // 4a- If no grey list is defined, block them as they are not whitelisted
        if (jsContentBlacklist.isEmpty()) return true;

        // 4b- If a grey list is defined, block them if they _contain_ keywords
        if (Looper.getMainLooper().getThread() != Thread.currentThread()) { // No network call on UI thread
            Timber.d(">> examining grey file %s", url);
            try {
                Response response = HttpHelper.getOnlineResource(url, null, site.useMobileAgent(), site.useHentoidAgent(), site.useWebviewAgent());
                ResponseBody body = response.body();
                if (null == body) throw new IOException("Empty body");

                String jsBody = body.string().toLowerCase();
                for (String s : jsContentBlacklist)
                    if (jsBody.contains(s)) {
                        Timber.d(">> grey file %s BLOCKED", url);
                        jsBlacklistCache.add(url);
                        return true;
                    }
            } catch (IOException e) {
                Timber.e(e);
            }
            addJsWhitelistUrlPattern("^" + url.replace(".", "\\.") + "$");
            Timber.d(">> grey file %s ALLOWED", url);
        }

        // Accept non-blocked (=grey) JS files
        return false;
    }
}
