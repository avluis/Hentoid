package me.devsaki.hentoid.fragments.viewer;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.listeners.ClickEventHook;
import com.mikepenz.fastadapter.select.SelectExtension;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.util.exception.ContentNotRemovedException;
import me.devsaki.hentoid.viewholders.ImageFileItem;
import me.devsaki.hentoid.viewmodels.ImageViewerViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;

public class ImageGalleryFragment extends Fragment {

    private static final String KEY_FILTER_FAVOURITES = "filter_favourites";

    private ImageViewerViewModel viewModel;

    // === UI
    private Toolbar toolbar;
    private Toolbar selectionToolbar;
    private MenuItem itemSetCover;
    private MenuItem showFavouritePagesButton;
    private RecyclerView recyclerView;
    private final ItemAdapter<ImageFileItem> itemAdapter = new ItemAdapter<>();
    private final FastAdapter<ImageFileItem> fastAdapter = FastAdapter.with(itemAdapter);
    private SelectExtension<ImageFileItem> selectExtension;

    // === VARIABLES
    // Used to ignore native calls to onBookClick right after that book has been deselected
    private boolean invalidateNextBookClick = false;
    private int startIndex = 0;
    private boolean firstLoadDone = false;
    private boolean filterFavourites = false;


    static ImageGalleryFragment newInstance(boolean filterFavourites) {
        ImageGalleryFragment fragment = new ImageGalleryFragment();
        Bundle args = new Bundle();
        args.putBoolean(KEY_FILTER_FAVOURITES, filterFavourites);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_viewer_gallery, container, false);

        Bundle arguments = getArguments();
        if (arguments != null)
            filterFavourites = arguments.getBoolean(KEY_FILTER_FAVOURITES, false);

        setHasOptionsMenu(true);

        if (!fastAdapter.hasObservers())
            fastAdapter.setHasStableIds(true);

        // Item click listener
        fastAdapter.setOnClickListener((v, a, i, p) -> onItemClick(i));
        // Favourite button click listener
        fastAdapter.addEventHook(new ClickEventHook<ImageFileItem>() {
            @Override
            public void onClick(@NotNull View view, int i, @NotNull FastAdapter<ImageFileItem> fastAdapter, @NotNull ImageFileItem item) {
                onFavouriteClick(item.getImage());
            }

            @Nullable
            @Override
            public View onBind(RecyclerView.@NotNull ViewHolder viewHolder) {
                if (viewHolder instanceof ImageFileItem.ImageViewHolder) {
                    return ((ImageFileItem.ImageViewHolder) viewHolder).getFavouriteButton();
                }
                return super.onBind(viewHolder);
            }
        });

        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        selectExtension = fastAdapter.getOrCreateExtension(SelectExtension.class);
        if (selectExtension != null) {
            selectExtension.setSelectable(true);
            selectExtension.setMultiSelect(true);
            selectExtension.setSelectOnLongClick(true);
            selectExtension.setSelectionListener((i, b) -> this.onSelectionChanged());
        }

        // Filtering
        itemAdapter.getItemFilter().setFilterPredicate((imageFileItem, charSequence) -> !charSequence.equals("true") || imageFileItem.isFavourite());

        recyclerView = requireViewById(rootView, R.id.viewer_gallery_recycler);
        recyclerView.setAdapter(fastAdapter);
        new FastScrollerBuilder(recyclerView).build();

