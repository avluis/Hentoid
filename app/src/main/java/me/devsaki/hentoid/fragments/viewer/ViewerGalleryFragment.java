package me.devsaki.hentoid.fragments.viewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import com.annimon.stream.IntStream;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.diff.DiffCallback;
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil;
import com.mikepenz.fastadapter.select.SelectExtension;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.ImageItemBundle;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.util.exception.ContentNotRemovedException;
import me.devsaki.hentoid.viewholders.ImageFileItem;
import me.devsaki.hentoid.viewmodels.ImageViewerViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import me.devsaki.hentoid.widget.DragSelectTouchListener;
import me.devsaki.hentoid.widget.FastAdapterPreClickSelectHelper;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;

public class ViewerGalleryFragment extends Fragment {

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

    private DragSelectTouchListener mDragSelectTouchListener;

    // === VARIABLES
    // Used to ignore native calls to onBookClick right after that book has been deselected
    private int startIndex = 0;
    private boolean firstLoadDone = false;
    private boolean firstMoveDone = false;

    private boolean filterFavouritesLaunchState = false;
    private boolean filterFavouritesLaunchRequest = false;
    private boolean filterFavouritesState = false;


    public static final DiffCallback<ImageFileItem> IMAGE_DIFF_CALLBACK = new DiffCallback<ImageFileItem>() {
        @Override
        public boolean areItemsTheSame(ImageFileItem oldItem, ImageFileItem newItem) {
            return oldItem.getIdentifier() == newItem.getIdentifier();
        }

        @Override
        public boolean areContentsTheSame(ImageFileItem oldItem, ImageFileItem newItem) {
            ImageFile oldImage = oldItem.getImage();
            ImageFile newImage = newItem.getImage();

            if (null == oldImage || null == newImage) return false;

            return oldItem.isFavourite() == newItem.isFavourite();
        }

        @Override
        public @org.jetbrains.annotations.Nullable Object getChangePayload(ImageFileItem oldImageItem, int oldPos, ImageFileItem newImageItem, int newPos) {
            ImageFile oldImage = oldImageItem.getImage();
            ImageFile newImage = newImageItem.getImage();

            if (null == oldImage || null == newImage) return false;

            ImageItemBundle.Builder diffBundleBuilder = new ImageItemBundle.Builder();

            if (oldImage.isFavourite() != newImage.isFavourite()) {
                diffBundleBuilder.setIsFavourite(newImage.isFavourite());
            }

            if (diffBundleBuilder.isEmpty()) return null;
            else return diffBundleBuilder.getBundle();
        }
    };


    static ViewerGalleryFragment newInstance(boolean filterFavourites) {
        ViewerGalleryFragment fragment = new ViewerGalleryFragment();
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
            filterFavouritesLaunchRequest = arguments.getBoolean(KEY_FILTER_FAVOURITES, false);

        setHasOptionsMenu(true);

        if (!fastAdapter.hasObservers()) fastAdapter.setHasStableIds(true);

        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        selectExtension = fastAdapter.getOrCreateExtension(SelectExtension.class);
        if (selectExtension != null) {
            selectExtension.setSelectable(true);
            selectExtension.setMultiSelect(true);
            selectExtension.setSelectOnLongClick(true);
            selectExtension.setSelectWithItemUpdate(true);
            selectExtension.setSelectionListener((i, b) -> this.onSelectionChanged());

            FastAdapterPreClickSelectHelper<ImageFileItem> helper = new FastAdapterPreClickSelectHelper<>(selectExtension);
            fastAdapter.setOnPreClickListener(helper::onPreClickListener);
            fastAdapter.setOnPreLongClickListener((v, a, i, p) -> {
                // Warning : specific code for drag selection
                mDragSelectTouchListener.startDragSelection(p);
                return helper.onPreLongClickListener(v, a, i, p);
            });
        }

        // Item click listener
        fastAdapter.setOnClickListener((v, a, i, p) -> onItemClick(i));

        // Filtering
        itemAdapter.getItemFilter().setFilterPredicate((imageFileItem, charSequence) -> !charSequence.equals("true") || imageFileItem.isFavourite());

        recyclerView = requireViewById(rootView, R.id.viewer_gallery_recycler);
        recyclerView.setAdapter(fastAdapter);
        new FastScrollerBuilder(recyclerView).build();

        // Select on swipe
        DragSelectTouchListener.OnDragSelectListener onDragSelectionListener = (start, end, isSelected) -> selectExtension.select(IntStream.rangeClosed(start, end).boxed().toList());
        mDragSelectTouchListener = new DragSelectTouchListener()
                .withSelectListener(onDragSelectionListener);
        recyclerView.addOnItemTouchListener(mDragSelectTouchListener);


        // Toolbar
        toolbar = requireViewById(rootView, R.id.viewer_gallery_toolbar);
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());

