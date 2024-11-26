package me.devsaki.hentoid.workers

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.notification.jsonUpdate.UpdateJsonCompleteNotification
import me.devsaki.hentoid.notification.jsonUpdate.UpdateJsonProgressNotification
import me.devsaki.hentoid.notification.jsonUpdate.UpdateJsonStartNotification
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.util.persistJson
import me.devsaki.hentoid.util.updateGroupsJson
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

    override suspend fun onClear(logFile: DocumentFile?) {
        dao.cleanup()
    }

    override suspend fun getToWork(input: Data) {
        val data = UpdateJsonData.Parser(inputData)
        var contentIds = data.contentIds

        if (data.updateMissingDlDate) contentIds = dao.selectContentIdsWithUpdatableJson()

        if (null == contentIds) {
            Timber.w("Expected contentIds or selectContentIdsWithUpdatableJson")
            return
        }

        totalItems = contentIds.size

        contentIds.forEach {
            dao.selectContent(it)?.let { c ->
                withContext(Dispatchers.IO) { persistJson(applicationContext, c) }
                nextOK()
            }
        }
        progressDone()

        if (data.updateGroups) updateGroupsJson(applicationContext, dao)
    }

    private fun nextOK() {
        nbOK++
        launchProgressNotification()
    }

    override fun runProgressNotification() {
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