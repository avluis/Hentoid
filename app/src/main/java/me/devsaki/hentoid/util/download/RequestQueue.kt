package me.devsaki.hentoid.util.download

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.annimon.stream.Optional
import com.annimon.stream.function.BiConsumer
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
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
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class RequestQueue(
    private val successHandler: BiConsumer<RequestOrder, Uri>,
    private val errorHandler: BiConsumer<RequestOrder, RequestOrder.NetworkError>
) {
    var active: Boolean = false
    private val downloadsQueue: Queue<RequestOrder> = ConcurrentLinkedQueue()
    private val downloadDisposables = ConcurrentHashMap<RequestOrder, Disposable>()

    fun start() {
        active = true
    }

    fun stop() {
        while (downloadsQueue.size > 0) {
            downloadsQueue.poll()?.let {
                it.killSwitch.set(true)
                downloadDisposables.remove(it)?.dispose()
                Timber.d("Aborting download request %s", it.url)
            }
        }
        active = false
    }

    fun executeRequest(requestOrder: RequestOrder) {
        downloadsQueue.add(requestOrder)

        val single = Single.fromCallable {
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

        downloadDisposables[requestOrder] =
            single.subscribeOn(Schedulers.io()) // Download on a thread from the I/O pool
                .observeOn(Schedulers.io()) // Process and store to DB on a thread from the I/O pool too
                .subscribe(
                    { res -> handleSuccess(requestOrder, res) })
                { t -> handleError(requestOrder, t) }
    }

    private fun handleComplete(requestOrder: RequestOrder) {
        downloadsQueue.remove(requestOrder)
        downloadDisposables.remove(requestOrder)?.dispose()
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
        // May happen when resetting OkHttp while some requests are still active
        if (t is java.lang.IllegalStateException && message.contains("cache is closed"))
            errorCode = RequestOrder.NetworkErrorType.INTERRUPTED
        // Purposeful interruption (e.g. pause button or auto-restart)
        if (t is DownloadInterruptedException) errorCode = RequestOrder.NetworkErrorType.INTERRUPTED
        if (t is ParseException) errorCode = RequestOrder.NetworkErrorType.PARSE
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
        val result: ImmutablePair<Uri, String> = DownloadHelper.downloadToFile(
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

        val targetFileUri = result.left
        val mimeType = result.right

        return Optional.of(
            ImmutableTriple(
                pageIndex,
                targetFileUri,
                mimeType
            )
        )
    }
}