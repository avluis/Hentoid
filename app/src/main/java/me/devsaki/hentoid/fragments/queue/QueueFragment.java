package me.devsaki.hentoid.fragments.queue;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;
import com.annimon.stream.function.LongConsumer;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil;
import com.mikepenz.fastadapter.drag.ItemTouchCallback;
import com.mikepenz.fastadapter.drag.SimpleDragCallback;
import com.mikepenz.fastadapter.listeners.ClickEventHook;
import com.mikepenz.fastadapter.swipe.SimpleSwipeCallback;
import com.mikepenz.fastadapter.swipe_drag.SimpleSwipeDragCallback;
import com.mikepenz.fastadapter.utils.DragDropUtil;
import com.pluscubed.recyclerfastscroll.RecyclerFastScroller;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.util.List;

import io.reactivex.disposables.CompositeDisposable;
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
import me.devsaki.hentoid.util.Debouncer;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.viewholders.ContentItem;
import me.devsaki.hentoid.viewholders.IDraggableViewHolder;
import me.devsaki.hentoid.viewmodels.QueueViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import me.devsaki.hentoid.views.CircularProgressView;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;

/**
 * Created by avluis on 04/10/2016.
 * Presents the list of works currently downloading to the user.
 */
// TODO cancel all
// TODO hold-to-confirm or ask for confirmation when using delete item
public class QueueFragment extends Fragment implements ItemTouchCallback, SimpleSwipeCallback.ItemSwipeCallback {

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    // COMMUNICATION
    // Viewmodel
    private QueueViewModel viewModel;

    // UI ELEMENTS
    private View rootView;
    private RecyclerView recyclerView;  // Queued book list
    private TextView mEmptyText;        // "Empty queue" message panel
    private ImageButton btnStart;       // Start / Resume button
    private ImageButton btnPause;       // Pause button
    private ImageButton btnStats;       // Error statistics button
    private TextView queueStatus;       // 1st line of text displayed on the right of the queue pause / play button
    private TextView queueInfo;         // 2nd line of text displayed on the right of the queue pause / play button
    private CircularProgressView dlPreparationProgressBar; // Circular progress bar for downloads preparation

    // Used to keep scroll position when moving items
    // https://stackoverflow.com/questions/27992427/recyclerview-adapter-notifyitemmoved0-1-scrolls-screen
    private int topItemPosition = -1;
    private int offsetTop = 0;

    // RecyclerView utils
    private LinearLayoutManager llm;
    private ItemTouchHelper touchHelper;

    // Used to start processing when the recyclerView has finished updating
    private final Debouncer<Integer> listRefreshDebouncer = new Debouncer<>(75, this::onRecyclerUpdated);

    // Used to effectively cancel a download when the user hasn't hit UNDO
    private FastAdapter<ContentItem> fastAdapter;
    private final Debouncer<Integer> cancelDebouncer = new Debouncer<>(2000, this::onBookCancel);


    // State
    private boolean isPreparingDownload = false;
    private boolean isPaused = false;
    private boolean isEmpty = false;


    // Use a non-pages model adapter; drag & drop doesn't work with paged content, as Adapter.move is not supported and move from DB refreshes the whole list
    private final ItemAdapter<ContentItem> itemAdapter = new ItemAdapter<>();


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

        mEmptyText = requireViewById(rootView, R.id.queue_empty_txt);

        btnStart = requireViewById(rootView, R.id.btnStart);
        btnPause = requireViewById(rootView, R.id.btnPause);
        btnStats = requireViewById(rootView, R.id.btnStats);
        queueStatus = requireViewById(rootView, R.id.queueStatus);
        queueInfo = requireViewById(rootView, R.id.queueInfo);
        dlPreparationProgressBar = requireViewById(rootView, R.id.queueDownloadPreparationProgressBar);

