package me.devsaki.hentoid.util.download

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.core.BiConsumer
import me.devsaki.hentoid.core.Consumer
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.events.DownloadEvent
import me.devsaki.hentoid.util.exception.DownloadInterruptedException
import me.devsaki.hentoid.util.exception.NetworkingException
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.network.HEADER_ACCEPT_KEY
import me.devsaki.hentoid.util.network.webkitRequestHeadersToOkHttpHeaders
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.io.FileNotFoundException
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class RequestQueue(
    private val successHandler: BiConsumer<RequestOrder, Uri>,
    private val errorHandler: BiConsumer<RequestOrder, RequestOrder.NetworkError>
) {
    var active: Boolean = false
        private set

    private val downloadsQueue: Queue<RequestOrder> = ConcurrentLinkedQueue()

    fun start() {
        active = true
    }

    fun stop() {
        Timber.d("Aborting %d download requests", downloadsQueue.size)
        while (downloadsQueue.isNotEmpty()) {
            downloadsQueue.poll()?.let {
                it.killSwitch.set(true)
                Timber.v("Aborting download request %s", it.url)
            }
        }
        active = false
    }

    suspend fun executeRequest(context: Context, requestOrder: RequestOrder) {
        if (!active) {
            Timber.d("Can't execute a request while request queue is inactive!")
            return
        }

        downloadsQueue.add(requestOrder)
        try {
            val notifyProgress: Consumer<Float>? =
                if (requestOrder.shouldReportIndividualProgress) { f ->
                    EventBus.getDefault().post(
                        DownloadEvent(
                            eventType = DownloadEvent.Type.EV_PROGRESS,
                            fileDownloadProgress = f
                        )
                    )
                } else null
            val res = withContext(Dispatchers.IO) {
                downloadPic(
                    context,
                    requestOrder.site,
                    requestOrder.url,
                    requestOrder.headers,
                    requestOrder.targetDir,
                    requestOrder.fileName,
                    requestOrder.pageIndex,
                    requestOrder.killSwitch,
                    notifyProgress
                )
            }
            handleSuccess(requestOrder, res)
        } catch (e: Exception) {
            handleError(requestOrder, e)
        }
    }

    private fun handleComplete(requestOrder: RequestOrder) {
        downloadsQueue.remove(requestOrder)
    }

    private fun handleSuccess(
        requestOrder: RequestOrder,
        resultOpt: Pair<Int, Uri>?
    ) {
        // Nothing to download => this is actually an error
        if (null == resultOpt) {
            handleError(requestOrder, ParseException("No image found"))
            return
        }

        handleComplete(requestOrder)
        successHandler.invoke(
            requestOrder,
            resultOpt.second
        )
    }

    private fun handleError(requestOrder: RequestOrder, t: Throwable) {
        handleComplete(requestOrder)

        var statusCode = 0
        var errorCode = RequestOrder.NetworkErrorType.NETWORK_ERROR
        val message = t.message ?: ""

        // Classify error messages
        // May happen when resetting OkHttp while some requests are still active
        if (t is java.lang.IllegalStateException && message.contains("cache is closed"))
            errorCode = RequestOrder.NetworkErrorType.INTERRUPTED
        // Purposeful interruption (e.g. pause button or auto-restart)
        if (t is DownloadInterruptedException) errorCode = RequestOrder.NetworkErrorType.INTERRUPTED
        if (t is ParseException) errorCode = RequestOrder.NetworkErrorType.PARSE
        if (t is FileNotFoundException) errorCode = RequestOrder.NetworkErrorType.FILE_IO
        if (message.contains("create file")) errorCode = RequestOrder.NetworkErrorType.FILE_IO
        if (t is NetworkingException) statusCode = t.statusCode

        val error = RequestOrder.NetworkError(
            statusCode,
            message,
            errorCode
        )
        errorHandler.invoke(requestOrder, error)
    }

    /**
     * Download the picture at the given index to the given folder
     *
     * @param pageIndex    Index of the picture to download
     * @param targetFolder Folder to download to
     * @param killSwitch   Switch to interrupt the download
     * @return Optional triple with
     * - The page index
     * - The Uri of the downloaded file
     * - The Mime-type of the downloaded file
     *
     * The return value is empty if the download fails
     */
    private suspend fun downloadPic(
        context: Context,
        site: Site,
        url: String,
        headers: Map<String, String>,
        targetFolder: Uri,
        targetFileNameNoExt: String,
        pageIndex: Int,
        killSwitch: AtomicBoolean,
        notifyProgress: Consumer<Float>? = null
    ): Pair<Int, Uri> {
        val requestHeaders =
            webkitRequestHeadersToOkHttpHeaders(headers, url).toMutableList()
        requestHeaders.add(
            Pair(
                HEADER_ACCEPT_KEY,
                "image/jpeg,image/png,image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*"
            )
        ) // Required to pass through cloudflare filtering on some sites

        // Initiate download
        val result = downloadToFile(
            context,
            site,
            url,
            requestHeaders,
            targetFolder,
            targetFileNameNoExt,
            killSwitch,
            pageIndex,
            failFast = false,
            notifyProgress = notifyProgress
        )
        if (null == result) throw ParseException("Resource not available")

        return Pair(pageIndex, result)
    }
}