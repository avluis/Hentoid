package me.devsaki.hentoid.util.download

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.annimon.stream.Optional
import com.annimon.stream.function.BiConsumer
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.StringHelper
import me.devsaki.hentoid.util.exception.DownloadInterruptedException
import me.devsaki.hentoid.util.exception.NetworkingException
import me.devsaki.hentoid.util.network.HttpHelper
import org.apache.commons.lang3.tuple.ImmutablePair
import org.apache.commons.lang3.tuple.ImmutableTriple
import timber.log.Timber
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class RequestQueue(
    private val successHandler: BiConsumer<RequestOrder_, Uri>,
    private val errorHandler: BiConsumer<RequestOrder_, RequestOrder_.NetworkError>
) {
    var active: Boolean = false
    private val downloadsQueue: Queue<RequestOrder_> = ConcurrentLinkedQueue()
    private val downloadDisposables = CompositeDisposable()

    fun start() {
        active = true
    }

    fun stop() {
        while (downloadsQueue.size > 0) {
            val requestOrder = downloadsQueue.poll()
            requestOrder?.killSwitch?.set(true)
            Timber.d("Aborting a download")
        }
        downloadDisposables.clear()
        active = false
    }

    fun executeRequest(requestOrder: RequestOrder_) {
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

        downloadDisposables.add(
            single.subscribeOn(Schedulers.io()) // Download on an IO thread
                .observeOn(Schedulers.io()) // Process and store to DB on an IO thread too
                .subscribe(
                    { resultOpt: Optional<ImmutableTriple<Int, Uri, String>> ->
                        if (resultOpt.isEmpty) { // Nothing to download
                            Timber.d("NO IMAGE FOUND AT INDEX %d", requestOrder.pageIndex)
                            return@subscribe
                        }
                        successHandler.accept(
                            requestOrder,
                            resultOpt.get().middle
                        ) // TODO transmit mime type
                    }) { t: Throwable -> handleError(requestOrder, t) }
        )
    }

    private fun handleError(order_: RequestOrder_, t: Throwable) {
        var statusCode = 0
        var errorCode = RequestOrder_.NetworkErrorType.NETWORK_ERROR
        if (t is DownloadInterruptedException) errorCode =
            RequestOrder_.NetworkErrorType.INTERRUPTED
        if (t is NetworkingException) statusCode = t.statusCode

        val error = RequestOrder_.NetworkError(
            statusCode,
            StringHelper.protect(t.message),
            errorCode
        )
        errorHandler.accept(order_, error)
    }

    /**
     * Download the picture at the given index to the given folder
     *
     * @param pageIndex    Index of the picture to download
     * @param targetFolder Folder to download to
     * @param stopDownload Switch to interrupt the download
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

        val requestHeaders = HttpHelper.webkitRequestHeadersToOkHttpHeaders(headers, url);
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
            killSwitch
        ) { f: Float? ->
            /*
            TODO notify
             */
        }

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