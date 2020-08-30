package me.devsaki.hentoid.fragments.library;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.extensions.ExtensionsFactories;
import com.mikepenz.fastadapter.select.SelectExtension;
import com.mikepenz.fastadapter.select.SelectExtensionFactory;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.List;
import java.util.Set;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.LibraryActivity;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.events.AppUpdatedEvent;
import me.devsaki.hentoid.util.Debouncer;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.RandomSeedSingleton;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.util.exception.ContentNotRemovedException;
import me.devsaki.hentoid.util.exception.FileNotRemovedException;
import me.devsaki.hentoid.viewholders.GroupDisplayItem;
import me.devsaki.hentoid.viewmodels.LibraryViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;

public class LibraryGroupsFragment extends Fragment {

    private static final String KEY_LAST_LIST_POSITION = "last_list_position";


    // ======== COMMUNICATION
    private OnBackPressedCallback callback;
    // Viewmodel
    private LibraryViewModel viewModel;
    // Settings listener
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (p, k) -> onSharedPreferenceChanged(k);


    // ======== UI
    // Text that displays in the background when the list is empty
    private TextView emptyText;
    // Main view where books are displayed
    private RecyclerView recyclerView;
    // LayoutManager of the recyclerView
    private LinearLayoutManager llm;

    // === SORT TOOLBAR
    // Sort direction button
    private ImageView sortDirectionButton;
    // Sort field button
    private TextView sortFieldButton;

    // === SELECTION TOOLBAR
    private Toolbar toolbar;
    private Toolbar selectionToolbar;
    private MenuItem itemDelete;
    private MenuItem itemShare;
    private MenuItem itemArchive;
    private MenuItem itemFolder;
    private MenuItem itemRedownload;
    private MenuItem itemDeleteAll;

    // === FASTADAPTER COMPONENTS AND HELPERS
    private ItemAdapter<GroupDisplayItem> itemAdapter;
    private FastAdapter<GroupDisplayItem> fastAdapter;
    private SelectExtension<GroupDisplayItem> selectExtension;


    // ======== VARIABLES
    // Records the system time (ms) when back button has been last pressed (to detect "double back button" event)
    private long backButtonPressed;
    // Used to ignore native calls to onBookClick right after that book has been deselected
    private boolean invalidateNextBookClick = false;
    // Total number of books in the whole unfiltered library
    private int totalContentCount;
    // Position of top item to memorize or restore (used when activity is destroyed and recreated)
    private int topItemPosition = -1;

    // Used to start processing when the recyclerView has finished updating
    private final Debouncer<Integer> listRefreshDebouncer = new Debouncer<>(75, this::onRecyclerUpdated);


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                customBackPress();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(this, callback);
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
        View rootView = inflater.inflate(R.layout.fragment_library_groups, container, false);

        Preferences.registerPrefsChangedListener(prefsListener);

        ViewModelFactory vmFactory = new ViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(requireActivity(), vmFactory).get(LibraryViewModel.class);

        initUI(rootView);
        initToolbars();

        selectionToolbar.setOnMenuItemClickListener(this::selectionToolbarOnItemClicked);

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel.getGroups().observe(getViewLifecycleOwner(), this::onGroupsChanged);
        viewModel.getLibraryPaged().observe(getViewLifecycleOwner(), this::onLibraryChanged);

