package me.devsaki.hentoid.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import org.greenrobot.eventbus.EventBus;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.disposables.Disposable;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.DuplicatesDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.notification.duplicates.DuplicateCompleteNotification;
import me.devsaki.hentoid.notification.duplicates.DuplicateProgressNotification;
import me.devsaki.hentoid.notification.duplicates.DuplicateStartNotification;
import me.devsaki.hentoid.util.DuplicateHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.notification.Notification;
import me.devsaki.hentoid.workers.data.DuplicateData;


/**
 * Worker responsible for running post-startup tasks
 */
public class DuplicateDetectorWorker extends BaseWorker {

    // Worker tag
    public static String WORKER_TAG = "duplicate detector";

    // Processing steps
    public static int STEP_COVER_INDEX = 0;
    public static int STEP_DUPLICATES = 1;

    private final CollectionDAO dao;
    private final DuplicatesDAO duplicatesDAO;

    private final AtomicBoolean interrupted = new AtomicBoolean(false);
    private Disposable disposable = null;


    public DuplicateDetectorWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters, R.id.duplicate_detector_service);

        dao = new ObjectBoxDAO(getApplicationContext());
        duplicatesDAO = new DuplicatesDAO(getApplicationContext());
    }

    public static boolean isRunning() {
        return isRunning(R.id.duplicate_detector_service);
    }

    @Override
    Notification getStartNotification() {
        return new DuplicateStartNotification();
    }

    @Override
    void onInterrupt() {
        interrupted.set(true);
        if (disposable != null) disposable.dispose();
    }

    @Override
    void onClear() {
        if (disposable != null) disposable.dispose();
        dao.cleanup();
        duplicatesDAO.cleanup();
    }

    @Override
    void getToWork(@NonNull Data input) {
        DuplicateData.Parser data = new DuplicateData.Parser(input);

        duplicatesDAO.clearEntries();

        List<Content> library = dao.selectStoredBooks(false, false, Preferences.Constant.ORDER_FIELD_SIZE, true);

        // Run cover indexing in the background
        disposable = DuplicateHelper.Companion.indexCoversRx(getApplicationContext(), dao, library, this::notifyIndexProgress);

        // Run duplicate detection in the worker
        DuplicateHelper.Companion.processLibrary(
                duplicatesDAO,
                library,
                data.getUseTitle(),
                data.getUseCover(),
                data.getUseArtist(),
                data.getUseSameLanguage(),
                data.getSensitivity(),
                interrupted,
                this::notifyProcessProgress);
    }

    private void notifyIndexProgress(Float progress) {
        int progressPc = Math.round(progress * 1000);
        if (progressPc < 1000) {
            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, STEP_COVER_INDEX, progressPc, 0, 1000));
        } else {
            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, STEP_COVER_INDEX, progressPc, 0, 1000));
        }
    }

    private void notifyProcessProgress(Float progress) {
        int progressPc = Math.round(progress * 1000);
        if (progressPc < 1000) {
            notificationManager.notify(new DuplicateProgressNotification(progressPc, 1000));
            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, STEP_DUPLICATES, progressPc, 0, 1000));
        } else {
            notificationManager.notify(new DuplicateCompleteNotification(0));
            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, STEP_DUPLICATES, progressPc, 0, 1000));
        }
    }
}
