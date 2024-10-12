package me.devsaki.hentoid.util.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.io.source.ByteArrayOutputStream
import com.itextpdf.kernel.colors.Color
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.events.Event
import com.itextpdf.kernel.events.IEventHandler
import com.itextpdf.kernel.events.PdfDocumentEvent
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfNumber
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.canvas.parser.EventType
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData
import com.itextpdf.kernel.pdf.canvas.parser.data.ImageRenderInfo
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.properties.AreaBreakType
import com.itextpdf.layout.properties.HorizontalAlignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.THUMB_FILE_NAME
import me.devsaki.hentoid.util.copy
import me.devsaki.hentoid.util.file.DiskCache
import me.devsaki.hentoid.util.file.getFileNameWithoutExtension
import me.devsaki.hentoid.util.file.getInputStream
import me.devsaki.hentoid.util.file.getOutputStream
import me.devsaki.hentoid.util.file.legacyFileFromUri
import me.devsaki.hentoid.util.file.saveBinary
import net.sf.sevenzipjbinding.SevenZipException
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger


// Inspired by https://github.com/T8RIN/ImageToolbox/blob/master/feature/pdf-tools/src/main/java/ru/tech/imageresizershrinker/feature/pdf_tools/data/AndroidPdfManager.kt
private val PORTRAIT = PdfNumber(0)
private val LANDSCAPE = PdfNumber(90)

class PdfManager {

    private val currentPageIndex = AtomicInteger(0)

    private fun computeScaleRatio(pageSize: PageSize, margin: RectF, imgDims: PointF): Float {
        // Firstly get the scale ratio required to fit the image width
        val pageWidth: Float = pageSize.width - margin.left - margin.right
        var scaleRatio = pageWidth / imgDims.x

        // Get scale ratio required to fit image height - if smaller, use this instead
        val pageHeight: Float = pageSize.height - margin.top - margin.bottom
        val heightScaleRatio: Float = pageHeight / imgDims.y
        if (heightScaleRatio < scaleRatio) scaleRatio = heightScaleRatio

        // Do not upscale - if the entire image can fit in the page, leave it unscaled.
        if (scaleRatio > 1f) scaleRatio = 1f
        return scaleRatio
    }

    private fun adjustImageLayout(
        img: Image,
        doc: Document,
        pageSize: PageSize,
        pageRotation: PdfNumber
    ) {
        // Expecting _radians_ here...
        img.setRotationAngle(pageRotation.value * Math.PI / 180f)

        var imgWidth = if (pageRotation == PORTRAIT) img.imageWidth else img.imageHeight
        var imgHeight = if (pageRotation == PORTRAIT) img.imageHeight else img.imageWidth

        val scaleRatio =
            computeScaleRatio(
                pageSize,
                RectF(doc.leftMargin, doc.topMargin, doc.rightMargin, doc.bottomMargin),
                PointF(imgWidth, imgHeight)
            )
        if (scaleRatio < 1F) img.scale(scaleRatio, scaleRatio)

        val docUsefulWidth = pageSize.width - doc.leftMargin - doc.rightMargin
        val docUsefulHeight = pageSize.height - doc.topMargin - doc.bottomMargin

        imgWidth = if (pageRotation == PORTRAIT) img.imageScaledWidth else img.imageScaledHeight
        imgHeight = if (pageRotation == PORTRAIT) img.imageScaledHeight else img.imageScaledWidth
        img.setMarginLeft((docUsefulWidth - imgWidth) / 2)
        img.setMarginTop((docUsefulHeight - imgHeight) / 2)
    }

    private fun processFile(context: Context, doc: DocumentFile, keepFormat: Boolean): ByteArray? {
        // TODO don't keep format when non-PNG/JPG/WEBP
        val stream = ByteArrayOutputStream()
        if (keepFormat) {
            getInputStream(context, doc).use { copy(it, stream) }
            return stream.toByteArray()
        } else {
            loadBitmap(context, doc)?.let { bmp ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
                return stream.toByteArray()
            }
        }
        return null
    }

