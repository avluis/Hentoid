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
//import com.crashlytics.android.Crashlytics;

import org.threeten.bp.Instant;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Completable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.network.OkHttpClientSingleton;
import timber.log.Timber;

/**
 * Manager class for image download queue (Volley)
 */
@SuppressWarnings("squid:S3077")
// https://stackoverflow.com/questions/11639746/what-is-the-point-of-making-the-singleton-instance-volatile-while-using-double-l
public class RequestQueueManager implements RequestQueue.RequestEventListener {
    private static volatile RequestQueueManager mInstance;
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int IO_TIMEOUT_MS = 15000;

    // Volley download request queue
    private RequestQueue mRequestQueue;
    // Maximum number of allowed parallel download threads (-1 = not capped)
    private int downloadThreadCap = -1;
    // Actual number of allowed parallel download threads
    private int downloadThreadCount = 0;
    // Maximum number of allowed requests per second (-1 = not capped)
    private int nbRequestsPerSecond = -1;
    // Used when waiting between requests
    private final CompositeDisposable waitDisposable = new CompositeDisposable();

    // Requests waiting to be executed
    private final LinkedList<RequestOrder> waitingRequestQueue = new LinkedList<>();
    // Requests being currently executed
    private final Set<RequestOrder> currentRequests = new HashSet<>();

    // Measurement of the number of requests per second
    private final Queue<Long> previousRequestsTimestamps = new LinkedList<>();

    // True if the queue is being initialized
    private final AtomicBoolean isInit = new AtomicBoolean(false);


    private RequestQueueManager(Context context) {
        downloadThreadCount = getPreferredThreadCount(context);
        //Crashlytics.setInt("Download thread count", dlThreadCount);
        //crashlytics.setCustomKey("Download thread count", dlThreadCount);

        init(context, downloadThreadCount, CONNECT_TIMEOUT_MS, IO_TIMEOUT_MS, true, false);
    }

    /**
     * Get the instance of the RequestQueueManager singleton
     *
     * @param context Context to use
     * @return Instance of the RequestQueueManager singleton
     */
    public static synchronized RequestQueueManager getInstance(Context context) {
        if (context != null && mInstance == null) {
            synchronized (RequestQueueManager.class) {
                if (mInstance == null) mInstance = new RequestQueueManager(context);
            }
        }
        return mInstance;
    }

    /**
     * Initialize the Volley request queue
     *
     * @param ctx              Context to use
     * @param connectTimeoutMs Connect timeout to use (ms)
     * @param ioTimeoutMs      I/O timeout to use (ms)
     */
    private void init(Context ctx, int connectTimeoutMs, int ioTimeoutMs) { // This is the safest code, as it relies on standard Volley interface
        if (mRequestQueue == null) {
            mRequestQueue = Volley.newRequestQueue(ctx.getApplicationContext(), new VolleyOkHttp3Stack(connectTimeoutMs, ioTimeoutMs));
            mRequestQueue.addRequestEventListener(this);
        }
    }

    /**
     * Initialize the Volley request queue using the given number of parallel downloads
     *
     * @param ctx              Context to use
     * @param nbDlThreads      Number of parallel downloads to use; -1 to use automated recommendation
     * @param connectTimeoutMs Connect timeout to use (ms)
     * @param ioTimeoutMs      I/O timeout to use (ms)
     * @param cancelQueue      True if queued requests should be canceled; false if it should be kept intact
     * @param resetOkHttp      If true, also reset the underlying OkHttp connections
     */
    private void init(Context ctx, int nbDlThreads, int connectTimeoutMs, int ioTimeoutMs, boolean cancelQueue, boolean resetOkHttp) {
        isInit.set(true);
        Timber.d("Init using %d Dl threads", nbDlThreads);
        try {
            if (mRequestQueue != null) {
                mRequestQueue.removeRequestEventListener(this);
                if (cancelQueue) cancelQueue();
                mRequestQueue.stop();
                mRequestQueue = null;
            }

            if (resetOkHttp) OkHttpClientSingleton.reset();

            mRequestQueue = createRequestQueue(ctx, nbDlThreads, connectTimeoutMs, ioTimeoutMs);
            mRequestQueue.addRequestEventListener(this);
            mRequestQueue.start();
        } finally {
            isInit.set(false);
        }
    }

