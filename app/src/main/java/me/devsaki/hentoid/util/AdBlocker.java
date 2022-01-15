package me.devsaki.hentoid.util;

import android.os.Looper;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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
    private final Set<String> localUrlBlacklist = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> localUrlWhitelist = Collections.synchronizedSet(new HashSet<>());
    private final Set<Pattern> jsUrlPatternWhitelist = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> jsContentBlacklist = Collections.synchronizedSet(new HashSet<>());

    private final Set<String> jsBlacklistCache = Collections.synchronizedSet(new HashSet<>());


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
        synchronized (localUrlBlacklist) {
            for (String s : localUrlBlacklist) {
                if (url.contains(s)) {
                    if (BuildConfig.DEBUG) Timber.v("Blacklisted URL blocked (local) : %s", url);
                    return true;
                }
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
        synchronized (localUrlWhitelist) {
            for (String s : localUrlWhitelist) {
                if (url.contains(s)) {
                    if (BuildConfig.DEBUG) Timber.v("Whitelisted URL (local) : %s", url);
                    return true;
                }
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
        synchronized (jsUrlPatternWhitelist) {
            for (Pattern p : jsUrlPatternWhitelist) {
                Matcher matcher = p.matcher(url);
                if (matcher.find()) {
                    if (BuildConfig.DEBUG) Timber.v("Whitelisted URL (pattern) : %s", url);
                    return true;
                }
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
        Collections.addAll(localUrlBlacklist, filter);
    }

    /**
     * Add an element to current URL whitelist
     *
     * @param filter Filter to addAll to local whitelist
     */
    public void addJsUrlWhitelist(String... filter) {
        Collections.addAll(localUrlWhitelist, filter);
    }

    /**
     * Add the given regexp pattern to the Javascript files URL whitelist
     *
     * @param pattern Pattern to add
     */
    public void addJsUrlPatternWhitelist(@NonNull final String pattern) {
        jsUrlPatternWhitelist.add(Pattern.compile(pattern));
    }

    /**
     * Add the given sequence to the Javascript content blacklist
     *
     * @param sequence Sequence to add to the Javascript content blacklist
     */
    public void addJsContentBlacklist(@NonNull final String sequence) {
        jsContentBlacklist.add(sequence);
    }

    /**
     * Indicate if the resource at the given URL is blocked by the current adblock settings
     *
     * @param url Url to examine
     * @return True if the resource is blocked; false if not
     */
    public boolean isBlocked(@NonNull final String url) {
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

        // If no grey list has been defined...
        if (jsContentBlacklist.isEmpty()) {
            // ...be lenient if there's no local whitelist set (vanilla adblocker); block instead as it has not been explicitly whitelisted
            return (localUrlWhitelist.size() + jsUrlPatternWhitelist.size() > 0);
        }


        // 4- If a grey list has been defined, block them if they _contain_ keywords
        if (Looper.getMainLooper().getThread() != Thread.currentThread()) { // No network call on UI thread
            Timber.d(">> examining grey file : %s", url);
            try {
                Response response = HttpHelper.getOnlineResourceFast(url, null, site.useMobileAgent(), site.useHentoidAgent(), site.useWebviewAgent());
                ResponseBody body = response.body();
                if (null == body) throw new IOException("Empty body");
                Timber.d(">> grey file downloaded : %s", url);

                String jsBody = body.string().toLowerCase();
                synchronized (jsContentBlacklist) {
                    for (String s : jsContentBlacklist)
                        if (jsBody.contains(s)) {
                            Timber.d(">> grey file %s BLOCKED", url);
                            jsBlacklistCache.add(cleanUrl);
                            return true;
                        }
                }
            } catch (IOException e) {
                Timber.d(e, ">> I/O issue while retrieving %s", url);
            } catch (IllegalArgumentException iae) {
                Timber.e(iae);
                return true; // Avoid feeding malformed URLs to Chromium on older Androids (crash reported on Lollipop)
            }
            addJsUrlWhitelist(cleanUrl);
            Timber.d(">> grey file %s ALLOWED", url);
        }

        // Accept non-blocked (=grey) JS files
        return false;
    }
}
