package me.devsaki.hentoid.fragments.queue;

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.AsyncDifferConfig;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.drag.ItemTouchCallback;
import com.mikepenz.fastadapter.listeners.ClickEventHook;
import com.mikepenz.fastadapter.paged.PagedModelAdapter;
import com.mikepenz.fastadapter.select.SelectExtension;
import com.mikepenz.fastadapter.swipe.SimpleSwipeCallback;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.fragments.library.ErrorsDialogFragment;
import me.devsaki.hentoid.services.ContentQueueManager;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Debouncer;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.viewholders.ContentItem;
import me.devsaki.hentoid.viewmodels.QueueViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;

/**
 * Created by Robb on 04/2020
 * Presents the list of downloads with errors
 */
public class ErrorsFragment extends Fragment implements ItemTouchCallback, SimpleSwipeCallback.ItemSwipeCallback, ErrorsDialogFragment.Parent {

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    // === COMMUNICATION
    private QueueViewModel viewModel;

    // === UI
    private Toolbar selectionToolbar;
    private TextView mEmptyText;    // "No errors" message panel

    // == FASTADAPTER COMPONENTS AND HELPERS
    private FastAdapter<ContentItem> fastAdapter;
    private SelectExtension<ContentItem> selectExtension;
    // Helper for swiping items
    private ItemTouchHelper touchHelper;

    // === VARIABLES
    // Used to ignore native calls to onBookClick right after that book has been deselected
    private boolean invalidateNextBookClick = false;


    /**
     * Diff calculation rules for list items
     * <p>
     * Created once and for all to be used by FastAdapter in endless mode (=using Android PagedList)
     */
    private final AsyncDifferConfig<Content> asyncDifferConfig = new AsyncDifferConfig.Builder<>(new DiffUtil.ItemCallback<Content>() {
        @Override
        public boolean areItemsTheSame(@NonNull Content oldItem, @NonNull Content newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Content oldItem, @NonNull Content newItem) {
            return oldItem.getUrl().equalsIgnoreCase(newItem.getUrl())
                    && oldItem.getSite().equals(newItem.getSite())
                    && oldItem.getLastReadDate() == newItem.getLastReadDate()
                    && oldItem.isFavourite() == newItem.isFavourite();
        }
    }).build();

    private final PagedModelAdapter<Content, ContentItem> itemAdapter = new PagedModelAdapter<>(asyncDifferConfig, i -> new ContentItem(ContentItem.ViewType.ERRORS), c -> new ContentItem(c, touchHelper, ContentItem.ViewType.ERRORS));


    @Override
    public void onDestroy() {
        compositeDisposable.clear();
        super.onDestroy();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // UI ELEMENTS
        View rootView = inflater.inflate(R.layout.fragment_queue_errors, container, false);

        mEmptyText = requireViewById(rootView, R.id.errors_empty_txt);

        // Book list container
        RecyclerView recyclerView = requireViewById(rootView, R.id.queue_list);

        fastAdapter = FastAdapter.with(itemAdapter);
        fastAdapter.setHasStableIds(true);
        ContentItem item = new ContentItem(ContentItem.ViewType.ERRORS);
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

        // Swiping
        SimpleSwipeCallback swipeCallback = new SimpleSwipeCallback(
                this,
                requireContext().getDrawable(R.drawable.ic_action_delete_forever));

        touchHelper = new ItemTouchHelper(swipeCallback);
        touchHelper.attachToRecyclerView(recyclerView);

        // Fast scroller
        new FastScrollerBuilder(recyclerView).build();

        // Item click listener
        fastAdapter.setOnClickListener((v, a, i, p) -> onBookClick(i));

        initToolbar();
        initSelectionToolbar();
        attachButtons(fastAdapter);

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewModelFactory vmFactory = new ViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(this, vmFactory).get(QueueViewModel.class);
        viewModel.getErrorsPaged().observe(getViewLifecycleOwner(), this::onErrorsChanged);
    }

    private void initToolbar() {
        if (!(requireActivity() instanceof QueueActivity)) return;
        QueueActivity activity = (QueueActivity) requireActivity();
        MenuItem redownloadAllMenu = activity.getToolbar().getMenu().findItem(R.id.action_redownload_all);
        redownloadAllMenu.setOnMenuItemClickListener(item -> {
            // Don't do anything if the queue is empty
            if (0 == itemAdapter.getAdapterItemCount()) return true;
                // Just do it if the queue has a single item
            else if (1 == itemAdapter.getAdapterItemCount()) redownloadAll();
                // Ask if there's more than 1 item
            else
                new MaterialAlertDialogBuilder(requireContext(), ThemeHelper.getIdForCurrentTheme(requireContext(), R.style.Theme_Light_Dialog))
                        .setIcon(R.drawable.ic_warning)
                        .setCancelable(false)
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
            return true;
        });
        MenuItem invertMenu = activity.getToolbar().getMenu().findItem(R.id.action_invert_queue);
        invertMenu.setOnMenuItemClickListener(item -> {
            viewModel.invertQueue();
            return true;
        });
    }