    /**
     * Initialize the Volley request queue using the given number of parallel downloads
     *
     * @param ctx         Context to use
     * @param nbDlThreads Number of parallel downloads to use; -1 to use automated recommendation
     * @param cancelQueue True if queued requests should be canceled; false if it should be kept intact
     */
    public void initUsingDownloadThreadCount(@NonNull Context ctx, int nbDlThreads, boolean cancelQueue) {
        downloadThreadCap = nbDlThreads;
        downloadThreadCount = nbDlThreads;
        if (-1 == downloadThreadCap) downloadThreadCount = getPreferredThreadCount(ctx);
        init(ctx, downloadThreadCount, CONNECT_TIMEOUT_MS, IO_TIMEOUT_MS, cancelQueue, false);
    }

    /**
     * Return the number of parallel downloads (download thread count) chosen by the user
     *
     * @param context Context to use
     * @return Number of parallel downloads (download thread count) chosen by the user
     */
    private static int getPreferredThreadCount(Context context) {
        int result = Preferences.getDownloadThreadCount();
        if (result == Preferences.Constant.DOWNLOAD_THREAD_COUNT_AUTO) {
            result = getSuggestedThreadCount(context);
        }
        return result;
    }

    /**
     * Return the automatic download thread count calculated from the device's memory capacity
     *
     * @param context Context to use
     * @return automatic download thread count calculated from the device's memory capacity
     */
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