        toolbar.setOnMenuItemClickListener(clickedMenuItem -> {
            if (clickedMenuItem.getItemId() == R.id.action_show_favorite_pages) {
                viewModel.toggleFilterFavouriteImages();
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

        ViewModelFactory vmFactory = new ViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(requireActivity(), vmFactory).get(ImageViewerViewModel.class);

        if (filterFavouritesLaunchRequest && filterFavouritesLaunchRequest != filterFavouritesLaunchState)
            viewModel.toggleFilterFavouriteImages();

        viewModel.getStartingIndex().observe(getViewLifecycleOwner(), this::onStartingIndexChanged);
        viewModel.getImages().observe(getViewLifecycleOwner(), this::onImagesChanged);
        viewModel.getShowFavouritesOnly().observe(getViewLifecycleOwner(), this::onShowFavouriteChanged);
    }

    @Override
    public void onDestroy() {
        if (recyclerView != null)
            recyclerView.setAdapter(null);
        recyclerView = null;

        if (filterFavouritesLaunchState != filterFavouritesState)
            viewModel.toggleFilterFavouriteImages();

        super.onDestroy();
    }

    private void onImagesChanged(List<ImageFile> images) {
        List<ImageFileItem> imgs = new ArrayList<>();
        for (ImageFile img : images) {
            ImageFileItem holder = new ImageFileItem(img);
            if (startIndex == img.getDisplayOrder()) holder.setCurrent(true);
            imgs.add(holder);
        }
        // Remove duplicates
        imgs = Stream.of(imgs).distinct().toList();
        FastAdapterDiffUtil.INSTANCE.set(itemAdapter, imgs, IMAGE_DIFF_CALLBACK);
        new Handler(Looper.getMainLooper()).postDelayed(this::moveToCurrent, 150);
    }

    private void onStartingIndexChanged(Integer startingIndex) {
        startIndex = startingIndex;
    }

    private void onShowFavouriteChanged(Boolean showFavouriteOnly) {
        if (!firstLoadDone) {
            filterFavouritesLaunchState = showFavouriteOnly;
            firstLoadDone = true;
        }
        filterFavouritesState = showFavouriteOnly;
        updateFavouriteDisplay(filterFavouritesState);
    }

    @SuppressLint("NonConstantResourceId")
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
            case R.id.action_toggle_favorite_pages:
                if (!selectedItems.isEmpty()) {
                    List<ImageFile> selectedContent = Stream.of(selectedItems).map(ImageFileItem::getImage).withoutNulls().toList();
                    viewModel.toggleImageFavourite(selectedContent, this::onFavouriteSuccess);
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
        ImageFile img = item.getImage();
        if (img != null) {
            viewModel.setReaderStartingIndex(img.getDisplayOrder());
            if (0 == getParentFragmentManager().getBackStackEntryCount()) { // Gallery mode (Library -> gallery -> pager)
                getParentFragmentManager()
                        .beginTransaction()
                        .replace(android.R.id.content, new ViewerPagerFragment())
                        .addToBackStack(null)
                        .commit();
            } else { // Pager mode (Library -> pager -> gallery -> pager)
                getParentFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE); // Leave only the latest element in the back stack
            }
            return true;
        }
        return false;
    }

    private void onFavouriteSuccess() {
        showFavouritePagesButton.setVisible(hasFavourite());
    }

    private void updateFavouriteDisplay(boolean showFavouritePages) {
        showFavouritePagesButton.setVisible(hasFavourite());
        showFavouritePagesButton.setIcon(showFavouritePages ? R.drawable.ic_filter_favs_on : R.drawable.ic_filter_favs_off);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    /**
     * Returns true if the current book has at least a favourite
     *
     * @return True if the current book has at least a favourite
     */
    private boolean hasFavourite() {
        List<ImageFileItem> images = itemAdapter.getAdapterItems();
        for (ImageFileItem item : images) if (item.isFavourite()) return true;
        return false;
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     */
    private void onSelectionChanged() {
        int selectedCount = selectExtension.getSelections().size();

        if (0 == selectedCount) {
            selectionToolbar.setVisibility(View.GONE);
            toolbar.setVisibility(View.VISIBLE);
            selectExtension.setSelectOnLongClick(true);
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
                .setPositiveButton(R.string.yes,
                        (dialog, which) -> {
                            selectExtension.deselect();
                            viewModel.deletePages(items, this::onDeleteError);
                        })
                .setNegativeButton(R.string.no,
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

    /**
     * Move the list to make the currently viewed image visible
     */
    private void moveToCurrent() {
        if (!firstMoveDone && recyclerView != null) {
            if (itemAdapter.getAdapterItemCount() > startIndex)
                recyclerView.scrollToPosition(startIndex);
            else recyclerView.scrollToPosition(0);
            firstMoveDone = true;
        }
    }

    /**
     * Display the yes/no dialog to make sure the user really wants to set the cover
     *
     * @param item Item that contains the image to set as a cover
     */
    private void askSetSelectedCover(@NonNull final ImageFile item) {
        Context context = getActivity();
        if (null == context) return;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        String title = context.getResources().getString(R.string.viewer_ask_cover);
        builder.setMessage(title)
                .setPositiveButton(R.string.yes,
                        (dialog, which) -> {
                            selectExtension.deselect();
                            viewModel.setCover(item);
                        })
                .setNegativeButton(R.string.no,
                        (dialog, which) -> selectExtension.deselect())
                .create().show();
    }
}
