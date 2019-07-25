package me.devsaki.hentoid.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;

import io.reactivex.disposables.Disposable;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.events.UpdateEvent;
import me.devsaki.hentoid.model.UpdateInfoJson;
import me.devsaki.hentoid.notification.update.UpdateAvailableNotification;
import me.devsaki.hentoid.notification.update.UpdateCheckNotification;
import me.devsaki.hentoid.retrofit.UpdateServer;
import me.devsaki.hentoid.util.notification.ServiceNotificationManager;
import timber.log.Timber;

import static android.widget.Toast.LENGTH_SHORT;
import static io.reactivex.android.schedulers.AndroidSchedulers.mainThread;

/**
 * Service responsible for checking for updates
 * <p>
 * Can be run silently or with toast notifications using "isManualCheck" flag.
 * <p>
 * Make sure to check if {@link UpdateDownloadService} is running before running this service by
 * calling {@link UpdateDownloadService#isRunning()} or it will result in undefined notification
 * behavior.
 */
public class UpdateCheckService extends Service {

    private static final int NOTIFICATION_ID = 1;

    private static final String EXTRA_IS_MANUAL_CHECK = "isManualCheck";

    public static Intent makeIntent(Context context, boolean isManualCheck) {
        Intent intent = new Intent(context, UpdateCheckService.class);
        intent.putExtra(EXTRA_IS_MANUAL_CHECK, isManualCheck);
        return intent;
    }

    private ServiceNotificationManager notificationManager;

    private boolean shouldShowToast; // false by default

    private Disposable disposable;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        notificationManager = new ServiceNotificationManager(this, NOTIFICATION_ID);
        notificationManager.startForeground(new UpdateCheckNotification());
        Timber.w("Service created");
    }

    @Override
    public void onDestroy() {
        if (disposable != null) disposable.dispose();
        Timber.w("Service destroyed");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean isManualCheck = intent.getBooleanExtra(EXTRA_IS_MANUAL_CHECK, false);
        if (isManualCheck && !shouldShowToast) {
            shouldShowToast = true;
            Toast.makeText(this, "Checking for updates...", LENGTH_SHORT).show();
        }

        if (disposable == null || disposable.isDisposed()) {
            checkForUpdates();
        }

        return START_NOT_STICKY;
    }

    private void checkForUpdates() {
        disposable = UpdateServer.API.getUpdateInfo()
                .retry(3)
                .observeOn(mainThread())
                .doFinally(this::stopSelf)
                .subscribe(this::onCheckSuccess, this::onCheckError);
    }

    private void onCheckSuccess(UpdateInfoJson updateInfoJson) {
        if (BuildConfig.VERSION_CODE < updateInfoJson.getVersionCode()) {
            stopForeground(true);

            String updateUrl = updateInfoJson.getUpdateUrl();
            EventBus.getDefault().postSticky(new UpdateEvent(true));
            notificationManager.notify(new UpdateAvailableNotification(updateUrl));
        } else {
            if (shouldShowToast) {
                String message = "Update Check: No new updates.";
                Toast.makeText(this, message, LENGTH_SHORT).show();
            }
        }
    }

    private void onCheckError(Throwable t) {
        Timber.w(t, "Failed to get update info");

        if (shouldShowToast) {
            String message = "Could not check for updates. Check your connection or try again later.";
            Toast.makeText(this, message, LENGTH_SHORT).show();
        }
    }
}
