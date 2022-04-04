package me.devsaki.hentoid.fragments.viewer;

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

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
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
import com.mikepenz.fastadapter.drag.ItemTouchCallback;
import com.mikepenz.fastadapter.drag.SimpleDragCallback;
import com.mikepenz.fastadapter.expandable.ExpandableExtension;
import com.mikepenz.fastadapter.select.SelectExtension;
import com.mikepenz.fastadapter.utils.DragDropUtil;
import com.skydoves.powerspinner.PowerSpinnerView;

import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.ImageViewerActivity;
import me.devsaki.hentoid.activities.bundles.ImageItemBundle;
import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.fragments.ProgressDialogFragment;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastHelper;
import me.devsaki.hentoid.util.exception.ContentNotProcessedException;
import me.devsaki.hentoid.viewholders.INestedItem;
import me.devsaki.hentoid.viewholders.ImageFileItem;
import me.devsaki.hentoid.viewholders.SubExpandableItem;
import me.devsaki.hentoid.viewmodels.ImageViewerViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import me.devsaki.hentoid.widget.DragSelectTouchListener;
import me.devsaki.hentoid.widget.FastAdapterPreClickSelectHelper;
import me.devsaki.hentoid.widget.ViewerKeyListener;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;
import timber.log.Timber;

public class ViewerGalleryFragment extends Fragment implements ItemTouchCallback {

    @IntDef({EditMode.NONE, EditMode.EDIT_CHAPTERS, EditMode.ADD_CHAPTER})
    @Retention(RetentionPolicy.SOURCE)
    public @interface EditMode {
        int NONE = 0; // Plain gallery
        int EDIT_CHAPTERS = 1; // Screen with foldable and draggable chapters
        int ADD_CHAPTER = 2; // Screen with tappable images to add and remove chapters
    }

    // ======== COMMUNICATION
    // Viewmodel
    private ImageViewerViewModel viewModel;
    // Activity
    private WeakReference<ImageViewerActivity> activity;

    // === UI
    private Toolbar toolbar;
    private Toolbar selectionToolbar;
    private MenuItem itemSetCoverMenu;
    private MenuItem showFavouritePagesMenu;
    private MenuItem editChaptersMenu;
    private MenuItem addChapterMenu;
    private MenuItem removeChaptersMenu;
    private PowerSpinnerView chaptersSelector;
    private RecyclerView recyclerView;
    private View chapterEditBottomHelpBanner;

    private final ItemAdapter<ImageFileItem> itemAdapter = new ItemAdapter<>();
    private final FastAdapter<ImageFileItem> fastAdapter = FastAdapter.with(itemAdapter);
    private SelectExtension<ImageFileItem> selectExtension;

    private final ItemAdapter<INestedItem<SubExpandableItem.ViewHolder>> itemAdapter2 = new ItemAdapter<>();
    private final FastAdapter<INestedItem<SubExpandableItem.ViewHolder>> fastAdapter2 = FastAdapter.with(itemAdapter2);
    private ItemTouchHelper touchHelper;

    private DragSelectTouchListener mDragSelectTouchListener = null;
    private DragSelectTouchListener mDragSelectTouchListener2 = null;

    // === VARIABLES
    // Used to ignore native calls to onBookClick right after that book has been deselected
    private int startIndex = 0;
    private boolean firstMoveDone = false;

    private @EditMode
    int editMode = EditMode.NONE;

    private boolean filterFavouritesState = false;
    private boolean shuffledState = false;


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

