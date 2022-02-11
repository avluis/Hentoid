package me.devsaki.hentoid.util.download;

import androidx.annotation.NonNull;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;

import me.devsaki.hentoid.util.network.HttpHelper;

/**
 * Specific Volley Request intended at transmitting :
 * - content as byte array
 * - raw HTTP response headers
 * <p>
 * to the download callback routine
 */
public class InputStreamVolleyRequest<T> extends Request<T> {
    // Callback listener
    // byte[] is the response's binary data; Map<String, String> are the response headers
    private final Response.Listener<Map.Entry<byte[], Map<String, String>>> mParseListener;
    private final Map<String, String> headers;
    private final boolean useHentoidAgent;
    private final boolean useWebviewAgent;


    public InputStreamVolleyRequest(@NonNull RequestOrder order) {
        super(order.getMethod(), order.getUrl(), error -> order.getErrorListener().accept(error));
        this.headers = order.getHeaders();
        this.useHentoidAgent = order.isUseHentoidAgent();
        this.useWebviewAgent = order.isUseWebviewAgent();
        // this request would never use cache.
        setShouldCache(false);
        setTag(order);
        mParseListener = response -> order.getParseListener().accept(response);
    }

    @Override
    protected void deliverResponse(T response) {
        // Nothing; all the work is done in Volley's worker thread, since it is time consuming (picture saving + DB operations)
    }

    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        // Initialise local responseHeaders map with response headers received
        mParseListener.onResponse(new AbstractMap.SimpleEntry<>(response.data, response.headers));

        // Pass the response data here
        return Response.success(null, HttpHeaderParser.parseCacheHeaders(response));
    }

    @Override
    public Map<String, String> getHeaders() {
        Map<String, String> params = new HashMap<>();
        params.put(HttpHelper.HEADER_USER_AGENT, HttpHelper.getMobileUserAgent(useHentoidAgent, useWebviewAgent));
        params.put("Accept", "image/jpeg,image/png,image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*"); // Required to pass through cloudflare filtering on some sites
        params.putAll(headers);
        return params;
    }
}