package me.devsaki.hentoid.workers

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.Data
import androidx.work.WorkerParameters
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.events.UpdateEvent
import me.devsaki.hentoid.json.core.UpdateInfo
import me.devsaki.hentoid.notification.update.UpdateAvailableNotification
import me.devsaki.hentoid.notification.update.UpdateCheckNotification
import me.devsaki.hentoid.retrofit.UpdateServer
import me.devsaki.hentoid.util.notification.BaseNotification
import org.greenrobot.eventbus.EventBus
import timber.log.Timber

class UpdateCheckWorker(context: Context, parameters: WorkerParameters) :
    BaseWorker(context, parameters, R.id.update_check_service, null) {

    override fun getStartNotification(): BaseNotification {
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
            EventBus.getDefault().post(
                CommunicationEvent(
                    CommunicationEvent.EV_BROADCAST,
                    CommunicationEvent.RC_PREFS,
                    applicationContext.resources.getString(R.string.pref_check_updates_manual_checking)
                )
            )
            val updateInfoJson = UpdateServer.api.updateInfo.execute().body()
            if (updateInfoJson != null) onSuccess(updateInfoJson)
            else {
                EventBus.getDefault().post(
                    CommunicationEvent(
                        CommunicationEvent.EV_BROADCAST,
                        CommunicationEvent.RC_PREFS,
                        applicationContext.resources.getString(R.string.pref_check_updates_manual_no_connection)
                    )
                )
                Timber.w("Failed to get update info (null result)")
            }
        } catch (e: Exception) {
            EventBus.getDefault().post(
                CommunicationEvent(
                    CommunicationEvent.EV_BROADCAST,
                    CommunicationEvent.RC_PREFS,
                    applicationContext.resources.getString(R.string.pref_check_updates_manual_no_connection)
                )
            )
            Timber.w(e, "Failed to get update info")
            notificationManager.cancel()
        }
    }

    private fun onSuccess(updateInfoJson: UpdateInfo) {
        var newVersion = false
        if (BuildConfig.VERSION_CODE < updateInfoJson.getVersionCode(BuildConfig.DEBUG)) {
            val updateUrl: String = updateInfoJson.getUpdateUrl(BuildConfig.DEBUG)
            notificationManager.notifyLast(UpdateAvailableNotification(updateUrl))
            newVersion = true
            EventBus.getDefault().post(
                CommunicationEvent(
                    CommunicationEvent.EV_BROADCAST,
                    CommunicationEvent.RC_PREFS,
                    applicationContext.resources.getString(R.string.pref_check_updates_manual_new)
                )
            )
        } else {
            EventBus.getDefault().post(
                CommunicationEvent(
                    CommunicationEvent.EV_BROADCAST,
                    CommunicationEvent.RC_PREFS,
                    applicationContext.resources.getString(R.string.pref_check_updates_manual_no_new)
                )
            )
        }

        // Get the alerts relevant to current version code
        val sourceAlerts: List<UpdateInfo.SourceAlert> =
            updateInfoJson.getSourceAlerts(BuildConfig.DEBUG)
                .filter { a -> a.fixedByBuild > BuildConfig.VERSION_CODE }
        // Send update info through the bus to whom it may concern
        EventBus.getDefault().postSticky(UpdateEvent(newVersion, sourceAlerts))
    }
}