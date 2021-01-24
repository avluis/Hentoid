package me.devsaki.hentoid;

import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.google.android.gms.security.ProviderInstaller;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.jakewharton.threetenabp.AndroidThreeTen;

import org.threeten.bp.Instant;

import java.io.IOException;

import io.reactivex.exceptions.UndeliverableException;
import io.reactivex.plugins.RxJavaPlugins;
import me.devsaki.hentoid.notification.download.DownloadNotificationChannel;
import me.devsaki.hentoid.notification.update.UpdateNotificationChannel;
import me.devsaki.hentoid.services.UpdateCheckService;
import me.devsaki.hentoid.timber.CrashlyticsTree;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.network.HttpHelper;
import timber.log.Timber;

/**
 * Created by DevSaki on 20/05/2015.
 * Initializes required components:
 * Database, Bitmap Cache, Update checks, etc.
 */
public class HentoidApp extends Application {

    // == APP INSTANCE

    private static Application instance;

    public static Application getInstance() {
        return instance;
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

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        Timber.i("Initializing %s", R.string.app_name);

        // Fix the SSLHandshake error with okhttp on Android 4.1-4.4 when server only supports TLS1.2
        // see https://github.com/square/okhttp/issues/2372 for more information
        // NB : Takes ~250ms at startup
        try {
            ProviderInstaller.installIfNeeded(getApplicationContext());
        } catch (Exception e) {
            Timber.e(e, "Google Play ProviderInstaller exception");
        }

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

        // Init HTTP user agents
        HttpHelper.initUserAgents(this);

        // Firebase
        boolean isAnalyticsEnabled = Preferences.isAnalyticsEnabled();
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(isAnalyticsEnabled);

        // Init notification channels
        UpdateNotificationChannel.init(this);
        DownloadNotificationChannel.init(this);

        // Clears all previous notifications
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancelAll();

        // Run app update checks
        if (Preferences.isAutomaticUpdateEnabled()) {
            Intent intent = UpdateCheckService.makeIntent(this, false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }

        // Send stats to Firebase
        FirebaseAnalytics.getInstance(this).setUserProperty("color_theme", Integer.toString(Preferences.getColorTheme()));
        FirebaseAnalytics.getInstance(this).setUserProperty("endless", Boolean.toString(Preferences.getEndlessScroll()));

        FirebaseCrashlytics.getInstance().setCustomKey("Library display mode", Preferences.getEndlessScroll() ? "endless" : "paged");

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
    }

    /**
     * Listener used to auto-lock the app when it goes to background
     * and the PIN lock is enabled
     */
    public static class LifeCycleListener implements LifecycleObserver {

        private static boolean enabled = true;

        public static void enable() {
            enabled = true;
        }

        public static void disable() {
            enabled = false;
        }


        @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
        private void onMoveToBackground() {
            Timber.d("Moving to background");
            if (enabled && !Preferences.getAppLockPin().isEmpty() && Preferences.isLockOnAppRestore()) {
                HentoidApp.setUnlocked(false);
                HentoidApp.setLockInstant(Instant.now().toEpochMilli());
            }
        }

    }
}