            return oldItem.isFavourite() == newItem.isFavourite() && oldItem.getChapterOrder() == newItem.getChapterOrder();
        }

        @Override
        public @org.jetbrains.annotations.Nullable Object getChangePayload(ImageFileItem oldItem, int oldPos, ImageFileItem newItem, int newPos) {
            ImageFile oldImage = oldItem.getImage();
            ImageFile newImage = newItem.getImage();

            if (null == oldImage || null == newImage) return false;

            ImageItemBundle diffBundleBuilder = new ImageItemBundle();

            if (oldImage.isFavourite() != newImage.isFavourite()) {
                diffBundleBuilder.setFavourite(newImage.isFavourite());
            }
            if (oldItem.getChapterOrder() != newItem.getChapterOrder()) {
                diffBundleBuilder.setChapterOrder(newItem.getChapterOrder());
            }

            if (diffBundleBuilder.isEmpty()) return null;
            else return diffBundleBuilder.getBundle();
        }
    };

    static ViewerGalleryFragment newInstance() {
        return new ViewerGalleryFragment();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (!(requireActivity() instanceof ImageViewerActivity))
            throw new IllegalStateException("Parent activity has to be a LibraryActivity");
        activity = new WeakReference<>((ImageViewerActivity) requireActivity());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_viewer_gallery, container, false);

        setHasOptionsMenu(true);

        recyclerView = requireViewById(rootView, R.id.viewer_gallery_recycler);
        updateListAdapter(editMode == EditMode.EDIT_CHAPTERS);

        new FastScrollerBuilder(recyclerView).build();
        recyclerView.setHasFixedSize(true);

        // Toolbar
        toolbar = requireViewById(rootView, R.id.viewer_gallery_toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackClick());

        toolbar.setOnMenuItemClickListener(clickedMenuItem -> {
            if (clickedMenuItem.getItemId() == R.id.action_show_favorite_pages) {
                viewModel.filterFavouriteImages(!filterFavouritesState);
            } else if (clickedMenuItem.getItemId() == R.id.action_edit_chapters) {
                setChapterEditMode(EditMode.EDIT_CHAPTERS);
            } else if (clickedMenuItem.getItemId() == R.id.action_add_remove_chapters) {
                setChapterEditMode(EditMode.ADD_CHAPTER);
            } else if (clickedMenuItem.getItemId() == R.id.action_remove_chapters) {
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
                String title = requireActivity().getString(R.string.ask_clear_chapters);
                builder.setMessage(title)
                        .setPositiveButton(R.string.yes,
                                (dialog, which) -> stripChapters())
                        .setNegativeButton(R.string.no,
                                (dialog, which) -> {
                                })
                        .create().show();
            }
            return true;
        });
        showFavouritePagesMenu = toolbar.getMenu().findItem(R.id.action_show_favorite_pages);
        editChaptersMenu = toolbar.getMenu().findItem(R.id.action_edit_chapters);
        removeChaptersMenu = toolbar.getMenu().findItem(R.id.action_remove_chapters);
        addChapterMenu = toolbar.getMenu().findItem(R.id.action_add_remove_chapters);

        selectionToolbar = requireViewById(rootView, R.id.viewer_gallery_selection_toolbar);
        itemSetCoverMenu = selectionToolbar.getMenu().findItem(R.id.action_set_cover);
        selectionToolbar.setNavigationOnClickListener(v -> {
            selectExtension.deselect(selectExtension.getSelections());
            selectionToolbar.setVisibility(View.GONE);
            toolbar.setVisibility(View.VISIBLE);
        });
        selectionToolbar.setOnMenuItemClickListener(this::onSelectionMenuItemClicked);

        chaptersSelector = requireViewById(rootView, R.id.chapter_selector);

        chapterEditBottomHelpBanner = requireViewById(rootView, R.id.chapter_edit_help_banner);
        updateToolbar();

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ViewModelFactory vmFactory = new ViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(requireActivity(), vmFactory).get(ImageViewerViewModel.class);

        viewModel.getStartingIndex().observe(getViewLifecycleOwner(), this::onStartingIndexChanged);
        viewModel.getViewerImages().observe(getViewLifecycleOwner(), this::onImagesChanged);
        viewModel.getContent().observe(getViewLifecycleOwner(), this::onContentChanged);
        viewModel.getShowFavouritesOnly().observe(getViewLifecycleOwner(), this::onShowFavouriteChanged);
        viewModel.getShuffled().observe(getViewLifecycleOwner(), this::onShuffledChanged);
    }

    @Override
    public void onDestroy() {
        if (recyclerView != null) recyclerView.setAdapter(null);
        recyclerView = null;
        if (chaptersSelector != null) {
            chaptersSelector.getSpinnerAdapter().setOnSpinnerItemSelectedListener(null); // Have to do that as the callback contains references to the fragment
            chaptersSelector.dismiss();
        }

        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((ImageViewerActivity) requireActivity()).registerKeyListener(
                new ViewerKeyListener().setOnBackListener(this::onBackClick)
        );
    }

    private void onBackClick() {
        if (editMode == EditMode.EDIT_CHAPTERS)
            setChapterEditMode(EditMode.NONE);
        else if (editMode == EditMode.ADD_CHAPTER)
            setChapterEditMode(EditMode.EDIT_CHAPTERS);
        else
            requireActivity().onBackPressed();
    }

    @Override
    public void onStop() {
        ((ImageViewerActivity) requireActivity()).unregisterKeyListener();
        super.onStop();
    }

    private void updateListAdapter(boolean isChapterEditMode) {
        if (isChapterEditMode) {
            if (!fastAdapter2.hasObservers()) fastAdapter2.setHasStableIds(true);
            itemAdapter2.clear();

            ExpandableExtension<INestedItem<SubExpandableItem.ViewHolder>> expandableExtension = fastAdapter2.getOrCreateExtension(ExpandableExtension.class);

            GridLayoutManager glm = (GridLayoutManager) recyclerView.getLayoutManager();
            if (glm != null) {
                int spanCount = Preferences.getViewerGalleryColumns();
                glm.setSpanCount(spanCount);

                // Use the correct size to display chapter separators, if any
                GridLayoutManager.SpanSizeLookup spanSizeLookup = new GridLayoutManager.SpanSizeLookup() {
                    @Override
                    public int getSpanSize(int position) {
                        if (fastAdapter2.getItemViewType(position) == R.id.expandable_item) {
                            return spanCount;
                        }
                        return 1;
                    }
                };
                glm.setSpanSizeLookup(spanSizeLookup);
            }

            // Activate drag & drop
            SimpleDragCallback dragCallback = new SimpleDragCallback(this);
            dragCallback.setNotifyAllDrops(true);
            touchHelper = new ItemTouchHelper(dragCallback);
            touchHelper.attachToRecyclerView(recyclerView);

            recyclerView.setAdapter(fastAdapter2);

            fastAdapter2.addEventHook(
                    new SubExpandableItem.DragHandlerTouchEvent(
                            position -> {
                                RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(position);
                                if (vh != null) touchHelper.startDrag(vh);
                                return null;
                            }
                    )
            );

            // Item click listener
            fastAdapter2.setOnClickListener((v, a, i, p) -> onNestedItemClick(i));

            // Select on swipe
            if (mDragSelectTouchListener != null) {
                recyclerView.removeOnItemTouchListener(mDragSelectTouchListener);
                mDragSelectTouchListener = null;
            }
        } else {
            if (!fastAdapter.hasObservers()) fastAdapter.setHasStableIds(true);
            itemAdapter.clear();

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

            GridLayoutManager glm = (GridLayoutManager) recyclerView.getLayoutManager();
            if (glm != null) {
                int spanCount = Preferences.getViewerGalleryColumns();
                glm.setSpanCount(spanCount);

                // Use the correct size to display chapter separators, if any
                GridLayoutManager.SpanSizeLookup spanSizeLookup = new GridLayoutManager.SpanSizeLookup() {
                    @Override
                    public int getSpanSize(int position) {
                        if (fastAdapter.getItemViewType(position) == R.id.expandable_item) {
                            return spanCount;
                        }
                        return 1;
                    }
                };
                glm.setSpanSizeLookup(spanSizeLookup);
            }

            if (touchHelper != null) touchHelper.attachToRecyclerView(null);

            recyclerView.setAdapter(fastAdapter);

            // Select on swipe
            DragSelectTouchListener.OnDragSelectListener onDragSelectionListener = (start, end, isSelected) -> selectExtension.select(IntStream.rangeClosed(start, end).boxed().toList());
            mDragSelectTouchListener = new DragSelectTouchListener()
                    .withSelectListener(onDragSelectionListener);
            if (mDragSelectTouchListener2 != null) {
                recyclerView.removeOnItemTouchListener(mDragSelectTouchListener2);
                mDragSelectTouchListener2 = null;
            }
            recyclerView.addOnItemTouchListener(mDragSelectTouchListener);
        }
    }

    private void onContentChanged(Content content) {
        if (null == content) return;
        List<Chapter> chapters = content.getChapters();
        if (null == chapters || chapters.isEmpty()) return;

        chaptersSelector.setOnFocusChangeListener(
                (v, hasFocus) -> Timber.i("hasFocus %s", hasFocus)
        );
        chaptersSelector.setItems(Stream.of(chapters).sortBy(Chapter::getOrder).filter(c -> c.getOrder() > -1).map(Chapter::getName).toList());
        chaptersSelector.selectItemByIndex(0);
        chaptersSelector.setOnSpinnerItemSelectedListener(
                (oldIndex, oldItem, newIndex, newItem) -> {
                    List<ImageFile> imgs = chapters.get(newIndex).getImageFiles();
                    if (imgs != null && !imgs.isEmpty()) {
                        viewModel.setReaderStartingIndex(imgs.get(0).getOrder() - 1);
                        moveToIndex(imgs.get(0).getOrder() - 1, true);
                    }
                    chaptersSelector.dismiss();
                }
        );

        chaptersSelector.setVisibility(View.VISIBLE);
    }

    private void onImagesChanged(List<ImageFile> images) {
        if (editMode == EditMode.EDIT_CHAPTERS) { // Expandable chapters
            List<INestedItem<SubExpandableItem.ViewHolder>> chapterItems = new ArrayList<>();

            boolean isArchive = false;
            if (!images.isEmpty())
                isArchive = images.get(0).getContent().getTarget().isArchive();

            List<Chapter> chapters = Stream.of(images)
                    .map(ImageFile::getLinkedChapter)
                    .withoutNulls()
                    .sortBy(Chapter::getOrder).filter(c -> c.getOrder() > -1).distinct().toList();

            int displayOrder = 0;
            for (Chapter c : chapters) {
                SubExpandableItem expandableItem = new SubExpandableItem(touchHelper, c.getName()).withDraggable(!isArchive);
                expandableItem.setIdentifier(c.getId());

                List<ImageFileItem> imgs = new ArrayList<>();
                List<ImageFile> chpImgs = c.getImageFiles();
                if (chpImgs != null) {
                    for (ImageFile img : chpImgs) {
                        // Reconstitute display order that has been lost because of @Transient property
                        img.setDisplayOrder(displayOrder++);
                        if (img.isReadable()) {
                            ImageFileItem holder = new ImageFileItem(img, false);
                            imgs.add(holder);
                        }
                    }
                }
                expandableItem.getSubItems().addAll(imgs);
                chapterItems.add(expandableItem);
            }

            // One last category for chapterless images
            List<ImageFile> chapterlessImages = Stream.of(images)
                    .filter(i -> null == i.getLinkedChapter())
                    .toList();

            if (!chapterlessImages.isEmpty()) {
                SubExpandableItem expandableItem = new SubExpandableItem(touchHelper, getResources().getString(R.string.gallery_no_chapter)).withDraggable(!isArchive);
                expandableItem.setIdentifier(Long.MAX_VALUE);

                List<ImageFileItem> imgs = new ArrayList<>();
                for (ImageFile img : chapterlessImages) {
                    ImageFileItem holder = new ImageFileItem(img, false);
                    imgs.add(holder);
                }
                expandableItem.getSubItems().addAll(imgs);
                chapterItems.add(expandableItem);
            }

            itemAdapter2.set(chapterItems);
        } else { // Classic gallery
            List<ImageFileItem> imgs = new ArrayList<>();
            for (ImageFile img : images) {
                ImageFileItem holder = new ImageFileItem(img, editMode == EditMode.ADD_CHAPTER);
                if (startIndex == img.getDisplayOrder()) holder.setCurrent(true);
                imgs.add(holder);
            }
            // Remove duplicates
            imgs = Stream.of(imgs).distinct().toList();
            FastAdapterDiffUtil.INSTANCE.set(itemAdapter, imgs, IMAGE_DIFF_CALLBACK);
        }
        updateToolbar();
        new Handler(Looper.getMainLooper()).postDelayed(() -> moveToIndex(startIndex, false), 150);
    }

    private void onStartingIndexChanged(Integer startingIndex) {
        startIndex = startingIndex;
    }

    private void onShowFavouriteChanged(Boolean showFavouriteOnly) {
        filterFavouritesState = showFavouriteOnly;
        updateFavouriteDisplay(filterFavouritesState);
    }

    private void onShuffledChanged(Boolean shuffled) {
        shuffledState = shuffled;
        updateToolbar();
    }

    @SuppressLint("NonConstantResourceId")
    private boolean onSelectionMenuItemClicked(@NonNull MenuItem menuItem) {
        Set<ImageFileItem> selectedItems = selectExtension.getSelectedItems();
        switch (menuItem.getItemId()) {
            case R.id.action_delete:
                if (!selectedItems.isEmpty()) {
                    List<ImageFile> selectedImages = Stream.of(selectedItems).map(ImageFileItem::getImage).withoutNulls().toList();
                    askDeleteSelected(selectedImages);
                }
                break;
            case R.id.action_set_cover:
                if (!selectedItems.isEmpty()) {
                    Optional<ImageFile> selectedImages = Stream.of(selectedItems).map(ImageFileItem::getImage).withoutNulls().findFirst();
                    if (selectedImages.isPresent()) askSetSelectedCover(selectedImages.get());
                }
                break;
            case R.id.action_toggle_favorite_pages:
                if (!selectedItems.isEmpty()) {
                    List<ImageFile> selectedImages = Stream.of(selectedItems).map(ImageFileItem::getImage).withoutNulls().toList();
                    viewModel.toggleImageFavourite(selectedImages, this::onFavouriteSuccess);
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

    private void updateToolbar() {
        showFavouritePagesMenu.setVisible(editMode == EditMode.NONE && hasFavourite());
        editChaptersMenu.setVisible(editMode == EditMode.NONE && !shuffledState);
        addChapterMenu.setVisible(editMode == EditMode.EDIT_CHAPTERS);
        removeChaptersMenu.setVisible(editMode == EditMode.EDIT_CHAPTERS);

        if (chaptersSelector.getSelectedIndex() > -1)
            chaptersSelector.setVisibility(editMode == EditMode.NONE ? View.VISIBLE : View.GONE);
    }

    private void updateSelectionToolbar(long selectedCount) {
        itemSetCoverMenu.setVisible(1 == selectedCount);
        selectionToolbar.setTitle(getResources().getQuantityString(R.plurals.items_selected, (int) selectedCount, (int) selectedCount));
    }

    private boolean onItemClick(ImageFileItem item) {
        ImageFile img = item.getImage();
        if (img != null) {
            if (editMode == EditMode.NONE) { // View image in gallery
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
            } else { // Create/remove chapter
                viewModel.createRemoveChapter(img, t -> ToastHelper.toast(R.string.chapter_toggle_failed, img.getOrder()));
            }
            return true;
        }
        return false;
    }

    private boolean onNestedItemClick(INestedItem<?> item) {
        if (item.getLevel() > 0) {
            ImageFile img = ((ImageFileItem) item).getImage();
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
        selectExtension.deselect(selectExtension.getSelections());
        selectExtension.setSelectOnLongClick(true);
        updateToolbar();
    }

    private void updateFavouriteDisplay(boolean showFavouritePages) {
        showFavouritePagesMenu.setIcon(showFavouritePages ? R.drawable.ic_filter_favs_on : R.drawable.ic_filter_favs_off);
        updateToolbar();
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
        Context context = activity.get();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        String title = context.getResources().getQuantityString(R.plurals.ask_delete_multiple, items.size(), items.size());
        builder.setMessage(title)
                .setPositiveButton(R.string.yes,
                        (dialog, which) -> {
                            selectExtension.deselect(selectExtension.getSelections());
                            viewModel.deletePages(items, this::onDeleteError);
                        })
                .setNegativeButton(R.string.no,
                        (dialog, which) -> selectExtension.deselect(selectExtension.getSelections()))
                .create().show();
    }

    /**
     * Callback for the failure of the "delete item" action
     */
    private void onDeleteError(Throwable t) {
        Timber.e(t);
        if (t instanceof ContentNotProcessedException) {
            ContentNotProcessedException e = (ContentNotProcessedException) t;
            String message = (null == e.getMessage()) ? getResources().getString(R.string.page_removal_failed) : e.getMessage();
            Snackbar.make(recyclerView, message, BaseTransientBottomBar.LENGTH_LONG).show();
        }
    }

    /**
     * Move the list to make the currently viewed image visible
     */
    private void moveToIndex(int index, boolean force) {
        if (recyclerView != null && (!firstMoveDone || force)) {
            if (itemAdapter.getAdapterItemCount() > index) {
                LinearLayoutManager llm = ((LinearLayoutManager) recyclerView.getLayoutManager());
                if (llm != null) llm.scrollToPositionWithOffset(index, 0);
            } else recyclerView.scrollToPosition(0);
            firstMoveDone = true;
        }
    }

    /**
     * Display the yes/no dialog to make sure the user really wants to set the cover
     *
     * @param item Item that contains the image to set as a cover
     */
    private void askSetSelectedCover(@NonNull final ImageFile item) {
        Context context = activity.get();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        String title = context.getResources().getString(R.string.viewer_ask_cover);
        builder.setMessage(title)
                .setPositiveButton(R.string.yes,
                        (dialog, which) -> {
                            selectExtension.deselect(selectExtension.getSelections());
                            viewModel.setCover(item);
                        })
                .setNegativeButton(R.string.no,
                        (dialog, which) -> selectExtension.deselect(selectExtension.getSelections()))
                .setOnCancelListener(dialog -> selectExtension.deselect(selectExtension.getSelections()))
                .create().show();
    }

    /**
     * Switch the screen into the given edit mode
     *
     * @param editMode Edit mode to switch the screen to
     */
    private void setChapterEditMode(@EditMode int editMode) {
        this.editMode = editMode;
        updateToolbar();

        chapterEditBottomHelpBanner.setVisibility(editMode == EditMode.ADD_CHAPTER ? View.VISIBLE : View.GONE);

        updateListAdapter(editMode == EditMode.EDIT_CHAPTERS);

        // Dismiss spinner dropdown
        if (chaptersSelector != null) chaptersSelector.dismiss();

        // Don't filter favs when editing chapters
        if (filterFavouritesState) {
            viewModel.filterFavouriteImages(editMode == EditMode.NONE);
        } else viewModel.repostImages();
    }

    /**
     * Strip all chapters from the current content
     */
    private void stripChapters() {
        viewModel.stripChapters(t -> ToastHelper.toast(R.string.chapters_remove_failed));
    }

    @Override
    public void itemTouchDropped(int oldPosition, int newPosition) {
        if (oldPosition == newPosition) return;
        if (oldPosition < 0 || newPosition < 0) return;
        if (itemAdapter2.getAdapterItem(oldPosition).getLevel() > 0) return;
        long nbLevelZeroItems = Stream.of(itemAdapter2.getAdapterItems()).filter(i -> 0 == i.getLevel()).count();
        if (newPosition > nbLevelZeroItems - 1) return;

        // Save final position of item in DB
        viewModel.moveChapter(oldPosition, newPosition, this::onChapterMoveError);
        ProgressDialogFragment.invoke(
                getParentFragmentManager(),
                getResources().getString(R.string.renaming_progress), R.plurals.file);
    }

    private void onChapterMoveError(Throwable t) {
        Timber.e(t);
        Snackbar.make(recyclerView, R.string.chapter_move_failed, BaseTransientBottomBar.LENGTH_LONG).show();
    }


    @Override
    public boolean itemTouchOnMove(int oldPosition, int newPosition) {
        if (oldPosition < 0 || newPosition < 0) return false;
        if (itemAdapter2.getAdapterItem(oldPosition).getLevel() > 0) return false;
        long nbLevelZeroItems = Stream.of(itemAdapter2.getAdapterItems()).filter(i -> 0 == i.getLevel()).count();
        if (newPosition > nbLevelZeroItems - 1) return false;

        // Update visuals
        DragDropUtil.onMove(itemAdapter2, oldPosition, newPosition);
        return true;
    }

    @Override
    public void itemTouchStartDrag(@NonNull RecyclerView.ViewHolder viewHolder) {
        // Nothing
    }

    @Override
    public void itemTouchStopDrag(@NonNull RecyclerView.ViewHolder viewHolder) {
        // Nothing
    }
}
