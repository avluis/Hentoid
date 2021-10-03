package me.devsaki.hentoid.fragments.queue;

import static androidx.core.view.ViewCompat.requireViewById;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil;
import com.mikepenz.fastadapter.drag.ItemTouchCallback;
import com.mikepenz.fastadapter.listeners.ClickEventHook;
import com.mikepenz.fastadapter.select.SelectExtension;
import com.mikepenz.fastadapter.swipe.SimpleSwipeDrawerCallback;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.fragments.ProgressDialogFragment;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Debouncer;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.util.ToastHelper;
import me.devsaki.hentoid.viewholders.ContentItem;
import me.devsaki.hentoid.viewholders.ISwipeableViewHolder;
import me.devsaki.hentoid.viewmodels.QueueViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import me.devsaki.hentoid.widget.FastAdapterPreClickSelectHelper;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;
import timber.log.Timber;

/**
 * Created by Robb on 04/2020
 * Presents the list of downloads with errors
 */
public class ErrorsFragment extends Fragment implements ItemTouchCallback, ErrorsDialogFragment.Parent, SimpleSwipeDrawerCallback.ItemSwipeCallback {

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    // === COMMUNICATION
    private OnBackPressedCallback callback;
    private QueueViewModel viewModel;
    // Activity
    private WeakReference<QueueActivity> activity;

    // === UI
    private LinearLayoutManager llm;
    private RecyclerView recyclerView;
    private Toolbar selectionToolbar;
    private TextView mEmptyText;    // "No errors" message panel

    // == FASTADAPTER COMPONENTS AND HELPERS
    private FastAdapter<ContentItem> fastAdapter;
    private final ItemAdapter<ContentItem> itemAdapter = new ItemAdapter<>();
    private SelectExtension<ContentItem> selectExtension;
    // Helper for swiping items
    private ItemTouchHelper touchHelper;

    // === VARIABLES
    // Used to show a given item at first display
    private long contentHashToDisplayFirst = 0;
    // Used to start processing when the recyclerView has finished updating
    private Debouncer<Integer> listRefreshDebouncer;
    // Indicate if the fragment is currently canceling all items
    private boolean isDeletingAll = false;


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (!(requireActivity() instanceof QueueActivity))
            throw new IllegalStateException("Parent activity has to be a LibraryActivity");
        activity = new WeakReference<>((QueueActivity) requireActivity());

        listRefreshDebouncer = new Debouncer<>(context, 75, this::onRecyclerUpdated);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // UI ELEMENTS
        View rootView = inflater.inflate(R.layout.fragment_queue_errors, container, false);

        mEmptyText = requireViewById(rootView, R.id.errors_empty_txt);

        // Book list container
        recyclerView = requireViewById(rootView, R.id.queue_list);

        fastAdapter = FastAdapter.with(itemAdapter);
        ContentItem item = new ContentItem(ContentItem.ViewType.ERRORS);
        fastAdapter.registerItemFactory(item.getType(), item);

        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        selectExtension = fastAdapter.getOrCreateExtension(SelectExtension.class);
        if (selectExtension != null) {
            selectExtension.setSelectable(true);
            selectExtension.setMultiSelect(true);
            selectExtension.setSelectOnLongClick(true);
            selectExtension.setSelectWithItemUpdate(true);
            selectExtension.setSelectionListener((i, b) -> this.onSelectionChanged());

            FastAdapterPreClickSelectHelper<ContentItem> helper = new FastAdapterPreClickSelectHelper<>(selectExtension);
            fastAdapter.setOnPreClickListener(helper::onPreClickListener);
            fastAdapter.setOnPreLongClickListener(helper::onPreLongClickListener);
        }

        recyclerView.setAdapter(fastAdapter);
        recyclerView.setHasFixedSize(true);
        llm = (LinearLayoutManager) recyclerView.getLayoutManager();

        // Swiping
        SimpleSwipeDrawerCallback swipeCallback = new SimpleSwipeDrawerCallback(ItemTouchHelper.LEFT, this)
                .withSwipeLeft(Helper.dimensAsDp(requireContext(), R.dimen.delete_drawer_width_list))
                .withSensitivity(1.5f)
                .withSurfaceThreshold(0.3f);

        touchHelper = new ItemTouchHelper(swipeCallback);
        touchHelper.attachToRecyclerView(recyclerView);

        // Item click listener
        fastAdapter.setOnClickListener((v, a, i, p) -> onItemClick(i));

