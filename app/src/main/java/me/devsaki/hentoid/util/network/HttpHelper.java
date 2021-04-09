package me.devsaki.hentoid.util.network;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Pair;
import android.webkit.CookieManager;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;

import androidx.annotation.NonNull;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import me.devsaki.hentoid.BuildConfig;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import timber.log.Timber;

/**
 * Helper for HTTP protocol operations
 */
public class HttpHelper {

    public static final int DEFAULT_REQUEST_TIMEOUT = 30000; // 30 seconds

    // Keywords of the HTTP protocol
    public static final String HEADER_ACCEPT_KEY = "accept";
    public static final String HEADER_COOKIE_KEY = "cookie";
    public static final String HEADER_REFERER_KEY = "referer";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_USER_AGENT = "User-Agent";

    public static final Set<String> COOKIES_STANDARD_ATTRS = new HashSet<>();

    // To display sites with desktop layouts
    public static final String DESKTOP_USER_AGENT_PATTERN = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) %s Safari/537.36";

    public static String defaultUserAgent = null;
    public static String defaultChromeAgent = null;
    public static int defaultChromeVersion = -1;


    static {
        // Can't be done on the variable initializer as Set.of is only available since API R
        COOKIES_STANDARD_ATTRS.addAll(Arrays.asList("expires", "max-age", "domain", "path", "secure", "httponly", "samesite"));
    }


    private HttpHelper() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Read an HTML resource from the given URL and retrieve it as a Document
     *
     * @param url URL to read the resource from
     * @return HTML resource read from the given URL represented as a Document
     * @throws IOException in case something bad happens when trying to access the online resource
     */
    @Nullable
    public static Document getOnlineDocument(String url) throws IOException {
        return getOnlineDocument(url, null, true, true);
    }

    /**
     * Read an HTML resource from the given URL, using the given headers and agent and retrieve it as a Document
     *
     * @param url             URL to read the resource from
     * @param headers         Headers to use when building the request
     * @param useHentoidAgent True if the Hentoid User-Agent has to be used; false if a neutral User-Agent has to be used
     * @return HTML resource read from the given URL represented as a Document
     * @throws IOException in case something bad happens when trying to access the online resource
     */
    @Nullable
    public static Document getOnlineDocument(String url, List<Pair<String, String>> headers, boolean useHentoidAgent, boolean useWebviewAgent) throws IOException {
        ResponseBody resource = getOnlineResource(url, headers, true, useHentoidAgent, useWebviewAgent).body();
        if (resource != null) {
            return Jsoup.parse(resource.string());
        }
        return null;
    }

    /**
     * Read a resource from the given URL with HTTP GET, using the given headers and agent
     *
     * @param url             URL to read the resource from
     * @param headers         Headers to use when building the request
     * @param useHentoidAgent True if the Hentoid User-Agent has to be used; false if a neutral User-Agent has to be used
     * @return HTTP response
     * @throws IOException in case something bad happens when trying to access the online resource
     */
    public static Response getOnlineResource(@NonNull String url, @Nullable List<Pair<String, String>> headers, boolean useMobileAgent, boolean useHentoidAgent, boolean useWebviewAgent) throws IOException {
        Request.Builder requestBuilder = buildRequest(url, headers, useMobileAgent, useHentoidAgent, useWebviewAgent);
        Request request = requestBuilder.get().build();
        return OkHttpClientSingleton.getInstance(DEFAULT_REQUEST_TIMEOUT).newCall(request).execute();
    }

    /**
     * Read a resource from the given URL with HTTP POST, using the given headers and agent
     *
     * @param url             URL to read the resource from
     * @param headers         Headers to use when building the request
     * @param useHentoidAgent True if the Hentoid User-Agent has to be used; false if a neutral User-Agent has to be used
     * @param body            Body of the resource to post
     * @return HTTP response
     * @throws IOException in case something bad happens when trying to access the online resource
     */
    public static Response postOnlineResource(
            @NonNull String url,
            @Nullable List<Pair<String, String>> headers,
            boolean useHentoidAgent,
            boolean useWebviewAgent,
            @NonNull final String body,
            @NonNull final String mimeType) throws IOException {
        Request.Builder requestBuilder = buildRequest(url, headers, true, useHentoidAgent, useWebviewAgent);
        Request request = requestBuilder.post(RequestBody.create(body, MediaType.parse(mimeType))).build();
        return OkHttpClientSingleton.getInstance(DEFAULT_REQUEST_TIMEOUT).newCall(request).execute();
    }

    /**
     * Build an HTTP request using the given arguments
     *
     * @param url             URL to read the resource from
     * @param headers         Headers to use when building the request
     * @param useMobileAgent  True if a mobile User-Agent has to be used; false if a desktop User-Agent has to be used
     * @param useHentoidAgent True if the Hentoid User-Agent has to be used; false if a neutral User-Agent has to be used
     * @return HTTP request built with the given arguments
     */
    private static Request.Builder buildRequest(@NonNull String url, @Nullable List<Pair<String, String>> headers, boolean useMobileAgent, boolean useHentoidAgent, boolean useWebviewAgent) {
        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (headers != null)
            for (Pair<String, String> header : headers)
                if (header.second != null)
                    requestBuilder.addHeader(header.first, header.second);

        requestBuilder.header(HEADER_USER_AGENT, useMobileAgent ? getMobileUserAgent(useHentoidAgent, useWebviewAgent) : getDesktopUserAgent(useHentoidAgent, useWebviewAgent));

        return requestBuilder;
    }

    /**
     * Convert the given OkHttp {@link Response} into a {@link WebResourceResponse}
     *
     * @param resp OkHttp {@link Response}
     * @return The {@link WebResourceResponse} converted from the given OkHttp {@link Response}
     */
    public static WebResourceResponse okHttpResponseToWebResourceResponse(@NonNull final Response resp, @NonNull final InputStream is) {
        final String contentTypeValue = resp.header(HEADER_CONTENT_TYPE);

        WebResourceResponse result;
        Map<String, String> responseHeaders = okHttpHeadersToWebResourceHeaders(resp.headers().toMultimap());
        String message = resp.message();
        if (message.trim().isEmpty()) message = "None";
        if (contentTypeValue != null) {
            Pair<String, String> details = cleanContentType(contentTypeValue);
            result = new WebResourceResponse(details.first, details.second, resp.code(), message, responseHeaders, is);
        } else {
            result = new WebResourceResponse("application/octet-stream", null, resp.code(), message, responseHeaders, is);
        }

        return result;
    }

    /**
     * "Flatten"" HTTP headers from an OkHttp-compatible structure to a Webkit-compatible structure
     * to be used with {@link android.webkit.WebResourceRequest} or {@link android.webkit.WebResourceResponse}
     *
     * @param okHttpHeaders HTTP Headers structured according to the convention used by OkHttp
     * @return "Flattened" HTTP headers structured according to the convention used by Webkit
     */
    private static Map<String, String> okHttpHeadersToWebResourceHeaders(@NonNull final Map<String, List<String>> okHttpHeaders) {
        Map<String, String> result = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : okHttpHeaders.entrySet()) {
            List<String> values = entry.getValue();
            if (values != null)
                result.put(entry.getKey(), TextUtils.join(getValuesSeparatorFromHttpHeader(entry.getKey()), values));
        }

        return result;
    }

    /**
     * Convert HTTP headers from a Webkit-compatible structure to an OkHttp-compatible structure
     *
     * @param webResourceHeaders HTTP Headers structured according to the convention used by Webkit
     * @param url                Corresponding URL
     * @return HTTP Headers structured according to the convention used by OkHttp
     */
    public static List<Pair<String, String>> webResourceHeadersToOkHttpHeaders(@Nullable final Map<String, String> webResourceHeaders, @Nullable String url) {
        List<Pair<String, String>> result = new ArrayList<>();

        if (webResourceHeaders != null)
            for (Map.Entry<String, String> entry : webResourceHeaders.entrySet())
                result.add(new Pair<>(entry.getKey(), entry.getValue()));

        String cookie = CookieManager.getInstance().getCookie(url);
        if (cookie != null) {
            cookie = HttpHelper.stripParams(cookie);
            result.add(new Pair<>(HttpHelper.HEADER_COOKIE_KEY, cookie));
        }

        return result;
    }

    /**
     * Get the values separator used inside the given HTTP header key
     *
     * @param header key of the HTTP header
     * @return Values separator used inside the given HTTP header key
     */
    private static String getValuesSeparatorFromHttpHeader(@NonNull final String header) {

        String separator = ", "; // HTTP spec

        if (header.equalsIgnoreCase("set-cookie") || header.equalsIgnoreCase("www-authenticate") || header.equalsIgnoreCase("proxy-authenticate"))
            separator = "\n"; // Special case : commas may appear in these headers => use a newline delimiter

        return separator;
    }

    /**
     * Process the value of a "Content-Type" HTTP header and return its parts
     *
     * @param rawContentType Value of the "Content-type" header
     * @return Pair containing
     * - The content-type (MIME-type) as its first value
     * - The charset, if it has been transmitted, as its second value (may be null)
     */
    public static Pair<String, String> cleanContentType(@NonNull String rawContentType) {
        if (rawContentType.contains("charset=")) {
            final String[] contentTypeAndEncoding = rawContentType.replace("; ", ";").split(";");
            final String contentType = contentTypeAndEncoding[0];
            final String charset = contentTypeAndEncoding[1].split("=")[1];
            return new Pair<>(contentType, charset);
        } else return new Pair<>(rawContentType, null);
    }

    /**
     * Return the extension of the file located at the given URI, without the leading '.'
     *
     * @param uri Location of the file
     * @return Extension of the file located at the given URI, without the leading '.'
     */
    public static String getExtensionFromUri(String uri) {
        UriParts parts = new UriParts(uri);
        return parts.getExtension();
    }

    /**
     * Extract the domain from the given URI
     *
     * @param uriStr URI to parse, in String form
     * @return Domain of the URI; null if no domain found
     */
    public static String getDomainFromUri(@NonNull String uriStr) {
        Uri uri = Uri.parse(uriStr);
        String result = uri.getHost();
        if (result != null && result.startsWith("www")) result = result.substring(3);
        return (null == result) ? "" : result;
    }

    /**
     * Parse the given cookie String
     *
     * @param cookiesStr Cookie string, as set in HTTP headers
     * @return Parsed cookies
     */
    public static Map<String, String> parseCookies(@NonNull String cookiesStr) {
        Map<String, String> result = new HashMap<>();

        String[] cookiesParts = cookiesStr.split(";");
        for (String cookie : cookiesParts) {
            String[] cookieParts = cookie.trim().split("=");
            result.put(cookieParts[0], (1 == cookieParts.length) ? "" : cookieParts[1]);
        }

        return result;
    }

    /**
     * Strip the given cookie string from the standard parameters
     * i.e. only return the cookie values
     *
     * @param cookieStr The cookie as a string, using the format of the 'Set-Cookie' HTTP response header
     * @return Cookie string without the standard parameters
     */
    public static String stripParams(@NonNull String cookieStr) {
        Map<String, String> cookies = parseCookies(cookieStr);
        List<String> namesToSet = new ArrayList<>();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (!COOKIES_STANDARD_ATTRS.contains(entry.getKey().toLowerCase()))
                namesToSet.add(entry.getKey() + "=" + entry.getValue());
        }
        return TextUtils.join("; ", namesToSet);
    }

    /**
     * Set session cookies for the given URL, keeping existing cookies if they are still active
     *
     * @param url       Url to set the cookies for
     * @param cookieStr The cookie as a string, using the format of the 'Set-Cookie' HTTP response header
     */
    public static void setCookies(String url, String cookieStr) {
        /*
        Check if given cookies are already registered

        Rationale : setting any cookie programmatically will set it as a _session_ cookie.
        It's not smart to do that if the very same cookie is already set for a longer lifespan.
         */
        Map<String, String> cookies = parseCookies(cookieStr);
        Map<String, String> names = new HashMap<>();

        List<String> paramsToSet = new ArrayList<>();
        List<String> namesToSet = new ArrayList<>();

        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (COOKIES_STANDARD_ATTRS.contains(entry.getKey().toLowerCase())) {
                if (entry.getValue().isEmpty())
                    paramsToSet.add(entry.getKey());
                else
                    paramsToSet.add(entry.getKey() + "=" + entry.getValue());
            } else names.put(entry.getKey(), entry.getValue());
        }

        CookieManager mgr = CookieManager.getInstance();
        String existingCookiesStr = mgr.getCookie(url);
        if (existingCookiesStr != null) {
            Map<String, String> existingCookies = parseCookies(existingCookiesStr);
            for (Map.Entry<String, String> entry : names.entrySet()) {
                String key = entry.getKey();
                String value = (null == entry.getValue()) ? "" : entry.getValue();
                if (!existingCookies.containsKey(key)) namesToSet.add(key + "=" + value);
            }
        } else {
            for (Map.Entry<String, String> name : names.entrySet())
                namesToSet.add(name.getKey() + "=" + name.getValue());
        }

        StringBuilder cookieStrToSet = new StringBuilder();

        cookieStrToSet.append(TextUtils.join("; ", paramsToSet));
        for (String name : namesToSet) cookieStrToSet.append("; ").append(name);

        mgr.setCookie(url, cookieStrToSet.toString());
        Timber.v("Setting cookie for %s : %s", url, cookieStrToSet.toString());

        mgr.flush();
    }

    /**
     * Fix the given URL if it is incomplete, using the provided base URL
     * If not given, the method assumes the protocol is HTTPS
     * e.g. fixUrl("images","http://abc.com") gives "http://abc.com/images"
     *
     * @param url     URL to fix
     * @param baseUrl Base URL to use
     * @return Fixed URL
     */
    public static String fixUrl(final String url, @NonNull final String baseUrl) {
        if (null == url || url.isEmpty()) return "";
        if (url.startsWith("//")) return "https:" + url;

        if (!url.startsWith("http")) {
            String sourceUrl = baseUrl;
            if (sourceUrl.endsWith("/")) sourceUrl = sourceUrl.substring(0, sourceUrl.length() - 1);

            if (url.startsWith("/")) return sourceUrl + url;
            else return sourceUrl + "/" + url;
        } else return url;
    }

    /**
     * Parse the parameters of the given Uri into a map
     *
     * @param uri Uri to parse the paramaters from
     * @return Parsed parameters, where each key is the parameters name and each corresponding value their respective value
     */
    public static Map<String, String> parseParameters(@NonNull final Uri uri) {
        Map<String, String> result = new HashMap<>();

        Set<String> keys = uri.getQueryParameterNames();
        for (String k : keys)
            result.put(k, uri.getQueryParameter(k));

        return result;
    }

    // TODO doc
    public static String getCookies(@NonNull String url) {
        String result = CookieManager.getInstance().getCookie(url);
        if (result != null) return HttpHelper.stripParams(result);
        else return "";
    }

    // TODO doc
    public static String getCookies(@NonNull String url, @Nullable List<Pair<String, String>> headers, boolean useMobileAgent, boolean useHentoidAgent, boolean useWebviewAgent) {
        String result = getCookies(url);
        if (result != null) return result;
        else return peekCookies(url, headers, useMobileAgent, useHentoidAgent, useWebviewAgent);
    }

    /**
     * Get cookie headers set by the page at the given URL
     *
     * @param url Url to peek cookies from
     * @return Raw cookies string
     */
    public static String peekCookies(@NonNull final String url) {
        return peekCookies(url, null, true, false, true);
    }

    // TODO doc
    public static String peekCookies(@NonNull String url, @Nullable List<Pair<String, String>> headers, boolean useMobileAgent, boolean useHentoidAgent, boolean useWebviewAgent) {
        try {
            Response response = getOnlineResource(url, headers, useMobileAgent, useHentoidAgent, useWebviewAgent);
            List<String> cookielist = response.headers().values("Set-Cookie");
            return TextUtils.join("; ", cookielist);
        } catch (IOException e) {
            Timber.e(e);
        }
        return "";
    }

    /**
     * Initialize the app's user agents
     *
     * @param context Context to be used
     */
    public static void initUserAgents(@NonNull final Context context) {
        String chromeString = "Chrome/";
        defaultUserAgent = WebSettings.getDefaultUserAgent(context);
        if (defaultUserAgent.contains(chromeString)) {
            int chromeIndex = defaultUserAgent.indexOf(chromeString);
            int spaceIndex = defaultUserAgent.indexOf(' ', chromeIndex);
            int dotIndex = defaultUserAgent.indexOf('.', chromeIndex);
            String version = defaultUserAgent.substring(chromeIndex + chromeString.length(), dotIndex);
            defaultChromeVersion = Integer.parseInt(version);
            defaultChromeAgent = defaultUserAgent.substring(chromeIndex, spaceIndex);
        }
        Timber.i("defaultUserAgent = %s", defaultUserAgent);
        Timber.i("defaultChromeAgent = %s", defaultChromeAgent);
        Timber.i("defaultChromeVersion = %s", defaultChromeVersion);
    }

    /**
     * Get the app's mobile user agent
     *
     * @param withHentoid True if the Hentoid user-agent has to appear
     * @return The app's mobile user agent
     */
    public static String getMobileUserAgent(boolean withHentoid, boolean withWebview) {
        return getDefaultUserAgent(withHentoid, withWebview);
    }

    /**
     * Get the app's desktop user agent
     *
     * @param withHentoid True if the Hentoid user-agent has to appear
     * @return The app's desktop user agent
     */
    public static String getDesktopUserAgent(boolean withHentoid, boolean withWebview) {
        if (null == defaultChromeAgent)
            throw new RuntimeException("Call initUserAgents first to initialize them !");
        String result = String.format(DESKTOP_USER_AGENT_PATTERN, defaultChromeAgent);
        if (withHentoid) result += " Hentoid/v" + BuildConfig.VERSION_NAME;
        if (!withWebview) result = cleanWebViewAgent(result);
        return result;
    }

    /**
     * Get the app's default user agent
     *
     * @param withHentoid True if the Hentoid user-agent has to appear
     * @return The app's default user agent
     */
    public static String getDefaultUserAgent(boolean withHentoid, boolean withWebview) {
        if (null == defaultUserAgent)
            throw new RuntimeException("Call initUserAgents first to initialize them !");
        String result = defaultUserAgent;
        if (withHentoid) result += " Hentoid/v" + BuildConfig.VERSION_NAME;
        if (!withWebview) result = cleanWebViewAgent(result);
        return result;
    }

    private static String cleanWebViewAgent(@NonNull final String agent) {
        String result = agent;
        int buildIndex = result.indexOf(" Build/");
        if (buildIndex > -1) {
            int closeIndex = result.indexOf(")", buildIndex);
            int separatorIndex = result.indexOf(";", buildIndex);
            int firstIndex = Math.min(closeIndex, separatorIndex);
            result = result.substring(0, buildIndex) + result.substring(firstIndex);
        }
        return result.replace("; wv", "");
    }

    /**
     * Get the app's Chrome version
     *
     * @return The app's Chrome version
     */
    public static int getChromeVersion() {
        if (-1 == defaultChromeVersion)
            throw new RuntimeException("Call initUserAgents first to initialize them !");
        return defaultChromeVersion;
    }

    public static class UriParts {
        private String path;
        private String fileName;
        private String extension;
        private String query;

        public UriParts(String uri) {
            String theUri = uri.toLowerCase();
            String uriNoParams = theUri;

            int paramsIndex = theUri.lastIndexOf('?');
            if (paramsIndex > -1) {
                uriNoParams = theUri.substring(0, paramsIndex);
                query = theUri.substring(paramsIndex + 1);
            } else {
                query = "";
            }

            int pathIndex = uriNoParams.lastIndexOf('/');
            if (pathIndex > -1)
                path = theUri.substring(0, pathIndex);
            else path = theUri;

            int extIndex = uriNoParams.lastIndexOf('.');
            // No extensions detected
            if (extIndex < 0 || extIndex < pathIndex) {
                extension = "";
                fileName = uriNoParams.substring(pathIndex + 1);
            } else {
                extension = uriNoParams.substring(extIndex + 1);
                fileName = uriNoParams.substring(pathIndex + 1, extIndex);
            }
        }

        public String toUri() {
            StringBuilder result = new StringBuilder(path);
            result.append("/").append(fileName);
            if (!extension.isEmpty()) result.append(".").append(extension);
            if (!query.isEmpty()) result.append("?").append(query);
            return result.toString();
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getFileNameNoExt() {
            return fileName;
        }

        public String getExtension() {
            return extension;
        }

        public void setExtension(String extension) {
            this.extension = extension;
        }

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public void setFileNameNoExt(@NonNull final String fileName) {
            this.fileName = fileName;
        }
    }
}
