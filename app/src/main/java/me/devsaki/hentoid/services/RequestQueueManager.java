package me.devsaki.hentoid.services;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import me.devsaki.hentoid.util.VolleyOkHttp3Stack;
import timber.log.Timber;

/**
 * Created by Robb_w on 2018/04
 * Manager class for image download queue (Volley)
 */
public class RequestQueueManager implements RequestQueue.RequestFinishedListener<Object> {
    private static RequestQueueManager mInstance;   // Instance of the singleton

    private RequestQueue mRequestQueue;             // Volley download request queue
    private int nbRequests = 0;                     // Number of requests currently in the queue (for debug display)

    private RequestQueueManager(Context context) {
        mRequestQueue = getRequestQueue(context);
    }

    public static synchronized RequestQueueManager getInstance() {
        return getInstance(null);
    }

    public static synchronized RequestQueueManager getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new RequestQueueManager(context);
        }
        return mInstance;
    }

    /**
     * Returns the app's Volley request queue
     *
     * @param ctx App context
     * @return Hentoid Volley request queue
     */
    private RequestQueue getRequestQueue(Context ctx) {
        if (mRequestQueue == null) {
            int TIMEOUT_MS = 15000;
            mRequestQueue = Volley.newRequestQueue(ctx.getApplicationContext(), new VolleyOkHttp3Stack(TIMEOUT_MS));
            mRequestQueue.addRequestFinishedListener(this);
        }
        return mRequestQueue;
    }

    /**
     * Add a request to the app's queue
     *
     * @param req Request to add to the queue
     * @param <T> Request content
     */
    public <T> void addToRequestQueue(Request<T> req) {
        mRequestQueue.add(req);
        nbRequests++;
        Timber.d("RequestQueue ::: request added - current total %s", nbRequests);
    }

    /**
     * Generic handler called when a request is completed
     *
     * @param request Completed request
     */
    public void onRequestFinished(Request request) {
        nbRequests--;
        Timber.d("RequestQueue ::: request removed - current total %s", nbRequests);
    }

    /**
     * Cancel the app's request queue : cancel all requests remaining in the queue
     */
    public void cancelQueue() {
        RequestQueue.RequestFilter filterForAll = request -> true;
        mRequestQueue.cancelAll(filterForAll);
        Timber.d("RequestQueue ::: canceled");
    }
}
