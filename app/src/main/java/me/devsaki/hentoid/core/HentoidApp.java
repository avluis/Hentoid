package me.devsaki.hentoid.core;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;

import android.app.ActivityManager;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.jakewharton.threetenabp.AndroidThreeTen;

import org.threeten.bp.Instant;

import java.io.IOException;

import io.reactivex.exceptions.UndeliverableException;
import io.reactivex.plugins.RxJavaPlugins;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.SplashActivity;
import me.devsaki.hentoid.timber.CrashlyticsTree;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.network.HttpHelper;
import timber.log.Timber;

/**
 * Initializes required components:
 * Database, Bitmap Cache, Update checks, etc.
 */
public class HentoidApp extends Application {

    // == APP INSTANCE

    private static Application instance;

    public static synchronized Application getInstance() {
        return instance;
    }

    private static synchronized void setInstance(@NonNull Application value) {
        instance = value;
    }


    // == GLOBAL VARIABLES

    // When PIN lock is activated, indicates whether the app has been unlocked or not
    // NB : Using static members to be certain they won't be wiped out
    // when the app runs out of memory (can happen with singletons)
    private static boolean isUnlocked = false;
    private static long lockInstant = 0;

    public static boolean isUnlocked() {
        return isUnlocked;
    }

    public static void setUnlocked(boolean unlocked) {
        isUnlocked = unlocked;
    }

    public static void setLockInstant(long instant) {
        lockInstant = instant;
    }

    public static long getLockInstant() {
        return lockInstant;
    }


    public static void trackDownloadEvent(String tag) {
        Bundle bundle = new Bundle();
        bundle.putString("tag", tag);
        FirebaseAnalytics.getInstance(instance).logEvent("Download", bundle);
    }

    /**
     * Must only contain FUNDAMENTAL app init tasks, as the time spent here makes
     * the app unresponsive. The rest should be deferred to AppStartup
     */
    @Override
    public void onCreate() {
        super.onCreate();
        setInstance(this);

        Timber.i("Initializing %s", R.string.app_name);

        // Init datetime
        AndroidThreeTen.init(this);

        // Timber
        if (BuildConfig.DEBUG) Timber.plant(new Timber.DebugTree());
        Timber.plant(new CrashlyticsTree());

        // Prefs
        Preferences.init(this);
        Preferences.performHousekeeping();

        // Init version number
        if (0 == Preferences.getLastKnownAppVersionCode())
            Preferences.setLastKnownAppVersionCode(BuildConfig.VERSION_CODE);

        // Firebase
        boolean isAnalyticsEnabled = Preferences.isAnalyticsEnabled();
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(isAnalyticsEnabled);

        // Make sure the app restarts with the splash screen in case of any unhandled issue
        Thread.setDefaultUncaughtExceptionHandler(new EmergencyRestartHandler(this, SplashActivity.class));

        // Plug the lifecycle listener to handle locking
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new LifeCycleListener());

        // Set RxJava's default error handler for unprocessed network and IO errors
        RxJavaPlugins.setErrorHandler(e -> {
            if (e instanceof UndeliverableException) {
                e = e.getCause();
            }
            if (e instanceof IOException) {
                // fine, irrelevant network problem or API that throws on cancellation
                return;
            }
            if (e instanceof InterruptedException) {
                // fine, some blocking code was interrupted by a dispose call
                return;
            }
            Timber.w(e, "Undeliverable exception received, not sure what to do");
        });

        // Init user agents (must be done here as some users seem not to complete AppStartup properly)
        Timber.i("Init user agents : start");
        try {
            HttpHelper.initUserAgents(this);
            Timber.i("Init user agents : done");
        } catch (Exception MissingWebViewPackageException) {
            Timber.e("Failed to init user agents: No WebView installed");
        }
    }

    public static boolean isInForeground() {
        ActivityManager.RunningAppProcessInfo appProcessInfo = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(appProcessInfo);
        return (appProcessInfo.importance == IMPORTANCE_FOREGROUND || appProcessInfo.importance == IMPORTANCE_VISIBLE);
    }

    /**
     * Listener used to auto-lock the app when it goes to background
     * and the PIN lock is enabled
     */
    public static class LifeCycleListener implements DefaultLifecycleObserver, LifecycleObserver {

        private static boolean enabled = true;

        public static void enable() {
            enabled = true;
        }

        public static void disable() {
            enabled = false;
        }

        @Override
        public void onStop(@NonNull LifecycleOwner owner) {
            Timber.d("App moving to background");
            if (enabled && isUnlocked && !Preferences.getAppLockPin().isEmpty() && Preferences.isLockOnAppRestore()) {
                HentoidApp.setUnlocked(false);
                HentoidApp.setLockInstant(Instant.now().toEpochMilli());
            }
        }
    }
}