    fun convertImagesToPdf(
        context: Context,
        out: OutputStream,
        imageFiles: List<DocumentFile>,
        keepImgFormat: Boolean,
        background: android.graphics.Color,
        onProgressChange: ((Float) -> Unit)? = null
    ) {
        PdfDocument(PdfWriter(out)).use { pdfDoc ->
            Document(pdfDoc, PageSize.A4).use { doc ->
                doc.setMargins(0f, 0f, 0f, 0f)
                doc.setHorizontalAlignment(HorizontalAlignment.CENTER)
                val bgColor = Color.createColorWithColorSpace(
                    arrayOf(background.red(), background.green(), background.blue()).toFloatArray()
                )
                doc.setBackgroundColor(bgColor)

                val rotationEventHandler = PageRotationEventHandler()
                pdfDoc.addEventHandler(PdfDocumentEvent.START_PAGE, rotationEventHandler)

                val bgEventHandler = PageBackgroundEventHandler()
                pdfDoc.addEventHandler(PdfDocumentEvent.START_PAGE, bgEventHandler)
                bgEventHandler.setBackground(bgColor)

                imageFiles
                    .asSequence()
                    .filter { isSupportedImage(it.name ?: "") }
                    .filterNot {
                        getFileNameWithoutExtension(it.name ?: "")
                            .equals(THUMB_FILE_NAME, true)
                    }
                    .mapNotNull { processFile(context, it, keepImgFormat) }
                    .forEachIndexed { index, data ->
                        val image = Image(ImageDataFactory.create(data))

                        val isImageLandscape = image.imageWidth > image.imageHeight * 1.33
                        val pageRotation = if (isImageLandscape) LANDSCAPE else PORTRAIT
                        adjustImageLayout(image, doc, PageSize.A4, pageRotation)
                        rotationEventHandler.setRotation(pageRotation)

                        if (index > 0) doc.add(AreaBreak(AreaBreakType.NEXT_PAGE))
                        doc.add(image)

                        onProgressChange?.invoke((index + 1) * 1f / imageFiles.size)
                    }
                doc.flush()
            }
        }
    }

    private fun cacheExtractedImage(
        id: String,
        data: ByteArray,
        onExtracted: ((String, Uri) -> Unit)?
    ) {
        val fileCreator: (String) -> File =
            { targetFileName -> File(DiskCache.createFile(targetFileName).path!!) }
        val fileFinder: (String) -> Uri? = { targetFileName -> DiskCache.getFile(targetFileName) }

        val existing = fileFinder.invoke(id)
        Timber.v("Extract PDF, get stream: id $id")
        try {
            val targetFile: File
            if (null == existing) {
                targetFile = fileCreator.invoke(id)
                targetFile.createNewFile()
            } else {
                targetFile = legacyFileFromUri(existing)!!
            }
            val uri = Uri.fromFile(targetFile)
            getOutputStream(targetFile).use { saveBinary(it, data) }
            onExtracted?.invoke(id, uri)
        } catch (e: IOException) {
            throw SevenZipException(e)
        }
    }

    fun extractImagesCached(
        context: Context,
        doc: DocumentFile,
        entriesToExtract: List<Pair<String, String>>?,
        interrupt: AtomicBoolean?,
        onExtracted: ((String, Uri) -> Unit)?,
        onComplete: () -> Unit
    ) {
        return extractImages(
            context,
            doc,
            entriesToExtract, interrupt,
            { id, data -> cacheExtractedImage(id, data, onExtracted) },
            onComplete
        )
    }

