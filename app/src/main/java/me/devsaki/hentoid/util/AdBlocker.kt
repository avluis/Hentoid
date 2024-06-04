package me.devsaki.hentoid.util

import android.os.Looper
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.file.readStreamAsStrings
import me.devsaki.hentoid.util.network.getExtensionFromUri
import me.devsaki.hentoid.util.network.getOnlineResourceFast
import me.devsaki.hentoid.util.network.webkitRequestHeadersToOkHttpHeaders
import timber.log.Timber
import java.io.IOException
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

class AdBlocker(val site: Site) {

    // List of blocked URLs (ads or annoying images) -- will be replaced by a blank stream
    // Universal lists (applied to all sites)
    private val universalUrlBlacklist: MutableSet<String> = HashSet()
    private val universalUrlWhitelist: MutableSet<String> = HashSet()
    private val universalJsContentBlacklist: MutableSet<String> = HashSet()

    // Local lists (applied to current site)
    private val localUrlBlacklist = Collections.synchronizedSet(HashSet<String>())
    private val localUrlWhitelist = Collections.synchronizedSet(HashSet<String>())
    private val localJsContentBlacklist = Collections.synchronizedSet(HashSet<String>())
    private val jsUrlPatternWhitelist = Collections.synchronizedSet(HashSet<Pattern>())

    private val jsBlacklistCache = Collections.synchronizedSet(HashSet<String>())

    private val isActive = AtomicBoolean(true)


    init {
        // Populate universal keywords
        HentoidApp.getInstance().resources.apply {
            openRawResource(R.raw.adblocker_url_blacklist).use { input ->
                universalUrlBlacklist.addAll(
                    readStreamAsStrings(input)
                        .map { s -> s.lowercase(Locale.getDefault()) })
            }
            openRawResource(R.raw.adblocker_url_whitelist).use { input ->
                universalUrlWhitelist.addAll(
                    readStreamAsStrings(input)
                        .map { s -> s.lowercase(Locale.getDefault()) })
            }
            openRawResource(R.raw.adblocker_js_word_blacklist).use { input ->
                universalJsContentBlacklist.addAll(
                    readStreamAsStrings(input)
                        .map { s -> s.lowercase(Locale.getDefault()) })
            }
        }
    }


    /**
     * Indicates if the given URL is blacklisted by the current content filters
     *
     * @param url URL to be examinated
     * @return True if URL is blacklisted according to current filters; false if not
     */
    private fun isUrlBlacklisted(url: String): Boolean {
        // First search into the local list...
        synchronized(localUrlBlacklist) {
            for (s in localUrlBlacklist) {
                if (url.contains(s)) {
                    if (BuildConfig.DEBUG) Timber.v(
                        "Blacklisted URL blocked (local) : %s",
                        url
                    )
                    return true
                }
            }
        }
        // ...then into the universal list
        for (s in universalUrlBlacklist) {
            if (url.contains(s)) {
                if (BuildConfig.DEBUG) Timber.v("Blacklisted URL blocked (global) : %s", url)
                return true
            }
        }
        return false
    }

    /**
     * Indicates if the given URL is whitelisted by the current content filters
     *
     * @param url URL to be examinated
     * @return True if the given URL is whitelisted according to current filters; false if not
     */
    private fun isUrlWhitelisted(url: String): Boolean {
        // First search into the local simple list...
        synchronized(localUrlWhitelist) {
            for (s in localUrlWhitelist) {
                if (url.contains(s)) {
                    if (BuildConfig.DEBUG) Timber.v("Whitelisted URL (local) : %s", url)
                    return true
                }
            }
        }
        // ...then into the global simple list...
        for (s in universalUrlWhitelist) {
            if (url.contains(s)) {
                if (BuildConfig.DEBUG) Timber.v("Whitelisted URL (global) : %s", url)
                return true
            }
        }
        // ...then into the js pattern list (more costly)
        synchronized(jsUrlPatternWhitelist) {
            for (p in jsUrlPatternWhitelist) {
                val matcher = p.matcher(url)
                if (matcher.find()) {
                    if (BuildConfig.DEBUG) Timber.v(
                        "Whitelisted URL (pattern) : %s",
                        url
                    )
                    return true
                }
            }
        }
        return false
    }

