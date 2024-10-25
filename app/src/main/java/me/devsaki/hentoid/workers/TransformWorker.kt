package me.devsaki.hentoid.workers

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Point
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.WorkerParameters
import coil3.SingletonImageLoader
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.robb.ai_upscale.AiUpscaler
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
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.util.pause
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


    override fun getStartNotification(): BaseNotification {
        return TransformProgressNotification(0, 0, 0f)
    }

    override fun onInterrupt() {
        // Nothing
    }

    override fun onClear(logFile: DocumentFile?) {
        dao.cleanup()
        upscaler?.cleanup()

        // Reset Coil cache as it gets confused by the resizing
        clearCoilCache(applicationContext)
    }

    override fun getToWork(input: Data) {
        val contentIds = inputData.getLongArray("IDS")
        val paramsStr = inputData.getString("PARAMS")
        require(contentIds != null)
        require(paramsStr != null)
        require(paramsStr.isNotEmpty())

        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

        val params = moshi.adapter(TransformParams::class.java).fromJson(paramsStr)
        require(params != null)

        if (params.resizeEnabled && 3 == params.resizeMethod) { // AI upscale
            upscaler = AiUpscaler()
            upscaler!!.init(
                applicationContext.resources.assets,
                "realsr/models-nose/up2x-no-denoise.param",
                "realsr/models-nose/up2x-no-denoise.bin"
            )
        }

        transform(contentIds, params)
    }

    private fun transform(contentIds: LongArray, params: TransformParams) {
        // Flag contents as "being deleted" (triggers blink animation; lock operations)
        // +count the total number of images to convert
        contentIds.forEach {
            if (it > 0) dao.updateContentProcessedFlag(it, true)
            totalItems += dao.selectDownloadedImagesFromContent(it).count { i -> i.isReadable }
            if (isStopped) return
        }

        globalProgress = ProgressManager(totalItems)
        notifyProcessProgress()

        // Process images
        contentIds.forEach {
            val content = dao.selectContent(it)
            if (content != null) transformContent(content, params)
            if (isStopped) return
        }
        notifyProcessEnd()
    }

    private fun transformContent(content: Content, params: TransformParams) {
        val contentFolder = getDocumentFromTreeUriString(applicationContext, content.storageUri)
        val images = content.imageList
        if (contentFolder != null) {
            val imagesWithoutChapters =
                images.filter { i -> null == i.linkedChapter }.filter { i -> i.isReadable }
            transformChapter(imagesWithoutChapters, contentFolder, params)

            val chapteredImgs =
                images.filterNot { i -> null == i.linkedChapter }.filter { i -> i.isReadable }
                    .groupBy { i -> i.linkedChapter!!.id }

            chapteredImgs.forEach {
                transformChapter(it.value, contentFolder, params)
            }

            dao.insertImageFiles(images)
            content.computeSize()
            content.lastEditDate = Instant.now().toEpochMilli()
            content.isBeingProcessed = false
            dao.insertContentCore(content)

            // Achievements
            if (!isStopped) {
                if (upscaler != null) { // AI upscale
                    Settings.nbAIRescale += 1
                    if (Settings.nbAIRescale >= 2) AchievementsManager.trigger(20)
                }
                val pagesTotal = images.count { i -> i.isReadable }
                if (pagesTotal >= 50) AchievementsManager.trigger(27)
                if (pagesTotal >= 100) AchievementsManager.trigger(28)
            }
        } else {
            nbKO += images.size
        }
    }

    private fun transformChapter(
        imgs: List<ImageFile>, contentFolder: DocumentFile, params: TransformParams
    ) {
        val nbManhwa = AtomicInteger(0)
        params.forceManhwa = false

        imgs.forEach {
            transformImage(it, contentFolder, params, nbManhwa, imgs.size)
            if (isStopped) return
        }
    }

    @Suppress("ReplaceArrayEqualityOpWithArraysEquals")
    private fun transformImage(
        img: ImageFile,
        contentFolder: DocumentFile,
        params: TransformParams,
        nbManhwa: AtomicInteger,
        nbPages: Int
    ) {
        val sourceFile = getDocumentFromTreeUriString(applicationContext, img.fileUri)
        if (null == sourceFile) {
            nextKO()
            return
        }
        val rawData = getInputStream(applicationContext, sourceFile).use {
            return@use it.readBytes()
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
        if (isStopped) return
        if (targetData == rawData) return // Unchanged picture

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
            img.mimeType = targetMime

            nextOK()
            globalProgress.setProgress(imageId, 1f)
            notifyProcessProgress()
        } else {
            nextKO()
            notifyProcessProgress()
        }
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
                CoroutineScope(Dispatchers.Default).launch {
                    val res = withContext(Dispatchers.Default) {
                        it.upscale(
                            dataIn, outputFile.absolutePath, progress, killSwitch
                        )
                    }
                    // Fail => exit immediately
                    if (res != 0) progress.put(0, 100)
                }

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
                    notifyProcessProgress()

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

    private fun notifyProcessProgress() {
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