    fun extractImages(
        context: Context,
        doc: DocumentFile,
        entriesToExtract: List<Pair<String, String>>?,
        interrupt: AtomicBoolean?,
        onExtract: (String, ByteArray) -> Unit,
        onComplete: () -> Unit
    ) {
        getInputStream(context, doc).use { input ->
            PdfDocument(PdfReader(input), PdfWriter(ByteArrayOutputStream())).use { pdfDoc ->
                val listener: IEventListener = ImageRenderListener(entriesToExtract, onExtract)
                val parser = PdfCanvasProcessor(listener)
                if (entriesToExtract.isNullOrEmpty()) {
                    for (i in 1..pdfDoc.numberOfPages) {
                        currentPageIndex.set(i)
                        parser.processPageContent(pdfDoc.getPage(i))
                        if (interrupt?.get() == true) return
                    }
                } else {
                    val targetEntries = entriesToExtract
                        .map {
                            val s = it.first.split(".")
                            if (s.size < 2) Triple(s[0].toInt(), 0, it.second)
                            Triple(s[0].toInt(), s[1].toInt(), it.second)
                        }
                        .filterNot { it.first < 0 || it.first > pdfDoc.numberOfPages }
                    targetEntries.forEach {
                        currentPageIndex.set(it.first)
                        // TODO selectively extract image X within selected page
                        parser.processPageContent(pdfDoc.getPage(it.first))
                        if (interrupt?.get() == true) return@forEach
                    }
                }
            }
        }
        onComplete.invoke()
    }

    fun convertPdfToImages(
        context: Context,
        pdfUri: String,
        pages: List<Int>?,
        onGetPagesCount: (Int) -> Unit,
        onProgressChange: (Int, Bitmap) -> Unit,
        onComplete: () -> Unit
    ) {
        context.contentResolver.openFileDescriptor(
            pdfUri.toUri(),
            "r"
        )?.use { fileDescriptor ->
            val pdfRenderer = PdfRenderer(fileDescriptor)

            onGetPagesCount(pages?.size ?: pdfRenderer.pageCount)

            for (pageIndex in 0 until pdfRenderer.pageCount) {
                if (pages == null || pages.contains(pageIndex)) {
                    val bitmap: Bitmap
                    pdfRenderer.openPage(pageIndex).use { page ->
                        bitmap =
                            Bitmap.createBitmap(
                                page.width,
                                page.height,
                                Bitmap.Config.ARGB_8888
                            )
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    }

                    val renderedBitmap = Bitmap.createBitmap(
                        bitmap.width,
                        bitmap.height,
                        Bitmap.Config.ARGB_8888
                    )
                    Canvas(renderedBitmap).apply {
                        drawColor(ContextCompat.getColor(context, R.color.white))
                        drawBitmap(bitmap, 0f, 0f, Paint().apply { isAntiAlias = true })
                    }

                    onProgressChange(pageIndex, renderedBitmap)
                }
            }
            onComplete()
            pdfRenderer.close()
        }
    }

