package me.devsaki.hentoid.core

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.DatabaseMaintenance
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.events.AppUpdatedEvent
import me.devsaki.hentoid.json.core.JsonSiteSettings
import me.devsaki.hentoid.notification.delete.DeleteNotificationChannel
import me.devsaki.hentoid.notification.download.DownloadNotificationChannel
import me.devsaki.hentoid.notification.startup.StartupNotificationChannel
import me.devsaki.hentoid.notification.transform.TransformNotificationChannel
import me.devsaki.hentoid.notification.update.UpdateNotificationChannel
import me.devsaki.hentoid.notification.updateJson.UpdateJsonNotificationChannel
import me.devsaki.hentoid.notification.userAction.UserActionNotificationChannel
import me.devsaki.hentoid.receiver.PlugEventsReceiver
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.JsonHelper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.workers.StartupWorker
import me.devsaki.hentoid.workers.UpdateCheckWorker
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.io.IOException

typealias BiConsumer<T, U> = (T, U) -> Unit

object AppStartup {
    private var isInitialized = false

    @Synchronized
    private fun setInitialized() {
        isInitialized = true
    }

    fun initApp(
        context: Context,
        onMainProgress: (Float) -> Unit,
        onSecondaryProgress: (Float) -> Unit,
        onComplete: () -> Unit
    ) {
        if (isInitialized) {
            onComplete()
            return
        }

        val prelaunchTasks: MutableList<BiConsumer<Context, (Float) -> Unit>> = ArrayList()
        prelaunchTasks.addAll(getPreLaunchTasks())
        prelaunchTasks.addAll(DatabaseMaintenance.getPreLaunchCleanupTasks())

        // Wait until pre-launch tasks are completed
        runPrelaunchTasks(
            context, prelaunchTasks, onMainProgress, onSecondaryProgress
        ) { postPrelaunch(context, onComplete) }
    }

