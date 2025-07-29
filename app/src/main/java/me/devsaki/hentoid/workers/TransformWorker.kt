package me.devsaki.hentoid.workers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.notification.transform.TransformCompleteNotification
import me.devsaki.hentoid.notification.transform.TransformProgressNotification
import me.devsaki.hentoid.util.AchievementsManager
import me.devsaki.hentoid.util.ProgressManager
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.file.findOrCreateDocumentFile
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.getExtensionFromMimeType
import me.devsaki.hentoid.util.file.getInputStream
import me.devsaki.hentoid.util.file.getOrCreateCacheFolder
import me.devsaki.hentoid.util.file.saveBinary
import me.devsaki.hentoid.util.formatIntAsStr
import me.devsaki.hentoid.util.image.PIXEL_BUFFER_HEIGHT
import me.devsaki.hentoid.util.image.TransformParams
import me.devsaki.hentoid.util.image.clearCoilCache
import me.devsaki.hentoid.util.image.determineEncoder
import me.devsaki.hentoid.util.image.isImageLossless
import me.devsaki.hentoid.util.image.transcodeTo
import me.devsaki.hentoid.util.image.transform
import me.devsaki.hentoid.util.network.UriParts
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.util.pause
import me.devsaki.hentoid.workers.data.DeleteData
import me.robb.ai_upscale.AiUpscaler
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt


