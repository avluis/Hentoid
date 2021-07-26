package me.devsaki.hentoid.util.download;

import android.app.ActivityManager;
import android.content.Context;
import android.net.Uri;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.Volley;
//import com.crashlytics.android.Crashlytics;

import java.io.File;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.network.VolleyOkHttp3Stack;
import timber.log.Timber;

/**
 * Created by Robb_w on 2018/04
 * Manager class for image download queue (Volley)
 * <p>
 * NB : Class looks like a singleton but isn't really one, since it is reinstanciated everytime forceSlowMode changes
 */
public class RequestQueueManager<T> implements RequestQueue.RequestEventListener {
    private static RequestQueueManager mInstance;           // Instance of the singleton
    private static final int TIMEOUT_MS = 15000;

    private RequestQueue mRequestQueue;                     // Volley download request queue
    private int nbRequests = 0;                             // Number of requests currently in the queue (for debug display)

    private boolean isSimulateHumanReading = false;
    private final LinkedList<Request<T>> waitingRequestQueue = new LinkedList<>();
    private Disposable waitDisposable = null;


    private RequestQueueManager(Context context) {
        int dlThreadCount = Preferences.getDownloadThreadCount();
        if (dlThreadCount == Preferences.Constant.DOWNLOAD_THREAD_COUNT_AUTO) {
            dlThreadCount = getSuggestedThreadCount(context);
        }
        //Crashlytics.setInt("Download thread count", dlThreadCount);
        //crashlytics.setCustomKey("Download thread count", dlThreadCount);

        mRequestQueue = getRequestQueue(context, dlThreadCount);
    }

    private static int getSuggestedThreadCount(Context context) {
        final int threshold = 64;
        final int maxThreads = 4;

        int memoryClass = getMemoryClass(context);
        //Crashlytics.setInt("Memory class", memoryClass);
        //crashlytics.setCustomKey("Memory class", memoryClass);

        if (memoryClass == 0) return maxThreads;
        int threadCount = (int) Math.ceil((double) memoryClass / (double) threshold);
        return Math.min(threadCount, maxThreads);
    }

    private static int getMemoryClass(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) return 0;
        return activityManager.getMemoryClass();
    }

    @SuppressWarnings("unchecked")
    public static synchronized <T> RequestQueueManager<T> getInstance(Context context) {
        if (context != null && mInstance == null) {
            mInstance = new RequestQueueManager<T>(context);
        }
        return mInstance;
    }

    /**
     * Start the app's Volley request queue
     *
     * @param ctx App context
     * @return Hentoid Volley request queue
     */
    private RequestQueue getRequestQueue(Context ctx) { // This is the safest code, as it relies on standard Volley interface
        if (mRequestQueue == null) {
            mRequestQueue = Volley.newRequestQueue(ctx.getApplicationContext(), new VolleyOkHttp3Stack(TIMEOUT_MS));
            mRequestQueue.addRequestEventListener(this);
        }
        return mRequestQueue;
    }

    private RequestQueue getRequestQueue(Context ctx, int nbDlThreads) { // Freely inspired by inner workings of Volley.java and RequestQueue.java; to be watched closely as Volley evolves
        if (mRequestQueue == null) {
            BasicNetwork network = new BasicNetwork(new VolleyOkHttp3Stack(TIMEOUT_MS));
            DiskBasedCache.FileSupplier cacheSupplier =
                    new DiskBasedCache.FileSupplier() {
                        private File cacheDir = null;

                        @Override
                        public File get() {
                            if (cacheDir == null) {
                                cacheDir = new File(ctx.getCacheDir(), "volley"); // NB : this is dirty, as this value is supposed to be private in Volley.java
                            }
                            return cacheDir;
                        }
                    };
            mRequestQueue = new RequestQueue(new DiskBasedCache(cacheSupplier), network, nbDlThreads);
            mRequestQueue.addRequestEventListener(this);
            mRequestQueue.start();
        }
        return mRequestQueue;
    }

    /**
     * Add a request to the app's queue
     *
     * @param request Request to addAll to the queue
     */
    public void queueRequest(Request<T> request) {
        if (isSimulateHumanReading && nbRequests > 0) {
            Timber.d("Waiting requests queue ::: request stored for host %s - current total %s", Uri.parse(request.getUrl()).getHost(), waitingRequestQueue.size());
            waitingRequestQueue.add(request);
        } else {
            addToRequestQueue(request);
        }
    }

    private void addToRequestQueue(Request<T> request) {
        mRequestQueue.add(request);
        nbRequests++;
        Timber.v("Global requests queue ::: request added for host %s - current total %s", Uri.parse(request.getUrl()).getHost(), nbRequests);
    }

    /**
     * Generic handler called when a request is completed
     * NB : This method is run on the app's main thread
     *
     * @param request Completed request
     */
    public void onRequestFinished(Request<T> request) {
        nbRequests--;
        Timber.v("Global requests queue ::: request removed for host %s - current total %s", Uri.parse(request.getUrl()).getHost(), nbRequests);

        if (isSimulateHumanReading && 0 == nbRequests && !waitingRequestQueue.isEmpty()) {
            // Wait on a separate thread as we're currently on the app's main thread
            int delayMs = 500 + new Random().nextInt(1500);
            Timber.d("Waiting requests queue ::: waiting %d ms", delayMs);
            waitDisposable = Observable.timer(delayMs, TimeUnit.MILLISECONDS)
                    .subscribeOn(Schedulers.computation())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            v -> {
                                // Add the next request to the queue
                                Timber.d("Waiting requests queue ::: request added for host %s - current total %s", Uri.parse(request.getUrl()).getHost(), waitingRequestQueue.size());
                                Request<T> req = waitingRequestQueue.removeFirst();
                                addToRequestQueue(req);
                                waitDisposable.dispose();
                            },
                            Timber::e
                    );
        }
    }

    public void setSimulateHumanReading(boolean value) {
        isSimulateHumanReading = value;
    }

    public boolean isSimulateHumanReading() {
        return isSimulateHumanReading;
    }

    /**
     * Cancel the app's request queue : cancel all requests remaining in the queue
     */
    public void cancelQueue() {
        RequestQueue.RequestFilter filterForAll = request -> true;
        mRequestQueue.cancelAll(filterForAll);
        waitingRequestQueue.clear();
        isSimulateHumanReading = false;
        if (waitDisposable != null) waitDisposable.dispose();
        Timber.d("RequestQueue ::: canceled");
    }

    @Override
    public void onRequestEvent(Request<?> request, int event) {
        if (event == RequestQueue.RequestEvent.REQUEST_FINISHED) {
            onRequestFinished((Request<T>) request); // https://github.com/google/volley/issues/403
        }
    }
}
