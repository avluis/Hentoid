package me.devsaki.hentoid.workers

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Point
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
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
import me.devsaki.hentoid.notification.transform.TransformCompleteNotification
import me.devsaki.hentoid.notification.transform.TransformProgressNotification
import me.devsaki.hentoid.util.AchievementsManager
import me.devsaki.hentoid.util.ProgressManager
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.getExtensionFromMimeType
import me.devsaki.hentoid.util.file.getInputStream
import me.devsaki.hentoid.util.file.getOrCreateCacheFolder
import me.devsaki.hentoid.util.file.saveBinary
import me.devsaki.hentoid.util.image.TransformParams
import me.devsaki.hentoid.util.image.clearCoilCache
import me.devsaki.hentoid.util.image.determineEncoder
import me.devsaki.hentoid.util.image.isImageLossless
import me.devsaki.hentoid.util.image.transform
import me.devsaki.hentoid.util.image.transformManhwaChapter
import me.devsaki.hentoid.util.network.UriParts
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.util.pause
import me.devsaki.hentoid.util.removeDocs
import me.robb.ai_upscale.AiUpscaler
import timber.log.Timber
import java.io.File
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger


class TransformWorker(context: Context, parameters: WorkerParameters) :
    BaseWorker(context, parameters, R.id.transform_service, null) {

    private val dao: CollectionDAO = ObjectBoxDAO()
    private var upscaler: AiUpscaler? = null

    private var totalItems = 0
    private var nbOK = 0
    private var nbKO = 0
    private lateinit var globalProgress: ProgressManager
    private lateinit var progressNotification: TransformProgressNotification


    override fun getStartNotification(): BaseNotification {
        return TransformProgressNotification(0, 0, 0f)
    }

    override fun onInterrupt() {
        // Nothing
    }

    override suspend fun onClear(logFile: DocumentFile?) {
        inputData.getLongArray("IDS")?.let { contentIds ->
            dao.updateContentsProcessedFlagById(contentIds.filter { it > 0 }, false)
        }
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
        val sourceImages =
            content.imageList.toList() // Copy to keep it intact after switching to new ones
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

            chapteredImgs.forEach { chgImgs ->
                if (chgImgs.value.isNotEmpty()) {
                    val newImgs = transformChapter(
                        chgImgs.value,
                        transformedImages.size + 1,
                        contentFolder,
                        params
                    )
                    if (newImgs.isEmpty()) isKO = true
                    else transformedImages.addAll(newImgs)
                }

                // Remove old unused images from chapter if any
                // NB : We need to make a small pass after each chapter to avoid breaking
                // the 10kB Data limit if we pass all the images to delete
                // when the whole book has been processed
                if (!isStopped) {
                    val originalUris = chgImgs.value.map { it.fileUri }.toMutableSet()
                    val newUris = transformedImages.map { it.fileUri }.toSet()
                    originalUris.removeAll(newUris)
                    if (originalUris.isNotEmpty())
                        removeDocs(
                            applicationContext,
                            contentFolder.uri,
                            originalUris.map {
                                val parts = UriParts(URLDecoder.decode(it, "UTF-8"))
                                return@map parts.fileNameFull
                            }
                        )
                }
            }

            if (!isKO && !isStopped) {
                // Final Content update
                transformedImages.forEach { it.content.targetId = content.id }
                content.setImageFiles(transformedImages)
                dao.insertImageFiles(transformedImages)
                content.qtyPages = transformedImages.count { it.isReadable }
                content.computeSize()
                content.lastEditDate = Instant.now().toEpochMilli()
                content.isBeingProcessed = false
                dao.insertContentCore(content)

                // Remove old unused images if any (last pass to make sure nothing is left)
                val originalUris = sourceImages.map { it.fileUri }.toMutableSet()
                val newUris = transformedImages.map { it.fileUri }.toSet()
                originalUris.removeAll(newUris)
                if (originalUris.isNotEmpty())
                    removeDocs(
                        applicationContext,
                        contentFolder.uri,
                        originalUris.map {
                            val parts = UriParts(URLDecoder.decode(it, "UTF-8"))
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
            return transformManhwaChapter(
                applicationContext,
                imgs,
                firstIndex,
                contentFolder.uri,
                params,
                false,
                this::isStopped
            ) { p ->
                {
                    if (p.second) nextOK() else nextKO()
                    globalProgress.setProgress(p.first.toString(), 1f)
                    launchProgressNotification()
                }
            }
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
    /*
        private suspend fun transformManhwaChapter(
            sourceImgs: List<ImageFile>,
            firstIndex: Int,
            contentFolder: DocumentFile,
            params: TransformParams
        ): List<ImageFile> {
            val result = ArrayList<Pair<Uri, ImageFile>>()
            Timber.d("transformManhwaChapter (${sourceImgs.size} items starting with $firstIndex)")

            // Compute the height of all images together + largest common width excluding outliers
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

            // Detect outlier images (>10% larger than the others)
            val excludedIndexes = HashSet<Int>()
            val avgWidth =
                weightedAverage(allDims.map { Pair(it.x.toFloat(), it.y.toFloat()) }.toList())
            allDims.forEachIndexed { idx, dim ->
                if (abs(dim.x - avgWidth) / avgWidth > 0.1) excludedIndexes.add(idx)
            }

            // Compute target dims without taking outliers into account
            val totalHeight =
                allDims.filterIndexed { idx, _ -> !excludedIndexes.contains(idx) }.sumOf { it.y }
            val targetDims = Point(
                allDims.filterIndexed { idx, _ -> !excludedIndexes.contains(idx) }.maxOf { it.x },
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
            val processedIds = HashSet<Long>()
            var currentImgIdx = firstIndex
            var isKO = false
            try {
                sourceImgs.forEachIndexed { idx, img ->
                    if (isStopped) {
                        isKO = true
                        return@forEachIndexed
                    }

                    if (excludedIndexes.contains(idx)) {
                        // Reuse outlier file into new image
                        val newImg = ImageFile()
                        val targetDoc = imgDocuments[idx]
                        newImg.name =
                            formatIntAsStr(currentImgIdx, 4) + "." + getExtension(targetDoc.name ?: "")
                        newImg.order = currentImgIdx
                        newImg.fileUri = targetDoc.uri.toString()
                        newImg.size = targetDoc.length()
                        newImg.status = StatusContent.DOWNLOADED
                        newImg.isTransformed = true
                        result.add(Pair(targetDoc.uri, newImg))
                        currentImgIdx++
                        nextOK()
                        return@forEachIndexed
                    }
                    Timber.d("Processing source file ${img.fileUri}")
                    val isLast = idx == sourceImgs.size - 1
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
                    if (leftToConsume <= 0 || isLast) {
                        if (leftToConsume < 0) Timber.w("!!! LEFTTOCONSUME IS NEGATIVE $leftToConsume")
                        val toProcess = processingQueue.map { it.img.id }
                        val newImgs = processManhwaImageQueue(
                            processingQueue,
                            targetImg,
                            pixelBuffer,
                            currentImgIdx,
                            isLast,
                            targetDims,
                            contentFolder,
                            params
                        )
                        // Report results in the notification
                        toProcess.forEach {
                            if (!processedIds.contains(it)) {
                                if (newImgs.isEmpty()) nextKO() else nextOK()
                                processedIds.add(it)
                                globalProgress.setProgress(it.toString(), 1f)
                            }
                        }
                        launchProgressNotification()

                        currentImgIdx += newImgs.size
                        result.addAll(newImgs)
                        consumedHeight = processingQueue.sumOf { it.toConsumeHeight }
                    }
                } // source images loop
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
                if (result.isNotEmpty() && !isStopped) {
                    val docNames = ArrayList<String>()
                    result.forEachIndexed { idx, uri ->
                        if (excludedIndexes.contains(idx)) return@forEachIndexed
                        getDocumentFromTreeUri(applicationContext, uri.first)?.let { doc ->
                            docNames.add(doc.name ?: "")
                        }
                    }
                    removeDocs(contentFolder.uri, docNames)
                }
                emptyList()
            }
        }

        private suspend fun processManhwaImageQueue(
            queue: MutableList<ManhwaProcessingItem>,
            targetImg: Bitmap,
            pixelBuffer: IntArray,
            startIndex: Int,
            isLast: Boolean,
            targetDims: Point,
            contentFolder: DocumentFile,
            params: TransformParams
        ): List<Pair<Uri, ImageFile>> {
            if (queue.isEmpty()) return emptyList()

            Timber.d("process queue (${queue.size} items)")
            val result = ArrayList<Pair<Uri, ImageFile>>()
            var yOffset = 0
            var previousWidth = Int.MAX_VALUE
            var containsLossless = false

            queue.forEach { img ->
                withContext(Dispatchers.IO) {
                    // Build raw bitmap
                    getInputStream(applicationContext, img.doc).use {
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
                        Timber.d("copy ${img.doc.name ?: ""} from ${img.toConsumeOffset} to ${img.toConsumeOffset + img.toConsumeHeight} (dims ${img.dims} ${bmp.width}x${bmp.height})")
                        while (linesToBuffer > 0) {
                            val bufTaken = min(linesToBuffer, PIXEL_BUFFER_HEIGHT)
                            val bufOffset = img.toConsumeHeight - linesToBuffer
                            val xOffset = (targetDims.x - img.dims.x) / 2 // Center pic
                            // Copy source pic to buffer (centered)
                            bmp.getPixels(
                                pixelBuffer,
                                xOffset,
                                targetDims.x,
                                0,
                                img.toConsumeOffset + bufOffset,
                                img.dims.x,
                                bufTaken
                            )
                            // Copy buffer to target pic (whole width)
                            targetImg.setPixels(
                                pixelBuffer,
                                0,
                                targetDims.x,
                                0,
                                yOffset + bufOffset,
                                targetDims.x,
                                bufTaken
                            )
                            linesToBuffer -= bufTaken
                        }
                        yOffset += img.toConsumeHeight
                        bmp.recycle()
                    }
                }
            } // queue loop

            val encoder = determineEncoder(containsLossless, targetDims, params)
            val targetName = formatIntAsStr(startIndex, 4)
            Timber.d("create image $targetName (${encoder.mimeType})")

            createFile(
                applicationContext,
                contentFolder.uri,
                targetName,
                encoder.mimeType,
                false
            ).let { targetUri ->
                Timber.d("Compressing...")
                val targetData = transcodeTo(targetImg, encoder, params.transcodeQuality)
                Timber.d("Saving...")
                saveBinary(applicationContext, targetUri, targetData)
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
                            queue,
                            targetImg,
                            pixelBuffer,
                            startIndex + 1,
                            isLast,
                            targetDims,
                            contentFolder,
                            params
                        )
                    )
                }
            }

            return result
        }
     */

    private fun nextOK() {
        nbOK++
    }

    private fun nextKO() {
        nbKO++
    }

    override fun runProgressNotification() {
        if (!this::progressNotification.isInitialized) {
            progressNotification = TransformProgressNotification(
                nbOK + nbKO, totalItems, globalProgress.getGlobalProgress()
            )
        } else {
            progressNotification.maxItems = totalItems
            progressNotification.processedItems = nbOK + nbKO
            progressNotification.progress = globalProgress.getGlobalProgress()
        }
        if (isStopped) return
        notificationManager.notify(progressNotification)
    }

    private fun notifyProcessEnd() {
        notificationManager.notifyLast(TransformCompleteNotification(nbOK, nbKO > 0))
    }
}