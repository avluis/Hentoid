package me.devsaki.hentoid.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.download.ContentQueueManager;
import me.devsaki.hentoid.util.exception.EmptyResultException;
import me.devsaki.hentoid.workers.DeleteWorker;
import me.devsaki.hentoid.workers.data.DeleteData;
import timber.log.Timber;


public class QueueViewModel extends AndroidViewModel {

    // Collection DAO
    private final CollectionDAO dao;
    // Cleanup for all RxJava calls
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    // Collection data for queue
    private LiveData<List<QueueRecord>> currentQueueSource;
    private final MediatorLiveData<List<QueueRecord>> queue = new MediatorLiveData<>();
    // Collection data for errors
    private LiveData<List<Content>> currentErrorsSource;
    private final MediatorLiveData<List<Content>> errors = new MediatorLiveData<>();

    private final MutableLiveData<Long> contentHashToShowFirst = new MutableLiveData<>();     // ID of the content to show at 1st display


    public QueueViewModel(@NonNull Application application, @NonNull CollectionDAO collectionDAO) {
        super(application);
        dao = collectionDAO;
        refresh();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        dao.cleanup();
        compositeDisposable.clear();
    }

    @NonNull
    public LiveData<List<QueueRecord>> getQueue() {
        return queue;
    }

    @NonNull
    public LiveData<List<Content>> getErrors() {
        return errors;
    }

    @NonNull
    public LiveData<Boolean> getNewSearch() {
        return newSearch;
    }

    @NonNull
    public LiveData<Long> getContentHashToShowFirst() {
        return contentHashToShowFirst;
    }

    // Updated whenever a new search is performed
    private final MediatorLiveData<Boolean> newSearch = new MediatorLiveData<>();


    // =========================
    // =========== QUEUE ACTIONS
    // =========================

    /**
     * Perform a new search
     */
    public void refresh() {
        // Queue
        if (currentQueueSource != null) queue.removeSource(currentQueueSource);
        currentQueueSource = dao.selectQueueLive();
        queue.addSource(currentQueueSource, queue::setValue);
        // Errors
        if (currentErrorsSource != null) errors.removeSource(currentErrorsSource);
        currentErrorsSource = dao.selectErrorContent();
        errors.addSource(currentErrorsSource, errors::setValue);
        newSearch.setValue(true);
    }

    public void searchQueueUniversal(String query) {
        if (currentQueueSource != null) queue.removeSource(currentQueueSource);
        currentQueueSource = dao.selectQueueLive(query);
        queue.addSource(currentQueueSource, queue::setValue);
        newSearch.setValue(true);
    }


    // =========================
    // ========= CONTENT ACTIONS
    // =========================

