package me.devsaki.hentoid.fragments.queue;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.listeners.ClickEventHook;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.util.List;

import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.events.DownloadPreparationEvent;
import me.devsaki.hentoid.services.ContentQueueManager;
import me.devsaki.hentoid.ui.BlinkAnimation;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.viewholders.ContentItem;
import me.devsaki.hentoid.views.CircularProgressView;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;

/**
 * Created by avluis on 04/10/2016.
 * Presents the list of works currently downloading to the user.
 */
public class QueueFragment extends Fragment {

    private final ItemAdapter<ContentItem> itemAdapter = new ItemAdapter<>();
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    // UI ELEMENTS
    private View rootView;
    private TextView mEmptyText;    // "Empty queue" message panel
    private ImageButton btnStart;   // Start / Resume button
    private ImageButton btnPause;   // Pause button
    private ImageButton btnStats;   // Error statistics button
    private TextView queueStatus;   // 1st line of text displayed on the right of the queue pause / play button
    private TextView queueInfo;     // 2nd line of text displayed on the right of the queue pause / play button
    private CircularProgressView dlPreparationProgressBar; // Circular progress bar for downloads preparation
    private Toolbar toolbar;

    // State
    private boolean isPreparingDownload = false;
    private boolean isPaused = false;
    private boolean isEmpty = false;

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        update(-1);
    }

    @Override
    public void onDestroy() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
        compositeDisposable.clear();
        super.onDestroy();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_queue, container, false);

        toolbar = requireViewById(rootView, R.id.queue_toolbar);
        toolbar.setTitle(getResources().getQuantityString(R.plurals.queue_book_count, itemAdapter.getAdapterItemCount(), itemAdapter.getAdapterItemCount()));
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());

        mEmptyText = requireViewById(rootView, R.id.queue_empty_txt);

        btnStart = requireViewById(rootView, R.id.btnStart);
        btnPause = requireViewById(rootView, R.id.btnPause);
        btnStats = requireViewById(rootView, R.id.btnStats);
        queueStatus = requireViewById(rootView, R.id.queueStatus);
        queueInfo = requireViewById(rootView, R.id.queueInfo);
        dlPreparationProgressBar = requireViewById(rootView, R.id.queueDownloadPreparationProgressBar);

        // Both queue control buttons actually just need to send a signal that will be processed accordingly by whom it may concern
        btnStart.setOnClickListener(v -> EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_UNPAUSE)));
        btnStart.setBackground(ThemeHelper.makeQueueButtonSelector(requireContext()));
        btnPause.setOnClickListener(v -> EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_PAUSE)));
        btnPause.setBackground(ThemeHelper.makeQueueButtonSelector(requireContext()));
        btnStats.setOnClickListener(v -> showErrorStats());

        ObjectBoxDB db = ObjectBoxDB.getInstance(requireActivity());
        List<Content> contents = db.selectQueueContents();
        itemAdapter.set(Stream.of(contents).map(c -> new ContentItem(c, itemAdapter)).toList());

        // Book list container
        RecyclerView recyclerView = requireViewById(rootView, R.id.queue_list);
        FastAdapter<ContentItem> fastAdapter = FastAdapter.with(itemAdapter);
        recyclerView.setAdapter(fastAdapter);

        // Item click listener
