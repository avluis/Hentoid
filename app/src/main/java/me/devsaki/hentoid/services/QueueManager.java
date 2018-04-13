package me.devsaki.hentoid.services;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import timber.log.Timber;

public class QueueManager implements RequestQueue.RequestFinishedListener {
    private static QueueManager mInstance;

    private RequestQueue mRequestQueue;
    private int nbRequests = 0;

    private QueueManager(Context context) {
        mRequestQueue = getRequestQueue(context);
    }

    public static synchronized QueueManager getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new QueueManager(context);
        }
        return mInstance;
    }

    private RequestQueue getRequestQueue(Context ctx) {
        if (mRequestQueue == null) {
            mRequestQueue = Volley.newRequestQueue(ctx.getApplicationContext());
            mRequestQueue.addRequestFinishedListener(this);
        }
        return mRequestQueue;
    }

    public <T> void addToRequestQueue(Request<T> req) {
        mRequestQueue.add(req);
        nbRequests++;
        Timber.d("Queue ::: request added - current total %s", nbRequests);
    }

    public void onRequestFinished(Request request)
    {
        nbRequests--;
        Timber.d("Queue ::: request removed - current total %s", nbRequests);
    }

    public boolean isQueueEmpty()
    {
        return (0 == nbRequests);
    }

}
