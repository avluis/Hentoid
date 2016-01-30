package me.devsaki.hentoid;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.LruCache;
import android.widget.ImageView;

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
    private static HentoidApplication instance;
    private LruCache<String, Bitmap> mMemoryCache;
    private SharedPreferences sharedPreferences;

    public static Context getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        AndroidHelper.ignoreSslErrors();

        HentoidDB db = new HentoidDB(this);
        db.updateContentStatus(StatusContent.PAUSED, StatusContent.DOWNLOADING);

        // Get max available VM memory, exceeding this amount will throw an
        // OutOfMemory exception. Stored in kilobytes as LruCache takes an
        // int in its constructor.
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

        // Use 1/8th of the available memory for this memory cache.
        final int cacheSize = maxMemory / 8;

        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                // The cache size will be measured in kilobytes rather than
                // number of items.
                return bitmap.getByteCount() / 1024;
            }
        };

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

    private void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (key != null && bitmap != null) {
            if (getBitmapFromMemCache(key) == null) {
                mMemoryCache.put(key, bitmap);
            }
        }
    }

    private Bitmap getBitmapFromMemCache(String key) {
        return mMemoryCache.get(key);
    }

    public void loadBitmap(File file, ImageView mImageView) {
        final String imageKey = file.getAbsolutePath();

        final Bitmap bitmap = getBitmapFromMemCache(imageKey);
        if (bitmap != null) {
            mImageView.setImageBitmap(bitmap);
        } else {
            mImageView.setImageResource(R.drawable.ic_hentoid);
            BitmapWorkerTask task = new BitmapWorkerTask(mImageView);
            task.execute(file);
        }
    }

    class BitmapWorkerTask extends AsyncTask<File, Void, Bitmap> {

        private final ImageView imageView;

        public BitmapWorkerTask(ImageView imageView) {
            this.imageView = imageView;
        }

        // Decode image in background.
        @Override
        protected Bitmap doInBackground(File... params) {
            if (params[0].exists()) {
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

                Bitmap thumbBitmap = Helper.decodeSampledBitmapFromFile(
                        params[0].getAbsolutePath(), imageQuality.getWidth(),
                        imageQuality.getHeight());
                addBitmapToMemoryCache(params[0].getAbsolutePath(), thumbBitmap);
                return thumbBitmap;
            } else
                return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
            } else {
                imageView.setImageResource(R.drawable.ic_hentoid);
            }
        }
    }
}