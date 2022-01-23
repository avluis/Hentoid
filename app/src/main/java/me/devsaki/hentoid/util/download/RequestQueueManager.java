package me.devsaki.hentoid.util.download;

import android.app.ActivityManager;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.Volley;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import org.threeten.bp.Instant;

import java.io.File;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.network.VolleyOkHttp3Stack;
import timber.log.Timber;

/**
 * Manager class for image download queue (Volley)
 */
public class RequestQueueManager<T> implements RequestQueue.RequestEventListener {
    private static RequestQueueManager mInstance;           // Instance of the singleton
    private static final int TIMEOUT_MS = 15000;

    private RequestQueue mRequestQueue;                     // Volley download request queue
    private int nbActiveRequests = 0;                       // Number of requests currently in the queue (for debug display)
    private int downloadThreadCap = -1;                     // Number of allowed parallel download threads

    private boolean isSimulateHumanReading = false;
    private final LinkedList<Request<T>> waitingRequestQueue = new LinkedList<>();
    private final CompositeDisposable waitDisposable = new CompositeDisposable();

    private int nbRequestsPerSecond = -1;
    private final Queue<Long> previousRequestsTimestamps = new LinkedList<>();


    private RequestQueueManager(Context context) {
        int dlThreadCount = getThreadCount(context);
        FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
        crashlytics.setCustomKey("Download thread count", dlThreadCount);

        initRequestQueue(context, dlThreadCount, TIMEOUT_MS);
    }

    private static int getThreadCount(Context context) {
        int result = Preferences.getDownloadThreadCount();
        if (result == Preferences.Constant.DOWNLOAD_THREAD_COUNT_AUTO) {
            result = getSuggestedThreadCount(context);
        }
        return result;
    }

