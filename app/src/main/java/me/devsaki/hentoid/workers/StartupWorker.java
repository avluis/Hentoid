package me.devsaki.hentoid.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.core.AppStartup;
import me.devsaki.hentoid.database.DatabaseMaintenance;
import me.devsaki.hentoid.notification.startup.StartupCompleteNotification;
import me.devsaki.hentoid.notification.startup.StartupProgressNotification;
import me.devsaki.hentoid.util.notification.NotificationManager;
import timber.log.Timber;


/**
 * Worker responsible for importing an existing Hentoid library.
 */
public class StartupWorker extends Worker {

    private static final int NOTIFICATION_ID = 1;

    private static boolean running;
    private NotificationManager notificationManager;
    private Disposable launchDisposable = null;

    private List<Observable<Float>> launchTasks;


    public StartupWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters);

        initNotifications(context);

        Timber.w("Import worker created");
    }

    @Override
    public void onStopped() {
        clear();
        launchTasks.clear();
        super.onStopped();
    }

    private void initNotifications(Context context) {
        notificationManager = new NotificationManager(context, NOTIFICATION_ID);
        notificationManager.cancel();
    }

    private void ensureLongRunning() {
        setForegroundAsync(notificationManager.buildForegroundInfo(new StartupProgressNotification(0, 0)));
    }

    private void clear() {
        running = false;

        if (notificationManager != null) notificationManager.cancel();

        Timber.d("Import worker destroyed");
    }

    @NonNull
    @Override
    public Result doWork() {
        if (running) return Result.failure();
        running = true;

        ensureLongRunning();

        try {
            launchTasks = AppStartup.getPostLaunchTasks(getApplicationContext());
            launchTasks.addAll(DatabaseMaintenance.getPostLaunchCleanupTasks(getApplicationContext()));
            doRunTask(0,
                    v -> notificationManager.notify(new StartupProgressNotification(Math.round(v * 100), 100)),
                    v -> {
                    },
                    () -> notificationManager.notify(new StartupCompleteNotification()));
        } finally {
            clear();
        }
        return Result.success();
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
