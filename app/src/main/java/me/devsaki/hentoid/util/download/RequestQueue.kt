package me.devsaki.hentoid.util.download

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.annimon.stream.Optional
import com.annimon.stream.function.BiConsumer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.StringHelper
import me.devsaki.hentoid.util.exception.DownloadInterruptedException
import me.devsaki.hentoid.util.exception.NetworkingException
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.network.HttpHelper
import org.apache.commons.lang3.tuple.ImmutablePair
import org.apache.commons.lang3.tuple.ImmutableTriple
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
        while (downloadsQueue.size > 0) {
            downloadsQueue.poll()?.let {
                it.killSwitch.set(true)
                Timber.v("Aborting download request %s", it.url)
            }
        }
        active = false
    }

    suspend fun executeRequest(requestOrder: RequestOrder) {
        if (!active) {
            Timber.d("Can't execute a request while request queue is inactive!")
            return
        }

        downloadsQueue.add(requestOrder)
        try {
            val res = withContext(Dispatchers.IO) {
                downloadPic(
                    requestOrder.site,
                    requestOrder.url,
                    requestOrder.headers,
                    requestOrder.targetDir,
                    requestOrder.fileName,
                    requestOrder.pageIndex,
                    requestOrder.killSwitch
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
        resultOpt: Optional<ImmutableTriple<Int, Uri, String>>
    ) {
        // Nothing to download => this is actually an error
        if (resultOpt.isEmpty) {
            handleError(requestOrder, ParseException("No image found"))
            return
        }

        handleComplete(requestOrder)
        successHandler.accept(
            requestOrder,
            resultOpt.get().middle
        )
    }

    private fun handleError(requestOrder: RequestOrder, t: Throwable) {
        handleComplete(requestOrder)

        var statusCode = 0
        var errorCode = RequestOrder.NetworkErrorType.NETWORK_ERROR
        val message = StringHelper.protect(t.message)

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
        errorHandler.accept(requestOrder, error)
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
    private fun downloadPic(
        site: Site,
        url: String,
        headers: Map<String, String>,
        targetFolder: DocumentFile,
        targetFileNameNoExt: String,
        pageIndex: Int,
        killSwitch: AtomicBoolean
    ): Optional<ImmutableTriple<Int, Uri, String>> {
        Helper.assertNonUiThread()

        val requestHeaders = HttpHelper.webkitRequestHeadersToOkHttpHeaders(headers, url)
        requestHeaders.add(
            androidx.core.util.Pair(
                "Accept",
                "image/jpeg,image/png,image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*"
            )
        ) // Required to pass through cloudflare filtering on some sites

        // Initiate download
        val result = DownloadHelper.downloadToFile(
            site,
            url,
            pageIndex,
            HttpHelper.webkitRequestHeadersToOkHttpHeaders(headers, url),
            targetFolder.uri,
            targetFileNameNoExt,
            null,
            false,
            killSwitch,
            null
        )

        val targetFileUri = result.first
        val mimeType = result.second

        return Optional.of(
            ImmutableTriple(
                pageIndex,
                targetFileUri,
                mimeType
            )
        )
    }
}