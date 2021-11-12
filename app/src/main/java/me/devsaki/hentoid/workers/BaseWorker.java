package me.devsaki.hentoid.workers;

import android.content.Context;
import android.util.Log;

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

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.events.ServiceDestroyedEvent;
import me.devsaki.hentoid.util.LogHelper;
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

    protected final String logName;
    private final List<LogHelper.LogEntry> logs;


    protected static boolean isRunning(@NonNull Context context, @IdRes int serviceId) {
        ListenableFuture<List<WorkInfo>> infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(Integer.toString(serviceId));
        try {
            Optional<WorkInfo> info = Stream.of(infos.get()).filter(i -> !i.getState().isFinished()).findFirst();
            return info.isPresent();
        } catch (Exception e) {
            Timber.e(e);
            // Restore interrupted state
            Thread.currentThread().interrupt();
        }
        return false;
    }

    protected BaseWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters,
            @IdRes int serviceId,
            String logName) {
        super(context, parameters);
        this.serviceId = serviceId;

        initNotifications(context);

        Timber.w("%s worker created", this.getClass().getSimpleName());
        if (logName != null && !logName.isEmpty()) {
            this.logName = logName;
            logs = new ArrayList<>();
            logs.add(new LogHelper.LogEntry("worker created"));
        } else {
            this.logName = "";
            logs = null;
        }
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

    protected void trace(int priority, String s, Object... t) {
        s = String.format(s, t);
        Timber.log(priority, s);
        boolean isError = (priority > Log.INFO);
        if (null != logs) logs.add(new LogHelper.LogEntry(s, isError));
    }

    private void clear() {
        onClear();

        if (logs != null) {
            logs.add(new LogHelper.LogEntry("Worker destroyed / stopped=%s / complete=%s", isStopped(), isComplete));

            LogHelper.LogInfo logInfo = new LogHelper.LogInfo();
            logInfo.setFileName(logName);
            logInfo.setHeaderName(logName);
            logInfo.setEntries(logs);
            LogHelper.writeLog(HentoidApp.getInstance(), logInfo);
        }

        // Tell everyone the worker is shutting down
        EventBus.getDefault().post(new ServiceDestroyedEvent(serviceId));

        Timber.d("%s worker destroyed", this.getClass().getSimpleName());
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            ensureLongRunning();
            getToWork(getInputData());
        } catch (Exception e) {
            if (logs != null)
                logs.add(new LogHelper.LogEntry("Exception caught ! %s : %s", e.getMessage(), e.getStackTrace()));
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