        // Both queue control buttons actually just need to send a signal that will be processed accordingly by whom it may concern
        btnStart.setOnClickListener(v -> EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_UNPAUSE)));
        btnPause.setOnClickListener(v -> EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_PAUSE)));
        btnStats.setOnClickListener(v -> showErrorStats());

        // Book list
        recyclerView = requireViewById(rootView, R.id.queue_list);

        fastAdapter = FastAdapter.with(itemAdapter);
        fastAdapter.setHasStableIds(true);
        ContentItem item = new ContentItem(ContentItem.ViewType.QUEUE);
        fastAdapter.registerItemFactory(item.getType(), item);
        recyclerView.setAdapter(fastAdapter);

        recyclerView.setHasFixedSize(true);

        llm = (LinearLayoutManager) recyclerView.getLayoutManager();

        // Fast scroller
        RecyclerFastScroller fastScroller = requireViewById(rootView, R.id.queue_list_fastscroller);
        fastScroller.attachRecyclerView(recyclerView);

        // Drag, drop & swiping
        SimpleDragCallback dragCallback = new SimpleSwipeDragCallback(
                this,
                this,
                requireContext().getDrawable(R.drawable.ic_action_delete_forever));
        dragCallback.setIsDragEnabled(false);

        touchHelper = new ItemTouchHelper(dragCallback);
        touchHelper.attachToRecyclerView(recyclerView);

        // Item click listener
        fastAdapter.setOnClickListener((v, a, i, p) -> onBookClick(i));

        attachButtons(fastAdapter);

        return rootView;
    }

    // Process the move command while keeping scroll position in memory
    // https://stackoverflow.com/questions/27992427/recyclerview-adapter-notifyitemmoved0-1-scrolls-screen
    private void processMove(@NotNull ContentItem item, @NonNull LongConsumer consumer) {
        Content c = item.getContent();
        if (c != null) {
            topItemPosition = getTopItemPosition();
            offsetTop = 0;
            if (topItemPosition >= 0) {
                View firstView = llm.findViewByPosition(topItemPosition);
                if (firstView != null)
                    offsetTop = llm.getDecoratedTop(firstView) - llm.getTopDecorationHeight(firstView);
            }
            consumer.accept(c.getId());
        }
    }

    private void attachButtons(FastAdapter<ContentItem> fastAdapter) {
        // Site button
        fastAdapter.addEventHook(new ClickEventHook<ContentItem>() {
            @Override
            public void onClick(@NotNull View view, int i, @NotNull FastAdapter<ContentItem> fastAdapter, @NotNull ContentItem item) {
                Content c = item.getContent();
                if (c != null) ContentHelper.viewContentGalleryPage(view.getContext(), c);
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

        // Top button
        fastAdapter.addEventHook(new ClickEventHook<ContentItem>() {
            @Override
            public void onClick(@NotNull View view, int i, @NotNull FastAdapter<ContentItem> fastAdapter, @NotNull ContentItem item) {
                viewModel.move(i, 0);
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

        // Bottom button
        fastAdapter.addEventHook(new ClickEventHook<ContentItem>() {
            @Override
            public void onClick(@NotNull View view, int i, @NotNull FastAdapter<ContentItem> fastAdapter, @NotNull ContentItem item) {
                viewModel.move(i, fastAdapter.getItemCount() - 1);
            }

            @org.jetbrains.annotations.Nullable
            @Override
            public View onBind(RecyclerView.@NotNull ViewHolder viewHolder) {
                if (viewHolder instanceof ContentItem.ContentViewHolder) {
                    return ((ContentItem.ContentViewHolder) viewHolder).getBottomButton();
                }
                return super.onBind(viewHolder);
            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewModelFactory vmFactory = new ViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(this, vmFactory).get(QueueViewModel.class);
        viewModel.getQueuePaged().observe(getViewLifecycleOwner(), this::onQueueChanged);
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
                updateProgressFirstItem(false);
                update(event.eventType);
                break;
            case DownloadEvent.EV_SKIP:
                // Books switch / display handled directly by the adapter
                updateBookTitle();
                queueInfo.setText("");
                dlPreparationProgressBar.setVisibility(View.GONE);
                break;
            case DownloadEvent.EV_COMPLETE:
                dlPreparationProgressBar.setVisibility(View.GONE);
                if (0 == itemAdapter.getAdapterItemCount()) btnStats.setVisibility(View.GONE);
                update(event.eventType);
                break;
            default: // EV_PAUSE, EV_CANCEL events
                dlPreparationProgressBar.setVisibility(View.GONE);
                updateProgressFirstItem(true);
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

    /**
     * Update book title in bottom progress panel
     */
    private void updateBookTitle() {
        if (0 == itemAdapter.getAdapterItemCount()) return;
        Content content = itemAdapter.getAdapterItem(0).getContent();
        if (null == content) return;

        queueStatus.setText(MessageFormat.format(requireActivity().getString(R.string.queue_dl), content.getTitle()));
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
        updateUI();
    }

    private void onQueueChanged(List<QueueRecord> result) {
        Timber.i(">>Queue changed ! Size=%s", result.size());
        isEmpty = (result.isEmpty());
        isPaused = (!isEmpty && (ContentQueueManager.getInstance().isQueuePaused() || !ContentQueueManager.getInstance().isQueueActive()));

        // Update list visibility
        mEmptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);

        // Update displayed books
        List<ContentItem> content = Stream.of(result).map(c -> new ContentItem(c, touchHelper)).toList();
        FastAdapterDiffUtil.INSTANCE.set(itemAdapter, content);

        updateUI();
    }

    /**
     * Callback for the end of item diff calculations
     * Activated when all _adapter_ items are placed on their definitive position
     */
    private void differEndCallback() {
        if (topItemPosition >= 0) {
            int targetPos = topItemPosition;
            listRefreshDebouncer.submit(targetPos);
            topItemPosition = -1;
        }
    }

    /**
     * Callback for the end of recycler updates
     * Activated when all _displayed_ items are placed on their definitive position
     */
    private void onRecyclerUpdated(int topItemPosition) {
        llm.scrollToPositionWithOffset(topItemPosition, offsetTop); // Used to restore position after activity has been stopped and recreated
    }

    private void updateUI() {
        boolean isActive = (!isEmpty && !isPaused);

        Timber.d("Queue state : E/P/A > %s/%s/%s -- %s elements", isEmpty, isPaused, isActive, itemAdapter.getAdapterItemCount());

        // Update list visibility
        mEmptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);

        // Update control bar status
        queueInfo.setText(isPreparingDownload && !isEmpty ? R.string.queue_preparing : R.string.queue_empty2);

        if (isActive) {
            btnPause.setVisibility(View.VISIBLE);
            btnStart.setVisibility(View.GONE);
            updateBookTitle();

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
    }

    private void showErrorStats() {
        if (itemAdapter.getAdapterItemCount() > 0) {
            Content c = itemAdapter.getAdapterItem(0).getContent();
            if (c != null) ErrorStatsDialogFragment.invoke(this, c.getId());
        }
    }

    private void updateProgressFirstItem(boolean isPausedevent) {
        if (itemAdapter.getAdapterItemCount() > 0 && llm != null && 0 == llm.findFirstVisibleItemPosition()) {
            Content content = itemAdapter.getAdapterItem(0).getContent();
            if (null == content) return;

            // Hack to update the progress bar of the 1st visible card even though it is controlled by the PagedList
            ContentItem.ContentViewHolder.updateProgress(content, requireViewById(rootView, R.id.pbDownload), 0, isPausedevent);
        }
    }

    private boolean onBookClick(ContentItem i) {
        Content c = i.getContent();
        if (c != null) {
            // TODO test long queues to see if a memorization of the top position (as in Library screen) is necessary
            if (!ContentHelper.openHentoidViewer(requireContext(), c, null))
                ToastUtil.toast(R.string.err_no_content);
            return true;
        } else return false;
    }

    private void onBookCancel(int position) {
        Content c = itemAdapter.getAdapterItem(position).getContent();
        if (c != null) {
            viewModel.cancel(c);
        }
    }

    /**
     * Calculate the position of the top visible item of the book list
     *
     * @return position of the top visible item of the book list
     */
    private int getTopItemPosition() {
        return Math.max(llm.findFirstVisibleItemPosition(), llm.findFirstCompletelyVisibleItemPosition());
    }

    /**
     * DRAG, DROP & SWIPE METHODS
     */

    @Override
    public boolean itemTouchOnMove(int oldPosition, int newPosition) {
        DragDropUtil.onMove(itemAdapter, oldPosition, newPosition); // change position
        return true;
    }

    @Override
    public void itemTouchDropped(int oldPosition, int newPosition) {
        RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(newPosition);
        if (vh instanceof IDraggableViewHolder) {
            ((IDraggableViewHolder) vh).onDropped();
        }
        // Save final position of item in DB
        viewModel.move(oldPosition, newPosition);
    }

    // TODO wait for the next release of FastAdapter to handle that when using drag, drop & swipe
    private void onStartDrag(RecyclerView.ViewHolder vh) {
        if (vh instanceof IDraggableViewHolder) {
            ((IDraggableViewHolder) vh).onDragged();
        }
    }

    @Override
    public void itemSwiped(int position, int direction) {
        ContentItem item = itemAdapter.getAdapterItem(position);
        item.setSwipeDirection(direction);
        cancelDebouncer.submit(position);

        Runnable cancelSwipe = () -> {
            cancelDebouncer.clear();
            item.setSwipeDirection(0);
            int position1 = itemAdapter.getAdapterPosition(item);
            if (position1 != RecyclerView.NO_POSITION) {
                fastAdapter.notifyItemChanged(position1);
            }
        };
        item.setUndoSwipeAction(cancelSwipe);
        fastAdapter.notifyItemChanged(position);
    }
}
