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
import me.devsaki.hentoid.util.createJson
import me.devsaki.hentoid.util.file.Beholder
import me.devsaki.hentoid.util.file.copyFile
import me.devsaki.hentoid.util.file.getDocumentFromTreeUri
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.getExtensionFromMimeType
import me.devsaki.hentoid.util.file.getInputStream
import me.devsaki.hentoid.util.file.getMimeTypeFromFileName
import me.devsaki.hentoid.util.file.getOrCreateCacheFolder
import me.devsaki.hentoid.util.file.getParent
import me.devsaki.hentoid.util.file.saveBinary
import me.devsaki.hentoid.util.getStorageRoot
import me.devsaki.hentoid.util.image.TransformParams
import me.devsaki.hentoid.util.image.clearCoilCache
import me.devsaki.hentoid.util.image.determineEncoder
import me.devsaki.hentoid.util.image.isImageLossless
import me.devsaki.hentoid.util.image.transform
import me.devsaki.hentoid.util.image.transformManhwaChapter
import me.devsaki.hentoid.util.network.UriParts
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.util.pause
import me.devsaki.hentoid.util.updateJson
import me.robb.ai_upscale.AiUpscaler
import okio.IOException
import timber.log.Timber
import java.io.File
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
    private var targetDirectory: DocumentFile? = null


    override fun getStartNotification(): BaseNotification {
        return TransformProgressNotification(0, 0, 0f)
    }

    override fun onInterrupt() {
        targetDirectory?.delete()
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
            totalItems += dao.selectImagesFromContent(it, true).count { i -> i.isReadable }
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
        val ctx = applicationContext
        // Copy to keep it intact after switching to new ones
        val sourceImages = content.imageList.toList()
        Timber.d("Transforming Content BEGIN ${content.title}")

        // Create target folder
        val sourceAndTarget = try {
            withContext(Dispatchers.IO) {
                val sourceFolder = getDocumentFromTreeUriString(ctx, content.storageUri)
                    ?: throw java.io.IOException("Source folder not found")
                // Split / merge manhwa => Need target folder
                val target = if (4 == params.resizeMethod) {
                    val sourceFolder = getDocumentFromTreeUriString(ctx, content.storageUri)
                        ?: throw java.io.IOException("Source folder not found")
                    val root = content.getStorageRoot()
                        ?: throw java.io.IOException("Storage root not found")
                    val parentUri = getParent(ctx, root, sourceFolder.uri)
                        ?: throw java.io.IOException("Parent Uri not found")
                    val parent = getDocumentFromTreeUri(ctx, parentUri)
                        ?: throw java.io.IOException("Parent folder not found")
                    parent.createDirectory((sourceFolder.name ?: "") + "_T")
                        ?: throw java.io.IOException("Target folder couldn't be created")
                } else null
                Pair(sourceFolder, target)
            }
        } catch (e: IOException) {
            Timber.w(e)
            nbKO += sourceImages.size
            return
        }
        val sourceFolder = sourceAndTarget.first
        val targetFolder = sourceAndTarget.second
        targetDirectory = targetFolder

        val transformedImages = ArrayList<ImageFile>()

        if (targetFolder != null) {
            // Don't scan new folder when it's being populated
            Beholder.ignoreFolder(targetFolder)

            // Transfer 'unreadable pics' (i.e. separate cover)
            sourceImages.filter { !it.isReadable }.forEach { img ->
                val name = UriParts(img.fileUri).fileNameFull
                copyFile(
                    ctx,
                    img.fileUri.toUri(),
                    targetFolder,
                    getMimeTypeFromFileName(name),
                    name
                )?.let { newUri ->
                    img.fileUri = newUri.toString()
                    transformedImages.add(img)
                }
            }
        }

        var isKO = false
        val imagesWithoutChapters =
            sourceImages.filter { null == it.linkedChapter }.filter { it.isReadable }
        if (imagesWithoutChapters.isNotEmpty()) {
            val newImgs = transformChapter(
                imagesWithoutChapters,
                1,
                sourceFolder,
                targetFolder,
                params
            )
            if (newImgs.isEmpty()) isKO = true
            else transformedImages.addAll(newImgs)
        }

        val chapteredImgs =
            sourceImages.filterNot { null == it.linkedChapter }.filter { it.isReadable }
                .groupBy { it.linkedChapter!!.id }

        chapteredImgs.filter { it.value.isNotEmpty() }.forEach { chImgs ->
            val newImgs = transformChapter(
                chImgs.value,
                transformedImages.size + 1,
                sourceFolder,
                targetFolder,
                params
            )
            if (newImgs.isEmpty()) isKO = true
            else {
                // Map new images to existing Content and Chapter
                newImgs.forEach {
                    it.content.targetId = content.id
                    it.chapterId = chImgs.key
                }
                transformedImages.addAll(newImgs)
            }
        }

        if (!isKO && !isStopped) {
            // Update Content
            withContext(Dispatchers.IO) {
                content.setImageFiles(transformedImages)
                dao.insertImageFiles(transformedImages)
                content.qtyPages = transformedImages.count { it.isReadable }
                content.computeSize()
                content.lastEditDate = Instant.now().toEpochMilli()
                content.isBeingProcessed = false
                targetFolder?.let { content.storageUri = it.uri.toString() }
                dao.insertContentCore(content)
                if (targetFolder != null) createJson(ctx, content)
                else updateJson(ctx, content)
                dao.cleanup()
            }

            // Remove old folder with old images
            if (targetFolder != null) sourceFolder.delete()
        } else {
            nbKO += sourceImages.size

            // Remove processed images
            targetFolder?.delete()
        }
        Timber.d("Transforming Content END ${content.title}")

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
    }

    private suspend fun transformChapter(
        imgs: List<ImageFile>,
        firstIndex: Int,
        sourceFolder: DocumentFile,
        targetFolder: DocumentFile?,
        params: TransformParams
    ): List<ImageFile> {
        val nbManhwa = AtomicInteger(0)
        params.forceManhwa = false

        if (4 == params.resizeMethod) {
            // Split / merge manhwa
            targetFolder ?: return emptyList()
            return transformManhwaChapter(
                applicationContext,
                imgs,
                firstIndex,
                targetFolder.uri,
                params,
                false,
                this::isStopped
            ) { p ->
                if (p.second) nextOK() else nextKO()
                globalProgress.setProgress(p.first.toString(), 1f)
                launchProgressNotification()
            }
        } else {
            val result = ArrayList<ImageFile>()
            // Per image individual transform
            imgs.forEach {
                result.add(transformImage(it, sourceFolder, params, nbManhwa, imgs.size))
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