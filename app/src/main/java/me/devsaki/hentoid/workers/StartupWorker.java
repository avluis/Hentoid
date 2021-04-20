package me.devsaki.hentoid.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import java.util.List;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
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
        super(context, parameters, R.id.startup_service);
    }

    @Override
    Notification getStartNotification() {
        return new StartupProgressNotification(0, 0);
    }

    @Override
    void onClear() {
        launchTasks.clear();
    }

    @Override
    void getToWork(@NonNull Data input) {
        launchTasks = AppStartup.getPostLaunchTasks(getApplicationContext());
        launchTasks.addAll(DatabaseMaintenance.getPostLaunchCleanupTasks(getApplicationContext()));
        doRunTask(0,
                v -> notificationManager.notify(new StartupProgressNotification(Math.round(v * 100), 100)),
                v -> {
                },
                () -> notificationManager.notify(new StartupCompleteNotification()));
    }

    private void doRunTask(
            int taskIndex,
            @NonNull Consumer<Float> onMainProgress,
            @NonNull Consumer<Float> onSecondaryProgress,
            @NonNull Runnable onComplete
    ) {
        if (launchDisposable != null) launchDisposable.dispose();
        try {
            onMainProgress.accept(taskIndex * 1f / launchTasks.size());
        } catch (Exception e) {
            Timber.w(e);
        }
        // Continue executing launch tasks
        if (taskIndex < launchTasks.size()) {
            Timber.i("Post-launch task %s/%s", taskIndex + 1, launchTasks.size());
            launchDisposable = launchTasks.get(taskIndex)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            onSecondaryProgress,
                            Timber::e,
                            () -> doRunTask(taskIndex + 1, onMainProgress, onSecondaryProgress, onComplete)
                    );
        } else {
            if (launchDisposable != null) launchDisposable.dispose();
            onComplete.run();
        }
    }
}
