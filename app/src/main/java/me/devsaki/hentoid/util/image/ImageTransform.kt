package me.devsaki.hentoid.util.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.net.Uri
import android.os.Build
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.awxkee.jxlcoder.JxlCoder
import com.awxkee.jxlcoder.JxlCompressionOption
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.PictureEncoder
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.util.file.copyFile
import me.devsaki.hentoid.util.file.createFile
import me.devsaki.hentoid.util.file.fileSizeFromUri
import me.devsaki.hentoid.util.file.getDocumentFromTreeUri
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.getInputStream
import me.devsaki.hentoid.util.file.getMimeTypeFromFileName
import me.devsaki.hentoid.util.file.saveBinary
import me.devsaki.hentoid.util.formatIntAsStr
import me.devsaki.hentoid.util.getScreenDimensionsPx
import me.devsaki.hentoid.util.network.UriParts
import timber.log.Timber
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@JsonClass(generateAdapter = true)
data class TransformParams(
    val resizeEnabled: Boolean,
    val resizeMethod: Int,
    val resize1Ratio: Int,
    val resize2Height: Int,
    val resize2Width: Int,
    val resize3Ratio: Int,
    val resize5Pages: Int,
    val transcodeMethod: Int,
    val transcoderAll: PictureEncoder,
    val transcoderLossy: PictureEncoder,
    val transcoderLossless: PictureEncoder,
    val transcodeQuality: Int,
    @Transient var forceManhwa: Boolean = false
)

private const val MAX_WEBP_DIMENSION = 16383 // As per WEBP specifications
private const val MANHWA_MIN_HEIGHT = 4 // Multiple of screen height
private const val MANHWA_MAX_HEIGHT = 10 // Multiple of screen height

// % of average width to seperate outliers from the rest
private const val OUTLIER_WIDTH_THRESHOLD = 0.1

val screenWidth = getScreenDimensionsPx(HentoidApp.getInstance()).x
val screenHeight = getScreenDimensionsPx(HentoidApp.getInstance()).y

val minPicHeight = screenHeight * MANHWA_MIN_HEIGHT
val maxPicHeight = screenHeight * MANHWA_MAX_HEIGHT


internal data class ManhwaProcessingItem(
    val img: ImageFile,
    val doc: DocumentFile,
    val dims: Point,
    val toConsumeOffset: Int,
    val toConsumeHeight: Int
)


/**
 * Transform the given raw picture data using the given params
 */
fun transform(
    rawData: ByteArray,
    params: TransformParams,
    allowBogusAiRescale: Boolean = false
): ByteArray {
    if (isImageAnimated(rawData)) return rawData

    val options = BitmapFactory.Options()
    options.inJustDecodeBounds = true
    BitmapFactory.decodeByteArray(rawData, 0, rawData.size, options)
    val dims = Point(options.outWidth, options.outHeight)
    val bitmapOut: Bitmap = if (params.resizeEnabled) {
        when (params.resizeMethod) {
            0 -> resizeScreenRatio(rawData, dims, params.resize1Ratio / 100f)
            1 -> resizeDims(
                rawData, dims, params.resize2Height, params.resize2Width, params.forceManhwa
            )

            2 -> resizePlainRatio(rawData, dims, params.resize3Ratio / 100f)
            3 -> { // AI rescale; handled at Worker level
                val scale = if (allowBogusAiRescale) 2f else 1f
                resizePlainRatio(rawData, dims, scale, allowBogusAiRescale)
            }

            // 4 : Manhwa split/merge; handled at Worker level and requires multiple images

            else -> resizePlainRatio(rawData, dims, 1f)
        }
    } else {
        BitmapFactory.decodeByteArray(rawData, 0, rawData.size)
    }

    val isLossless = isImageLossless(rawData)
    val targetDims = Point(bitmapOut.width, bitmapOut.height)
    try {
        val encoder = determineEncoder(isLossless, targetDims, params)
        val noResize = (targetDims.x == dims.x && targetDims.y == dims.y)
        return if (noResize && PictureEncoder.JXL_LOSSLESS == encoder
            && getMimeTypeFromPictureBinary(rawData) == MIME_IMAGE_JPEG
        ) {
            transcodeJpegToLosslessJxl(rawData)
        } else {
            transcodeTo(
                bitmapOut,
                determineEncoder(isLossless, targetDims, params),
                params.transcodeQuality
            )
        }
    } finally {
        bitmapOut.recycle()
    }
}

