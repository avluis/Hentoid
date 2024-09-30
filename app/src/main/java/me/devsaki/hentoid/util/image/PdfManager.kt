package me.devsaki.hentoid.util.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.pause
import java.io.OutputStream

// Inspired by https://github.com/T8RIN/ImageToolbox/blob/master/feature/pdf-tools/src/main/java/ru/tech/imageresizershrinker/feature/pdf_tools/data/AndroidPdfManager.kt
class PdfManager {
    fun convertImagesToPdf(
        context: Context,
        out: OutputStream,
        imageFiles: List<DocumentFile>,
        onProgressChange: ((Float) -> Unit)? = null
//        scaleSmallImagesToLarge: Boolean
    ) {
        val pdfDocument = PdfDocument()

        /*
        val dims = calculateCombinedImageDimensions(
            context,
            imageUris = imageUris,
//            scaleSmallImagesToLarge = scaleSmallImagesToLarge,
            isHorizontal = false,
            imageSpacing = 0
        )

        // Upscale/downscale code was there

         */

        // TODO load one after the other

        try {
            imageFiles
                .asSequence()
                .mapNotNull { loadBitmap(context, it) }
                .forEachIndexed { index, bmp ->
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        bmp.width,
                        bmp.height,
                        index
                    ).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawBitmap(
                        bmp,
                        0f, 0f,
                        Paint().apply {
                            isAntiAlias = true
                        }
                    )
                    pdfDocument.finishPage(page)
                    pause(50)
                    pdfDocument.writeTo(out)
                    onProgressChange?.invoke(index * 1f / imageFiles.size)
                }
        } finally {
            pdfDocument.writeTo(out)
            pdfDocument.close()
        }
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
}