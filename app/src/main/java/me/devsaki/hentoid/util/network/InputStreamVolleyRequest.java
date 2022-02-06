package me.devsaki.hentoid.util.network;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Specific Volley Request intended at transmitting :
 * - content as byte array
 * - raw HTTP response headers
 * <p>
 * to the download callback routine
 */
public class InputStreamVolleyRequest extends Request<Object> {
    // Callback listener
    // byte[] is the response's binary data; Map<String, String> are the response headers
    private final Response.Listener<Map.Entry<byte[], Map<String, String>>> mParseListener;
    private final Map<String, String> headers;
    private final boolean useHentoidAgent;
    private final boolean useWebviewAgent;

    public InputStreamVolleyRequest(
            int method,
            String mUrl,
            Map<String, String> headers,
            boolean useHentoidAgent,
            boolean useWebviewAgent,
            Response.Listener<Map.Entry<byte[], Map<String, String>>> parseListener,
            Response.ErrorListener errorListener) {
        super(method, mUrl, errorListener);
        this.headers = headers;
        this.useHentoidAgent = useHentoidAgent;
        this.useWebviewAgent = useWebviewAgent;
        // this request would never use cache.
        setShouldCache(false);
        mParseListener = parseListener;
    }

    @Override
    protected void deliverResponse(Object response) {
        // Nothing; all the work is done in Volley's worker thread, since it is time consuming (picture saving + DB operations)
    }

    @Override
    protected Response<Object> parseNetworkResponse(NetworkResponse response) {
        //Initialise local responseHeaders map with response headers received
        Map<String, String> responseHeaders = response.headers;

        mParseListener.onResponse(new AbstractMap.SimpleEntry<>(response.data, responseHeaders));

        //Pass the response data here
        return Response.success(response.data, HttpHeaderParser.parseCacheHeaders(response));
    }

    @Override
    public Map<String, String> getHeaders() {
        Map<String, String> params = new HashMap<>();
        params.put(HttpHelper.HEADER_USER_AGENT, HttpHelper.getMobileUserAgent(useHentoidAgent, useWebviewAgent));
        params.put("Accept", "image/jpeg,image/png,image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*"); // Required to pass through cloudflare filtering on some sites
        params.putAll(headers);
        return params;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InputStreamVolleyRequest req = (InputStreamVolleyRequest) o;
        return getUrl().equals(req.getUrl())
                && getMethod() == req.getMethod();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUrl(), getMethod());
    }
}