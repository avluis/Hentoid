package me.devsaki.hentoid.services;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;

class InputStreamVolleyRequest extends Request<byte[]> {
    private final Response.Listener<Map.Entry<byte[], Map<String, String>>> mListener;
    private Map<String, String> mParams;

    //create a static map for directly accessing headers
    private Map<String, String> responseHeaders ;

    InputStreamVolleyRequest(int method, String mUrl, Response.Listener<Map.Entry<byte[], Map<String, String>>> listener,
                             Response.ErrorListener errorListener, HashMap<String, String> params) {
        super(method, mUrl, errorListener);
        // this request would never use cache.
        setShouldCache(false);
        mListener = listener;
        mParams=params;
    }

    @Override
    protected Map<String, String> getParams() {
        return mParams;
    }


    @Override
    protected void deliverResponse(byte[] response) {
        mListener.onResponse(new AbstractMap.SimpleEntry<>(response, responseHeaders));
    }

    @Override
    protected Response<byte[]> parseNetworkResponse(NetworkResponse response) {
        //Initialise local responseHeaders map with response headers received
        responseHeaders = response.headers;

        //Pass the response data here
        return Response.success( response.data, HttpHeaderParser.parseCacheHeaders(response));
    }
}