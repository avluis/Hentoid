package me.devsaki.hentoid;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.facebook.stetho.Stetho;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.StandardExceptionParser;
import com.google.android.gms.analytics.Tracker;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;

import java.util.List;

import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.updater.UpdateCheck;
import me.devsaki.hentoid.util.Helper;
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
    private static boolean donePressed;
    private static int downloadCount = 0; // Used to store the number of downloads completed during current session in order to display notifications correctly ("download completed" vs. "N downloads completed")
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

    public static int getDownloadCount() {
        return downloadCount;
    }

    public static void setDownloadCount(int downloadCount) {
        HentoidApp.downloadCount = downloadCount;
    }

    /*
    Signals a new completed download
     */
    public static void downloadComplete() {
        HentoidApp.downloadCount++;
    }

    public static boolean isImportComplete() {
        return !beginImport;
    }

    public static void setBeginImport(boolean started) {
        HentoidApp.beginImport = started;
    }

    public static boolean isDonePressed() {
        return donePressed;
    }

    public static void setDonePressed(boolean pressed) {
        HentoidApp.donePressed = pressed;
    }

    public static RefWatcher getRefWatcher(Context context) {
        HentoidApp app = (HentoidApp) context.getApplicationContext();
        return app.refWatcher;
    }

    private synchronized Tracker getGoogleAnalyticsTracker() {
        return GoogleAnalytics.getInstance(this).newTracker(R.xml.app_tracker);
    }

    /***
     * Tracking screen view
     *
     * @param screenName screen name to be displayed on GA dashboard
     */
    public void trackScreenView(String screenName) {
        Tracker tracker = getGoogleAnalyticsTracker();

        // Set screen name.
        tracker.setScreenName(screenName);

        // Send a screen view.
        tracker.send(new HitBuilders.ScreenViewBuilder().build());

        GoogleAnalytics.getInstance(this).dispatchLocalHits();
    }

    /***
     * Tracking exception
     * Note: Timber will track exceptions as well,
     * so no need to call if making use of Timber with a throwable.
     *
     * @param e exception to be tracked
     */
    public void trackException(Exception e) {
        if (e != null) {
            getGoogleAnalyticsTracker().send(
                    new HitBuilders.ExceptionBuilder()
                            .setDescription(
                                    new StandardExceptionParser(this, null)
                                            .getDescription(Thread.currentThread().getName(), e)
                            )
                            .setFatal(false)
                            .build()
            );
        }
    }

    /***
     * Tracking event
     *
     * @param clazz  event category based on class name
     * @param action action of the event
     * @param label  label
     */
    public void trackEvent(Class clazz, String action, String label) {
        // Build and send an Event.
        getGoogleAnalyticsTracker().send(
                new HitBuilders.EventBuilder()
                        .setCategory(clazz.getSimpleName())
                        .setAction(action)
                        .setLabel(label)
                        .build()
        );
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
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        } else {
            Timber.plant(new Timber.Tree() {
                @Override
                protected void log(int priority, String tag, String message, Throwable t) {
                    if (priority >= Log.INFO && t != null) {
                        trackException((Exception) t);
                    }
                }
            });
        }

        instance = this;
        Preferences.init(this);

        // When dry run is set, hits will not be dispatched,
        // but will still be logged as though they were dispatched.
        GoogleAnalytics.getInstance(this).setDryRun(BuildConfig.DEBUG);

        // Analytics Opt-Out
        boolean isAnalyticsDisabled = Preferences.isAnalyticsDisabled();
        GoogleAnalytics.getInstance(this).setAppOptOut(isAnalyticsDisabled);

        if (BuildConfig.DEBUG) {
            // Stetho init
            Stetho.initializeWithDefaults(this);
        }

        Helper.ignoreSslErrors();

        HentoidDB db = HentoidDB.getInstance(this);
        Timber.d("Content item(s) count: %s", db.getContentCount());
        db.updateContentStatus(StatusContent.PAUSED, StatusContent.DOWNLOADING);
        try {
            UpgradeTo(Helper.getAppVersionCode(this), db);
        } catch (PackageManager.NameNotFoundException e) {
            Timber.d("Package Name NOT Found");
        }

        UpdateCheck(!Preferences.getMobileUpdate());

        if (Helper.isAtLeastAPI(Build.VERSION_CODES.N_MR1)) {
            ShortcutHelper.buildShortcuts(this);
        }
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
     * @param db Hentoid DB
     */
    private void UpgradeTo(int versionCode, HentoidDB db) {
        if (versionCode > 43) // Update all "storage_folder" fields in CONTENT table (mandatory)
        {
            List<Content> contents = db.selectContentEmptyFolder();
            if (contents != null && contents.size() > 0) {
                for (int i = 0; i < contents.size(); i++) {
                    Content content = contents.get(i);
                    content.setStorageFolder("/" + content.getSite().getDescription() + "/" + content.getOldUniqueSiteId());
                    db.updateContentStorageFolder(content);
                }
            }
        }
    }
}