private fun resizeScreenRatio(source: ByteArray, dims: Point, ratio: Float): Bitmap {
    val targetWidth = screenWidth * ratio
    val targetHeight = screenHeight * ratio
    val widthRatio = targetWidth / dims.x
    val heightRatio = targetHeight / dims.y
    val targetRatio = if (widthRatio > 1 && heightRatio > 1) max(widthRatio, heightRatio)
    else min(widthRatio, heightRatio)
    return resizePlainRatio(source, dims, targetRatio)
}

private fun resizeDims(
    source: ByteArray, dims: Point, maxHeight: Int, maxWidth: Int, forceManhwa: Boolean
): Bitmap {
    val isManhwa = forceManhwa || (dims.y * 1.0 / dims.x > 3)
    val ratio = if (isManhwa) {
        if (dims.x > maxWidth) maxWidth * 1f / dims.x else 1f
    } else {
        // Only take height into account (portrait)
        if (dims.y > maxHeight) maxHeight * 1f / dims.y else 1f
    }
    return resizePlainRatio(source, dims, ratio)
}

private fun resizePlainRatio(
    source: ByteArray,
    dims: Point,
    ratio: Float,
    allowUpscale: Boolean = false
): Bitmap {
    val sourceBmp = BitmapFactory.decodeByteArray(source, 0, source.size)
    return if (ratio > 0.99 && ratio < 1.01) sourceBmp // Don't do anything
    else if (ratio > 1.01 && !allowUpscale) sourceBmp // Prevent upscaling
    else {
        val rescaled = sharpRescale(sourceBmp, ratio)
        if (rescaled != sourceBmp) sourceBmp.recycle()
        try {
            rescaled.scale((dims.x * ratio).roundToInt(), (dims.y * ratio).roundToInt())
        } finally {
            rescaled.recycle()
        }
    }
}

fun determineEncoder(
    isLossless: Boolean,
    dims: Point,
    params: TransformParams
): PictureEncoder {
    // AI rescale always produces PNGs
    if (3 == params.resizeMethod) return PictureEncoder.PNG

    // Other cases
    val result = when (params.transcodeMethod) {
        0 -> params.transcoderAll
        else -> if (isLossless) params.transcoderLossless else params.transcoderLossy
    }
    return if (PictureEncoder.WEBP_LOSSY == result && max(dims.x, dims.y) > MAX_WEBP_DIMENSION)
        PictureEncoder.JPEG
    else if (PictureEncoder.WEBP_LOSSLESS == result && max(dims.x, dims.y) > MAX_WEBP_DIMENSION)
        PictureEncoder.PNG
    else result
}

@Suppress("DEPRECATION")
fun transcodeTo(bitmap: Bitmap, encoder: PictureEncoder, quality: Int): ByteArray {
    val output = ByteArrayOutputStream()
    when (encoder) {
        PictureEncoder.WEBP_LOSSY -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, output)
        else bitmap.compress(Bitmap.CompressFormat.WEBP, quality, output)

        PictureEncoder.WEBP_LOSSLESS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, output)
        else bitmap.compress(Bitmap.CompressFormat.WEBP, 100, output)

        PictureEncoder.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)

        PictureEncoder.JPEG -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)

        PictureEncoder.JXL_LOSSY -> return JxlCoder.encode(bitmap, quality = quality)

        PictureEncoder.JXL_LOSSLESS -> return JxlCoder.encode(
            bitmap,
            compressionOption = JxlCompressionOption.LOSSLESS
        )
    }
    return output.toByteArray()
}

fun transcodeJpegToLosslessJxl(jpegData: ByteArray): ByteArray {
    return JxlCoder.Convenience.construct(jpegData)
}

