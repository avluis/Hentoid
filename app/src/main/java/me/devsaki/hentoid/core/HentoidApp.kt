package me.devsaki.hentoid.core

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.analytics.FirebaseAnalytics
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.SplashActivity
import me.devsaki.hentoid.receiver.WebViewUpdateCycleReceiver
import me.devsaki.hentoid.timber.CrashlyticsTree
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.network.WebkitPackageHelper
import me.devsaki.hentoid.util.network.initUserAgents
import timber.log.Timber
import java.time.Instant

/**
 * Initializes required components:
 * Database, Bitmap Cache, Update checks, etc.
 */
class HentoidApp : Application() {
    private var webViewUpdateCycleReceiver = WebViewUpdateCycleReceiver()

    /**
     * Must only contain FUNDAMENTAL app init tasks, as the time spent here makes
     * the app unresponsive. The rest should be deferred to AppStartup
     */
    override fun onCreate() {
        super.onCreate()
        setInstance(this)
        Timber.i("Initializing %s", R.string.app_name)

        // Timber
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        Timber.plant(CrashlyticsTree())

        // Prefs
        Preferences.init(this)
        Settings.init(this)
        Preferences.performHousekeeping()

        // Init version number
        if (0 == Preferences.getLastKnownAppVersionCode()) Preferences.setLastKnownAppVersionCode(
            BuildConfig.VERSION_CODE
        )

        // Firebase
        val isAnalyticsEnabled = Settings.isAnalyticsEnabled
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(isAnalyticsEnabled)

        // Make sure the app restarts with the splash screen in case of any unhandled issue
        Thread.setDefaultUncaughtExceptionHandler(
            EmergencyRestartHandler(
                this,
                SplashActivity::class.java
            )
        )

        // Plug the lifecycle listener to handle locking
        ProcessLifecycleOwner.get().lifecycle.addObserver(LifeCycleListener())

        // Initialize WebView availability status and register the WebView Update Cycle Receiver
        WebkitPackageHelper.setWebViewAvailable()
        val filterWVUC = IntentFilter(Intent.ACTION_PACKAGE_ADDED)
        filterWVUC.addAction(Intent.ACTION_PACKAGE_REMOVED)
        filterWVUC.addAction(Intent.ACTION_PACKAGE_REPLACED)
        registerReceiver(webViewUpdateCycleReceiver, filterWVUC)

        // Init user agents (must be done here as some users seem not to complete AppStartup properly)
        Timber.i("Init user agents : start")
        if (WebkitPackageHelper.getWebViewAvailable()) {
            initUserAgents(this)
            Timber.i("Init user agents : done")
        } else Timber.w("Failed to init user agents: WebView is unavailable")
    }

    companion object {
        private lateinit var instance: Application

        @Synchronized
        fun getInstance(): Application {
            return instance
        }

        @Synchronized
        private fun setInstance(value: Application) {
            instance = value
        }

        fun trackDownloadEvent(tag: String?) {
            val bundle = Bundle()
            bundle.putString("tag", tag)
            FirebaseAnalytics.getInstance(instance).logEvent("Download", bundle)
        }

        fun isInForeground(): Boolean {
            val appProcessInfo = RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(appProcessInfo)
            return appProcessInfo.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND || appProcessInfo.importance == RunningAppProcessInfo.IMPORTANCE_VISIBLE
        }

        // When PIN lock is activated, indicates whether the app has been unlocked or not
        // NB : Using static members to be certain they won't be wiped out
        // when the app runs out of memory (can happen with singletons)
        private var isUnlocked = false
        private var lockInstant: Long = 0

        fun isUnlocked(): Boolean {
            return isUnlocked
        }

        fun setUnlocked(unlocked: Boolean) {
            isUnlocked = unlocked
        }

        fun setLockInstant(instant: Long) {
            lockInstant = instant
        }

        fun getLockInstant(): Long {
            return lockInstant
        }
    }

    /**
     * Listener used to auto-lock the app when it goes to background
     * and the PIN lock is enabled
     */
    class LifeCycleListener : DefaultLifecycleObserver, LifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            Timber.d("App moving to background")
            if (enabled && isUnlocked && Settings.lockType > 0 && Preferences.isLockOnAppRestore()
            ) {
                setUnlocked(false)
                setLockInstant(Instant.now().toEpochMilli())
            }
        }

        companion object {
            private var enabled = true
            fun enable() {
                enabled = true
            }

            fun disable() {
                enabled = false
            }
        }
    }
}