package me.devsaki.hentoid.util.network

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import android.webkit.CookieManager
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.file.DEFAULT_MIME_TYPE
import me.devsaki.hentoid.util.isNumeric
import me.devsaki.hentoid.util.pause
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import kotlin.math.min

/**
 * Helper for HTTP protocol operations
 */
const val DEFAULT_REQUEST_TIMEOUT = 30000 // 30 seconds


// Keywords of the HTTP protocol
const val HEADER_ACCEPT_KEY = "accept"
const val HEADER_COOKIE_KEY = "cookie"
const val HEADER_REFERER_KEY = "referer"
const val HEADER_CONTENT_TYPE = "Content-Type"
const val HEADER_USER_AGENT = "user-agent"

const val POST_MIME_TYPE = "application/x-www-form-urlencoded"

// To display sites with desktop layouts
private const val DESKTOP_USER_AGENT_PATTERN =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) %s Safari/537.36"

private var defaultUserAgent: String? = null
private var defaultChromeAgent: String? = null
private var defaultChromeVersion = -1

// Error messages
private const val AGENT_INIT_ISSUE = "Call initUserAgents first to initialize them !"


/**
 * Read an HTML resource from the given URL and retrieve it as a Document
 *
 * @param url URL to read the resource from
 * @return HTML resource read from the given URL represented as a Document
 * @throws IOException in case something bad happens when trying to access the online resource
 */