//        fastAdapter.setOnClickListener((v, a, i, p) -> onBookClick(i)); TODO implement book reading while downloading

        attachButtons(fastAdapter);

        return rootView;
    }

    private void attachButtons(FastAdapter<ContentItem> fastAdapter) {
        // Site button
        fastAdapter.addEventHook(new ClickEventHook<ContentItem>() {
            @Override
            public void onClick(@NotNull View view, int i, @NotNull FastAdapter<ContentItem> fastAdapter, @NotNull ContentItem item) {
                ContentHelper.viewContent(view.getContext(), item.getContent());
            }

            @org.jetbrains.annotations.Nullable
            @Override
            public View onBind(RecyclerView.@NotNull ViewHolder viewHolder) {
                if (viewHolder instanceof ContentItem.ContentViewHolder) {
                    return ((ContentItem.ContentViewHolder) viewHolder).getSiteButton();
                }
                return super.onBind(viewHolder);
            }
        });

        // Up button
        fastAdapter.addEventHook(new ClickEventHook<ContentItem>() {
            @Override
            public void onClick(@NotNull View view, int i, @NotNull FastAdapter<ContentItem> fastAdapter, @NotNull ContentItem item) {
                moveUp(item.getContent().getId());
            }

            @org.jetbrains.annotations.Nullable
            @Override
            public View onBind(RecyclerView.@NotNull ViewHolder viewHolder) {
                if (viewHolder instanceof ContentItem.ContentViewHolder) {
                    return ((ContentItem.ContentViewHolder) viewHolder).getUpButton();
                }
                return super.onBind(viewHolder);
            }
        });

        // Top button
        fastAdapter.addEventHook(new ClickEventHook<ContentItem>() {
            @Override
            public void onClick(@NotNull View view, int i, @NotNull FastAdapter<ContentItem> fastAdapter, @NotNull ContentItem item) {
                moveTop(item.getContent().getId());
            }

            @org.jetbrains.annotations.Nullable
            @Override
            public View onBind(RecyclerView.@NotNull ViewHolder viewHolder) {
                if (viewHolder instanceof ContentItem.ContentViewHolder) {
                    return ((ContentItem.ContentViewHolder) viewHolder).getTopButton();
                }
                return super.onBind(viewHolder);
            }
        });

        // Down button
        fastAdapter.addEventHook(new ClickEventHook<ContentItem>() {
            @Override
            public void onClick(@NotNull View view, int i, @NotNull FastAdapter<ContentItem> fastAdapter, @NotNull ContentItem item) {
                moveDown(item.getContent().getId());
            }

            @org.jetbrains.annotations.Nullable
            @Override
            public View onBind(RecyclerView.@NotNull ViewHolder viewHolder) {
                if (viewHolder instanceof ContentItem.ContentViewHolder) {
                    return ((ContentItem.ContentViewHolder) viewHolder).getDownButton();
                }
                return super.onBind(viewHolder);
            }
        });

        // Cancel button
        fastAdapter.addEventHook(new ClickEventHook<ContentItem>() {
            @Override
            public void onClick(@NotNull View view, int i, @NotNull FastAdapter<ContentItem> fastAdapter, @NotNull ContentItem item) {
                cancel(item.getContent());
            }

            @org.jetbrains.annotations.Nullable
            @Override
            public View onBind(RecyclerView.@NotNull ViewHolder viewHolder) {
                if (viewHolder instanceof ContentItem.ContentViewHolder) {
                    return ((ContentItem.ContentViewHolder) viewHolder).getCancelButton();
                }
                return super.onBind(viewHolder);
            }
        });
    }

    /**
     * Download event handler
     *
     * @param event Broadcasted event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDownloadEvent(DownloadEvent event) {

        Timber.d("Event received : %s", event.eventType);
        btnStats.setVisibility((event.pagesKO > 0) ? View.VISIBLE : View.GONE);

        switch (event.eventType) {
            case DownloadEvent.EV_PROGRESS:
                updateProgress(event.pagesOK, event.pagesKO, event.pagesTotal, event.getNumberRetries());
                break;
            case DownloadEvent.EV_UNPAUSE:
                ContentQueueManager.getInstance().unpauseQueue();
                ObjectBoxDB db = ObjectBoxDB.getInstance(requireActivity());
                db.updateContentStatus(StatusContent.PAUSED, StatusContent.DOWNLOADING);
                ContentQueueManager.getInstance().resumeQueue(requireActivity());
                refreshFirstBook(false);
                update(event.eventType);
                break;
            case DownloadEvent.EV_SKIP:
                // Books switch / display handled directly by the adapter
                Content content = itemAdapter.getAdapterItem(0).getContent();
                if (content != null) {
                    updateBookTitle(content.getTitle());
                    queueInfo.setText("");
                }
                dlPreparationProgressBar.setVisibility(View.GONE);
                break;
            case DownloadEvent.EV_COMPLETE:
                removeFromQueue(event.content);
                dlPreparationProgressBar.setVisibility(View.GONE);
                if (0 == itemAdapter.getAdapterItemCount()) btnStats.setVisibility(View.GONE);
                update(event.eventType);
                break;
            default: // EV_PAUSE, EV_CANCEL events
                dlPreparationProgressBar.setVisibility(View.GONE);
                refreshFirstBook(true);
                update(event.eventType);
        }
    }

    /**
     * Download preparation event handler
     *
     * @param event Broadcasted event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPrepDownloadEvent(DownloadPreparationEvent event) {
        if (!dlPreparationProgressBar.isShown() && !event.isCompleted() && !isPaused && !isEmpty) {
            dlPreparationProgressBar.setTotal(event.total);
            dlPreparationProgressBar.setVisibility(View.VISIBLE);
            queueInfo.setText(R.string.queue_preparing);
            isPreparingDownload = true;
        } else if (dlPreparationProgressBar.isShown() && event.isCompleted()) {
            dlPreparationProgressBar.setVisibility(View.GONE);
        }

        dlPreparationProgressBar.setProgress(event.total - event.done);
    }

    /**
     * Update main progress bar and bottom progress panel for current (1st in queue) book
     *
     * @param pagesOK       Number of pages successfully downloaded for current (1st in queue) book
     * @param pagesKO       Number of pages whose download has failed for current (1st in queue) book
     * @param totalPages    Total pages of current (1st in queue) book
     * @param numberRetries Current number of download auto-retries for current (1st in queue) book
     */
    private void updateProgress(int pagesOK, int pagesKO, int totalPages, int numberRetries) {
        if (!ContentQueueManager.getInstance().isQueuePaused() && itemAdapter.getAdapterItemCount() > 0) {
            Content content = itemAdapter.getAdapterItem(0).getContent();

            // Pages download has started
            if (content != null && pagesKO + pagesOK > 0) {
                // Update book progress bar
                content.setPercent((pagesOK + pagesKO) * 100.0 / totalPages);
                updateProgressFirstItem(false);

                // Update information bar
                StringBuilder message = new StringBuilder();
                String processedPagesFmt = Helper.formatIntAsStr(pagesOK, String.valueOf(totalPages).length());
                message.append(processedPagesFmt).append("/").append(totalPages).append(" processed (").append(pagesKO).append(" errors)");
                if (numberRetries > 0)
                    message.append(" [ retry").append(numberRetries).append("/").append(Preferences.getDlRetriesNumber()).append("]");

                queueInfo.setText(message.toString());
                isPreparingDownload = false;
            }
        }
    }

    private void refreshFirstBook(boolean isPausedEvent) {
        if (itemAdapter.getAdapterItemCount() > 0) {
            // Update book progress bar
            updateProgressFirstItem(isPausedEvent);
        }
    }

    /**
     * Update book title in bottom progress panel
     *
     * @param bookTitle Book title to display
     */
    private void updateBookTitle(String bookTitle) {
        queueStatus.setText(MessageFormat.format(requireActivity().getString(R.string.queue_dl), bookTitle));
    }

    /**
     * Update the entire Download queue screen
     *
     * @param eventType Event type that triggered the update, if any (See types described in DownloadEvent); -1 if none
     */
    private void update(int eventType) {
        int bookDiff = (eventType == DownloadEvent.EV_CANCEL) ? 1 : 0; // Cancel event means a book will be removed very soon from the queue
        isEmpty = (0 == itemAdapter.getAdapterItemCount() - bookDiff);
        isPaused = (!isEmpty && (eventType == DownloadEvent.EV_PAUSE || ContentQueueManager.getInstance().isQueuePaused() || !ContentQueueManager.getInstance().isQueueActive()));
        boolean isActive = (!isEmpty && !isPaused);

        Timber.d("Queue state : E/P/A > %s/%s/%s -- %s elements", isEmpty, isPaused, isActive, itemAdapter.getAdapterItemCount());

        // Update list visibility
        mEmptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);

        // Update control bar status
        queueInfo.setText(isPreparingDownload && !isEmpty ? R.string.queue_preparing : R.string.queue_empty2);

        Content firstContent = isEmpty ? null : itemAdapter.getAdapterItem(0).getContent();

        if (isActive) {
            btnPause.setVisibility(View.VISIBLE);
            btnStart.setVisibility(View.GONE);
            if (firstContent != null) updateBookTitle(firstContent.getTitle());

            // Stop blinking animation, if any
            queueInfo.clearAnimation();
            queueStatus.clearAnimation();
        } else {
            btnPause.setVisibility(View.GONE);

            if (isPaused) {
                btnStart.setVisibility(View.VISIBLE);
                queueStatus.setText(R.string.queue_paused);

                // Set blinking animation when queue is paused
                BlinkAnimation animation = new BlinkAnimation(750, 20);
                queueStatus.startAnimation(animation);
                queueInfo.startAnimation(animation);
            } else { // Empty
                btnStart.setVisibility(View.GONE);
                btnStats.setVisibility(View.GONE);
                queueStatus.setText("");
            }
        }

        int nbItems = itemAdapter.getAdapterItemCount();
        toolbar.setTitle(getResources().getQuantityString(R.plurals.queue_book_count, (nbItems - bookDiff), (nbItems - bookDiff)));
    }

    private void showErrorStats() {
        if (itemAdapter.getAdapterItemCount() > 0 && itemAdapter.getAdapterItem(0).getContent() != null)
            ErrorStatsDialogFragment.invoke(this, itemAdapter.getAdapterItem(0).getContent().getId());
    }

    private void updateProgressFirstItem(boolean isPausedevent) {
        Content content = itemAdapter.getAdapterItem(0).getContent();
        if (null == content) return;

        ContentItem.ContentViewHolder.updateProgress(content, requireViewById(rootView, R.id.pbDownload), 0, isPausedevent);
    }

    private void swap(int firstPosition, int secondPosition) {

        int firstPos = firstPosition < secondPosition ? firstPosition : secondPosition;
        int secondPos = firstPosition < secondPosition ? secondPosition : firstPosition;

        Content first = itemAdapter.getAdapterItem(firstPos).getContent();
        Content second = itemAdapter.getAdapterItem(secondPos).getContent();

        itemAdapter.remove(firstPos);
        itemAdapter.remove(secondPos);

        itemAdapter.add(secondPosition - 1, new ContentItem(first, itemAdapter));
        itemAdapter.add(firstPosition, new ContentItem(second, itemAdapter));
    }

    /**
     * Move designated content up in the download queue (= raise its priority)
     *
     * @param contentId ID of Content whose priority has to be raised
     */
    private void moveUp(long contentId) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(requireContext());
        List<QueueRecord> queue = db.selectQueue();

        long prevItemId = 0;
        int prevItemQueuePosition = -1;
        int prevItemPosition = -1;
        int loopPosition = 0;

