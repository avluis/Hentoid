package me.devsaki.hentoid.util.network;

import com.android.volley.AuthFailureError;
import com.android.volley.Header;
import com.android.volley.Request;
import com.android.volley.toolbox.BaseHttpStack;
import com.android.volley.toolbox.HttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Created by Robb_w in 2018/04; heavily inspired by
 * https://gist.github.com/LOG-TAG/3ad1c191b3ca7eab3ea6834386e30eb9
 * and
 * https://gist.github.com/JakeWharton/5616899
 * <p>
 * okhttp wrapper for Volley; allows the use of okhttp as low-level network operations handler by Volley
 * The main reason being okhttp's ability to automatically follow 301 & 302's while default Volley handler cannot
 */
public class VolleyOkHttp3Stack extends BaseHttpStack {

    private final OkHttpClient client;
    private static final RequestBody EMPTY_REQUEST = RequestBody.create(new byte[0]);

    public VolleyOkHttp3Stack(int timeoutMs) {
        client = OkHttpClientSingleton.getInstance(timeoutMs, timeoutMs);
    }

    private static void setConnectionParametersForRequest(okhttp3.Request.Builder builder, Request<?> request)
            throws AuthFailureError {
        switch (request.getMethod()) {
            case Request.Method.DEPRECATED_GET_OR_POST:
                // Ensure backwards compatibility.  Volley assumes a request with a null body is a GET.
                byte[] postBody = request.getBody();
                if (postBody != null) {
                    builder.post(RequestBody.create(postBody, MediaType.parse(request.getBodyContentType())));
                }
                break;
            case Request.Method.GET:
                builder.get();
                break;
            case Request.Method.DELETE:
                builder.delete(createRequestBody(request));
                break;
            case Request.Method.POST:
                builder.post(createRequestBody(request));
                break;
            case Request.Method.PUT:
                builder.put(createRequestBody(request));
                break;
            case Request.Method.HEAD:
                builder.head();
                break;
            case Request.Method.OPTIONS:
                builder.method("OPTIONS", null);
                break;
            case Request.Method.TRACE:
                builder.method("TRACE", null);
                break;
            case Request.Method.PATCH:
                builder.patch(createRequestBody(request));
                break;
            default:
                throw new IllegalStateException("Unknown method type.");
        }
    }

    private static RequestBody createRequestBody(Request<?> r) throws AuthFailureError {
        final byte[] body = r.getBody();
        if (body == null) return EMPTY_REQUEST;
        return RequestBody.create(body, MediaType.parse(r.getBodyContentType()));
    }

    @Override
    public HttpResponse executeRequest(Request<?> request, Map<String, String> additionalHeaders) throws IOException, AuthFailureError {
        okhttp3.Request.Builder okHttpRequestBuilder = new okhttp3.Request.Builder();
        okHttpRequestBuilder.url(request.getUrl());

        Map<String, String> headers = request.getHeaders();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String value = (null == entry.getValue()) ? "" : entry.getValue();
            okHttpRequestBuilder.addHeader(entry.getKey(), value);
        }
        for (Map.Entry<String, String> entry : additionalHeaders.entrySet()) {
            String value = (null == entry.getValue()) ? "" : entry.getValue();
            okHttpRequestBuilder.addHeader(entry.getKey(), value);
        }

        setConnectionParametersForRequest(okHttpRequestBuilder, request);

        okhttp3.Request okHttpRequest = okHttpRequestBuilder.build();
        Call okHttpCall = client.newCall(okHttpRequest);
        Response okHttpResponse = okHttpCall.execute();

        int code = okHttpResponse.code();
        ResponseBody body = okHttpResponse.body();
        InputStream content = body == null ? null : body.byteStream();
        int contentLength = body == null ? 0 : (int) body.contentLength();
        List<Header> responseHeaders = mapHeaders(okHttpResponse.headers());
        return new HttpResponse(code, responseHeaders, contentLength, content);
    }

    private List<Header> mapHeaders(Headers responseHeaders) {
        List<Header> headers = new ArrayList<>();
        for (int i = 0, len = responseHeaders.size(); i < len; i++) {
            final String name = responseHeaders.name(i);
            final String value = responseHeaders.value(i);
            headers.add(new Header(name, value));
        }
        return headers;
    }
}


