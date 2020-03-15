package me.devsaki.hentoid.util;

import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Pair;
import android.webkit.CookieManager;
import android.webkit.WebResourceResponse;

import androidx.annotation.NonNull;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class HttpHelper {

    private static final int TIMEOUT = 30000; // 30 seconds
    public static final String HEADER_COOKIE_KEY = "cookie";
    public static final String HEADER_REFERER_KEY = "referer";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";

    private HttpHelper() {
        throw new IllegalStateException("Utility class");
    }

    @Nullable
    public static Document getOnlineDocument(String url) throws IOException {
        return getOnlineDocument(url, null, true);
    }

    @Nullable
    public static Document getOnlineDocument(String url, List<Pair<String, String>> headers, boolean useHentoidAgent) throws IOException {
        ResponseBody resource = getOnlineResource(url, headers, useHentoidAgent).body();
        if (resource != null) {
            return Jsoup.parse(resource.string());
        }
        return null;
    }

    @Nullable
    public static <T> T getOnlineJson(String url, List<Pair<String, String>> headers, boolean useHentoidAgent, Class<T> type) throws IOException {
        ResponseBody resource = getOnlineResource(url, headers, useHentoidAgent).body();
        if (resource != null) {
            String s = resource.string();
            if (s.startsWith("{")) return JsonHelper.jsonToObject(s, type);
        }
        return null;
    }

    public static Response getOnlineResource(String url, List<Pair<String, String>> headers, boolean useHentoidAgent) throws IOException {
        OkHttpClient okHttp = OkHttpClientSingleton.getInstance(TIMEOUT);
        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (headers != null)
            for (Pair<String, String> header : headers)
                if (header.second != null)
                    requestBuilder.addHeader(header.first, header.second);
        requestBuilder.header("User-Agent", useHentoidAgent ? Consts.USER_AGENT : Consts.USER_AGENT_NEUTRAL);
        Request request = requestBuilder.get().build();
        return okHttp.newCall(request).execute();
    }

    /**
     * Convert OkHttp {@link Response} into a {@link WebResourceResponse}
     *
     * @param resp The OkHttp {@link Response}
     * @return The {@link WebResourceResponse}
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            result.setResponseHeaders(okHttpHeadersToWebResourceHeaders(resp.headers().toMultimap()));
        }

        return result;
    }

    private static Map<String, String> okHttpHeadersToWebResourceHeaders(@NonNull final Map<String, List<String>> okHttpHeaders) {
        Map<String, String> result = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : okHttpHeaders.entrySet()) {
            List<String> values = entry.getValue();
            if (values != null)
                result.put(entry.getKey(), TextUtils.join(getValuesSeparatorFromHttpHeader(entry.getKey()), values));
        }

        return result;
    }

    private static String getValuesSeparatorFromHttpHeader(@NonNull final String header) {

        String separator = ", "; // HTTP spec

        if (header.equalsIgnoreCase("set-cookie") || header.equalsIgnoreCase("www-authenticate") || header.equalsIgnoreCase("proxy-authenticate"))
            separator = "\n"; // Special case : commas may appear in these headers => use a newline delimiter

        return separator;
    }

    /**
     * Processes the value of a "Content-Type" HTTP header and returns its parts
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

    @Nullable
    private static String getDomainFromUri(@NonNull String uriStr) {
        Uri uri = Uri.parse(uriStr);
        String result = uri.getHost();
        if (result != null && result.startsWith("www")) result = result.substring(3);
        return result;
    }

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
     * Set a new cookie for the domain of the given url
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
    }
}
