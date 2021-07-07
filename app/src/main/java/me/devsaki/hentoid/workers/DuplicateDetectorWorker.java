package me.devsaki.hentoid.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Completable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.DuplicatesDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.DuplicateEntry;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.notification.duplicates.DuplicateCompleteNotification;
import me.devsaki.hentoid.notification.duplicates.DuplicateProgressNotification;
import me.devsaki.hentoid.notification.duplicates.DuplicateStartNotification;
import me.devsaki.hentoid.util.ContentHelper;
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

    private final CompositeDisposable notificationDisposables = new CompositeDisposable();

    private final AtomicInteger currentIndex = new AtomicInteger(0);
    private final AtomicBoolean stopped = new AtomicBoolean(false);


    public DuplicateDetectorWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters, R.id.duplicate_detector_service, "duplicate_detector");

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
        notificationDisposables.clear();
    }

    @Override
    void onClear() {
        if (!isStopped() && !isComplete())
            Preferences.setDuplicateLastIndex(currentIndex.get());
        else Preferences.setDuplicateLastIndex(-1);

        stopped.set(true);
        notificationDisposables.clear();
        dao.cleanup();
        duplicatesDAO.cleanup();
    }

    @Override
    void getToWork(@NonNull Data input) {
        DuplicateData.Parser inputData = new DuplicateData.Parser(input);

        if (inputData.getUseCover()) {
            // Run cover indexing in the background
            trace(Log.INFO, "Covers to index : %s", dao.countContentWithUnhashedCovers());

            DuplicateHelper.Companion.indexCovers(getApplicationContext(), dao, stopped,
                    this::indexContentInfo, this::notifyIndexProgress, this::indexError);

            trace(Log.INFO, "Indexing done");
        }

        // No need to continue if the process has already been stopped
        if (isStopped()) return;

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

        Map<Long, List<Long>> matchedIds = new HashMap<>();
        Map<Long, List<Long>> reverseMatchedIds = new HashMap<>();

        // Retrieve number of lines done in previous iteration (ended with RETRY)
        int startIndex = Preferences.getDuplicateLastIndex() + 1;
        if (0 == startIndex) duplicatesDAO.clearEntries();
        else {
            trace(Log.DEBUG, "Resuming from index %d", startIndex);
            // Pre-populate matchedIds and reverseMatchedIds using existing duplicates
            List<DuplicateEntry> entries = duplicatesDAO.getEntries();
            for (DuplicateEntry entry : entries)
                processEntry(entry.getReferenceId(), entry.getDuplicateId(), matchedIds, reverseMatchedIds);
        }

        trace(Log.DEBUG, "Preparation started");
        // Pre-compute all book entries as DuplicateCandidates
        List<DuplicateHelper.DuplicateCandidate> candidates = new ArrayList<>();
        dao.streamStoredContent(false, false, Preferences.Constant.ORDER_FIELD_SIZE, true,
                content -> candidates.add(new DuplicateHelper.DuplicateCandidate(content, useTitle, useArtist, useSameLanguage, Long.MIN_VALUE)));

        trace(Log.DEBUG, "Detection started for %d books", candidates.size());
        processAll(
                duplicatesDAO,
                candidates,
                matchedIds,
                reverseMatchedIds,
                startIndex,
                useTitle,
                useCover,
                useArtist,
                useSameLanguage,
                ignoreChapters,
                sensitivity);

        trace(Log.DEBUG, "Final End reached (currentIndex=%d, complete=%s)", currentIndex.get(), isComplete());

        setComplete(true);
        matchedIds.clear();
    }

    private void processAll(DuplicatesDAO duplicatesDao,
                            List<DuplicateHelper.DuplicateCandidate> library,
                            Map<Long, List<Long>> matchedIds,
                            Map<Long, List<Long>> reverseMatchedIds,
                            int startIndex,
                            boolean useTitle,
                            boolean useCover,
                            boolean useSameArtist,
                            boolean useSameLanguage,
                            boolean ignoreChapters,
                            int sensitivity) {
        List<DuplicateEntry> tempResults = new ArrayList<>();
        StringSimilarity cosine = new Cosine();
        int max = library.size() - 1;
        for (int i = startIndex; i < library.size(); i++) {
            if (isStopped()) return;

            DuplicateHelper.DuplicateCandidate reference = library.get(i);

            for (int j = (i + 1); j < library.size(); j++) {
                if (isStopped()) return;
                DuplicateHelper.DuplicateCandidate candidate = library.get(j);

                DuplicateEntry entry = DuplicateHelper.Companion.processContent(
                        reference, candidate,
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

            if (0 == i % 10)
                notifyProcessProgress(i, max); // Only update every 10 iterations for performance
        }
        notifyProcessProgress(max, max);
    }

    private void indexContentInfo(Content c) {
        // No need for that unless we're debugging
        if (BuildConfig.DEBUG)
            trace(Log.DEBUG, "Indexing %s/%s", c.getSite().name(), ContentHelper.formatBookFolderName(c).left);
    }

    private void indexError(Throwable t) {
        Timber.w(t);
        String message = t.getMessage();
        if (message != null)
            trace(Log.WARN, "Indexing error : %s", message);
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

    private void notifyIndexProgress(int progress, int max) {
        Timber.i(">> indexing progress %s", progress * 1f / max);
        if (progress < max) {
            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, R.id.duplicate_index, STEP_COVER_INDEX, progress, 0, max));
        } else {
            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, R.id.duplicate_index, STEP_COVER_INDEX, progress, 0, max));
        }
    }

    private void notifyProcessProgress(int progress, int max) {
        notificationDisposables.add(Completable.fromRunnable(() -> doNotifyProcessProgress(progress, max))
                .subscribeOn(Schedulers.computation())
                .subscribe(
                        notificationDisposables::clear
                )
        );
    }

    private void doNotifyProcessProgress(int progress, int max) {
        if (progress < max) {
            setForegroundAsync(notificationManager.buildForegroundInfo(new DuplicateProgressNotification(progress, max)));
            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, R.id.duplicate_detect, STEP_DUPLICATES, progress, 0, max));
        } else {
            setForegroundAsync(notificationManager.buildForegroundInfo(new DuplicateCompleteNotification(0)));
            EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, R.id.duplicate_detect, STEP_DUPLICATES, progress, 0, max));
        }
    }
}