    private void initSelectionToolbar() {
        if (!(requireActivity() instanceof QueueActivity)) return;
        QueueActivity activity = (QueueActivity) requireActivity();

        selectionToolbar = activity.getSelectionToolbar();
        selectionToolbar.setNavigationOnClickListener(v -> {
            selectExtension.deselect();
            selectionToolbar.setVisibility(View.GONE);
        });
        selectionToolbar.setOnMenuItemClickListener(this::selectionToolbarOnItemClicked);
    }

    private boolean selectionToolbarOnItemClicked(@NonNull MenuItem menuItem) {
        boolean keepToolbar = false;
        switch (menuItem.getItemId()) {
            /*
            case R.id.action_queue_delete:
                //askDeleteSelected();
                // TODO
                break;
             */
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

    private void onErrorsChanged(PagedList<Content> result) {
        Timber.i(">>Errors changed ! Size=%s", result.size());

        // Update list visibility
        mEmptyText.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);

        // Update displayed books
        itemAdapter.submitList(result/*, this::differEndCallback*/);
    }

    private boolean onBookClick(ContentItem item) {
        if (selectExtension.getSelectedItems().isEmpty()) {
            Content c = item.getContent();
            if (!invalidateNextBookClick && c != null) {
                if (!ContentHelper.openHentoidViewer(requireContext(), c, null))
                    ToastUtil.toast(R.string.err_no_content);
            } else invalidateNextBookClick = false;

            return true;
        } else {
            selectExtension.setSelectOnLongClick(false);
        }
        return false;
    }

    private void onDeleteBook(@NonNull Content c) {
        viewModel.remove(c);
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
    public void itemSwiped(int position, int direction) {
        ContentItem item = itemAdapter.getAdapterItem(position);
        item.setSwipeDirection(direction);

        if (item.getContent() != null) {
            Debouncer<Content> deleteDebouncer = new Debouncer<>(2000, this::onDeleteBook);
            deleteDebouncer.submit(item.getContent());

            Runnable cancelSwipe = () -> {
                deleteDebouncer.clear();
                item.setSwipeDirection(0);
                int position1 = itemAdapter.getAdapterPosition(item);
                if (position1 != RecyclerView.NO_POSITION)
                    fastAdapter.notifyItemChanged(position1);
            };
            item.setUndoSwipeAction(cancelSwipe);
            fastAdapter.notifyItemChanged(position);
        }
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
                .setPositiveButton(android.R.string.yes,
                        (dialog1, which) -> {
                            dialog1.dismiss();
                            redownloadContent(contents, true);
                            for (ContentItem ci : selectedItems) ci.setSelected(false);
                            selectExtension.deselect();
                            selectionToolbar.setVisibility(View.GONE);
                        })
                .setNegativeButton(android.R.string.no,
                        (dialog12, which) -> dialog12.dismiss())
                .create()
                .show();
    }

    private void redownloadSelected() {
        redownloadContent(Stream.of(selectExtension.getSelectedItems()).map(ContentItem::getContent).withoutNulls().toList(), false);
    }

    private void redownloadAll() {
        redownloadContent(new ArrayList<>(itemAdapter.getModels()), false);
    }

    @Override
    public void redownloadContent(Content content) {
        List<Content> contentList = new ArrayList<>();
        contentList.add(content);
        redownloadContent(contentList, false);
    }

    private void redownloadContent(@NonNull final List<Content> contentList, boolean reparseImages) {
        StatusContent targetImageStatus = reparseImages ? StatusContent.ERROR : null;
        for (Content c : contentList)
            if (c != null)
                viewModel.addContentToQueue(c, targetImageStatus);

        if (Preferences.isQueueAutostart())
            ContentQueueManager.getInstance().resumeQueue(getContext());

        String message = getResources().getQuantityString(R.plurals.add_to_queue, contentList.size(), contentList.size());
        Snackbar snackbar = Snackbar.make(mEmptyText, message, BaseTransientBottomBar.LENGTH_LONG);
        snackbar.show();
    }

    @Override
    public void itemTouchStartDrag(RecyclerView.@NotNull ViewHolder viewHolder) {
        // Nothing
    }
}
