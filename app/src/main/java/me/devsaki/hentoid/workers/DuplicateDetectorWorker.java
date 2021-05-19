package me.devsaki.hentoid.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Completable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
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
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.notification.Notification;
import me.devsaki.hentoid.util.string_similarity.Cosine;
import me.devsaki.hentoid.util.string_similarity.StringSimilarity;
import me.devsaki.hentoid.workers.data.DuplicateData;
import timber.log.Timber;


/**
 * Worker responsible for running post-startup tasks
 */
public class DuplicateDetectorWorker extends BaseWorker {

    // Processing steps
    public static int STEP_COVER_INDEX = 0;
    public static int STEP_DUPLICATES = 1;

    private final CollectionDAO dao;
    private final DuplicatesDAO duplicatesDAO;

    private Disposable indexDisposable = null;
    private final CompositeDisposable notificationDisposables = new CompositeDisposable();

    private final AtomicInteger currentIndex = new AtomicInteger(0);


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
        if (notificationDisposables != null) notificationDisposables.clear();
    }

    @Override
    void onClear() {
        if (!isStopped() && !isComplete())
            Preferences.setDuplicateLastIndex(currentIndex.get());
        else Preferences.setDuplicateLastIndex(-1);

        if (indexDisposable != null) indexDisposable.dispose();
        if (notificationDisposables != null) notificationDisposables.clear();
        dao.cleanup();
        duplicatesDAO.cleanup();
    }

    @Override
    void getToWork(@NonNull Data input) {
        DuplicateData.Parser inputData = new DuplicateData.Parser(input);

        // Run cover indexing in the background
        indexDisposable = DuplicateHelper.Companion.indexCoversRx(getApplicationContext(), dao, this::notifyIndexProgress);

        // Initialize duplicate detection
        detectDuplicates(inputData.getUseTitle(), inputData.getUseCover(), inputData.getUseArtist(), inputData.getUseSameLanguage(), inputData.getIgnoreChapters(), inputData.getSensitivity());
    }

    private void detectDuplicates(
            boolean useTitle,
            boolean useCover,
            boolean useArtist,
            boolean useSameLanguage,
            boolean ignoreChapters,
            int sensitivity) {

        // Mark process as incomplete until all combinations are searched
        // to support abort and retry
        setComplete(false);

        HashSet<Pair<Long, Long>> ignoredIds = new HashSet<>();
        Map<Long, List<Long>> matchedIds = new HashMap<>();
        Map<Long, List<Long>> reverseMatchedIds = new HashMap<>();

        // Retrieve number of lines done in previous iteration (ended with RETRY)
        int startIndex = Preferences.getDuplicateLastIndex() + 1;
        if (0 == startIndex) duplicatesDAO.clearEntries();
        else {
            // Pre-populate matchedIds and reverseMatchedIds using existing duplicates
            List<DuplicateEntry> entries = duplicatesDAO.getEntries();
            for (DuplicateEntry entry : entries)
                processEntry(entry.getReferenceId(), entry.getDuplicateId(), matchedIds, reverseMatchedIds);
        }

        boolean isReRun = false;
        do {
//            logs.add(new LogHelper.LogEntry("Preparation started"));
            // Pre-compute all book entries as DuplicateCandidates
            List<DuplicateHelper.DuplicateCandidate> candidates = new ArrayList<>();
            dao.streamStoredContent(false, false, Preferences.Constant.ORDER_FIELD_SIZE, true,
                    content -> candidates.add(new DuplicateHelper.DuplicateCandidate(content, useTitle, useArtist, useSameLanguage, Long.MIN_VALUE)));

//            logs.add(new LogHelper.LogEntry("Detection started"));
            processAll(
                    duplicatesDAO,
                    candidates,
                    ignoredIds,
                    matchedIds,
                    reverseMatchedIds,
                    startIndex,
                    isReRun,
                    useTitle,
                    useCover,
                    useArtist,
                    useSameLanguage,
                    ignoreChapters,
                    sensitivity);
            Timber.d(" >> PROCESS End reached");
//            logs.add(new LogHelper.LogEntry("Setection End"));
            if (isStopped()) break;
            if (!ignoredIds.isEmpty()) {
                try {
                    //noinspection BusyWait
                    Thread.sleep(3000); // Don't rush in another loop
                } catch (InterruptedException e) {
                    Timber.w(e);
                }
            }
            if (isStopped()) break;
            isReRun = true;
        } while (!ignoredIds.isEmpty());

        setComplete(ignoredIds.isEmpty());
//        logs.add(new LogHelper.LogEntry("Final End reached (complete=%s)", isComplete()));

        ignoredIds.clear();
        matchedIds.clear();
    }

    private void processAll(DuplicatesDAO duplicatesDao,
                            List<DuplicateHelper.DuplicateCandidate> library,
                            HashSet<Pair<Long, Long>> ignoredIds,
                            Map<Long, List<Long>> matchedIds,
                            Map<Long, List<Long>> reverseMatchedIds,
                            int startIndex,
                            boolean isReRun,
                            boolean useTitle,
                            boolean useCover,
                            boolean useSameArtist,
                            boolean useSameLanguage,
                            boolean ignoreChapters,
                            int sensitivity) {
        List<DuplicateEntry> tempResults = new ArrayList<>();
        StringSimilarity cosine = new Cosine();
        for (int i = startIndex; i < library.size(); i++) {
            if (ignoredIds.size() > 1e6)
                break; // Better to wait rather than saturating the ignored IDs map
            if (isStopped()) return;

            DuplicateHelper.DuplicateCandidate reference = library.get(i);

            for (int j = (i + 1); j < library.size(); j++) {
                if (isStopped()) return;
                DuplicateHelper.DuplicateCandidate candidate = library.get(j);

                // For re-runs, check if that combination has been ignored in the past and has to be matched
                if (isReRun && !ignoredIds.contains(new Pair<>(reference.getId(), candidate.getId())))
                    continue;

                DuplicateEntry entry = DuplicateHelper.Companion.processContent(
                        reference, candidate,
                        ignoredIds,
                        useTitle, useCover, useSameArtist, useSameLanguage, ignoreChapters, sensitivity, cosine);
                if (entry != null && processEntry(entry.getReferenceId(), entry.getDuplicateId(), matchedIds, reverseMatchedIds))
                    tempResults.add(entry);
            }

            // Save results for this reference
            if (!tempResults.isEmpty()) {
                duplicatesDao.insertEntries(tempResults);
                tempResults.clear();
            }

            currentIndex.set(i);

            if (0 == i % 10) {
                float progress = i * 1f / (library.size() - 1);
                notifyProcessProgress(progress); // Only update every 10 iterations for performance
            }
        }
    }

    private void notifyIndexProgress(Float progress) {
        int progressPc = Math.round(progress * 10000);
        if (progressPc < 10000) {
            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, STEP_COVER_INDEX, progressPc, 0, 10000));
        } else {
            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, STEP_COVER_INDEX, progressPc, 0, 10000));
        }
    }

    private boolean processEntry(
            long referenceId,
            long candidateId,
            Map<Long, List<Long>> matchedIds,
            Map<Long, List<Long>> reverseMatchedIds
    ) {
        // Check if matched IDs don't already contain the reference as a transitive link
        // TODO doc
        boolean transitiveMatchFound = false;
        List<Long> reverseMatchesC = reverseMatchedIds.get(candidateId);
        if (reverseMatchesC != null && !reverseMatchesC.isEmpty()) {
            List<Long> reverseMatchesRef = reverseMatchedIds.get(referenceId);
            if (reverseMatchesRef != null && !reverseMatchesRef.isEmpty()) {
                for (long lc : reverseMatchesC) {
                    for (long lr : reverseMatchesRef)
                        if (lc == lr) {
                            transitiveMatchFound = true;
                            break;
                        }
                    if (transitiveMatchFound) break;
                }
            }
        }
        // Record the entry
        if (!transitiveMatchFound) {
            List<Long> matches = matchedIds.get(referenceId);
            if (null == matches) matches = new ArrayList<>();
            matches.add(candidateId);
            matchedIds.put(referenceId, matches);

            List<Long> reverseMatches = reverseMatchedIds.get(candidateId);
            if (null == reverseMatches) reverseMatches = new ArrayList<>();
            reverseMatches.add(referenceId);
            reverseMatchedIds.put(candidateId, reverseMatches);
            return true;
        }
        return false;
    }

    private void notifyProcessProgress(Float progress) {
        notificationDisposables.add(Completable.fromRunnable(() -> doNotifyProcessProgress(progress))
                .subscribeOn(Schedulers.computation())
                .subscribe(
                        notificationDisposables::clear
                )
        );
    }

    private void doNotifyProcessProgress(Float progress) {
        int progressPc = Math.round(progress * 10000);
        if (progressPc < 10000) {
            setForegroundAsync(notificationManager.buildForegroundInfo(new DuplicateProgressNotification(progressPc, 10000)));
            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, STEP_DUPLICATES, progressPc, 0, 10000));
        } else {
            setForegroundAsync(notificationManager.buildForegroundInfo(new DuplicateCompleteNotification(0)));
            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, STEP_DUPLICATES, progressPc, 0, 10000));
        }
    }
}
