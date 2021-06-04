package me.devsaki.hentoid.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import java.util.List;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.core.AppStartup;
import me.devsaki.hentoid.database.DatabaseMaintenance;
import me.devsaki.hentoid.notification.startup.StartupCompleteNotification;
import me.devsaki.hentoid.notification.startup.StartupProgressNotification;
import me.devsaki.hentoid.util.notification.Notification;
import timber.log.Timber;


/**
 * Worker responsible for running post-startup tasks
 */
public class StartupWorker extends BaseWorker {

    private Disposable launchDisposable = null;
    private List<Observable<Float>> launchTasks;


    public StartupWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters, R.id.startup_service, null);
    }

    @Override
    Notification getStartNotification() {
        return new StartupProgressNotification(0, 0);
    }

    @Override
    void onInterrupt() {
        if (launchDisposable != null) launchDisposable.dispose();
    }

    @Override
    void onClear() {
        launchTasks.clear();
        if (launchDisposable != null) launchDisposable.dispose();
    }

    @Override
    void getToWork(@NonNull Data input) {
        launchTasks = AppStartup.getPostLaunchTasks(getApplicationContext());
        launchTasks.addAll(DatabaseMaintenance.getPostLaunchCleanupTasks(getApplicationContext()));

        launchDisposable = Observable.concat(launchTasks)
                .subscribe(
                        v -> notificationManager.notify(new StartupProgressNotification(Math.round(v * 100), 100)),
                        Timber::e,
                        () -> notificationManager.notify(new StartupCompleteNotification())
                );
    }
}