suspend fun transformManhwaChapter(
    context: Context,
    sourceImgs: List<ImageFile>,
    firstIndex: Int,
    targetFolder: Uri,
    params: TransformParams,
    isPreview: Boolean = false,
    interrupt: (() -> Boolean)? = null,
    onProgress: ((Pair<Long, Boolean>) -> Unit)? = null
): List<ImageFile> {
    val result = ArrayList<Pair<Uri, ImageFile>>()
    Timber.d("transformManhwaChapter (${sourceImgs.size} items starting with $firstIndex)")

    // Compute the height of all images together + largest common width excluding outliers
    val allDims = ArrayList<Point>()
    val metadataOpts = BitmapFactory.Options()
    val imgDocuments = ArrayList<DocumentFile>()
    metadataOpts.inJustDecodeBounds = true
    sourceImgs.forEachIndexed { idx, img ->
        if (true == interrupt?.invoke() || (isPreview && idx > 10)) return@forEachIndexed
        val sourceFile = withContext(Dispatchers.IO) {
            getDocumentFromTreeUriString(context, img.fileUri)
        } ?: run {
            Timber.w("Can't open source file ${img.fileUri}")
            return emptyList()
        }
        Timber.v("Reading source file ${img.fileUri}")
        imgDocuments.add(sourceFile)
        withContext(Dispatchers.IO) {
            getInputStream(context, sourceFile).use {
                val rawData = it.readBytes()
                BitmapFactory.decodeByteArray(rawData, 0, rawData.size, metadataOpts)
            }
        }
        allDims.add(Point(metadataOpts.outWidth, metadataOpts.outHeight))
    }
    if (true == interrupt?.invoke()) return emptyList()

    // Compute target dims
    val totalHeight = allDims.sumOf { it.y }

    var bitmapBuffer = createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    var pixelBuffer = IntArray(PIXEL_BUFFER_HEIGHT)
    var targetDims = Point(1, 1)

    // Create target images one by one
    var consumedHeight = 0
    val processingQueue = ArrayList<ManhwaProcessingItem>()
    val processedIds = HashSet<Long>()
    var currentImgIdx = firstIndex
    var isKO = false
    try {
        sourceImgs.forEachIndexed { idx, img ->
            if (true == interrupt?.invoke()) {
                isKO = true
                return@forEachIndexed
            }
            if (isPreview && currentImgIdx > firstIndex) return@forEachIndexed

            val isLast = idx == sourceImgs.size - 1
            val dims = allDims[idx]
            val isSingleOutlier = isSingleOutlier(allDims, idx)
            Timber.v("Processing source file ${img.fileUri} [$dims]")

            // Image width deviates from current bitmap buffer dims
            if (!isSingleOutlier &&
                (dims.x > bitmapBuffer.width || abs(dims.x.toFloat() - bitmapBuffer.width.toFloat()) / bitmapBuffer.width.toFloat() > OUTLIER_WIDTH_THRESHOLD)
            ) {
                // New dims for a batch of at least 2 images
                Timber.d("New width detected! ${dims.x}")
                if (!processingQueue.isEmpty()) {
                    Timber.d("Clearing queue")
                    // => Complete what's being built
                    val toProcess = processingQueue.map { it.img.id }
                    val newImgs = processManhwaImageQueue(
                        context,
                        processingQueue,
                        bitmapBuffer,
                        pixelBuffer,
                        currentImgIdx,
                        true,
                        targetDims,
                        targetFolder,
                        params
                    )
                    // Report results in the notification
                    toProcess.forEach {
                        if (!processedIds.contains(it)) {
                            onProgress?.invoke(Pair(it, !newImgs.isEmpty()))
                            processedIds.add(it)
                        }
                    }

                    currentImgIdx += newImgs.size
                    result.addAll(newImgs)
                    consumedHeight = 0
                } // Clear queue

                // => Redim buffers
                targetDims = Point(
                    dims.x,
                    ceil(totalHeight * 1.0 / params.resize5Pages).roundToInt()
                )
                clampDims(targetDims)
                Timber.d("Redimensioning buffers to $targetDims")

                bitmapBuffer.recycle()
                bitmapBuffer = createBitmap(
                    dims.x,
                    targetDims.y,
                    Bitmap.Config.ARGB_8888
                )
                pixelBuffer = IntArray(dims.x * PIXEL_BUFFER_HEIGHT)
            }

            if (isSingleOutlier) {
                // Reuse outlier file into new image without any processing
                Timber.d("File is a single outlier $dims")
                if (!processingQueue.isEmpty()) {
                    Timber.d("Clearing queue")
                    // => complete what's being built
                    val toProcess = processingQueue.map { it.img.id }
                    val newImgs = processManhwaImageQueue(
                        context,
                        processingQueue,
                        bitmapBuffer,
                        pixelBuffer,
                        currentImgIdx,
                        true,
                        targetDims,
                        targetFolder,
                        params
                    )
                    // Report results in the notification
                    toProcess.forEach {
                        if (!processedIds.contains(it)) {
                            onProgress?.invoke(Pair(it, !newImgs.isEmpty()))
                            processedIds.add(it)
                        }
                    }

                    currentImgIdx += newImgs.size
                    result.addAll(newImgs)
                    consumedHeight = 0
                } // Clear queue

                val newImg = ImageFile()
                val uriParts = UriParts(img.fileUri)
                getDocumentFromTreeUri(context, targetFolder)?.let { targetFolderDoc ->
                    copyFile(
                        context,
                        img.fileUri.toUri(),
                        targetFolderDoc,
                        uriParts.fileNameFull,
                        getMimeTypeFromFileName(uriParts.fileNameFull)
                    )?.let { newUri ->
                        newImg.fileUri = newUri.toString()
                        newImg.name =
                            formatIntAsStr(currentImgIdx, 4) + "." + uriParts.extension
                        newImg.order = currentImgIdx
                        newImg.fileUri = newUri.toString()
                        newImg.size = fileSizeFromUri(context, newUri)
                        newImg.status = StatusContent.DOWNLOADED
                        newImg.isTransformed = true
                        newImg.isCover = img.isCover
                        result.add(Pair(newUri, newImg))
                    }
                }
                currentImgIdx++
                onProgress?.invoke(Pair(img.id, true))
                return@forEachIndexed
            }

            var leftToConsume = targetDims.y - consumedHeight

            // Compute consumption for current image
            val toConsume = min(dims.y, leftToConsume)
            consumedHeight += toConsume
            leftToConsume = targetDims.y - consumedHeight

            processingQueue.add(
                ManhwaProcessingItem(img, imgDocuments[idx], dims, 0, toConsume)
            )

            // Create target image
            if (leftToConsume <= 0 || isLast) {
                if (leftToConsume < 0) Timber.w("!!! LEFTTOCONSUME IS NEGATIVE $leftToConsume")
                val toProcess = processingQueue.map { it.img.id }
                val newImgs = processManhwaImageQueue(
                    context,
                    processingQueue,
                    bitmapBuffer,
                    pixelBuffer,
                    currentImgIdx,
                    isLast,
                    targetDims,
                    targetFolder,
                    params
                )
                // Report results in the notification
                toProcess.forEach {
                    if (!processedIds.contains(it)) {
                        onProgress?.invoke(Pair(it, !newImgs.isEmpty()))
                        processedIds.add(it)
                    }
                }

                currentImgIdx += newImgs.size
                result.addAll(newImgs)
                consumedHeight = processingQueue.sumOf { it.toConsumeHeight }
            }
        } // source images loop
    } catch (e: Exception) {
        Timber.w(e)
        isKO = true
    } finally {
        bitmapBuffer.recycle()
    }

    return if (!isKO || isPreview) {
        result.map { it.second }
    } else {
        emptyList()
    }
}

