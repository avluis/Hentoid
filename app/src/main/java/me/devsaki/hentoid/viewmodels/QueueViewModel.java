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
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.util.ContentHelper;
import timber.log.Timber;


public class QueueViewModel extends AndroidViewModel {

    // Collection DAO
    private final CollectionDAO queueDao = new ObjectBoxDAO(getApplication().getApplicationContext());
    // Cleanup for all RxJava calls
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    // Collection data
    private LiveData<PagedList<QueueRecord>> currentSource;
    private final MediatorLiveData<PagedList<QueueRecord>> queuePaged = new MediatorLiveData<>();


    public QueueViewModel(@NonNull Application application) {
        super(application);
        refresh();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        compositeDisposable.clear();
    }

    @NonNull
    public LiveData<PagedList<QueueRecord>> getQueuePaged() {
        return queuePaged;
    }


    // =========================
    // =========== QUEUE ACTIONS
    // =========================

    /**
     * Perform a new library search
     */
    private void refresh() {
        if (currentSource != null) queuePaged.removeSource(currentSource);

        currentSource = queueDao.getQueueContent();

        queuePaged.addSource(currentSource, queuePaged::setValue);
    }


    // =========================
    // ========= CONTENT ACTIONS
    // =========================


    /**
     * Move designated content up in the download queue (= raise its priority)
     *
     * @param contentId ID of Content whose priority has to be raised
     */
    public void moveUp(long contentId) {
        List<QueueRecord> queue = queueDao.selectQueue();

        long prevItemId = 0;
        int prevItemQueuePosition = -1;
        int prevItemPosition = -1;
        int loopPosition = 0;

        for (QueueRecord p : queue) {
            if (p.content.getTargetId() == contentId && prevItemId != 0) {
                queueDao.updateQueue(p.content.getTargetId(), prevItemQueuePosition);
                queueDao.updateQueue(prevItemId, p.rank);

                if (0 == prevItemPosition)
                    EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_SKIP));
                break;
            } else {
                prevItemId = p.content.getTargetId();
                prevItemQueuePosition = p.rank;
                prevItemPosition = loopPosition;
            }
            loopPosition++;
        }
    }

    /**
     * Move designated content on the top of the download queue (= raise its priority)
     *
     * @param contentId ID of Content whose priority has to be raised to the top
     */
    public void moveTop(long contentId) {
        List<QueueRecord> queue = queueDao.selectQueue();
        QueueRecord p;

        long topItemId = 0;
        int topItemQueuePosition = -1;

        for (int i = 0; i < queue.size(); i++) {
            p = queue.get(i);
            if (0 == topItemId) {
                topItemId = p.content.getTargetId();
                topItemQueuePosition = p.rank;
            }

            if (p.content.getTargetId() == contentId) {
                // Put selected item on top of list in the DB
                queueDao.updateQueue(p.content.getTargetId(), topItemQueuePosition);

                // Skip download for the 1st item of the adapter
                EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_SKIP));

                break;
            } else {
                queueDao.updateQueue(p.content.getTargetId(), p.rank + 1); // Depriorize every item by 1
            }
        }
    }

    /**
     * Move designated content down in the download queue (= lower its priority)
     *
     * @param contentId ID of Content whose priority has to be lowered
     */
    public void moveDown(long contentId) {
        List<QueueRecord> queue = queueDao.selectQueue();

        long itemId = 0;
        int itemQueuePosition = -1;
        int itemPosition = -1;
        int loopPosition = 0;

        for (QueueRecord p : queue) {
            if (p.content.getTargetId() == contentId) {
                itemId = p.content.getTargetId();
                itemQueuePosition = p.rank;
                itemPosition = loopPosition;
            } else if (itemId != 0) {
                queueDao.updateQueue(p.content.getTargetId(), itemQueuePosition);
                queueDao.updateQueue(itemId, p.rank);

                if (0 == itemPosition)
                    EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_SKIP));
                break;
            }
            loopPosition++;
        }
    }

    /**
     * Cancel download of designated Content
     * NB : Contrary to Pause command, Cancel removes the Content from the download queue
     *
     * @param content Content whose download has to be canceled
     */
    public void cancel(Content content) {
        EventBus.getDefault().post(new DownloadEvent(content, DownloadEvent.EV_CANCEL));

        compositeDisposable.add(
                Completable.fromRunnable(() -> doCancel(content.getId()))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> {
                                },
                                Timber::e
                        )
        );
    }

    private void doCancel(long contentId) {
        // Remove content altogether from the DB (including queue)
        Content content = queueDao.selectContent(contentId);
        if (content != null) {
            queueDao.deleteQueue(content);
            queueDao.deleteContent(content);
            // Remove the content from the disk
            ContentHelper.removeContent(content);
        }
    }
}
