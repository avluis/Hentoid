package me.devsaki.hentoid.fragments.queue;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;
import com.annimon.stream.function.BiConsumer;
import com.annimon.stream.function.Consumer;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.drag.ItemTouchCallback;
import com.mikepenz.fastadapter.drag.SimpleDragCallback;
import com.mikepenz.fastadapter.listeners.ClickEventHook;
import com.mikepenz.fastadapter.select.SelectExtension;
import com.mikepenz.fastadapter.swipe.SimpleSwipeCallback;
import com.mikepenz.fastadapter.swipe_drag.SimpleSwipeDragCallback;
import com.mikepenz.fastadapter.utils.DragDropUtil;
import com.skydoves.balloon.ArrowOrientation;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.PrefsActivity;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.activities.bundles.PrefsActivityBundle;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.events.DownloadPreparationEvent;
import me.devsaki.hentoid.events.ServiceDestroyedEvent;
import me.devsaki.hentoid.services.ContentQueueManager;
import me.devsaki.hentoid.ui.BlinkAnimation;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Debouncer;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.util.TooltipUtil;
import me.devsaki.hentoid.util.exception.ContentNotRemovedException;
import me.devsaki.hentoid.util.network.DownloadSpeedCalculator;
import me.devsaki.hentoid.util.network.NetworkHelper;
import me.devsaki.hentoid.viewholders.ContentItem;
import me.devsaki.hentoid.viewholders.IDraggableViewHolder;
import me.devsaki.hentoid.viewmodels.QueueViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import me.devsaki.hentoid.views.CircularProgressView;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;

/**
 * Created by avluis on 04/10/2016.
 * Presents the list of works currently downloading to the user.
 */
public class QueueFragment extends Fragment implements ItemTouchCallback, SimpleSwipeCallback.ItemSwipeCallback {

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    // COMMUNICATION
    // Viewmodel
    private QueueViewModel viewModel;

    // UI ELEMENTS
    private View rootView;
    private Toolbar selectionToolbar;
    private MenuItem errorStatsMenu;    // Toolbar menu item for error stats
    private RecyclerView recyclerView;  // Queued book list
    private LinearLayoutManager llm;
    private TextView mEmptyText;        // "Empty queue" message panel
    private ImageButton btnStart;       // Start / Resume button
    private ImageButton btnPause;       // Pause button
    private TextView queueStatus;       // 1st line of text displayed on the right of the queue pause / play button
    private TextView queueInfo;         // 2nd line of text displayed on the right of the queue pause / play button
    private CircularProgressView dlPreparationProgressBar; // Circular progress bar for downloads preparation

    // == FASTADAPTER COMPONENTS AND HELPERS
    private FastAdapter<ContentItem> fastAdapter;
    private SelectExtension<ContentItem> selectExtension;
    private ItemTouchHelper touchHelper;

    // Download speed calculator
    private final DownloadSpeedCalculator downloadSpeedCalulator = new DownloadSpeedCalculator();

    // State
    private boolean isPreparingDownload = false;
    private boolean isPaused = false;
    private boolean isEmpty = false;

    // === VARIABLES
    // Used to ignore native calls to onBookClick right after that book has been deselected
    private boolean invalidateNextBookClick = false;
    // Used to show a given item at first display
    private long contentIdToDisplayFirst = -1;

    // Used to start processing when the recyclerView has finished updating
    private final Debouncer<Integer> listRefreshDebouncer = new Debouncer<>(75, this::onRecyclerUpdated);
    private int itemToRefreshIndex = -1;

    // Used to keep scroll position when moving items
    // https://stackoverflow.com/questions/27992427/recyclerview-adapter-notifyitemmoved0-1-scrolls-screen
    private int topItemPosition = -1;
    private int offsetTop = 0;


    // Use a non-paged model adapter; drag & drop doesn't work with paged content, as Adapter.move is not supported and move from DB refreshes the whole list
    private final ItemAdapter<ContentItem> itemAdapter = new ItemAdapter<>();


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (selectExtension != null) selectExtension.deselect();
        initSelectionToolbar();
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
        queueStatus = requireViewById(rootView, R.id.queueStatus);
        queueInfo = requireViewById(rootView, R.id.queueInfo);
        dlPreparationProgressBar = requireViewById(rootView, R.id.queueDownloadPreparationProgressBar);

