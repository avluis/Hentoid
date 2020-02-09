package me.devsaki.hentoid;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.security.ProviderInstaller;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.jakewharton.threetenabp.AndroidThreeTen;

import org.threeten.bp.Instant;

import io.fabric.sdk.android.Fabric;
import me.devsaki.hentoid.activities.IntroActivity;
import me.devsaki.hentoid.database.DatabaseMaintenance;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.notification.download.DownloadNotificationChannel;
import me.devsaki.hentoid.notification.maintenance.MaintenanceNotificationChannel;
import me.devsaki.hentoid.notification.update.UpdateNotificationChannel;
import me.devsaki.hentoid.services.DatabaseMaintenanceService;
import me.devsaki.hentoid.services.UpdateCheckService;
import me.devsaki.hentoid.timber.CrashlyticsTree;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ShortcutHelper;
import me.devsaki.hentoid.util.ToastUtil;
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

        Fabric.with(this, new Crashlytics());

        // Fix the SSLHandshake error with okhttp on Android 4.1-4.4 when server only supports TLS1.2
        // see https://github.com/square/okhttp/issues/2372 for more information
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

        // Init version number on first run
        if (0 == Preferences.getLastKnownAppVersionCode())
            Preferences.setLastKnownAppVersionCode(BuildConfig.VERSION_CODE);

        // Firebase
        boolean isAnalyticsEnabled = Preferences.isAnalyticsEnabled();
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(isAnalyticsEnabled);

        // This code has been inherited from the FakkuDroid era; no documentation available
        // Best guess : allows networking on main thread
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // DB housekeeping
        performDatabaseHousekeeping();

        // Init notification channels
        UpdateNotificationChannel.init(this);
        DownloadNotificationChannel.init(this);
        MaintenanceNotificationChannel.init(this);

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            ShortcutHelper.buildShortcuts(this);
        }

        FirebaseAnalytics.getInstance(this).setUserProperty("color_theme", Integer.toString(Preferences.getColorTheme()));
        FirebaseAnalytics.getInstance(this).setUserProperty("endless", Boolean.toString(Preferences.getEndlessScroll()));

        ProcessLifecycleOwner.get().getLifecycle().addObserver(new LifeCycleListener());
    }

    // We have asked for permissions, but still denied.
    public static void reset(Activity activity) {
        ToastUtil.toast(R.string.reset);
        Preferences.setIsFirstRun(true);
        Intent intent = new Intent(activity, IntroActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        instance.startActivity(intent);
        activity.finish();
    }

    /**
     * Clean up and upgrade database
     */
    @SuppressWarnings({"deprecation", "squid:CallToDeprecatedMethod"})
    private void performDatabaseHousekeeping() {
        HentoidDB oldDB = HentoidDB.getInstance(this);

        // Perform technical data updates that need to be done before app launches
        DatabaseMaintenance.performOldDatabaseUpdate(oldDB);

        // Launch a service that will perform non-structural DB housekeeping tasks
        Intent intent = DatabaseMaintenanceService.makeIntent(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
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