    /**
     * Indicate if the given Javascript content is blacklisted by the current content filters
     * If the URL is blacklisted, add it to jsBlacklistCache
     *
     * @param jsContent Contents of the Javascript file to be examinated
     * @return True if the given content is blacklisted according to current filters; false if not
     */
    private fun isJsContentBlacklisted(jsContent: String, url: String): Boolean {
        // First search into the local list...
        synchronized(localJsContentBlacklist) {
            for (s in localJsContentBlacklist) {
                if (jsContent.contains(s)) {
                    if (BuildConfig.DEBUG) Timber.v(
                        "Blacklisted JS content blocked (local) : %s",
                        jsContent
                    )
                    jsBlacklistCache.add(url)
                    return true
                }
            }
        }
        // ...then into the universal list
        for (s in universalJsContentBlacklist) {
            if (jsContent.contains(s)) {
                if (BuildConfig.DEBUG) Timber.v(
                    "Blacklisted JS content blocked (global) : %s",
                    jsContent
                )
                jsBlacklistCache.add(url)
                return true
            }
        }
        return false
    }

    /**
     * Add an element to current URL blacklist
     *
     * @param filter Filter to addAll to local blacklist
     */
    fun addToUrlBlacklist(vararg filter: String?) {
        Collections.addAll(localUrlBlacklist, *filter)
    }

    /**
     * Add an element to current URL whitelist
     *
     * @param filter Filter to addAll to local whitelist
     */
    fun addToJsUrlWhitelist(vararg filter: String?) {
        Collections.addAll(localUrlWhitelist, *filter)
    }

    /**
     * Add the given regexp pattern to the Javascript files URL whitelist
     *
     * @param pattern Pattern to add
     */
    fun addJsUrlPatternWhitelist(pattern: String) {
        jsUrlPatternWhitelist.add(Pattern.compile(pattern))
    }

    /**
     * Add the given sequence to the Javascript content blacklist
     *
     * @param sequence Sequence to add to the Javascript content blacklist
     */
    fun addJsContentBlacklist(sequence: String) {
        localJsContentBlacklist.add(sequence.lowercase(Locale.getDefault()))
    }

    fun setActive(value: Boolean) {
        isActive.set(value)
    }

    /**
     * Indicate if the resource at the given URL is blocked by the current adblock settings
     *
     * @param url     Url to examine
     * @param headers HTTP request headers to use
     * @return True if the resource is blocked; false if not
     */
    fun isBlocked(url: String, headers: Map<String, String>?): Boolean {
        if (!isActive.get()) return false

        val cleanUrl = url.lowercase(Locale.getDefault())

        // 1- Accept whitelisted JS files
        if (isUrlWhitelisted(cleanUrl)) return false

        // 2- Process usual blacklist and cached dynamic blacklist
        if (isUrlBlacklisted(cleanUrl)) return true
        if (jsBlacklistCache.contains(cleanUrl)) {
            if (BuildConfig.DEBUG)
                Timber.v("Blacklisted file BLOCKED (jsBlacklistCache) : %s", cleanUrl)
            return true
        }

        // 3- Accept non-JS files that are not blacklisted
        val extension = getExtensionFromUri(cleanUrl)
        val isJs = extension == "js" || extension.isEmpty() // Obvious js and hidden js
        if (!isJs) return false

        // If no grey list has been defined...
        if (universalJsContentBlacklist.isEmpty() && localJsContentBlacklist.isEmpty()) {
            // ...be lenient if there's no local whitelist set (vanilla adblocker); block instead as it has not been explicitly whitelisted
            return localUrlWhitelist.size + jsUrlPatternWhitelist.size > 0
        }


        // 4- If a grey list has been defined, block them if they _contain_ keywords
        if (Looper.getMainLooper().thread !== Thread.currentThread()) { // No network call on UI thread
            Timber.d(">> examining grey file : %s", url)
            try {
                val requestHeadersList = webkitRequestHeadersToOkHttpHeaders(headers, url)
                val response = getOnlineResourceFast(
                    url,
                    requestHeadersList,
                    site.useMobileAgent(),
                    site.useHentoidAgent(),
                    site.useWebviewAgent()
                )
                if (response.code >= 400) {
                    Timber.d(">> grey file KO (%d) : %s", response.code, url)
                    return false // Better safe than sorry
                }
                val body = response.body ?: throw IOException("Empty body")
                Timber.d(">> grey file downloaded : %s", url)
                // Handle "grey files" by analyzing its contents
                val jsBody = body.string().lowercase(Locale.getDefault())
                if (isJsContentBlacklisted(jsBody, cleanUrl)) return true
            } catch (e: IOException) {
                Timber.d(e, ">> I/O issue while retrieving %s", url)
            } catch (iae: IllegalArgumentException) {
                Timber.e(iae)
                return true // Avoid feeding malformed URLs to Chromium on older Androids
            }
            // Don't whitelist the site root as it will auto-whitelist every file hosted there
            if (cleanUrl != site.url.lowercase(Locale.getDefault())) addToJsUrlWhitelist(cleanUrl)
            Timber.d(">> grey file %s ALLOWED", url)
        }

        // Accept non-blocked (=grey) JS files
        return false
    }
}