    /**
     * Return the device's per-app memory capacity
     *
     * @param context Context to use
     * @return Device's per-app memory capacity
     */
    private static int getMemoryClass(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) return 0;
        return activityManager.getMemoryClass();
    }

    /**
     * Reset the entire queue
     *
     * @param ctx         Context to use
     * @param resetOkHttp If true, also reset the underlying OkHttp connections
     */
    public void resetRequestQueue(@NonNull Context ctx, boolean resetOkHttp) {
        init(ctx, downloadThreadCount, CONNECT_TIMEOUT_MS, IO_TIMEOUT_MS, false, resetOkHttp);
        // Requeue interrupted requests
        synchronized (currentRequests) {
            Timber.d("resetRequestQueue :: Requeuing %d requests", currentRequests.size());
            for (RequestOrder order : currentRequests) executeRequest(order);
        }
        refill();
    }

    /**
     * Restart the current request queue (cancel, then re-execute all pending requests)
     */
    public void restartRequestQueue() {
        if (mRequestQueue != null) {
            mRequestQueue.removeRequestEventListener(this); // Prevent interrupted requests from messing with downloads
            mRequestQueue.cancelAll(request -> true);
            mRequestQueue.addRequestEventListener(this);
            synchronized (currentRequests) {
                // Requeue interrupted requests
                for (RequestOrder order : currentRequests)
                    executeRequest(order);
            }
            refill();
        }
    }

    /**
     * Cancel the app's request queue : cancel all requests remaining in the queue
     */
    public void cancelQueue() {
        mRequestQueue.cancelAll(request -> true);
        synchronized (waitingRequestQueue) {
            waitingRequestQueue.clear();
        }
        synchronized (currentRequests) {
            currentRequests.clear();
        }
        waitDisposable.clear();
        Timber.d("RequestQueue ::: canceled");
    }

    // Freely inspired by inner workings of Volley.java and RequestQueue.java; to be watched closely as Volley evolves
    private RequestQueue createRequestQueue(Context ctx, int nbDlThreads, int connectTimeoutMs, int ioTimeoutMs) {
        BasicNetwork network = new BasicNetwork(new VolleyOkHttp3Stack(connectTimeoutMs, ioTimeoutMs));
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
     * @param order Request to add to the queue
     */
    public void queueRequest(RequestOrder order) {
        long now = Instant.now().toEpochMilli();
        if (getAllowedNewRequests(now) > 0) executeRequest(order, now);
        else {
            synchronized (waitingRequestQueue) {
                waitingRequestQueue.add(order);
            }
        }
    }

    /**
     * Get the number of new requests that can be executed at the given timestamp
     * This method is where the number of parallel downloads and the download rate limitations
     * are actually used
     *
     * @param now Timestamp to consider
     * @return Number of new requests that can be executed at the given timestamp
     */
    private int getAllowedNewRequests(long now) {
        int remainingSlots = downloadThreadCount - getNbActiveRequests();
        if (0 == remainingSlots) return 0;

        if (nbRequestsPerSecond > -1) {
            synchronized (previousRequestsTimestamps) {
                boolean polled;
                do {
                    polled = false;
                    Long earliestRequestTimestamp = previousRequestsTimestamps.peek();
                    if (null == earliestRequestTimestamp) break; // Empty collection
                    if (now - earliestRequestTimestamp > 1000) {
                        previousRequestsTimestamps.poll();
                        polled = true;
                    }
                } while (polled);

                int nbRequestsLastSecond = previousRequestsTimestamps.size();
                return Math.min(remainingSlots, nbRequestsPerSecond - nbRequestsLastSecond);
            }
        } else return remainingSlots;
    }

    /**
     * Refill the queue with the allowed number of requests
     */
    private void refill() {
        if (getNbActiveRequests() < downloadThreadCount) {
            waitDisposable.add(Completable.fromRunnable(this::doRefill)
                    .subscribeOn(Schedulers.computation())
                    .observeOn(Schedulers.computation())
                    .subscribe(Helper.EMPTY_ACTION, Timber::e)
            );
        }
    }

    /**
     * Refill the queue with the allowed number of requests
     */
    private synchronized void doRefill() {
        long now = Instant.now().toEpochMilli();
        int allowedNewRequests = getAllowedNewRequests(now);
        while (0 == allowedNewRequests && 0 == getNbActiveRequests()) { // Dry queue
            Helper.pause(250);
            now = Instant.now().toEpochMilli();
            allowedNewRequests = getAllowedNewRequests(now);
        }

        if (allowedNewRequests > 0) {
            for (int i = 0; i < allowedNewRequests; i++) {
                synchronized (waitingRequestQueue) {
                    if (waitingRequestQueue.isEmpty()) break;
                    RequestOrder o = waitingRequestQueue.removeFirst();
                    if (o != null) executeRequest(o, now);
                }
            }
        }
    }

    /**
     * Execute the given request order now
     *
     * @param order Request order to execute
     */
    private void executeRequest(@NonNull RequestOrder order) {
        executeRequest(order, Instant.now().toEpochMilli());
    }

    /**
     * Execute the given request order at the given timestamp
     *
     * @param order Request order to execute
     * @param now   Tiemstamp to record the execution for
     */
    private void executeRequest(@NonNull RequestOrder order, long now) {
        Timber.d("Waiting requests queue ::: request executed for host %s - current total %s", Uri.parse(order.getUrl()).getHost(), waitingRequestQueue.size());
        synchronized (currentRequests) {
            currentRequests.add(order);
        }
        mRequestQueue.add(new InputStreamVolleyRequest<>(order));
        if (nbRequestsPerSecond > -1) {
            synchronized (previousRequestsTimestamps) {
                previousRequestsTimestamps.add(now);
            }
        }
        Timber.v("Global requests queue ::: request added for host %s - current total %s", Uri.parse(order.getUrl()).getHost(), getNbActiveRequests());
    }

    /**
     * Generic handler called when a request is completed
     * NB : This method is run on the app's main thread
     *
     * @param request Completed request
     */
    public void onRequestFinished(Request<?> request) {
        if (request.hasHadResponseDelivered()) {
            synchronized (currentRequests) {
                //noinspection SuspiciousMethodCalls
                currentRequests.remove(request.getTag()); // tag _is_ the original RequestOrder
            }
        }

        Timber.v("Global requests queue ::: request removed for host %s - current total %s", Uri.parse(request.getUrl()).getHost(), getNbActiveRequests());

        if (!waitingRequestQueue.isEmpty()) {
            refill();
        } else { // No more requests to add
            waitDisposable.clear();
        }
    }

    public void setNbRequestsPerSecond(int value) {
        nbRequestsPerSecond = value;
    }

    public int getDownloadThreadCap() {
        return downloadThreadCap;
    }

    private int getNbActiveRequests() {
        synchronized (currentRequests) {
            return currentRequests.size();
        }
    }

    public boolean isInit() {
        return isInit.get();
    }

    @Override
    public void onRequestEvent(Request<?> request, int event) {
        if (event == RequestQueue.RequestEvent.REQUEST_FINISHED) {
            onRequestFinished(request); // https://github.com/google/volley/issues/403
        }
    }
}
