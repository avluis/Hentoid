package me.devsaki.hentoid.fragments.queue;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
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
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil;
import com.mikepenz.fastadapter.drag.ItemTouchCallback;
import com.mikepenz.fastadapter.listeners.ClickEventHook;
import com.mikepenz.fastadapter.select.SelectExtension;
import com.mikepenz.fastadapter.swipe.SimpleSwipeDrawerCallback;
import com.mikepenz.fastadapter.swipe_drag.SimpleSwipeDrawerDragCallback;
import com.mikepenz.fastadapter.utils.DragDropUtil;
import com.skydoves.balloon.ArrowOrientation;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
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
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.events.DownloadPreparationEvent;
import me.devsaki.hentoid.events.ServiceDestroyedEvent;
import me.devsaki.hentoid.fragments.DeleteProgressDialogFragment;
import me.devsaki.hentoid.util.download.ContentQueueManager;
import me.devsaki.hentoid.ui.BlinkAnimation;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Debouncer;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.PermissionUtil;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.util.TooltipUtil;
import me.devsaki.hentoid.util.exception.ContentNotRemovedException;
import me.devsaki.hentoid.util.network.DownloadSpeedCalculator;
import me.devsaki.hentoid.util.network.NetworkHelper;
import me.devsaki.hentoid.viewholders.ContentItem;
import me.devsaki.hentoid.viewholders.IDraggableViewHolder;
import me.devsaki.hentoid.viewholders.ISwipeableViewHolder;
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
public class QueueFragment extends Fragment implements ItemTouchCallback, SimpleSwipeDrawerCallback.ItemSwipeCallback {

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    // COMMUNICATION
    // Viewmodel
    private QueueViewModel viewModel;
    // Activity
    private WeakReference<QueueActivity> activity;

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
    // Use a non-paged model adapter; drag & drop doesn't work with paged content, as Adapter.move is not supported and move from DB refreshes the whole list
    private final ItemAdapter<ContentItem> itemAdapter = new ItemAdapter<>();
    private final FastAdapter<ContentItem> fastAdapter = FastAdapter.with(itemAdapter);
    private SelectExtension<ContentItem> selectExtension;
    private ItemTouchHelper touchHelper;

    // Download speed calculator
    private final DownloadSpeedCalculator downloadSpeedCalculator = new DownloadSpeedCalculator();

    // State
    private boolean isPreparingDownload = false;
    private boolean isPaused = false;
    private boolean isEmpty = false;
    // Indicate if the fragment is currently canceling all items
    private boolean isCancelingAll = false;

    // === VARIABLES
    // Used to show a given item at first display
    private long contentHashToDisplayFirst = 0;

    // Used to start processing when the recyclerView has finished updating
    private Debouncer<Integer> listRefreshDebouncer;
    private int itemToRefreshIndex = -1;

    // Used to keep scroll position when moving items
    // https://stackoverflow.com/questions/27992427/recyclerview-adapter-notifyitemmoved0-1-scrolls-screen
    private int topItemPosition = -1;
    private int offsetTop = 0;


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (!(requireActivity() instanceof QueueActivity))
            throw new IllegalStateException("Parent activity has to be a LibraryActivity");
        activity = new WeakReference<>((QueueActivity) requireActivity());

