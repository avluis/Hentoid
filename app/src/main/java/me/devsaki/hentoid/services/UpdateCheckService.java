package me.devsaki.hentoid.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;

import java.util.List;

import io.reactivex.disposables.Disposable;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.events.UpdateEvent;
import me.devsaki.hentoid.json.UpdateInfo;
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

    private static final int NOTIFICATION_ID = UpdateCheckService.class.getName().hashCode();

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
            Toast.makeText(this, R.string.pref_check_updates_manual_checking, LENGTH_SHORT).show();
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

    private void onCheckSuccess(UpdateInfo updateInfoJson) {
        boolean newVersion = false;
        if (BuildConfig.VERSION_CODE < updateInfoJson.getVersionCode(BuildConfig.DEBUG)) {
            stopForeground(true);

            String updateUrl = updateInfoJson.getUpdateUrl(BuildConfig.DEBUG);
            notificationManager.notify(new UpdateAvailableNotification(updateUrl));
            newVersion = true;
        } else if (shouldShowToast) {
            Toast.makeText(this, R.string.pref_check_updates_manual_no_new, LENGTH_SHORT).show();
        }

        // Get the alerts relevant to current version code
        List<UpdateInfo.SourceAlert> sourceAlerts = Stream.of(updateInfoJson.getSourceAlerts(BuildConfig.DEBUG))
                .filter(a -> a.getFixedByBuild() > BuildConfig.VERSION_CODE)
                .toList();
        // Send update info through the bus to whom it may concern
        EventBus.getDefault().postSticky(new UpdateEvent(newVersion, sourceAlerts));
    }

    private void onCheckError(Throwable t) {
        Timber.w(t, "Failed to get update info");
        notificationManager.cancel();

        if (shouldShowToast)
            Toast.makeText(this, R.string.pref_check_updates_manual_no_connection, LENGTH_SHORT).show();
    }
}
