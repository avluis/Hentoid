package me.devsaki.hentoid.workers

import android.content.Context
import androidx.work.Data
import androidx.work.WorkerParameters
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import me.devsaki.hentoid.R
import me.devsaki.hentoid.notification.transform.TransformProgressNotification
import me.devsaki.hentoid.util.image.ImageTransform
import me.devsaki.hentoid.util.notification.Notification
import timber.log.Timber

class TransformWorker(context: Context, parameters: WorkerParameters) :
    BaseWorker(context, parameters, R.id.transform_service, null) {

    override fun getStartNotification(): Notification {
        // TODO
        return TransformProgressNotification(0, 0)
    }

    override fun onInterrupt() {
        // Nothing
    }

    override fun onClear() {
        // Nothing
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

        val serializedParams = moshi.adapter(ImageTransform.Params::class.java).fromJson(paramsStr)
        require(serializedParams != null)

        Timber.i(contentIds.size.toString())
        Timber.i(serializedParams.resize2Width.toString())
    }

}