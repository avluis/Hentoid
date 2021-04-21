package me.devsaki.hentoid.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.DuplicatesDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.notification.duplicates.DuplicateStartNotification;
import me.devsaki.hentoid.util.DuplicateHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.notification.Notification;
import me.devsaki.hentoid.workers.data.DuplicateData;


/**
 * Worker responsible for running post-startup tasks
 */
public class DuplicateDetectorWorker extends BaseWorker {

    private final CollectionDAO dao;
    private final DuplicatesDAO duplicatesDAO;

    private AtomicBoolean interrupted = new AtomicBoolean(false);


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
        dao.cleanup();
        duplicatesDAO.cleanup();
    }

    @Override
    void getToWork(@NonNull Data input) {
        DuplicateData.Parser data = new DuplicateData.Parser(input);

        duplicatesDAO.clear();

        List<Content> library = dao.selectStoredBooks(false, false, Preferences.Constant.ORDER_FIELD_SIZE, true);
        if (data.getUseCover())
            DuplicateHelper.Companion.indexCovers(getApplicationContext(), dao, library, interrupted);
        if (!interrupted.get())
            DuplicateHelper.Companion.processLibrary(duplicatesDAO, library, data.getUseTitle(), data.getUseCover(), data.getUseArtist(), data.getUseSameLanguage(), data.getSensitivity(), interrupted);
    }
}
