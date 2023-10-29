package me.devsaki.hentoid.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.notification.update.UpdateFailedNotification;
import me.devsaki.hentoid.notification.update.UpdateInstallNotification;
import me.devsaki.hentoid.notification.update.UpdateProgressNotification;
import me.devsaki.hentoid.util.file.FileHelper;
import me.devsaki.hentoid.util.network.HttpHelper;
import me.devsaki.hentoid.util.notification.BaseNotification;
import me.devsaki.hentoid.workers.data.UpdateDownloadData;
import okhttp3.Response;
import okhttp3.ResponseBody;
import timber.log.Timber;


/**
 * Worker responsible for downloading app udate (APK)
 */
public class UpdateDownloadWorker extends BaseWorker {

    public UpdateDownloadWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters, R.id.update_download_service, null);
    }

    public static boolean isRunning(@NonNull Context context) {
        return isRunning(context, R.id.update_download_service);
    }

    @Override
    protected BaseNotification getStartNotification() {
        return new UpdateProgressNotification();
    }

    @Override
    protected void onInterrupt() {
        // Nothing
    }

    @Override
    protected void onClear() {
        // Nothing
    }

    @Override
    protected void getToWork(@NonNull Data input) {
        UpdateDownloadData.Parser data = new UpdateDownloadData.Parser(getInputData());
        String apkUrl = data.getUrl();

        try {
            downloadUpdate(apkUrl);
        } catch (IOException e) {
            Timber.w(e, "Update download failed");
            notificationManager.notifyLast(new UpdateFailedNotification(apkUrl));
        }
    }

    private void downloadUpdate(String apkUrl) throws IOException {
        Context context = getApplicationContext();
        Timber.w(context.getResources().getString(R.string.starting_download));

        File file = new File(context.getExternalCacheDir(), "hentoid.apk");
        file.createNewFile();

        Response response = HttpHelper.getOnlineResource(apkUrl, null, false, false, false);
        Timber.d("DOWNLOADING APK - RESPONSE %s", response.code());
        if (response.code() >= 300) throw new IOException("Network error " + response.code());

        ResponseBody body = response.body();
        if (null == body)
            throw new IOException("Could not read response : empty body for " + apkUrl);

        long size = body.contentLength();
        if (size < 1) size = 1;

        Timber.d("WRITING DOWNLOADED APK TO %s (size %.2f KB)", file.getAbsolutePath(), size / 1024.0);
        byte[] buffer = new byte[FileHelper.FILE_IO_BUFFER_SIZE];
        int len;
        long processed = 0;
        int iteration = 0;
        try (InputStream in = body.byteStream(); OutputStream out = FileHelper.getOutputStream(file)) {
            while ((len = in.read(buffer)) > -1) {
                processed += len;
                if (0 == ++iteration % 50) // Notify every 200KB
                    updateNotificationProgress(Math.round(processed * 100f / size));
                out.write(buffer, 0, len);
            }
            out.flush();
        }
        Timber.d("Download successful");

        notificationManager.notifyLast(new UpdateInstallNotification(FileHelper.getFileUriCompat(context, file)));
    }

    private void updateNotificationProgress(int processPc) {
        Timber.v("Download progress: %s%%", processPc);
        notificationManager.notify(new UpdateProgressNotification(processPc));
    }
}
