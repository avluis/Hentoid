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
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.AsyncDifferConfig;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.listeners.ClickEventHook;
import com.mikepenz.fastadapter.paged.PagedModelAdapter;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;

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
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.viewholders.ContentItem;
import me.devsaki.hentoid.viewmodels.QueueViewModel;
import me.devsaki.hentoid.views.CircularProgressView;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;

/**
 * Created by avluis on 04/10/2016.
 * Presents the list of works currently downloading to the user.
 */
public class QueueFragment extends Fragment {

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    // COMMUNICATION
    // Viewmodel
    private QueueViewModel viewModel;

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

    private LinearLayoutManager llm;

    // State
    private boolean isPreparingDownload = false;
    private boolean isPaused = false;
    private boolean isEmpty = false;


    /**
     * Diff calculation rules for list items
     * <p>
     * Created once and for all to be used by FastAdapter in endless mode (=using Android PagedList)
     */
    private final AsyncDifferConfig<QueueRecord> asyncDifferConfig = new AsyncDifferConfig.Builder<>(new DiffUtil.ItemCallback<QueueRecord>() {
        @Override
        public boolean areItemsTheSame(@NonNull QueueRecord oldItem, @NonNull QueueRecord newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull QueueRecord oldItem, @NonNull QueueRecord newItem) {
            return oldItem.rank == newItem.rank;
        }
    }).build();

    private final PagedModelAdapter<QueueRecord, ContentItem> itemAdapter = new PagedModelAdapter<>(asyncDifferConfig, i -> new ContentItem(true), ContentItem::new);


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

        // Book list container
        RecyclerView recyclerView = requireViewById(rootView, R.id.queue_list);

        FastAdapter<ContentItem> fastAdapter = FastAdapter.with(itemAdapter);
        fastAdapter.setHasStableIds(true);
        fastAdapter.registerTypeInstance(new ContentItem(true));
        recyclerView.setAdapter(fastAdapter);

        llm = (LinearLayoutManager)recyclerView.getLayoutManager();

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
                viewModel.moveUp(item.getContent().getId());
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
                viewModel.moveTop(item.getContent().getId());
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
                viewModel.moveDown(item.getContent().getId());
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
                viewModel.cancel(item.getContent());
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

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(QueueViewModel.class);
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

    private void onQueueChanged(PagedList<QueueRecord> result) {
        Timber.i(">>Queue changed ! Size=%s", result.size());
        isEmpty = (0 == result.size());
        isPaused = (!isEmpty && (ContentQueueManager.getInstance().isQueuePaused() || !ContentQueueManager.getInstance().isQueueActive()));

        // Update toolbar
        toolbar.setTitle(getResources().getQuantityString(R.plurals.queue_book_count, result.size(), result.size()));

        // Update list visibility
        mEmptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);

        // Update displayed books
        itemAdapter.submitList(result);

        updateUI();
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
        if (itemAdapter.getAdapterItemCount() > 0 && itemAdapter.getAdapterItem(0).getContent() != null)
            ErrorStatsDialogFragment.invoke(this, itemAdapter.getAdapterItem(0).getContent().getId());
    }

    private void updateProgressFirstItem(boolean isPausedevent) {
        if (itemAdapter.getAdapterItemCount() > 0 && llm != null && 0 == llm.findFirstVisibleItemPosition()) {
            Content content = itemAdapter.getAdapterItem(0).getContent();
            if (null == content) return;

            // Hack to update the progress bar of the 1st visible card even though it is controlled by the PagedList
            ContentItem.ContentViewHolder.updateProgress(content, requireViewById(rootView, R.id.pbDownload), 0, isPausedevent);
        }
    }
}
