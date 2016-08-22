package me.devsaki.hentoid;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.StandardExceptionParser;
import com.google.android.gms.analytics.Tracker;

import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.updater.UpdateCheck;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by DevSaki on 20/05/2015.
 * Initializes required components:
 * Database, Bitmap Cache, Update checks, etc.
 * <p/>
 * TODO: Cache the number of items in db
 */
public class HentoidApp extends Application {
    private static final String TAG = LogHelper.makeLogTag(HentoidApp.class);

    private static boolean beginImport;
    private static boolean donePressed;
    private static HentoidApp instance;
    private static SharedPreferences sharedPrefs;
    private static Context context;
    private static int downloadCount = 0;

    // Only for use when activity context cannot be passed or used e.g.;
    // Notification resources, Analytics, etc.
    public static synchronized HentoidApp getInstance() {
        return instance;
    }

    public static SharedPreferences getSharedPrefs() {
        return sharedPrefs;
    }

    public static Context getAppContext() {
        return context;
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

    public static boolean hasImportStarted() {
        return beginImport;
    }

    public static void setBeginImport(boolean started) {
        HentoidApp.beginImport = started;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isDonePressed() {
        return donePressed;
    }

    public static void setDonePressed(boolean pressed) {
        HentoidApp.donePressed = pressed;
    }

    public void loadBitmap(String image, ImageView imageView) {
        // The following is needed due to RecyclerView recycling layouts and Glide not considering
        // the layout invalid for the current image:
        // https://github.com/bumptech/glide/issues/835#issuecomment-167438903
        imageView.layout(0, 0, 0, 0);

        Glide.with(this)
                .load(image)
                .fitCenter()
                .crossFade()
                .placeholder(R.drawable.ic_placeholder)
                .error(R.drawable.ic_placeholder)
                .diskCacheStrategy(DiskCacheStrategy.RESULT)
                .into(imageView);
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

        HentoidApp.context = getApplicationContext();
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        instance = this;

        AnalyticsTrackers.initialize(this);
        AnalyticsTrackers.getInstance().get(AnalyticsTrackers.Target.APP);

        Helper.queryPrefsKey(this);
        Helper.ignoreSslErrors();

        HentoidDB db = HentoidDB.getInstance(this);
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
