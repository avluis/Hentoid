package me.devsaki.hentoid.activities.sources;

import static me.devsaki.hentoid.util.network.HttpHelper.HEADER_CONTENT_TYPE;
import static me.devsaki.hentoid.util.network.HttpHelper.getExtensionFromUri;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.util.Pair;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.function.BiFunction;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.ImageHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.ToastHelper;
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
    private final byte[] nothing = "".getBytes();
    // Pre-built object to represent WEBP binary data for the checkmark icon used to mark downloaded boks
    // (will be fed directly to the browser when the resourcei is requested)
    private final byte[] checkmark;

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

    protected final AdBlocker adBlocker;
    private final AtomicBoolean markDownloaded = new AtomicBoolean(Preferences.isBrowserMarkDownloaded());

    // Disposable to be used for punctual search
    private Disposable disposable;


    // List of "dirty" elements (CSS selector) to be cleaned before displaying the page
    private List<String> dirtyElements;


    CustomWebViewClient(Site site, String[] galleryUrl, CustomWebActivity activity) {
        this.site = site;
        this.activity = activity;

        Class<? extends ContentParser> c = ContentParserFactory.getInstance().getContentParserClass(site);
        final Jspoon jspoon = Jspoon.create();
        htmlAdapter = jspoon.adapter(c); // Unchecked but alright

        adBlocker = new AdBlocker(site);

        for (String s : galleryUrl) galleryUrlPattern.add(Pattern.compile(s));

        checkmark = ImageHelper.BitmapToWebp(
                ImageHelper.tintBitmap(
                        ImageHelper.getBitmapFromVectorDrawable(HentoidApp.getInstance(), R.drawable.ic_check),
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
    protected void addDirtyElements(String... elements) {
        if (null == dirtyElements) dirtyElements = new ArrayList<>();
        Collections.addAll(dirtyElements, elements);
    }


    void setResultsUrlPatterns(String... patterns) {
        for (String s : patterns) resultsUrlPattern.add(Pattern.compile(s));
    }

    void setResultUrlRewriter(@NonNull BiFunction<Uri, Integer, String> rewriter) {
        resultsUrlRewriter = rewriter;
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
        if (adBlocker.isBlocked(url) || !url.startsWith("http")) return true;

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
                        ToastHelper.toast("Downloading torrent failed : " + e.getMessage());
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
        File file = new File(cacheDir.getAbsolutePath() + File.separator + new Random().nextInt(10000) + "." + getExtensionFromUri(url));
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
        activity.onPageStarted(url, isGalleryPage(url), isHtmlLoaded.get(), true);
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
        if (adBlocker.isBlocked(url) || !url.startsWith("http")) {
            return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(nothing));
        } else if (isMarkDownloaded() && url.contains("hentoid-checkmark")) {
            return new WebResourceResponse(ImageHelper.MIME_IMAGE_WEBP, "utf-8", new ByteArrayInputStream(checkmark));
        } else {
            if (isGalleryPage(url)) return parseResponse(url, headers, true, false);
            else if (BuildConfig.DEBUG) Timber.v("WebView : not gallery %s", url);

            // If we're here to remove "dirty elements" or mark downloaded books, we only do it
            // on HTML resources (URLs without extension) from the source's main domain
            if ((dirtyElements != null || isMarkDownloaded())
                    && HttpHelper.getExtensionFromUri(url).isEmpty()) {
                String host = Uri.parse(url).getHost();
                if (host != null && !isHostNotInRestrictedDomains(host))
                    return parseResponse(url, headers, false, false);
            }

            return null;
        }
    }

    WebResourceResponse sendRequest(@NonNull WebResourceRequest request) {
        if (true) { // TODO make dynamic
            // Query resource using OkHttp
            String urlStr = request.getUrl().toString();
            List<Pair<String, String>> requestHeadersList = HttpHelper.webkitRequestHeadersToOkHttpHeaders(request.getRequestHeaders(), urlStr);
            try {
                Response response = HttpHelper.getOnlineResource(urlStr, requestHeadersList, site.useMobileAgent(), site.useHentoidAgent(), site.useWebviewAgent());
                ResponseBody body = response.body();
                if (null == body) throw new IOException("Empty body");
                return HttpHelper.okHttpResponseToWebkitResponse(response, body.byteStream());
            } catch (IOException e) {
                Timber.i(e);
            }
        }
        return null;
    }

    /**
     * Process the given webpage in a background thread (used by quick download)
     *
     * @param urlStr URL of the page to parse
     */
    void parseResponseAsync(@NonNull String urlStr) {
        compositeDisposable.add(
                Completable.fromCallable(() -> parseResponse(urlStr, null, true, true))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(() -> {
                        }, Timber::e)
        );
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

        if (BuildConfig.DEBUG) Timber.v("WebView : parseResponse %s", urlStr);

        // If we're here for dirty content removal only, and can't use the OKHTTP request, it's no use going further
        if (!analyzeForDownload && !canUseSingleOkHttpRequest()) return null;

        if (analyzeForDownload) activity.onGalleryPageStarted();

        List<Pair<String, String>> requestHeadersList = HttpHelper.webkitRequestHeadersToOkHttpHeaders(requestHeaders, urlStr);

        try {
            // Query resource here, using OkHttp
            Response response = HttpHelper.getOnlineResource(urlStr, requestHeadersList, site.useMobileAgent(), site.useHentoidAgent(), site.useWebviewAgent());

            // Scram if the response is a redirection or an error
            if (response.code() >= 300) return null;

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
                if (dirtyElements != null || isMarkDownloaded()) {
                    browserStream = ProcessHtml(browserStream, urlStr, dirtyElements, activity.getAllSiteUrls());
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
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(
                                        content -> processContent(content, urlStr, quickDownload),
                                        throwable -> {
                                            Timber.e(throwable, "Error parsing content.");
                                            isHtmlLoaded.set(true);
                                            activity.onResultFailed();
                                        })
                );
            } else {
                isHtmlLoaded.set(true);
            }

            return result;
        } catch (MalformedURLException e) {
            Timber.e(e, "Malformed URL : %s", urlStr);
        } catch (IOException e) {
            Timber.e(e);
        }
        return null;
    }

    /**
     * Process Content parsed from a webpage
     *
     * @param content       Content to be processed
     * @param quickDownload True if the present call has been triggered by a quick download action
     */
    protected void processContent(@Nonnull Content content, @NonNull String url, boolean quickDownload) {
        if (content.getStatus() != null && content.getStatus().equals(StatusContent.IGNORED))
            return;

        // Save useful download params for future use during download
        Map<String, String> params;
        if (content.getDownloadParams().length() > 2) // Params already contain values
            params = ContentHelper.parseDownloadParams(content.getDownloadParams());
        else params = new HashMap<>();

        params.put(HttpHelper.HEADER_COOKIE_KEY, HttpHelper.getCookies(url));
        params.put(HttpHelper.HEADER_REFERER_KEY, content.getSite().getUrl());

        content.setDownloadParams(JsonHelper.serializeToJson(params, JsonHelper.MAP_STRINGS));
        isHtmlLoaded.set(true);

        activity.onResultReady(content, quickDownload);
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

    /**
     * Process the given HTML document contained in the given stream :
     * - If set, remove nodes using the given list of CSS selectors to identify them
     * - If set, mark book covers or links matching the given list of Urls
     *
     * @param stream        Stream containing the HTML document to process
     * @param baseUri       Base URI if the document
     * @param dirtyElements CSS selectors of the nodes to remove
     * @param siteUrls      Urls of the covers or links to mark
     * @return Stream containing the HTML document stripped from the elements to remove
     */
    @Nullable
    private InputStream ProcessHtml(
            @NonNull InputStream stream,
            @NonNull String baseUri,
            @Nullable List<String> dirtyElements,
            @Nullable List<String> siteUrls) {
        try {
            Document doc = Jsoup.parse(stream, null, baseUri);

            if (dirtyElements != null)
                for (String s : dirtyElements)
                    for (Element e : doc.select(s)) {
                        Timber.d("[%s] Removing node %s", baseUri, e.toString());
                        e.remove();
                    }

            if (siteUrls != null && !siteUrls.isEmpty()) {
                // Add custom inline CSS to the main page only
                if (!isHtmlLoaded.get())
                    doc.head().appendElement("style").attr("type", "text/css").appendText(activity.getCustomCss());
                // Format elements
                Elements links = doc.select("a");
                Set<String> found = new HashSet<>();
                for (Element link : links) {
                    String aHref = link.attr("href").replaceAll("\\p{Punct}", ".");
                    if (aHref.length() < 2) continue;
                    if (aHref.endsWith(".")) aHref = aHref.substring(0, aHref.length() - 1);
                    for (String url : siteUrls) {
                        if (aHref.endsWith(url) && !found.contains(url)) {
                            Element markedElement = link;
                            Element img = link.select("img").first();
                            if (img != null) { // Mark two levels above the image
                                Element imgParent = img.parent();
                                if (imgParent != null) imgParent = imgParent.parent();
                                if (imgParent != null) markedElement = imgParent;
                            }
                            markedElement.addClass("watermarked");
                            found.add(url); // We only process the first match - usually the cover
                            break;
                        }
                    }
                }
            }

            return new ByteArrayInputStream(doc.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Timber.e(e);
            return null;
        }
    }

    interface CustomWebActivity {
        void onPageStarted(String url, boolean isGalleryPage, boolean isHtmlLoaded, boolean isBookmarkable);

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
         * Callback when the page should have been parsed into a Content, but the parsing failed
         */
        void onResultFailed();

        List<String> getAllSiteUrls();

        String getCustomCss();
    }
}