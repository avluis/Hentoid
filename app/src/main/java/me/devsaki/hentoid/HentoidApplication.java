package me.devsaki.hentoid;

import android.app.Application;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.LruCache;
import android.widget.ImageView;

import me.devsaki.hentoid.asynctasks.UpdateCheckerTask;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.enums.Status;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.ConstantsPreferences;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.ImageQuality;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;
import org.acra.sender.HttpSender;

import java.io.File;

/**
 * Created by DevSaki on 20/05/2015.
 */
@ReportsCrashes(formUri = "http://acra.devsaki.me/acra-hentoid/_design/acra-storage/_update/report",
        httpMethod = HttpSender.Method.PUT,
        reportType = HttpSender.Type.JSON,
        formUriBasicAuthLogin = "reportuser",
        formUriBasicAuthPassword = "\\~hRcq#o?UVtT!7G",
        mode = ReportingInteractionMode.TOAST,
        resToastText = R.string.crash_app)
public class HentoidApplication extends Application {

    private static final String TAG = HentoidApplication.class.getName();
    private LruCache<String, Bitmap> mMemoryCache;
    private SharedPreferences sharedPreferences;

    @Override
    public void onCreate() {
        super.onCreate();

        ACRA.init(this);

        AndroidHelper.ignoreSslErros();

        HentoidDB db = new HentoidDB(this);
        db.updateContentStatus(Status.PAUSED, Status.DOWNLOADING);

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

        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if(sharedPreferences.getString(ConstantsPreferences.PREF_CHECK_UPDATES_LISTS, ConstantsPreferences.PREF_CHECK_UPDATES_DEFAULT+"").equals(ConstantsPreferences.PREF_CHECK_UPDATES_ENABLE+"")) {
            AndroidHelper.executeAsyncTask(new UpdateCheckerTask(this));
        }
    }


    public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (key != null && bitmap != null) {
            if (getBitmapFromMemCache(key) == null) {
                mMemoryCache.put(key, bitmap);
            }
        }
    }

    public Bitmap getBitmapFromMemCache(String key) {
        return mMemoryCache.get(key);
    }

    public void loadBitmap(File file, ImageView mImageView) {
        final String imageKey = file.getAbsolutePath();

        final Bitmap bitmap = getBitmapFromMemCache(imageKey);
        if (bitmap != null) {
            mImageView.setImageBitmap(bitmap);
        } else {
            mImageView.setImageResource(R.drawable.ic_fakkudroid_launcher);
            BitmapWorkerTask task = new BitmapWorkerTask(mImageView);
            task.execute(file);
        }
    }

    class BitmapWorkerTask extends AsyncTask<File, Void, Bitmap> {

        private ImageView imageView;

        public BitmapWorkerTask(ImageView imageView) {
            this.imageView = imageView;
        }

        // Decode image in background.
        @Override
        protected Bitmap doInBackground(File... params) {
            if (params[0].exists()) {
                String imageQualityPref = sharedPreferences.getString(ConstantsPreferences.PREF_QUALITY_IMAGE_LISTS, ConstantsPreferences.PREF_QUALITY_IMAGE_DEFAULT);
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
                imageView.setImageResource(R.drawable.ic_fakkudroid_launcher);
            }
        }
    }
}