        viewModel.selectGroups(Preferences.getGroupingDisplay()); // Trigger a blank search
    }

    /**
     * Initialize the UI components
     *
     * @param rootView Root view of the library screen
     */
    private void initUI(@NonNull View rootView) {
        LibraryActivity activity = ((LibraryActivity) requireActivity());
        emptyText = requireViewById(rootView, R.id.library_empty_txt);

        // Sort controls
        sortDirectionButton = activity.getSortDirectionButton();
        sortDirectionButton.setImageResource(Preferences.isContentSortDesc() ? R.drawable.ic_simple_arrow_down : R.drawable.ic_simple_arrow_up);
        sortDirectionButton.setOnClickListener(v -> {
            boolean sortDesc = !Preferences.isContentSortDesc();
            Preferences.setContentSortDesc(sortDesc);
            // Update icon
            sortDirectionButton.setImageResource(sortDesc ? R.drawable.ic_simple_arrow_down : R.drawable.ic_simple_arrow_up);
            // Run a new search
            viewModel.updateOrder();
            activity.sortCommandsAutoHide(true);
        });
        sortFieldButton = activity.getSortFieldButton();
        sortFieldButton.setText(getNameFromFieldCode(Preferences.getContentSortField()));
        sortFieldButton.setOnClickListener(v -> {
            // Load and display the field popup menu
            PopupMenu popup = new PopupMenu(requireContext(), sortDirectionButton);
            popup.getMenuInflater()
                    .inflate(R.menu.library_sort_menu, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> {
                // Update button text
                sortFieldButton.setText(item.getTitle());
                item.setChecked(true);
                int fieldCode = getFieldCodeFromMenuId(item.getItemId());
                if (fieldCode == Preferences.Constant.ORDER_FIELD_RANDOM)
                    RandomSeedSingleton.getInstance().renewSeed();

                Preferences.setContentSortField(fieldCode);
                // Run a new search
                viewModel.updateOrder();
                activity.sortCommandsAutoHide(true);
                return true;
            });
            popup.show(); //showing popup menu
            activity.sortCommandsAutoHide(true);
        }); //closing the setOnClickListener method

        // RecyclerView
        recyclerView = requireViewById(rootView, R.id.library_list);
        llm = (LinearLayoutManager) recyclerView.getLayoutManager();
        new FastScrollerBuilder(recyclerView).build();

        // Pager
        initPagingMethod();
    }

    private String getQuery() {
        return ((LibraryActivity) requireActivity()).getQuery();
    }

    private void setQuery(String query) {
        ((LibraryActivity) requireActivity()).setQuery(query);
    }

    private List<Attribute> getMetadata() {
        return ((LibraryActivity) requireActivity()).getMetadata();
    }

    private void setMetadata(List<Attribute> attrs) {
        ((LibraryActivity) requireActivity()).setMetadata(attrs);
    }

    public void onSearch(String query) {
        viewModel.searchUniversal(query);
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
            case (Preferences.Constant.ORDER_FIELD_RANDOM):
                return R.string.sort_random;
            default:
                return R.string.sort_invalid;
        }
    }

    private void initToolbars() {
        if (!(requireActivity() instanceof LibraryActivity)) return;
        LibraryActivity activity = (LibraryActivity) requireActivity();

        toolbar = activity.getToolbar();
        selectionToolbar = activity.getSelectionToolbar();
        selectionToolbar.setNavigationOnClickListener(v -> {
            selectExtension.deselect();
            selectionToolbar.setVisibility(View.GONE);
        });

        selectionToolbar.getMenu().clear();
        selectionToolbar.inflateMenu(R.menu.library_selection_menu);

        itemDelete = selectionToolbar.getMenu().findItem(R.id.action_delete);
        itemShare = selectionToolbar.getMenu().findItem(R.id.action_share);
        itemArchive = selectionToolbar.getMenu().findItem(R.id.action_archive);
        itemFolder = selectionToolbar.getMenu().findItem(R.id.action_open_folder);
        itemRedownload = selectionToolbar.getMenu().findItem(R.id.action_redownload);
        itemDeleteAll = selectionToolbar.getMenu().findItem(R.id.action_delete_all);
    }

    private boolean selectionToolbarOnItemClicked(@NonNull MenuItem menuItem) {
        boolean keepToolbar = false;
        switch (menuItem.getItemId()) {
            case R.id.action_delete:
            case R.id.action_delete_all:
                purgeSelectedItems();
                break;
            case R.id.action_archive:
                archiveSelectedItems();
                break;
            default:
                selectionToolbar.setVisibility(View.GONE);
                return false;
        }
        if (!keepToolbar) selectionToolbar.setVisibility(View.GONE);
        return true;
    }

    private void updateSelectionToolbar(long selectedTotalCount, long selectedLocalCount) {
        boolean isMultipleSelection = selectedTotalCount > 1;

        itemDelete.setVisible(!isMultipleSelection && (1 == selectedLocalCount || Preferences.isDeleteExternalLibrary()));
        itemShare.setVisible(!isMultipleSelection && 1 == selectedLocalCount);
        itemArchive.setVisible(!isMultipleSelection);
        itemFolder.setVisible(!isMultipleSelection);
        itemRedownload.setVisible(selectedLocalCount > 0);
        itemDeleteAll.setVisible(isMultipleSelection && (selectedLocalCount > 0 || Preferences.isDeleteExternalLibrary()));

        selectionToolbar.setTitle(getResources().getQuantityString(R.plurals.items_selected, (int) selectedTotalCount, (int) selectedTotalCount));
    }

    /**
     * Callback for the "delete item" action button
     */
    private void purgeSelectedItems() {
        Set<GroupDisplayItem> selectedItems = selectExtension.getSelectedItems();
        if (!selectedItems.isEmpty()) {
            List<Content> selectedContent = Stream.of(selectedItems).map(GroupDisplayItem::getGroup).withoutNulls().map(Group::getContents).single();
            // TODO process underlying content
            /*
            // Remove external items if they can't be deleted
            if (!Preferences.isDeleteExternalLibrary())
                selectedContent = Stream.of(selectedContent).filterNot(c -> c.getStatus().equals(StatusContent.EXTERNAL)).toList();
            if (!selectedContent.isEmpty()) askDeleteItems(selectedContent);
             */
        }
    }

    /**
     * Callback for the "archive item" action button
     */
    private void archiveSelectedItems() {
        Set<GroupDisplayItem> selectedItems = selectExtension.getSelectedItems();
        Context context = getActivity();
        if (1 == selectedItems.size() && context != null) {
            ToastUtil.toast(R.string.packaging_content);
            List<Content> selectedContent = Stream.of(selectedItems).map(GroupDisplayItem::getGroup).withoutNulls().map(Group::getContents).single();
            // TODO process underlying content
            /*
            if (c != null) {
                if (c.getStorageUri().isEmpty()) {
                    ToastUtil.toast(R.string.folder_undefined);
                    return;
                }
                viewModel.archiveContent(c, this::onContentArchiveSuccess);
            }
             */
        }
    }

    /**
     * Callback for the success of the "archive item" action
     *
     * @param archive File containing the created archive
     */
    private void onContentArchiveSuccess(File archive) {
        Context context = getActivity();
        if (null == context) return;

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_STREAM,
                FileProvider.getUriForFile(context, FileHelper.AUTHORITY, archive));
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileHelper.getExtension(archive.getName()));
        sendIntent.setType(mimeType);

        try {
            context.startActivity(sendIntent);
        } catch (ActivityNotFoundException e) {
            Timber.e(e, "No activity found to send %s", archive.getPath());
            ToastUtil.toast(context, R.string.error_send, Toast.LENGTH_LONG);
        }
    }

    /**
     * Display the yes/no dialog to make sure the user really wants to delete selected items
     *
     * @param items Items to be deleted if the answer is yes
     */
    private void askDeleteItems(@NonNull final List<Content> items) {
        Context context = getActivity();
        if (null == context) return;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        String title = context.getResources().getQuantityString(R.plurals.ask_delete_multiple, items.size());
        builder.setMessage(title)
                .setPositiveButton(android.R.string.yes,
                        (dialog, which) -> {
                            selectExtension.deselect();
                            onDeleteBooks(items);
                        })
                .setNegativeButton(android.R.string.no,
                        (dialog, which) -> selectExtension.deselect())
                .create().show();
    }

    private void onDeleteBooks(@NonNull final List<Content> items) {
        viewModel.deleteItems(items, this::onDeleteError);
    }

    /**
     * Callback for the failure of the "delete item" action
     */
    private void onDeleteError(Throwable t) {
        Timber.e(t);
        if (t instanceof ContentNotRemovedException) {
            ContentNotRemovedException e = (ContentNotRemovedException) t;
            String message = (null == e.getMessage()) ? "Content removal failed" : e.getMessage();
            Snackbar snackbar = Snackbar.make(recyclerView, message, BaseTransientBottomBar.LENGTH_LONG);
            // If the cause if not the file not being removed, keep the item on screen, not blinking
            if (!(t instanceof FileNotRemovedException))
                viewModel.flagContentDelete(e.getContent(), false);
            snackbar.setAction("RETRY", v -> viewModel.deleteItems(Stream.of(e.getContent()).toList(), this::onDeleteError));
            snackbar.show();
        }
    }

    /**
     * Indicates whether a search query is active (using universal search or advanced search) or not
     *
     * @return True if a search query is active (using universal search or advanced search); false if not (=whole unfiltered library selected)
     */
    private boolean isSearchQueryActive() {
        return ((LibraryActivity) requireActivity()).isSearchQueryActive();
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
        topItemPosition = -1;
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

    @Override
    public void onDestroy() {
        Preferences.unregisterPrefsChangedListener(prefsListener);
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    private void customBackPress() {
        // If content is selected, deselect it
        if (!selectExtension.getSelectedItems().isEmpty()) {
            selectExtension.deselect();
            selectionToolbar.setVisibility(View.GONE);
            backButtonPressed = 0;
            return;
        }

        if (!((LibraryActivity) requireActivity()).collapseSearchMenu()) {
            // If none of the above, user is asking to leave => use double-tap
            if (backButtonPressed + 2000 > SystemClock.elapsedRealtime()) {
                callback.remove();
                requireActivity().onBackPressed();
            } else {
                backButtonPressed = SystemClock.elapsedRealtime();
                ToastUtil.toast(R.string.press_back_again);

                llm.scrollToPositionWithOffset(0, 0);
            }
        }
    }

    /**
     * Callback for any change in Preferences
     */
    private void onSharedPreferenceChanged(String key) {
        Timber.i("Prefs change detected : %s", key);
        if (Preferences.Key.PREF_COLOR_THEME.equals(key)) {
            // Restart the app with the library activity on top
            Intent intent = requireActivity().getIntent();
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            requireActivity().finish();
            startActivity(intent);
        } else if (Preferences.Key.PREF_GROUPING_DISPLAY.equals(key)) {
            viewModel.selectGroups(Preferences.getGroupingDisplay());
        }
    }

    /**
     * Initialize the paging method of the screen
     */
    private void initPagingMethod() {
        viewModel.setPagingMethod(true);

        itemAdapter = new ItemAdapter<>();
        fastAdapter = FastAdapter.with(itemAdapter);
        fastAdapter.setHasStableIds(true);

        // Item click listener
        fastAdapter.setOnClickListener((v, a, i, p) -> onGroupClick(i, p));

        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        selectExtension = fastAdapter.getOrCreateExtension(SelectExtension.class);
        if (selectExtension != null) {
            selectExtension.setSelectable(true);
            selectExtension.setMultiSelect(true);
            selectExtension.setSelectOnLongClick(true);
            selectExtension.setSelectionListener((i, b) -> this.onSelectionChanged());
        }

        recyclerView.setAdapter(fastAdapter);
    }

    private void onGroupsChanged(List<Group> result) {
        Timber.i(">>Groups changed ! Size=%s", result.size());

        boolean isEmpty = (result.isEmpty());
        emptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);

        List<GroupDisplayItem> groups = Stream.of(result).map(GroupDisplayItem::new).toList();
        itemAdapter.set(groups);
        differEndCallback();
    }

    /**
     * LiveData callback when the library changes
     * Happens when a book has been downloaded or deleted
     *
     * @param result Current library according to active filters
     */
    private void onLibraryChanged(PagedList<Content> result) {
        Timber.i(">>Library changed (groups) ! Size=%s", result.size());

        // Refresh groups
        // TODO do we really want to do that, especially when deleting content ?
        viewModel.selectGroups(Preferences.getGroupingDisplay());
    }

    /**
     * Update the screen title according to current search filter (#TOTAL BOOKS) if no filter is
     * enabled (#FILTERED / #TOTAL BOOKS) if a filter is enabled
     */
    private void updateTitle(long totalSelectedCount, long totalCount) {
        ((LibraryActivity) requireActivity()).updateTitle(totalSelectedCount, totalCount);
    }

    /**
     * Callback for the group holder itself
     *
     * @param item GroupDisplayItem that has been clicked on
     */
    private boolean onGroupClick(@NonNull GroupDisplayItem item, int position) {
        if (selectExtension.getSelectedItems().isEmpty()) {
            if (!invalidateNextBookClick && item.getGroup() != null && !item.getGroup().isBeingDeleted()) {
                topItemPosition = position;
                ((LibraryActivity)requireActivity()).showBooksInGroup(item.getGroup());
            } else invalidateNextBookClick = false;

            return true;
        } else {
            selectExtension.setSelectOnLongClick(false);
        }
        return false;
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     */
    private void onSelectionChanged() {
        Set<GroupDisplayItem> selectedItems = selectExtension.getSelectedItems();
        int selectedTotalCount = selectedItems.size();

        if (0 == selectedTotalCount) {
            selectionToolbar.setVisibility(View.GONE);
            selectExtension.setSelectOnLongClick(true);
            invalidateNextBookClick = true;
            new Handler().postDelayed(() -> invalidateNextBookClick = false, 200);
        } else {
            long selectedLocalCount = Stream.of(selectedItems).map(GroupDisplayItem::getGroup).withoutNulls().count();
            updateSelectionToolbar(selectedTotalCount, selectedLocalCount);
            selectionToolbar.setVisibility(View.VISIBLE);
        }
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
}
