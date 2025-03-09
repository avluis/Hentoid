package me.devsaki.hentoid.util.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
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
import me.devsaki.hentoid.core.THUMB_FILE_NAME
import me.devsaki.hentoid.enums.PictureEncoder
import me.devsaki.hentoid.util.copy
import me.devsaki.hentoid.util.file.ArchiveEntry
import me.devsaki.hentoid.util.file.DiskCache
import me.devsaki.hentoid.util.file.NameFilter
import me.devsaki.hentoid.util.file.findFile
import me.devsaki.hentoid.util.file.getExtension
import me.devsaki.hentoid.util.file.getExtensionFromMimeType
import me.devsaki.hentoid.util.file.getFileNameWithoutExtension
import me.devsaki.hentoid.util.file.getInputStream
import me.devsaki.hentoid.util.file.getOutputStream
import me.devsaki.hentoid.util.file.legacyFileFromUri
import me.devsaki.hentoid.util.file.saveBinary
import me.devsaki.hentoid.util.pause
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

private val pdfNamesFilter = NameFilter { getExtension(it).equals("pdf", true) }

/**
 * Build a [NameFilter] only accepting archive files supported by the app
 *
 * @return [NameFilter] only accepting archive files supported by the app
 */
fun getPdfNamesFilter(): NameFilter {
    return pdfNamesFilter
}

class PdfManager {

    private val extractedFiles = ArrayList<Uri>()
    private val entries = ArrayList<ArchiveEntry>()
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
                        // Convert to PNG or JPEG if not supported
                        val image = Image(
                            ImageDataFactory.create(
                                if (!ImageDataFactory.isSupportedType(data)) {
                                    var params = TransformParams(
                                        false, 0, 0, 0, 0, 0, 1, PictureEncoder.PNG,
                                        PictureEncoder.JPEG, PictureEncoder.PNG, 90
                                    )
                                    transform(data, params)
                                } else data
                            )
                        )

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

    private fun saveExtractedImage(
        id: String,
        data: ByteArray,
        fileCreator: (String) -> File,
        fileFinder: (String) -> Uri?,
        onExtracted: ((String, Uri) -> Unit)? = null
    ) {
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
        pdf: DocumentFile,
        entriesToExtract: List<Pair<String, String>>?,
        interrupt: AtomicBoolean?,
        onExtracted: ((String, Uri) -> Unit)?,
        onComplete: () -> Unit
    ) {
        val fileCreator: (String) -> File =
            { targetFileName -> File(DiskCache.createFile(targetFileName).path!!) }
        val fileFinder: (String) -> Uri? = { targetFileName -> DiskCache.getFile(targetFileName) }

        extractImages(
            context,
            pdf,
            entriesToExtract, interrupt,
            { id, data -> saveExtractedImage(id, data, fileCreator, fileFinder, onExtracted) },
            onComplete
        )
    }

    @Throws(IOException::class)
    fun extractImagesBlocking(
        context: Context,
        pdf: DocumentFile,
        targetFolder: File,  // We either extract on the app's persistent files folder or the app's cache folder - either way we have to deal without SAF :scream:
        entriesToExtract: List<Pair<String, String>>
    ): List<Uri> {
        extractedFiles.clear()
        val fileCreator: (String) -> File =
            { targetFileName -> File(targetFolder.absolutePath + File.separator + targetFileName) }
        val fileFinder: (String) -> Uri? =
            { targetFileName -> findFile(targetFolder, targetFileName)?.let { Uri.fromFile(it) } }

        extractImages(
            context,
            pdf,
            entriesToExtract, null,
            { id, data ->
                saveExtractedImage(id, data, fileCreator, fileFinder)
                { _, uri -> extractedFiles.add(uri) }
            }
        )
        // Block calling thread until all entries are processed
        val delay = 250
        var nbPauses = 0
        var lastSize = 0
        while (extractedFiles.size < entriesToExtract.size) {
            extractedFiles.apply {
                if (lastSize == size) {
                    // 3 seconds timeout when no progression
                    if (nbPauses++ > 3 * 1000 / delay) throw IOException("Extraction timed out")
                } else {
                    nbPauses = 0
                }
                lastSize = size
            }
            pause(delay)
        }
        return extractedFiles
    }

    private fun extractImages(
        context: Context,
        pdf: DocumentFile,
        entriesToExtract: List<Pair<String, String>>?,
        interrupt: AtomicBoolean?,
        onExtract: (String, ByteArray) -> Unit,
        onComplete: (() -> Unit)? = null
    ) {
        getInputStream(context, pdf).use { input ->
            PdfDocument(PdfReader(input)).use { pdfDoc ->
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
        onComplete?.invoke()
    }

    fun getPdfEntries(
        context: Context,
        doc: DocumentFile
    ): List<ArchiveEntry> {
        entries.clear()
        getInputStream(context, doc).use { input ->
            PdfDocument(PdfReader(input)).use { pdfDoc ->
                val listener: IEventListener = ImageRenderListener(null)
                { name, data -> entries.add(ArchiveEntry(name, data.size.toLong())) }
                val parser = PdfCanvasProcessor(listener)
                for (i in 1..pdfDoc.numberOfPages) {
                    currentPageIndex.set(i)
                    parser.processPageContent(pdfDoc.getPage(i))
                }
            }
        }
        return entries
    }

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
        override fun eventOccurred(evt: IEventData, type: EventType?) {
            when (type) {
                EventType.RENDER_IMAGE -> try {
                    val renderInfo = evt as ImageRenderInfo
                    val image = renderInfo.image ?: return
                    val curPage = currentPageIndex.get()
                    if (curPage != page) {
                        indexInPage = 0
                        page = curPage
                    }
                    val data = image.getImageBytes(false)
                    val ext = getExtensionFromMimeType(getMimeTypeFromPictureBinary(data))
                    val internalIdentifier = "$curPage.$indexInPage.$ext"
                    val identifier = entriesToExtract?.let { entry ->
                        entry.firstOrNull { it.first == internalIdentifier }?.second
                    } ?: internalIdentifier
                    onExtract.invoke(identifier, data)
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