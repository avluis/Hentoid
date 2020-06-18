package me.devsaki.hentoid.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.paging.PagedList;

import org.greenrobot.eventbus.EventBus;

import java.util.List;

import io.reactivex.Completable;
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
import timber.log.Timber;


public class QueueViewModel extends AndroidViewModel {

    // Collection DAO
    private final CollectionDAO dao;
    // Cleanup for all RxJava calls
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    // Collection data for queue
    private LiveData<List<QueueRecord>> currentQueueSource;
    private final MediatorLiveData<List<QueueRecord>> queuePaged = new MediatorLiveData<>();
    // Collection data for errors
    private LiveData<PagedList<Content>> currentErrorsSource;
    private final MediatorLiveData<PagedList<Content>> errorsPaged = new MediatorLiveData<>();


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
    public LiveData<List<QueueRecord>> getQueuePaged() {
        return queuePaged;
    }

    @NonNull
    public LiveData<PagedList<Content>> getErrorsPaged() {
        return errorsPaged;
    }


    // =========================
    // =========== QUEUE ACTIONS
    // =========================

    /**
     * Perform a new search
     */
    private void refresh() {
        // Queue
        if (currentQueueSource != null) queuePaged.removeSource(currentQueueSource);
        currentQueueSource = dao.getQueueContent();
        queuePaged.addSource(currentQueueSource, queuePaged::setValue);
        // Errors
        if (currentErrorsSource != null) errorsPaged.removeSource(currentErrorsSource);
        currentErrorsSource = dao.getErrorContent();
        errorsPaged.addSource(currentErrorsSource, errorsPaged::setValue);
    }


    // =========================
    // ========= CONTENT ACTIONS
    // =========================

    public void move(@NonNull Integer oldPosition, @NonNull Integer newPosition) {
        if (oldPosition.equals(newPosition)) return;

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
     * @param content Content whose download has to be canceled
     */
    public void cancel(@NonNull Content content) {
        EventBus.getDefault().post(new DownloadEvent(content, DownloadEvent.EV_CANCEL));
        remove(content);
    }

    public void remove(@NonNull Content content) {
        compositeDisposable.add(
                Completable.fromRunnable(() -> doRemove(content.getId()))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> {
                                    // Nothing to do here; UI callbacks are handled through LiveData
                                },
                                Timber::e
                        )
        );
    }

    public void cancelAll() {
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
                                Timber::e
                        )
        );
    }

    private boolean doRemove(long contentId) {
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
}
