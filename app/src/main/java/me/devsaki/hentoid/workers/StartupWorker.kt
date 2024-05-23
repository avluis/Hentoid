package me.devsaki.hentoid.workers

import android.annotation.SuppressLint
import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.WorkerParameters
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.AppStartup
import me.devsaki.hentoid.core.BiConsumer
import me.devsaki.hentoid.database.DatabaseMaintenance
import me.devsaki.hentoid.notification.startup.StartupCompleteNotification
import me.devsaki.hentoid.notification.startup.StartupProgressNotification
import me.devsaki.hentoid.util.notification.BaseNotification
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class StartupWorker(context: Context, parameters: WorkerParameters) :
    BaseWorker(context, parameters, R.id.startup_service, null) {

    private val killSwitch = AtomicBoolean(false)


    override fun getStartNotification(): BaseNotification {
        return StartupProgressNotification("Startup progress", 0, 0)
    }

    override fun onInterrupt() {
        killSwitch.set(true)
    }

    override fun onClear(logFile: DocumentFile?) {
        killSwitch.set(true)
    }

    @SuppressLint("TimberArgCount")
    override fun getToWork(input: Data) {
        val launchTasks: MutableList<BiConsumer<Context, (Float) -> Unit>> = ArrayList()
        launchTasks.addAll(AppStartup.getPostLaunchTasks())
        launchTasks.addAll(DatabaseMaintenance.getPostLaunchCleanupTasks())
        launchTasks.forEachIndexed { index, task ->
            val message = String.format(
                Locale.ENGLISH,
                "Startup progress : step %d / %d",
                index + 1,
                launchTasks.size
            )
            Timber.d(message)
            notificationManager.notify(
                StartupProgressNotification(message, (index / launchTasks.size * 100), 100)
            )
            task.invoke(applicationContext) {
                // No handler for secondary progress
            }
            if (killSwitch.get()) return@forEachIndexed
        }
        notificationManager.notify(StartupCompleteNotification())
    }
}