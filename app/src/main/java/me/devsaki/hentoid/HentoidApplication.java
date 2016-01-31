package me.devsaki.hentoid;

import android.app.Application;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.LruCache;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

import java.io.File;

import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.enums.StatusContent;
import me.devsaki.hentoid.updater.UpdateCheck;
import me.devsaki.hentoid.updater.UpdateCheck.UpdateCheckCallback;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.ConstantsPreferences;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.ImageQuality;

/**
 * Created by DevSaki on 20/05/2015.
 */
public class HentoidApplication extends Application {

    private static final String TAG = HentoidApplication.class.getName();
    private SharedPreferences sharedPreferences;

    @Override
    public void onCreate() {
        super.onCreate();

        AndroidHelper.ignoreSslErrors();

        HentoidDB db = new HentoidDB(this);
        db.updateContentStatus(StatusContent.PAUSED, StatusContent.DOWNLOADING);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        if (sharedPreferences.getString(
                ConstantsPreferences.PREF_CHECK_UPDATES_LISTS,
                ConstantsPreferences.PREF_CHECK_UPDATES_DEFAULT + "").equals(
                ConstantsPreferences.PREF_CHECK_UPDATES_ENABLE + "")) {
            UpdateCheck(false);
        } else {
            UpdateCheck(true);
        }
    }

    private void UpdateCheck(boolean onlyWifi) {
        UpdateCheck.getInstance().checkForUpdate(getApplicationContext(),
                onlyWifi, false, new UpdateCheckCallback() {
                    @Override
                    public void noUpdateAvailable() {
                        System.out.println("Auto update check: No update available.");
                    }

                    @Override
                    public void onUpdateAvailable() {
                        System.out.println("Auto update check: Update available!");
                    }
                });
    }

    public void loadBitmap(String image, ImageView mImageView) {
        String imageQualityPref = sharedPreferences.getString(
                ConstantsPreferences.PREF_QUALITY_IMAGE_LISTS,
                ConstantsPreferences.PREF_QUALITY_IMAGE_DEFAULT);

        ImageQuality imageQuality = ImageQuality.LOW;
        switch (imageQualityPref) {
            case "Medium":
                imageQuality = ImageQuality.MEDIUM;
                break;
            case "High":
                imageQuality = ImageQuality.HIGH;
                break;
            case "Low":
                imageQuality = ImageQuality.LOW;
                break;
        }

        Glide.with(this).load(image).override(imageQuality.getWidth(), imageQuality.getHeight()).into(mImageView);
    }
}