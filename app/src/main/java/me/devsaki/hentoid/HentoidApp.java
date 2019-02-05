package me.devsaki.hentoid;

import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Pair;

import com.crashlytics.android.Crashlytics;
import com.facebook.stetho.Stetho;
import com.google.android.gms.security.ProviderInstaller;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.squareup.leakcanary.LeakCanary;

import java.util.List;

import io.fabric.sdk.android.Fabric;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.notification.download.DownloadNotificationChannel;
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
    private static HentoidApp instance;

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

        // LeakCanary
        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return;
        }
        LeakCanary.install(this);

        // Timber
        if (BuildConfig.DEBUG) Timber.plant(new Timber.DebugTree());
        Timber.plant(new CrashlyticsTree());

        // Prefs
        instance = this;
        Preferences.init(this);

        // Firebase
        boolean isAnalyticsDisabled = Preferences.isAnalyticsDisabled();
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(!isAnalyticsDisabled);

        // Stetho
        if (BuildConfig.DEBUG) {
            Stetho.initializeWithDefaults(this);
        }

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // DB housekeeping
        performDatabaseHousekeeping();

        // Init notifications
        UpdateNotificationChannel.init(this);
        DownloadNotificationChannel.init(this);
        startService(UpdateCheckService.makeIntent(this, false));

        // Clears all previous notifications
        NotificationManager manager = (NotificationManager) instance.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancelAll();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            ShortcutHelper.buildShortcuts(this);
        }
    }

    /**
     * Clean up and upgrade database
     */
    private void performDatabaseHousekeeping() {
        HentoidDB db = HentoidDB.getInstance(this);
        Timber.d("Content item(s) count: %s", db.countContentEntries());

        // Perform technical data updates that need to be done before app launches
        UpgradeTo(BuildConfig.VERSION_CODE, db);

        // Launch a service that will perform non-structural DB housekeeping tasks
        Intent intent = DatabaseMaintenanceService.makeIntent(this);
        startService(intent);
    }

    /**
     * Handles complex DB version updates at startup
     *
     * @param versionCode Current app version
     * @param db          Hentoid DB
     */
    @SuppressWarnings("deprecation")
    private void UpgradeTo(int versionCode, HentoidDB db) {
        if (versionCode > 43) // Update all "storage_folder" fields in CONTENT table (mandatory)
        {
            List<Content> contents = db.selectContentEmptyFolder();
            if (contents != null && contents.size() > 0) {
                for (int i = 0; i < contents.size(); i++) {
                    Content content = contents.get(i);
                    content.setStorageFolder("/" + content.getSite().getDescription() + "/" + content.getOldUniqueSiteId()); // This line must use deprecated code, as it migrates it to newest version
                    db.updateContentStorageFolder(content);
                }
            }
        }
        if (versionCode > 59) // Migrate the old download queue (books in DOWNLOADING or PAUSED status) in the queue table
        {
            // Gets books that should be in the queue but aren't
            List<Integer> contentToMigrate = db.selectContentsForQueueMigration();

            if (contentToMigrate.size() > 0) {
                // Gets last index of the queue
                List<Pair<Integer, Integer>> queue = db.selectQueue();
                int lastIndex = 1;
                if (queue.size() > 0) {
                    lastIndex = queue.get(queue.size() - 1).second + 1;
                }

                for (int i : contentToMigrate) {
                    db.insertQueue(i, lastIndex++);
                }
            }
        }
    }
}
