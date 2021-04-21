package me.devsaki.hentoid.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import org.greenrobot.eventbus.EventBus;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
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
import timber.log.Timber;


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
        Observable<Integer> indexObservable = Observable.create(emitter -> DuplicateHelper.Companion.indexCovers(getApplicationContext(), dao, library, interrupted, emitter));
        Observable<Float> processObservable = Observable.create(emitter -> DuplicateHelper.Companion.processLibrary(duplicatesDAO, library, data.getUseTitle(), data.getUseCover(), data.getUseArtist(), data.getUseSameLanguage(), data.getSensitivity(), interrupted, emitter));

        // tasks are used to execute Rx's observeOn on current thread
        // See https://github.com/square/retrofit/issues/370#issuecomment-315868381
        LinkedBlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();

        disposable = indexObservable
                .subscribeOn(Schedulers.io())
                .doOnNext(indexProgress -> EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, STEP_COVER_INDEX, indexProgress, 0, 100)))
                .filter(indexProgress -> indexProgress % 10 > 0) // Only run a comparison every 10% of indexing
                .observeOn(Schedulers.computation())
                .flatMap(indexProgress -> processObservable)
                .observeOn(Schedulers.from(tasks::add))
                .subscribe(
                        processProgress -> {
                            int progressPc = Math.round(processProgress * 100);
                            Timber.i(">> DUPLICATE PROGRESS %s", progressPc);
                            notificationManager.notify(new DuplicateProgressNotification(progressPc, 100));
                            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, STEP_DUPLICATES, progressPc, 0, 100));
                        },
                        Timber::w,
                        () -> {
                            Timber.i(">> DUPLICATE COMPLETE");
                            notificationManager.notify(new DuplicateCompleteNotification(0)); // TODO nb duplicates
                            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, STEP_DUPLICATES, 100, 0, 100));
                        }
                );

        try {
            tasks.take().run();
        } catch (InterruptedException e) {
            Timber.w(e);
        }
        /*
        if (data.getUseCover())
            DuplicateHelper.Companion.indexCovers(getApplicationContext(), dao, library, interrupted);
        if (!interrupted.get())
            DuplicateHelper.Companion.processLibrary(duplicatesDAO, library, data.getUseTitle(), data.getUseCover(), data.getUseArtist(), data.getUseSameLanguage(), data.getSensitivity(), interrupted);
         */
    }
}
