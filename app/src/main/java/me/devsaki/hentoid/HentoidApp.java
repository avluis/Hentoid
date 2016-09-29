package me.devsaki.hentoid;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.StandardExceptionParser;
import com.google.android.gms.analytics.Tracker;

import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.updater.UpdateCheck;
import me.devsaki.hentoid.util.ConstsPrefs;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by DevSaki on 20/05/2015.
 * Initializes required components:
 * Database, Bitmap Cache, Update checks, etc.
 */
public class HentoidApp extends Application {
    private static final String TAG = LogHelper.makeLogTag(HentoidApp.class);

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
        AnalyticsTrackers trackers = AnalyticsTrackers.getInstance();
        return trackers.get(AnalyticsTrackers.Target.APP);
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
     *
     * @param e exception to be tracked
     */
    public void trackException(Exception e) {
        if (e != null) {
            Tracker tracker = getGoogleAnalyticsTracker();

            tracker.send(new HitBuilders.ExceptionBuilder()
                    .setDescription(new StandardExceptionParser(this, null)
                            .getDescription(Thread.currentThread().getName(), e))
                    .setFatal(false)
                    .build()
            );
        }
    }

    /***
     * Tracking event
     *
     * @param category event category
     * @param action   action of the event
     * @param label    label
     */
    public void trackEvent(String category, String action, String label) {
        Tracker tracker = getGoogleAnalyticsTracker();

        // Build and send an Event.
        tracker.send(new HitBuilders.EventBuilder().setCategory(category).setAction(action)
                .setLabel(label).build());
    }

    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        AnalyticsTrackers.initialize(this);
        AnalyticsTrackers.getInstance().get(AnalyticsTrackers.Target.APP);

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
        LogHelper.d(TAG, "Content item(s) count: " + db.getContentCount());
        db.updateContentStatus(StatusContent.PAUSED, StatusContent.DOWNLOADING);

        UpdateCheck(!Helper.getMobileUpdatePrefs());
    }

    private void UpdateCheck(boolean onlyWifi) {
        UpdateCheck.getInstance().checkForUpdate(this,
                onlyWifi, false, new UpdateCheck.UpdateCheckCallback() {
                    @Override
                    public void noUpdateAvailable() {
                        LogHelper.d(TAG, "Update Check: No update.");
                    }

                    @Override
                    public void onUpdateAvailable() {
                        LogHelper.d(TAG, "Update Check: Update!");
                    }
                });
    }
}
