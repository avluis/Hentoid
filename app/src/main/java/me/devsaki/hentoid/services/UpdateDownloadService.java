package me.devsaki.hentoid.services;

import static java.util.Objects.requireNonNull;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.thin.downloadmanager.ThinDownloadManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.notification.update.UpdateFailedNotification;
import me.devsaki.hentoid.notification.update.UpdateInstallNotification;
import me.devsaki.hentoid.notification.update.UpdateProgressNotification;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.network.HttpHelper;
import me.devsaki.hentoid.util.notification.ServiceNotificationManager;
import okhttp3.Response;
import okhttp3.ResponseBody;
import timber.log.Timber;

/**
 * Service responsible for downloading an update APK.
 *
 * @see UpdateCheckService
 */
public class UpdateDownloadService extends Service {

    private static final int NOTIFICATION_ID = UpdateDownloadService.class.getName().hashCode();

    private static boolean running;

    public static Intent makeIntent(Context context, String updateUrl) {
        Intent intent = new Intent(context, UpdateDownloadService.class);
        intent.setData(Uri.parse(updateUrl));
        return intent;
    }

    public static boolean isRunning() {
        return running;
    }

    private ThinDownloadManager downloadManager;

    private ServiceNotificationManager notificationManager;


    @Override
    public void onCreate() {
        running = true;
        downloadManager = new ThinDownloadManager();

        notificationManager = new ServiceNotificationManager(this, NOTIFICATION_ID);
        notificationManager.startForeground(new UpdateProgressNotification());

        Timber.w("Service created");
    }

    @Override
    public void onDestroy() {
        running = false;
        downloadManager.release();
        Timber.w("Service destroyed");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Uri updateUri = requireNonNull(intent.getData());
        try {
            downloadUpdate(updateUri);
        } catch (IOException e) {
            Timber.w(e, "Update download failed");
            notificationManager.notify(new UpdateFailedNotification(updateUri.toString()));
        }
        return START_NOT_STICKY;
    }

    private void downloadUpdate(Uri updateUri) throws IOException {
        Timber.w(this.getResources().getString(R.string.starting_download));

        File file = new File(getExternalCacheDir(), "hentoid.apk");
        if (!file.createNewFile())
            Timber.w("Could not create file %s", file.getPath());


        Response response = HttpHelper.getOnlineResource(updateUri.toString(), null, false, false, false);
        Timber.d("DOWNLOADING APK - RESPONSE %s", response.code());
        if (response.code() >= 300) throw new IOException("Network error " + response.code());

        ResponseBody body = response.body();
        if (null == body)
            throw new IOException("Could not read response : empty body for " + updateUri.toString());

        long size = body.contentLength();
        if (size < 1) size = 1;

        Timber.d("WRITING DOWNLOADED APK TO %s (size %.2f KB)", file.getAbsolutePath(), size / 1024.0);
        byte[] buffer = new byte[4196];
        int len;
        long processed = 0;
        int iteration = 0;
        try (InputStream in = body.byteStream(); OutputStream out = FileHelper.getOutputStream(file)) {
            while ((len = in.read(buffer)) > -1) {
                processed += len;
                // Read mime-type on the fly
                if (0 == ++iteration % 50) // Notify every 200KB
                    updateNotificationProgress(Math.round(processed * 100f / size));
                out.write(buffer, 0, len);
            }
            out.flush();
        }
        notificationManager.notify(new UpdateInstallNotification(FileHelper.getFileUri(this, file)));
    }

    private void updateNotificationProgress(int processPc) {
        Timber.w("Download progress: %s%%", processPc);
        notificationManager.notify(new UpdateProgressNotification(processPc));
    }
}
