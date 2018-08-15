package me.devsaki.hentoid;

import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Pair;

import com.facebook.stetho.Stetho;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;

import java.util.Date;
import java.util.List;

import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.timber.CrashlyticsTree;
import me.devsaki.hentoid.updater.UpdateCheck;
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
    private static Date lastCollectionRefresh;
    private static HentoidApp instance;
    private RefWatcher refWatcher;

    // Only for use when activity context cannot be passed or used e.g.;
    // Notification resources, Analytics, etc.
    public static synchronized HentoidApp getInstance() {
        return instance;
    }

    public static Context getAppContext() {
        return instance.getApplicationContext();
    }

    public static boolean isImportComplete() {
        return !beginImport;
    }

    public static void setBeginImport(boolean started) {
        HentoidApp.beginImport = started;
    }

    public static Date getLastCollectionRefresh() { return lastCollectionRefresh; }

    public static void resetLastCollectionRefresh() { lastCollectionRefresh = new Date(); }


    public static RefWatcher getRefWatcher(Context context) {
        HentoidApp app = (HentoidApp) context.getApplicationContext();
        return app.refWatcher;
    }

    public static void trackDownloadEvent(String tag) {
        Bundle bundle = new Bundle();
        bundle.putString("tag", tag);
        FirebaseAnalytics.getInstance(instance).logEvent("Download", bundle);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // LeakCanary
        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return;
        }
        refWatcher = LeakCanary.install(this);

        // Timber
        if (BuildConfig.DEBUG) Timber.plant(new Timber.DebugTree());
        Timber.plant(new CrashlyticsTree());

        instance = this;
        Preferences.init(this);

        boolean isAnalyticsDisabled = Preferences.isAnalyticsDisabled();
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(!isAnalyticsDisabled);

        if (BuildConfig.DEBUG) {
            Stetho.initializeWithDefaults(this);
        }

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        HentoidDB db = HentoidDB.getInstance(this);
        Timber.d("Content item(s) count: %s", db.countContent());
        db.updateContentStatus(StatusContent.DOWNLOADING, StatusContent.PAUSED);
        UpgradeTo(BuildConfig.VERSION_CODE, db);

        UpdateCheck(!Preferences.getMobileUpdate());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            ShortcutHelper.buildShortcuts(this);
        }

        // Clears all previous notifications
        NotificationManager manager = (NotificationManager) instance.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancelAll();

        resetLastCollectionRefresh();
    }

    private void UpdateCheck(boolean onlyWifi) {
        UpdateCheck.getInstance().checkForUpdate(this,
                onlyWifi, false, new UpdateCheck.UpdateCheckCallback() {
                    @Override
                    public void noUpdateAvailable() {
                        Timber.d("Update Check: No update.");
                    }

                    @Override
                    public void onUpdateAvailable() {
                        Timber.d("Update Check: Update!");
                    }
                });
    }

    /**
     * Handles complex DB version updates at startup
     *
     * @param versionCode Current app version
     * @param db          Hentoid DB
     */
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