    public void moveAbsolute(@NonNull Integer oldPosition, @NonNull Integer newPosition) {
        if (oldPosition.equals(newPosition)) return;
        Timber.d(">> move %s to %s", oldPosition, newPosition);

        // Get unpaged data to be sure we have everything in one collection
        List<QueueRecord> localQueue = dao.selectQueue();
        if (oldPosition < 0 || oldPosition >= localQueue.size()) return;

        // Move the item
        QueueRecord fromValue = localQueue.get(oldPosition);
        int delta = oldPosition < newPosition ? 1 : -1;
        for (int i = oldPosition; i != newPosition; i += delta) {
            localQueue.set(i, localQueue.get(i + delta));
        }
        localQueue.set(newPosition, fromValue);

        // Renumber everything
        int index = 1;
        for (QueueRecord qr : localQueue) qr.setRank(index++);

        // Update queue in DB
        dao.updateQueue(localQueue);

        // If the 1st item is involved, signal it being skipped
        if (0 == newPosition || 0 == oldPosition)
            EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_SKIP));
    }

    /**
     * Move all items at given positions to top of the list
     *
     * @param relativePositions Adapter positions of the items to move
     */
    public void moveTop(List<Integer> relativePositions) {
        List<Integer> absolutePositions = relativeToAbsolutePositions(relativePositions);

        int processed = 0;
        for (Integer oldPos : absolutePositions) {
            moveAbsolute(oldPos, processed);
            processed++;
        }
    }

    /**
     * Move all items at given positions to bottom of the list
     *
     * @param relativePositions Adapter positions of the items to move
     */
    public void moveBottom(List<Integer> relativePositions) {
        List<Integer> absolutePositions = relativeToAbsolutePositions(relativePositions);

        List<QueueRecord> dbQueue = dao.selectQueue();
        if (null == dbQueue) return;
        int endPos = dbQueue.size() - 1;
        int processed = 0;

        for (Integer oldPos : absolutePositions) {
            moveAbsolute(oldPos - processed, endPos);
            processed++;
        }
    }

    private List<Integer> relativeToAbsolutePositions(List<Integer> relativePositions) {
        List<Integer> result = new ArrayList<>();
        List<QueueRecord> currentQueue = queue.getValue();
        List<QueueRecord> dbQueue = dao.selectQueue();
        if (null == currentQueue || null == dbQueue) return relativePositions;

        for (Integer position : relativePositions) {
            for (int i = 0; i < dbQueue.size(); i++)
                if (dbQueue.get(i).id == currentQueue.get(position).id) {
                    result.add(i);
                    break;
                }
        }
        return result;
    }


    public void invertQueue() {
        // Get unpaged data to be sure we have everything in one collection
        List<QueueRecord> localQueue = dao.selectQueue();
        if (localQueue.size() < 2) return;

        // Renumber everything in reverse order
        int index = 1;
        for (int i = localQueue.size() - 1; i >= 0; i--) {
            localQueue.get(i).setRank(index++);
        }

        // Update queue and signal skipping the 1st item
        dao.updateQueue(localQueue);
        EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_SKIP));
    }

    /**
     * Cancel download of designated Content
     * NB : Contrary to Pause command, Cancel removes the Content from the download queue
     *
     * @param contents Contents whose download has to be canceled
     */
    public void cancel(@NonNull List<Content> contents) {
        remove(contents);
    }

    public void removeAll() {
        List<Content> errorsLocal = dao.selectErrorContentList();
        if (errorsLocal.isEmpty()) return;

        remove(errorsLocal);
    }

    public void remove(@NonNull List<Content> contentList) {
        DeleteData.Builder builder = new DeleteData.Builder();
        if (!contentList.isEmpty())
            builder.setQueueIds(Stream.of(contentList).map(Content::getId).toList());

        WorkManager workManager = WorkManager.getInstance(getApplication());
        workManager.enqueueUniqueWork(
                Integer.toString(R.id.delete_service),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                new OneTimeWorkRequest.Builder(DeleteWorker.class).setInputData(builder.getData()).build()
        );
    }

    private void purgeItem(@NonNull Content content) {
        DeleteData.Builder builder = new DeleteData.Builder();
        builder.setContentPurgeIds(Stream.of(content).map(Content::getId).toList());

        WorkManager workManager = WorkManager.getInstance(getApplication());
        workManager.enqueueUniqueWork(
                Integer.toString(R.id.delete_service),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                new OneTimeWorkRequest.Builder(DeleteWorker.class).setInputData(builder.getData()).build()
        );
    }

    public void cancelAll() {
        List<QueueRecord> localQueue = dao.selectQueue();
        if (localQueue.isEmpty()) return;
        List<Long> contentIdList = Stream.of(localQueue).map(qr -> qr.getContent().getTargetId()).toList();

        EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_PAUSE));

        DeleteData.Builder builder = new DeleteData.Builder();
        if (!contentIdList.isEmpty()) builder.setQueueIds(contentIdList);

        WorkManager workManager = WorkManager.getInstance(getApplication());
        workManager.enqueueUniqueWork(
                Integer.toString(R.id.delete_service),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                new OneTimeWorkRequest.Builder(DeleteWorker.class).setInputData(builder.getData()).build()
        );
    }

    public void redownloadContent(
            @NonNull final List<Content> contentList,
            boolean reparseContent,
            boolean reparseImages,
            int position,
            @NonNull final Consumer<Integer> onSuccess,
            @NonNull final Consumer<Throwable> onError) {
        StatusContent targetImageStatus = reparseImages ? StatusContent.ERROR : null;

        AtomicInteger errorCount = new AtomicInteger(0);

        compositeDisposable.add(
                Observable.fromIterable(contentList)
                        .observeOn(Schedulers.io())
                        .map(c -> (reparseContent) ? ContentHelper.reparseFromScratch(c) : Optional.of(c))
                        .doOnNext(c -> {
                            if (c.isPresent()) {
                                Content content = c.get();
                                // Non-blocking performance bottleneck; run in a dedicated worker
                                // TODO if the purge is extremely long, that worker might still be working while downloads are happening on these same books
                                if (reparseImages) purgeItem(content);
                                dao.addContentToQueue(
                                        content, targetImageStatus, position,
                                        ContentQueueManager.getInstance().isQueueActive());
                            } else {
                                errorCount.incrementAndGet();
                                onError.accept(new EmptyResultException("Content unreachable"));
                            }
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnComplete(() -> {
                            if (Preferences.isQueueAutostart())
                                ContentQueueManager.getInstance().resumeQueue(getApplication());
                            onSuccess.accept(contentList.size() - errorCount.get());
                        })
                        .subscribe(
                                v -> { // Nothing; feedback is done through LiveData
                                },
                                onError::accept
                        )
        );
    }

    public void setContentToShowFirst(long hash) {
        contentHashToShowFirst.setValue(hash);
    }
}
