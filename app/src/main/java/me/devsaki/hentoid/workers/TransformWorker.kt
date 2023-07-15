package me.devsaki.hentoid.workers

import android.content.Context
import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.WorkerParameters
import com.bumptech.glide.Glide
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import me.devsaki.hentoid.R
import me.devsaki.hentoid.ai_upscale.NativeLib
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.notification.transform.TransformCompleteNotification
import me.devsaki.hentoid.notification.transform.TransformProgressNotification
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.image.ImageHelper
import me.devsaki.hentoid.util.image.ImageTransform
import me.devsaki.hentoid.util.notification.Notification
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger


class TransformWorker(context: Context, parameters: WorkerParameters) :
    BaseWorker(context, parameters, R.id.transform_service, null) {

    private val dao: CollectionDAO
    private var totalItems = 0
    private var nbOK = 0
    private var nbKO = 0

    init {
        dao = ObjectBoxDAO(context)
    }


    override fun getStartNotification(): Notification {
        return TransformProgressNotification(0, 0)
    }

    override fun onInterrupt() {
        // Nothing
    }

    override fun onClear() {
        dao.cleanup()

        // Reset Glide cache as it gets confused by the resizing
        Glide.get(applicationContext).clearDiskCache()
    }

    override fun getToWork(input: Data) {
        val contentIds = inputData.getLongArray("IDS")
        val paramsStr = inputData.getString("PARAMS")
        require(contentIds != null)
        require(paramsStr != null)
        require(paramsStr.isNotEmpty())

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        val params = moshi.adapter(ImageTransform.Params::class.java).fromJson(paramsStr)
        require(params != null)

        transform(contentIds, params)
    }

    private fun transform(contentIds: LongArray, params: ImageTransform.Params) {
        // Flag contents as "being deleted" (triggers blink animation; lock operations)
        // +count the total number of images to convert
        contentIds.forEach {
            if (it > 0) dao.updateContentDeleteFlag(it, true)
            totalItems += dao.selectDownloadedImagesFromContent(it).count { i -> i.isReadable }
            if (isStopped) return
        }

        // Process images
        contentIds.forEach {
            val content = dao.selectContent(it)
            if (content != null) transformContent(content, params)
            if (isStopped) return
        }
        notifyProcessEnd()
    }

    private fun transformContent(content: Content, params: ImageTransform.Params) {
        val contentFolder =
            FileHelper.getDocumentFromTreeUriString(applicationContext, content.storageUri)
        val images = content.imageList
        if (contentFolder != null) {
            val imagesWithoutChapters = images
                .filter { i -> null == i.linkedChapter }
                .filter { i -> i.isReadable }
            transformChapter(imagesWithoutChapters, contentFolder, params)

            val chapteredImgs = images
                .filterNot { i -> null == i.linkedChapter }
                .filter { i -> i.isReadable }
                .groupBy { i -> i.linkedChapter!!.id }

            chapteredImgs.forEach {
                transformChapter(it.value, contentFolder, params)
            }

            dao.insertImageFiles(images)
            content.computeSize()
            content.lastEditDate = Instant.now().toEpochMilli()
            content.setIsBeingProcessed(false)
            dao.insertContentCore(content)
        } else {
            nbKO += images.size
            notifyProcessProgress()
        }
    }

    private fun transformChapter(
        imgs: List<ImageFile>,
        contentFolder: DocumentFile,
        params: ImageTransform.Params
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
        params: ImageTransform.Params,
        nbManhwa: AtomicInteger,
        nbPages: Int
    ) {
        val sourceFile = FileHelper.getDocumentFromTreeUriString(applicationContext, img.fileUri)
        if (null == sourceFile) {
            nextKO()
            return
        }
        val rawData = FileHelper.getInputStream(applicationContext, sourceFile).use {
            return@use it.readBytes()
        }
        val isLossless = ImageHelper.isImageLossless(rawData)
        val sourceName = sourceFile.name ?: ""
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(rawData, 0, rawData.size, options)
        val isManhwa = options.outHeight * 1.0 / options.outWidth > 3

        if (isManhwa) nbManhwa.incrementAndGet()
        params.forceManhwa = nbManhwa.get() * 1.0 / nbPages > 0.9

        /* TODO TEMP */
        val bmp = BitmapFactory.decodeByteArray(rawData, 0, rawData.size)
        try {
            val absSourcePath =
                FileHelper.getFullPathFromUri(applicationContext, Uri.parse(img.fileUri))
            val mgr: AssetManager = applicationContext.resources.assets
            val upscale = NativeLib()
            val res = upscale.upscale(
                mgr,
                "realsr/models-nose/up2x-no-denoise.param",
                "realsr/models-nose/up2x-no-denoise.bin",
                bmp,
                absSourcePath
            )
            if (res > -1) nextOK() else nextKO()
        } finally {
            bmp.recycle();
        }
        /* TEMP */

        /*
                val targetData = ImageTransform.transform(rawData, params)
                if (targetData == rawData) return // Unchanged picture

         */

        /*
        BitmapFactory.decodeByteArray(targetData, 0, targetData.size, options)
        val targetDims = Point(options.outWidth, options.outHeight)
        val targetMime = ImageTransform.determineEncoder(isLossless, targetDims, params).mimeType
        val targetName = img.name + "." + FileHelper.getExtensionFromMimeType(targetMime)
        val newFile = sourceName != targetName

        val targetUri = if (!newFile) sourceFile.uri
        else {
            val targetFile = contentFolder.createFile(targetMime, targetName)
            if (targetFile != null) sourceFile.delete()
            targetFile?.uri
        }
        if (targetUri != null) {
            FileHelper.saveBinary(applicationContext, targetUri, targetData)
            // Update image properties
            img.fileUri = targetUri.toString()
            img.size = targetData.size.toLong()
            img.isTransformed = true
            img.mimeType = targetMime
            nextOK()
        } else nextKO()
        */
    }

    private fun nextOK() {
        nbOK++
        notifyProcessProgress()
    }

    private fun nextKO() {
        nbKO++
        notifyProcessProgress()
    }

    private fun notifyProcessProgress() {
        notificationManager.notify(TransformProgressNotification(nbOK + nbKO, totalItems))
    }

    private fun notifyProcessEnd() {
        notificationManager.notifyLast(TransformCompleteNotification(nbOK, nbKO > 0))
    }
}