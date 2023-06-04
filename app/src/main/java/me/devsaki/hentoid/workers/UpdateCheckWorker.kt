package me.devsaki.hentoid.workers

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.Data
import androidx.work.WorkerParameters
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.events.UpdateEvent
import me.devsaki.hentoid.json.core.UpdateInfo
import me.devsaki.hentoid.notification.update.UpdateAvailableNotification
import me.devsaki.hentoid.notification.update.UpdateCheckNotification
import me.devsaki.hentoid.retrofit.UpdateServer
import me.devsaki.hentoid.util.notification.Notification
import org.greenrobot.eventbus.EventBus
import timber.log.Timber

class UpdateCheckWorker(context: Context, parameters: WorkerParameters) :
    BaseWorker(context, parameters, R.id.update_check_service, null) {

    override fun getStartNotification(): Notification {
        return UpdateCheckNotification()
    }

    override fun onInterrupt() {
        // Nothing
    }

    override fun onClear() {
        // Nothing
    }

    @SuppressLint("TimberArgCount")
    override fun getToWork(input: Data) {
        try {
            val updateInfoJson = UpdateServer.api.updateInfo.execute().body()
            if (updateInfoJson != null) onSuccess(updateInfoJson)
            else Timber.w("Failed to get update info (null result)")
        } catch (e: Exception) {
            Timber.w(e, "Failed to get update info")
            notificationManager.cancel()
        }
    }

    private fun onSuccess(updateInfoJson: UpdateInfo) {
        var newVersion = false
        if (BuildConfig.VERSION_CODE < updateInfoJson.getVersionCode(BuildConfig.DEBUG)) {
            val updateUrl: String = updateInfoJson.getUpdateUrl(BuildConfig.DEBUG)
            notificationManager.notify(UpdateAvailableNotification(updateUrl))
            newVersion = true
        }

        // Get the alerts relevant to current version code
        val sourceAlerts: List<UpdateInfo.SourceAlert> =
            updateInfoJson.getSourceAlerts(BuildConfig.DEBUG)
                .filter { a -> a.fixedByBuild > BuildConfig.VERSION_CODE }
        // Send update info through the bus to whom it may concern
        EventBus.getDefault().postSticky(UpdateEvent(newVersion, sourceAlerts))
    }
}