package me.devsaki.hentoid.util.download

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import com.annimon.stream.function.BiConsumer
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.reactivex.Completable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.network.OkHttpClientSingleton
import org.threeten.bp.Instant
import timber.log.Timber
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.min

/**
 * Manager class for image download queue (Volley)
 */
class RequestQueueManager_ private constructor(
    context: Context,
    val onSuccess: BiConsumer<RequestOrder_, Uri>,
    val onError: BiConsumer<RequestOrder_, RequestOrder_.NetworkError>
) {
    var mRequestQueue: RequestQueue? = null

    // Maximum number of allowed parallel download threads (-1 = not capped)
    var downloadThreadCap = -1
        private set

    // Actual number of allowed parallel download threads
    private var downloadThreadCount = 0

    // Maximum number of allowed requests per second (-1 = not capped)
    private var nbRequestsPerSecond = -1

    // Used when waiting between requests
    private val waitDisposable = CompositeDisposable()

    // Requests waiting to be executed
    private val waitingRequestQueue = LinkedList<RequestOrder_>()

    // Requests being currently executed
    private val currentRequests: MutableSet<RequestOrder_> = HashSet()

    // Measurement of the number of requests per second
    private val previousRequestsTimestamps: Queue<Long> = LinkedList()

    // True if the queue is being initialized
    private val ignorableErrors = AtomicInteger(0)


    init {
        downloadThreadCount = getPreferredThreadCount(context)
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCustomKey("Download thread count", downloadThreadCount)
        init(context, downloadThreadCount, CONNECT_TIMEOUT_MS, IO_TIMEOUT_MS, true, false)
    }

    /**
     * Initialize the Volley request queue
     *
     * @param ctx              Context to use
     * @param connectTimeoutMs Connect timeout to use (ms)
     * @param ioTimeoutMs      I/O timeout to use (ms)
     */
    private fun init(
        ctx: Context,
        connectTimeoutMs: Int,
        ioTimeoutMs: Int
    ) { // This is the safest code, as it relies on standard Volley interface
        if (mRequestQueue == null) {
            /*
            mRequestQueue = Volley.newRequestQueue(
                ctx.applicationContext,
                VolleyOkHttp3Stack(connectTimeoutMs, ioTimeoutMs)
            )
            mRequestQueue!!.addRequestEventListener(this)
             */
            mRequestQueue = RequestQueue(this::onRequestSuccess, this::onRequestError)
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
    private fun init(
        ctx: Context,
        nbDlThreads: Int,
        connectTimeoutMs: Int,
        ioTimeoutMs: Int,
        cancelQueue: Boolean,
        resetOkHttp: Boolean
    ) {
        Timber.d("Init using %d Dl threads", nbDlThreads)

        if (cancelQueue) cancelQueue()

        /*
        if (mRequestQueue != null) {
            mRequestQueue!!.removeRequestEventListener(this)
            if (cancelQueue) cancelQueue()
            mRequestQueue!!.stop()
            mRequestQueue = null
        }
         */
        if (resetOkHttp) {
            ignorableErrors.set(nbActiveRequests)
            OkHttpClientSingleton.reset()
        }

        mRequestQueue = RequestQueue(this::onRequestSuccess, this::onRequestError)
        mRequestQueue?.start()

        /*
        mRequestQueue = createRequestQueue(ctx, nbDlThreads, connectTimeoutMs, ioTimeoutMs)
        mRequestQueue!!.addRequestEventListener(this)
        mRequestQueue!!.start()
         */
    }

    /**
     * Initialize the Volley request queue using the given number of parallel downloads
     *
     * @param ctx         Context to use
     * @param nbDlThreads Number of parallel downloads to use; -1 to use automated recommendation
     * @param cancelQueue True if queued requests should be canceled; false if it should be kept intact
     */
    fun initUsingDownloadThreadCount(ctx: Context, nbDlThreads: Int, cancelQueue: Boolean) {
        downloadThreadCap = nbDlThreads
        downloadThreadCount = nbDlThreads
        if (-1 == downloadThreadCap) downloadThreadCount = getPreferredThreadCount(ctx)
        init(ctx, downloadThreadCount, CONNECT_TIMEOUT_MS, IO_TIMEOUT_MS, cancelQueue, false)
    }

    /**
     * Reset the entire queue
     *
     * @param ctx         Context to use
     * @param resetOkHttp If true, also reset the underlying OkHttp connections
     */
    fun resetRequestQueue(ctx: Context, resetOkHttp: Boolean) {
        init(ctx, downloadThreadCount, CONNECT_TIMEOUT_MS, IO_TIMEOUT_MS, false, resetOkHttp)
        // Requeue interrupted requests
        synchronized(currentRequests) {
            Timber.d("resetRequestQueue :: Requeuing %d requests", currentRequests.size)
            for (order in currentRequests) executeRequest(order)
        }
        refill()
    }

    /**
     * Restart the current request queue (cancel, then re-execute all pending requests)
     */
    fun restartRequestQueue() {
        /*
        if (mRequestQueue != null) {
            mRequestQueue!!.removeRequestEventListener(this) // Prevent interrupted requests from messing with downloads
            mRequestQueue!!.cancelAll { request: Request<*>? -> true }
            mRequestQueue!!.addRequestEventListener(this)
            synchronized(currentRequests) {
                // Requeue interrupted requests
                for (order in currentRequests) executeRequest(order)
            }
            refill()
        }
         */

        mRequestQueue?.stop()
        mRequestQueue?.start()

        synchronized(currentRequests) {
            // Requeue interrupted requests
            for (order in currentRequests) executeRequest(order)
        }
        refill()
    }

    /**
     * Cancel the app's request queue : cancel all requests remaining in the queue
     */
    fun cancelQueue() {
//        mRequestQueue!!.cancelAll { request: Request<*>? -> true }
        mRequestQueue?.stop()
        synchronized(waitingRequestQueue) { waitingRequestQueue.clear() }
        synchronized(currentRequests) { currentRequests.clear() }
        ignorableErrors.set(0)
        waitDisposable.clear()
        Timber.d("RequestQueue ::: canceled")
    }
/*
    // Freely inspired by inner workings of Volley.java and RequestQueue.java; to be watched closely as Volley evolves
    private fun createRequestQueue(
        ctx: Context,
        nbDlThreads: Int,
        connectTimeoutMs: Int,
        ioTimeoutMs: Int
    ): RequestQueue {
        val network = BasicNetwork(VolleyOkHttp3Stack(connectTimeoutMs, ioTimeoutMs))
        val cacheSupplier: DiskBasedCache.FileSupplier = object : DiskBasedCache.FileSupplier {
            private var cacheDir: File? = null
            override fun get(): File {
                if (cacheDir == null) {
                    cacheDir = File(
                        ctx.cacheDir,
                        "volley"
                    ) // NB : this is dirty, as this value is supposed to be private in Volley.java
                }
                return cacheDir
            }
        }
        return RequestQueue(DiskBasedCache(cacheSupplier), network, nbDlThreads)
    }
 */

    /**
     * Add a request to the app's queue
     *
     * @param order Request to add to the queue
     */
    fun queueRequest(order: RequestOrder_) {
        val now = Instant.now().toEpochMilli()
        if (getAllowedNewRequests(now) > 0) executeRequest(order, now) else {
            synchronized(waitingRequestQueue) {
                waitingRequestQueue.add(order)
                Timber.d(
                    "Waiting requests queue ::: added new request - current total %d",
                    waitingRequestQueue.size
                )
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
    private fun getAllowedNewRequests(now: Long): Int {
        val remainingSlots = downloadThreadCount - nbActiveRequests
        if (0 == remainingSlots) return 0
        return if (nbRequestsPerSecond > -1) {
            synchronized(previousRequestsTimestamps) {
                var polled: Boolean
                do {
                    polled = false
                    val earliestRequestTimestamp = previousRequestsTimestamps.peek() ?: break
                    // Empty collection
                    if (now - earliestRequestTimestamp > 1000) {
                        previousRequestsTimestamps.poll()
                        polled = true
                    }
                } while (polled)
                val nbRequestsLastSecond = previousRequestsTimestamps.size
                return min(
                    remainingSlots,
                    nbRequestsPerSecond - nbRequestsLastSecond
                )
            }
        } else remainingSlots
    }

    /**
     * Refill the queue with the allowed number of requests
     */
    private fun refill() {
        if (nbActiveRequests < downloadThreadCount) {
            waitDisposable.add(Completable.fromRunnable { doRefill() }
                .subscribeOn(Schedulers.computation())
                .observeOn(Schedulers.computation())
                .subscribe(
                    Helper.EMPTY_ACTION
                ) { t: Throwable? ->
                    Timber.e(
                        t
                    )
                }
            )
        }
    }

    /**
     * Refill the queue with the allowed number of requests
     */
    @Synchronized
    private fun doRefill() {
        var now = Instant.now().toEpochMilli()
        var allowedNewRequests = getAllowedNewRequests(now)
        while (0 == allowedNewRequests && 0 == nbActiveRequests) { // Dry queue
            Helper.pause(250)
            now = Instant.now().toEpochMilli()
            allowedNewRequests = getAllowedNewRequests(now)
        }
        if (allowedNewRequests > 0) {
            synchronized(waitingRequestQueue) {
                for (i in 0 until allowedNewRequests) {
                    if (waitingRequestQueue.isEmpty()) break
                    val o = waitingRequestQueue.removeFirst()
                    o?.let { executeRequest(it, now) }
                }
            }
        }
    }

    /**
     * Execute the given request order at the given timestamp
     * NB : if we're here, that means all quota checks have already passed
     *
     * @param order Request order to execute
     * @param now   Timestamp to record the execution for
     */
    private fun executeRequest(order: RequestOrder_, now: Long = Instant.now().toEpochMilli()) {
        synchronized(currentRequests) { currentRequests.add(order) }
//        mRequestQueue!!.add(InputStreamVolleyRequest<Any>(order))
        mRequestQueue?.executeRequest(order)
        if (nbRequestsPerSecond > -1) {
            synchronized(previousRequestsTimestamps) { previousRequestsTimestamps.add(now) }
        }
        synchronized(waitingRequestQueue) {
            Timber.d(
                "Requests queue ::: request executed for host %s - current total (%d active + %d waiting)",
                Uri.parse(order.url).host,
                nbActiveRequests,
                waitingRequestQueue.size
            )
        }
    }

    /**
     * Generic handler called when a request is completed
     *
     * @param request Completed request
     */
    private fun onRequestSuccess(request: RequestOrder_, resultFileUri: Uri) {
        // No lost requests when force-restarting the queue
        if (!popIgnorableErrors()) {
            synchronized(currentRequests) {
                currentRequests.remove(request)
                Timber.v(
                    "Global requests queue ::: request removed for host %s - current total %s",
                    Uri.parse(request.url).host,
                    nbActiveRequests
                )
            }
        } else {
            Timber.v(
                "Global requests queue ::: no request removed for host %s due to ignorable errors - current total %s",
                Uri.parse(request.url).host,
                nbActiveRequests
            )
        }
        if (!waitingRequestQueue.isEmpty()) {
            refill()
        } else { // No more requests to add
            waitDisposable.clear()
        }
        onSuccess.accept(request, resultFileUri)
    }

    private fun onRequestError(req: RequestOrder_, err: RequestOrder_.NetworkError) {
        onError.accept(req, err)
    }

    fun setNbRequestsPerSecond(value: Int) {
        nbRequestsPerSecond = value
    }

    private val nbActiveRequests: Int
        get() {
            synchronized(currentRequests) { return currentRequests.size }
        }

    fun hasRemainingIgnorableErrors(): Boolean {
        return ignorableErrors.get() > 0
    }

    private fun popIgnorableErrors(): Boolean {
        return if (ignorableErrors.get() > 0) {
            ignorableErrors.getAndDecrement()
            true
        } else false
    }

    /*
    override fun onRequestEvent(request: Request<*>, event: Int) {
        if (event == RequestQueue.RequestEvent.REQUEST_FINISHED) {
            onRequestFinished(request) // https://github.com/google/volley/issues/403
        }
    }
     */

    companion object {
        @Volatile
        private var mInstance: RequestQueueManager_? = null
        private const val CONNECT_TIMEOUT_MS = 4000
        private const val IO_TIMEOUT_MS = 15000

        /**
         * Get the instance of the RequestQueueManager singleton
         *
         * @param context Context to use
         * @return Instance of the RequestQueueManager singleton
         */
        @Synchronized
        fun getInstance(
            context: Context,
            onSuccess: BiConsumer<RequestOrder_, Uri>,
            onError: BiConsumer<RequestOrder_, RequestOrder_.NetworkError>
        ): RequestQueueManager_? {
            if (mInstance == null) {
                synchronized(RequestQueueManager_::class.java) {
                    if (mInstance == null) mInstance =
                        RequestQueueManager_(context, onSuccess, onError)
                }
            }
            return mInstance
        }

        /**
         * Return the number of parallel downloads (download thread count) chosen by the user
         *
         * @param context Context to use
         * @return Number of parallel downloads (download thread count) chosen by the user
         */
        private fun getPreferredThreadCount(context: Context): Int {
            var result = Preferences.getDownloadThreadCount()
            if (result == Preferences.Constant.DOWNLOAD_THREAD_COUNT_AUTO) {
                result = getSuggestedThreadCount(context)
            }
            return result
        }

        /**
         * Return the automatic download thread count calculated from the device's memory capacity
         *
         * @param context Context to use
         * @return automatic download thread count calculated from the device's memory capacity
         */
        private fun getSuggestedThreadCount(context: Context): Int {
            val threshold = 64
            val maxThreads = 4
            val memoryClass = getMemoryClass(context)
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey("Memory class", memoryClass)
            if (memoryClass == 0) return maxThreads
            val threadCount = ceil(memoryClass.toDouble() / threshold.toDouble()).toInt()
            return min(threadCount, maxThreads)
        }

        /**
         * Return the device's per-app memory capacity
         *
         * @param context Context to use
         * @return Device's per-app memory capacity
         */
        private fun getMemoryClass(context: Context): Int {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return activityManager.memoryClass
        }
    }
}