package me.devsaki.hentoid.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.annimon.stream.function.Consumer;

import org.greenrobot.eventbus.EventBus;

import java.util.List;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.exception.ContentNotRemovedException;
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

    private final MutableLiveData<Long> contentIdToShowFirst = new MutableLiveData<>();     // ID of the content to show at 1st display


    public QueueViewModel(@NonNull Application application, @NonNull CollectionDAO collectionDAO) {
        super(application);
        dao = collectionDAO;
        refresh();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
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
    public LiveData<Long> getContentIdToShowFirst() {
        return contentIdToShowFirst;
    }


    // =========================
    // =========== QUEUE ACTIONS
    // =========================

    /**
     * Perform a new search
     */
    private void refresh() {
        // Queue
        if (currentQueueSource != null) queue.removeSource(currentQueueSource);
        currentQueueSource = dao.getQueueContent();
        queue.addSource(currentQueueSource, queue::setValue);
        // Errors
        if (currentErrorsSource != null) errors.removeSource(currentErrorsSource);
        currentErrorsSource = dao.getErrorContent();
        errors.addSource(currentErrorsSource, errors::setValue);
    }


    // =========================
    // ========= CONTENT ACTIONS
    // =========================

    public void move(@NonNull Integer oldPosition, @NonNull Integer newPosition) {
        if (oldPosition.equals(newPosition)) return;
        Timber.d(">> move %s to %s", oldPosition, newPosition);

        // Get unpaged data to be sure we have everything in one collection
        List<QueueRecord> queue = dao.selectQueue();
        if (oldPosition < 0 || oldPosition >= queue.size()) return;

        // Move the item
        QueueRecord fromValue = queue.get(oldPosition);
        int delta = oldPosition < newPosition ? 1 : -1;
        for (int i = oldPosition; i != newPosition; i += delta) {
            queue.set(i, queue.get(i + delta));
        }
        queue.set(newPosition, fromValue);

        // Renumber everything
        int index = 1;
        for (QueueRecord qr : queue) qr.rank = index++;

        // Update queue in DB
        dao.updateQueue(queue);

        // If the 1st item is involved, signal it being skipped
        if (0 == newPosition || 0 == oldPosition)
            EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_SKIP));
    }

    public void invertQueue() {
        // Get unpaged data to be sure we have everything in one collection
        List<QueueRecord> queue = dao.selectQueue();
        if (queue.size() < 2) return;

        // Renumber everything in reverse order
        int index = 1;
        for (int i = queue.size() - 1; i >= 0; i--) {
            queue.get(i).rank = index++;
        }

        // Update queue and signal skipping the 1st item
        dao.updateQueue(queue);
        EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_SKIP));
    }

    /**
     * Cancel download of designated Content
     * NB : Contrary to Pause command, Cancel removes the Content from the download queue
     *
     * @param contents Contents whose download has to be canceled
     */
    public void cancel(@NonNull List<Content> contents, Consumer<Throwable> onError) {
        for (Content c : contents)
            EventBus.getDefault().post(new DownloadEvent(c, DownloadEvent.EV_CANCEL));
        remove(contents, onError);
    }

    public void remove(@NonNull List<Content> content, Consumer<Throwable> onError) {
        compositeDisposable.add(
                Observable.fromIterable(content)
                        .observeOn(Schedulers.io())
                        .map(c -> doRemove(c.getId()))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                v -> saveQueue(),
                                onError::accept
                        )
        );
    }

    public void cancelAll(Consumer<Throwable> onError) {
        List<QueueRecord> queue = dao.selectQueue();
        if (queue.isEmpty()) return;

        EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_PAUSE));

        compositeDisposable.add(
                Observable.fromIterable(queue)
                        .observeOn(Schedulers.io())
                        .map(qr -> doRemove(qr.content.getTargetId()))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                v -> {
                                    // Nothing to do here; UI callbacks are handled through LiveData
                                },
                                onError::accept
                        )
        );
    }

    private boolean doRemove(long contentId) throws ContentNotRemovedException {
        Helper.assertNonUiThread();
        // Remove content altogether from the DB (including queue)
        Content content = dao.selectContent(contentId);
        if (content != null) {
            dao.deleteQueue(content);
            // Remove the content from the disk and the DB
            ContentHelper.removeContent(getApplication(), content, dao);
        }
        return true;
    }

    /**
     * Add the given content to the download queue
     *
     * @param content Content to be added to the download queue
     */
    public void addContentToQueue(@NonNull final Content content, StatusContent targetImageStatus) {
        dao.addContentToQueue(content, targetImageStatus);
    }

    public void setContentIdToShowFirst(long id) {
        contentIdToShowFirst.setValue(id);
    }

    private void saveQueue() {
        if (ContentHelper.updateQueueJson(getApplication().getApplicationContext(), dao))
            Timber.i("Queue JSON successfully saved");
        else Timber.w("Queue JSON saving failed");
    }

    /**
     * Move all items at given positions to top of the list
     *
     * @param positions Adapter positions of the items to move
     */
    public void moveTop(List<Integer> positions) {
        int processed = 0;
        for (Integer oldPos : positions) {
            move(oldPos, processed);
            processed++;
        }
    }

    public void moveBottom(List<Integer> positions) {
        List<QueueRecord> queueRecords = queue.getValue();
        if (null == queueRecords) return;
        int endPos = queueRecords.size() - 1;
        int processed = 0;

        for (Integer oldPos : positions) {
            move(oldPos - processed, endPos);
            processed++;
        }
    }
}