        // Fast scroller
        new FastScrollerBuilder(recyclerView).build();

        initToolbar();
        initSelectionToolbar();
        attachButtons(fastAdapter);

        addCustomBackControl();

        return rootView;
    }

    private void addCustomBackControl() {
        if (callback != null) callback.remove();
        callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                customBackPress();
            }
        };
        activity.get().getOnBackPressedDispatcher().addCallback(activity.get(), callback);
    }

    private void customBackPress() {
        // If content is selected, deselect it
        if (selectExtension != null && !selectExtension.getSelections().isEmpty()) {
            selectExtension.deselect(selectExtension.getSelections());
            activity.get().getSelectionToolbar().setVisibility(View.GONE);
        } else {
            callback.remove();
            requireActivity().onBackPressed();
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewModelFactory vmFactory = new ViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(requireActivity(), vmFactory).get(QueueViewModel.class);
        viewModel.getErrors().observe(getViewLifecycleOwner(), this::onErrorsChanged);
        viewModel.getContentHashToShowFirst().observe(getViewLifecycleOwner(), this::onContentHashToShowFirstChanged);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (fastAdapter != null) fastAdapter.saveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (null == savedInstanceState) return;

        if (fastAdapter != null) fastAdapter.withSavedInstanceState(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (selectExtension != null) selectExtension.deselect(selectExtension.getSelections());
        initSelectionToolbar();
    }

    @Override
    public void onDestroy() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
        compositeDisposable.clear();
        super.onDestroy();
    }

    private void initToolbar() {
        QueueActivity queueActivity = activity.get();
        MenuItem redownloadAllMenu = queueActivity.getToolbar().getMenu().findItem(R.id.action_redownload_all);
        redownloadAllMenu.setOnMenuItemClickListener(item -> {
            onRedownloadAllClick();
            return true;
        });

        MenuItem cancelAllMenu = queueActivity.getToolbar().getMenu().findItem(R.id.action_cancel_all_errors);
        cancelAllMenu.setOnMenuItemClickListener(item -> {
            onCancelAllClick();
            return true;
        });

        MenuItem invertMenu = queueActivity.getToolbar().getMenu().findItem(R.id.action_invert_queue);
        invertMenu.setOnMenuItemClickListener(item -> {
            viewModel.invertQueue();
            return true;
        });
    }

    private void initSelectionToolbar() {
        QueueActivity queueActivity = activity.get();

        selectionToolbar = queueActivity.getSelectionToolbar();
        selectionToolbar.setNavigationOnClickListener(v -> {
            selectExtension.deselect(selectExtension.getSelections());
            selectionToolbar.setVisibility(View.GONE);
        });
        selectionToolbar.setOnMenuItemClickListener(this::onSelectionMenuItemClicked);
    }

    @SuppressLint("NonConstantResourceId")
    private boolean onSelectionMenuItemClicked(@NonNull MenuItem menuItem) {
        boolean keepToolbar = false;
        switch (menuItem.getItemId()) {
            case R.id.action_queue_delete:
                Set<ContentItem> selectedItems = selectExtension.getSelectedItems();
                if (!selectedItems.isEmpty()) {
                    List<Content> selectedContent = Stream.of(selectedItems).map(ContentItem::getContent).withoutNulls().toList();
                    askDeleteSelected(selectedContent);
                }
                break;
            case R.id.action_download:
                redownloadSelected();
                break;
            case R.id.action_download_scratch:
                askRedownloadSelectedScratch();
                keepToolbar = true;
                break;
            default:
                selectionToolbar.setVisibility(View.GONE);
                return false;
        }
        if (!keepToolbar) selectionToolbar.setVisibility(View.GONE);
        return true;
    }

    private void updateSelectionToolbar(long selectedCount) {
        selectionToolbar.setTitle(getResources().getQuantityString(R.plurals.items_selected, (int) selectedCount, (int) selectedCount));
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

        // Info button
        fastAdapter.addEventHook(new ClickEventHook<ContentItem>() {
            @Override
            public void onClick(@NotNull View view, int i, @NotNull FastAdapter<ContentItem> fastAdapter, @NotNull ContentItem item) {
                Content c = item.getContent();
                if (c != null) showErrorLogDialog(c);
            }

            @org.jetbrains.annotations.Nullable
            @Override
            public View onBind(RecyclerView.@NotNull ViewHolder viewHolder) {
                if (viewHolder instanceof ContentItem.ContentViewHolder) {
                    return ((ContentItem.ContentViewHolder) viewHolder).getErrorButton();
                }
                return super.onBind(viewHolder);
            }
        });

        // Redownload button
        fastAdapter.addEventHook(new ClickEventHook<ContentItem>() {
            @Override
            public void onClick(@NotNull View view, int i, @NotNull FastAdapter<ContentItem> fastAdapter, @NotNull ContentItem item) {
                Content c = item.getContent();
                if (c != null) redownloadContent(c);
            }

            @org.jetbrains.annotations.Nullable
            @Override
            public View onBind(RecyclerView.@NotNull ViewHolder viewHolder) {
                if (viewHolder instanceof ContentItem.ContentViewHolder) {
                    return ((ContentItem.ContentViewHolder) viewHolder).getDownloadButton();
                }
                return super.onBind(viewHolder);
            }
        });
    }

    private void showErrorLogDialog(@NonNull Content content) {
        ErrorsDialogFragment.invoke(this, content.getId());
    }

    private void onErrorsChanged(List<Content> result) {
        Timber.i(">>Errors changed ! Size=%s", result.size());

        // Don't process changes while everything is being canceled, it usually kills the UI as too many changes are processed at the same time
        if (isDeletingAll && !result.isEmpty()) return;

        // Update list visibility
        mEmptyText.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);

        // Update displayed books
        List<ContentItem> contentItems = Stream.of(result).map(c -> new ContentItem(c, touchHelper, ContentItem.ViewType.ERRORS, this::onDeleteSwipedBook)).withoutNulls().distinct().toList();
        FastAdapterDiffUtil.INSTANCE.set(itemAdapter, contentItems);
        new Handler(Looper.getMainLooper()).postDelayed(this::differEndCallback, 150);
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
        }
    }

    /**
     * Callback for the end of recycler updates
     * Activated when all _displayed_ items are placed on their definitive position
     */
    private void onRecyclerUpdated(int topItemPosition) {
        llm.scrollToPositionWithOffset(topItemPosition, 0); // Used to restore position after activity has been stopped and recreated
    }

    private void onContentHashToShowFirstChanged(Long contentHash) {
        Timber.d(">>onContentIdToShowFirstChanged %s", contentHash);
        contentHashToDisplayFirst = contentHash;
    }

    private boolean onItemClick(ContentItem item) {
        if (null == selectExtension || selectExtension.getSelections().isEmpty()) {
            Content c = item.getContent();
            if (c != null && !ContentHelper.openHentoidViewer(requireContext(), c, -1, null, false))
                ToastHelper.toast(R.string.err_no_content);

            return true;
        }
        return false;
    }

    private void onDeleteSwipedBook(@NonNull final ContentItem item) {
        // Deleted book is the last selected books => disable selection mode
        if (item.isSelected()) {
            selectExtension.deselect(item);
            if (selectExtension.getSelections().isEmpty())
                selectionToolbar.setVisibility(View.GONE);
        }
        Content content = item.getContent();
        if (content != null)
            viewModel.remove(Stream.of(content).toList());
    }

    private void onDeleteBooks(@NonNull List<Content> c) {
        if (c.size() > 2) {
            isDeletingAll = true;
            ProgressDialogFragment.invoke(getParentFragmentManager(), getResources().getString(R.string.cancel_queue_progress), getResources().getString(R.string.books));
        }
        viewModel.remove(c);
    }

    private void doCancelAll() {
        isDeletingAll = true;
        ProgressDialogFragment.invoke(getParentFragmentManager(), getResources().getString(R.string.cancel_queue_progress), getResources().getString(R.string.books));
        viewModel.removeAll();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onProcessEvent(ProcessEvent event) {
        // Filter on cancel complete event
        if (R.id.generic_progress != event.processId) return;
        if (event.eventType == ProcessEvent.EventType.COMPLETE) onDeleteComplete();
    }

    private void onDeleteComplete() {
        isDeletingAll = false;
        viewModel.refresh();
        if (null == selectExtension || selectExtension.getSelections().isEmpty())
            selectionToolbar.setVisibility(View.GONE);
    }

    private void onRedownloadAllClick() {
        // Don't do anything if the queue is empty
        if (0 == itemAdapter.getAdapterItemCount()) return;

        // Just do it if the queue has a single item
        if (1 == itemAdapter.getAdapterItemCount()) redownloadAll();
        else // Ask if there's more than 1 item
            new MaterialAlertDialogBuilder(requireContext(), ThemeHelper.getIdForCurrentTheme(requireContext(), R.style.Theme_Light_Dialog))
                    .setIcon(R.drawable.ic_warning)
                    .setTitle(R.string.app_name)
                    .setMessage(getString(R.string.confirm_redownload_all, fastAdapter.getItemCount()))
                    .setPositiveButton(R.string.yes,
                            (dialog1, which) -> {
                                dialog1.dismiss();
                                redownloadAll();
                            })
                    .setNegativeButton(R.string.no,
                            (dialog12, which) -> dialog12.dismiss())
                    .create()
                    .show();
    }

    private void onCancelAllClick() {
        // Don't do anything if the queue is empty
        if (0 == itemAdapter.getAdapterItemCount()) return;

        // Just do it if the queue has a single item
        if (1 == itemAdapter.getAdapterItemCount()) doCancelAll();
        else // Ask if there's more than 1 item
            new MaterialAlertDialogBuilder(requireContext(), ThemeHelper.getIdForCurrentTheme(requireContext(), R.style.Theme_Light_Dialog))
                    .setIcon(R.drawable.ic_warning)
                    .setTitle(R.string.app_name)
                    .setMessage(getString(R.string.confirm_cancel_all_errors, fastAdapter.getItemCount()))
                    .setPositiveButton(R.string.yes,
                            (dialog1, which) -> {
                                dialog1.dismiss();
                                doCancelAll();
                            })
                    .setNegativeButton(R.string.no,
                            (dialog12, which) -> dialog12.dismiss())
                    .create()
                    .show();
    }

    /**
     * DRAG, DROP & SWIPE METHODS
     */

    @Override
    public boolean itemTouchOnMove(int oldPosition, int newPosition) {
        // Nothing; error items are not draggable
        return false;
    }

    @Override
    public void itemTouchDropped(int oldPosition, int newPosition) {
        // Nothing; error items are not draggable
    }

    @Override
    public void itemTouchStopDrag(RecyclerView.@NotNull ViewHolder viewHolder) {
        // Nothing; error items are not draggable
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

    private void askRedownloadSelectedScratch() {
        Set<ContentItem> selectedItems = selectExtension.getSelectedItems();

        List<Content> contents = new ArrayList<>();
        for (ContentItem ci : selectedItems) {
            Content c = ci.getContent();
            if (null == c) continue;
            contents.add(c);
        }

        String message = getResources().getQuantityString(R.plurals.redownload_confirm, contents.size());
        new MaterialAlertDialogBuilder(requireContext(), ThemeHelper.getIdForCurrentTheme(requireContext(), R.style.Theme_Light_Dialog))
                .setIcon(R.drawable.ic_warning)
                .setCancelable(false)
                .setTitle(R.string.app_name)
                .setMessage(message)
                .setPositiveButton(R.string.yes,
                        (dialog1, which) -> {
                            dialog1.dismiss();
                            activity.get().redownloadContent(contents, true, true);
                            selectExtension.deselect(selectExtension.getSelections());
                            selectionToolbar.setVisibility(View.GONE);
                        })
                .setNegativeButton(R.string.no,
                        (dialog12, which) -> {
                            dialog12.dismiss();
                            selectExtension.deselect(selectExtension.getSelections());
                            selectionToolbar.setVisibility(View.GONE);
                        })
                .create()
                .show();
    }

    private void redownloadSelected() {
        activity.get().redownloadContent(Stream.of(selectExtension.getSelectedItems()).map(ContentItem::getContent).withoutNulls().toList(), false, false);
    }

    private void redownloadAll() {
        List<Content> contents = Stream.of(itemAdapter.getAdapterItems()).map(ContentItem::getContent).withoutNulls().toList();
        if (!contents.isEmpty()) activity.get().redownloadContent(contents, false, false);
    }

    @Override
    public void redownloadContent(Content content) {
        List<Content> contentList = new ArrayList<>();
        contentList.add(content);
        activity.get().redownloadContent(contentList, false, false);
    }

    @Override
    public void itemTouchStartDrag(RecyclerView.@NotNull ViewHolder viewHolder) {
        // Nothing
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
                            selectExtension.deselect(selectExtension.getSelections());
                            onDeleteBooks(items);
                        })
                .setNegativeButton(R.string.no,
                        (dialog, which) -> selectExtension.deselect(selectExtension.getSelections()))
                .setOnCancelListener(dialog -> selectExtension.deselect(selectExtension.getSelections()))
                .create().show();
    }
}
