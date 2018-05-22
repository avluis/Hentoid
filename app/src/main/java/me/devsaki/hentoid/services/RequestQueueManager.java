package me.devsaki.hentoid.services;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import me.devsaki.hentoid.util.VolleyOkHttp3Stack;
import timber.log.Timber;

public class RequestQueueManager implements RequestQueue.RequestFinishedListener<Object> {
    private static RequestQueueManager mInstance;

    private RequestQueue mRequestQueue;
    private int nbRequests = 0;

    private RequestQueueManager(Context context) {
        mRequestQueue = getRequestQueue(context);
    }

    public static synchronized RequestQueueManager getInstance() { return getInstance(null); }
    public static synchronized RequestQueueManager getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new RequestQueueManager(context);
        }
        return mInstance;
    }

    private RequestQueue getRequestQueue(Context ctx) {
        if (mRequestQueue == null) {
            mRequestQueue = Volley.newRequestQueue(ctx.getApplicationContext(), new VolleyOkHttp3Stack());
            mRequestQueue.addRequestFinishedListener(this);
        }
        return mRequestQueue;
    }

    public <T> void addToRequestQueue(Request<T> req) {
        mRequestQueue.add(req);
        nbRequests++;
        Timber.d("RequestQueue ::: request added - current total %s", nbRequests);
    }

    public void onRequestFinished(Request request)
    {
        nbRequests--;
        Timber.d("RequestQueue ::: request removed - current total %s", nbRequests);
    }

    public void cancelQueue()
    {
        RequestQueue.RequestFilter filterForAll = request -> true;
        mRequestQueue.cancelAll(filterForAll);
        Timber.d("RequestQueue ::: canceled");
    }
}