        listRefreshDebouncer = new Debouncer<>(context, 75, this::onRecyclerUpdated);
    }

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

        ContentItem item = new ContentItem(ContentItem.ViewType.QUEUE);
        fastAdapter.registerItemFactory(item.getType(), item);

        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        selectExtension = fastAdapter.getOrCreateExtension(SelectExtension.class);
        if (selectExtension != null) {
            selectExtension.setSelectable(true);
            selectExtension.setMultiSelect(true);
            selectExtension.setSelectOnLongClick(true);
            selectExtension.setSelectWithItemUpdate(true);
            selectExtension.setSelectionListener((i, b) -> this.onSelectionChanged());
        }

        recyclerView.setAdapter(fastAdapter);
        recyclerView.setHasFixedSize(true);
        llm = (LinearLayoutManager) recyclerView.getLayoutManager();

        // Fast scroller
        new FastScrollerBuilder(recyclerView).build();

        // Drag, drop & swiping
        SimpleSwipeDrawerDragCallback dragSwipeCallback = new SimpleSwipeDrawerDragCallback(this, ItemTouchHelper.LEFT, this)
                .withSwipeLeft(Helper.dimensAsDp(requireContext(), R.dimen.delete_drawer_width_list))
                .withSensitivity(1.5f)
                .withSurfaceThreshold(0.3f)
                .withNotifyAllDrops(true);
        dragSwipeCallback.setIsDragEnabled(false); // Despite its name, that's actually to disable drag on long tap

        touchHelper = new ItemTouchHelper(dragSwipeCallback);
        touchHelper.attachToRecyclerView(recyclerView);

        // Item click listeners
        fastAdapter.setOnPreClickListener((v, a, i, p) -> {
            Set<Integer> selectedPositions = selectExtension.getSelections();
            if (0 == selectedPositions.size()) { // No selection -> normal click
                return false;
            } else { // Existing selection -> toggle selection
                if (selectedPositions.contains(p) && 1 == selectedPositions.size())
                    selectExtension.setSelectOnLongClick(true);
                selectExtension.toggleSelection(p);
                return true;
            }
        });
        fastAdapter.setOnClickListener((v, a, i, p) -> onItemClick(i));
        fastAdapter.setOnPreLongClickListener((v, a, i, p) -> {
            Set<Integer> selectedPositions = selectExtension.getSelections();
            if (0 == selectedPositions.size()) { // No selection -> select things
                selectExtension.select(p);
                selectExtension.setSelectOnLongClick(false);
                return true;
            }
            return false;
        });

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
            if (1 == itemAdapter.getAdapterItemCount()) onCancelAll();
            else // Ask if there's more than 1 item
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
        viewModel.getContentHashToShowFirst().observe(getViewLifecycleOwner(), this::onContentHashToShowFirstChanged);
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
        @StringRes int motiveMsg;
        switch (event.motive) {
            case DownloadEvent.Motive.NO_INTERNET:
                motiveMsg = R.string.paused_no_internet;
                break;
            case DownloadEvent.Motive.NO_WIFI:
                motiveMsg = R.string.paused_no_wifi;
                break;
            case DownloadEvent.Motive.NO_STORAGE:
                motiveMsg = R.string.paused_no_storage;
                break;
            case DownloadEvent.Motive.NO_DOWNLOAD_FOLDER:
                motiveMsg = R.string.paused_no_dl_folder;
                break;
            case DownloadEvent.Motive.DOWNLOAD_FOLDER_NOT_FOUND:
                motiveMsg = R.string.paused_dl_folder_not_found;
                break;
            case DownloadEvent.Motive.DOWNLOAD_FOLDER_NO_CREDENTIALS:
                motiveMsg = R.string.paused_dl_folder_credentials;
                PermissionUtil.requestExternalStorageReadWritePermission(getActivity(), PermissionUtil.RQST_STORAGE_PERMISSION);
                break;
            case DownloadEvent.Motive.NONE:
            default: // NONE
                motiveMsg = -1;
        }
        if (motiveMsg != -1)
            Snackbar.make(recyclerView, getString(motiveMsg), BaseTransientBottomBar.LENGTH_SHORT).show();

        switch (event.eventType) {
            case DownloadEvent.EV_PROGRESS:
                updateProgress(event.pagesOK, event.pagesKO, event.pagesTotal, event.getNumberRetries(), event.downloadedSizeB, false);
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
                // Don't update the UI if it is in the process of canceling all items
                if (isCancelingAll) return;
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
     * @param forceDisplay    True to force display even if the queue is paused
     */
    private void updateProgress(
            final int pagesOK,
            final int pagesKO,
            final int totalPages,
            final int numberRetries,
            final long downloadedSizeB,
            boolean forceDisplay) {
        if ((!ContentQueueManager.getInstance().isQueuePaused() || forceDisplay) && itemAdapter.getAdapterItemCount() > 0) {
            Content content = itemAdapter.getAdapterItem(0).getContent();

            // Pages download has started
            if (content != null && (pagesKO + pagesOK > 1 || forceDisplay)) {
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
                int avgSpeedKbps = (int) downloadSpeedCalculator.getAvgSpeedKbps();
                if (avgSpeedKbps > 0)
                    message.append(String.format(Locale.ENGLISH, " @ %d KBps", avgSpeedKbps));

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

        // Don't process changes while everything is being canceled, it usually kills the UI as too many changes are processed at the same time
        if (isCancelingAll && !isEmpty) return;

        // Update list visibility
        mEmptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);

        // Update displayed books
        List<ContentItem> contentItems = Stream.of(result).map(c -> new ContentItem(c, touchHelper, this::onCancelSwipedBook)).withoutNulls().distinct().toList();
        FastAdapterDiffUtil.INSTANCE.set(itemAdapter, contentItems);
        new Handler(Looper.getMainLooper()).postDelayed(this::differEndCallback, 150);
        updateControlBar();

        // Signal swipe-to-cancel though a tooltip
        if (!isEmpty)
            TooltipUtil.showTooltip(
                    requireContext(), R.string.help_swipe_cancel, ArrowOrientation.BOTTOM, recyclerView,
                    getViewLifecycleOwner());
    }

    private void onContentHashToShowFirstChanged(Integer contentHash) {
        Timber.d(">>onContentIdToShowFirstChanged %s", contentHash);
        contentHashToDisplayFirst = contentHash;
    }

    /**
     * Callback for the end of item diff calculations
     * Activated when all _adapter_ items are placed on their definitive position
     */
    private void differEndCallback() {
        if (contentHashToDisplayFirst != 0) {
            int targetPos = fastAdapter.getPosition(contentHashToDisplayFirst);
            if (targetPos > -1) listRefreshDebouncer.submit(targetPos);
            contentHashToDisplayFirst = 0;
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

    private boolean onItemClick(ContentItem item) {
        if (null == selectExtension || selectExtension.getSelections().isEmpty()) {
            Content c = item.getContent();
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
        }
        return false;
    }

    private void onCancelSwipedBook(@NonNull final ContentItem item) {
        // Deleted book is the last selected books => disable selection mode
        if (item.isSelected()) {
            selectExtension.deselect(item);
            if (selectExtension.getSelections().isEmpty())
                selectionToolbar.setVisibility(View.GONE);
        }

        viewModel.cancel(Stream.of(item.getContent()).toList(), this::onCancelError, this::onCancelComplete);
    }

    private void onCancelBooks(@NonNull List<Content> c) {
        if (c.size() > 2) {
            isCancelingAll = true;
            DeleteProgressDialogFragment.invoke(getParentFragmentManager(), getResources().getString(R.string.cancel_queue_progress));
        }
        viewModel.cancel(c, this::onCancelError, this::onCancelComplete);
    }

    private void onCancelAll() {
        isCancelingAll = true;
        DeleteProgressDialogFragment.invoke(getParentFragmentManager(), getResources().getString(R.string.cancel_queue_progress));
        viewModel.cancelAll(this::onCancelError, this::onCancelComplete);
    }

    private void onCancelComplete() {
        isCancelingAll = false;
        viewModel.refresh();
        if (null == selectExtension || selectExtension.getSelections().isEmpty())
            selectionToolbar.setVisibility(View.GONE);
    }

    /**
     * Callback for the failure of the "delete item" action
     */
    private void onCancelError(Throwable t) {
        Timber.e(t);
        isCancelingAll = false;
        viewModel.refresh();
        if (t instanceof ContentNotRemovedException) {
            String message = (null == t.getMessage()) ? "Content removal failed" : t.getMessage();
            Snackbar.make(recyclerView, message, BaseTransientBottomBar.LENGTH_LONG).show();
        }
        if (null == selectExtension || selectExtension.getSelections().isEmpty())
            selectionToolbar.setVisibility(View.GONE);
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

    private void recordMoveFromFirstPos(int from, int to) {
        if (0 == from) itemToRefreshIndex = to;
    }

    private void recordMoveFromFirstPos(List<Integer> positions) {
        // Only useful when moving the 1st item to the bottom
        if (!positions.isEmpty() && 0 == positions.get(0))
            itemToRefreshIndex = itemAdapter.getAdapterItemCount() - positions.size();
    }

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
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(newPosition);
            if (vh instanceof IDraggableViewHolder) {
                ((IDraggableViewHolder) vh).onDropped();
            }
        }, 75);
    }

    @Override
    public void itemTouchStartDrag(RecyclerView.@NotNull ViewHolder viewHolder) {
        if (viewHolder instanceof IDraggableViewHolder) {
            ((IDraggableViewHolder) viewHolder).onDragged();
        }
    }

    @Override
    public void itemSwiped(int position, int direction) {
        RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(position);
        if (vh instanceof ISwipeableViewHolder) {
            ((ISwipeableViewHolder) vh).onSwiped();
        }
    }

    @Override
    public void itemUnswiped(int position) {
        RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(position);
        if (vh instanceof ISwipeableViewHolder) {
            ((ISwipeableViewHolder) vh).onUnswiped();
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
        downloadSpeedCalculator.addSampleNow(bytesReceived);
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

    @SuppressLint("NonConstantResourceId")
    private boolean onSelectionMenuItemClicked(@NonNull MenuItem menuItem) {
        Set<ContentItem> selectedItems = selectExtension.getSelectedItems();
        List<Integer> selectedPositions;
        boolean keepToolbar = false;

        switch (menuItem.getItemId()) {
            case R.id.action_select_queue_cancel:
                List<Content> selectedContent = Stream.of(selectedItems).map(ContentItem::getContent).withoutNulls().toList();
                if (!selectedContent.isEmpty()) askDeleteSelected(selectedContent);
                break;
            case R.id.action_select_queue_top:
                selectedPositions = Stream.of(selectedItems).map(fastAdapter::getPosition).sorted().toList();
                selectExtension.deselect();
                if (!selectedPositions.isEmpty())
                    processMove(selectedPositions, viewModel::moveTop);
                break;
            case R.id.action_select_queue_bottom:
                selectedPositions = Stream.of(selectedItems).map(fastAdapter::getPosition).sorted().toList();
                selectExtension.deselect();
                if (!selectedPositions.isEmpty())
                    processMove(selectedPositions, viewModel::moveBottom);
                break;
            case R.id.action_download_scratch:
                askRedownloadSelectedScratch();
                keepToolbar = true;
                break;
            default:
                // Nothing here
        }
        if (!keepToolbar) selectionToolbar.setVisibility(View.GONE);

        return true;
    }

    private void updateSelectionToolbar(long selectedCount) {
        selectionToolbar.setTitle(getResources().getQuantityString(R.plurals.items_selected, (int) selectedCount, (int) selectedCount));
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     */
    private void onSelectionChanged() {
        int selectedCount = selectExtension.getSelections().size();

        if (0 == selectedCount) {
            selectionToolbar.setVisibility(View.GONE);
            selectExtension.setSelectOnLongClick(true);
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
                .setPositiveButton(R.string.yes,
                        (dialog, which) -> {
                            selectExtension.deselect();
                            onCancelBooks(items);
                        })
                .setNegativeButton(R.string.no,
                        (dialog, which) -> selectExtension.deselect())
                .setOnCancelListener(dialog -> selectExtension.deselect())
                .create().show();
    }

    private void askRedownloadSelectedScratch() {
        Set<ContentItem> selectedItems = selectExtension.getSelectedItems();

        int securedContent = 0;
        List<Content> contents = new ArrayList<>();
        for (ContentItem ci : selectedItems) {
            Content c = ci.getContent();
            if (null == c) continue;
            if (c.getSite().equals(Site.FAKKU2) || c.getSite().equals(Site.EXHENTAI)) {
                securedContent++;
            } else {
                contents.add(c);
            }
        }

        String message = getResources().getQuantityString(R.plurals.redownload_confirm, contents.size());
        if (securedContent > 0)
            message = getResources().getQuantityString(R.plurals.redownload_secured_content, securedContent);

        // TODO make it work for secured sites (Fakku, ExHentai) -> open a browser to fetch the relevant cookies ?

        new MaterialAlertDialogBuilder(requireContext(), ThemeHelper.getIdForCurrentTheme(requireContext(), R.style.Theme_Light_Dialog))
                .setIcon(R.drawable.ic_warning)
                .setCancelable(false)
                .setTitle(R.string.app_name)
                .setMessage(message)
                .setPositiveButton(R.string.yes,
                        (dialog1, which) -> {
                            dialog1.dismiss();
                            activity.get().redownloadContent(contents, true);
                            // If the 1st item is selected, visually reset its progress
                            if (selectExtension.getSelections().contains(0))
                                updateProgress(0, 0, 1, 0, 0, true);
                            selectExtension.deselect();
                            selectionToolbar.setVisibility(View.GONE);
                        })
                .setNegativeButton(R.string.no,
                        (dialog12, which) -> {
                            dialog12.dismiss();
                            selectExtension.deselect();
                            selectionToolbar.setVisibility(View.GONE);
                        })
                .create()
                .show();
    }
}
