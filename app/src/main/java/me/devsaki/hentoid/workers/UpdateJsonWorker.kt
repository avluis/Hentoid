package me.devsaki.hentoid.workers

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.notification.updateJson.UpdateJsonCompleteNotification
import me.devsaki.hentoid.notification.updateJson.UpdateJsonProgressNotification
import me.devsaki.hentoid.notification.updateJson.UpdateJsonStartNotification
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.GroupHelper
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.workers.data.UpdateJsonData
import org.greenrobot.eventbus.EventBus
import timber.log.Timber

/**
 * Service responsible for updating JSON files
 */
class UpdateJsonWorker(context: Context, parameters: WorkerParameters) :
    BaseWorker(context, parameters, R.id.udpate_json_service, "update_json") {

    // Variable used during the import process
    private var dao: CollectionDAO = ObjectBoxDAO()
    private var totalItems = 0
    private var nbOK = 0

    override fun getStartNotification(): BaseNotification {
        return UpdateJsonStartNotification()
    }

    override fun onInterrupt() {
        // Nothing
    }

    override fun onClear(logFile: DocumentFile?) {
        dao.cleanup()
    }

    override fun getToWork(input: Data) {
        val data = UpdateJsonData.Parser(inputData)
        var contentIds = data.contentIds

        if (data.updateMissingDlDate) contentIds = dao.selectContentIdsWithUpdatableJson()

        if (null == contentIds) {
            Timber.w("Expected contentIds or selectContentIdsWithUpdatableJson")
            return
        }

        totalItems = contentIds.size

        for (id in contentIds) {
            val c = dao.selectContent(id)
            if (c != null) ContentHelper.persistJson(applicationContext, c)
            nextOK()
        }
        progressDone()

        if (data.updateGroups) GroupHelper.updateGroupsJson(applicationContext, dao)
    }

    private fun nextOK() {
        nbOK++
        notifyProcessProgress()
    }

    private fun notifyProcessProgress() {
        CoroutineScope(Dispatchers.Default).launch {
            withContext(Dispatchers.Main) {
                doNotifyProcessProgress()
            }
        }
    }

    private fun doNotifyProcessProgress() {
        notificationManager.notify(UpdateJsonProgressNotification(nbOK, totalItems))
        EventBus.getDefault().post(
            ProcessEvent(
                ProcessEvent.Type.PROGRESS,
                R.id.update_json,
                0,
                nbOK,
                0,
                totalItems
            )
        )
    }

    private fun progressDone() {
        notificationManager.notify(UpdateJsonCompleteNotification())
        EventBus.getDefault().postSticky(
            ProcessEvent(
                ProcessEvent.Type.COMPLETE,
                R.id.update_json,
                0,
                nbOK,
                0,
                totalItems
            )
        )
    }
}