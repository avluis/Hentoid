package me.devsaki.hentoid.fragments.library;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.DimenRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.PopupMenu;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.AsyncDifferConfig;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.IAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.diff.DiffCallback;
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil;
import com.mikepenz.fastadapter.drag.ItemTouchCallback;
import com.mikepenz.fastadapter.extensions.ExtensionsFactories;
import com.mikepenz.fastadapter.listeners.ClickEventHook;
import com.mikepenz.fastadapter.paged.PagedModelAdapter;
import com.mikepenz.fastadapter.select.SelectExtension;
import com.mikepenz.fastadapter.select.SelectExtensionFactory;
import com.mikepenz.fastadapter.swipe.SimpleSwipeDrawerCallback;
import com.mikepenz.fastadapter.swipe_drag.SimpleSwipeDrawerDragCallback;
import com.mikepenz.fastadapter.utils.DragDropUtil;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.LibraryActivity;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.activities.SearchActivity;
import me.devsaki.hentoid.activities.bundles.ContentItemBundle;
import me.devsaki.hentoid.activities.bundles.SearchActivityBundle;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.AppUpdatedEvent;
import me.devsaki.hentoid.events.CommunicationEvent;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Debouncer;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.util.ToastHelper;
import me.devsaki.hentoid.viewholders.ContentItem;
import me.devsaki.hentoid.viewholders.IDraggableViewHolder;
import me.devsaki.hentoid.viewholders.ISwipeableViewHolder;
import me.devsaki.hentoid.viewmodels.LibraryViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import me.devsaki.hentoid.widget.AddQueueMenu;
import me.devsaki.hentoid.widget.AutofitGridLayoutManager;
import me.devsaki.hentoid.widget.FastAdapterPreClickSelectHelper;
import me.devsaki.hentoid.widget.LibraryPager;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;
import static com.annimon.stream.Collectors.toCollection;
import static me.devsaki.hentoid.events.CommunicationEvent.EV_ADVANCED_SEARCH;
import static me.devsaki.hentoid.events.CommunicationEvent.EV_DISABLE;
import static me.devsaki.hentoid.events.CommunicationEvent.EV_ENABLE;
import static me.devsaki.hentoid.events.CommunicationEvent.EV_SEARCH;
import static me.devsaki.hentoid.events.CommunicationEvent.EV_UPDATE_SORT;
import static me.devsaki.hentoid.events.CommunicationEvent.RC_CONTENTS;
import static me.devsaki.hentoid.util.Preferences.Constant.QUEUE_NEW_DOWNLOADS_POSITION_ASK;
import static me.devsaki.hentoid.util.Preferences.Constant.QUEUE_NEW_DOWNLOADS_POSITION_BOTTOM;
import static me.devsaki.hentoid.util.Preferences.Constant.QUEUE_NEW_DOWNLOADS_POSITION_TOP;

@SuppressLint("NonConstantResourceId")
public class LibraryContentFragment extends Fragment implements ChangeGroupDialogFragment.Parent, ItemTouchCallback, SimpleSwipeDrawerCallback.ItemSwipeCallback {

    private static final String KEY_LAST_LIST_POSITION = "last_list_position";


    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    // ======== COMMUNICATION
    private OnBackPressedCallback callback;
    // Viewmodel
    private LibraryViewModel viewModel;
    // Settings listener
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (p, k) -> onSharedPreferenceChanged(k);
    // Activity
    private WeakReference<LibraryActivity> activity;


    // ======== UI
    // Wrapper for the bottom pager
    private final LibraryPager pager = new LibraryPager(this::handleNewPage);
    // Text that displays in the background when the list is empty
    private TextView emptyText;
    // Main view where books are displayed
    private RecyclerView recyclerView;
    // LayoutManager of the recyclerView
    private LinearLayoutManager llm;

    // === SORT TOOLBAR
    // Sort direction button
    private ImageView sortDirectionButton;
    // Sort reshuffle button
    private View sortReshuffleButton;
    // Sort field button
    private TextView sortFieldButton;

    // === FASTADAPTER COMPONENTS AND HELPERS
    private ItemAdapter<ContentItem> itemAdapter;
    private PagedModelAdapter<Content, ContentItem> pagedItemAdapter;
    private FastAdapter<ContentItem> fastAdapter;
    private SelectExtension<ContentItem> selectExtension;
    private ItemTouchHelper touchHelper;


    // ======== VARIABLES
    // Records the system time (ms) when back button has been last pressed (to detect "double back button" event)
    private long backButtonPressed;
    // Total number of books in the whole unfiltered library
    private int totalContentCount;
    // True when a new search has been performed and its results have not been handled yet
    // False when the refresh is passive (i.e. not from a direct user action)
    private boolean newSearch = false;
    // Collection of books according to current filters
    private PagedList<Content> library;
    // Position of top item to memorize or restore (used when activity is destroyed and recreated)
    private int topItemPosition = -1;
    // TODO doc
    private Group group = null;
    // TODO doc
    private boolean enabled = true;

    // Used to start processing when the recyclerView has finished updating
    private Debouncer<Integer> listRefreshDebouncer;
    private int itemToRefreshIndex = -1;
    private boolean excludeClicked = false;

    // Launches the search activity according to the returned result
    private final ActivityResultLauncher<Intent> advancedSearchReturnLauncher =
            registerForActivityResult(new StartActivityForResult(), this::advancedSearchReturnResult);

    /**
     * Diff calculation rules for contents
     * <p>
     * Created once and for all to be used by FastAdapter in endless mode (=using Android PagedList)
     */
    // The one for the PagedList (endless mode)
    private final AsyncDifferConfig<Content> asyncDifferConfig = new AsyncDifferConfig.Builder<>(new DiffUtil.ItemCallback<Content>() {
        @Override
        public boolean areItemsTheSame(@NonNull Content oldItem, @NonNull Content newItem) {
            return oldItem.uniqueHash() == newItem.uniqueHash();
        }

        // Using equals for consistency with hashcode (see comment on Content#equals)
        @Override
        public boolean areContentsTheSame(@NonNull Content oldItem, @NonNull Content newItem) {
            return Objects.equals(oldItem, newItem);
        }

        @Nullable
        @Override
        public Object getChangePayload(@NonNull Content oldItem, @NonNull Content newItem) {
            ContentItemBundle.Builder diffBundleBuilder = new ContentItemBundle.Builder();

            if (oldItem.isFavourite() != newItem.isFavourite()) {
                diffBundleBuilder.setIsFavourite(newItem.isFavourite());
            }
            if (oldItem.isCompleted() != newItem.isCompleted()) {
                diffBundleBuilder.setIsCompleted(newItem.isCompleted());
            }
            if (oldItem.getReads() != newItem.getReads()) {
                diffBundleBuilder.setReads(newItem.getReads());
            }
            if (oldItem.getReadPagesCount() != newItem.getReadPagesCount()) {
                diffBundleBuilder.setReadPagesCount(newItem.getReadPagesCount());
            }
            if (!oldItem.getCoverImageUrl().equals(newItem.getCoverImageUrl())) {
                diffBundleBuilder.setCoverUri(newItem.getCover().getFileUri());
            }

            if (diffBundleBuilder.isEmpty()) return null;
            else return diffBundleBuilder.getBundle();
        }

    }).build();

