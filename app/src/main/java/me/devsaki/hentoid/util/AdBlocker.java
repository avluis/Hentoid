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

import me.devsaki.hentoid.BuildConfig;
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
    private static final Set<String> universalUrlWhitelist = new HashSet<>();
    // Local lists (applied to current site)
    private List<String> localUrlBlacklist;
    private List<String> localUrlWhitelist;
    private final List<Pattern> jsUrlPatternWhitelist = Collections.synchronizedList(new ArrayList<>());
    private final List<String> jsContentBlacklist = new ArrayList<>();

    private final List<String> jsBlacklistCache = Collections.synchronizedList(new ArrayList<>());


    static {
        String[] appUrlBlacklist = HentoidApp.getInstance().getResources().getStringArray(R.array.blocked_domains);
        universalUrlBlacklist.addAll(Arrays.asList(appUrlBlacklist));
        String[] appUrlWhitelist = HentoidApp.getInstance().getResources().getStringArray(R.array.allowed_domains);
        universalUrlWhitelist.addAll(Arrays.asList(appUrlWhitelist));
    }


    public AdBlocker(Site site) {
        this.site = site;
    }

    /**
     * Indicates if the given URL is blacklisted by the current content filters
     *
     * @param url URL to be examinated
     * @return True if URL is blacklisted according to current filters; false if not
     */
    private boolean isUrlBlacklisted(@NonNull String url) {
        // First search into the local list...
        if (localUrlBlacklist != null)
            for (String s : localUrlBlacklist) {
                if (url.contains(s)) {
                    if (BuildConfig.DEBUG) Timber.v("Blacklisted URL blocked (local) : %s", url);
                    return true;
                }
            }
        // ...then into the universal list
        for (String s : universalUrlBlacklist) {
            if (url.contains(s)) {
                if (BuildConfig.DEBUG) Timber.v("Blacklisted URL blocked (global) : %s", url);
                return true;
            }
        }
        return false;
    }

    /**
     * Indicates if the given URL is whitelisted by the current content filters
     *
     * @param url URL to be examinated
     * @return True if URL is whitelisted according to current filters; false if not
     */
    private boolean isUrlWhitelisted(@NonNull String url) {
        // First search into the local simple list...
        if (localUrlWhitelist != null)
            for (String s : localUrlWhitelist) {
                if (url.contains(s)) {
                    if (BuildConfig.DEBUG) Timber.v("Whitelisted URL (local) : %s", url);
                    return true;
                }
            }
        // ...then into the global simple list...
        for (String s : universalUrlWhitelist) {
            if (url.contains(s)) {
                if (BuildConfig.DEBUG) Timber.v("Whitelisted URL (global) : %s", url);
                return true;
            }
        }
        // ...then into the js pattern list (more costly)
        for (Pattern p : jsUrlPatternWhitelist) {
            Matcher matcher = p.matcher(url);
            if (matcher.find()) {
                if (BuildConfig.DEBUG) Timber.v("Whitelisted URL (pattern) : %s", url);
                return true;
            }
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
    public void addJsUrlWhitelist(String... filter) {
        if (null == localUrlWhitelist) localUrlWhitelist = new ArrayList<>();
        Collections.addAll(localUrlWhitelist, filter);
    }

    // TODO doc
    public void addJsUrlPatternWhitelist(String pattern) {
        jsUrlPatternWhitelist.add(Pattern.compile(pattern));
    }

    // TODO doc
    public void addJsContentBlacklist(String sequence) {
        jsContentBlacklist.add(sequence);
    }

    // TODO doc
    public boolean isBlocked(@NonNull String url) {
        final String cleanUrl = url.toLowerCase();

        // 1- Accept whitelisted JS files
        String extension = HttpHelper.getExtensionFromUri(cleanUrl);
        boolean isJs = (extension.equals("js") || extension.isEmpty()); // Obvious js and hidden js
        if (isJs && isUrlWhitelisted(cleanUrl)) return false;

        // 2- Process usual blacklist and cached dynamic blacklist
        if (isUrlBlacklisted(cleanUrl)) return true;
        if (jsBlacklistCache.contains(cleanUrl)) return true;

        // 3- Accept non-JS files that are not blacklisted
        if (!isJs) return false;

        // If no grey list has been defined, block url as it has not been whitelisted
        if (jsContentBlacklist.isEmpty()) return true;


        // 4- If a grey list has been defined, block them if they _contain_ keywords
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
                        jsBlacklistCache.add(cleanUrl);
                        return true;
                    }
            } catch (IOException e) {
                Timber.e(e);
            } catch (IllegalArgumentException iae) {
                Timber.e(iae);
                return true; // Avoid feeding malformed URLs to Chromium on older Androids (crash reported on Lollipop)
            }
            addJsUrlPatternWhitelist("^" + cleanUrl.replace(".", "\\.") + "$");
            Timber.d(">> grey file %s ALLOWED", url);
        }

        // Accept non-blocked (=grey) JS files
        return false;
    }
}