    private fun postPrelaunch(
        context: Context, onComplete: () -> Unit
    ) {
        setInitialized()
        onComplete()
        // Run post-launch tasks on a worker
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniqueWork(
            R.id.startup_service.toString(),
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<StartupWorker>().build()
        )
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun runPrelaunchTasks(
        context: Context,
        tasks: List<BiConsumer<Context, (Float) -> Unit>>,
        onMainProgress: (Float) -> Unit,
        onSecondaryProgress: (Float) -> Unit,
        onComplete: () -> Unit
    ) {
        // Yes, we do need this to run on the GlobalScope
        GlobalScope.launch {
            tasks.forEachIndexed { index, task ->
                Timber.i("Pre-launch task %s/%s", index + 1, tasks.size)
                try {
                    withContext(Dispatchers.IO) {
                        task.invoke(context, onSecondaryProgress)
                        onMainProgress(index * 1f / tasks.size)
                    }
                } catch (e: Exception) {
                    Timber.w(e)
                }
            }
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    /**
     * Application initialization tasks
     * NB : Heavy operations; must be performed in the background to avoid ANR at startup
     */
    private fun getPreLaunchTasks(): List<BiConsumer<Context, (Float) -> Unit>> {
        return listOf(
            this::stopWorkers, this::processAppUpdate, this::loadSiteProperties, this::initUtils
        )
    }

    fun getPostLaunchTasks(): List<BiConsumer<Context, (Float) -> Unit>> {
        return listOf(
            this::searchForUpdates,
            this::sendFirebaseStats,
            this::clearPictureCache,
            this::createBookmarksJson,
            this::createPlugReceiver
        )
    }

    private fun stopWorkers(context: Context, emitter: (Float) -> Unit) {
        Timber.i("Stop workers : start")
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_CLOSEABLE)
        Timber.i("Stop workers : done")
    }

    private fun loadSiteProperties(context: Context, emitter: (Float) -> Unit) {
        try {
            context.resources.openRawResource(R.raw.sites).use { stream ->
                val siteSettingsStr = FileHelper.readStreamAsString(stream)
                val siteSettings = JsonHelper.jsonToObject(
                    siteSettingsStr, JsonSiteSettings::class.java
                )
                for ((key, value) in siteSettings.sites) {
                    for (site in Site.values()) {
                        if (site.name.equals(key, ignoreCase = true)) {
                            site.updateFrom(value!!)
                            break
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Timber.e(e)
        }
    }

    private fun initUtils(context: Context, emitter: (Float) -> Unit) {
        Timber.i("Init notifications : start")
        // Init notification channels
        StartupNotificationChannel.init(context)
        UpdateNotificationChannel.init(context)
        DownloadNotificationChannel.init(context)
        UserActionNotificationChannel.init(context)
        DeleteNotificationChannel.init(context)
        UpdateJsonNotificationChannel.init(context)
        TransformNotificationChannel.init(context)
        // Clears all previous notifications
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()
        Timber.i("Init notifications : done")
    }

    private fun processAppUpdate(context: Context, emitter: (Float) -> Unit) {
        Timber.i("Process app update : start")
        if (Preferences.getLastKnownAppVersionCode() < BuildConfig.VERSION_CODE) {
            Timber.d(
                "Process app update : update detected from %s to %s",
                Preferences.getLastKnownAppVersionCode(),
                BuildConfig.VERSION_CODE
            )
            Timber.d("Process app update : Clearing webview cache")
            context.clearWebviewCache(null)
            Timber.d("Process app update : Clearing app cache")
            context.clearAppCache()
            Timber.d("Process app update : Complete")
            EventBus.getDefault().postSticky(AppUpdatedEvent())
            Preferences.setLastKnownAppVersionCode(BuildConfig.VERSION_CODE)
        }
        Timber.i("Process app update : done")
    }

    private fun searchForUpdates(context: Context, emitter: (Float) -> Unit) {
        Timber.i("Run app update : start")
        if (Preferences.isAutomaticUpdateEnabled()) {
            Timber.i("Run app update : auto-check is enabled")
            val workManager = WorkManager.getInstance(context)
            workManager.enqueueUniqueWork(
                R.id.update_check_service.toString(),
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<UpdateCheckWorker>().build()
            )
        }
        Timber.i("Run app update : done")
    }

    private fun sendFirebaseStats(context: Context, emitter: (Float) -> Unit) {
        Timber.i("Send Firebase stats : start")
        try {
            FirebaseAnalytics.getInstance(context).setUserProperty(
                "color_theme", Preferences.getColorTheme().toString()
            )
            FirebaseAnalytics.getInstance(context).setUserProperty(
                "endless", Preferences.getEndlessScroll().toString()
            )
            FirebaseCrashlytics.getInstance().setCustomKey(
                "Library display mode", if (Preferences.getEndlessScroll()) "endless" else "paged"
            )
        } catch (e: IllegalStateException) { // Happens during unit tests
            Timber.e(e, "fail@init Crashlytics")
        }
        Timber.i("Send Firebase stats : done")
    }

    // Clear archive picture cache (useful when user kills the app while in background with the viewer open)
    private fun clearPictureCache(context: Context, emitter: (Float) -> Unit) {
        Timber.i("Clear picture cache : start")
        FileHelper.emptyCacheFolder(context, PICTURE_CACHE_FOLDER)
        Timber.i("Clear picture cache : done")
    }

    // Creates the JSON file for bookmarks if it doesn't exist
    private fun createBookmarksJson(context: Context, emitter: (Float) -> Unit) {
        Timber.i("Create bookmarks JSON : start")
        val appRoot = FileHelper.getDocumentFromTreeUriString(
            context, Preferences.getStorageUri(StorageLocation.PRIMARY_1)
        )
        if (appRoot != null) {
            val bookmarksJson = FileHelper.findFile(context, appRoot, BOOKMARKS_JSON_FILE_NAME)
            if (null == bookmarksJson) {
                Timber.i("Create bookmarks JSON : creating JSON")
                val dao: CollectionDAO = ObjectBoxDAO(context)
                try {
                    Helper.updateBookmarksJson(context, dao)
                } finally {
                    dao.cleanup()
                }
            } else {
                Timber.i("Create bookmarks JSON : already exists")
            }
        }
        Timber.i("Create bookmarks JSON : done")
    }

    private fun createPlugReceiver(context: Context, emitter: (Float) -> Unit) {
        Timber.i("Create plug receiver : start")
        val rcv = PlugEventsReceiver()
        val filter = IntentFilter()
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(Intent.ACTION_POWER_CONNECTED)
        filter.addAction(Intent.ACTION_HEADSET_PLUG)
        context.registerReceiver(rcv, filter)
        Timber.i("Create plug receiver : done")
    }
}
