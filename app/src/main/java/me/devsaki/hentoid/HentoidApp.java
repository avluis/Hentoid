package me.devsaki.hentoid;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.StandardExceptionParser;
import com.google.android.gms.analytics.Tracker;

import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.updater.UpdateCheck;
import me.devsaki.hentoid.util.ConstsPrefs;
import me.devsaki.hentoid.util.Helper;
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
    private static int downloadCount = 0;
    private static HentoidApp instance;
    private static SharedPreferences sharedPrefs;

    // Only for use when activity context cannot be passed or used e.g.;
    // Notification resources, Analytics, etc.
    public static synchronized HentoidApp getInstance() {
        return instance;
    }

    public static SharedPreferences getSharedPrefs() {
        return sharedPrefs;
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
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // When dry run is set, hits will not be dispatched,
        // but will still be logged as though they were dispatched.
        GoogleAnalytics.getInstance(this).setDryRun(BuildConfig.DEBUG);

        // Analytics Opt-Out
        GoogleAnalytics.getInstance(this).setAppOptOut(sharedPrefs.getBoolean(
                ConstsPrefs.PREF_ANALYTICS_TRACKING, false));
        sharedPrefs.registerOnSharedPreferenceChangeListener((sp, key) -> {
            if (key.equals(ConstsPrefs.PREF_ANALYTICS_TRACKING)) {
                GoogleAnalytics.getInstance(getAppContext()).setAppOptOut(
                        sp.getBoolean(key, false));
            }
        });

        Helper.queryPrefsKey(this);
        Helper.ignoreSslErrors();

        HentoidDB db = HentoidDB.getInstance(this);
        Timber.d("Content item(s) count: %s", db.getContentCount());
        db.updateContentStatus(StatusContent.PAUSED, StatusContent.DOWNLOADING);

        UpdateCheck(!Helper.getMobileUpdatePrefs());

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
}