    suspend fun getPdfPages(
        context: Context,
        uri: String
    ): List<Int> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openFileDescriptor(
                uri.toUri(),
                "r"
            )?.use { fileDescriptor ->
                List(PdfRenderer(fileDescriptor).pageCount) { it }
            }
        }.getOrNull() ?: emptyList()
    }

    /*

    private val pagesBuf = hashMapOf<String, List<Point>>()
    suspend fun getPdfPageSizes(
        context: Context,
        uri: String
    ): List<Point> = withContext(Dispatchers.IO) {
        if (!pagesBuf[uri].isNullOrEmpty()) {
            pagesBuf[uri]!!
        } else {
            runCatching {
                context.contentResolver.openFileDescriptor(
                    uri.toUri(),
                    "r"
                )?.use { fileDescriptor ->
                    val r = PdfRenderer(fileDescriptor)
                    List(r.pageCount) {
                        val page = r.openPage(it)
                        page?.run {
                            Point(width, height)
                        }.also {
                            page.close()
                        }
                    }.filterNotNull().also {
                        pagesBuf[uri] = it
                    }
                }
            }.getOrNull() ?: emptyList()
        }
    }

    private suspend fun calculateCombinedImageDimensions(
        context: Context,
        imageUris: List<String>,
        isHorizontal: Boolean,
    //        scaleSmallImagesToLarge: Boolean,
        imageSpacing: Int
    ): Point = withContext(Dispatchers.IO) {
        var w = 0
        var h = 0
        var maxHeight = 0
        var maxWidth = 0
        val drawables = imageUris.map { uri ->
            val dims = getImageDimensions(context, uri)
            maxWidth = max(maxWidth, dims.x)
            maxHeight = max(maxHeight, dims.y)
            return@map dims
        }

        drawables.forEachIndexed { index, dims ->
            val width = dims.x
            val height = dims.y

            val spacing = if (index != drawables.lastIndex) imageSpacing else 0

            /*
            if (scaleSmallImagesToLarge && image.shouldUpscale(
                    isHorizontal = isHorizontal,
                    size = Int(maxWidth, maxHeight)
                )
            ) {
                val targetHeight: Int
                val targetWidth: Int

                if (isHorizontal) {
                    targetHeight = maxHeight
                    targetWidth = (targetHeight * image.aspectRatio).toInt()
                } else {
                    targetWidth = maxWidth
                    targetHeight = (targetWidth / image.aspectRatio).toInt()
                }
                if (isHorizontal) {
                    w += (targetWidth + spacing).coerceAtLeast(1)
                } else {
                    h += (targetHeight + spacing).coerceAtLeast(1)
                }
            } else {
                if (isHorizontal) {
                    w += (width + spacing).coerceAtLeast(1)
                } else {
                    h += (height + spacing).coerceAtLeast(1)
                }
            }
             */
        }

        if (isHorizontal) {
            h = maxHeight
        } else {
            w = maxWidth
        }

        Point(w, h)
    }

     */

    private class PageRotationEventHandler : IEventHandler {
        private var rotation: PdfNumber = PORTRAIT

        fun setRotation(orientation: PdfNumber) {
            this.rotation = orientation
        }

        override fun handleEvent(currentEvent: Event) {
            val docEvent = currentEvent as PdfDocumentEvent
            docEvent.page.put(PdfName.Rotate, rotation)
        }
    }

    private class PageBackgroundEventHandler : IEventHandler {
        private var color = ColorConstants.WHITE

        fun setBackground(color: Color) {
            this.color = color
        }

        override fun handleEvent(currentEvent: Event) {
            val docEvent = currentEvent as PdfDocumentEvent
            val page = docEvent.page

            val canvas = PdfCanvas(page)
            val rect: Rectangle = page.pageSize
            canvas
                .saveState()
                .setFillColor(color)
                .rectangle(rect)
                .fillStroke()
                .restoreState()
        }
    }

    inner class ImageRenderListener(
        private val entriesToExtract: List<Pair<String, String>>?,
        private val onExtract: (String, ByteArray) -> Unit
    ) : IEventListener {
        private var page = -1
        private var indexInPage = 0
        override fun eventOccurred(data: IEventData, type: EventType?) {
            when (type) {
                EventType.RENDER_IMAGE -> try {
                    val renderInfo = data as ImageRenderInfo
                    val image = renderInfo.image ?: return
                    val curPage = currentPageIndex.get()
                    if (curPage != page) {
                        indexInPage = 0
                        page = curPage
                    }
                    val internalIdentifier = "$curPage.$indexInPage"
                    val identifier = entriesToExtract?.let { entry ->
                        entry.firstOrNull { it.first == internalIdentifier }?.second
                    } ?: internalIdentifier
                    onExtract.invoke(identifier, image.getImageBytes(false))
                } catch (e: com.itextpdf.io.exceptions.IOException) {
                    Timber.w(e)
                } catch (e: IOException) {
                    Timber.w(e)
                }

                else -> {}
            }
        }

        override fun getSupportedEvents(): Set<EventType>? {
            return null
        }
    }
}