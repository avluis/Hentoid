package me.devsaki.hentoid.activities.sources;

import static me.devsaki.hentoid.util.network.HttpHelper.HEADER_CONTENT_TYPE;
import static me.devsaki.hentoid.util.network.HttpHelper.getExtensionFromUri;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.BiFunction;
import com.annimon.stream.function.Consumer;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ContentParserFactory;
import me.devsaki.hentoid.parsers.content.ContentParser;
import me.devsaki.hentoid.util.AdBlocker;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.ToastHelper;
import me.devsaki.hentoid.util.file.FileHelper;
import me.devsaki.hentoid.util.image.ImageHelper;
import me.devsaki.hentoid.util.network.HttpHelper;
import okhttp3.Response;
import okhttp3.ResponseBody;
import pl.droidsonroids.jspoon.HtmlAdapter;
import pl.droidsonroids.jspoon.Jspoon;
import timber.log.Timber;

/**
 * Analyze loaded HTML to display download button
 * Override blocked content with empty content
 */
class CustomWebViewClient extends WebViewClient {

    // Pre-built object to represent an empty input stream
    // (will be used instead of the actual stream when the requested resource is blocked)
    private final byte[] NOTHING = new byte[0];
    // Pre-built object to represent WEBP binary data for the checkmark icon used to mark downloaded books
    // (will be fed directly to the browser when the resourcei is requested)
    private final byte[] CHECKMARK;
    // this is for merged books
    private final byte[] MERGED_MARK;
    // this is for books with blocked tags;
    private final byte[] BLOCKED_MARK;

    // Site for the session
    protected final Site site;
    // Used to clear RxJava observers (avoiding memory leaks)
    protected final CompositeDisposable compositeDisposable = new CompositeDisposable();
    // Listener to the results of the page parser
    protected final CustomWebActivity activity;
    // List of the URL patterns identifying a parsable book gallery page
    private final List<Pattern> galleryUrlPattern = new ArrayList<>();
    // List of the URL patterns identifying a parsable book gallery page
    private final List<Pattern> resultsUrlPattern = new ArrayList<>();

    // Results URL rewriter to insert page to seek to
    private BiFunction<Uri, Integer, String> resultsUrlRewriter = null;
    // Adapter used to parse the HTML code of book gallery pages
    private final HtmlAdapter<? extends ContentParser> htmlAdapter;
    // Domain name for which link navigation is restricted
    private final List<String> restrictedDomainNames = new ArrayList<>();
    // Loading state of the current webpage (used for the refresh/stop feature)
    private final AtomicBoolean isPageLoading = new AtomicBoolean(false);
    // Loading state of the HTML code of the current webpage (used to trigger the action button)
    private final AtomicBoolean isHtmlLoaded = new AtomicBoolean(false);
    // URL string of the main page (used for custom CSS loading)
    private String mainPageUrl;

    protected final AdBlocker adBlocker;

    // Faster access to Preferences settings
    private final AtomicBoolean markDownloaded = new AtomicBoolean(Preferences.isBrowserMarkDownloaded());
    private final AtomicBoolean markMerged = new AtomicBoolean((Preferences.isBrowserMarkMerged()));
    private final AtomicBoolean markBlockedTags = new AtomicBoolean((Preferences.isBrowserMarkBlockedTags()));
    private final AtomicBoolean dnsOverHttpsEnabled = new AtomicBoolean(Preferences.getDnsOverHttps() > -1);

    // Disposable to be used for punctual operations
    private Disposable disposable;


    // List of elements (CSS selector) to be removed before displaying the page
    private List<String> removableElements;

    // List of blacklisted Javascript strings : if any of these is found inside
    // an inline script tag, the entire tag is removed from the HTML
    private List<String> jsContentBlacklist;

    // Custom method to use while pre-processing HTML
    private Consumer<Document> customHtmlRewriter = null;

    // List of JS scripts to load from app resources every time a webpage is started
    private List<String> jsStartupScripts;


