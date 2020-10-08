package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.Group;
import androidx.core.view.GravityCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.mikepenz.fastadapter.select.SelectExtension;
import com.skydoves.balloon.ArrowOrientation;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.events.AppUpdatedEvent;
import me.devsaki.hentoid.events.CommunicationEvent;
import me.devsaki.hentoid.fragments.library.LibraryContentFragment;
import me.devsaki.hentoid.fragments.library.LibraryGroupsFragment;
import me.devsaki.hentoid.fragments.library.UpdateSuccessDialogFragment;
import me.devsaki.hentoid.notification.archive.ArchiveCompleteNotification;
import me.devsaki.hentoid.notification.archive.ArchiveNotificationChannel;
import me.devsaki.hentoid.notification.archive.ArchiveProgressNotification;
import me.devsaki.hentoid.notification.archive.ArchiveStartNotification;
import me.devsaki.hentoid.notification.delete.DeleteCompleteNotification;
import me.devsaki.hentoid.notification.delete.DeleteNotificationChannel;
import me.devsaki.hentoid.notification.delete.DeleteProgressNotification;
import me.devsaki.hentoid.notification.delete.DeleteStartNotification;
import me.devsaki.hentoid.util.Debouncer;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.PermissionUtil;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.TooltipUtil;
import me.devsaki.hentoid.util.exception.ContentNotRemovedException;
import me.devsaki.hentoid.util.exception.FileNotRemovedException;
import me.devsaki.hentoid.util.notification.NotificationManager;
import me.devsaki.hentoid.viewmodels.LibraryViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import timber.log.Timber;

import static com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG;

public class LibraryActivity extends BaseActivity {

    public static final int EV_SEARCH = 1;
    public static final int EV_ADVANCED_SEARCH = 2;
    public static final int EV_UPDATE_SORT = 3;

    public static final int RC_GROUPS = 1;
    public static final int RC_CONTENTS = 2;


    private DrawerLayout drawerLayout;

    private OnBackPressedCallback callback;

    // ======== COMMUNICATION
    // Viewmodel
    private LibraryViewModel viewModel;
    // Settings listener
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (p, k) -> onSharedPreferenceChanged(k);


    // ======== UI
    // Action view associated with search menu button
    private SearchView mainSearchView;

    // ==== Advanced search / sort bar
    // Grey background of the advanced search / sort bar
    private View advancedSearchBar;
    // Groups button
    private View groupsButton;
    // Advanced search text button
    private View advancedSearchButton;
    // CLEAR button
    private TextView searchClearButton;
    // Sort direction button
    private ImageView sortDirectionButton;
    // Sort field button
    private TextView sortFieldButton;

    // === TOOLBAR
    private Toolbar toolbar;
    // "Search" button on top menu
    private MenuItem searchMenu;
    // "Edit mode" / "Validate edit" button on top menu
    private MenuItem editMenu;
    // "Cancel edit" button on top menu
    private MenuItem editCancelMenu;
    // "Create new group" button on top menu
    private MenuItem newGroupMenu;
    // "Toggle favourites" button on top menu
    private MenuItem favsMenu;
    // "Sort" button on top menu
    private MenuItem sortMenu;
    // Alert bars
    private Group permissionsAlertBar;
    private Group storageAlertBar;
    private PopupMenu popup;

    // === SELECTION TOOLBAR
    private Toolbar selectionToolbar;
    private MenuItem editNameMenu;
    private MenuItem deleteMenu;
    private MenuItem shareMenu;
    private MenuItem archiveMenu;
    private MenuItem changeGroupMenu;
    private MenuItem folderMenu;
    private MenuItem redownloadMenu;
    private MenuItem coverMenu;

    private ViewPager2 viewPager;


    // === NOTIFICATIONS
    // Deletion activities
    private NotificationManager deleteNotificationManager;
    private int deleteProgress;
    private int deleteMax;
    // Notification for book archival
    private NotificationManager archiveNotificationManager;
    private int archiveProgress;
    private int archiveMax;