//        setNotifyOnChange(false); // Prevents every update from calling a screen refresh

        for (QueueRecord p : queue) {
            if (p.content.getTargetId() == contentId && prevItemId != 0) {
                db.udpateQueue(p.content.getTargetId(), prevItemQueuePosition);
                db.udpateQueue(prevItemId, p.rank);

                swap(prevItemPosition, loopPosition);
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

//        notifyDataSetChanged(); // Final screen refresh once everything had been updated
    }

    /**
     * Move designated content on the top of the download queue (= raise its priority)
     *
     * @param contentId ID of Content whose priority has to be raised to the top
     */
    private void moveTop(long contentId) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(requireContext());
        List<QueueRecord> queue = db.selectQueue();
        QueueRecord p;

        long topItemId = 0;
        int topItemQueuePosition = -1;

//        setNotifyOnChange(false);  // Prevents every update from calling a screen refresh

        for (int i = 0; i < queue.size(); i++) {
            p = queue.get(i);
            if (0 == topItemId) {
                topItemId = p.content.getTargetId();
                topItemQueuePosition = p.rank;
            }

            if (p.content.getTargetId() == contentId) {
                // Put selected item on top of list in the DB
                db.udpateQueue(p.content.getTargetId(), topItemQueuePosition);

                // Update the displayed items
                if (i < itemAdapter.getAdapterItemCount()) { // That should never happen, but we do have rare crashes here, so...
                    Content c = itemAdapter.getAdapterItem(i).getContent();
                    itemAdapter.remove(i);
                    itemAdapter.set(0, new ContentItem(c, itemAdapter));
                }

                // Skip download for the 1st item of the adapter
                EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_SKIP));

                break;
            } else {
                db.udpateQueue(p.content.getTargetId(), p.rank + 1); // Depriorize every item by 1
            }
        }