    // The one for "classic" List (paged mode)
    public static final DiffCallback<ContentItem> CONTENT_ITEM_DIFF_CALLBACK = new DiffCallback<ContentItem>() {
        @Override
        public boolean areItemsTheSame(ContentItem oldItem, ContentItem newItem) {
            return oldItem.getIdentifier() == newItem.getIdentifier();
        }

        @Override
        public boolean areContentsTheSame(ContentItem oldContentItem, ContentItem newContentItem) {
            return Objects.equals(oldContentItem.getContent(), newContentItem.getContent());
        }

        @Override
        public @org.jetbrains.annotations.Nullable Object getChangePayload(ContentItem oldContentItem, int oldPos, ContentItem newContentItem, int newPos) {
            Content oldItem = oldContentItem.getContent();
            Content newItem = newContentItem.getContent();

            if (null == oldItem || null == newItem) return false;

            ContentItemBundle.Builder diffBundleBuilder = new ContentItemBundle.Builder();

            if (oldItem.isFavourite() != newItem.isFavourite()) {
                diffBundleBuilder.setIsFavourite(newItem.isFavourite());
            }
            if (oldItem.isCompleted() != newItem.isCompleted()) {
                diffBundleBuilder.setIsCompleted(newItem.isCompleted());
            }
            if (oldItem.getReads() != newItem.getReads()) {
                diffBundleBuilder.setReads(newItem.getReads());
            }
            if (oldItem.getReadPagesCount() != newItem.getReadPagesCount()) {
                diffBundleBuilder.setReadPagesCount(newItem.getReadPagesCount());
            }
            if (!oldItem.getCoverImageUrl().equals(newItem.getCoverImageUrl())) {
                diffBundleBuilder.setCoverUri(newItem.getCover().getFileUri());
            }

            if (diffBundleBuilder.isEmpty()) return null;
            else return diffBundleBuilder.getBundle();
        }
    };

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (!(requireActivity() instanceof LibraryActivity))
            throw new IllegalStateException("Parent activity has to be a LibraryActivity");
        activity = new WeakReference<>((LibraryActivity) requireActivity());