class TransformWorker(context: Context, parameters: WorkerParameters) :
    BaseWorker(context, parameters, R.id.transform_service, null) {

    private val dao: CollectionDAO = ObjectBoxDAO()
    private var upscaler: AiUpscaler? = null

    private var totalItems = 0
    private var nbOK = 0
    private var nbKO = 0
    private lateinit var globalProgress: ProgressManager


    private data class ManhwaProcessingItem(
        val img: ImageFile,
        val doc: DocumentFile,
        val dims: Point,
        val toConsumeOffset: Int,
        val toConsumeHeight: Int
    )


    override fun getStartNotification(): BaseNotification {
        return TransformProgressNotification(0, 0, 0f)
    }

    override fun onInterrupt() {
        // Nothing
    }

    override suspend fun onClear(logFile: DocumentFile?) {
        dao.cleanup()
        upscaler?.cleanup()

        // Reset Coil cache as it gets confused by the resizing
        clearCoilCache(applicationContext)
    }

    override suspend fun getToWork(input: Data) {
        val contentIds = inputData.getLongArray("IDS")
        val paramsStr = inputData.getString("PARAMS")
        require(contentIds != null)
        require(paramsStr != null)
        require(paramsStr.isNotEmpty())

        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

        val params = moshi.adapter(TransformParams::class.java).fromJson(paramsStr)
        require(params != null)

        if (params.resizeEnabled && 3 == params.resizeMethod) { // AI upscale
            AiUpscaler().let {
                upscaler = it
                it.init(
                    applicationContext.resources.assets,
                    "realsr/models-nose/up2x-no-denoise.param",
                    "realsr/models-nose/up2x-no-denoise.bin"
                )
            }
        }

        transform(contentIds, params)
    }

    private suspend fun transform(contentIds: LongArray, params: TransformParams) {
        // Flag contents as "being deleted" (triggers blink animation; lock operations)
        // +count the total number of images to convert
        dao.updateContentsProcessedFlagById(contentIds.filter { it > 0 }, true)

        contentIds.forEach {
            totalItems += dao.selectDownloadedImagesFromContent(it).count { i -> i.isReadable }
            if (isStopped) return
        }

        globalProgress = ProgressManager(totalItems)
        launchProgressNotification()

        // Process images
        contentIds.forEach {
            val content = dao.selectContent(it)
            if (content != null) transformContent(content, params)
            if (isStopped) return
        }
        notifyProcessEnd()
    }

    private suspend fun transformContent(content: Content, params: TransformParams) {
        val contentFolder = withContext(Dispatchers.IO) {
            getDocumentFromTreeUriString(applicationContext, content.storageUri)
        }
        val sourceImages = content.imageList
        val transformedImages = ArrayList<ImageFile>()
        var isKO = false
        if (contentFolder != null) {
            val imagesWithoutChapters =
                sourceImages.filter { null == it.linkedChapter }.filter { it.isReadable }
            if (imagesWithoutChapters.isNotEmpty()) {
                val newImgs = transformChapter(
                    imagesWithoutChapters,
                    1,
                    contentFolder,
                    params
                )
                if (newImgs.isEmpty()) isKO = true
                else transformedImages.addAll(newImgs)
            }

            val chapteredImgs =
                sourceImages.filterNot { null == it.linkedChapter }.filter { it.isReadable }
                    .groupBy { it.linkedChapter!!.id }

            chapteredImgs.forEach {
                if (it.value.isNotEmpty()) {
                    val newImgs = transformChapter(
                        it.value,
                        transformedImages.size + 1,
                        contentFolder,
                        params
                    )
                    if (newImgs.isEmpty()) isKO = true
                    else transformedImages.addAll(newImgs)
                }
            }

            if (!isKO) {
                transformedImages.forEach { it.content.targetId = content.id }
                content.setImageFiles(transformedImages)
                dao.insertImageFiles(transformedImages)
                content.qtyPages = transformedImages.count { it.isReadable }
                content.computeSize()
                content.lastEditDate = Instant.now().toEpochMilli()
                content.isBeingProcessed = false
                dao.insertContentCore(content)
                // Remove old unused images if any
                val originalUris = sourceImages.map { it.fileUri }.toMutableSet()
                val newUris = transformedImages.map { it.fileUri }.toSet()
                originalUris.removeAll(newUris)
                if (originalUris.isNotEmpty())
                    removeDocs(
                        contentFolder.uri,
                        originalUris.map {
                            val parts = UriParts(it)
                            return@map parts.fileNameFull
                        }
                    )
            } else {
                nbKO += sourceImages.size
            }

            // Achievements
            if (!isStopped && !isKO) {
                if (upscaler != null) { // AI upscale
                    Settings.nbAIRescale += 1
                    if (Settings.nbAIRescale >= 2) AchievementsManager.trigger(20)
                }
                val pagesTotal = sourceImages.count { it.isReadable }
                if (pagesTotal >= 50) AchievementsManager.trigger(27)
                if (pagesTotal >= 100) AchievementsManager.trigger(28)
            }
        } else {
            nbKO += sourceImages.size
        }
    }

    private suspend fun transformChapter(
        imgs: List<ImageFile>,
        firstIndex: Int,
        contentFolder: DocumentFile,
        params: TransformParams
    ): List<ImageFile> {
        val nbManhwa = AtomicInteger(0)
        params.forceManhwa = false

        if (4 == params.resizeMethod) {
            // Split / merge manhwa
            return transformManhwaChapter(imgs, firstIndex, contentFolder, params)
        } else {
            val result = ArrayList<ImageFile>()
            // Per image individual transform
            imgs.forEach {
                result.add(transformImage(it, contentFolder, params, nbManhwa, imgs.size))
                if (isStopped) return@forEach
            }
            return result
        }
    }

    @Suppress("ReplaceArrayEqualityOpWithArraysEquals")
    private suspend fun transformImage(
        img: ImageFile,
        contentFolder: DocumentFile,
        params: TransformParams,
        nbManhwa: AtomicInteger,
        nbPages: Int
    ): ImageFile {
        val sourceFile = withContext(Dispatchers.IO) {
            getDocumentFromTreeUriString(applicationContext, img.fileUri)
        } ?: run {
            nextKO()
            return img
        }
        val rawData = withContext(Dispatchers.IO) {
            getInputStream(applicationContext, sourceFile).use {
                return@use it.readBytes()
            }
        }
        val imageId = img.fileUri
        val metadataOpts = BitmapFactory.Options()
        metadataOpts.inJustDecodeBounds = true

        val targetData: ByteArray
        if (upscaler != null) { // AI upscale
            targetData = upscale(imageId, rawData)
        } else { // regular resize
            BitmapFactory.decodeByteArray(rawData, 0, rawData.size, metadataOpts)
            val isManhwa = metadataOpts.outHeight * 1.0 / metadataOpts.outWidth > 3

            if (isManhwa) nbManhwa.incrementAndGet()
            params.forceManhwa = nbManhwa.get() * 1.0 / nbPages > 0.9

            targetData = transform(rawData, params)
        }
        if (isStopped) return img
        if (targetData == rawData) return img // Unchanged picture

        // Save transformed image data back to original image file
        val isLossless = isImageLossless(rawData)
        val sourceName = sourceFile.name ?: ""

        BitmapFactory.decodeByteArray(targetData, 0, targetData.size, metadataOpts)
        val targetDims = Point(metadataOpts.outWidth, metadataOpts.outHeight)
        val targetMime = determineEncoder(isLossless, targetDims, params).mimeType
        val targetName = img.name + "." + getExtensionFromMimeType(targetMime)
        val newFile = sourceName != targetName

        val targetUri = if (!newFile) sourceFile.uri
        else {
            val targetFile = contentFolder.createFile(targetMime, targetName)
            if (targetFile != null) sourceFile.delete()
            targetFile?.uri
        }
        if (targetUri != null) {
            saveBinary(applicationContext, targetUri, targetData)
            // Update image properties
            img.fileUri = targetUri.toString()
            img.size = targetData.size.toLong()
            img.isTransformed = true

            nextOK()
            globalProgress.setProgress(imageId, 1f)
            launchProgressNotification()
        } else {
            nextKO()
            launchProgressNotification()
        }
        return img
    }

    private fun upscale(imgId: String, rawData: ByteArray): ByteArray {
        val cacheDir =
            getOrCreateCacheFolder(applicationContext, "upscale") ?: return rawData
        val outputFile = File(cacheDir, "upscale.png")
        val progress = ByteBuffer.allocateDirect(1)
        val killSwitch = ByteBuffer.allocateDirect(1)
        val dataIn = ByteBuffer.allocateDirect(rawData.size)
        dataIn.put(rawData)

        upscaler?.let {
            try {
                killSwitch.put(0, 0)
                val res = it.upscale(
                    dataIn, outputFile.absolutePath, progress, killSwitch
                )
                // Fail => exit immediately
                if (res != 0) progress.put(0, 100)

                // Poll while processing
                val intervalSeconds = 3
                var iterations = 0
                while (iterations < 180 / intervalSeconds) { // max 3 minutes
                    pause(intervalSeconds * 1000)

                    if (isStopped) {
                        Timber.d("Kill order sent")
                        killSwitch.put(0, 1)
                        return rawData
                    }

                    val p = progress.get(0)
                    globalProgress.setProgress(imgId, p / 100f)
                    launchProgressNotification()

                    iterations++
                    if (p >= 100) break
                }
            } finally {
                // can't recycle ByteBuffer dataIn
            }
        }

        getInputStream(applicationContext, outputFile.toUri()).use { input ->
            return input.readBytes()
        }
    }

    private suspend fun transformManhwaChapter(
        sourceImgs: List<ImageFile>,
        firstIndex: Int,
        contentFolder: DocumentFile,
        params: TransformParams
    ): List<ImageFile> {
        val result = ArrayList<Pair<DocumentFile, ImageFile>>()
        Timber.d("transformManhwaChapter (${sourceImgs.size} items starting with $firstIndex)")

        // Compute the height of all images together + largest common width
        val allDims = ArrayList<Point>()
        val metadataOpts = BitmapFactory.Options()
        val imgDocuments = ArrayList<DocumentFile>()
        metadataOpts.inJustDecodeBounds = true
        sourceImgs.forEach { img ->
            val sourceFile = withContext(Dispatchers.IO) {
                getDocumentFromTreeUriString(applicationContext, img.fileUri)
            } ?: run {
                Timber.w("Can't open source file ${img.fileUri}")
                nextKO()
                return emptyList()
            }
            Timber.d("Reading source file ${img.fileUri}")
            imgDocuments.add(sourceFile)
            withContext(Dispatchers.IO) {
                getInputStream(applicationContext, sourceFile).use {
                    val rawData = it.readBytes()
                    BitmapFactory.decodeByteArray(rawData, 0, rawData.size, metadataOpts)
                }
            }
            allDims.add(Point(metadataOpts.outWidth, metadataOpts.outHeight))
            if (isStopped) return@forEach
        }
        if (isStopped) return emptyList()
        val totalHeight = allDims.sumOf { it.y }
        val targetDims = Point(
            allDims.maxOf { it.x },
            ceil(totalHeight * 1.0 / params.resize5Pages).roundToInt()
        )
        Timber.d("targetDims $targetDims")

        val targetImg = createBitmap(
            targetDims.x,
            targetDims.y,
            Bitmap.Config.ARGB_8888
        )
        val pixelBuffer = IntArray(targetDims.x * PIXEL_BUFFER_HEIGHT)

        // Create target images one by one
        var consumedHeight = 0
        val processingQueue = ArrayList<ManhwaProcessingItem>()
        var currentIdx = firstIndex
        var isKO = false
        try {
            sourceImgs.forEachIndexed { idx, img ->
                Timber.d("Processing source file ${img.fileUri}")
                val dims = allDims[idx]
                var leftToConsume = targetDims.y - consumedHeight

                // Compute consumption for current image
                val toConsume = min(dims.y, leftToConsume)
                consumedHeight += toConsume
                leftToConsume = targetDims.y - consumedHeight

                processingQueue.add(
                    ManhwaProcessingItem(img, imgDocuments[idx], dims, 0, toConsume)
                )

                // Create target image
                if (leftToConsume <= 0) {
                    if (leftToConsume < 0) Timber.w("!!! LEFTTOCONSUME IS NEGATIVE $leftToConsume")
                    val newImgs = processManhwaImageQueue(
                        processingQueue,
                        targetImg,
                        pixelBuffer,
                        currentIdx,
                        targetDims,
                        contentFolder,
                        params
                    )
                    currentIdx += newImgs.size
                    result.addAll(newImgs)
                    consumedHeight = processingQueue.sumOf { it.toConsumeHeight }
                }
                if (isStopped) {
                    isKO = true
                    return@forEachIndexed
                }
            }
        } catch (e: Exception) {
            Timber.w(e)
            isKO = true
        } finally {
            targetImg.recycle()
        }

        return if (!isKO) {
            result.map { it.second }
        } else {
            // Remove processed files, if any
            if (result.isNotEmpty())
                removeDocs(contentFolder.uri, result.map { it.first.name ?: "" })
            emptyList()
        }
    }

    private suspend fun processManhwaImageQueue(
        queue: MutableList<ManhwaProcessingItem>,
        targetImg: Bitmap,
        pixelBuffer: IntArray,
        startIndex: Int,
        targetDims: Point,
        contentFolder: DocumentFile,
        params: TransformParams
    ): List<Pair<DocumentFile, ImageFile>> {
        if (queue.isEmpty()) return emptyList()

        Timber.d("process queue (${queue.size} items)")
        val result = ArrayList<Pair<DocumentFile, ImageFile>>()
        var yOffset = 0
        var containsLossless = false
        queue.forEachIndexed { idx, img ->
            withContext(Dispatchers.IO) {
                // Build raw bitmap
                getInputStream(applicationContext, img.doc).use {
                    val rawData = it.readBytes()
                    if (!containsLossless && isImageLossless(rawData)) containsLossless = true
                    val bmp = BitmapFactory.decodeByteArray(rawData, 0, rawData.size)

                    var linesToBuffer = img.toConsumeHeight
                    Timber.d("copy ${img.doc.name ?: ""} from ${img.toConsumeOffset} to ${img.toConsumeOffset + img.toConsumeHeight}")
                    while (linesToBuffer > 0) {
                        val bufTaken = min(linesToBuffer, PIXEL_BUFFER_HEIGHT)
                        bmp.getPixels(
                            pixelBuffer,
                            0,
                            img.dims.x,
                            0,
                            img.toConsumeOffset + (img.toConsumeHeight - linesToBuffer),
                            img.dims.x,
                            bufTaken
                        )
                        val xOffset = (targetDims.x - img.dims.x) / 2 // Center pic
                        targetImg.setPixels(
                            pixelBuffer,
                            0,
                            img.dims.x,
                            xOffset,
                            yOffset + (img.toConsumeHeight - linesToBuffer),
                            img.dims.x,
                            bufTaken
                        )
                        linesToBuffer -= bufTaken
                    }
                    yOffset += img.toConsumeHeight
                    bmp.recycle()
                }
            }
        }

        val encoder = determineEncoder(containsLossless, targetDims, params)
        val targetName = formatIntAsStr(startIndex, 4)
        Timber.d("create image $targetName (${encoder.mimeType})")

        val targetDoc = findOrCreateDocumentFile(
            applicationContext,
            contentFolder,
            encoder.mimeType,
            targetName + "." + getExtensionFromMimeType(encoder.mimeType)
        )
        if (targetDoc != null) {
            Timber.d("Compressing...")
            val targetData = transcodeTo(targetImg, encoder, params.transcodeQuality)
            Timber.d("Saving...")
            saveBinary(applicationContext, targetDoc.uri, targetData)
            // Update image properties
            val newImg = ImageFile()
            newImg.name = targetName
            newImg.order = startIndex
            newImg.fileUri = targetDoc.uri.toString()
            newImg.size = targetData.size.toLong()
            newImg.status = StatusContent.DOWNLOADED
            newImg.isTransformed = true
            result.add(Pair(targetDoc, newImg))

            nextOK()
        } else {
            nextKO()
        }
        launchProgressNotification()

        val last = queue.last()
        queue.clear()

        // Is the last image of the queue completely consumed?
        if (last.toConsumeHeight != last.dims.y) {
            val remainingHeight = last.dims.y - last.toConsumeHeight
            queue.add(
                ManhwaProcessingItem(
                    last.img,
                    last.doc,
                    last.dims,
                    last.toConsumeHeight,
                    remainingHeight
                )
            )
            // Reprocess the queue right now if there's a remanining image with enough height
            if (remainingHeight >= targetDims.y) {
                result.addAll(
                    processManhwaImageQueue(
                        queue,
                        targetImg,
                        pixelBuffer,
                        startIndex + 1,
                        targetDims,
                        contentFolder,
                        params
                    )
                )
            }
        }

        return result
    }

    private fun removeDocs(root: Uri, names: Collection<String>) {
        val builder = DeleteData.Builder()
        builder.setOperation(BaseDeleteWorker.Operation.DELETE)
        builder.setDocsRootAndNames(root, names)
        val workManager = WorkManager.getInstance(applicationContext)
        val request: WorkRequest =
            OneTimeWorkRequest.Builder(DeleteWorker::class.java)
                .setInputData(builder.data).build()
        workManager.enqueue(request)
    }

    private fun nextOK() {
        nbOK++
    }

    private fun nextKO() {
        nbKO++
    }

    override fun runProgressNotification() {
        notificationManager.notify(
            TransformProgressNotification(
                nbOK + nbKO, totalItems, globalProgress.getGlobalProgress()
            )
        )
    }

    private fun notifyProcessEnd() {
        notificationManager.notifyLast(TransformCompleteNotification(nbOK, nbKO > 0))
    }
}