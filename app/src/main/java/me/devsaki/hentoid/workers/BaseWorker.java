package me.devsaki.hentoid.workers;

import android.content.Context;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.google.common.util.concurrent.ListenableFuture;

import org.greenrobot.eventbus.EventBus;

import java.util.List;

import me.devsaki.hentoid.events.ServiceDestroyedEvent;
import me.devsaki.hentoid.util.notification.Notification;
import me.devsaki.hentoid.util.notification.NotificationManager;
import timber.log.Timber;


/**
 * Base class for all workers
 */
public abstract class BaseWorker extends Worker {

    protected NotificationManager notificationManager;

    private final @IdRes
    int serviceId;

    protected static boolean isRunning(@NonNull Context context, @IdRes int serviceId) {
        ListenableFuture<List<WorkInfo>> infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(Integer.toString(serviceId));
        try {
            Optional<WorkInfo> info = Stream.of(infos.get()).filter(i -> !i.getState().isFinished()).findFirst();
            return info.isPresent();
        } catch (Exception e) {
            Timber.e(e);
        }
        return false;
    }

    public BaseWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters,
            @IdRes int serviceId) {
        super(context, parameters);
        this.serviceId = serviceId;

        initNotifications(context);

        Timber.w("%s worker created", this.getClass().getSimpleName());
    }

    @Override
    public void onStopped() {
        onInterrupt();
        clear();
        super.onStopped();
    }

    private void initNotifications(Context context) {
        notificationManager = new NotificationManager(context, serviceId);
        notificationManager.cancel();
    }

    private void ensureLongRunning() {
        setForegroundAsync(notificationManager.buildForegroundInfo(getStartNotification()));
    }

    private void clear() {
        onClear();

        // Tell everyone the worker is shutting down
        EventBus.getDefault().post(new ServiceDestroyedEvent(serviceId));

        if (notificationManager != null) notificationManager.cancel();

        Timber.d("%s worker destroyed", this.getClass().getSimpleName());
    }

    @NonNull
    @Override
    public Result doWork() {
        ensureLongRunning();
        try {
            getToWork(getInputData());
        } finally {
            clear();
        }
        return Result.success();
    }

    abstract Notification getStartNotification();

    abstract void onInterrupt();

    abstract void onClear();

    abstract void getToWork(@NonNull Data input);
}
