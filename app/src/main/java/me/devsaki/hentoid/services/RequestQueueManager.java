package me.devsaki.hentoid.services;

import android.app.ActivityManager;
import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.Volley;
import com.crashlytics.android.Crashlytics;

import java.io.File;

import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.VolleyOkHttp3Stack;
import timber.log.Timber;

/**
 * Created by Robb_w on 2018/04
 * Manager class for image download queue (Volley)
 *
 * NB : Class looks like a singleton but isn't really once, since it is reinstanciated everytime forceSlowMode changes
 */
public class RequestQueueManager implements RequestQueue.RequestFinishedListener<Object> {
    private static RequestQueueManager mInstance;   // Instance of the singleton
    private static Boolean isSlowMode = null;       // True if current instance a "slow mode" (=1 thread) instance
    private static final int TIMEOUT_MS = 15000;

    private RequestQueue mRequestQueue;             // Volley download request queue
    private int nbRequests = 0;                     // Number of requests currently in the queue (for debug display)


    private RequestQueueManager(Context context, boolean forceSlowMode) {
        int nbDlThreads = Preferences.getDownloadThreadCount();
        if (forceSlowMode) nbDlThreads = 1;
        else if (nbDlThreads == Preferences.Constant.DOWNLOAD_THREAD_COUNT_AUTO) {
            nbDlThreads = getSuggestedThreadCount(context);
        }
        Crashlytics.setInt("Download thread count", nbDlThreads);

        mRequestQueue = getRequestQueue(context, nbDlThreads);
        isSlowMode = forceSlowMode;
    }

    private int getSuggestedThreadCount(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) return 4;

        int memoryClass = activityManager.getMemoryClass();
        Crashlytics.setInt("Memory class", memoryClass);

        if (memoryClass <= 64) {
            return 1;
        } else if (memoryClass <= 96) {
            return 2;
        } else if (memoryClass <= 128) {
            return 3;
        } else {
            return 4;
        }
    }

    public static synchronized RequestQueueManager getInstance() {
        return getInstance(null, false);
    }

    public static synchronized RequestQueueManager getInstance(Context context, boolean forceSlowMode) {
        if (context != null && (mInstance == null || (null == isSlowMode || isSlowMode != forceSlowMode))) {
            mInstance = new RequestQueueManager(context, forceSlowMode);
        }
        return mInstance;
    }

    /**
     * Returns the app's Volley request queue
     *
     * @param ctx App context
     * @return Hentoid Volley request queue
     */
    private RequestQueue getRequestQueue(Context ctx) { // This is the safest code, as it relies on standard Volley interface
        if (mRequestQueue == null) {
            mRequestQueue = Volley.newRequestQueue(ctx.getApplicationContext(), new VolleyOkHttp3Stack(TIMEOUT_MS));
            mRequestQueue.addRequestFinishedListener(this);
        }
        return mRequestQueue;
    }

    private RequestQueue getRequestQueue(Context ctx, int nbDlThreads) { // Freely inspired by inner workings of Volley.java and RequestQueue.java; to be watched closely as Volley evolves
        if (mRequestQueue == null) {
            BasicNetwork network = new BasicNetwork(new VolleyOkHttp3Stack(TIMEOUT_MS));

            File cacheDir = new File(ctx.getCacheDir(), "volley"); // NB : this is dirty, as this value is supposed to be private in Volley.java
            mRequestQueue = new RequestQueue(new DiskBasedCache(cacheDir), network, nbDlThreads);
            mRequestQueue.addRequestFinishedListener(this);
            mRequestQueue.start();
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