    // ======== VARIABLES
    // Used to ignore native calls to onQueryTextChange
    private boolean invalidateNextQueryTextChange = false;
    // Current text search query
    private String query = "";
    // Current metadata search query
    private List<Attribute> metadata = Collections.emptyList();
    // True if item positioning edit mode is on (only available for specific groupings)
    private boolean editMode = false;
    // True if there's at least one existing custom group; false instead
    private boolean isCustomGroupingAvailable;


    // Used to auto-hide the sort controls bar when no activity is detected
    private final Debouncer<Boolean> sortCommandsAutoHide = new Debouncer<>(2500, this::hideSearchSortBar);


    // === PUBLIC ACCESSORS (to be used by fragments)

    public Toolbar getToolbar() {
        return toolbar;
    }

    public Toolbar getSelectionToolbar() {
        return selectionToolbar;
    }

    public ImageView getSortDirectionButton() {
        return sortDirectionButton;
    }

    public TextView getSortFieldButton() {
        return sortFieldButton;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public List<Attribute> getMetadata() {
        return metadata;
    }

    public void setMetadata(List<Attribute> metadata) {
        this.metadata = metadata;
    }

    public boolean isEditMode() {
        return editMode;
    }

    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
        updateToolbar();
    }

    public void toggleEditMode() {
        setEditMode(!editMode);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_library);
        drawerLayout = findViewById(R.id.drawer_layout);