private suspend fun processManhwaImageQueue(
    context: Context,
    queue: MutableList<ManhwaProcessingItem>,
    bitmapBuffer: Bitmap,
    pixelBuffer: IntArray,
    startIndex: Int,
    isLast: Boolean,
    targetDims: Point,
    targetFolder: Uri,
    params: TransformParams
): List<Pair<Uri, ImageFile>> {
    if (queue.isEmpty()) return emptyList()

    Timber.d("process queue (${queue.size} items)")
    val result = ArrayList<Pair<Uri, ImageFile>>()
    var yOffset = 0
    var previousWidth = Int.MAX_VALUE
    var containsLossless = false

    // Queue contents are shorter than the bitmap buffer => use an ad-hoc bitmap buffer
    val totalHeight = queue.sumOf { it.toConsumeHeight }
    val bmpBuffer = if (totalHeight < bitmapBuffer.height) {
        createBitmap(
            bitmapBuffer.width,
            totalHeight,
            Bitmap.Config.ARGB_8888
        )
    } else bitmapBuffer

    try {
        queue.forEach { img ->
            withContext(Dispatchers.IO) {
                // Build raw bitmap
                getInputStream(context, img.doc).use {
                    val rawData = it.readBytes()
                    if (!containsLossless && isImageLossless(rawData)) containsLossless = true
                    val bmp = BitmapFactory.decodeByteArray(rawData, 0, rawData.size)

                    // Clear pixelbuffer to avoid seeing ghosts of larger images
                    // behind thinner images that may be processed later
                    if (bmp.width < previousWidth) {
                        pixelBuffer.fill(0)
                        previousWidth = bmp.width
                    }

                    var linesToBuffer = img.toConsumeHeight
                    Timber.v("copy ${img.doc.name ?: ""} from ${img.toConsumeOffset} to ${img.toConsumeOffset + img.toConsumeHeight} (dims ${img.dims} ${bmp.width}x${bmp.height})")
                    while (linesToBuffer > 0) {
                        val bufTaken = min(linesToBuffer, PIXEL_BUFFER_HEIGHT)
                        val bufOffset = img.toConsumeHeight - linesToBuffer
                        val targetX = bmpBuffer.width
                        val xOffset = (targetX - img.dims.x) / 2 // Center pic
                        // Copy source pic to buffer (centered)
                        bmp.getPixels(
                            pixelBuffer,
                            xOffset,
                            targetX,
                            0,
                            img.toConsumeOffset + bufOffset,
                            img.dims.x,
                            bufTaken
                        )
                        // Copy buffer to target pic (whole width)
                        bmpBuffer.setPixels(
                            pixelBuffer,
                            0,
                            targetX,
                            0,
                            yOffset + bufOffset,
                            targetX,
                            bufTaken
                        )
                        linesToBuffer -= bufTaken
                    }
                    yOffset += img.toConsumeHeight
                    bmp.recycle()
                }
            }
        } // queue loop

        val encoder =
            determineEncoder(
                containsLossless,
                Point(bmpBuffer.width, bmpBuffer.height),
                params
            )
        val targetName = formatIntAsStr(startIndex, 4)
        Timber.d("create image $targetName (${encoder.mimeType})")

        createFile(
            context,
            targetFolder,
            targetName,
            encoder.mimeType,
            false
        ).let { targetUri ->
            Timber.d("Compressing...")
            val targetData = transcodeTo(bmpBuffer, encoder, params.transcodeQuality)
            Timber.d("Saving...")
            saveBinary(context, targetUri, targetData)
            // Update image properties
            val newImg = ImageFile()
            newImg.name = targetName
            newImg.order = startIndex
            newImg.fileUri = targetUri.toString()
            newImg.size = targetData.size.toLong()
            newImg.status = StatusContent.DOWNLOADED
            newImg.isTransformed = true
            result.add(Pair(targetUri, newImg))
        }
    } finally {
        // Recycle local bitmap buffer if any (all has been consumed)
        if (bmpBuffer != bitmapBuffer) bmpBuffer.recycle()
    }

    val last = queue.last()
    queue.clear()

    // Is the last image of the queue completely consumed?
    if (last.toConsumeHeight != last.dims.y) {
        val remainingHeight = last.dims.y - last.toConsumeOffset - last.toConsumeHeight
        queue.add(
            ManhwaProcessingItem(
                last.img,
                last.doc,
                last.dims,
                last.toConsumeOffset + last.toConsumeHeight,
                min(remainingHeight, targetDims.y)
            )
        )
        // Reprocess the queue right now if there's a remanining image with enough height
        if (remainingHeight > 0 && (remainingHeight >= targetDims.y || isLast)) {
            Timber.d("Reusing last queued image (remaining $remainingHeight)")
            result.addAll(
                processManhwaImageQueue(
                    context,
                    queue,
                    bitmapBuffer,
                    pixelBuffer,
                    startIndex + 1,
                    isLast,
                    targetDims,
                    targetFolder,
                    params
                )
            )
        }
    }

    return result
}

