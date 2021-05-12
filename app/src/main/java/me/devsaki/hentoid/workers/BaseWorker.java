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
    private boolean isComplete = true;

//    protected final List<LogHelper.LogEntry> logs = new ArrayList<>();


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
        // TEMP
//        logs.add(new LogHelper.LogEntry("worker created"));
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

    void setComplete(boolean complete) {
        isComplete = complete;
    }

    boolean isComplete() {
        return isComplete;
    }

    private void clear() {
        onClear();

/*
        logs.add(new LogHelper.LogEntry("Worker destroyed / stopped=%s / complete=%s", isStopped(), isComplete));

        LogHelper.LogInfo logInfo = new LogHelper.LogInfo();
        logInfo.setFileName(Integer.toString(serviceId));
        logInfo.setLogName(Integer.toString(serviceId));
        logInfo.setEntries(logs);
        LogHelper.writeLog(HentoidApp.getInstance(), logInfo);
*/
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
        } catch (Exception e) {
//            logs.add(new LogHelper.LogEntry("Exception caught ! %s : %s", e.getMessage(), e.getStackTrace()));
            Timber.e(e);
        } finally {
            clear();
        }

        // Retry when incomplete and not manually stopped
        if (!isStopped() && !isComplete) return Result.retry();
        return Result.success();
    }

    abstract Notification getStartNotification();

    abstract void onInterrupt();

    abstract void onClear();

    abstract void getToWork(@NonNull Data input);
}
