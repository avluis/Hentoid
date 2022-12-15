package me.devsaki.hentoid.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import org.greenrobot.eventbus.EventBus;

import io.reactivex.Completable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.notification.updateJson.UpdateJsonCompleteNotification;
import me.devsaki.hentoid.notification.updateJson.UpdateJsonProgressNotification;
import me.devsaki.hentoid.notification.updateJson.UpdateJsonStartNotification;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.GroupHelper;
import me.devsaki.hentoid.util.notification.Notification;
import me.devsaki.hentoid.workers.data.UpdateJsonData;
import timber.log.Timber;


/**
 * Service responsible for updating JSON files
 */
public class UpdateJsonWorker extends BaseWorker {

    // Variable used during the import process
    private final CollectionDAO dao;
    private int totalItems = 0;
    private int nbOK = 0;

    private final CompositeDisposable notificationDisposables = new CompositeDisposable();


    public UpdateJsonWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters, R.id.udpate_json_service, "update_json");
        dao = new ObjectBoxDAO(context);
    }

    public static boolean isRunning(@NonNull Context context) {
        return isRunning(context, R.id.udpate_json_service);
    }

    @Override
    Notification getStartNotification() {
        return new UpdateJsonStartNotification();
    }

    @Override
    void onInterrupt() {
        notificationDisposables.clear();
    }

    @Override
    void onClear() {
        notificationDisposables.clear();
        if (dao != null) dao.cleanup();
    }

    @Override
    void getToWork(@NonNull Data input) {
        UpdateJsonData.Parser data = new UpdateJsonData.Parser(getInputData());
        long[] contentIds = data.getContentIds();

        if (data.getUpdateMissingDlDate())
            contentIds = dao.selectContentIdsWithUpdatableJson();

        if (null == contentIds) {
            Timber.w("Expected contentIds or selectContentIdsWithUpdatableJson");
            return;
        }

        totalItems = contentIds.length;

        for (long id : contentIds) {
            Content c = dao.selectContent(id);
            if (c != null) ContentHelper.persistJson(getApplicationContext(), c);
            nextOK();
        }
        progressDone();

        if (data.getUpdateGroups()) GroupHelper.updateGroupsJson(getApplicationContext(), dao);
    }

    private void nextOK() {
        nbOK++;
        notifyProcessProgress();
    }

    private void notifyProcessProgress() {
        notificationDisposables.add(Completable.fromRunnable(this::doNotifyProcessProgress).subscribeOn(Schedulers.computation()).subscribe(notificationDisposables::clear));
    }

    private void doNotifyProcessProgress() {
        notificationManager.notify(new UpdateJsonProgressNotification(nbOK, totalItems));
        EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, R.id.update_json, 0, nbOK, 0, totalItems));
    }

    private void progressDone() {
        notificationManager.notify(new UpdateJsonCompleteNotification());
        EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, R.id.update_json, 0, nbOK, 0, totalItems));
    }
}
