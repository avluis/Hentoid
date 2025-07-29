package me.devsaki.hentoid.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.graphics.createBitmap
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.activities.sources.CustomWebViewClient
import me.devsaki.hentoid.core.Consumer
import me.devsaki.hentoid.enums.PictureEncoder
import me.devsaki.hentoid.util.copy
import me.devsaki.hentoid.util.file.createFile
import me.devsaki.hentoid.util.file.getOutputStream
import me.devsaki.hentoid.util.getRandomInt
import me.devsaki.hentoid.util.image.MIME_IMAGE_PNG
import me.devsaki.hentoid.util.image.transcodeTo
import me.devsaki.hentoid.util.pause
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class WysiwygBackgroundWebView(
    context: Context,
    private val client: CustomWebViewClient,
    isScreencap: Boolean = false,
    downloadFolder: DocumentFile? = null
) : WebView(context) {

    var isLoaded = AtomicBoolean(false)
    private var imageIndex: Int = 0
    var result: WysiwigResult? = null

    init {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptThirdPartyCookies(this, true)
        val webSettings = settings
        webSettings.userAgentString = client.site.userAgent
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false
        webSettings.domStorageEnabled = true
        webSettings.useWideViewPort = true
        webSettings.javaScriptEnabled = true
        webSettings.loadWithOverviewMode = true
        if (BuildConfig.DEBUG) setWebContentsDebuggingEnabled(true)
        webViewClient = client

        addJavascriptInterface(
            JsContentInterface { result = WysiwigResult(it) },
            interfaceName
        )

        if (isScreencap) {
            downloadFolder?.let {
                enableSlowWholeDocumentDraw()
                addJavascriptInterface(ScreencapInterface(it), "screencap")
            }
        }
    }

    fun clear() {
        client.destroy()
        removeAllViews()
        destroy()
    }

    inner class JsContentInterface(private val handler: Consumer<Document>) {
        @JavascriptInterface
        @Suppress("unused")
        fun ha(url: String, html: String) {
            synchronized(isLoaded) {
                if (isLoaded.get()) return
                Timber.d("doc %d", imageIndex)
                handler.invoke(Jsoup.parse("<html>$html</html>", url))
                isLoaded.set(true)
            }
        }
    }

    inner class ScreencapInterface(private val downloadFolder: DocumentFile) {
        @JavascriptInterface
        @Suppress("unused")
        fun onLoaded(width: Int, height: Int) {
            synchronized(isLoaded) {
                if (isLoaded.get()) return
                screenCap(downloadFolder, width, height)
            }
        }
    }

    suspend fun loadUrlBlocking(
        url: String,
        imgIndex: Int,
        killSwitch: AtomicBoolean,
        forceGetPage: Boolean = false
    ): WysiwigResult? {
        client.addJsReplacement("\$force_page", forceGetPage.toString())
        isLoaded.set(false)
        result = null
        imageIndex = imgIndex

        Timber.v("Loading %d : %s", imgIndex, url)
        withContext(Dispatchers.Main) {
            loadUrl(url)
        }
        var remainingIterations = 6 * 2 // Timeout 6s
        while (!isLoaded.get() && remainingIterations-- > 0 && !killSwitch.get()) pause(500)
        Timber.v("%s with %d iterations remaining", isLoaded.get(), remainingIterations)
        withContext(Dispatchers.Main) {
            stopLoading()
        }
        if (!isLoaded.get()) return null
        return result
    }

    fun screenCap(downloadFolder: DocumentFile, width: Int, height: Int) {
        Timber.d("screencap %d : %dx%d BEGIN", imageIndex, width, height)
        val ratio = height * 1.0 / width
        // measure the webview
        measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        Timber.v("measured : %dx%d", measuredWidth, measuredHeight)
//layout of webview
        val defWidth = max(width, measuredWidth)
        layout(0, 0, defWidth, max(height, measuredHeight))
        // TODO https://stackoverflow.com/questions/52642055/view-getdrawingcache-is-deprecated-in-android-api-28
        isDrawingCacheEnabled = true
        buildDrawingCache()
//create Bitmap if measured height and width >0
        val b = if (measuredWidth > 0 && measuredHeight > 0) createBitmap(
            defWidth,
            (defWidth * ratio).toInt(),
            Bitmap.Config.ARGB_8888
        )
        else null
// Draw bitmap on canvas
        b?.let {
            try {
                Canvas(it).let { cv ->
                    cv.drawBitmap(it, 0f, 0f, Paint())
                    this.draw(cv)
                }
                result = WysiwigResult(saveBmp(it, downloadFolder))
                isLoaded.set(true)
                Timber.v("screencap $imageIndex : SUCCESS")
            } finally {
                it.recycle()
            }
        }
        Timber.v("screencap $imageIndex : END")
    }

    private fun saveBmp(bmp: Bitmap, downloadFolder: DocumentFile): Uri {
        // Save file in download folder with proper name
        val fileName = String.format(Locale.ENGLISH, "%0" + 5 + "d", imageIndex)
        val newFile = createFile(context, downloadFolder.uri, fileName, MIME_IMAGE_PNG)
        getOutputStream(context, newFile)?.use { out ->
            ByteArrayInputStream(transcodeTo(bmp, PictureEncoder.PNG, 100))
                .use { input -> copy(input, out) }
        }
        return newFile
    }

    companion object {
        val interfaceName = generateName()
        const val FUNCTION_NAME = "ha"

        private fun generateName(): String {
            val sb = StringBuilder()
            repeat(10) {
                val randomChar = 65 + getRandomInt(26)
                sb.append(randomChar.toChar())
            }
            return sb.toString()
        }
    }
}

data class WysiwigResult(
    val doc: Document?,
    val fileUri: Uri?
) {
    constructor(doc: Document) : this(doc, null)

    constructor(uri: Uri) : this(null, uri)

    val isImage: Boolean
        get() = (fileUri != null)
}