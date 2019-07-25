package me.devsaki.hentoid;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;

import androidx.appcompat.app.AppCompatDelegate;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.security.ProviderInstaller;
import com.google.firebase.analytics.FirebaseAnalytics;

import io.fabric.sdk.android.Fabric;
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
import timber.log.Timber;

/**
 * Created by DevSaki on 20/05/2015.
 * Initializes required components:
 * Database, Bitmap Cache, Update checks, etc.
 */
public class HentoidApp extends Application {

    private static boolean beginImport;
    @SuppressLint("StaticFieldLeak")
    // A context leak happening at app level isn't _really_ a leak, right ? ;-)
    private static Context instance;

    public static Context getAppContext() {
        return instance;
    }

    public static boolean isImportComplete() {
        return !beginImport;
    }

    public static void setBeginImport(boolean started) {
        HentoidApp.beginImport = started;
    }


    public static void trackDownloadEvent(String tag) {
        Bundle bundle = new Bundle();
        bundle.putString("tag", tag);
        FirebaseAnalytics.getInstance(instance).logEvent("Download", bundle);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Fabric.with(this, new Crashlytics());

        // Fix the SSLHandshake error with okhttp on Android 4.1-4.4 when server only supports TLS1.2
        // see https://github.com/square/okhttp/issues/2372 for more information
        try {
            ProviderInstaller.installIfNeeded(getApplicationContext());
        } catch (Exception e) {
            Timber.e(e, "Google Play ProviderInstaller exception");
        }

/*
        // LeakCanary
        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return;
        }
        LeakCanary.install(this);
*/

        // Timber
        if (BuildConfig.DEBUG) Timber.plant(new Timber.DebugTree());
        Timber.plant(new CrashlyticsTree());

        // Prefs
        instance = this.getApplicationContext();
        Preferences.init(this);

        // Firebase
        boolean isAnalyticsDisabled = Preferences.isAnalyticsDisabled();
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(!isAnalyticsDisabled);

        // Stetho
/*
        if (BuildConfig.DEBUG) {
            Stetho.initializeWithDefaults(this);
        }
*/

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // DB housekeeping
        performDatabaseHousekeeping();

        // Init notification channels
        UpdateNotificationChannel.init(this);
        DownloadNotificationChannel.init(this);
        MaintenanceNotificationChannel.init(this);

        // Clears all previous notifications
        NotificationManager manager = (NotificationManager) instance.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancelAll();

        // Run app update checks
        Intent intent = UpdateCheckService.makeIntent(this, false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            ShortcutHelper.buildShortcuts(this);
        }

        // Set Night mode
        int darkMode = Preferences.getDarkMode();
        AppCompatDelegate.setDefaultNightMode(darkModeFromPrefs(darkMode));
        FirebaseAnalytics.getInstance(this).setUserProperty("night_mode", Integer.toString(darkMode));
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

    public static int darkModeFromPrefs(int prefsMode) {
        switch (prefsMode) {
            case Preferences.Constant.DARK_MODE_ON:
                return AppCompatDelegate.MODE_NIGHT_YES;
            case Preferences.Constant.DARK_MODE_OFF:
                return AppCompatDelegate.MODE_NIGHT_NO;
            case Preferences.Constant.DARK_MODE_BATTERY:
                return AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY;
            default:
                return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
    }
}