    CustomWebViewClient(Site site, String[] galleryUrl, CustomWebActivity activity) {
        this.site = site;
        this.activity = activity;

        Class<? extends ContentParser> c = ContentParserFactory.getInstance().getContentParserClass(site);
        final Jspoon jspoon = Jspoon.create();
        htmlAdapter = jspoon.adapter(c); // Unchecked but alright

        adBlocker = new AdBlocker(site);

        for (String s : galleryUrl) galleryUrlPattern.add(Pattern.compile(s));

        CHECKMARK = ImageHelper.BitmapToWebp(
                ImageHelper.tintBitmap(
                        ImageHelper.getBitmapFromVectorDrawable(HentoidApp.getInstance(), R.drawable.ic_checked),
                        HentoidApp.getInstance().getResources().getColor(R.color.secondary_light)
                )
        );

        MERGED_MARK = ImageHelper.BitmapToWebp(
                ImageHelper.tintBitmap(
                        ImageHelper.getBitmapFromVectorDrawable(HentoidApp.getInstance(), R.drawable.ic_action_merge),
                        HentoidApp.getInstance().getResources().getColor(R.color.secondary_light)
                )
        );

        BLOCKED_MARK = ImageHelper.BitmapToWebp(
                ImageHelper.tintBitmap(
                        ImageHelper.getBitmapFromVectorDrawable(HentoidApp.getInstance(), R.drawable.ic_forbidden),
                        HentoidApp.getInstance().getResources().getColor(R.color.secondary_light)
                )
        );
    }

    void destroy() {
        Timber.d("WebClient destroyed");
        compositeDisposable.clear();
    }

    /**
     * Add an element filter to current site
     *
     * @param elements Elements (CSS selector) to addAll to page cleaner
     */
    protected void addRemovableElements(String... elements) {
        if (null == removableElements) removableElements = new ArrayList<>();
        Collections.addAll(removableElements, elements);
    }

    /**
     * Add a Javascript blacklisted element filter to current site
     *
     * @param elements Elements (string) to addAll to page cleaner
     */
    protected void addJavascriptBlacklist(String... elements) {
        if (null == jsContentBlacklist) jsContentBlacklist = new ArrayList<>();
        Collections.addAll(jsContentBlacklist, elements);
    }

    /**
     * Set the list of patterns to detect URLs where result paging can be applied
     *
     * @param patterns Patterns to detect URLs where result paging can be applied
     */
    void setResultsUrlPatterns(String... patterns) {
        for (String s : patterns) resultsUrlPattern.add(Pattern.compile(s));
    }

    /**
     * Set the rewriter to use when paging results from the app :
     * - 1st argument : Search results page URL, as an Uri
     * - 2nd argument : Search results page number to reach
     * - Result : Modified Uri, as a string
     *
     * @param rewriter Rewriter to use when paging results from the app
     */
    void setResultUrlRewriter(@NonNull BiFunction<Uri, Integer, String> rewriter) {
        resultsUrlRewriter = rewriter;
    }

    void setCustomHtmlRewriter(@NonNull Consumer<Document> rewriter) {
        customHtmlRewriter = rewriter;
    }

    /**
     * Set the list of JS scripts (app assets) to load at each new page start
     *
     * @param assetNames Name of assets to load
     */
    void setJsStartupScripts(String... assetNames) {
        if (null == jsStartupScripts) jsStartupScripts = new ArrayList<>();
        Collections.addAll(jsStartupScripts, assetNames);
    }

    /**
     * Restrict link navigation to a given domain name
     *
     * @param s Domain name to restrict link navigation to
     */
    protected void restrictTo(String s) {
        restrictedDomainNames.add(s);
    }

    void restrictTo(String... s) {
        restrictedDomainNames.addAll(Arrays.asList(s));
    }

    private boolean isHostNotInRestrictedDomains(@NonNull String host) {
        if (restrictedDomainNames.isEmpty()) return false;

        for (String s : restrictedDomainNames) {
            if (host.contains(s)) return false;
        }

        Timber.i("Unrestricted host detected : %s", host);
        return true;
    }

    /**
     * Indicates if the given URL is a book gallery page
     *
     * @param url URL to test
     * @return True if the given URL represents a book gallery page
     */
    boolean isGalleryPage(@NonNull final String url) {
        if (galleryUrlPattern.isEmpty()) return false;

        for (Pattern p : galleryUrlPattern) {
            Matcher matcher = p.matcher(url);
            if (matcher.find()) return true;
        }
        return false;
    }

    /**
     * Indicates if the given URL is a results page
     *
     * @param url URL to test
     * @return True if the given URL represents a results page
     */
    boolean isResultsPage(@NonNull final String url) {
        if (resultsUrlPattern.isEmpty()) return false;

        for (Pattern p : resultsUrlPattern) {
            Matcher matcher = p.matcher(url);
            if (matcher.find()) return true;
        }
        return false;
    }