        toolbar = requireViewById(rootView, R.id.viewer_gallery_toolbar);
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());

        toolbar.setOnMenuItemClickListener(clickedMenuItem -> {
            if (clickedMenuItem.getItemId() == R.id.action_show_favorite_pages) {
                toggleFavouritesDisplay();
            }
            return true;
        });
        showFavouritePagesButton = toolbar.getMenu().findItem(R.id.action_show_favorite_pages);

        selectionToolbar = requireViewById(rootView, R.id.viewer_gallery_selection_toolbar);
        itemSetCover = selectionToolbar.getMenu().findItem(R.id.action_set_cover);
        selectionToolbar.setNavigationOnClickListener(v -> {
            selectExtension.deselect();
            selectionToolbar.setVisibility(View.GONE);
            toolbar.setVisibility(View.VISIBLE);
        });
        selectionToolbar.setOnMenuItemClickListener(this::onSelectionMenuItemClicked);

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        firstLoadDone = false;

        ViewModelFactory vmFactory = new ViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(requireActivity(), vmFactory).get(ImageViewerViewModel.class);
        viewModel.getStartingIndex().observe(getViewLifecycleOwner(), this::onStartingIndexChanged);
        viewModel.getImages().observe(getViewLifecycleOwner(), this::onImagesChanged);
    }

    @Override
    public void onDestroy() {
        if (recyclerView != null)
            recyclerView.setAdapter(null);
        recyclerView = null;
        super.onDestroy();
    }

    private void onImagesChanged(List<ImageFile> images) {
        List<ImageFileItem> imgs = new ArrayList<>();
        for (ImageFile img : images) {
            ImageFileItem holder = new ImageFileItem(img);
            if (startIndex == img.getDisplayOrder()) holder.setCurrent(true);
            imgs.add(holder);
        }
        itemAdapter.set(imgs);
        updateListFilter();
        updateFavouriteDisplay();
    }

    private void onStartingIndexChanged(Integer startingIndex) {
        startIndex = startingIndex;
    }

    private boolean onSelectionMenuItemClicked(@NonNull MenuItem menuItem) {
        Set<ImageFileItem> selectedItems = selectExtension.getSelectedItems();
        switch (menuItem.getItemId()) {
            case R.id.action_delete:
                if (!selectedItems.isEmpty()) {
                    List<ImageFile> selectedContent = Stream.of(selectedItems).map(ImageFileItem::getImage).withoutNulls().toList();
                    askDeleteSelected(selectedContent);
                }
                break;
            case R.id.action_set_cover:
                if (!selectedItems.isEmpty()) {
                    Optional<ImageFile> selectedContent = Stream.of(selectedItems).map(ImageFileItem::getImage).withoutNulls().findFirst();
                    if (selectedContent.isPresent()) askSetSelectedCover(selectedContent.get());
                }
                break;
            default:
                selectionToolbar.setVisibility(View.GONE);
                toolbar.setVisibility(View.VISIBLE);
                return false;
        }
        selectionToolbar.setVisibility(View.GONE);
        toolbar.setVisibility(View.VISIBLE);
        return true;
    }

    private void updateSelectionToolbar(long selectedCount) {
        itemSetCover.setVisible(1 == selectedCount);
        selectionToolbar.setTitle(getResources().getQuantityString(R.plurals.items_selected, (int) selectedCount, (int) selectedCount));
    }

    private boolean onItemClick(ImageFileItem item) {
        if (null == selectExtension || selectExtension.getSelectedItems().isEmpty()) {
            ImageFile img = item.getImage();
            if (!invalidateNextBookClick && img != null) {
                viewModel.setStartingIndex(img.getDisplayOrder());
                if (0 == getParentFragmentManager().getBackStackEntryCount()) { // Gallery mode (Library -> gallery -> pager)
                    getParentFragmentManager()
                            .beginTransaction()
                            .replace(android.R.id.content, new ImagePagerFragment())
                            .addToBackStack(null)
                            .commit();
                } else { // Pager mode (Library -> pager -> gallery -> pager)
                    getParentFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE); // Leave only the latest element in the back stack
                }

            } else invalidateNextBookClick = false;
            return true;
        } else {
            selectExtension.setSelectOnLongClick(false);
        }
        return false;
    }

    private void onFavouriteClick(ImageFile img) {
        viewModel.togglePageFavourite(img, this::onFavouriteSuccess);
    }

    private void onFavouriteSuccess(ImageFile img) {
        if (filterFavourites) {
            // Reset favs filter if no favourite page remains
            if (!hasFavourite()) {
                filterFavourites = false;
                itemAdapter.filter("");
                if (itemAdapter.getAdapterItemCount() > 0)
                    recyclerView.scrollToPosition(0);
            } else {
                fastAdapter.notifyDataSetChanged(); // Because no easy way to spot which item has changed when the view is filtered
            }
        } else fastAdapter.notifyItemChanged(img.getDisplayOrder());

        showFavouritePagesButton.setVisible(hasFavourite());
    }

    private void toggleFavouritesDisplay() {
        filterFavourites = !filterFavourites;
        updateFavouriteDisplay();
        updateListFilter();
    }

    private void updateFavouriteDisplay() {
        showFavouritePagesButton.setVisible(hasFavourite());
        showFavouritePagesButton.setIcon(filterFavourites ? R.drawable.ic_filter_favs_on : R.drawable.ic_filter_favs_off);
    }

    private void updateListFilter() {
        if (itemAdapter.getAdapterItemCount() > 0) {
            itemAdapter.filter(filterFavourites ? "true" : "");

            if (!firstLoadDone) {
                if (itemAdapter.getAdapterItemCount() > startIndex)
                    recyclerView.scrollToPosition(startIndex);
                else recyclerView.scrollToPosition(0);
                firstLoadDone = true;
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_FILTER_FAVOURITES, filterFavourites);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null)
            filterFavourites = savedInstanceState.getBoolean(KEY_FILTER_FAVOURITES, false);
    }

    private boolean hasFavourite() {
        List<ImageFileItem> images = itemAdapter.getAdapterItems();
        for (ImageFileItem item : images) if (item.isFavourite()) return true;
        return false;
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     */
    private void onSelectionChanged() {
        int selectedCount = selectExtension.getSelectedItems().size();

        if (0 == selectedCount) {
            selectionToolbar.setVisibility(View.GONE);
            toolbar.setVisibility(View.VISIBLE);
            selectExtension.setSelectOnLongClick(true);
            invalidateNextBookClick = true;
            new Handler().postDelayed(() -> invalidateNextBookClick = false, 200);
        } else {
            updateSelectionToolbar(selectedCount);
            selectionToolbar.setVisibility(View.VISIBLE);
            toolbar.setVisibility(View.GONE);
        }
    }

    /**
     * Display the yes/no dialog to make sure the user really wants to delete selected items
     *
     * @param items Items to be deleted if the answer is yes
     */
    private void askDeleteSelected(@NonNull final List<ImageFile> items) {
        Context context = getActivity();
        if (null == context) return;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        String title = context.getResources().getQuantityString(R.plurals.ask_delete_multiple, items.size());
        builder.setMessage(title)
                .setPositiveButton(android.R.string.yes,
                        (dialog, which) -> {
                            selectExtension.deselect();
                            viewModel.deletePages(items, this::onDeleteError);
                        })
                .setNegativeButton(android.R.string.no,
                        (dialog, which) -> selectExtension.deselect())
                .create().show();
    }

    /**
     * Callback for the failure of the "delete item" action
     */
    private void onDeleteError(Throwable t) {
        Timber.e(t);
        if (t instanceof ContentNotRemovedException) {
            ContentNotRemovedException e = (ContentNotRemovedException) t;
            String message = (null == e.getMessage()) ? "Page removal failed" : e.getMessage();
            Snackbar.make(recyclerView, message, BaseTransientBottomBar.LENGTH_LONG).show();
        }
    }

    // TODO doc
    private void askSetSelectedCover(@NonNull final ImageFile item) {
        Context context = getActivity();
        if (null == context) return;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        String title = context.getResources().getString(R.string.viewer_ask_cover);
        builder.setMessage(title)
                .setPositiveButton(android.R.string.yes,
                        (dialog, which) -> {
                            selectExtension.deselect();
                            viewModel.setCover(item);
                        })
                .setNegativeButton(android.R.string.no,
                        (dialog, which) -> selectExtension.deselect())
                .create().show();
    }
}
