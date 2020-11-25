package me.devsaki.hentoid.util.network;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Pair;
import android.webkit.CookieManager;
import android.webkit.WebResourceResponse;

import androidx.annotation.NonNull;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import me.devsaki.hentoid.util.Consts;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import timber.log.Timber;

public class HttpHelper {

    private static final int TIMEOUT = 30000; // 30 seconds
    public static final String HEADER_ACCEPT_KEY = "accept";
    public static final String HEADER_COOKIE_KEY = "cookie";
    public static final String HEADER_REFERER_KEY = "referer";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";

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
        return getOnlineDocument(url, null, true);
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
    public static Document getOnlineDocument(String url, List<Pair<String, String>> headers, boolean useHentoidAgent) throws IOException {
        ResponseBody resource = getOnlineResource(url, headers, useHentoidAgent).body();
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
    public static Response getOnlineResource(@NonNull String url, @Nullable List<Pair<String, String>> headers, boolean useHentoidAgent) throws IOException {
        Request.Builder requestBuilder = buildRequest(url, headers, useHentoidAgent);
        Request request = requestBuilder.get().build();
        return OkHttpClientSingleton.getInstance(TIMEOUT).newCall(request).execute();
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
            @NonNull final String body,
            @NonNull final String mimeType) throws IOException {
        Request.Builder requestBuilder = buildRequest(url, headers, useHentoidAgent);
        Request request = requestBuilder.post(RequestBody.create(body, MediaType.parse(mimeType))).build();
        return OkHttpClientSingleton.getInstance(TIMEOUT).newCall(request).execute();
    }

    private static Request.Builder buildRequest(@NonNull String url, @Nullable List<Pair<String, String>> headers, boolean useHentoidAgent) {
        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (headers != null)
            for (Pair<String, String> header : headers)
                if (header.second != null)
                    requestBuilder.addHeader(header.first, header.second);
        requestBuilder.header("User-Agent", useHentoidAgent ? Consts.USER_AGENT : Consts.USER_AGENT_NEUTRAL);
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
        if (contentTypeValue != null) {
            Pair<String, String> details = cleanContentType(contentTypeValue);
            result = new WebResourceResponse(details.first, details.second, is);
        } else {
            result = new WebResourceResponse("application/octet-stream", null, is);
        }

        result.setResponseHeaders(okHttpHeadersToWebResourceHeaders(resp.headers().toMultimap()));

        return result;
    }

    /**
     * "Flatten"" HTTP headers from the OKHTTP convention to be used with {@link android.webkit.WebResourceRequest} or {@link android.webkit.WebResourceResponse}
     *
     * @param okHttpHeaders HTTP Headers orgarnized according to the convention used by OKHTTP
     * @return "Flattened" HTTP headers
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

    // TODO doc
    public static List<Pair<String, String>> webResourceHeadersToOkHttpHeaders(@Nullable final Map<String, String> webResourceHeaders, @Nullable String url, boolean useCookies) {
        List<Pair<String, String>> result = new ArrayList<>();

        if (webResourceHeaders != null)
            for (Map.Entry<String, String> entry : webResourceHeaders.entrySet())
                result.add(new Pair<>(entry.getKey(), entry.getValue()));

        if (useCookies) {
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null)
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
        String theUri = uri.toLowerCase();
        String uriNoParams = theUri;

        int paramsIndex = theUri.lastIndexOf('?');
        if (paramsIndex > -1) uriNoParams = theUri.substring(0, paramsIndex);

        int pathIndex = uriNoParams.lastIndexOf('/');
        int extIndex = uriNoParams.lastIndexOf('.');

        // No extensions detected
        if (extIndex < 0 || extIndex < pathIndex) return "";

        return uriNoParams.substring(extIndex + 1);
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
            if (cookieParts.length > 1)
                result.put(cookieParts[0], cookieParts[1]);
        }

        return result;
    }

    /**
     * Set a new cookie for the domain of the given url, using the CookieManager
     * If the cookie already exists, replace it
     *
     * @param url     Full URL of the cookie
     * @param cookies Cookies to set using key = name and value = value
     */
    public static void setDomainCookies(String url, Map<String, String> cookies) {
        CookieManager mgr = CookieManager.getInstance();
        String domain = getDomainFromUri(url);

        /*
        Check if given cookies are already registered

        Rationale : setting any cookie programmatically will set it as a _session_ cookie.
        It's not smart to do that if the very same cookie is already set for a longer lifespan.
         */
        Map<String, String> cookiesToSet = new HashMap<>();

        String existingCookiesStr = mgr.getCookie(domain);
        if (existingCookiesStr != null) {
            Map<String, String> existingCookies = parseCookies(existingCookiesStr);

            for (Map.Entry<String, String> entry : cookies.entrySet()) {
                String key = entry.getKey();
                String value = (null == entry.getValue()) ? "" : entry.getValue();
                if (!existingCookies.containsKey(key)) cookiesToSet.put(key, value);
                else {
                    String val = existingCookies.get(key);
                    if (val != null && !val.equals(cookies.get(key)))
                        cookiesToSet.put(key, cookies.get(key));
                }
            }
        }

        for (Map.Entry<String, String> entry : cookiesToSet.entrySet())
            mgr.setCookie(domain, entry.getKey() + "=" + entry.getValue());

        mgr.flush();
    }

    /**
     * Determine whether the given URL is associated with a cookie with the given name
     *
     * @param url        URL to test against
     * @param cookieName Cookie name to test against on the given URl's domain
     * @return True if the given URL's domain is associated with a cookie with the given name; false if not
     */
    public static boolean hasDomainCookie(@NonNull final String url, @NonNull final String cookieName) {
        String domain = getDomainFromUri(url);
        String existingCookiesStr = CookieManager.getInstance().getCookie(domain);
        return (existingCookiesStr != null && existingCookiesStr.toLowerCase().contains(cookieName.toLowerCase() + "="));
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

    // TODO Doc
    public static Map<String, String> extractParameters(@NonNull final Uri uri) {
        Map<String, String> result = new HashMap<>();

        Set<String> keys = uri.getQueryParameterNames();
        for (String k : keys)
            result.put(k, uri.getQueryParameter(k));

        return result;
    }

    /**
     * Get cookie headers set by the page at the given URL
     *
     * @param url Url to peek cookies from
     * @return Raw cookies string
     */
    public static String peekCookies(@NonNull final String url) {
        try {
            Response response = getOnlineResource(url, null, false);
            List<String> cookielist = response.headers().values("Set-Cookie");
            return TextUtils.join("; ", cookielist);
        } catch (IOException e) {
            Timber.e(e);
        }
        return "";
    }
}