    /**
     * Rewrite the given URL to seek the given page number
     *
     * @param url     URL to be rewritten
     * @param pageNum page number to seek
     * @return Given URL to be rewritten
     */
    protected String seekResultsUrl(@NonNull String url, int pageNum) {
        if (null == resultsUrlRewriter || !isResultsPage(url) || isGalleryPage(url)) return url;
        else return resultsUrlRewriter.apply(Uri.parse(url), pageNum);
    }

    /**
     * Determines if the browser can use one single OkHttp request to serve HTML pages
     * - Does not work on 4.4 & 4.4.2 because calling CookieManager.getCookie inside shouldInterceptRequest triggers a deadlock
     * https://issuetracker.google.com/issues/36989494
     * - Does not work on Chrome 45-71 because sameSite cookies are not published by CookieManager.getCookie (causes session issues on nHentai)
     * https://bugs.chromium.org/p/chromium/issues/detail?id=780491
     *
     * @return true if HTML content can be served by a single OkHttp request,
     * false if the webview has to handle the display (OkHttp will be used as a 2nd request for parsing)
     */
    private boolean canUseSingleOkHttpRequest() {
        return (Preferences.isBrowserAugmented()
                && (HttpHelper.getChromeVersion() < 45 || HttpHelper.getChromeVersion() > 71)
        );
    }

