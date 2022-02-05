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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int IO_TIMEOUT_MS = 15000;

    // Volley download request queue
    private RequestQueue mRequestQueue;
    // Number of requests currently in the queue (for debug display)
    private final AtomicInteger nbActiveRequests = new AtomicInteger(0);
    // Maximum number of allowed parallel download threads (-1 = not capped)
    private int downloadThreadCap = -1;
    // TODO doc
    private int downloadThreadCount = -1;
    // Maximum number of allowed requests per second (-1 = not capped)
    private int nbRequestsPerSecond = -1;
    // True to mark pauses between pages to simulate human reading
    private boolean isSimulateHumanReading = false;
    // Used when waiting between requests
    private final CompositeDisposable waitDisposable = new CompositeDisposable();

    private final LinkedList<Request<T>> waitingRequestQueue = new LinkedList<>(); // Requests waiting to be executed
    private final Set<Request<T>> currentRequests = new HashSet<>(); // Requests being currently executed

    // Measurement of the number of requests per second
    private final Queue<Long> previousRequestsTimestamps = new LinkedList<>();


    private RequestQueueManager(Context context) {
        int dlThreadCount = getThreadCount(context);
        FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
        crashlytics.setCustomKey("Download thread count", dlThreadCount);

        initRequestQueue(context, dlThreadCount, CONNECT_TIMEOUT_MS, IO_TIMEOUT_MS);
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
    private void initRequestQueue(Context ctx, int connectTimeoutMs, int ioTimeoutMs) { // This is the safest code, as it relies on standard Volley interface
        if (mRequestQueue == null) {
            mRequestQueue = Volley.newRequestQueue(ctx.getApplicationContext(), new VolleyOkHttp3Stack(connectTimeoutMs, ioTimeoutMs));
            mRequestQueue.addRequestEventListener(this);
        }
    }

    private void initRequestQueue(Context ctx, int nbDlThreads, int connectTimeoutMs, int ioTimeoutMs) {
        if (mRequestQueue == null) {
            mRequestQueue = createRequestQueue(ctx, nbDlThreads, connectTimeoutMs, ioTimeoutMs);
            mRequestQueue.addRequestEventListener(this);
            mRequestQueue.start();
        }
    }

    // This will cancel any current download
    public void setDownloadThreadCount(@NonNull Context ctx, int value) {
        downloadThreadCap = value;
        int dlThreadCount = value;
        if (-1 == downloadThreadCap) dlThreadCount = getThreadCount(ctx);
        forceRequestQueue(ctx, dlThreadCount, CONNECT_TIMEOUT_MS, IO_TIMEOUT_MS);
    }

    public void resetRequestQueue(@NonNull Context ctx) {
        setDownloadThreadCount(ctx, downloadThreadCap);
        restartRequestQueue(); // TODO check if that works
    }

    private void forceRequestQueue(Context ctx, int nbDlThreads, int connectTimeoutMs, int ioTimeoutMs) {
        if (mRequestQueue != null) {
            mRequestQueue.removeRequestEventListener(this);
            mRequestQueue.stop();
            mRequestQueue = null;
        }
        synchronized (currentRequests) {
            currentRequests.clear();
            nbActiveRequests.set(0);
        }
        initRequestQueue(ctx, nbDlThreads, connectTimeoutMs, ioTimeoutMs);
    }

    public void restartRequestQueue() {
        if (mRequestQueue != null) {
            mRequestQueue.removeRequestEventListener(this); // Prevent interrupted requests from messing with downloads
            mRequestQueue.cancelAll(request -> true);
            mRequestQueue.addRequestEventListener(this);
            synchronized (currentRequests) {
                // Requeue interrupted requests
                for (Request<T> request : currentRequests) executeRequest(request);
            }
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
            nbActiveRequests.set(0);
        }
        isSimulateHumanReading = false;
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
        downloadThreadCount = nbDlThreads;
        return new RequestQueue(new DiskBasedCache(cacheSupplier), network, nbDlThreads);
    }

    /**
     * Add a request to the app's queue
     *
     * @param request Request to addAll to the queue
     */
    public void queueRequest(Request<T> request) {
        if ((isSimulateHumanReading && nbActiveRequests.get() > 0) || nbRequestsPerSecond > -1 && nbActiveRequests.get() == nbRequestsPerSecond) {
            Timber.d("Waiting requests queue ::: request stored for host %s - current total %s", Uri.parse(request.getUrl()).getHost(), waitingRequestQueue.size());
            synchronized (waitingRequestQueue) {
                waitingRequestQueue.add(request);
            }
        } else {
            executeRequest(request);
        }
    }

    private int getAllowedNewRequests(long now) {
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
                return nbRequestsPerSecond - nbRequestsLastSecond;
            }
        } else return Integer.MAX_VALUE;
    }

    private void executeRequest(Request<T> request) {
        long now = Instant.now().toEpochMilli();
        if (getAllowedNewRequests(now) > 0) executeRequest(request, now);
    }

    private void refillRequestQueue() {
        long now = Instant.now().toEpochMilli();
        int allowedNewRequests = getAllowedNewRequests(now);
        while (0 == allowedNewRequests && 0 == nbActiveRequests.get()) { // Dry queue
            Helper.pause(250);
            now = Instant.now().toEpochMilli();
            allowedNewRequests = getAllowedNewRequests(now);
        }

        if (allowedNewRequests > 0) {
            for (int i = 0; i < allowedNewRequests; i++) {
                synchronized (waitingRequestQueue) {
                    if (waitingRequestQueue.isEmpty()) break;
                    Request<T> r = waitingRequestQueue.removeFirst();
                    if (r != null) executeRequest(r, now);
                }
            }
        }
    }

    private void executeRequest(@NonNull Request<T> request, long now) {
        synchronized (currentRequests) {
            currentRequests.add(request);
            nbActiveRequests.incrementAndGet();
        }
        mRequestQueue.add(request);
        if (nbRequestsPerSecond > -1) {
            synchronized (previousRequestsTimestamps) {
                previousRequestsTimestamps.add(now);
            }
        }
        Timber.v("Global requests queue ::: request added for host %s - current total %s", Uri.parse(request.getUrl()).getHost(), nbActiveRequests);
    }

    /**
     * Generic handler called when a request is completed
     * NB : This method is run on the app's main thread
     *
     * @param request Completed request
     */
    public void onRequestFinished(Request<T> request) {
        if (request.hasHadResponseDelivered()) {
            synchronized (currentRequests) {
                currentRequests.remove(request); // NB : equals and hashCode are InputStreamVolleyRequest's
                nbActiveRequests.decrementAndGet();
            }
        }

        Timber.v("Global requests queue ::: request removed for host %s - current total %s", Uri.parse(request.getUrl()).getHost(), nbActiveRequests);

        if (!waitingRequestQueue.isEmpty()) {
            if (isSimulateHumanReading && 0 == nbActiveRequests.get()) {
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
                                executeRequest(req);
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

    public int getDownloadThreadCap() {
        return downloadThreadCap;
    }

    @Override
    public void onRequestEvent(Request<?> request, int event) {
        if (event == RequestQueue.RequestEvent.REQUEST_FINISHED) {
            onRequestFinished((Request<T>) request); // https://github.com/google/volley/issues/403
        }
    }
}
