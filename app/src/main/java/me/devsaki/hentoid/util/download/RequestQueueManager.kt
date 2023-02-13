package me.devsaki.hentoid.util.download

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import com.annimon.stream.function.BiConsumer
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.network.OkHttpClientSingleton
import timber.log.Timber
import java.util.LinkedList
import kotlin.math.ceil
import kotlin.math.min

/**
 * Manager class for image download queue
 */
class RequestQueueManager private constructor(
    context: Context,
    private val onSuccess: BiConsumer<RequestOrder, Uri>,
    private val onError: BiConsumer<RequestOrder, RequestOrder.NetworkError>
) {
    private var mRequestQueue: RequestQueue? = null

    // Maximum number of allowed parallel download threads (-1 = not capped)
    var downloadThreadCap = -1
        private set

    // Actual number of allowed parallel download threads
    private var downloadThreadCount = 0

    // Requests waiting to be executed
    private val waitingRequestQueue = LinkedList<RequestOrder>()

    // Requests being currently executed
    private val activeRequests: MutableSet<RequestOrder> = HashSet()


    init {
        downloadThreadCount = getPreferredThreadCount(context)
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCustomKey("Download thread count", downloadThreadCount)
        init(resetActiveRequests = false, cancelQueue = true)
    }

    /**
     * Initialize the request queue
     *
     * @param cancelQueue     True if queued requests should be canceled; false if it should be kept intact
     * @param resetOkHttp     If true, also reset the underlying OkHttp connections
     */
    private fun init(
        resetActiveRequests: Boolean, cancelQueue: Boolean, resetOkHttp: Boolean = false
    ) {
        if (cancelQueue) cancelQueue()
        else if (resetOkHttp || resetActiveRequests) mRequestQueue?.stop()

        if (resetOkHttp) OkHttpClientSingleton.reset()

        mRequestQueue = RequestQueue(this::onRequestSuccess, this::onRequestError)
        start()
    }

    /**
     * Initialize the request queue
     *
     * @param ctx         Context to use
     * @param nbDlThreads Number of parallel downloads to use; -1 to use automated recommendation
     * @param cancelQueue True if queued requests should be canceled; false if it should be kept intact
     */
    fun initUsingDownloadThreadCount(ctx: Context, nbDlThreads: Int, cancelQueue: Boolean) {
        downloadThreadCap = nbDlThreads
        downloadThreadCount = nbDlThreads
        if (-1 == downloadThreadCap) downloadThreadCount = getPreferredThreadCount(ctx)
        init(false, cancelQueue)
    }

    /**
     * Reset the entire queue
     *
     * @param resetOkHttp If true, also reset the underlying OkHttp connections
     */
    fun resetRequestQueue(resetOkHttp: Boolean) {
        init(true, cancelQueue = false, resetOkHttp = resetOkHttp)
        // Requeue interrupted requests
        synchronized(activeRequests) {
            Timber.d("resetRequestQueue :: Requeuing %d requests", activeRequests.size)
            CoroutineScope(Dispatchers.Default).launch {
                for (order in activeRequests) executeRequest(order)
            }
        }
        refill()
    }

    /**
     * Cancel the app's request queue : cancel all requests remaining in the queue
     */
    fun cancelQueue() {
        mRequestQueue?.stop()
        synchronized(waitingRequestQueue) { waitingRequestQueue.clear() }
        synchronized(activeRequests) { activeRequests.clear() }
        Timber.d("RequestQueue ::: canceled")
    }

    fun start() {
        mRequestQueue?.start()
        Timber.d("RequestQueue ::: started")
    }

    /**
     * Add a request to the app's queue
     *
     * @param order Request to add to the queue
     */
    fun queueRequest(order: RequestOrder) {
        CoroutineScope(Dispatchers.Default).launch {
            if (isNewRequestAllowed()) executeRequest(order) else {
                synchronized(waitingRequestQueue) {
                    waitingRequestQueue.add(order)
                    Timber.d(
                        "Waiting requests queue ::: added new request - current total %d",
                        waitingRequestQueue.size
                    )
                }
            }
        }
    }

    /**
     * Indicates whether a new request can be executed or not
     * NB : This call might _block_ the calling thread according to the current download rate limit
     */
    private fun isNewRequestAllowed(): Boolean {
        val remainingSlots = downloadThreadCount - nbActiveRequests
        if (0 == remainingSlots) return false
        DownloadRateLimiter.take()
        return true
    }

    /**
     * Refill the queue with the allowed number of requests
     */
    private fun refill() {
        if (nbActiveRequests < downloadThreadCount) {
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    doRefill()
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
        }
    }

    /**
     * Refill the queue with the allowed number of requests
     */
    private suspend fun doRefill() {
        var newRequestAllowed = isNewRequestAllowed()
        while (!newRequestAllowed && 0 == nbActiveRequests) { // Dry queue
            Helper.pause(250)
            newRequestAllowed = isNewRequestAllowed()
        }
        if (newRequestAllowed) {
            val o: RequestOrder?
            synchronized(waitingRequestQueue) {
                o = if (!waitingRequestQueue.isEmpty()) waitingRequestQueue.removeFirst()
                else null
            }
            o?.let { executeRequest(it) }
        }
    }

    /**
     * Execute the given request order at the given timestamp
     * NB : if we're here, that means all quota checks have already passed
     *
     * @param order Request order to execute
     */
    private suspend fun executeRequest(order: RequestOrder) {
        mRequestQueue?.let {
            if (!it.active) return
            synchronized(activeRequests) { activeRequests.add(order) }
            it.executeRequest(order)
            synchronized(waitingRequestQueue) {
                Timber.d(
                    "Requests queue ::: request executed for host %s - current total (%d active + %d waiting)",
                    Uri.parse(order.url).host,
                    nbActiveRequests,
                    waitingRequestQueue.size
                )
            }
        }
    }

    /**
     * Generic handler called when a request is completed
     *
     * @param request Completed request
     */
    private fun onRequestCompleted(request: RequestOrder) {
        synchronized(activeRequests) {
            activeRequests.remove(request)
            Timber.v(
                "Global requests queue ::: request removed for host %s - current total %s",
                Uri.parse(request.url).host,
                nbActiveRequests
            )
        }

        if (!waitingRequestQueue.isEmpty()) refill()
    }

    private fun onRequestSuccess(request: RequestOrder, resultFileUri: Uri) {
        onRequestCompleted(request)
        onSuccess.accept(request, resultFileUri)
    }

    private fun onRequestError(request: RequestOrder, err: RequestOrder.NetworkError) {
        onRequestCompleted(request)
        // Don't propagate interruptions
        if (err.type != RequestOrder.NetworkErrorType.INTERRUPTED) onError.accept(request, err)
        else Timber.d("Downloader : Interruption detected for %s : %s", request.url, err.message)
    }

    fun setNbRequestsPerSecond(value: Int) {
        DownloadRateLimiter.setRateLimit(value.toLong())
    }

    private val nbActiveRequests: Int
        get() {
            synchronized(activeRequests) { return activeRequests.size }
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
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return activityManager.memoryClass
    }

    companion object {
        @Volatile
        private var instance: RequestQueueManager? = null

        /**
         * Get the instance of the RequestQueueManager singleton
         *
         * @param context Context to use
         * @return Instance of the RequestQueueManager singleton
         */
        fun getInstance(
            context: Context,
            onSuccess: BiConsumer<RequestOrder, Uri>,
            onError: BiConsumer<RequestOrder, RequestOrder.NetworkError>
        ): RequestQueueManager = instance ?: synchronized(this) {
            instance ?: RequestQueueManager(context, onSuccess, onError).also { instance = it }
        }

        fun getInstance(): RequestQueueManager? {
            synchronized(this) {
                return instance
            }
        }
    }
}