@Throws(IOException::class)
fun getOnlineDocument(url: String): Document? {
    return getOnlineDocument(url, null, useHentoidAgent = true, useWebviewAgent = true)
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
@Throws(IOException::class)
fun getOnlineDocument(
    url: String,
    headers: List<Pair<String, String>>?,
    useHentoidAgent: Boolean,
    useWebviewAgent: Boolean
): Document? {
    getOnlineResource(url, headers, true, useHentoidAgent, useWebviewAgent).body
        .use { resource ->
            if (resource != null) return Jsoup.parse(resource.string())
        }
    return null
}

@Throws(IOException::class)
fun postOnlineDocument(
    url: String,
    headers: List<Pair<String, String>>?,
    useHentoidAgent: Boolean, useWebviewAgent: Boolean,
    body: String,
    mimeType: String
): Document? {
    postOnlineResource(
        url,
        headers,
        true,
        useHentoidAgent,
        useWebviewAgent,
        body,
        mimeType
    ).body.use { resource ->
        if (resource != null) return Jsoup.parse(resource.string())
    }
    return null
}

/**
 * Read a resource from the given URL with HTTP GET, using the given headers and agent
 *
 * @param url             URL to read the resource from
 * @param headers         Headers to use when building the request
 * @param useMobileAgent  True to use the mobile User-Agent; false to use the desktop User-Agent
 * @param useHentoidAgent True to use the Hentoid User-Agent; false to use a neutral User-Agent
 * @param useWebviewAgent True to reveal the use of a webview through the User-Agent; false to use a neutral User-Agent
 * @return HTTP response
 * @throws IOException in case something bad happens when trying to access the online resource
 */
@Throws(IOException::class)
fun getOnlineResource(
    url: String,
    headers: List<Pair<String, String>>?,
    useMobileAgent: Boolean,
    useHentoidAgent: Boolean,
    useWebviewAgent: Boolean
): Response {
    val requestBuilder: Request.Builder =
        buildRequest(url, headers, useMobileAgent, useHentoidAgent, useWebviewAgent)
    val request: Request = requestBuilder.get().build()
    return OkHttpClientSingleton.getInstance().newCall(request).execute()
}

@Throws(IOException::class)
fun getOnlineResourceFast(
    url: String,
    headers: List<Pair<String, String>>?,
    useMobileAgent: Boolean,
    useHentoidAgent: Boolean,
    useWebviewAgent: Boolean
): Response {
    return getOnlineResourceFast(
        url,
        headers,
        useMobileAgent,
        useHentoidAgent,
        useWebviewAgent,
        true
    )
}

@Throws(IOException::class)
fun getOnlineResourceFast(
    url: String,
    headers: List<Pair<String, String>>?,
    useMobileAgent: Boolean,
    useHentoidAgent: Boolean,
    useWebviewAgent: Boolean,
    followRedirects: Boolean
): Response {
    val requestBuilder: Request.Builder =
        buildRequest(url, headers, useMobileAgent, useHentoidAgent, useWebviewAgent)
    val request: Request = requestBuilder.get().build()
    return OkHttpClientSingleton.getInstance(2000, 10000, followRedirects).newCall(request)
        .execute()
}

@Throws(IOException::class)
fun optOnlineResourceFast(
    url: String,
    headers: List<Pair<String, String>>?,
    useMobileAgent: Boolean,
    useHentoidAgent: Boolean,
    useWebviewAgent: Boolean,
    followRedirects: Boolean
): Response {
    val requestBuilder: Request.Builder =
        buildRequest(url, headers, useMobileAgent, useHentoidAgent, useWebviewAgent)
    val request: Request = requestBuilder.method("OPTIONS", null).build()
    return OkHttpClientSingleton.getInstance(2000, 10000, followRedirects).newCall(request)
        .execute()
}

@Throws(IOException::class)
fun getOnlineResourceDownloader(
    url: String,
    headers: List<Pair<String, String>>?,
    useMobileAgent: Boolean,
    useHentoidAgent: Boolean,
    useWebviewAgent: Boolean
): Response {
    return getOnlineResourceDownloader(
        url,
        headers,
        useMobileAgent,
        useHentoidAgent,
        useWebviewAgent,
        true
    )
}

@Throws(IOException::class)
fun getOnlineResourceDownloader(
    url: String,
    headers: List<Pair<String, String>>?,
    useMobileAgent: Boolean,
    useHentoidAgent: Boolean,
    useWebviewAgent: Boolean,
    followRedirects: Boolean
): Response {
    val requestBuilder: Request.Builder =
        buildRequest(url, headers, useMobileAgent, useHentoidAgent, useWebviewAgent)
    val request: Request = requestBuilder.get().build()
    return OkHttpClientSingleton.getInstance(4000, 15000, followRedirects).newCall(request)
        .execute()
}

/**
 * Read a resource from the given URL with HTTP POST, using the given headers and agent
 *
 * @param url             URL to read the resource from
 * @param headers         Headers to use when building the request
 * @param useMobileAgent  True to use the mobile User-Agent; false to use the desktop User-Agent
 * @param useHentoidAgent True to use the Hentoid User-Agent; false to use a neutral User-Agent
 * @param useWebviewAgent True to reveal the use of a webview through the User-Agent; false to use a neutral User-Agent
 * @param body            Body of the resource to post
 * @param mimeType        MIME-type of the posted body
 * @return HTTP response
 * @throws IOException in case something bad happens when trying to access the online resource
 */
@Throws(IOException::class)
fun postOnlineResource(
    url: String,
    headers: List<Pair<String, String>>?,
    useMobileAgent: Boolean,
    useHentoidAgent: Boolean,
    useWebviewAgent: Boolean,
    body: String,
    mimeType: String
): Response {
    val requestBuilder: Request.Builder =
        buildRequest(url, headers, useMobileAgent, useHentoidAgent, useWebviewAgent)
    val request: Request =
        requestBuilder.post(body.toRequestBody(mimeType.toMediaTypeOrNull())).build()
    return OkHttpClientSingleton.getInstance().newCall(request).execute()
}

/**
 * Build an HTTP request using the given arguments
 *
 * @param url             URL to read the resource from
 * @param headers         Headers to use when building the request
 * @param useMobileAgent  True to use the mobile User-Agent; false to use the desktop User-Agent
 * @param useHentoidAgent True to use the Hentoid User-Agent; false to use a neutral User-Agent
 * @param useWebviewAgent True to reveal the use of a webview through the User-Agent; false to use a neutral User-Agent
 * @return HTTP request built with the given arguments
 */
private fun buildRequest(
    url: String,
    headers: List<Pair<String, String>>?,
    useMobileAgent: Boolean,
    useHentoidAgent: Boolean,
    useWebviewAgent: Boolean
): Request.Builder {
    val requestBuilder = Request.Builder().url(url)
    if (headers != null) for (header in headers) requestBuilder.addHeader(
        header.first,
        header.second
    )
    requestBuilder.header(
        HEADER_USER_AGENT,
        getUserAgent(useMobileAgent, useHentoidAgent, useWebviewAgent)
    )
    return requestBuilder
}

/**
 * Generate the user agent corresponding to the given parameters
 *
 * @param useMobileAgent  True to use the mobile User-Agent; false to use the desktop User-Agent
 * @param useHentoidAgent True to use the Hentoid User-Agent; false to use a neutral User-Agent
 * @param useWebviewAgent True to reveal the use of a webview through the User-Agent; false to use a neutral User-Agent
 * @return User agent corresponding to the given parameters
 */
fun getUserAgent(
    useMobileAgent: Boolean,
    useHentoidAgent: Boolean,
    useWebviewAgent: Boolean
): String {
    return if (useMobileAgent) getMobileUserAgent(useHentoidAgent, useWebviewAgent)
    else getDesktopUserAgent(useHentoidAgent, useWebviewAgent)
}

/**
 * Convert the given OkHttp [Response] into a [WebResourceResponse], using the data from the given InputStream
 *
 * @param resp         OkHttp [Response]
 * @param responseData Data to include in the resulting response
 * @return The [WebResourceResponse] converted from the given OkHttp [Response]
 */
fun okHttpResponseToWebkitResponse(
    resp: Response,
    responseData: InputStream
): WebResourceResponse {
    val contentTypeValue = resp.header(HEADER_CONTENT_TYPE)
    val result: WebResourceResponse
    val responseHeaders = okHttpHeadersToWebResourceHeaders(resp.headers.toMultimap())
    var message = resp.message
    if (message.trim { it <= ' ' }.isEmpty()) message = "None"
    result = if (contentTypeValue != null) {
        val details = cleanContentType(contentTypeValue)
        WebResourceResponse(
            details.first,
            details.second,
            resp.code,
            message,
            responseHeaders,
            responseData
        )
    } else {
        WebResourceResponse(
            DEFAULT_MIME_TYPE,
            null,
            resp.code,
            message,
            responseHeaders,
            responseData
        )
    }
    return result
}

/**
 * "Flatten"" HTTP headers from an OkHttp-compatible structure to a Webkit-compatible structure
 * to be used with [android.webkit.WebResourceRequest] or [android.webkit.WebResourceResponse]
 *
 * @param okHttpHeaders HTTP Headers structured according to the convention used by OkHttp
 * @return "Flattened" HTTP headers structured according to the convention used by Webkit
 */
private fun okHttpHeadersToWebResourceHeaders(okHttpHeaders: Map<String, List<String?>>): Map<String, String> {
    val result: MutableMap<String, String> = HashMap()
    for ((key, values) in okHttpHeaders) {
        result[key] = TextUtils.join(getValuesSeparatorFromHttpHeader(key), values)
    }
    return result
}

/**
 * Convert request HTTP headers from a Webkit-compatible structure to an OkHttp-compatible structure
 * and enrich them with current cookies
 *
 * @param webkitRequestHeaders HTTP request Headers structured according to the convention used by Webkit
 * @param url                  Corresponding URL
 * @return HTTP request Headers structured according to the convention used by OkHttp
 */
fun webkitRequestHeadersToOkHttpHeaders(
    webkitRequestHeaders: Map<String, String>?,
    url: String?
): List<Pair<String, String>> {
    val result: MutableList<Pair<String, String>> = ArrayList()
    if (webkitRequestHeaders != null) for ((key, value) in webkitRequestHeaders) result.add(
        Pair(key, value)
    )
    url?.let { addCurrentCookiesToHeader(it, result) }
    return result
}

/**
 * Add current cookies of the given URL to the given headers structure
 * If the given header already has a cookie entry, it is removed and replaced with the one
 * associated with the given URL.
 *
 * @param url     URL to get cookies for
 * @param headers Structure to populate or update
 */
fun addCurrentCookiesToHeader(url: String, headers: MutableList<Pair<String, String>>) {
    val cookieStr = getCookies(url)
    if (cookieStr.isNotEmpty()) {
        for (i in headers.indices) {
            if (headers[i].first == HEADER_COOKIE_KEY) {
                headers.removeAt(i)
                break
            }
        }
        headers.add(Pair(HEADER_COOKIE_KEY, cookieStr))
    }
}

/**
 * Get the values separator used inside the given HTTP header key
 *
 * @param header key of the HTTP header
 * @return Values separator used inside the given HTTP header key
 */
private fun getValuesSeparatorFromHttpHeader(header: String): String {
    var separator = ", " // HTTP spec
    if (header.equals("set-cookie", ignoreCase = true) || header.equals(
            "www-authenticate",
            ignoreCase = true
        ) || header.equals("proxy-authenticate", ignoreCase = true)
    ) separator =
        "\n" // Special case : commas may appear in these headers => use a newline delimiter
    return separator
}

/**
 * Process the value of a "Content-Type" HTTP header and return its parts
 *
 * @param rawContentType Value of the "Content-type" header
 * @return Pair containing
 * - The content-type (MIME-type) as its first value
 * - The charset, if it has been transmitted, as its second value (may be null)
 */
fun cleanContentType(rawContentType: String): Pair<String, String?> {
    return if (rawContentType.contains("charset=")) {
        val contentTypeAndEncoding = rawContentType.replace("; ", ";").split(";")
        val contentType = contentTypeAndEncoding[0]
        val charset = contentTypeAndEncoding[1].split("=")[1]
        Pair(contentType, charset)
    } else Pair(rawContentType, null)
}

/**
 * Return the extension of the file located at the given URI, without the leading '.'
 *
 * @param uri Location of the file
 * @return Extension of the file located at the given URI, without the leading '.'
 */
fun getExtensionFromUri(uri: String): String {
    val parts = UriParts(uri, true)
    return parts.extension
}

/**
 * Extract and return the main domain from the given URI
 *
 * @param uriStr URI to parse, in String form
 * @return Main domain of the given URI (i.e. without any subdomain); null if no domain found
 */
fun getDomainFromUri(uriStr: String): String {
    val result = Uri.parse(uriStr).host ?: return ""
    val parts = result.split(".")
    // Domain without extension
    return if (1 == parts.size) parts[0] else parts[parts.size - 2] + "." + parts[parts.size - 1]
    // Main domain and extension
}

/**
 * Extract and return the protocol from the given HTTP URL
 *
 * @param url URL to parse, in String form
 * @return Protocol of the given URL : https ou http
 */
fun getHttpProtocol(url: String): String {
    return if (url.startsWith("https")) "https" else "http"
}

/**
 * Parse the given cookie String
 *
 * @param cookiesStr Cookie string, as set in HTTP request headers
 * @return Parsed cookies (key and value of each cookie; key only if there's no value)
 */
fun parseCookies(cookiesStr: String): Map<String, String> {
    val result: MutableMap<String, String> = HashMap()
    val cookiesParts = cookiesStr.split(";").map { s -> s.trim() }
    for (part in cookiesParts) {
        // Don't use split as the value of the cookie may contain an '='
        val equalsIndex = part.indexOf('=')
        if (equalsIndex > -1) result[part.substring(0, equalsIndex)] =
            part.substring(equalsIndex + 1) else result[part] = ""
    }
    return result
}

/**
 * Set session cookies for the given URL, keeping existing cookies if they are still active
 *
 * @param url       Url to set the cookies for
 * @param cookieStr The cookie as a string, using the format of the 'Set-Cookie' HTTP response header
 */
fun setCookies(url: String?, cookieStr: String) {
    /*
    Check if given cookies are already registered

    Rationale : setting any cookie programmatically will set it as a _session_ cookie.
    It's not smart to do that if the very same cookie is already set for a longer lifespan.
     */
    val cookies = parseCookies(cookieStr)
    val names: MutableMap<String, String?> = HashMap()
    val paramsToSet: MutableList<String> = ArrayList()
    val namesToSet: MutableList<String?> = ArrayList()
    for ((key, value) in cookies) {
        if (Cookie.STANDARD_ATTRS.contains(key.lowercase(Locale.getDefault()))) {
            if (value.isEmpty()) paramsToSet.add(key) else paramsToSet.add("$key=$value")
        } else names[key] = value
    }
    val mgr = CookieManager.getInstance()
    val existingCookiesStr = mgr.getCookie(url)
    if (existingCookiesStr != null) {
        val existingCookies = parseCookies(existingCookiesStr)
        for ((key, value1) in names) {
            val value = value1 ?: ""
            val existingValue = existingCookies[key]
            if (null == existingValue || existingValue != value) namesToSet.add("$key=$value")
        }
    } else {
        for ((key, value) in names)
            namesToSet.add("$key=$value")
    }
    if (namesToSet.isEmpty()) {
        Timber.v("No new cookie to set %s", url)
        return
    }
    val cookieStrToSet = StringBuilder()
    cookieStrToSet.append(TextUtils.join("; ", namesToSet))
    for (param in paramsToSet) cookieStrToSet.append("; ").append(param)
    mgr.setCookie(url, cookieStrToSet.toString())
    Timber.v("Setting cookie for %s : %s", url, cookieStrToSet.toString())
    mgr.flush()
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
fun fixUrl(url: String?, baseUrl: String): String {
    if (url.isNullOrEmpty()) return ""
    if (url.startsWith("//")) return "https:$url"
    return if (!url.startsWith("http")) {
        var sourceUrl = baseUrl
        if (sourceUrl.endsWith("/")) sourceUrl = sourceUrl.substring(0, sourceUrl.length - 1)
        if (url.startsWith("/")) sourceUrl + url else "$sourceUrl/$url"
    } else url
}

/**
 * Parse the parameters of the given Uri into a map
 *
 * @param uri Uri to parse the paramaters from
 * @return Parsed parameters, where each key is the parameters name and each corresponding value their respective value
 */
fun parseParameters(uri: Uri): Map<String, String> {
    val result: MutableMap<String, String> = HashMap()
    val keys = uri.queryParameterNames
    for (k in keys) result[k] = uri.getQueryParameter(k) ?: ""
    return result
}

/**
 * Get current cookie headers for the given URL
 *
 * @param url URL to get cookies from
 * @return Raw cookies string for the given URL
 */
fun getCookies(url: String): String {
    val result = CookieManager.getInstance().getCookie(url)
    return if (result != null) Cookie.stripParams(result) else ""
}

/**
 * Get current cookie headers for the given URL
 * If the app doesn't have any, load the given URL to get them
 *
 * @param url             URL to get cookies from
 * @param headers         Headers to call the URL with
 * @param useMobileAgent  True if mobile agent should be used
 * @param useHentoidAgent True if Hentoid user agent should be used
 * @param useWebviewAgent True if webview user agent should be used
 * @return Raw cookies string for the given URL
 */
fun getCookies(
    url: String,
    headers: List<Pair<String, String>>?,
    useMobileAgent: Boolean,
    useHentoidAgent: Boolean,
    useWebviewAgent: Boolean
): String {
    val result = getCookies(url)
    if (result.isEmpty()) return peekCookies(
        url,
        headers,
        useMobileAgent,
        useHentoidAgent,
        useWebviewAgent
    )
    return result
}

/**
 * Get cookie headers set by the page at the given URL by calling that page
 *
 * @param url URL to peek cookies from
 * @return Raw cookies string
 */
fun peekCookies(url: String): String {
    return peekCookies(url, null, true, useHentoidAgent = false, useWebviewAgent = true)
}

/**
 * Get cookie headers set by the page at the given URL by calling that page
 *
 * @param url             URL to peek cookies from
 * @param headers         Headers to call the URL with
 * @param useMobileAgent  True if mobile user agent should be used
 * @param useHentoidAgent True if Hentoid user agent should be used
 * @param useWebviewAgent True if webview user agent should be used
 * @return Raw cookies string for the given URL
 */
private fun peekCookies(
    url: String,
    headers: List<Pair<String, String>>?,
    useMobileAgent: Boolean,
    useHentoidAgent: Boolean,
    useWebviewAgent: Boolean
): String {
    try {
        val response = getOnlineResourceFast(
            url,
            headers,
            useMobileAgent,
            useHentoidAgent,
            useWebviewAgent
        )
        var cookielist: List<String?> = response.headers("Set-Cookie")
        if (cookielist.isEmpty()) cookielist = response.headers("Set-Cookie")
        return TextUtils.join("; ", cookielist)
    } catch (e: IOException) {
        Timber.e(e)
    }
    return ""
}

/**
 * Initialize the app's user agents
 *
 * @param context Context to be used
 */
fun initUserAgents(context: Context) {
    val chromeString = "Chrome/"
    defaultUserAgent = WebSettings.getDefaultUserAgent(context)
    defaultUserAgent?.let {
        if (it.contains(chromeString)) {
            val chromeIndex = it.indexOf(chromeString)
            val spaceIndex = it.indexOf(' ', chromeIndex)
            val dotIndex = it.indexOf('.', chromeIndex)
            val version = it.substring(chromeIndex + chromeString.length, dotIndex)
            defaultChromeVersion = version.toInt()
            defaultChromeAgent = it.substring(chromeIndex, spaceIndex)
        }
    }
    Timber.i("defaultUserAgent = %s", defaultUserAgent)
    Timber.i("defaultChromeAgent = %s", defaultChromeAgent)
    Timber.i("defaultChromeVersion = %s", defaultChromeVersion)
}

/**
 * Get the app's mobile user agent
 *
 * @param withHentoid True if the Hentoid user-agent has to appear
 * @param withWebview True if the user-agent has to mention the use of a webview
 * @return The app's mobile user agent
 */
fun getMobileUserAgent(withHentoid: Boolean, withWebview: Boolean): String {
    return getDefaultUserAgent(withHentoid, withWebview)
}

/**
 * Get the app's desktop user agent
 *
 * @param withHentoid True if the Hentoid user-agent has to appear
 * @param withWebview True if the user-agent has to mention the use of a webview
 * @return The app's desktop user agent
 */
fun getDesktopUserAgent(withHentoid: Boolean, withWebview: Boolean): String {
    if (null == defaultChromeAgent) throw RuntimeException(AGENT_INIT_ISSUE)
    var result = String.format(DESKTOP_USER_AGENT_PATTERN, defaultChromeAgent)
    if (withHentoid) result += " Hentoid/v" + BuildConfig.VERSION_NAME
    if (!withWebview) result = cleanWebViewAgent(result)
    return result
}

/**
 * Get the app's default user agent
 *
 * @param withHentoid True if the Hentoid user-agent has to appear
 * @param withWebview True if the user-agent has to mention the use of a webview
 * @return The app's default user agent
 */
private fun getDefaultUserAgent(withHentoid: Boolean, withWebview: Boolean): String {
    var result = defaultUserAgent ?: throw RuntimeException(AGENT_INIT_ISSUE)
    if (withHentoid) result += " Hentoid/v" + BuildConfig.VERSION_NAME
    if (!withWebview) result = cleanWebViewAgent(result)
    return result
}

/**
 * Remove all references to webview in the given user agent
 *
 * @param agent User agent to clean from webview references
 * @return User agent cleaned from webview references
 */
fun cleanWebViewAgent(agent: String): String {
    var result = agent
    val buildIndex = result.indexOf(" Build/")
    if (buildIndex > -1) {
        val closeIndex = result.indexOf(")", buildIndex)
        val separatorIndex = result.indexOf(";", buildIndex)
        var firstIndex = closeIndex
        if (separatorIndex > -1) firstIndex = min(closeIndex, separatorIndex)
        result = result.substring(0, buildIndex) + result.substring(firstIndex)
    }
    val versionIndex = result.indexOf(" Version/")
    if (versionIndex > -1) {
        val closeIndex = result.indexOf(" ", versionIndex + 1)
        result = result.substring(0, versionIndex) + result.substring(closeIndex)
    }
    return result.replace("; wv", "")
}

/**
 * Get the app's Chrome version
 *
 * @return The app's Chrome version
 */
fun getChromeVersion(): Int {
    if (-1 == defaultChromeVersion) throw RuntimeException(AGENT_INIT_ISSUE)
    return defaultChromeVersion
}

/**
 * Simplify the given URL :
 * - Remove parameters
 * - Turn -'s into /'s (Hitomi : /doujinshi/this_is_a_title-lang_code-launch_code.html vs. /launch_code.html)
 * - Make sure there's a trailing /
 *
 * @param url Url to simplify
 * @return Simplified URL according to the above rules
 */
fun simplifyUrl(url: String): String {
    var result = url
    // Remove parameters
    val paramsIndex = result.indexOf("?")
    if (paramsIndex > -1) result = result.substring(0, paramsIndex)
    // Simplify & eliminate double separators
    result = result.trim { it <= ' ' }.replace("-", "/")
    if (!result.endsWith("/")) result = "$result/"
    return result
}

/**
 * If the given response is an HTTP 429, block and wait according to the delay supplied in the response
 *
 * @param response Response to examine
 * @return True if the response is an HTTP 429 _and_ a delay has been supplied and waited out
 */
fun waitBlocking429(response: retrofit2.Response<*>, defaultDelayMs: Int): Boolean {
    if (429 == response.code()) {
        var delay = defaultDelayMs
        var retryDelay = response.headers()["Retry-After"]
        if (null == retryDelay) retryDelay = response.headers()["retry-after"]
        if (retryDelay != null && isNumeric(retryDelay)) {
            delay = retryDelay.toInt() + 1000 // 1s extra margin
        }
        pause(delay)
        return true
    }
    return false
}

@Throws(IOException::class, CloudflareHelper.CloudflareProtectedException::class)
fun fetchBodyFast(
    url: String,
    site: Site,
    requestHeaders: MutableList<Pair<String, String>>?,
    targetContentType: String?
): Pair<ResponseBody?, String> {
    val requestHeadersList: MutableList<Pair<String, String>>
    if (null == requestHeaders) {
        requestHeadersList = ArrayList()
        requestHeadersList.add(Pair(HEADER_REFERER_KEY, url))
    } else {
        requestHeadersList = requestHeaders
    }
    val cookieStr = getCookies(
        url,
        requestHeadersList,
        site.useMobileAgent(),
        site.useHentoidAgent(),
        site.useWebviewAgent()
    )
    if (cookieStr.isNotEmpty()) requestHeadersList.add(Pair(HEADER_COOKIE_KEY, cookieStr))

    val response = getOnlineResourceFast(
        url,
        requestHeadersList,
        site.useMobileAgent(),
        site.useHentoidAgent(),
        site.useWebviewAgent()
    )

    // Raise exception if blocked by Cloudflare
    if (503 == response.code && site.isUseCloudflare) throw CloudflareHelper.CloudflareProtectedException()

    // Scram if the response is a redirection or an error
    if (response.code >= 300) throw IOException("Network error " + response.code + " @ " + url)

    // Scram if the response content-type is something else than the target type
    if (targetContentType != null) {
        val contentType =
            cleanContentType(response.header(HEADER_CONTENT_TYPE, "") ?: "")
        if (contentType.first.isNotEmpty() && !contentType.first.equals(
                targetContentType,
                ignoreCase = true
            )
        ) throw IOException(
            "Not an HTML resource $url"
        )
    }

    return Pair(response.body, cookieStr)
}

data class Cookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val isSecure: Boolean = false,
    val isHttpOnly: Boolean = false
) {
    companion object {
        private const val DOMAIN = "domain"
        private const val PATH = "path"
        private const val SECURE = "secure"
        private const val HTTPONLY = "httponly"

        val STANDARD_ATTRS =
            setOf("expires", "max-age", DOMAIN, PATH, SECURE, HTTPONLY, "samesite")

        /**
         * Parse a cookie from an RFC6265 string
         */
        fun parse(str: String): Cookie {
            val parts = str.split(";").map { s -> s.trim() }

            var cookieName = ""
            var cookieValue = ""
            var cookieDomain = ""
            var cookiePath = "/"
            var isSecure = false
            var isHttpOnly = false

            parts.forEachIndexed { index, s ->
                // Don't use split as the value of the cookie may contain an '='
                val equalsIndex = s.indexOf('=')
                val key = if (equalsIndex > -1) s.substring(0, equalsIndex) else s
                val value = if (equalsIndex > -1) s.substring(equalsIndex + 1) else ""

                if (0 == index) {
                    cookieName = key
                    cookieValue = value
                } else {
                    when (key.lowercase()) {
                        DOMAIN -> cookieDomain = value
                        PATH -> cookiePath = value
                        SECURE -> isSecure = true
                        HTTPONLY -> isHttpOnly = true
                        else -> { // Nothing }
                        }
                    }
                }
            }

            return Cookie(
                cookieName,
                cookieValue,
                cookieDomain,
                cookiePath,
                isSecure,
                isHttpOnly
            )
        }


        /**
         * Strip the given cookie string from the standard parameters
         * i.e. only return the cookie values
         *
         * @param cookieStr The cookie as a string, using the format of the 'Set-Cookie' HTTP response header
         * @return Cookie string without the standard parameters
         */
        fun stripParams(cookieStr: String): String {
            val cookies = parseCookies(cookieStr)
            val namesToSet: MutableList<String> = ArrayList()
            for ((key, value) in cookies) {
                if (!STANDARD_ATTRS.contains(key.lowercase(Locale.getDefault())))
                    namesToSet.add("$key=$value")
            }
            return TextUtils.join("; ", namesToSet)
        }
    }
}

/**
 * Class to parse and manipulate Uri parts
 * Example source Uri : http://host.ext:80/this/is/the/police.jpg?query=here#anchor
 */
class UriParts(uri: String, lowercase: Boolean = false) {
    val host: String // Host alone (e.g. http://host.ext:80)
    var path: String // Entire path, host included (e.g. http://host.ext:80/this/is/the/police)
    var fileNameNoExt: String // Filename without extension (e.g. police)
    var extension: String // File extension alone (e.g. jpg)
    var query: String // Query alone (e.g. query=here)
    private var fragment: String // Fragment alone (e.g. anchor)

    init {
        var uriNoParams = if (lowercase) uri.lowercase(Locale.getDefault()) else uri
        val fragmentIndex = uriNoParams.lastIndexOf('#')
        if (fragmentIndex > -1) {
            fragment = uriNoParams.substring(fragmentIndex + 1)
            uriNoParams = uriNoParams.substring(0, fragmentIndex)
        } else fragment = ""
        val paramsIndex = uriNoParams.lastIndexOf('?')
        if (paramsIndex > -1) {
            query = uriNoParams.substring(paramsIndex + 1)
            uriNoParams = uriNoParams.substring(0, paramsIndex)
        } else query = ""
        val pathIndex = uriNoParams.lastIndexOf('/')
        path = if (pathIndex > -1) uriNoParams.substring(0, pathIndex) else uriNoParams
        val protocolEndIndex = path.indexOf("://")
        val hostEndIndex = path.indexOf("/", protocolEndIndex + 3)
        host = if (hostEndIndex > -1) path.substring(0, hostEndIndex) else path

        val extIndex = uriNoParams.lastIndexOf('.')
        // No file extension detected
        if (extIndex < 0 || extIndex < pathIndex) {
            extension = ""
            fileNameNoExt = uriNoParams.substring(pathIndex + 1)
        } else {
            extension = uriNoParams.substring(extIndex + 1)
            fileNameNoExt = uriNoParams.substring(pathIndex + 1, extIndex)
        }
    }

    fun toUri(): String {
        val result = StringBuilder(path)
        result.append("/").append(fileNameNoExt)
        if (extension.isNotEmpty()) result.append(".").append(extension)
        if (query.isNotEmpty()) result.append("?").append(query)
        if (fragment.isNotEmpty()) result.append("#").append(fragment)
        return result.toString()
    }

    val entireFileName: String
        get() = "$fileNameNoExt.$extension"
}