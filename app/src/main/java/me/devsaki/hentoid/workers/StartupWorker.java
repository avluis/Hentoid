package me.devsaki.hentoid.workers;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;

import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
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

    private final CompositeDisposable launchDisposable = new CompositeDisposable();
    private List<Observable<Float>> launchTasks;


    public StartupWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters, R.id.startup_service, null);
    }

    @Override
    Notification getStartNotification() {
        return new StartupProgressNotification("Startup progress", 0, 0);
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

    @SuppressLint("TimberArgCount")
    @Override
    void getToWork(@NonNull Data input) {
        launchTasks = AppStartup.getPostLaunchTasks(getApplicationContext());
        launchTasks.addAll(DatabaseMaintenance.getPostLaunchCleanupTasks(getApplicationContext()));

        int step = 0;
        for (Observable<Float> o : launchTasks) {
            String message = String.format(Locale.ENGLISH, "Startup progress : step %d / %d", ++step, launchTasks.size());
            Timber.d(message);
            notificationManager.notify(new StartupProgressNotification(message, Math.round(step * 1f / launchTasks.size() * 100), 100));
            runTask(o);
        }

        notificationManager.notify(new StartupCompleteNotification());
    }

    private void runTask(Observable<Float> o) {

        // Tasks are used to execute Rx's observeOn on current thread
        // See https://github.com/square/retrofit/issues/370#issuecomment-315868381
        LinkedBlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();

        launchDisposable.add(o
                .observeOn(Schedulers.from(tasks::add))
                .subscribe()
        );

        try {
            tasks.take().run();
        } catch (InterruptedException e) {
            Timber.w(e);
            Thread.currentThread().interrupt();
        }
    }
}