        listRefreshDebouncer = new Debouncer<>(context, 75, this::onRecyclerUpdated);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ExtensionsFactories.INSTANCE.register(new SelectExtensionFactory());
        EventBus.getDefault().register(this);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_library_content, container, false);

        Preferences.registerPrefsChangedListener(prefsListener);

        ViewModelFactory vmFactory = new ViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(requireActivity(), vmFactory).get(LibraryViewModel.class);

        initUI(rootView);
        activity.get().initFragmentToolbars(selectExtension, this::toolbarOnItemClicked, this::selectionToolbarOnItemClicked);

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel.getNewSearch().observe(getViewLifecycleOwner(), this::onNewSearch);
        viewModel.getLibraryPaged().observe(getViewLifecycleOwner(), this::onLibraryChanged);
        viewModel.getTotalContent().observe(getViewLifecycleOwner(), this::onTotalContentChanged);
        viewModel.getGroup().observe(getViewLifecycleOwner(), this::onGroupChanged);

        viewModel.updateContentOrder(); // Trigger a blank search

        // Display pager tooltip
        if (pager.isVisible()) pager.showTooltip(getViewLifecycleOwner());
    }

    public void onEnable() {
        enabled = true;
        callback.setEnabled(true);
    }

    public void onDisable() {
        enabled = false;
        callback.setEnabled(false);
    }

    /**
     * Initialize the UI components
     *
     * @param rootView Root view of the library screen
     */
    private void initUI(@NonNull View rootView) {
        emptyText = requireViewById(rootView, R.id.library_empty_txt);

        sortDirectionButton = activity.get().getSortDirectionButton();
        sortReshuffleButton = activity.get().getSortReshuffleButton();
        sortFieldButton = activity.get().getSortFieldButton();

        // RecyclerView
        recyclerView = requireViewById(rootView, R.id.library_list);
        if (Preferences.Constant.LIBRARY_DISPLAY_LIST == Preferences.getLibraryDisplay())
            llm = new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false);
        else
            llm = new AutofitGridLayoutManager(requireContext(), (int) getResources().getDimension(R.dimen.card_grid_width));
        recyclerView.setLayoutManager(llm);
        new FastScrollerBuilder(recyclerView).build();

        // Pager
        pager.initUI(rootView);
        setPagingMethod(Preferences.getEndlessScroll(), false);

        updateSortControls();
        addCustomBackControl();
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

    private void updateSortControls() {
        // Sort controls
        sortDirectionButton.setImageResource(Preferences.isContentSortDesc() ? R.drawable.ic_simple_arrow_down : R.drawable.ic_simple_arrow_up);
        sortDirectionButton.setOnClickListener(v -> {
            boolean sortDesc = !Preferences.isContentSortDesc();
            Preferences.setContentSortDesc(sortDesc);
            // Update icon
            sortDirectionButton.setImageResource(sortDesc ? R.drawable.ic_simple_arrow_down : R.drawable.ic_simple_arrow_up);
            // Run a new search
            viewModel.updateContentOrder();
            activity.get().sortCommandsAutoHide(true, null);
        });
        sortReshuffleButton.setOnClickListener(v -> {
            viewModel.shuffleContent();
            viewModel.updateContentOrder();
            activity.get().sortCommandsAutoHide(true, null);
        });
        sortFieldButton.setText(getNameFromFieldCode(Preferences.getContentSortField()));
        sortFieldButton.setOnClickListener(v -> {
            // Load and display the field popup menu
            PopupMenu popup = new PopupMenu(requireContext(), sortFieldButton, Gravity.END);
            popup.getMenuInflater()
                    .inflate(R.menu.library_books_sort_popup, popup.getMenu());
            popup.getMenu().findItem(R.id.sort_custom).setVisible(group != null && group.hasCustomBookOrder);
            popup.setOnMenuItemClickListener(item -> {
                // Update button text
                sortFieldButton.setText(item.getTitle());
                item.setChecked(true);
                int fieldCode = getFieldCodeFromMenuId(item.getItemId());
                if (fieldCode == Preferences.Constant.ORDER_FIELD_RANDOM) {
                    viewModel.shuffleContent();
                    sortDirectionButton.setVisibility(View.GONE);
                    sortReshuffleButton.setVisibility(View.VISIBLE);
                } else {
                    sortReshuffleButton.setVisibility(View.GONE);
                    sortDirectionButton.setVisibility(View.VISIBLE);
                }

                Preferences.setContentSortField(fieldCode);
                // Run a new search
                viewModel.updateContentOrder();
                activity.get().sortCommandsAutoHide(true, popup);
                return true;
            });
            popup.show(); //showing popup menu
            activity.get().sortCommandsAutoHide(true, popup);
        }); //closing the setOnClickListener method
    }

    private String getQuery() {
        return activity.get().getQuery();
    }

    private void setQuery(String query) {
        activity.get().setQuery(query);
    }

    private List<Attribute> getMetadata() {
        return activity.get().getMetadata();
    }

    private void setMetadata(List<Attribute> attrs) {
        activity.get().setMetadata(attrs);
    }

    private int getFieldCodeFromMenuId(@IdRes int menuId) {
        switch (menuId) {
            case (R.id.sort_title):
                return Preferences.Constant.ORDER_FIELD_TITLE;
            case (R.id.sort_artist):
                return Preferences.Constant.ORDER_FIELD_ARTIST;
            case (R.id.sort_pages):
                return Preferences.Constant.ORDER_FIELD_NB_PAGES;
            case (R.id.sort_dl_date):
                return Preferences.Constant.ORDER_FIELD_DOWNLOAD_DATE;
            case (R.id.sort_read_date):
                return Preferences.Constant.ORDER_FIELD_READ_DATE;
            case (R.id.sort_reads):
                return Preferences.Constant.ORDER_FIELD_READS;
            case (R.id.sort_size):
                return Preferences.Constant.ORDER_FIELD_SIZE;
            case (R.id.sort_reading_progress):
                return Preferences.Constant.ORDER_FIELD_READ_PROGRESS;
            case (R.id.sort_custom):
                return Preferences.Constant.ORDER_FIELD_CUSTOM;
            case (R.id.sort_random):
                return Preferences.Constant.ORDER_FIELD_RANDOM;
            default:
                return Preferences.Constant.ORDER_FIELD_NONE;
        }
    }

    private int getNameFromFieldCode(int prefFieldCode) {
        switch (prefFieldCode) {
            case (Preferences.Constant.ORDER_FIELD_TITLE):
                return R.string.sort_title;
            case (Preferences.Constant.ORDER_FIELD_ARTIST):
                return R.string.sort_artist;
            case (Preferences.Constant.ORDER_FIELD_NB_PAGES):
                return R.string.sort_pages;
            case (Preferences.Constant.ORDER_FIELD_DOWNLOAD_DATE):
                return R.string.sort_dl_date;
            case (Preferences.Constant.ORDER_FIELD_READ_DATE):
                return R.string.sort_read_date;
            case (Preferences.Constant.ORDER_FIELD_READS):
                return R.string.sort_reads;
            case (Preferences.Constant.ORDER_FIELD_SIZE):
                return R.string.sort_size;
            case (Preferences.Constant.ORDER_FIELD_READ_PROGRESS):
                return R.string.sort_reading_progress;
            case (Preferences.Constant.ORDER_FIELD_CUSTOM):
                return R.string.sort_custom;
            case (Preferences.Constant.ORDER_FIELD_RANDOM):
                return R.string.sort_random;
            default:
                return R.string.sort_invalid;
        }
    }

    private void toggleEditMode() {
        activity.get().toggleEditMode();

        // Leave edit mode by validating => Save new item position
        if (!activity.get().isEditMode()) {
            // Set ordering field to custom
            Preferences.setContentSortField(Preferences.Constant.ORDER_FIELD_CUSTOM);
            sortFieldButton.setText(getNameFromFieldCode(Preferences.Constant.ORDER_FIELD_CUSTOM));
            // Set ordering direction to ASC (we just manually ordered stuff; it has to be displayed as is)
            Preferences.setContentSortDesc(false);
            viewModel.saveContentPositions(Stream.of(itemAdapter.getAdapterItems()).map(ContentItem::getContent).withoutNulls().toList(), this::refreshIfNeeded);
            group.hasCustomBookOrder = true;
        } else if (group.hasCustomBookOrder) { // Enter edit mode -> warn if a custom order already exists
            new MaterialAlertDialogBuilder(requireContext(), ThemeHelper.getIdForCurrentTheme(requireContext(), R.style.Theme_Light_Dialog))
                    .setIcon(R.drawable.ic_warning)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.menu_edit_warning_custom)
                    .setPositiveButton(R.string.yes,
                            (dialog1, which) -> dialog1.dismiss())
                    .setNegativeButton(R.string.no,
                            (dialog2, which) -> {
                                dialog2.dismiss();
                                cancelEditMode();
                            })
                    .create()
                    .show();
        }

        setPagingMethod(Preferences.getEndlessScroll(), activity.get().isEditMode());
    }

    private void cancelEditMode() {
        activity.get().setEditMode(false);
        setPagingMethod(Preferences.getEndlessScroll(), false);
    }

    private boolean toolbarOnItemClicked(@NonNull MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.action_edit:
                toggleEditMode();
                break;
            case R.id.action_edit_cancel:
                cancelEditMode();
                break;
            default:
                return activity.get().toolbarOnItemClicked(menuItem);
        }
        return true;
    }

    private boolean selectionToolbarOnItemClicked(@NonNull MenuItem menuItem) {
        boolean keepToolbar = false;
        switch (menuItem.getItemId()) {
            case R.id.action_share:
                shareSelectedItems();
                break;
            case R.id.action_delete:
                deleteSelectedItems();
                break;
            case R.id.action_completed:
                markSelectedAsCompleted();
                break;
            case R.id.action_archive:
                archiveSelectedItems();
                break;
            case R.id.action_change_group:
                moveSelectedItems();
                break;
            case R.id.action_open_folder:
                openItemFolder();
                break;
            case R.id.action_redownload:
                askRedownloadSelectedItemsScratch();
                keepToolbar = true;
                break;
            case R.id.action_download:
                askDownloadSelectedItems();
                keepToolbar = true;
                break;
            case R.id.action_stream:
                askStreamSelectedItems();
                keepToolbar = true;
                break;
            case R.id.action_selectAll:
                // Make certain _everything_ is properly selected (selectExtension.select() as doesn't get everything the 1st time it's called)
                int count = 0;
                while (selectExtension.getSelections().size() < getItemAdapter().getAdapterItemCount() && ++count < 5)
                    selectExtension.select(Stream.range(0, getItemAdapter().getAdapterItemCount()).toList());
                keepToolbar = true;
                break;
            case R.id.action_set_cover:
                askSetCover();
                break;
            default:
                activity.get().getSelectionToolbar().setVisibility(View.GONE);
                return false;
        }
        if (!keepToolbar) activity.get().getSelectionToolbar().setVisibility(View.GONE);
        return true;
    }


    /**
     * Callback for the "share item" action button
     */
    private void shareSelectedItems() {
        Set<ContentItem> selectedItems = selectExtension.getSelectedItems();
        Context context = getActivity();
        if (1 == selectedItems.size() && context != null) {
            Content c = Stream.of(selectedItems).findFirst().get().getContent();
            if (c != null) ContentHelper.shareContent(context, c);
        }
    }

    /**
     * Callback for the "delete item" action button
     */
    private void deleteSelectedItems() {
        Set<ContentItem> selectedItems = selectExtension.getSelectedItems();
        if (!selectedItems.isEmpty()) {
            List<Content> selectedContent = Stream.of(selectedItems).map(ContentItem::getContent).withoutNulls().toList();
            // Remove external items if they can't be deleted
            if (!Preferences.isDeleteExternalLibrary())
                selectedContent = Stream.of(selectedContent).filterNot(c -> c.getStatus().equals(StatusContent.EXTERNAL)).toList();
            if (!selectedContent.isEmpty())
                activity.get().askDeleteItems(selectedContent, Collections.emptyList(), this::refreshIfNeeded, selectExtension);
        }
    }

    /**
     * Callback for "book completed" action button
     */
    private void markSelectedAsCompleted() {
        Set<ContentItem> selectedItems = selectExtension.getSelectedItems();
        if (!selectedItems.isEmpty()) {
            List<Content> selectedContent = Stream.of(selectedItems).map(ContentItem::getContent).withoutNulls().toList();
            if (!selectedContent.isEmpty()) {
                viewModel.toggleContentCompleted(selectedContent, this::refreshIfNeeded);
                selectExtension.deselect(selectExtension.getSelections());
            }
        }
    }

    /**
     * Callback for the "archive item" action button
     */
    private void archiveSelectedItems() {
        Set<ContentItem> selectedItems = selectExtension.getSelectedItems();
        List<Content> contents = Stream.of(selectedItems)
                .map(ContentItem::getContent)
                .withoutNulls()
                .filterNot(c -> c.getStorageUri().isEmpty())
                .toList();
        activity.get().askArchiveItems(contents, selectExtension);
    }

    /**
     * Callback for the "change group" action button
     */
    private void moveSelectedItems() {
        Set<ContentItem> selectedItems = selectExtension.getSelectedItems();
        selectExtension.deselect(selectExtension.getSelections());
        List<Long> bookIds = Stream.of(selectedItems).map(ContentItem::getContent).withoutNulls().map(Content::getId).toList();
        ChangeGroupDialogFragment.invoke(this, Helper.getPrimitiveLongArrayFromList(bookIds));
    }

    /**
     * Callback for the "open containing folder" action button
     */
    private void openItemFolder() {
        Set<ContentItem> selectedItems = selectExtension.getSelectedItems();
        Context context = getActivity();
        if (1 == selectedItems.size() && context != null) {
            Content c = Stream.of(selectedItems).findFirst().get().getContent();
            if (c != null) {
                if (c.getStorageUri().isEmpty()) {
                    ToastHelper.toast(R.string.folder_undefined);
                    return;
                }

                DocumentFile folder = FileHelper.getFolderFromTreeUriString(requireContext(), c.getStorageUri());
                if (folder != null) {
                    selectExtension.deselect(selectExtension.getSelections());
                    activity.get().getSelectionToolbar().setVisibility(View.GONE);
                    FileHelper.openFile(requireContext(), folder);
                }
            }
        }
    }

    /**
     * Callback for the "redownload from scratch" action button
     */
    private void askRedownloadSelectedItemsScratch() {
        Set<ContentItem> selectedItems = selectExtension.getSelectedItems();

        int externalContent = 0;
        List<Content> contents = new ArrayList<>();
        for (ContentItem ci : selectedItems) {
            Content c = ci.getContent();
            if (null == c) continue;
            if (c.getStatus().equals(StatusContent.EXTERNAL)) {
                externalContent++;
            } else {
                contents.add(c);
            }
        }

        String message = getResources().getQuantityString(R.plurals.redownload_confirm, contents.size());
        if (externalContent > 0)
            message = getResources().getQuantityString(R.plurals.redownload_external_content, externalContent);

        new MaterialAlertDialogBuilder(requireContext(), ThemeHelper.getIdForCurrentTheme(requireContext(), R.style.Theme_Light_Dialog))
                .setIcon(R.drawable.ic_warning)
                .setCancelable(false)
                .setTitle(R.string.app_name)
                .setMessage(message)
                .setPositiveButton(R.string.yes,
                        (dialog1, which) -> {
                            dialog1.dismiss();
                            redownloadFromScratch(contents);
                            for (ContentItem ci : selectedItems) ci.setSelected(false);
                            selectExtension.setSelectOnLongClick(true);
                            selectExtension.deselect(selectExtension.getSelections());
                            activity.get().getSelectionToolbar().setVisibility(View.GONE);
                        })
                .setNegativeButton(R.string.no,
                        (dialog12, which) -> dialog12.dismiss())
                .create()
                .show();
    }

    /**
     * Callback for the "Download" action button
     */
    private void askDownloadSelectedItems() {
        Set<ContentItem> selectedItems = selectExtension.getSelectedItems();

        int nonOnlineContent = 0;
        List<Content> contents = new ArrayList<>();
        for (ContentItem ci : selectedItems) {
            Content c = ci.getContent();
            if (null == c) continue;
            if (!c.getStatus().equals(StatusContent.ONLINE)) {
                nonOnlineContent++;
            } else {
                contents.add(c);
            }
        }

        String message = getResources().getQuantityString(R.plurals.download_confirm, contents.size());
        if (nonOnlineContent > 0)
            message = getResources().getQuantityString(R.plurals.download_non_online_content, nonOnlineContent);

        new MaterialAlertDialogBuilder(requireContext(), ThemeHelper.getIdForCurrentTheme(requireContext(), R.style.Theme_Light_Dialog))
                .setIcon(R.drawable.ic_warning)
                .setCancelable(false)
                .setTitle(R.string.app_name)
                .setMessage(message)
                .setPositiveButton(R.string.yes,
                        (dialog1, which) -> {
                            dialog1.dismiss();
                            download(contents);
                            for (ContentItem ci : selectedItems) ci.setSelected(false);
                            selectExtension.setSelectOnLongClick(true);
                            selectExtension.deselect(selectExtension.getSelections());
                            activity.get().getSelectionToolbar().setVisibility(View.GONE);
                        })
                .setNegativeButton(R.string.no,
                        (dialog12, which) -> dialog12.dismiss())
                .create()
                .show();
    }

    /**
     * Callback for the "Switch to streaming" action button
     */
    private void askStreamSelectedItems() {
        Set<ContentItem> selectedItems = selectExtension.getSelectedItems();

        int onlineExternalContent = 0;
        List<Content> contents = new ArrayList<>();
        for (ContentItem ci : selectedItems) {
            Content c = ci.getContent();
            if (null == c) continue;
            if (c.getStatus().equals(StatusContent.ONLINE) || c.getStatus().equals(StatusContent.EXTERNAL)) {
                onlineExternalContent++;
            } else {
                contents.add(c);
            }
        }

        String message = getResources().getQuantityString(R.plurals.stream_confirm, contents.size());
        if (onlineExternalContent > 0)
            message = getResources().getQuantityString(R.plurals.stream_external_online_content, onlineExternalContent);

        new MaterialAlertDialogBuilder(requireContext(), ThemeHelper.getIdForCurrentTheme(requireContext(), R.style.Theme_Light_Dialog))
                .setIcon(R.drawable.ic_warning)
                .setCancelable(false)
                .setTitle(R.string.app_name)
                .setMessage(message)
                .setPositiveButton(R.string.yes,
                        (dialog1, which) -> {
                            dialog1.dismiss();
                            stream(contents);
                            for (ContentItem ci : selectedItems) ci.setSelected(false);
                            selectExtension.setSelectOnLongClick(true);
                            selectExtension.deselect(selectExtension.getSelections());
                            activity.get().getSelectionToolbar().setVisibility(View.GONE);
                        })
                .setNegativeButton(R.string.no,
                        (dialog12, which) -> dialog12.dismiss())
                .create()
                .show();
    }

    /**
     * Callback for the "set as group cover" action button
     */
    private void askSetCover() {
        Set<ContentItem> selectedItems = selectExtension.getSelectedItems();
        if (selectedItems.isEmpty()) return;

        Content content = Stream.of(selectedItems).findFirst().get().getContent();

        new MaterialAlertDialogBuilder(requireContext(), ThemeHelper.getIdForCurrentTheme(requireContext(), R.style.Theme_Light_Dialog))
                .setCancelable(false)
                .setTitle(R.string.app_name)
                .setMessage(getResources().getString(R.string.group_make_cover_ask))
                .setPositiveButton(R.string.yes,
                        (dialog1, which) -> {
                            dialog1.dismiss();
                            viewModel.setGroupCover(group.id, content.getCover());
                            for (ContentItem ci : selectedItems) ci.setSelected(false);
                            selectExtension.deselect(selectExtension.getSelections());
                            activity.get().getSelectionToolbar().setVisibility(View.GONE);
                        })
                .setNegativeButton(R.string.no,
                        (dialog12, which) -> dialog12.dismiss())
                .create()
                .show();
    }

    /**
     * Indicates whether a search query is active (using universal search or advanced search) or not
     *
     * @return True if a search query is active (using universal search or advanced search); false if not (=whole unfiltered library selected)
     */
    private boolean isSearchQueryActive() {
        return activity.get().isSearchQueryActive();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (viewModel != null) viewModel.onSaveState(outState);
        if (fastAdapter != null) fastAdapter.saveInstanceState(outState);

        // Remember current position in the sorted list
        int currentPosition = getTopItemPosition();
        if (currentPosition > 0 || -1 == topItemPosition) topItemPosition = currentPosition;

        outState.putInt(KEY_LAST_LIST_POSITION, topItemPosition);
        //topItemPosition = -1;
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);

        topItemPosition = 0;
        if (null == savedInstanceState) return;

        if (viewModel != null) viewModel.onRestoreState(savedInstanceState);
        if (fastAdapter != null) fastAdapter.withSavedInstanceState(savedInstanceState);
        // Mark last position in the list to be the one it will come back to
        topItemPosition = savedInstanceState.getInt(KEY_LAST_LIST_POSITION, 0);
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onAppUpdated(AppUpdatedEvent event) {
        EventBus.getDefault().removeStickyEvent(event);
        // Display the "update success" dialog when an update is detected on a release version
        if (!BuildConfig.DEBUG) UpdateSuccessDialogFragment.invoke(getParentFragmentManager());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onActivityEvent(CommunicationEvent event) {
        if (event.getRecipient() != RC_CONTENTS || null == sortDirectionButton) return;
        switch (event.getType()) {
            case EV_SEARCH:
                if (event.getMessage() != null) onSubmitSearch(event.getMessage());
                break;
            case EV_ADVANCED_SEARCH:
                onAdvancedSearchButtonClick();
                break;
            case EV_UPDATE_SORT:
                updateSortControls();
                addCustomBackControl();
                activity.get().initFragmentToolbars(selectExtension, this::toolbarOnItemClicked, this::selectionToolbarOnItemClicked);
                break;
            case EV_ENABLE:
                onEnable();
                break;
            case EV_DISABLE:
                onDisable();
                break;
            default:
                // No default behaviour
        }
    }

    @Override
    public void onDestroy() {
        Preferences.unregisterPrefsChangedListener(prefsListener);
        EventBus.getDefault().unregister(this);
        compositeDisposable.clear();
        if (callback != null) callback.remove();
        super.onDestroy();
    }

    private void customBackPress() {
        // If content is selected, deselect it
        if (!selectExtension.getSelections().isEmpty()) {
            selectExtension.deselect(selectExtension.getSelections());
            activity.get().getSelectionToolbar().setVisibility(View.GONE);
            backButtonPressed = 0;
            return;
        }

        if (!activity.get().collapseSearchMenu() && !activity.get().closeLeftDrawer()) {
            // If none of the above and we're into a grouping, go back to the groups view
            if (!Grouping.FLAT.equals(Preferences.getGroupingDisplay())) {
                // Load an empty list to avoid having the image of the current list appear
                // on screen next time the activity's ViewPager2 switches back to LibraryContentFragment
                viewModel.clearContent();
                // Let the list become visually empty before going back to the groups fragment
                new Handler(Looper.getMainLooper()).postDelayed(() -> activity.get().goBackToGroups(), 100);
            }
            // If none of the above and a search filter is on => clear search filter
            else if (isSearchQueryActive()) {
                setQuery("");
                setMetadata(Collections.emptyList());
                activity.get().hideSearchSortBar(false);
                viewModel.searchContent(getQuery(), getMetadata());
            }
            // If none of the above, user is asking to leave => use double-tap
            else if (backButtonPressed + 2000 > SystemClock.elapsedRealtime()) {
                callback.remove();
                requireActivity().onBackPressed();
            } else {
                backButtonPressed = SystemClock.elapsedRealtime();
                ToastHelper.toast(R.string.press_back_again);

                llm.scrollToPositionWithOffset(0, 0);
            }
        }
    }

    /**
     * Callback for any change in Preferences
     */
    private void onSharedPreferenceChanged(String key) {
        Timber.i("Prefs change detected : %s", key);
        switch (key) {
            case Preferences.Key.ENDLESS_SCROLL:
                setPagingMethod(Preferences.getEndlessScroll(), activity.get().isEditMode());
                FirebaseCrashlytics.getInstance().setCustomKey("Library display mode", Preferences.getEndlessScroll() ? "endless" : "paged");
                viewModel.updateContentOrder(); // Trigger a blank search
                break;
            default:
                // Nothing to handle there
        }
    }

    private void onSubmitSearch(@NonNull final String query) {
        if (query.startsWith("http")) { // Quick-open a page
            Site s = Site.searchByUrl(query);
            if (null == s)
                Snackbar.make(recyclerView, R.string.malformed_url, BaseTransientBottomBar.LENGTH_SHORT).show();
            else if (s.equals(Site.NONE))
                Snackbar.make(recyclerView, R.string.unsupported_site, BaseTransientBottomBar.LENGTH_SHORT).show();
            else
                ContentHelper.launchBrowserFor(requireContext(), s, query);
        } else {
            viewModel.searchContentUniversal(query);
        }
    }

    /**
     * Handler for the "Advanced search" button
     */
    private void onAdvancedSearchButtonClick() {
        Intent search = new Intent(this.getContext(), SearchActivity.class);

        SearchActivityBundle.Builder builder = new SearchActivityBundle.Builder();

        if (!getMetadata().isEmpty())
            builder.setUri(SearchActivityBundle.Builder.buildSearchUri(getMetadata()));

        if (group != null)
            builder.setGroup(group.id);

        builder.setExcludeMode(excludeClicked);
        search.putExtras(builder.getBundle());

        advancedSearchReturnLauncher.launch(search);
        activity.get().collapseSearchMenu();
    }

    /**
     * Called when returning from the Advanced Search screen
     */
    private void advancedSearchReturnResult(final ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK
                && result.getData() != null && result.getData().getExtras() != null) {
            SearchActivityBundle.Parser parser = new SearchActivityBundle.Parser(result.getData().getExtras());
            Uri searchUri = parser.getUri();

            if (searchUri != null) {
                excludeClicked = parser.getExcludeMode();
                setQuery(searchUri.getPath());
                setMetadata(SearchActivityBundle.Parser.parseSearchUri(searchUri));
                viewModel.searchContent(getQuery(), getMetadata());
            }
        }
    }

    /**
     * Initialize the paging method of the screen
     *
     * @param isEndless True if endless mode has to be set; false if paged mode has to be set
     */
    private void setPagingMethod(boolean isEndless, boolean isEditMode) {
        // Editing will always be done in Endless mode
        viewModel.setPagingMethod(isEndless || isEditMode);

        // RecyclerView horizontal centering
        ViewGroup.LayoutParams layoutParams = recyclerView.getLayoutParams();
        recyclerView.setLayoutParams(layoutParams);

        // Pager appearance
        if (!isEndless && !isEditMode) {
            pager.setCurrentPage(1);
            pager.show();
        } else pager.hide();

        // Adapter initialization
        if (isEndless && !isEditMode) {
            @ContentItem.ViewType int viewType;
            if (Preferences.Constant.LIBRARY_DISPLAY_LIST == Preferences.getLibraryDisplay())
                viewType = ContentItem.ViewType.LIBRARY;
            else
                viewType = ContentItem.ViewType.LIBRARY_GRID;

            pagedItemAdapter = new PagedModelAdapter<>(asyncDifferConfig, i -> new ContentItem(viewType), c -> new ContentItem(c, touchHelper, viewType, this::onDeleteSwipedBook));
            fastAdapter = FastAdapter.with(pagedItemAdapter);

            ContentItem item = new ContentItem(viewType);
            fastAdapter.registerItemFactory(item.getType(), item);
            itemAdapter = null;
        } else { // Paged mode or edit mode
            itemAdapter = new ItemAdapter<>();
            fastAdapter = FastAdapter.with(itemAdapter);

            pagedItemAdapter = null;
        }

        if (!fastAdapter.hasObservers()) fastAdapter.setHasStableIds(true);

        // Item click listener
        fastAdapter.setOnClickListener((v, a, i, p) -> onItemClick(p, i));

        // Favourite button click listener
        fastAdapter.addEventHook(new ClickEventHook<ContentItem>() {
            @Override
            public void onClick(@NotNull View view, int i, @NotNull FastAdapter<ContentItem> fastAdapter, @NotNull ContentItem item) {
                if (item.getContent() != null) onBookFavouriteClick(item.getContent());
            }

            @org.jetbrains.annotations.Nullable
            @Override
            public View onBind(RecyclerView.@NotNull ViewHolder viewHolder) {
                if (viewHolder instanceof ContentItem.ContentViewHolder) {
                    return ((ContentItem.ContentViewHolder) viewHolder).getFavouriteButton();
                }
                return super.onBind(viewHolder);
            }
        });

        // Site button click listener
        fastAdapter.addEventHook(new ClickEventHook<ContentItem>() {
            @Override
            public void onClick(@NotNull View view, int i, @NotNull FastAdapter<ContentItem> fastAdapter, @NotNull ContentItem item) {
                if (item.getContent() != null) onBookSourceClick(item.getContent());
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

        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        selectExtension = fastAdapter.getOrCreateExtension(SelectExtension.class);
        if (selectExtension != null) {
            selectExtension.setSelectable(true);
            selectExtension.setMultiSelect(true);
            selectExtension.setSelectOnLongClick(true);
            selectExtension.setSelectWithItemUpdate(true);
            selectExtension.setSelectionListener((item, b) -> this.onSelectionChanged());

            FastAdapterPreClickSelectHelper<ContentItem> helper = new FastAdapterPreClickSelectHelper<>(selectExtension);
            fastAdapter.setOnPreClickListener(helper::onPreClickListener);
            fastAdapter.setOnPreLongClickListener(helper::onPreLongClickListener);
        }

        // Drag, drop & swiping
        @DimenRes int dimen = (Preferences.Constant.LIBRARY_DISPLAY_LIST == Preferences.getLibraryDisplay()) ? R.dimen.delete_drawer_width_list : R.dimen.delete_drawer_width_grid;
        SimpleSwipeDrawerDragCallback dragSwipeCallback = new SimpleSwipeDrawerDragCallback(this, ItemTouchHelper.LEFT, this)
                .withSwipeLeft(Helper.dimensAsDp(requireContext(), dimen))
                .withSensitivity(1.5f)
                .withSurfaceThreshold(0.3f)
                .withNotifyAllDrops(true);
        dragSwipeCallback.setIsDragEnabled(false); // Despite its name, that's actually to disable drag on long tap

        touchHelper = new ItemTouchHelper(dragSwipeCallback);
        touchHelper.attachToRecyclerView(recyclerView);

        recyclerView.setAdapter(fastAdapter);
    }

    /**
     * Returns the index bounds of the list to be displayed according to the given shelf number
     * Used for paged mode only
     *
     * @param shelfNumber Number of the shelf to display
     * @param librarySize Size of the library
     * @return Min and max index of the books to display on the given page
     */
    private ImmutablePair<Integer, Integer> getShelfBound(int shelfNumber, int librarySize) {
        int minIndex = (shelfNumber - 1) * Preferences.getContentPageQuantity();
        int maxIndex = Math.min(minIndex + Preferences.getContentPageQuantity(), librarySize);
        return new ImmutablePair<>(minIndex, maxIndex);
    }

    /**
     * Loads current shelf of books to into the paged mode adapter
     * NB : A bookshelf is the portion of the collection that is displayed on screen by the paged mode
     * The width of the shelf is determined by the "Quantity per page" setting
     *
     * @param iLibrary Library to extract the shelf from
     */
    private void loadBookshelf(@NonNull final PagedList<Content> iLibrary) {
        if (iLibrary.isEmpty()) {
            itemAdapter.set(Collections.emptyList());
            fastAdapter.notifyDataSetChanged();
        } else {
            ImmutablePair<Integer, Integer> bounds = getShelfBound(pager.getCurrentPageNumber(), iLibrary.size());
            int minIndex = bounds.getLeft();
            int maxIndex = bounds.getRight();

            if (minIndex >= maxIndex) { // We just deleted the last item of the last page => Go back one page
                pager.setCurrentPage(pager.getCurrentPageNumber() - 1);
                loadBookshelf(iLibrary);
                return;
            }

            populateBookshelf(iLibrary, pager.getCurrentPageNumber());
        }
    }

    /**
     * Displays the current "bookshelf" (section of the list corresponding to the selected page)
     * A shelf contains as many books as the user has set in Preferences
     * <p>
     * Used in paged mode only
     *
     * @param iLibrary    Library to display books from
     * @param shelfNumber Number of the shelf to display
     */
    private void populateBookshelf(@NonNull final PagedList<Content> iLibrary, int shelfNumber) {
        if (Preferences.getEndlessScroll() || null == itemAdapter) return;

        ImmutablePair<Integer, Integer> bounds = getShelfBound(shelfNumber, iLibrary.size());
        int minIndex = bounds.getLeft();
        int maxIndex = bounds.getRight();

        @ContentItem.ViewType int viewType;
        if (Preferences.Constant.LIBRARY_DISPLAY_LIST == Preferences.getLibraryDisplay())
            viewType = ContentItem.ViewType.LIBRARY; // Paged mode won't be used in edit mode
        else
            viewType = ContentItem.ViewType.LIBRARY_GRID; // Paged mode won't be used in edit mode

        List<ContentItem> contentItems = Stream.of(iLibrary.subList(minIndex, maxIndex)).withoutNulls().map(c -> new ContentItem(c, null, viewType, this::onDeleteSwipedBook)).distinct().toList();
        FastAdapterDiffUtil.INSTANCE.set(itemAdapter, contentItems, CONTENT_ITEM_DIFF_CALLBACK);

        new Handler(Looper.getMainLooper()).postDelayed(this::differEndCallback, 150);
    }

    private void populateAllResults(@NonNull final PagedList<Content> iLibrary) {
        if (null == itemAdapter) return;

        List<ContentItem> contentItems;
        if (iLibrary.isEmpty()) {
            contentItems = Collections.emptyList();
        } else {
            @ContentItem.ViewType int viewType;

            if (Preferences.Constant.LIBRARY_DISPLAY_LIST == Preferences.getLibraryDisplay() || activity.get().isEditMode()) // Grid won't be used in edit mode
                viewType = activity.get().isEditMode() ? ContentItem.ViewType.LIBRARY_EDIT : ContentItem.ViewType.LIBRARY;
            else
                viewType = ContentItem.ViewType.LIBRARY_GRID;

            contentItems = Stream.of(iLibrary
                    .subList(0, iLibrary.size()))
                    .withoutNulls().map(c ->
                            new ContentItem(c, touchHelper, viewType, this::onDeleteSwipedBook))
                    .distinct()
                    .toList();
        }
        FastAdapterDiffUtil.INSTANCE.set(itemAdapter, contentItems, CONTENT_ITEM_DIFF_CALLBACK);

        new Handler(Looper.getMainLooper()).postDelayed(this::differEndCallback, 150);
    }

    /**
     * LiveData callback when a new search takes place
     *
     * @param b Unused parameter (always set to true)
     */
    private void onNewSearch(Boolean b) {
        newSearch = b;
    }

    /**
     * LiveData callback when the library changes
     * - Either because a new search has been performed
     * - Or because a book has been downloaded, deleted, updated
     *
     * @param result Current library according to active filters
     */
    private void onLibraryChanged(PagedList<Content> result) {
        Timber.i(">> Library changed ! Size=%s", result.size());
        if (!enabled) return;

        activity.get().updateTitle(result.size(), totalContentCount);

        // Update background text
        @StringRes int backgroundText = -1;
        if (result.isEmpty()) {
            if (isSearchQueryActive())
                backgroundText = R.string.search_entry_not_found;
            else if (0 == totalContentCount) backgroundText = R.string.downloads_empty_library;
        }

        if (backgroundText != -1) {
            emptyText.setVisibility(View.VISIBLE);
            emptyText.setText(backgroundText);
        } else emptyText.setVisibility(View.GONE);

        // Update visibility of advanced search bar
        activity.get().updateSearchBarOnResults(!result.isEmpty());

        String query = getQuery();
        // User searches a book ID
        // => Suggests searching through all sources except those where the selected book ID is already in the collection
        if (newSearch && StringHelper.isNumeric(query)) {
            ArrayList<Integer> siteCodes = Stream.of(result)
                    .withoutNulls()
                    .filter(content -> query.equals(content.getUniqueSiteId()))
                    .map(Content::getSite)
                    .map(Site::getCode)
                    .collect(toCollection(ArrayList::new)); // ArrayList is required by SearchContentIdDialogFragment.invoke

            if (!result.isEmpty()) {
                Snackbar snackbar = Snackbar.make(recyclerView, R.string.launchcode_present, BaseTransientBottomBar.LENGTH_LONG);
                snackbar.setAction(R.string.menu_search, v -> SearchContentIdDialogFragment.invoke(requireContext(), getParentFragmentManager(), query, siteCodes));
                snackbar.show();
            } else
                SearchContentIdDialogFragment.invoke(requireContext(), getParentFragmentManager(), query, siteCodes);
        }

        // If the update is the result of a new search, get back on top of the list
        if (newSearch) topItemPosition = 0;

        // Update displayed books
        if (Preferences.getEndlessScroll() && !activity.get().isEditMode() && pagedItemAdapter != null) {
            pagedItemAdapter.submitList(result, this::differEndCallback);
        } else if (activity.get().isEditMode()) {
            populateAllResults(result);
        } else { // Paged mode
            if (newSearch) pager.setCurrentPage(1);
            pager.setPageCount((int) Math.ceil(result.size() * 1.0 / Preferences.getContentPageQuantity()));
            loadBookshelf(result);
        }

        newSearch = false;
        library = result;
    }

    /**
     * LiveData callback when the total number of books changes (because of book download of removal)
     *
     * @param count Current book count in the whole, unfiltered library
     */
    private void onTotalContentChanged(Integer count) {
        totalContentCount = count;
        if (library != null && enabled)
            activity.get().updateTitle(library.size(), totalContentCount);
    }

    /**
     * LiveData callback when the selected group changes (when zooming on a group)
     *
     * @param group Currently selected group
     */
    private void onGroupChanged(Group group) {
        this.group = group;
    }

    /**
     * Callback for the book holder itself
     *
     * @param item ContentItem that has been clicked on
     */
    private boolean onItemClick(int position, @NonNull ContentItem item) {
        if (selectExtension.getSelections().isEmpty()) {
            if (item.getContent() != null && !item.getContent().isBeingDeleted()) {
                topItemPosition = position;
                ContentHelper.openHentoidViewer(requireContext(), item.getContent(), -1, viewModel.getSearchManagerBundle());
            }
            return true;
        }
        return false;
    }

    /**
     * Callback for the "source" button of the book holder
     *
     * @param content Content whose "source" button has been clicked on
     */
    private void onBookSourceClick(@NonNull Content content) {
        ContentHelper.viewContentGalleryPage(requireContext(), content);
    }

    /**
     * Callback for the "favourite" button of the book holder
     *
     * @param content Content whose "favourite" button has been clicked on
     */
    private void onBookFavouriteClick(@NonNull Content content) {
        viewModel.toggleContentFavourite(content, this::refreshIfNeeded);
    }

    private void redownloadFromScratch(@NonNull final List<Content> contentList) {
        if (Preferences.getQueueNewDownloadPosition() == QUEUE_NEW_DOWNLOADS_POSITION_ASK) {
            AddQueueMenu.show(activity.get(), recyclerView, this, (position, item) ->
                    redownloadFromScratch(contentList, (0 == position) ? QUEUE_NEW_DOWNLOADS_POSITION_TOP : QUEUE_NEW_DOWNLOADS_POSITION_BOTTOM)
            );
        } else
            redownloadFromScratch(contentList, Preferences.getQueueNewDownloadPosition());
    }

    private void redownloadFromScratch(@NonNull final List<Content> contentList, int addMode) {
        viewModel.redownloadContent(contentList, true, true, addMode,
                () -> {
                    String message = getResources().getQuantityString(R.plurals.add_to_queue, contentList.size(), contentList.size());
                    Snackbar snackbar = Snackbar.make(recyclerView, message, BaseTransientBottomBar.LENGTH_LONG);
                    snackbar.setAction("VIEW QUEUE", v -> viewQueue());
                    snackbar.show();
                },
                t -> {
                    Timber.w(t);
                    Snackbar.make(recyclerView, R.string.redownloaded_error, BaseTransientBottomBar.LENGTH_LONG).show();
                });
    }

    private void download(@NonNull final List<Content> contentList) {
        if (Preferences.getQueueNewDownloadPosition() == QUEUE_NEW_DOWNLOADS_POSITION_ASK) {
            AddQueueMenu.show(activity.get(), recyclerView, this, (position, item) ->
                    download(contentList, (0 == position) ? QUEUE_NEW_DOWNLOADS_POSITION_TOP : QUEUE_NEW_DOWNLOADS_POSITION_BOTTOM)
            );
        } else
            download(contentList, Preferences.getQueueNewDownloadPosition());
    }

    private void download(@NonNull final List<Content> contentList, int addMode) {
        viewModel.downloadContent(contentList, addMode,
                () -> {
                    String message = getResources().getQuantityString(R.plurals.add_to_queue, contentList.size(), contentList.size());
                    Snackbar snackbar = Snackbar.make(recyclerView, message, BaseTransientBottomBar.LENGTH_LONG);
                    snackbar.setAction("VIEW QUEUE", v -> viewQueue());
                    snackbar.show();
                });
    }
    private void stream(@NonNull final List<Content> contentList) {
        viewModel.streamContent(contentList);
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     */
    private void onSelectionChanged() {
        Set<ContentItem> selectedItems = selectExtension.getSelectedItems();
        int selectedCount = selectedItems.size();

        if (0 == selectedCount) {
            activity.get().getSelectionToolbar().setVisibility(View.GONE);
            selectExtension.setSelectOnLongClick(true);
        } else {
            long selectedLocalCount = Stream.of(selectedItems).map(ContentItem::getContent).withoutNulls().map(Content::getStatus).filterNot(s -> s.equals(StatusContent.EXTERNAL)).count();
            long selectedOnlineCount = Stream.of(selectedItems).map(ContentItem::getContent).withoutNulls().map(Content::getStatus).filter(s -> s.equals(StatusContent.ONLINE)).count();
            activity.get().updateSelectionToolbar(selectedCount, selectedLocalCount, selectedOnlineCount);
            activity.get().getSelectionToolbar().setVisibility(View.VISIBLE);
        }
    }

    /**
     * Handler for any page change
     */
    private void handleNewPage() {
        loadBookshelf(library);
        recyclerView.scrollToPosition(0);
    }

    /**
     * Navigate to the queue screen
     */
    private void viewQueue() {
        Intent intent = new Intent(requireContext(), QueueActivity.class);
        requireContext().startActivity(intent);
    }

    /**
     * Callback for the end of item diff calculations
     * Activated when all _adapter_ items are placed on their definitive position
     */
    private void differEndCallback() {
        if (topItemPosition > -1) {
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
        int currentPosition = getTopItemPosition();
        if (currentPosition != topItemPosition)
            llm.scrollToPositionWithOffset(topItemPosition, 0); // Used to restore position after activity has been stopped and recreated
    }

    /**
     * Calculate the position of the top visible item of the book list
     *
     * @return position of the top visible item of the book list
     */
    private int getTopItemPosition() {
        return Math.max(llm.findFirstVisibleItemPosition(), llm.findFirstCompletelyVisibleItemPosition());
    }

    private IAdapter<ContentItem> getItemAdapter() {
        if (itemAdapter != null) return itemAdapter;
        else return pagedItemAdapter;
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
    public void itemTouchDropped(int i, int i1) {
        // Nothing; final position will be saved once the "save" button is hit
    }

    @Override
    public void itemTouchStartDrag(RecyclerView.@NotNull ViewHolder viewHolder) {
        if (viewHolder instanceof IDraggableViewHolder) {
            ((IDraggableViewHolder) viewHolder).onDragged();
        }
    }

    @Override
    public void itemTouchStopDrag(RecyclerView.@NotNull ViewHolder viewHolder) {
        // Nothing
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

    private void onDeleteSwipedBook(@NonNull final ContentItem item) {
        // Deleted book is the last selected books => disable selection mode
        if (item.isSelected()) {
            selectExtension.deselect(item);
            if (selectExtension.getSelections().isEmpty())
                activity.get().getSelectionToolbar().setVisibility(View.GONE);
        }
        Content content = item.getContent();
        if (content != null)
            viewModel.deleteItems(Stream.of(content).toList(), Collections.emptyList(), false);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onProcessEvent(ProcessEvent event) {
        // Filter on delete complete event
        if (R.id.delete_service != event.processId) return;
        if (ProcessEvent.EventType.COMPLETE != event.eventType) return;
        refreshIfNeeded();
    }

    @Override
    public void onChangeGroupSuccess() {
        refreshIfNeeded();
    }

    /**
     * Force a new search when the sort order is custom
     * (in that case, LiveData can't do its job because of https://github.com/objectbox/objectbox-java/issues/141)
     */
    private void refreshIfNeeded() {
        if (Preferences.getContentSortField() == Preferences.Constant.ORDER_FIELD_CUSTOM)
            viewModel.updateContentOrder();
    }
}