    /**
     * @deprecated kept for API19-API23
     */
    @Override
    @Deprecated
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return shouldOverrideUrlLoadingInternal(view, url, null);
    }

    @TargetApi(Build.VERSION_CODES.N)
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return shouldOverrideUrlLoadingInternal(view, request.getUrl().toString(), request.getRequestHeaders());
    }

    protected boolean shouldOverrideUrlLoadingInternal(
            @NonNull final WebView view,
            @NonNull final String url,
            @Nullable final Map<String, String> requestHeaders) {
        if ((Preferences.isBrowserAugmented() && adBlocker.isBlocked(url, requestHeaders)) || !url.startsWith("http"))
            return true;

        // Download and open the torrent file
        // NB : Opening the URL itself won't work when the tracker is private
        // as the 3rd party torrent app doesn't have access to it
        if (HttpHelper.getExtensionFromUri(url).equals("torrent")) {
            disposable = Single.fromCallable(() -> downloadFile(view.getContext(), url, requestHeaders))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(uri -> {
                        disposable.dispose();
                        FileHelper.openFile(view.getContext(), uri);
                    }, e -> {
                        disposable.dispose();
                        ToastHelper.toast(R.string.torrent_dl_fail, e.getMessage());
                        Timber.w(e);
                    });
        }

        String host = Uri.parse(url).getHost();
        return host != null && isHostNotInRestrictedDomains(host);
    }

    /**
     * Download the resource at the given URL to the app's cache folder
     *
     * @param context        Context to be used
     * @param url            URL to load
     * @param requestHeaders Request headers (optional)
     * @return Saved file, if successful
     * @throws IOException if anything horrible happens during the download
     */
    private File downloadFile(@NonNull final Context context,
                              @NonNull final String url,
                              @Nullable final Map<String, String> requestHeaders) throws IOException {
        List<Pair<String, String>> requestHeadersList;
        requestHeadersList = HttpHelper.webkitRequestHeadersToOkHttpHeaders(requestHeaders, url);

        Response onlineFileResponse = HttpHelper.getOnlineResource(url, requestHeadersList, site.useMobileAgent(), site.useHentoidAgent(), site.useWebviewAgent());
        ResponseBody body = onlineFileResponse.body();
        if (null == body)
            throw new IOException("Empty response from server");

        File cacheDir = context.getCacheDir();
        // Using a random file name rather than the original name to avoid errors caused by path length
        File file = new File(cacheDir.getAbsolutePath() + File.separator + Helper.getRandomInt(10000) + "." + getExtensionFromUri(url));
        if (!file.createNewFile())
            throw new IOException("Could not create file " + file.getPath());

        Uri torrentFileUri = Uri.fromFile(file);
        FileHelper.saveBinary(context, torrentFileUri, body.bytes());
        return file;
    }

    /**
     * Important note
     * <p>
     * Based on observation, for a given URL, onPageStarted seems to be called
     * - Before {@link this.shouldInterceptRequest} when the page is not cached (1st call)
     * - After {@link this.shouldInterceptRequest} when the page is cached (Nth call; N>1)
     */
    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        if (BuildConfig.DEBUG) Timber.v("WebView : page started %s", url);
        isPageLoading.set(true);
        activity.onPageStarted(url, isGalleryPage(url), isHtmlLoaded.get(), true, jsStartupScripts);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        if (BuildConfig.DEBUG) Timber.v("WebView : page finished %s", url);
        isPageLoading.set(false);
        isHtmlLoaded.set(false); // Reset for the next page
        activity.onPageFinished(isResultsPage(StringHelper.protect(url)), isGalleryPage(url));
    }

    /**
     * Note : this method is called by a non-UI thread
     */
    @Override
    public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                      @NonNull WebResourceRequest request) {
        String url = request.getUrl().toString();

        // Data fetched with POST is out of scope of analysis and adblock
        if (!request.getMethod().equalsIgnoreCase("get")) {
            Timber.v("[%s] ignored by interceptor; method = %s", url, request.getMethod());
            return sendRequest(request);
        }
        if (request.isForMainFrame())
            mainPageUrl = url;
        WebResourceResponse result = shouldInterceptRequestInternal(url, request.getRequestHeaders());
        if (result != null) return result;
        else return sendRequest(request);
    }

    /**
     * Determines if the page at the given URL is to be processed
     *
     * @param url     Called URL
     * @param headers Request headers
     * @return Processed response if the page has been processed;
     * null if vanilla processing should happen instead
     */
    @Nullable
    private WebResourceResponse shouldInterceptRequestInternal(@NonNull final String url,
                                                               @Nullable final Map<String, String> headers) {
        if ((Preferences.isBrowserAugmented() && adBlocker.isBlocked(url, headers)) || !url.startsWith("http")) {
            return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(NOTHING));
        } else if (isMarkDownloaded() && url.contains("hentoid-checkmark")) {
            return new WebResourceResponse(ImageHelper.MIME_IMAGE_WEBP, "utf-8", new ByteArrayInputStream(CHECKMARK));
        } else if (isMarkMerged() && url.contains("hentoid-mergedmark")) {
            return new WebResourceResponse(ImageHelper.MIME_IMAGE_WEBP, "utf-8", new ByteArrayInputStream(MERGED_MARK));
        } else if (url.contains("hentoid-blockedmark")) {
            return new WebResourceResponse(ImageHelper.MIME_IMAGE_WEBP, "utf-8", new ByteArrayInputStream(BLOCKED_MARK));
        } else {
            if (isGalleryPage(url)) return parseResponse(url, headers, true, false);
            else if (BuildConfig.DEBUG) Timber.v("WebView : not gallery %s", url);

            // If we're here to remove "dirty elements" or mark downloaded books, we only do it
            // on HTML resources (URLs without extension) from the source's main domain
            if ((removableElements != null || jsContentBlacklist != null || isMarkDownloaded() || isMarkMerged() || isMarkBlockedTags() || !activity.getCustomCss().isEmpty())
                    && (HttpHelper.getExtensionFromUri(url).isEmpty() || HttpHelper.getExtensionFromUri(url).equalsIgnoreCase("html"))) {
                String host = Uri.parse(url).getHost();
                if (host != null && !isHostNotInRestrictedDomains(host))
                    return parseResponse(url, headers, false, false);
            }

            return null;
        }
    }

    WebResourceResponse sendRequest(@NonNull WebResourceRequest request) {
        if (dnsOverHttpsEnabled.get()) {
            // Query resource using OkHttp
            String urlStr = request.getUrl().toString();
            List<Pair<String, String>> requestHeadersList = HttpHelper.webkitRequestHeadersToOkHttpHeaders(request.getRequestHeaders(), urlStr);
            try {
                Response response = HttpHelper.getOnlineResource(urlStr, requestHeadersList, site.useMobileAgent(), site.useHentoidAgent(), site.useWebviewAgent());

                // Scram if the response is a redirection or an error
                if (response.code() >= 300) return null;

                ResponseBody body = response.body();
                if (null == body) throw new IOException("Empty body");
                return HttpHelper.okHttpResponseToWebkitResponse(response, body.byteStream());
            } catch (IOException | IllegalStateException e) {
                Timber.i(e);
            }
        }
        return null;
    }

    /**
     * Load the given URL using a separate thread
     *
     * @param url URL to load
     */
    void browserLoadAsync(@NonNull String url) {
        compositeDisposable.add(
                Completable.fromRunnable(() -> activity.loadUrl(url))
                        .subscribeOn(AndroidSchedulers.mainThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(() -> {
                        }, Timber::e)
        );
    }

    @SuppressWarnings("SameParameterValue")
    protected Optional<WebResourceResponse> parseResponseOptional(@NonNull String urlStr, @Nullable Map<String, String> requestHeaders, boolean analyzeForDownload, boolean quickDownload) {
        WebResourceResponse result = parseResponse(urlStr, requestHeaders, analyzeForDownload, quickDownload);

        if (null == result) return Optional.empty();
        else return Optional.of(result);
    }

    /**
     * Process the webpage at the given URL
     *
     * @param urlStr             URL of the page to process
     * @param requestHeaders     Request headers to use
     * @param analyzeForDownload True if the page has to be analyzed for potential downloads;
     *                           false if only ad removal should happen
     * @param quickDownload      True if the present call has been triggered by a quick download action
     * @return Processed response if the page has been actually processed;
     * null if vanilla processing should happen instead
     */
    @SuppressLint("NewApi")
    protected WebResourceResponse parseResponse(@NonNull String urlStr, @Nullable Map<String, String> requestHeaders, boolean analyzeForDownload, boolean quickDownload) {
        Helper.assertNonUiThread();

        if (BuildConfig.DEBUG)
            Timber.v("WebView : parseResponse %s %s", analyzeForDownload ? "DL" : "", urlStr);

        // If we're here for dirty content removal only, and can't use the OKHTTP request, it's no use going further
        if (!analyzeForDownload && !canUseSingleOkHttpRequest()) return null;

        if (analyzeForDownload) activity.onGalleryPageStarted();

        List<Pair<String, String>> requestHeadersList = HttpHelper.webkitRequestHeadersToOkHttpHeaders(requestHeaders, urlStr);

        Response response = null;
        try {
            // Query resource here, using OkHttp
            response = HttpHelper.getOnlineResourceFast(urlStr, requestHeadersList, site.useMobileAgent(), site.useHentoidAgent(), site.useWebviewAgent(), false);
        } catch (MalformedURLException e) {
            Timber.e(e, "Malformed URL : %s", urlStr);
        } catch (SocketTimeoutException e) {
            // If fast method occurred timeout, reconnect with non-fast method
            Timber.d("Timeout; Reconnect with non-fast method : %s", urlStr);
            try {
                response = HttpHelper.getOnlineResource(urlStr, requestHeadersList, site.useMobileAgent(), site.useHentoidAgent(), site.useWebviewAgent());
            } catch (IOException | IllegalStateException ex) {
                Timber.e(ex);
            }
        } catch (IOException | IllegalStateException e) {
            Timber.e(e);
        }

        if (response != null) {
            try {
                // Scram if the response is an error
                if (response.code() >= 400) return null;

                // Handle redirection and force the browser to reload to be able to process the page
                // NB1 : shouldInterceptRequest doesn't trigger on redirects
                // NB2 : parsing alone won't cut it because the adblocker needs the new content on the new URL
                if (response.code() >= 300) {
                    String targetUrl = StringHelper.protect(response.header("location"));
                    if (targetUrl.isEmpty())
                        targetUrl = StringHelper.protect(response.header("Location"));
                    if (BuildConfig.DEBUG)
                        Timber.v("WebView : redirection from %s to %s", urlStr, targetUrl);
                    if (!targetUrl.isEmpty())
                        browserLoadAsync(HttpHelper.fixUrl(targetUrl, site.getUrl()));
                    return null;
                }

                // Scram if the response is something else than html
                String rawContentType = response.header(HEADER_CONTENT_TYPE, "");
                if (null == rawContentType) return null;

                Pair<String, String> contentType = HttpHelper.cleanContentType(rawContentType);
                if (!contentType.first.isEmpty() && !contentType.first.equals("text/html"))
                    return null;

                // Scram if the response is empty
                ResponseBody body = response.body();
                if (null == body) throw new IOException("Empty body");

                InputStream parserStream;
                WebResourceResponse result;
                if (canUseSingleOkHttpRequest()) {
                    InputStream browserStream;
                    if (analyzeForDownload) {
                        // Response body bytestream needs to be duplicated
                        // because Jsoup closes it, which makes it unavailable for the WebView to use
                        List<InputStream> is = Helper.duplicateInputStream(body.byteStream(), 2);
                        parserStream = is.get(0);
                        browserStream = is.get(1);
                    } else {
                        parserStream = null;
                        browserStream = body.byteStream();
                    }

                    // Remove dirty elements from HTML resources
                    String customCss = activity.getCustomCss();
                    if (removableElements != null || jsContentBlacklist != null || isMarkDownloaded() || isMarkMerged() || isMarkBlockedTags() || !customCss.isEmpty()) {
                        browserStream = ProcessHtml(browserStream, urlStr, customCss, removableElements, jsContentBlacklist, activity.getAllSiteUrls(), activity.getAllMergedBooksUrls(), activity.getPrefBlockedTags());
                        if (null == browserStream) return null;
                    }

                    // Convert OkHttp response to the expected format
                    result = HttpHelper.okHttpResponseToWebkitResponse(response, browserStream);

                    // Manually set cookie if present in response header (has to be set manually because we're using OkHttp right now, not the webview)
                    if (result.getResponseHeaders().containsKey("set-cookie") || result.getResponseHeaders().containsKey("Set-Cookie")) {
                        String cookiesStr = result.getResponseHeaders().get("set-cookie");
                        if (null == cookiesStr)
                            cookiesStr = result.getResponseHeaders().get("Set-Cookie");
                        if (cookiesStr != null) {
                            // Set-cookie might contain multiple cookies to set separated by a line feed (see HttpHelper.getValuesSeparatorFromHttpHeader)
                            String[] cookieParts = cookiesStr.split("\n");
                            for (String cookie : cookieParts)
                                if (!cookie.isEmpty())
                                    HttpHelper.setCookies(urlStr, cookie);
                        }
                    }
                } else {
                    parserStream = body.byteStream();
                    result = null; // Default webview behaviour
                }

                if (analyzeForDownload) {
                    compositeDisposable.add(
                            Single.fromCallable(() -> htmlAdapter.fromInputStream(parserStream, new URL(urlStr)).toContent(urlStr))
                                    .subscribeOn(Schedulers.computation())
                                    .observeOn(Schedulers.computation())
                                    .map(content -> processContent(content, urlStr, quickDownload))
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(
                                            content2 -> activity.onResultReady(content2, quickDownload),
                                            throwable -> {
                                                Timber.e(throwable, "Error parsing content.");
                                                isHtmlLoaded.set(true);
                                                activity.onResultFailed();
                                            })
                    );
                } else {
                    isHtmlLoaded.set(true);
                    activity.onNoResult();
                }

                return result;
            } catch (IOException | IllegalStateException e) {
                Timber.e(e);
            }
        }
        return null;
    }

    /**
     * Process Content parsed from a webpage
     *
     * @param content       Content to be processed
     * @param quickDownload True if the present call has been triggered by a quick download action
     */
    protected Content processContent(@Nonnull Content content, @NonNull String url, boolean quickDownload) {
        if (content.getStatus() != null && content.getStatus().equals(StatusContent.IGNORED))
            return content;

        // Save useful download params for future use during download
        Map<String, String> params;
        if (content.getDownloadParams().length() > 2) // Params already contain values
            params = ContentHelper.parseDownloadParams(content.getDownloadParams());
        else params = new HashMap<>();

        params.put(HttpHelper.HEADER_COOKIE_KEY, HttpHelper.getCookies(url));
        params.put(HttpHelper.HEADER_REFERER_KEY, content.getSite().getUrl());

        content.setDownloadParams(JsonHelper.serializeToJson(params, JsonHelper.MAP_STRINGS));
        isHtmlLoaded.set(true);

        return content;
    }

    /**
     * Indicate whether the current webpage is still loading or not
     *
     * @return True if current webpage is being loaded; false if not
     */
    boolean isLoading() {
        return isPageLoading.get();
    }

    boolean isMarkDownloaded() {
        return markDownloaded.get();
    }

    void setMarkDownloaded(boolean value) {
        markDownloaded.set(value);
    }

    boolean isMarkMerged() {
        return markMerged.get();
    }

    void setMarkMerged(boolean value) {
        markMerged.set(value);
    }

    boolean isMarkBlockedTags() {
        return markBlockedTags.get();
    }

    void setMarkBlockedTags(boolean value) {
        markBlockedTags.set(value);
    }

    void setDnsOverHttpsEnabled(boolean value) {
        dnsOverHttpsEnabled.set(value);
    }

    /**
     * Process the given HTML document contained in the given stream :
     * - If set, remove nodes using the given list of CSS selectors to identify them
     * - If set, mark book covers or links matching the given list of Urls
     *
     * @param stream             Stream containing the HTML document to process; will be closed during the process
     * @param baseUri            Base URI of the document
     * @param removableElements  CSS selectors of the nodes to remove
     * @param jsContentBlacklist Blacklisted elements to detect script tags to remove
     * @param siteUrls           Urls of the covers or links to visually mark as downloaded
     * @param mergedSiteUrls     Urls of the covers or links to visually mark as merged
     * @param blockedTags        Tags of the preference-browser-blocked tag option to visually mark as blocked
     * @return Stream containing the HTML document stripped from the elements to remove
     */
    @Nullable
    private InputStream ProcessHtml(
            @NonNull InputStream stream,
            @NonNull String baseUri,
            @Nullable String customCss,
            @Nullable List<String> removableElements,
            @Nullable List<String> jsContentBlacklist,
            @Nullable List<String> siteUrls,
            @Nullable List<String> mergedSiteUrls,
            @Nullable List<String> blockedTags) {
        try {
            Document doc = Jsoup.parse(stream, null, baseUri);

            // Add custom inline CSS to the main page only
            if (customCss != null && baseUri.equals(mainPageUrl))
                doc.head().appendElement("style").attr("type", "text/css").appendText(customCss);

            // Remove ad spaces
            if (removableElements != null)
                for (String s : removableElements)
                    for (Element e : doc.select(s)) {
                        Timber.d("[%s] Removing node %s", baseUri, e.toString());
                        e.remove();
                    }

            // Remove scripts
            if (jsContentBlacklist != null) {
                for (Element e : doc.select("script")) {
                    String scriptContent = e.toString().toLowerCase();
                    for (String s : jsContentBlacklist) {
                        if (scriptContent.contains(s.toLowerCase())) {
                            Timber.d("[%s] Removing script %s", baseUri, e.toString());
                            e.remove();
                            break;
                        }
                    }
                }
            }

            // Mark downloaded books and merged books
            if (siteUrls != null && mergedSiteUrls != null && (!siteUrls.isEmpty() || !mergedSiteUrls.isEmpty())) {
                // Format elements
                Elements plainLinks = doc.select("a");
                Elements linkedImages = doc.select("a img");

                // Key = simplified HREF
                // Value.left = plain link ("a")
                // Value.right = corresponding linked images ("a img"), if any
                Map<String, Pair<Element, Element>> elements = new HashMap<>();

                for (Element link : plainLinks) {
                    if (!site.getBookCardExcludedParentClasses().isEmpty()) {
                        boolean isForbidden = Stream.of(link.parents()).anyMatch(e -> containsForbiddenClass(site, e.classNames()));
                        if (isForbidden) continue;
                    }
                    String aHref = HttpHelper.simplifyUrl(link.attr("href"));
                    if (!aHref.isEmpty() && !elements.containsKey(aHref)) // We only process the first match - usually the cover
                        elements.put(aHref, new Pair<>(link, null));
                }

                for (Element linkedImage : linkedImages) {
                    Element parent = linkedImage.parent();
                    while (parent != null && !parent.is("a")) parent = parent.parent();
                    if (null == parent) break;

                    if (!site.getBookCardExcludedParentClasses().isEmpty()) {
                        boolean isForbidden = Stream.of(parent.parents()).anyMatch(e -> containsForbiddenClass(site, e.classNames()));
                        if (isForbidden) continue;
                    }

                    String aHref = HttpHelper.simplifyUrl(parent.attr("href"));
                    Pair<Element, Element> elt = elements.get(aHref);
                    if (elt != null && null == elt.second) // We only process the first match - usually the cover
                        elements.put(aHref, new Pair<>(elt.first, linkedImage));
                }

                for (Map.Entry<String, Pair<Element, Element>> entry : elements.entrySet()) {
                    for (String url : siteUrls) {
                        if (entry.getKey().endsWith(url)) {
                            Element markedElement = entry.getValue().second; // Linked images have priority over plain links
                            if (markedElement != null) { // Mark <site.bookCardDepth> levels above the image
                                Element imgParent = markedElement.parent();
                                for (int i = 0; i < site.getBookCardDepth() - 1; i++)
                                    if (imgParent != null) imgParent = imgParent.parent();
                                if (imgParent != null) markedElement = imgParent;
                            } else { // Mark plain link
                                markedElement = entry.getValue().first;
                            }
                            markedElement.addClass("watermarked");
                            break;
                        }
                    }
                    for (String url : mergedSiteUrls) {
                        if (entry.getKey().endsWith(url)) {
                            Element markedElement = entry.getValue().second; // Linked images have priority over plain links
                            if (markedElement != null) { // // Mark <site.bookCardDepth> levels above the image
                                Element imgParent = markedElement.parent();
                                for (int i = 0; i < site.getBookCardDepth() - 1; i++)
                                    if (imgParent != null) imgParent = imgParent.parent();
                                if (imgParent != null) markedElement = imgParent;
                            } else { // Mark plain link
                                markedElement = entry.getValue().first;
                            }
                            markedElement.addClass("watermarked-merged");
                            break;
                        }
                    }
                }
            }

            // Mark books with blocked tags
            if (blockedTags != null && !blockedTags.isEmpty()) {
                Elements plainLinks = doc.select("a");

                // Key= plain link ("a")
                // Value = simplified HREF
                Map<Element, String> elements = new HashMap<>();

                for (Element link : plainLinks) {
                    if (!site.getBookCardExcludedParentClasses().isEmpty()) {
                        boolean isForbidden = Stream.of(link.parents()).anyMatch(e -> containsForbiddenClass(site, e.classNames()));
                        if (isForbidden) continue;
                    }
                    String aHref = HttpHelper.simplifyUrl(link.attr("href"));
                    elements.put(link, aHref);
                }

                for (Map.Entry<Element, String> entry : elements.entrySet()) {
                    if (site.getGalleryHeight() != -1) {
                        for (String blockedTag : blockedTags) {
                            if (entry.getValue().contains("/tag/") || entry.getValue().contains("/category/")) {
                                String tag = null;
                                if (entry.getKey().childNodeSize() != 0)
                                    tag = entry.getKey().childNode(0).toString();
                                if (tag == null)
                                    break;
                                if (blockedTag.equalsIgnoreCase(tag) || StringHelper.isPresentAsWord(blockedTag, tag)) {
                                    Element imgParent = entry.getKey();
                                    for (int i = 0; i <= site.getGalleryHeight(); i++) {
                                        if (imgParent.parent() != null)
                                            imgParent = imgParent.parent();
                                    }
                                    Elements imgs = imgParent.getAllElements().select("img");
                                    for (Element img : imgs) {
                                        if (img.parent() != null)
                                            img.parent().addClass("watermarked-blocked");
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            if (customHtmlRewriter != null) customHtmlRewriter.accept(doc);

            return new ByteArrayInputStream(doc.toString().getBytes(StandardCharsets.UTF_8));
        } catch (
                IOException e) {
            Timber.e(e);
            return null;
        }
    }

    private boolean containsForbiddenClass(@NonNull Site s, @NonNull Set<String> classNames) {
        Set<String> forbiddenElements = s.getBookCardExcludedParentClasses();
        return Stream.of(classNames).anyMatch(forbiddenElements::contains);
    }

    interface CustomWebActivity {
        // ACTIONS
        void loadUrl(@NonNull final String url);

        // CALLBACKS
        void onPageStarted(
                String url,
                boolean isGalleryPage,
                boolean isHtmlLoaded,
                boolean isBookmarkable,
                List<String> jsStartupScripts);

        void onPageFinished(boolean isResultsPage, boolean isGalleryPage);

        void onGalleryPageStarted();

        /**
         * Callback when the page has been successfuly parsed into a Content
         *
         * @param results       Parsed Content
         * @param quickDownload True if the action has been triggered by a quick download action
         */
        void onResultReady(@NonNull Content results, boolean quickDownload);

        /**
         * Callback when the page does not have any Content to parse
         */
        void onNoResult();

        /**
         * Callback when the page should have been parsed into a Content, but the parsing failed
         */
        void onResultFailed();

        // GETTERS
        List<String> getAllSiteUrls();

        List<String> getAllMergedBooksUrls();

        List<String> getPrefBlockedTags();

        String getCustomCss();
    }
}