//        notifyDataSetChanged(); // Final screen refresh once everything had been updated
    }

    /**
     * Move designated content down in the download queue (= lower its priority)
     *
     * @param contentId ID of Content whose priority has to be lowered
     */
    private void moveDown(long contentId) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(requireContext());
        List<QueueRecord> queue = db.selectQueue();

        long itemId = 0;
        int itemQueuePosition = -1;
        int itemPosition = -1;
        int loopPosition = 0;

//        setNotifyOnChange(false);  // Prevents every update from calling a screen refresh

        for (QueueRecord p : queue) {
            if (p.content.getTargetId() == contentId) {
                itemId = p.content.getTargetId();
                itemQueuePosition = p.rank;
                itemPosition = loopPosition;
            } else if (itemId != 0) {
                db.udpateQueue(p.content.getTargetId(), itemQueuePosition);
                db.udpateQueue(itemId, p.rank);

                swap(itemPosition, loopPosition);

                if (0 == itemPosition)
                    EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_SKIP));
                break;
            }
            loopPosition++;
        }

//        notifyDataSetChanged(); // Final screen refresh once everything had been updated
    }

    /**
     * Cancel download of designated Content
     * NB : Contrary to Pause command, Cancel removes the Content from the download queue
     *
     * @param content Content whose download has to be canceled
     */
    private void cancel(Content content) {
        EventBus.getDefault().post(new DownloadEvent(content, DownloadEvent.EV_CANCEL));

        compositeDisposable.add(
                Completable.fromRunnable(() -> doCancel(content.getId()))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(() -> remove(content))); // Remove the content from the in-memory list and the UI
    }

    private void doCancel(long contentId) {
        // Remove content altogether from the DB (including queue)
        ObjectBoxDB db = ObjectBoxDB.getInstance(requireContext());
        Content content = db.selectContentById(contentId);
        if (content != null) {
            db.deleteQueue(content);
            db.deleteContent(content);
            // Remove the content from the disk
            ContentHelper.removeContent(content);
        }
    }

    private void removeFromQueue(Content content) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(requireContext());
        // Remove content from the queue in the DB
        db.deleteQueue(content);
        // Remove the content from the in-memory list and the UI
        remove(content);
    }

    private void remove(Content content) {
        for (int i = 0; i < itemAdapter.getAdapterItemCount(); i++) {
            Content c = itemAdapter.getAdapterItem(i).getContent();
            if (c.getId() == content.getId()) {
                itemAdapter.remove(i);
                break;
            }
        }
    }
}