        // Both queue control buttons actually just need to send a signal that will be processed accordingly by whom it may concern
        btnStart.setOnClickListener(v -> EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_UNPAUSE)));
        btnPause.setOnClickListener(v -> EventBus.getDefault().post(new DownloadEvent(DownloadEvent.EV_PAUSE)));

        // Book list
        recyclerView = requireViewById(rootView, R.id.queue_list);

        fastAdapter = FastAdapter.with(itemAdapter);
        fastAdapter.setHasStableIds(true);
        ContentItem item = new ContentItem(ContentItem.ViewType.QUEUE);
        fastAdapter.registerItemFactory(item.getType(), item);

        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        selectExtension = fastAdapter.getOrCreateExtension(SelectExtension.class);
        if (selectExtension != null) {
            selectExtension.setSelectable(true);
            selectExtension.setMultiSelect(true);
            selectExtension.setSelectOnLongClick(true);
            selectExtension.setSelectionListener((i, b) -> this.onSelectionChanged());
        }

        recyclerView.setAdapter(fastAdapter);

        recyclerView.setHasFixedSize(true);

        llm = (LinearLayoutManager) recyclerView.getLayoutManager();

        // Fast scroller
        new FastScrollerBuilder(recyclerView).build();

        // Drag, drop & swiping
        SimpleDragCallback dragSwipeCallback = new SimpleSwipeDragCallback(
                this,
                this,
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_action_delete_forever)).withSensitivity(10f).withSurfaceThreshold(0.75f);
        dragSwipeCallback.setNotifyAllDrops(true);
        dragSwipeCallback.setIsDragEnabled(false); // Despite its name, that's actually to disable drag on long tap

        touchHelper = new ItemTouchHelper(dragSwipeCallback);
        touchHelper.attachToRecyclerView(recyclerView);

        // Item click listener
        fastAdapter.setOnClickListener((v, a, i, p) -> onBookClick(i));

        initToolbar();
        initSelectionToolbar();
        attachButtons(fastAdapter);

        // Network usage display refresh
        compositeDisposable.add(Observable.timer(1, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.computation())
                .repeat()
                .observeOn(Schedulers.computation())
                .map(v -> NetworkHelper.getIncomingNetworkUsage(requireContext()))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::updateNetworkUsage));

        return rootView;
    }

    private void initToolbar() {
        if (!(requireActivity() instanceof QueueActivity)) return;
        QueueActivity activity = (QueueActivity) requireActivity();

        MenuItem cancelAllMenu = activity.getToolbar().getMenu().findItem(R.id.action_cancel_all);
        cancelAllMenu.setOnMenuItemClickListener(item -> {
            // Don't do anything if the queue is empty
            if (0 == itemAdapter.getAdapterItemCount()) return true;
                // Just do it if the queue has a single item
            else if (1 == itemAdapter.getAdapterItemCount()) onCancelAll();
                // Ask if there's more than 1 item
            else
                new MaterialAlertDialogBuilder(requireContext(), ThemeHelper.getIdForCurrentTheme(requireContext(), R.style.Theme_Light_Dialog))
                        .setIcon(R.drawable.ic_warning)
                        .setCancelable(false)
                        .setTitle(R.string.app_name)
                        .setMessage(R.string.confirm_cancel_all)
                        .setPositiveButton(R.string.yes,
                                (dialog1, which) -> {
                                    dialog1.dismiss();
                                    onCancelAll();
                                })
                        .setNegativeButton(R.string.no,
                                (dialog12, which) -> dialog12.dismiss())
                        .create()
                        .show();
            return true;
        });
        MenuItem settingsMenu = activity.getToolbar().getMenu().findItem(R.id.action_queue_prefs);
        settingsMenu.setOnMenuItemClickListener(item -> {
            onSettingsClick();
            return true;
        });
        MenuItem invertMenu = activity.getToolbar().getMenu().findItem(R.id.action_invert_queue);
        invertMenu.setOnMenuItemClickListener(item -> {
            viewModel.invertQueue();
            return true;
        });
        errorStatsMenu = activity.getToolbar().getMenu().findItem(R.id.action_error_stats);
        errorStatsMenu.setOnMenuItemClickListener(item -> {
            showErrorStats();
            return true;
        });
    }

    // Process the move command while keeping scroll position in memory
    // https://stackoverflow.com/questions/27992427/recyclerview-adapter-notifyitemmoved0-1-scrolls-screen
    private void processMove(int from, int to, @NonNull BiConsumer<Integer, Integer> consumer) {
        topItemPosition = getTopItemPosition();
        offsetTop = 0;
        if (topItemPosition >= 0) {
            View firstView = llm.findViewByPosition(topItemPosition);
            if (firstView != null)
                offsetTop = llm.getDecoratedTop(firstView) - llm.getTopDecorationHeight(firstView);
        }
        consumer.accept(from, to);
        recordMoveFromFirstPos(from, to);
    }

    private void processMove(List<Integer> positions, @NonNull Consumer<List<Integer>> consumer) {
        topItemPosition = getTopItemPosition();
        offsetTop = 0;
        if (topItemPosition >= 0) {
            View firstView = llm.findViewByPosition(topItemPosition);
            if (firstView != null)
                offsetTop = llm.getDecoratedTop(firstView) - llm.getTopDecorationHeight(firstView);
        }
        consumer.accept(positions);
        recordMoveFromFirstPos(positions);
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
                processMove(i, 0, viewModel::move);
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
                processMove(i, fastAdapter.getItemCount() - 1, viewModel::move);
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
        viewModel = new ViewModelProvider(requireActivity(), vmFactory).get(QueueViewModel.class);
        viewModel.getQueue().observe(getViewLifecycleOwner(), this::onQueueChanged);
        viewModel.getContentIdToShowFirst().observe(getViewLifecycleOwner(), this::onContentIdToShowFirstChanged);
    }

    /**
     * Download event handler
     *
     * @param event Broadcasted event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDownloadEvent(DownloadEvent event) {

        Timber.d("Event received : %s", event.eventType);
        errorStatsMenu.setVisible(event.pagesKO > 0);

        // Display motive, if any
        if (event.motive != DownloadEvent.Motive.NONE) {
            String motiveMsg = "";
            if (event.motive == DownloadEvent.Motive.NO_INTERNET)
                motiveMsg = getString(R.string.paused_no_internet);
            else if (event.motive == DownloadEvent.Motive.NO_WIFI)
                motiveMsg = getString(R.string.paused_no_wifi);
            else if (event.motive == DownloadEvent.Motive.NO_STORAGE)
                motiveMsg = getString(R.string.paused_no_storage);
            Snackbar.make(recyclerView, motiveMsg, BaseTransientBottomBar.LENGTH_SHORT).show();
        }

        switch (event.eventType) {
            case DownloadEvent.EV_PROGRESS:
                updateProgress(event.pagesOK, event.pagesKO, event.pagesTotal, event.getNumberRetries(), event.downloadedSizeB);
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
                queueInfo.setText("");
                dlPreparationProgressBar.setVisibility(View.GONE);
                break;
            case DownloadEvent.EV_COMPLETE:
                dlPreparationProgressBar.setVisibility(View.GONE);
                if (0 == itemAdapter.getAdapterItemCount()) errorStatsMenu.setVisible(false);
                update(event.eventType);
                break;
            default: // EV_PAUSE, EV_CANCEL
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
            updateProgressFirstItem(false);
        } else if (dlPreparationProgressBar.isShown() && event.isCompleted()) {
            dlPreparationProgressBar.setVisibility(View.GONE);
        }

        dlPreparationProgressBar.setProgress1((long) event.total - event.done);
    }

    /**
     * Service destroyed event handler
     *
     * @param event Broadcasted event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServiceDestroyed(ServiceDestroyedEvent event) {
        if (event.service != ServiceDestroyedEvent.Service.DOWNLOAD) return;

        isPaused = true;
        updateProgressFirstItem(true);
        updateControlBar();
    }

    /**
     * Update main progress bar and bottom progress panel for current (1st in queue) book
     *
     * @param pagesOK         Number of pages successfully downloaded for current (1st in queue) book
     * @param pagesKO         Number of pages whose download has failed for current (1st in queue) book
     * @param totalPages      Total pages of current (1st in queue) book
     * @param numberRetries   Current number of download auto-retries for current (1st in queue) book
     * @param downloadedSizeB Current size of downloaded content (in bytes)
     */
    private void updateProgress(final int pagesOK, final int pagesKO, final int totalPages, final int numberRetries, final long downloadedSizeB) {
        if (!ContentQueueManager.getInstance().isQueuePaused() && itemAdapter.getAdapterItemCount() > 0) {
            Content content = itemAdapter.getAdapterItem(0).getContent();

            // Pages download has started
            if (content != null && pagesKO + pagesOK > 1) {
                // Downloader reports about the cover thumbnail too
                // Display one less page to avoid confusing the user
                int totalPagesDisplay = Math.max(0, totalPages - 1);
                int pagesOKDisplay = Math.max(0, pagesOK - 1);

                // Update book progress bar
                Timber.d(">> setProgress %s", pagesOKDisplay + pagesKO);
                content.setProgress((long) pagesOKDisplay + pagesKO);
                content.setDownloadedBytes(downloadedSizeB);
                content.setQtyPages(totalPagesDisplay);
                updateProgressFirstItem(false);

                // Update information bar
                StringBuilder message = new StringBuilder();
                String processedPagesFmt = Helper.formatIntAsStr(pagesOKDisplay, String.valueOf(totalPagesDisplay).length());
                message.append(processedPagesFmt).append("/").append(totalPagesDisplay).append(" processed");
                if (pagesKO > 0)
                    message.append(" (").append(pagesKO).append(" errors)");
                if (numberRetries > 0)
                    message.append(" [ retry").append(numberRetries).append("/").append(Preferences.getDlRetriesNumber()).append("]");
                int avgSpeedKbps = (int) downloadSpeedCalulator.getAvgSpeedKbps();
                if (avgSpeedKbps > 0)
                    message.append(String.format(Locale.US, " @ %d KBps", avgSpeedKbps));

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

        queueStatus.setText(getResources().getString(R.string.queue_dl, content.getTitle()));
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
        updateControlBar();
    }

    private void onQueueChanged(List<QueueRecord> result) {
        Timber.d(">>Queue changed ! Size=%s", result.size());
        isEmpty = (result.isEmpty());
        isPaused = (!isEmpty && (ContentQueueManager.getInstance().isQueuePaused() || !ContentQueueManager.getInstance().isQueueActive()));

        // Update list visibility
        mEmptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);

        // Update displayed books
        List<ContentItem> content = Stream.of(result).map(c -> new ContentItem(c, touchHelper)).toList();
        // When using mass-moving (select multiple + move up/down), diff calculations ignored certain items
        // and desynch the "real" list from the one manipulated by selectExtension
        // => use a plain ItemAdapter.set for now (and live with the occasional blinking)
//        FastAdapterDiffUtil.INSTANCE.set(itemAdapter, content);
        itemAdapter.set(content);
        differEndCallback();

        updateControlBar();

        // Signal swipe-to-cancel though a tooltip
        if (!isEmpty)
            TooltipUtil.showTooltip(requireContext(), R.string.help_swipe_cancel, ArrowOrientation.BOTTOM, recyclerView, getViewLifecycleOwner());
    }

    private void onContentIdToShowFirstChanged(Long contentId) {
        Timber.d(">>onContentIdToShowFirstChanged %s", contentId);
        contentIdToDisplayFirst = contentId;
    }

    /**
     * Callback for the end of item diff calculations
     * Activated when all _adapter_ items are placed on their definitive position
     */
    private void differEndCallback() {
        if (contentIdToDisplayFirst > -1) {
            int targetPos = fastAdapter.getPosition(contentIdToDisplayFirst);
            if (targetPos > -1) listRefreshDebouncer.submit(targetPos);
            contentIdToDisplayFirst = -1;
            return;
        }
        // Reposition the list on the initial top item position
        if (topItemPosition >= 0) {
            int targetPos = topItemPosition;
            listRefreshDebouncer.submit(targetPos);
            topItemPosition = -1;
        }
        // Refresh the item that moved from the 1st position
        if (itemToRefreshIndex > -1) {
            fastAdapter.notifyAdapterItemChanged(itemToRefreshIndex);
            itemToRefreshIndex = -1;
        }
    }

    /**
     * Callback for the end of recycler updates
     * Activated when all _displayed_ items are placed on their definitive position
     */
    private void onRecyclerUpdated(int topItemPosition) {
        llm.scrollToPositionWithOffset(topItemPosition, offsetTop); // Used to restore position after activity has been stopped and recreated
    }

    private void updateControlBar() {
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

            if (isEmpty) {
                btnStart.setVisibility(View.GONE);
                errorStatsMenu.setVisible(false);
                queueStatus.setText("");
            } else if (isPaused) {
                btnStart.setVisibility(View.VISIBLE);
                queueStatus.setText(R.string.queue_paused);

                // Set blinking animation when queue is paused
                BlinkAnimation animation = new BlinkAnimation(750, 20);
                queueStatus.startAnimation(animation);
                queueInfo.startAnimation(animation);
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

            // Hack to update the 1st visible card even though it is controlled by the PagedList
            ContentItem.ContentViewHolder.updateProgress(content, requireViewById(rootView, R.id.item_card), 0, isPausedevent);
        }
    }

    private boolean onBookClick(ContentItem item) {
        if (null == selectExtension || selectExtension.getSelectedItems().isEmpty()) {
            Content c = item.getContent();
            if (!invalidateNextBookClick) {
                // Process the click
                if (null == c) {
                    ToastUtil.toast(R.string.err_no_content);
                    return false;
                }
                // Retrieve the latest version of the content if storage URI is unknown
                // (may happen when the item is fetched before it is processed by the downloader)
                if (c.getStorageUri().isEmpty())
                    c = new ObjectBoxDAO(requireContext()).selectContent(c.getId());

                if (c != null) {
                    if (!ContentHelper.openHentoidViewer(requireContext(), c, null))
                        ToastUtil.toast(R.string.err_no_content);
                    return true;
                } else return false;
            } else invalidateNextBookClick = false;

            return true;
        } else {
            selectExtension.setSelectOnLongClick(false);
        }
        return false;
    }

    private void onCancelBook(@NonNull Content c) {
        viewModel.cancel(Stream.of(c).toList(), this::onDeleteError);
    }

    private void onCancelBooks(@NonNull List<Content> c) {
        viewModel.cancel(c, this::onDeleteError);
    }

    private void onCancelAll() {
        viewModel.cancelAll(this::onDeleteError);
    }

    /**
     * Callback for the failure of the "delete item" action
     */
    private void onDeleteError(Throwable t) {
        Timber.e(t);
        if (t instanceof ContentNotRemovedException) {
            ContentNotRemovedException e = (ContentNotRemovedException) t;
            String message = (null == e.getMessage()) ? "Content removal failed" : e.getMessage();
            Snackbar.make(recyclerView, message, BaseTransientBottomBar.LENGTH_LONG).show();
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
        recordMoveFromFirstPos(oldPosition, newPosition);
        return true;
    }

    @Override
    public void itemTouchDropped(int oldPosition, int newPosition) {
        // Save final position of item in DB
        viewModel.move(oldPosition, newPosition);
        recordMoveFromFirstPos(oldPosition, newPosition);

        // Delay execution of findViewHolderForAdapterPosition to give time for the new layout to
        // be calculated (if not, it might return null under certain circumstances)
        new Handler().postDelayed(() -> {
            RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(newPosition);
            if (vh instanceof IDraggableViewHolder) {
                ((IDraggableViewHolder) vh).onDropped();
            }
        }, 75);
    }

    @Override
    public void itemSwiped(int position, int direction) {
        ContentItem item = itemAdapter.getAdapterItem(position);
        item.setSwipeDirection(direction);

        if (item.getContent() != null) {
            Debouncer<Content> cancelDebouncer = new Debouncer<>(2000, this::onCancelBook);
            cancelDebouncer.submit(item.getContent());

            Runnable cancelSwipe = () -> {
                cancelDebouncer.clear();
                item.setSwipeDirection(0);
                int position1 = itemAdapter.getAdapterPosition(item);
                if (position1 != RecyclerView.NO_POSITION)
                    fastAdapter.notifyItemChanged(position1);
            };
            item.setUndoSwipeAction(cancelSwipe);
            fastAdapter.notifyItemChanged(position);
        }
    }

    @Override
    public void itemTouchStartDrag(RecyclerView.@NotNull ViewHolder viewHolder) {
        if (viewHolder instanceof IDraggableViewHolder) {
            ((IDraggableViewHolder) viewHolder).onDragged();
        }
    }

    /**
     * Show the viewer settings dialog
     */
    private void onSettingsClick() {
        Intent intent = new Intent(requireActivity(), PrefsActivity.class);

        PrefsActivityBundle.Builder builder = new PrefsActivityBundle.Builder();
        builder.setIsDownloaderPrefs(true);
        intent.putExtras(builder.getBundle());

        requireContext().startActivity(intent);
    }

    private void updateNetworkUsage(long bytesReceived) {
        downloadSpeedCalulator.addSampleNow(bytesReceived);
    }

    private void recordMoveFromFirstPos(int from, int to) {
        if (0 == from) itemToRefreshIndex = to;
    }

    private void recordMoveFromFirstPos(List<Integer> positions) {
        // Only useful when moving the 1st item to the bottom
        if (!positions.isEmpty() && 0 == positions.get(0))
            itemToRefreshIndex = itemAdapter.getAdapterItemCount() - positions.size();
    }

    private void initSelectionToolbar() {
        if (!(requireActivity() instanceof QueueActivity)) return;
        QueueActivity activity = (QueueActivity) requireActivity();

        selectionToolbar = activity.getSelectionToolbar();
        selectionToolbar.setNavigationOnClickListener(v -> {
            selectExtension.deselect();
            selectionToolbar.setVisibility(View.GONE);
        });
        selectionToolbar.setOnMenuItemClickListener(this::onSelectionMenuItemClicked);
    }

    private boolean onSelectionMenuItemClicked(@NonNull MenuItem menuItem) {
        Set<ContentItem> selectedItems = selectExtension.getSelectedItems();
        List<Integer> selectedPositions;
        boolean exitSelection = false;

        switch (menuItem.getItemId()) {
            case R.id.action_select_queue_cancel:
                List<Content> selectedContent = Stream.of(selectedItems).map(ContentItem::getContent).withoutNulls().toList();
                if (!selectedContent.isEmpty()) askDeleteSelected(selectedContent);
                break;
            case R.id.action_select_queue_top:
                selectedPositions = Stream.of(selectedItems).map(i -> fastAdapter.getPosition(i)).sorted().toList();
                selectExtension.deselect();
                if (!selectedPositions.isEmpty())
                    processMove(selectedPositions, viewModel::moveTop);
                exitSelection = true;
                break;
            case R.id.action_select_queue_bottom:
                selectedPositions = Stream.of(selectedItems).map(i -> fastAdapter.getPosition(i)).sorted().toList();
                selectExtension.deselect();
                if (!selectedPositions.isEmpty())
                    processMove(selectedPositions, viewModel::moveBottom);
                exitSelection = true;
                break;
            default:
                // Nothing here
        }
        if (exitSelection)
            selectionToolbar.setVisibility(View.GONE);
        return true;
    }

    private void updateSelectionToolbar(long selectedCount) {
        selectionToolbar.setTitle(getResources().getQuantityString(R.plurals.items_selected, (int) selectedCount, (int) selectedCount));
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     */
    private void onSelectionChanged() {
        int selectedCount = selectExtension.getSelectedItems().size();

        if (0 == selectedCount) {
            selectionToolbar.setVisibility(View.GONE);
            selectExtension.setSelectOnLongClick(true);
            invalidateNextBookClick = true;
            new Handler().postDelayed(() -> invalidateNextBookClick = false, 200);
        } else {
            updateSelectionToolbar(selectedCount);
            selectionToolbar.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Display the yes/no dialog to make sure the user really wants to delete selected items
     *
     * @param items Items to be deleted if the answer is yes
     */
    private void askDeleteSelected(@NonNull final List<Content> items) {
        Context context = getActivity();
        if (null == context) return;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        String title = context.getResources().getQuantityString(R.plurals.ask_cancel_multiple, items.size());
        builder.setMessage(title)
                .setPositiveButton(android.R.string.yes,
                        (dialog, which) -> {
                            selectExtension.deselect();
                            onCancelBooks(items);
                        })
                .setNegativeButton(android.R.string.no,
                        (dialog, which) -> selectExtension.deselect())
                .create().show();
    }
}