        callback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                closeNavigationDrawer();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);

        // When the user runs the app for the first time, we want to land them with the
        // navigation drawer open. But just the first time.
        if (!Preferences.isFirstRunProcessComplete()) {
            // first run of the app starts with the nav drawer open
            openNavigationDrawer();
            Preferences.setIsFirstRunProcessComplete(true);
        }

        ViewModelFactory vmFactory = new ViewModelFactory(getApplication());
        viewModel = new ViewModelProvider(this, vmFactory).get(LibraryViewModel.class);
        viewModel.isCustomGroupingAvailable().observe(this, b -> this.isCustomGroupingAvailable = b);

        if (!Preferences.getRecentVisibility()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }

        Preferences.registerPrefsChangedListener(prefsListener);

        initUI();
        initToolbar();
        initSelectionToolbar();

        onCreated();

        EventBus.getDefault().register(this);
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onAppUpdated(AppUpdatedEvent event) {
        EventBus.getDefault().removeStickyEvent(event);
        // Display the "update success" dialog when an update is detected on a release version
        if (!BuildConfig.DEBUG) UpdateSuccessDialogFragment.invoke(getSupportFragmentManager());
    }

    @Override
    public void onDestroy() {
        Preferences.unregisterPrefsChangedListener(prefsListener);
        EventBus.getDefault().unregister(this);
        if (archiveNotificationManager != null) archiveNotificationManager.cancel();
        if (deleteNotificationManager != null) deleteNotificationManager.cancel();

        super.onDestroy();
    }

    private void onCreated() {
        // Display search bar tooltip _after_ the left drawer closes (else it displays over it)
        if (Preferences.isFirstRunProcessComplete())
            TooltipUtil.showTooltip(this, R.string.help_search, ArrowOrientation.TOP, toolbar, this);

        // Display permissions alert if required
        if (!PermissionUtil.checkExternalStorageReadWritePermission(this)) {
            ((TextView) findViewById(R.id.library_alert_txt)).setText(R.string.permissions_lost);
            findViewById(R.id.library_alert_fix_btn).setOnClickListener(v -> fixPermissions());
            permissionsAlertBar.setVisibility(View.VISIBLE);
        } else if (isLowOnSpace()) { // Else display low space alert
            ((TextView) findViewById(R.id.library_alert_txt)).setText(R.string.low_memory);
            permissionsAlertBar.setVisibility(View.GONE);
            storageAlertBar.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Initialize the UI components
     */
    private void initUI() {
        // Permissions alert bar
        permissionsAlertBar = findViewById(R.id.library_permissions_alert_group);
        storageAlertBar = findViewById(R.id.library_storage_alert_group);

        // Search bar
        advancedSearchBar = findViewById(R.id.advanced_search_background);

        // Groups menu
        groupsButton = findViewById(R.id.groups_btn);
        groupsButton.setOnClickListener(v -> {
            // Load and display the field popup menu
            PopupMenu popup = new PopupMenu(this, groupsButton);
            popup.getMenuInflater()
                    .inflate(R.menu.library_groups_menu, popup.getMenu());
            popup.getMenu().findItem(R.id.groups_custom).setVisible(isCustomGroupingAvailable);
            popup.setOnMenuItemClickListener(item -> {
                item.setChecked(true);
                int fieldCode = getGroupingCodeFromMenuId(item.getItemId());
                Preferences.setGroupingDisplay(fieldCode);
                viewModel.setGroup(null);

                // Update screen display
                updateDisplay();
                sortCommandsAutoHide(true, popup);
                return true;
            });
            popup.show(); //showing popup menu
            sortCommandsAutoHide(true, popup);
        }); //closing the setOnClickListener method

        // Link to advanced search
        advancedSearchButton = findViewById(R.id.advanced_search_btn);
        advancedSearchButton.setOnClickListener(v -> onAdvancedSearchButtonClick());

        // Clear search
        searchClearButton = findViewById(R.id.search_clear_btn);
        searchClearButton.setOnClickListener(v -> {
            query = "";
            metadata.clear();
            mainSearchView.setQuery("", false);
            hideSearchSortBar(false);
            signalFragment(EV_SEARCH, "");
        });

        // Sort controls
        sortDirectionButton = findViewById(R.id.sort_direction_btn);
        sortFieldButton = findViewById(R.id.sort_field_btn);

        // Main tabs
        viewPager = findViewById(R.id.library_pager);
        viewPager.setUserInputEnabled(false); // Disable swipe to change tabs
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                hideSearchSortBar(false);
                updateToolbar();
                updateSelectionToolbar(0, 0);
            }
        });

        updateDisplay();
    }

    private void updateDisplay() {
        FragmentStateAdapter pagerAdapter = new LibraryPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        pagerAdapter.notifyDataSetChanged();
    }

    private void initToolbar() {
        toolbar = findViewById(R.id.library_toolbar);
        toolbar.setNavigationOnClickListener(v -> openNavigationDrawer());

        searchMenu = toolbar.getMenu().findItem(R.id.action_search);
        searchMenu.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                showSearchSortBar(true, false, null);
                invalidateNextQueryTextChange = true;

                // Re-sets the query on screen, since default behaviour removes it right after collapse _and_ expand
                if (!query.isEmpty())
                    // Use of handler allows to set the value _after_ the UI has auto-cleared it
                    // Without that handler the view displays with an empty value
                    new Handler().postDelayed(() -> {
                        invalidateNextQueryTextChange = true;
                        mainSearchView.setQuery(query, false);
                    }, 100);

                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                if (!isSearchQueryActive()) {
                    hideSearchSortBar(false);
                }
                invalidateNextQueryTextChange = true;
                return true;
            }
        });

        favsMenu = toolbar.getMenu().findItem(R.id.action_favourites);
        updateFavouriteFilter();

        editMenu = toolbar.getMenu().findItem(R.id.action_edit);
        editCancelMenu = toolbar.getMenu().findItem(R.id.action_edit_cancel);
        newGroupMenu = toolbar.getMenu().findItem(R.id.action_group_new);
        sortMenu = toolbar.getMenu().findItem(R.id.action_order);

        mainSearchView = (SearchView) searchMenu.getActionView();
        mainSearchView.setIconifiedByDefault(true);
        mainSearchView.setQueryHint(getString(R.string.search_hint));
        // Change display when text query is typed
        mainSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                query = s;
                signalFragment(EV_SEARCH, query);
                mainSearchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (invalidateNextQueryTextChange) { // Should not happen when search panel is closing or opening
                    invalidateNextQueryTextChange = false;
                } else if (s.isEmpty()) {
                    query = "";
                    signalFragment(EV_SEARCH, query);
                    searchClearButton.setVisibility(View.GONE);
                }

                return true;
            }
        });
        // Update icons visibility
        updateToolbar();
    }

    public void sortCommandsAutoHide(boolean hideSortOnly, PopupMenu popup) {
        this.popup = popup;
        sortCommandsAutoHide.submit(hideSortOnly);
    }

    public void updateSearchBarOnResults(boolean nonEmptyResults) {
        if (isSearchQueryActive()) {
            showSearchSortBar(true, true, false);
            if (nonEmptyResults) collapseSearchMenu();
        } else {
            searchClearButton.setVisibility(View.GONE);
        }
    }

    /**
     * Callback method used when a sort method is selected in the sort drop-down menu
     * Updates the UI according to the chosen sort method
     *
     * @param menuItem Toolbar of the fragment
     */
    public boolean toolbarOnItemClicked(@NonNull MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.action_favourites:
                menuItem.setChecked(!menuItem.isChecked());
                updateFavouriteFilter();
                viewModel.toggleFavouriteFilter();
                break;
            case R.id.action_order:
                showSearchSortBar(null, null, true);
                sortCommandsAutoHide.submit(true);
                break;
            default:
                return false;
        }
        return true;
    }

    private void showSearchSortBar(Boolean showAdvancedSearch, Boolean showClear, Boolean showSort) {
        advancedSearchBar.setVisibility(View.VISIBLE);
        if (showAdvancedSearch != null)
            advancedSearchButton.setVisibility(showAdvancedSearch && !isGroupDisplayed() ? View.VISIBLE : View.GONE);

        if (showClear != null)
            searchClearButton.setVisibility(showClear ? View.VISIBLE : View.GONE);

        if (showSort != null) {
            if (showSort) searchClearButton.setVisibility(View.GONE);
            sortDirectionButton.setVisibility(showSort ? View.VISIBLE : View.GONE);
            sortFieldButton.setVisibility(showSort ? View.VISIBLE : View.GONE);
        }
    }

    private void hideSearchSortBar(boolean hideSortOnly) {
        boolean isSearchVisible = (View.VISIBLE == advancedSearchButton.getVisibility() || View.VISIBLE == searchClearButton.getVisibility());

        if (!hideSortOnly || !isSearchVisible)
            advancedSearchBar.setVisibility(View.GONE);

        if (!hideSortOnly) {
            advancedSearchButton.setVisibility(View.GONE);
            searchClearButton.setVisibility(View.GONE);
        }

        sortDirectionButton.setVisibility(View.GONE);
        sortFieldButton.setVisibility(View.GONE);

        if (popup != null) popup.dismiss();

        // Restore CLEAR button if it's needed
        if (hideSortOnly && isSearchQueryActive()) searchClearButton.setVisibility(View.VISIBLE);
    }

    public boolean collapseSearchMenu() {
        if (searchMenu != null && searchMenu.isActionViewExpanded()) {
            searchMenu.collapseActionView();
            return true;
        }
        return false;
    }

    private void initSelectionToolbar() {
        selectionToolbar = findViewById(R.id.library_selection_toolbar);
        selectionToolbar.getMenu().clear();
        selectionToolbar.inflateMenu(R.menu.library_selection_menu);

        editNameMenu = selectionToolbar.getMenu().findItem(R.id.action_edit_name);
        deleteMenu = selectionToolbar.getMenu().findItem(R.id.action_delete);
        shareMenu = selectionToolbar.getMenu().findItem(R.id.action_share);
        archiveMenu = selectionToolbar.getMenu().findItem(R.id.action_archive);
        changeGroupMenu = selectionToolbar.getMenu().findItem(R.id.action_change_group);
        folderMenu = selectionToolbar.getMenu().findItem(R.id.action_open_folder);
        redownloadMenu = selectionToolbar.getMenu().findItem(R.id.action_redownload);
        coverMenu = selectionToolbar.getMenu().findItem(R.id.action_set_cover);

        updateSelectionToolbar(0, 0);
    }

    private int getGroupingCodeFromMenuId(@IdRes int menuId) {
        switch (menuId) {
            case (R.id.groups_flat):
                return Grouping.FLAT.getId();
            case (R.id.groups_by_artist):
                return Grouping.ARTIST.getId();
            case (R.id.groups_by_dl_date):
                return Grouping.DL_DATE.getId();
            case (R.id.groups_custom):
                return Grouping.CUSTOM.getId();
            default:
                return Grouping.NONE.getId();
        }
    }

    /**
     * Update favourite filter button appearance on the action bar
     */
    private void updateFavouriteFilter() {
        favsMenu.setIcon(favsMenu.isChecked() ? R.drawable.ic_filter_favs_on : R.drawable.ic_filter_favs_off);
    }

    /**
     * Callback for any change in Preferences
     */
    private void onSharedPreferenceChanged(String key) {
        Timber.i("Prefs change detected : %s", key);
        if (Preferences.Key.PREF_COLOR_THEME.equals(key)) {
            // Restart the app with the library activity on top
            Intent intent = getIntent();
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            finish();
            startActivity(intent);
        }
    }

    /**
     * Handler for the "Advanced search" button
     */
    private void onAdvancedSearchButtonClick() {
        signalFragment(EV_ADVANCED_SEARCH, null);
    }

    /**
     * Update the screen title according to current search filter (#TOTAL BOOKS) if no filter is
     * enabled (#FILTERED / #TOTAL BOOKS) if a filter is enabled
     */
    public void updateTitle(long totalSelectedCount, long totalCount) {
        String title;
        if (totalSelectedCount == totalCount)
            title = totalCount + " items";
        else {
            title = getResources().getQuantityString(R.plurals.number_of_book_search_results, (int) totalSelectedCount, (int) totalSelectedCount, totalCount);
        }
        toolbar.setTitle(title);
    }

    /**
     * Indicates whether a search query is active (using universal search or advanced search) or not
     *
     * @return True if a search query is active (using universal search or advanced search); false if not (=whole unfiltered library selected)
     */
    public boolean isSearchQueryActive() {
        return (!query.isEmpty() || !metadata.isEmpty());
    }

    private void fixPermissions() {
        PermissionUtil.requestExternalStorageReadWritePermission(this, PermissionUtil.RQST_STORAGE_PERMISSION);
    }

    private boolean isLowOnSpace() {
        DocumentFile rootFolder = FileHelper.getFolderFromTreeUriString(this, Preferences.getStorageUri());
        if (null == rootFolder) return false;

        double freeSpaceRatio = new FileHelper.MemoryUsageFigures(this, rootFolder).getFreeUsageRatio100();
        return (freeSpaceRatio < 100 - Preferences.getMemoryAlertThreshold());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode != PermissionUtil.RQST_STORAGE_PERMISSION) return;
        if (permissions.length < 2) return;
        if (grantResults.length == 0) return;
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            permissionsAlertBar.setVisibility(View.GONE);
        } // Don't show rationales here; the alert still displayed on screen should be enough
    }

    public void closeNavigationDrawer() {
        drawerLayout.closeDrawer(GravityCompat.START);
        callback.setEnabled(false);
    }

    public void openNavigationDrawer() {
        drawerLayout.openDrawer(GravityCompat.START);
        callback.setEnabled(true);
    }

    public void goBackToGroups() {
        viewPager.setCurrentItem(0);
    }

    private boolean isGroupDisplayed() {
        return (0 == viewPager.getCurrentItem() && !Preferences.getGroupingDisplay().equals(Grouping.FLAT));
    }

    public void showBooksInGroup(me.devsaki.hentoid.database.domains.Group group) {
        viewModel.setGroup(group);
        viewPager.setCurrentItem(1);
    }

    private void updateToolbar() {
        searchMenu.setVisible(!editMode);
        newGroupMenu.setVisible(!editMode && isGroupDisplayed());
        favsMenu.setVisible(!editMode && !isGroupDisplayed());
        editMenu.setIcon(editMode ? R.drawable.ic_check : R.drawable.ic_reorder_lines);
        editCancelMenu.setVisible(editMode);
        sortMenu.setVisible(!editMode);

        Grouping currentGrouping = Preferences.getGroupingDisplay();
        if (isGroupDisplayed()) editMenu.setVisible(currentGrouping.canReorderGroups());
        else editMenu.setVisible(currentGrouping.canReorderBooks());

        signalFragment(EV_UPDATE_SORT, null);
    }

    public void updateSelectionToolbar(long selectedTotalCount, long selectedLocalCount) {
        boolean isMultipleSelection = selectedTotalCount > 1;
        selectionToolbar.setTitle(getResources().getQuantityString(R.plurals.items_selected, (int) selectedTotalCount, (int) selectedTotalCount));

        if (isGroupDisplayed()) {
            editNameMenu.setVisible(!isMultipleSelection && Preferences.getGroupingDisplay().canReorderGroups());
            deleteMenu.setVisible(!isMultipleSelection && (1 == selectedLocalCount || Preferences.isDeleteExternalLibrary()));
            shareMenu.setVisible(false);
            archiveMenu.setVisible(true);
            changeGroupMenu.setVisible(false);
            folderMenu.setVisible(false);
            redownloadMenu.setVisible(false);
            coverMenu.setVisible(false);
        } else {
            editNameMenu.setVisible(false);
            deleteMenu.setVisible(selectedLocalCount > 0 || Preferences.isDeleteExternalLibrary());
            shareMenu.setVisible(!isMultipleSelection && 1 == selectedLocalCount);
            archiveMenu.setVisible(true);
            changeGroupMenu.setVisible(true);
            folderMenu.setVisible(!isMultipleSelection);
            redownloadMenu.setVisible(selectedLocalCount > 0);
            coverMenu.setVisible(!isMultipleSelection && !Preferences.getGroupingDisplay().equals(Grouping.FLAT));
        }
    }

    /**
     * Display the yes/no dialog to make sure the user really wants to delete selected items
     *
     * @param contents Items to be deleted if the answer is yes
     */
    public void askDeleteItems(
            @NonNull final List<Content> contents,
            @NonNull final List<me.devsaki.hentoid.database.domains.Group> groups,
            @NonNull final SelectExtension<?> selectExtension) {
        // TODO display the number of books and groups that will be deleted
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        int count = !groups.isEmpty() ? groups.size() : contents.size();
        String title = getResources().getQuantityString(R.plurals.ask_delete_multiple, count);
        builder.setMessage(title)
                .setPositiveButton(android.R.string.yes,
                        (dialog, which) -> {
                            selectExtension.deselect();
                            deleteItems(contents, groups);
                        })
                .setNegativeButton(android.R.string.no,
                        (dialog, which) -> selectExtension.deselect())
                .create().show();
    }

    public void deleteItems(
            @NonNull final List<Content> contents,
            @NonNull final List<me.devsaki.hentoid.database.domains.Group> groups
    ) {
        DeleteNotificationChannel.init(this);
        deleteNotificationManager = new NotificationManager(this, 1);
        deleteNotificationManager.cancel();
        deleteProgress = 0;
        deleteMax = contents.size() + groups.size();
        deleteNotificationManager.notify(new DeleteStartNotification());

        viewModel.deleteItems(contents, groups, this::onDeleteProgress, this::onDeleteSuccess, this::onDeleteError);
    }

    /**
     * Callback for the failure of the "delete item" action
     */
    private void onDeleteError(Throwable t) {
        Timber.e(t);
        if (t instanceof ContentNotRemovedException) {
            ContentNotRemovedException e = (ContentNotRemovedException) t;
            String message = (null == e.getMessage()) ? "Content removal failed" : e.getMessage();
            Snackbar.make(viewPager, message, BaseTransientBottomBar.LENGTH_LONG).show();
            // If the cause if not the file not being removed, keep the item on screen, not blinking
            if (!(t instanceof FileNotRemovedException))
                viewModel.flagContentDelete(e.getContent(), false);
        }
    }

    /**
     * Callback for the progress of the "delete item" action
     */
    private void onDeleteProgress(Object item) {
        String title = null;
        if (item instanceof Content) title = ((Content) item).getTitle();
        else if (item instanceof me.devsaki.hentoid.database.domains.Group)
            title = ((me.devsaki.hentoid.database.domains.Group) item).name;

        if (title != null)
            deleteNotificationManager.notify(new DeleteProgressNotification(title, deleteProgress++, deleteMax));
    }

    /**
     * Callback for the success of the "delete item" action
     */
    private void onDeleteSuccess() {
        deleteNotificationManager.notify(new DeleteCompleteNotification(deleteProgress, false));
        Snackbar.make(viewPager, R.string.delete_success, LENGTH_LONG).show();
    }


    /**
     * Display the yes/no dialog to make sure the user really wants to archive selected items
     *
     * @param items Items to be archived if the answer is yes
     */
    public void askArchiveItems(@NonNull final List<Content> items, @NonNull final SelectExtension<?> selectExtension) {
        // TODO display the number of books to archive
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        String title = getResources().getQuantityString(R.plurals.ask_archive_multiple, items.size());
        builder.setMessage(title)
                .setPositiveButton(android.R.string.yes,
                        (dialog, which) -> {
                            selectExtension.deselect();
                            ArchiveNotificationChannel.init(this);
                            archiveNotificationManager = new NotificationManager(this, 1);
                            archiveNotificationManager.cancel();
                            archiveProgress = 0;
                            archiveMax = items.size();
                            archiveNotificationManager.notify(new ArchiveStartNotification());
                            viewModel.archiveContents(items, this::onContentArchiveProgress, this::onContentArchiveSuccess, this::onContentArchiveError);
                        })
                .setNegativeButton(android.R.string.no,
                        (dialog, which) -> selectExtension.deselect())
                .create().show();
    }

    private void onContentArchiveProgress(Content content) {
        archiveNotificationManager.notify(new ArchiveProgressNotification(content.getTitle(), archiveProgress++, archiveMax));
    }

    /**
     * Callback for the success of the "archive item" action
     */
    private void onContentArchiveSuccess() {
        archiveNotificationManager.notify(new ArchiveCompleteNotification(archiveProgress, false));
        Snackbar.make(viewPager, R.string.archive_success, LENGTH_LONG)
                .setAction("OPEN FOLDER", v -> FileHelper.openFile(this, FileHelper.getDownloadsFolder()))
                .show();
    }

    /**
     * Callback for the success of the "archive item" action
     */
    private void onContentArchiveError(Throwable e) {
        Timber.e(e);
        archiveNotificationManager.notify(new ArchiveCompleteNotification(archiveProgress, true));
    }

    private void signalFragment(int eventType, @Nullable String message) {
        EventBus.getDefault().post(new CommunicationEvent(eventType, isGroupDisplayed() ? RC_GROUPS : RC_CONTENTS, message));
    }

    /**
     * ============================== SUBCLASS
     */

    private static class LibraryPagerAdapter extends FragmentStateAdapter {
        LibraryPagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (Grouping.FLAT.equals(Preferences.getGroupingDisplay())) {
                return new LibraryContentFragment();
            } else {
                if (0 == position) {
                    return new LibraryGroupsFragment();
                } else {
                    return new LibraryContentFragment();
                }
            }
        }

        @Override
        public int getItemCount() {
            return (Grouping.FLAT.equals(Preferences.getGroupingDisplay())) ? 1 : 2;
        }
    }
}
