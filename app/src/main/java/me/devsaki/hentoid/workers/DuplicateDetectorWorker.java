package me.devsaki.hentoid.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import info.debatty.java.stringsimilarity.Cosine;
import io.reactivex.disposables.Disposable;
import kotlin.Pair;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.DuplicatesDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.DuplicateEntry;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.notification.duplicates.DuplicateCompleteNotification;
import me.devsaki.hentoid.notification.duplicates.DuplicateProgressNotification;
import me.devsaki.hentoid.notification.duplicates.DuplicateStartNotification;
import me.devsaki.hentoid.util.DuplicateHelper;
import me.devsaki.hentoid.util.LogHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.notification.Notification;
import me.devsaki.hentoid.workers.data.DuplicateData;
import timber.log.Timber;


/**
 * Worker responsible for running post-startup tasks
 */
public class DuplicateDetectorWorker extends BaseWorker {

    private final double[] TOTAL_THRESHOLDS = new double[]{0.8, 0.85, 0.9};

    // Processing steps
    public static int STEP_COVER_INDEX = 0;
    public static int STEP_DUPLICATES = 1;

    private final CollectionDAO dao;
    private final DuplicatesDAO duplicatesDAO;

    private Disposable indexDisposable = null;

    private final Set<Integer> fullLines = new HashSet<>();

    private DuplicateData.Parser inputData;
    private Cosine textComparator;


    public DuplicateDetectorWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters, R.id.duplicate_detector_service);

        dao = new ObjectBoxDAO(getApplicationContext());
        duplicatesDAO = new DuplicatesDAO(getApplicationContext());
    }

    public static boolean isRunning(@NonNull Context context) {
        return isRunning(context, R.id.duplicate_detector_service);
    }

    @Override
    Notification getStartNotification() {
        return new DuplicateStartNotification();
    }

    @Override
    void onInterrupt() {
        if (indexDisposable != null) indexDisposable.dispose();
    }

    @Override
    void onClear() {
        if (indexDisposable != null) indexDisposable.dispose();
        dao.cleanup();
        duplicatesDAO.cleanup();
    }

    @Override
    void getToWork(@NonNull Data input) {
        inputData = new DuplicateData.Parser(input);

        duplicatesDAO.clearEntries();

        // Run cover indexing in the background
        indexDisposable = DuplicateHelper.Companion.indexCoversRx(getApplicationContext(), dao, this::notifyIndexProgress);

        List<DuplicateHelper.DuplicateCandidate> candidates = new ArrayList<>();
        dao.streamStoredContent(false, false, Preferences.Constant.ORDER_FIELD_SIZE, true,
                content -> candidates.add(new DuplicateHelper.DuplicateCandidate(content, inputData.getUseTitle(), inputData.getUseArtist(), inputData.getUseSameLanguage())));

        textComparator = new Cosine();

        long nbCombinations = (candidates.size() * (candidates.size() - 1)) / 2;
        HashSet<Pair<Long, Long>> detectedIds = new HashSet<>();

        do {
            logs.add(new LogHelper.LogEntry("Loop started"));
            processAll(duplicatesDAO, candidates, detectedIds, nbCombinations);
            Timber.i(" >> PROCESS End reached");
            logs.add(new LogHelper.LogEntry("Loop End reached"));
            if (isStopped()) break;
            try {
                //noinspection BusyWait
                Thread.sleep(3000); // Don't rush in another loop
            } catch (InterruptedException e) {
                Timber.w(e);
            }
            if (isStopped()) break;
        } while (detectedIds.size() * 1f / nbCombinations < 1f);
        logs.add(new LogHelper.LogEntry("Final End reached"));

        detectedIds.clear();
        notifyProcessProgress(1f);
    }

    private void processAll(DuplicatesDAO duplicatesDao,
                            List<DuplicateHelper.DuplicateCandidate> library,
                            HashSet<Pair<Long, Long>> detectedIds,
                            long nbCombinations) {
        List<DuplicateEntry> tempResults = new ArrayList<>();
        for (int i = 0; i < library.size(); i++) {
            if (isStopped()) return;

            if (!fullLines.contains(i)) {
                int lineMatchCounter = 0;
                DuplicateHelper.DuplicateCandidate reference = library.get(i);

                for (int j = (i + 1); j < library.size(); j++) {
                    if (isStopped()) return;
                    DuplicateHelper.DuplicateCandidate candidate = library.get(j);

                    // Check if that combination has already been processed
                    if (detectedIds.contains(new Pair<>(reference.getId(), candidate.getId()))) {
                        lineMatchCounter++;
                        continue;
                    }

                    DuplicateEntry entry = processContent(reference, candidate);
                    if (entry != null) tempResults.add(entry);

                    // Mark as processed
                    detectedIds.add(new Pair<>(reference.getId(), candidate.getId()));
                }

                // Record full lines for quicker scan
                if (lineMatchCounter == library.size() - i - 1) fullLines.add(i);

                // Save results for this reference
                if (!tempResults.isEmpty()) {
                    duplicatesDao.insertEntries(tempResults);
                    tempResults.clear();
                }
            }

            float progress = detectedIds.size() * 1f / nbCombinations;
            Timber.i(" >> PROCESS [%s] %s / %s (%s %%)", i, detectedIds.size(), nbCombinations, progress);
            notifyProcessProgress(progress);
        }
    }

    @Nullable
    private DuplicateEntry processContent(
            DuplicateHelper.DuplicateCandidate reference,
            DuplicateHelper.DuplicateCandidate candidate) {
        float titleScore = -1f;
        float coverScore = -1f;
        float artistScore = -1f;

        // Remove if not same language
        if (inputData.getUseSameLanguage() && !DuplicateHelper.Companion.containsSameLanguage(reference.getCountryCodes(), candidate.getCountryCodes()))
            return null;

        if (inputData.getUseCover())
            coverScore = DuplicateHelper.Companion.computeCoverScore(
                    reference.getCoverHash(), candidate.getCoverHash(),
                    inputData.getSensitivity());

        if (inputData.getUseTitle())
            titleScore = DuplicateHelper.Companion.computeTitleScore(
                    textComparator,
                    reference.getTitleCleanup(), reference.getTitleNoDigits(),
                    candidate.getTitleCleanup(), candidate.getTitleNoDigits(),
                    inputData.getSensitivity());

        if (inputData.getUseArtist())
            artistScore = DuplicateHelper.Companion.computeArtistScore(reference.getArtistsCleanup(), candidate.getArtistsCleanup());

        DuplicateEntry result = new DuplicateEntry(reference.getId(), reference.getSize(), candidate.getId(), titleScore, coverScore, artistScore, 0);
        if (result.calcTotalScore() >= TOTAL_THRESHOLDS[inputData.getSensitivity()]) return result;
        else return null;
    }

    private void notifyIndexProgress(Float progress) {
        int progressPc = Math.round(progress * 10000);
        if (progressPc < 10000) {
            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, STEP_COVER_INDEX, progressPc, 0, 10000));
        } else {
            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, STEP_COVER_INDEX, progressPc, 0, 10000));
        }
    }

    private void notifyProcessProgress(Float progress) {
        int progressPc = Math.round(progress * 10000);
        if (progressPc < 10000) {
            notificationManager.notify(new DuplicateProgressNotification(progressPc, 10000));
            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, STEP_DUPLICATES, progressPc, 0, 10000));
        } else {
            notificationManager.notify(new DuplicateCompleteNotification(0));
            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, STEP_DUPLICATES, progressPc, 0, 10000));
        }
    }
}
