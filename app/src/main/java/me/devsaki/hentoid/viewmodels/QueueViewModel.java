package me.devsaki.hentoid.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.annimon.stream.function.Consumer;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.download.ContentQueueManager;
import me.devsaki.hentoid.util.exception.ContentNotRemovedException;
import me.devsaki.hentoid.util.exception.FileNotRemovedException;
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
    public void cancel(@NonNull List<Content> contents, Consumer<Throwable> onError, Runnable onComplete) {
        remove(contents, onError, onComplete);
    }

    public void removeAll(Consumer<Throwable> onError, Runnable onComplete) {
        List<Content> errors = dao.selectErrorContentList();
        if (errors.isEmpty()) return;

        remove(errors, onError, onComplete);
    }

    public void remove(@NonNull List<Content> content, Consumer<Throwable> onError, Runnable onComplete) {
        AtomicInteger nbDeleted = new AtomicInteger();

        compositeDisposable.add(
                Observable.fromIterable(content)
                        .observeOn(Schedulers.io())
                        .map(c -> doRemove(c.getId()))
                        .doOnComplete(this::onRemoveComplete) // Done properly in the IO thread
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                v -> {
                                    nbDeleted.getAndIncrement();
                                    EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, 0, nbDeleted.get(), 0, content.size()));
                                },
                                t -> {
                                    EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, 0, nbDeleted.get(), 0, content.size()));
                                    onError.accept(t);
                                },
                                () -> {
                                    EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, 0, nbDeleted.get(), 0, content.size()));
                                    onComplete.run();
                                }
                        )
        );
    }

    public void cancelAll(Consumer<Throwable> onError, Runnable onComplete) {
        List<QueueRecord> localQueue = dao.selectQueue();
        if (localQueue.isEmpty()) return;

        EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_PAUSE));

        AtomicInteger nbDeleted = new AtomicInteger();

        compositeDisposable.add(
                Observable.fromIterable(localQueue)
                        .observeOn(Schedulers.io())
                        .map(qr -> doRemove(qr.getContent().getTargetId()))
                        .doOnComplete(this::onRemoveComplete) // Done properly in the IO thread
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                v -> {
                                    nbDeleted.getAndIncrement();
                                    EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, 0, nbDeleted.get(), 0, localQueue.size()));
                                },
                                t -> {
                                    EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, 0, nbDeleted.get(), 0, localQueue.size()));
                                    onError.accept(t);
                                },
                                () -> {
                                    EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, 0, nbDeleted.get(), 0, localQueue.size()));
                                    onComplete.run();
                                }
                        )
        );
    }

    private boolean doRemove(long contentId) throws ContentNotRemovedException {
        Helper.assertNonUiThread();
        // Remove content altogether from the DB (including queue)
        Content content = dao.selectContent(contentId);
        if (null == content) return true;
        try {
            ContentHelper.removeQueuedContent(getApplication(), dao, content);
        } catch (ContentNotRemovedException e) {
            // Don't throw the exception if we can't remove something that isn't there
            if (!(e instanceof FileNotRemovedException && content.getStorageUri().isEmpty()))
                throw e;
        }
        return true;
    }

    public void redownloadContent(
            @NonNull final List<Content> contentList,
            boolean reparseContent,
            boolean reparseImages,
            int addMode,
            @NonNull final Runnable onSuccess) {
        StatusContent targetImageStatus = reparseImages ? StatusContent.ERROR : null;

        compositeDisposable.add(
                Observable.fromIterable(contentList)
                        .observeOn(Schedulers.io())
                        .map(c -> (reparseContent) ? ContentHelper.reparseFromScratch(c) : c)
                        .map(c -> {
                            if (reparseImages) ContentHelper.purgeFiles(getApplication(), c);
                            return c;
                        })
                        .doOnNext(c -> dao.addContentToQueue(c, targetImageStatus, addMode, ContentQueueManager.getInstance().isQueueActive()))
                        .doOnComplete(() -> {
                            // TODO is there stuff to do on the IO thread ?
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                v -> {
                                    if (Preferences.isQueueAutostart())
                                        ContentQueueManager.getInstance().resumeQueue(getApplication());
                                    onSuccess.run();
                                },
                                Timber::e
                        )
        );
    }

    public void setContentToShowFirst(long hash) {
        contentHashToShowFirst.setValue(hash);
    }

    private void onRemoveComplete() {
        if (ContentHelper.updateQueueJson(getApplication().getApplicationContext(), dao))
            Timber.i("Queue JSON successfully saved");
        else Timber.w("Queue JSON saving failed");
    }
}
