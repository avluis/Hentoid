package me.devsaki.hentoid.services;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import me.devsaki.hentoid.util.VolleyOkHttp3Stack;
import timber.log.Timber;

public class QueueManager implements RequestQueue.RequestFinishedListener<Object> {
    private static QueueManager mInstance;

    private RequestQueue mRequestQueue;
    private int nbRequests = 0;

    private QueueManager(Context context) {
        mRequestQueue = getRequestQueue(context);
    }

    public static synchronized QueueManager getInstance() { return getInstance(null); }
    public static synchronized QueueManager getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new QueueManager(context);
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

    public void pauseQueue()
    {
        mRequestQueue.stop();
        Timber.d("Queue ::: paused");
    }

    public void startQueue()
    {
        mRequestQueue.start();
        Timber.d("Queue ::: started");
    }

    public void cancelQueue()
    {
        RequestQueue.RequestFilter filterForAll = request -> true;
        mRequestQueue.cancelAll(filterForAll);
        Timber.d("Queue ::: canceled");
    }
}
