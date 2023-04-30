package me.devsaki.hentoid.workers

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.WorkerParameters
import com.bumptech.glide.Glide
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.notification.transform.TransformProgressNotification
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.image.ImageHelper
import me.devsaki.hentoid.util.image.ImageTransform
import me.devsaki.hentoid.util.network.HttpHelper
import me.devsaki.hentoid.util.notification.Notification

class TransformWorker(context: Context, parameters: WorkerParameters) :
    BaseWorker(context, parameters, R.id.transform_service, null) {

    private val threadPoolScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

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
        // Count the total number of images to convert
        contentIds.forEach {
            totalItems += dao.selectDownloadedImagesFromContent(it).count { i -> i.isReadable }
        }

        // Process images
        contentIds.forEach {
            val content = dao.selectContent(it)
            if (content != null) transformContent(content, params)
        }
        notifyProcessEnd()
    }

    private fun transformContent(content: Content, params: ImageTransform.Params) {
        val contentFolder =
            FileHelper.getDocumentFromTreeUriString(applicationContext, content.storageUri)
        val images = content.imageList.filter { i -> i.isReadable }
        if (contentFolder != null) {
            images.forEach { img ->
                transformImage(img, contentFolder, params)
            }
            dao.insertImageFiles(images)
            content.computeSize()
            dao.insertContentCore(content)
        } else {
            nbKO += images.size
            notifyProcessProgress()
        }
    }

    @Suppress("ReplaceArrayEqualityOpWithArraysEquals")
    private fun transformImage(
        img: ImageFile,
        contentFolder: DocumentFile,
        params: ImageTransform.Params
    ) {
        val fileUri = Uri.parse(img.fileUri)
        val rawData = FileHelper.getInputStream(applicationContext, fileUri).use {
            return@use it.readBytes()
        }
        val sourceName = HttpHelper.UriParts(img.fileUri).entireFileName

        val isLossless = ImageHelper.isImageLossless(rawData)
        val targetMime = ImageTransform.determineEncoder(isLossless, params).mimeType
        val targetName = img.name + "." + FileHelper.getExtensionFromMimeType(targetMime)

        val targetData = ImageTransform.transform(rawData, params)
        if (targetData == rawData) return // Unchanged picture

        val newFile = !sourceName.equals(targetName)
        val targetUri = if (!newFile) fileUri
        else {
            val targetFile = contentFolder.createFile(targetMime, targetName)
            if (targetFile != null) FileHelper.removeFile(applicationContext, fileUri)
            targetFile?.uri
        }
        if (targetUri != null) {
            FileHelper.saveBinary(applicationContext, targetUri, targetData)
            // Update image properties
            img.fileUri = targetUri.toString()
            img.size = targetData.size.toLong()
            img.mimeType = targetMime
            nextOK()
        } else nextKO()
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
        threadPoolScope.launch {
            notificationManager.notify(
                TransformProgressNotification(
                    nbOK + nbKO,
                    totalItems
                )
            )
        }
    }

    private fun notifyProcessEnd() {
        notificationManager.notify(TransformProgressNotification(nbOK, nbKO))
    }
}