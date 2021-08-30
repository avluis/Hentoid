package me.devsaki.hentoid.util

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.Consts
import me.devsaki.hentoid.workers.UpdateDownloadWorker
import me.devsaki.hentoid.workers.data.UpdateDownloadData

class AppHelper {

    companion object {
        fun runUpdateDownloadWorker(context: Context, apkUrl: String) {
            if (!UpdateDownloadWorker.isRunning(context) && apkUrl.isNotEmpty()) {
                val builder = UpdateDownloadData.Builder()
                builder.setUrl(apkUrl)

                val workManager = WorkManager.getInstance(context)
                workManager.enqueueUniqueWork(
                    R.id.update_download_service.toString(),
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
                        .setInputData(builder.data)
                        .addTag(Consts.WORK_CLOSEABLE)
                        .build()
                )
            }
        }
    }
}