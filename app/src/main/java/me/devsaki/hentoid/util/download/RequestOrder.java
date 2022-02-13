package me.devsaki.hentoid.util.download;

import com.android.volley.VolleyError;
import com.annimon.stream.function.Consumer;

import java.util.Map;
import java.util.Objects;

/**
 * Download request
 */
public class RequestOrder {

    private final int method;
    private final String url;
    private final Map<String, String> headers;
    private final boolean useHentoidAgent;
    private final boolean useWebviewAgent;
    private final Consumer<Map.Entry<byte[], Map<String, String>>> parseListener;
    private final Consumer<VolleyError> errorListener;

    public RequestOrder(
            int method,
            String url,
            Map<String, String> headers,
            boolean useHentoidAgent,
            boolean useWebviewAgent,
            Consumer<Map.Entry<byte[], Map<String, String>>> parseListener,
            Consumer<VolleyError> errorListener) {
        this.method = method;
        this.url = url;
        this.headers = headers;
        this.useHentoidAgent = useHentoidAgent;
        this.useWebviewAgent = useWebviewAgent;
        this.parseListener = parseListener;
        this.errorListener = errorListener;
    }

    public int getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public boolean isUseHentoidAgent() {
        return useHentoidAgent;
    }

    public boolean isUseWebviewAgent() {
        return useWebviewAgent;
    }

    public Consumer<Map.Entry<byte[], Map<String, String>>> getParseListener() {
        return parseListener;
    }

    public Consumer<VolleyError> getErrorListener() {
        return errorListener;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RequestOrder req = (RequestOrder) o;
        return url.equals(req.url)
                && method == req.method;
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, method);
    }
}
