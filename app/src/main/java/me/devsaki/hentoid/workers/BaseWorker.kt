package me.devsaki.hentoid.workers

import android.content.Context
import android.util.Log
import androidx.annotation.IdRes
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.core.convertLocaleToEnglish
import me.devsaki.hentoid.events.ServiceDestroyedEvent
import me.devsaki.hentoid.util.LogEntry
import me.devsaki.hentoid.util.LogInfo
import me.devsaki.hentoid.util.notification.BaseNotification
import me.devsaki.hentoid.util.notification.NotificationManager
import me.devsaki.hentoid.util.writeLog
import org.greenrobot.eventbus.EventBus
import timber.log.Timber

/**
 * Base class for all workers
 */
abstract class BaseWorker(
    context: Context,
    parameters: WorkerParameters,
    @IdRes val serviceId: Int,
    logName: String?
) : CoroutineWorker(context, parameters) {
    protected lateinit var notificationManager: NotificationManager

    protected var isComplete = true

    protected var logName: String
    protected var logNoDataMessage = ""
    protected var logs: MutableList<LogEntry>?
        private set

    companion object {
        @JvmStatic
        protected fun isRunning(context: Context, @IdRes serviceId: Int): Boolean {
            val infos =
                WorkManager.getInstance(context).getWorkInfosForUniqueWork(serviceId.toString())
            try {
                val info = infos.get().firstOrNull { i -> !i.state.isFinished }
                return info != null
            } catch (e: Exception) {
                Timber.e(e)
                // Restore interrupted state
                Thread.currentThread().interrupt()
            }
            return false
        }
    }

    init {
        // Change locale if set manually
        context.convertLocaleToEnglish()
        initNotifications(context)
        Timber.w("%s worker created", this.javaClass.simpleName)
        if (!logName.isNullOrEmpty()) {
            this.logName = logName
            logs = ArrayList()
            logs?.add(LogEntry("worker created"))
        } else {
            this.logName = ""
            logs = null
        }
    }

    private fun initNotifications(context: Context) {
        notificationManager = NotificationManager(context, serviceId)
        notificationManager.cancel()
    }

    private fun ensureLongRunning() {
        setForegroundAsync(notificationManager.buildForegroundInfo(getStartNotification()!!))
    }

    protected fun trace(priority: Int, msg: String?, vararg t: Any?) {
        var s = msg ?: ""
        s = String.format(s, *t)
        Timber.log(priority, s)
        val isError = priority > Log.INFO
        logs?.add(LogEntry(s, isError))
    }

    private fun clear() {
        logs?.apply {
            add(LogEntry("Worker destroyed / stopped=%s / complete=%s", isStopped, isComplete))
        }
        val logFile = dumpLog()
        onClear(logFile)

        // Tell everyone the worker is shutting down
        EventBus.getDefault().post(ServiceDestroyedEvent(serviceId))
        Timber.d("%s worker destroyed", this.javaClass.simpleName)
    }

    override suspend fun doWork(): Result {
        try {
            ensureLongRunning()
            withContext(Dispatchers.IO) {
                getToWork(inputData)
            }
        } catch (e: Exception) {
            onInterrupt()
            logs?.apply {
                add(LogEntry("Exception caught ! %s : %s", e.message, e.stackTrace))
            }
            Timber.e(e)
        } finally {
            clear()
        }
        // Retry when incomplete and not manually stopped
        return if (!isStopped && !isComplete) Result.retry() else Result.success()
    }

    private fun dumpLog(): DocumentFile? {
        return logs?.let {
            val logInfo = LogInfo(logName)
            logInfo.setHeaderName(logName)
            logInfo.setEntries(it)
            applicationContext.writeLog(logInfo)
        }
    }

    protected abstract fun getStartNotification(): BaseNotification?

    protected abstract fun onInterrupt()

    protected abstract fun onClear(logFile: DocumentFile?)

    protected abstract fun getToWork(input: Data)
}