    private static int getSuggestedThreadCount(Context context) {
        final int threshold = 64;
        final int maxThreads = 4;

        int memoryClass = getMemoryClass(context);
        FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
        crashlytics.setCustomKey("Memory class", memoryClass);

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
     */
    private void initRequestQueue(Context ctx, int timeoutMs) { // This is the safest code, as it relies on standard Volley interface
        if (mRequestQueue == null) {
            mRequestQueue = Volley.newRequestQueue(ctx.getApplicationContext(), new VolleyOkHttp3Stack(timeoutMs));
            mRequestQueue.addRequestEventListener(this);
        }
    }

    private void initRequestQueue(Context ctx, int nbDlThreads, int timeoutMs) {
        if (mRequestQueue == null) {
            mRequestQueue = createRequestQueue(ctx, nbDlThreads, timeoutMs);
            mRequestQueue.addRequestEventListener(this);
            mRequestQueue.start();
        }
    }

    private void forceRequestQueue(Context ctx, int nbDlThreads, int timeoutMs) {
        if (mRequestQueue != null) {
            mRequestQueue.removeRequestEventListener(this);
            mRequestQueue.stop();
            mRequestQueue = null;
        }
        initRequestQueue(ctx, nbDlThreads, timeoutMs);
    }

    // Freely inspired by inner workings of Volley.java and RequestQueue.java; to be watched closely as Volley evolves
    private RequestQueue createRequestQueue(Context ctx, int nbDlThreads, int timeoutMs) {
        BasicNetwork network = new BasicNetwork(new VolleyOkHttp3Stack(timeoutMs));
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
        return new RequestQueue(new DiskBasedCache(cacheSupplier), network, nbDlThreads);
    }

    /**
     * Add a request to the app's queue
     *
     * @param request Request to addAll to the queue
     */
    public void queueRequest(Request<T> request) {
        if ((isSimulateHumanReading && nbActiveRequests > 0) || nbRequestsPerSecond > -1 && nbActiveRequests == nbRequestsPerSecond) {
            Timber.d("Waiting requests queue ::: request stored for host %s - current total %s", Uri.parse(request.getUrl()).getHost(), waitingRequestQueue.size());
            synchronized (waitingRequestQueue) {
                waitingRequestQueue.add(request);
            }
        } else {
            addToRequestQueue(request);
        }
    }

    private int getAllowedNewRequests(long now) {
        if (nbRequestsPerSecond > -1) {
            synchronized (previousRequestsTimestamps) {
                boolean polled;
                do {
                    polled = false;
                    Long earliestRequestTimestamp = previousRequestsTimestamps.peek();
                    if (null != earliestRequestTimestamp && now - earliestRequestTimestamp > 1000) {
                        previousRequestsTimestamps.poll();
                        polled = true;
                    }
                } while (polled);

                int nbRequestsLastSecond = previousRequestsTimestamps.size();
                return nbRequestsPerSecond - nbRequestsLastSecond;
            }
        } else return Integer.MAX_VALUE;
    }

    private void addToRequestQueue(Request<T> request) {
        long now = Instant.now().toEpochMilli();
        if (getAllowedNewRequests(now) > 0) addToRequestQueue(request, now);
    }

    private void refillRequestQueue() {
        long now = Instant.now().toEpochMilli();
        int allowedNewRequests = getAllowedNewRequests(now);
        while (0 == allowedNewRequests && 0 == nbActiveRequests) { // Dry queue
            Helper.pause(250);
            now = Instant.now().toEpochMilli();
            allowedNewRequests = getAllowedNewRequests(now);
        }

        if (allowedNewRequests > 0) {
            for (int i = 0; i < allowedNewRequests; i++) {
                synchronized (waitingRequestQueue) {
                    if (waitingRequestQueue.isEmpty()) break;
                    Request<T> r = waitingRequestQueue.removeFirst();
                    if (r != null) addToRequestQueue(r, now);
                }
            }
        }
    }

    private void addToRequestQueue(@NonNull Request<T> request, long now) {
        mRequestQueue.add(request);
        nbActiveRequests++;
        if (nbRequestsPerSecond > -1) {
            synchronized (previousRequestsTimestamps) {
                previousRequestsTimestamps.add(now);
            }
        }
        Timber.v("Global requests queue ::: request added for host %s - current total %s", Uri.parse(request.getUrl()).getHost(), nbActiveRequests);
    }

    public void restartRequestQueue() {
        mRequestQueue.stop();
        mRequestQueue.start();
    }

    /**
     * Generic handler called when a request is completed
     * NB : This method is run on the app's main thread
     *
     * @param request Completed request
     */
    public void onRequestFinished(Request<T> request) {
        nbActiveRequests--;
        Timber.v("Global requests queue ::: request removed for host %s - current total %s", Uri.parse(request.getUrl()).getHost(), nbActiveRequests);

        if (!waitingRequestQueue.isEmpty()) {
            if (isSimulateHumanReading && 0 == nbActiveRequests) {
                // Wait on a separate thread as we're currently on the app's main thread
                int delayMs = 500 + new Random().nextInt(1500);
                Timber.d("Waiting requests queue ::: waiting %d ms", delayMs);
                waitDisposable.add(Observable.timer(delayMs, TimeUnit.MILLISECONDS)
                        .subscribeOn(Schedulers.computation())
                        .observeOn(Schedulers.computation())
                        .map(v -> {
                            // Add the next request to the queue
                            Timber.d("Waiting requests queue ::: request added for host %s - current total %s", Uri.parse(request.getUrl()).getHost(), waitingRequestQueue.size());
                            synchronized (waitingRequestQueue) {
                                Request<T> req = waitingRequestQueue.removeFirst();
                                addToRequestQueue(req);
                            }
                            return true;
                        })
                        .observeOn(Schedulers.computation())
                        .subscribe(Helper.EMPTY_CONSUMER, Timber::e)
                );
            }
            if (nbRequestsPerSecond > -1) {
                waitDisposable.add(Completable.fromRunnable(this::refillRequestQueue)
                        .subscribeOn(Schedulers.computation())
                        .observeOn(Schedulers.computation())
                        .subscribe(Helper.EMPTY_ACTION, Timber::e)
                );
            }
        } else { // No more requests to add
            waitDisposable.clear();
        }
    }

    public void setSimulateHumanReading(boolean value) {
        isSimulateHumanReading = value;
    }

    public boolean isSimulateHumanReading() {
        return isSimulateHumanReading;
    }

    public void setNbRequestsPerSecond(int value) {
        nbRequestsPerSecond = value;
    }

    // This will cancel any current download
    public void setDownloadThreadCount(@NonNull Context ctx, int value) {
        downloadThreadCap = value;
        int dlThreadCount = value;
        if (-1 == downloadThreadCap) dlThreadCount = getThreadCount(ctx);
        forceRequestQueue(ctx, dlThreadCount, TIMEOUT_MS);
    }

    public int getDownloadThreadCap() {
        return downloadThreadCap;
    }

    /**
     * Cancel the app's request queue : cancel all requests remaining in the queue
     */
    public void cancelQueue() {
        RequestQueue.RequestFilter filterForAll = request -> true;
        mRequestQueue.cancelAll(filterForAll);
        synchronized (waitingRequestQueue) {
            waitingRequestQueue.clear();
        }
        isSimulateHumanReading = false;
        waitDisposable.clear();
        Timber.d("RequestQueue ::: canceled");
    }

    @Override
    public void onRequestEvent(Request<?> request, int event) {
        if (event == RequestQueue.RequestEvent.REQUEST_FINISHED) {
            onRequestFinished((Request<T>) request); // https://github.com/google/volley/issues/403
        }
    }
}
