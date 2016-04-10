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
import me.devsaki.hentoid.enums.ImageQuality;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.updater.UpdateCheck;
import me.devsaki.hentoid.updater.UpdateCheck.UpdateCheckCallback;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.ConstantsPreferences;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by DevSaki on 20/05/2015.
 * Initializes required components:
 * Database, Bitmap Cache, Update checks, etc.
 */
public class HentoidApplication extends Application {
    public static final String DOWNLOAD_COUNT = "DOWNLOAD_COUNT";

    private static final String TAG = LogHelper.makeLogTag(HentoidApplication.class);

    private static HentoidApplication mInstance;
    private static SharedPreferences sharedPreferences;
    private static Context context;
    private static int downloadCount = 0;

    public static synchronized HentoidApplication getInstance() {
        return mInstance;
    }

    public static SharedPreferences getAppPreferences() {
        return sharedPreferences;
    }

    public static Context getAppContext() {
        return context;
    }

    public static int getDownloadCount() {
        return downloadCount;
    }

    public static void setDownloadCount(int downloadCount) {
        HentoidApplication.downloadCount = downloadCount;
    }

    public synchronized Tracker getGoogleAnalyticsTracker() {
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

        HentoidApplication.context = getApplicationContext();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mInstance = this;

        AnalyticsTrackers.initialize(this);
        AnalyticsTrackers.getInstance().get(AnalyticsTrackers.Target.APP);

        AndroidHelper.queryPrefsKey(getAppContext());
        AndroidHelper.ignoreSslErrors();

        HentoidDB db = new HentoidDB(this);
        db.updateContentStatus(StatusContent.PAUSED, StatusContent.DOWNLOADING);

        if (AndroidHelper.getMobileUpdatePrefs()) {
            LogHelper.d(TAG, "Mobile Updates: ON");
            UpdateCheck(false);
        } else {
            LogHelper.d(TAG, "Mobile Updates; OFF");
            UpdateCheck(true);
        }
    }

    private void UpdateCheck(boolean onlyWifi) {
        UpdateCheck.getInstance().checkForUpdate(getAppContext(),
                onlyWifi, false, new UpdateCheckCallback() {
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

    public void loadBitmap(String image, ImageView mImageView) {
        String imageQualityPref = sharedPreferences.getString(
                ConstantsPreferences.PREF_QUALITY_IMAGE_LISTS,
                ConstantsPreferences.PREF_QUALITY_IMAGE_DEFAULT);

        ImageQuality imageQuality;
        switch (imageQualityPref) {
            case "Low":
                imageQuality = ImageQuality.LOW;
                break;
            case "High":
                imageQuality = ImageQuality.HIGH;
                break;
            case "Medium":
            default:
                imageQuality = ImageQuality.MEDIUM;
                break;
        }

        Glide.with(this)
                .load(image)
                .override(imageQuality.getWidth(), imageQuality.getHeight())
                .diskCacheStrategy(DiskCacheStrategy.RESULT)
                .into(mImageView);
    }
}