/**
 * Clamp diven dims' height to hardcoded min and max dims
 */
fun clampDims(dims: Point) {
    val targetHeightInScreenDims =
        dims.y.toFloat() * screenWidth.toFloat() / dims.x.toFloat()
    if (targetHeightInScreenDims > maxPicHeight)
        dims.y = (maxPicHeight.toFloat() * dims.x.toFloat() / screenWidth.toFloat()).roundToInt()
    else if (targetHeightInScreenDims < minPicHeight)
        dims.y = (minPicHeight.toFloat() * dims.x.toFloat() / screenWidth.toFloat()).roundToInt()
}

fun isSingleOutlier(dims: List<Point>, idx: Int): Boolean {
    val currentX = dims[idx].x.toFloat()
    val isFirst = 0 == idx
    val isLast = dims.size - 1 == idx

    val previousX = if (!isFirst) dims[idx - 1].x.toFloat() else currentX
    val nextX = if (!isLast) dims[idx + 1].x.toFloat() else currentX

    val previousKO =
        currentX > previousX || abs(currentX - previousX) / previousX > OUTLIER_WIDTH_THRESHOLD
    val nextKO = currentX > nextX || abs(currentX - nextX) / currentX > OUTLIER_WIDTH_THRESHOLD

    return if (isFirst) nextKO else if (isLast) previousKO else nextKO && previousKO
}